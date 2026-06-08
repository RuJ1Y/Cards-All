package com.example.cards.game.engine

import com.example.cards.game.model.BattleEvent
import com.example.cards.game.model.CardDefinition
import com.example.cards.game.model.CharacterState
import com.example.cards.game.model.CharacterTemplate
import com.example.cards.game.model.EffectType
import com.example.cards.game.model.GameRoomState
import com.example.cards.game.model.GameStatus
import com.example.cards.game.model.PlayerState
import com.example.cards.game.model.SkillDefinition
import com.example.cards.game.model.SkillType
import com.example.cards.game.model.TargetCamp
import com.example.cards.game.model.TargetSlot
import com.example.cards.game.repo.CardRepository
import com.example.cards.game.repo.CharacterRepository
import kotlin.random.Random

class GameEngine(
    private val characterRepository: CharacterRepository,
    private val cardRepository: CardRepository
) {

    data class EngineResult(
        val newState: GameRoomState,
        val events: List<BattleEvent>,
        val errorMessage: String? = null
    )

    fun createInitialState(
        roomId: String,
        playerAId: String,
        playerBId: String,
        playerACharacterIds: List<String>,
        playerBCharacterIds: List<String>
    ): GameRoomState {
        require(playerACharacterIds.size == 2) { "玩家A必须选择2个角色" }
        require(playerBCharacterIds.size == 2) { "玩家B必须选择2个角色" }

        val playerA = createPlayerState(playerAId, playerACharacterIds)
        val playerB = createPlayerState(playerBId, playerBCharacterIds)

        val priorityPlayerId = if (Random.nextBoolean()) playerAId else playerBId

        return GameRoomState(
            roomId = roomId,
            version = 1,
            status = GameStatus.PLAYING,
            turnNumber = 1,
            priorityPlayerId = priorityPlayerId,
            activePlayerId = priorityPlayerId,
            players = listOf(playerA, playerB),
            winnerPlayerId = null,
            lastActionName = "游戏开始"
        )
    }

    fun reduce(state: GameRoomState, action: GameAction): EngineResult {
        if (state.status == GameStatus.FINISHED) {
            return EngineResult(state, emptyList(), "对局已结束")
        }

        return when (action) {
            is GameAction.UseCharacterSkill -> handleCharacterSkill(state, action)
            is GameAction.UseCard -> handleUseCard(state, action)
            is GameAction.DiscardCard -> handleDiscardCard(state, action)
            is GameAction.EndTurn -> handleEndTurn(state, action)
        }
    }

    private fun handleCharacterSkill(state: GameRoomState, action: GameAction.UseCharacterSkill): EngineResult {
        if (state.activePlayerId != action.actorPlayerId) {
            return EngineResult(state, emptyList(), "当前不是你的行动回合")
        }

        val actorIndex = state.players.indexOfFirst { it.playerId == action.actorPlayerId }
        val actor = state.players.getOrNull(actorIndex)
            ?: return EngineResult(state, emptyList(), "找不到行动玩家")

        val character = actor.characters.getOrNull(action.actorCharacterIndex)
            ?: return EngineResult(state, emptyList(), "行动角色序号无效")

        if (!character.isAlive) {
            return EngineResult(state, emptyList(), "已阵亡角色不能行动")
        }
        if (character.controlTurns > 0) {
            return EngineResult(state, emptyList(), "该角色处于硬控状态，无法行动")
        }
        if (character.silenceTurns > 0) {
            return EngineResult(state, emptyList(), "该角色被沉默，无法释放技能")
        }

        val characterTemplate = characterRepository.getById(character.templateId)
        val skill = when (action.skillType) {
            SkillType.NORMAL -> characterTemplate.normalSkill
            SkillType.POWER -> characterTemplate.powerSkill
            SkillType.ULTIMATE -> characterTemplate.ultimateSkill
            SkillType.CARD -> return EngineResult(state, emptyList(), "角色技能不支持 CARD 类型")
        }

        val powerSkillFree = action.skillType == SkillType.POWER && character.freePowerSkillCount > 0
        val actorDamageBonus = if (character.damageBoostTurns > 0) character.damageBoostValue else 0

        val nextCharacter = when (action.skillType) {
            SkillType.NORMAL -> {
                if (character.usedNormalThisTurn) {
                    return EngineResult(state, emptyList(), "该角色本回合已使用普通技能")
                }
                character.copy(
                    usedNormalThisTurn = true,
                    ultimateCharge = addCharge(character.ultimateCharge, 1, character.ultimateRequired)
                )
            }

            SkillType.POWER -> {
                if (character.usedPowerThisTurn) {
                    return EngineResult(state, emptyList(), "该角色本回合已使用强化技能")
                }
                if (!powerSkillFree && actor.cost < 2) {
                    return EngineResult(state, emptyList(), "cost 不足，无法释放强化技能")
                }
                character.copy(
                    usedPowerThisTurn = true,
                    ultimateCharge = addCharge(character.ultimateCharge, 2, character.ultimateRequired),
                    freePowerSkillCount = if (powerSkillFree) {
                        (character.freePowerSkillCount - 1).coerceAtLeast(0)
                    } else {
                        character.freePowerSkillCount
                    }
                )
            }

            SkillType.ULTIMATE -> {
                if (character.ultimateCharge < character.ultimateRequired) {
                    return EngineResult(state, emptyList(), "大招点数未满")
                }
                character.copy(ultimateCharge = 0)
            }

            SkillType.CARD -> character
        }

        val actorCharacters = actor.characters.toMutableList()
        actorCharacters[action.actorCharacterIndex] = nextCharacter

        val costAfterBase = when (action.skillType) {
            SkillType.NORMAL -> actor.cost + 1
            SkillType.POWER -> if (powerSkillFree) actor.cost else actor.cost - 2
            SkillType.ULTIMATE -> actor.cost
            SkillType.CARD -> actor.cost
        }

        val playerList = state.players.toMutableList()
        playerList[actorIndex] = actor.copy(cost = costAfterBase, characters = actorCharacters)

        var changed = applySkillEffects(
            players = playerList,
            actorPlayerId = action.actorPlayerId,
            targetPlayerId = action.targetPlayerId,
            targetCharacterIndex = action.targetCharacterIndex,
            skill = skill,
            fallbackTargetCharacterIndex = action.actorCharacterIndex,
            damageBonus = actorDamageBonus
        )

        // 剑士终极技复合效果：再给自己加3点护盾。
        if (skill.id == "swordsman_u") {
            val actorStateIndex = changed.indexOfFirst { it.playerId == action.actorPlayerId }
            if (actorStateIndex >= 0) {
                val actorState = changed[actorStateIndex]
                val nextChars = actorState.characters.toMutableList()
                val selfChar = nextChars.getOrNull(action.actorCharacterIndex)
                if (selfChar != null) {
                    nextChars[action.actorCharacterIndex] = selfChar.copy(shield = selfChar.shield + 3)
                    changed[actorStateIndex] = actorState.copy(characters = nextChars)
                }
            }
        }
        // 法师终极技：为自己添加 2 回合 2 点增伤（不影响灼烧）。
        if (skill.id == "mage_u") {
            val actorStateIndex = changed.indexOfFirst { it.playerId == action.actorPlayerId }
            if (actorStateIndex >= 0) {
                val actorState = changed[actorStateIndex]
                val nextChars = actorState.characters.toMutableList()
                val selfChar = nextChars.getOrNull(action.actorCharacterIndex)
                if (selfChar != null && selfChar.isAlive) {
                    nextChars[action.actorCharacterIndex] = selfChar.copy(
                        damageBoostTurns = maxOf(selfChar.damageBoostTurns, 2),
                        damageBoostValue = maxOf(selfChar.damageBoostValue, 2)
                    )
                    changed[actorStateIndex] = actorState.copy(characters = nextChars)
                }
            }
        }
        // 牧师终极技：附加 2 回合 2 点持续治疗 buff。
        if (skill.id == "priest_u") {
            val actorStateIndex = changed.indexOfFirst { it.playerId == action.actorPlayerId }
            if (actorStateIndex >= 0) {
                val actorState = changed[actorStateIndex]
                val nextChars = actorState.characters.map { old ->
                    if (!old.isAlive) {
                        old
                    } else {
                        old.copy(
                            regenTurns = maxOf(old.regenTurns, 2),
                            regenHeal = maxOf(old.regenHeal, 2)
                        )
                    }
                }
                changed[actorStateIndex] = actorState.copy(characters = nextChars)
            }
        }
        // Saber 特殊伤害逻辑：主目标按技能本体结算，另一名敌方角色追加固定伤害。
        if (skill.id == "saber_p") {
            changed = applyDamageToOtherEnemy(
                players = changed,
                actorPlayerId = action.actorPlayerId,
                targetPlayerId = action.targetPlayerId,
                primaryTargetIndex = action.targetCharacterIndex,
                damage = (1 + actorDamageBonus).coerceAtLeast(0)
            )
        }
        if (skill.id == "saber_u") {
            changed = applyDamageToOtherEnemy(
                players = changed,
                actorPlayerId = action.actorPlayerId,
                targetPlayerId = action.targetPlayerId,
                primaryTargetIndex = action.targetCharacterIndex,
                damage = (2 + actorDamageBonus).coerceAtLeast(0)
            )
        }

        val withWinner = attachWinnerIfNeeded(state.copy(players = changed), skill.name)
        return EngineResult(
            newState = withWinner.copy(version = withWinner.version + 1, lastActionName = skill.name),
            events = listOf(BattleEvent("${action.actorPlayerId} 释放 ${skill.name}"))
        )
    }

    private fun handleUseCard(state: GameRoomState, action: GameAction.UseCard): EngineResult {
        if (state.activePlayerId != action.actorPlayerId) {
            return EngineResult(state, emptyList(), "当前不是你的行动回合")
        }

        val actorIndex = state.players.indexOfFirst { it.playerId == action.actorPlayerId }
        val actor = state.players.getOrNull(actorIndex)
            ?: return EngineResult(state, emptyList(), "找不到行动玩家")

        if (actor.cost < 1) {
            return EngineResult(state, emptyList(), "cost 不足，无法打出卡牌")
        }

        val cardId = actor.handCardIds.getOrNull(action.handIndex)
            ?: return EngineResult(state, emptyList(), "手牌序号无效")

        val card = cardRepository.getById(cardId)

        val nextHand = actor.handCardIds.toMutableList().apply { removeAt(action.handIndex) }
        val nextDiscard = actor.discardCardIds + cardId

        val players = state.players.toMutableList()
        players[actorIndex] = actor.copy(
            cost = actor.cost - 1,
            handCardIds = nextHand,
            discardCardIds = nextDiscard
        )

        val changed = applyCardEffects(
            players = players,
            action = action,
            card = card
        )

        val withWinner = attachWinnerIfNeeded(state.copy(players = changed), card.name)
        return EngineResult(
            newState = withWinner.copy(version = withWinner.version + 1, lastActionName = card.name),
            events = listOf(BattleEvent("${action.actorPlayerId} 打出卡牌 ${card.name}"))
        )
    }

    private fun handleDiscardCard(state: GameRoomState, action: GameAction.DiscardCard): EngineResult {
        if (state.activePlayerId != action.actorPlayerId) {
            return EngineResult(state, emptyList(), "当前不是你的行动回合")
        }

        val actorIndex = state.players.indexOfFirst { it.playerId == action.actorPlayerId }
        val actor = state.players.getOrNull(actorIndex)
            ?: return EngineResult(state, emptyList(), "找不到行动玩家")

        val cardId = actor.handCardIds.getOrNull(action.handIndex)
            ?: return EngineResult(state, emptyList(), "手牌序号无效")

        val nextHand = actor.handCardIds.toMutableList().apply { removeAt(action.handIndex) }
        val nextDiscard = actor.discardCardIds + cardId

        val players = state.players.toMutableList()
        players[actorIndex] = actor.copy(
            cost = actor.cost + 1,
            handCardIds = nextHand,
            discardCardIds = nextDiscard
        )

        val nextState = state.copy(
            version = state.version + 1,
            players = players,
            lastActionName = "弃牌"
        )

        return EngineResult(
            newState = nextState,
            events = listOf(BattleEvent("${action.actorPlayerId} 弃置卡牌 $cardId，cost +1"))
        )
    }

    private fun handleEndTurn(state: GameRoomState, action: GameAction.EndTurn): EngineResult {
        if (state.activePlayerId != action.actorPlayerId) {
            return EngineResult(state, emptyList(), "当前不是你的行动回合")
        }

        val nextActiveId = state.players.firstOrNull { it.playerId != action.actorPlayerId }?.playerId
            ?: return EngineResult(state, emptyList(), "房间玩家数据异常")

        val players = state.players.toMutableList()

        val nextActiveIndex = players.indexOfFirst { it.playerId == nextActiveId }
        val nextActive = players[nextActiveIndex]

        val tickedCharacters = nextActive.characters.map { applyTurnStartEffects(it) }
        val resetCharacters = tickedCharacters.map { it.copy(usedNormalThisTurn = false, usedPowerThisTurn = false) }

        val drawResult = drawCards(nextActive.deckCardIds, nextActive.handCardIds, 2)

        players[nextActiveIndex] = nextActive.copy(
            characters = resetCharacters,
            deckCardIds = drawResult.first,
            handCardIds = drawResult.second
        )

        val progressed = state.copy(
            version = state.version + 1,
            turnNumber = state.turnNumber + 1,
            activePlayerId = nextActiveId,
            players = players,
            lastActionName = "回合结束"
        )

        val withWinner = attachWinnerIfNeeded(progressed, "回合结束")
        return EngineResult(
            newState = withWinner,
            events = listOf(BattleEvent("${action.actorPlayerId} 回合结束，轮到 $nextActiveId"))
        )
    }

    private fun applyCardEffects(
        players: MutableList<PlayerState>,
        action: GameAction.UseCard,
        card: CardDefinition
    ): MutableList<PlayerState> {
        return when (card.id) {
            "c03_coffee",
            "c15_turbo_boost" -> {
                applyStatusToSingleTarget(players, action, card.skill.targetCamp) {
                    it.copy(
                        damageBoostTurns = maxOf(it.damageBoostTurns, 2),
                        damageBoostValue = maxOf(it.damageBoostValue, 2)
                    )
                }
            }

            "c04_tower_never_fall" -> {
                applyStatusToSingleTarget(players, action, card.skill.targetCamp) {
                    it.copy(silenceTurns = maxOf(it.silenceTurns, 1))
                }
            }

            "c05_again_once" -> {
                applyStatusToSingleTarget(players, action, card.skill.targetCamp) {
                    if (!it.isAlive) it else it.copy(usedNormalThisTurn = false, usedPowerThisTurn = false)
                }
            }

            "c07_lottery" -> {
                val actorIndex = players.indexOfFirst { it.playerId == action.actorPlayerId }
                if (actorIndex >= 0 && Random.nextInt(100) < 10) {
                    val actor = players[actorIndex]
                    players[actorIndex] = actor.copy(cost = MAX_COST)
                }
                players
            }

            "c08_one_for_two" -> {
                val actorIndex = players.indexOfFirst { it.playerId == action.actorPlayerId }
                if (actorIndex >= 0) {
                    val actor = players[actorIndex]
                    // 一换二优先抽取非“一换二”卡，避免连续回手造成体感异常。
                    // 仅当牌堆里可抽卡不足时，才会兜底抽取被排除卡。
                    val draw = drawCardsPreferNonExcluded(
                        deck = actor.deckCardIds,
                        hand = actor.handCardIds,
                        count = 2,
                        excludedCardIds = setOf("c08_one_for_two")
                    )
                    players[actorIndex] = actor.copy(deckCardIds = draw.first, handCardIds = draw.second)
                }
                players
            }

            "c09_little_ivan",
            "c18_c0_boom" -> {
                applyToAllCharacters(players) { old ->
                    if (!old.isAlive) old else applyDamageToCharacter(old, 10)
                }
            }

            "c10_world_peace" -> {
                applyToAllCharacters(players) { old ->
                    if (!old.isAlive) old else old.copy(currentHp = (old.currentHp + 5).coerceAtMost(old.maxHp))
                }
            }

            "c12_stun_gun" -> {
                applyStatusToSingleTarget(players, action, card.skill.targetCamp) {
                    it.copy(controlTurns = maxOf(it.controlTurns, 1))
                }
            }

            "c13_fufu" -> players

            "c14_revive" -> {
                applyStatusToSingleTarget(players, action, card.skill.targetCamp) {
                    if (it.currentHp > 0) it else it.copy(currentHp = minOf(10, it.maxHp))
                }
            }

            "c16_million_pound" -> {
                applyStatusToSingleTarget(players, action, card.skill.targetCamp) {
                    it.copy(freePowerSkillCount = it.freePowerSkillCount + 1)
                }
            }

            "c19_rho_aias" -> {
                applyStatusToSingleTarget(players, action, card.skill.targetCamp) {
                    it.copy(nextDamageLimit = 1)
                }
            }

            "c20_mingdao" -> {
                applyStatusToSingleTarget(players, action, card.skill.targetCamp) {
                    it.copy(immortalityCharges = it.immortalityCharges + 1)
                }
            }

            "c22_dual_doctor" -> {
                val actorIndex = players.indexOfFirst { it.playerId == action.actorPlayerId }
                if (actorIndex >= 0) {
                    val healSide = Random.nextBoolean()
                    val actor = players[actorIndex]
                    val nextChars = actor.characters.map { old ->
                        if (!old.isAlive) {
                            old
                        } else if (healSide) {
                            old.copy(currentHp = (old.currentHp + 5).coerceAtMost(old.maxHp))
                        } else {
                            applyDamageToCharacter(old, 5)
                        }
                    }
                    players[actorIndex] = actor.copy(characters = nextChars)
                }
                players
            }

            "c23_paraquat" -> {
                applyStatusToSingleTarget(players, action, card.skill.targetCamp) {
                    it.copy(poisonTurns = maxOf(it.poisonTurns, 2), poisonDamage = maxOf(it.poisonDamage, 3))
                }
            }

            else -> {
                applySkillEffects(
                    players = players,
                    actorPlayerId = action.actorPlayerId,
                    targetPlayerId = action.targetPlayerId,
                    targetCharacterIndex = action.targetCharacterIndex,
                    skill = card.skill,
                    fallbackTargetCharacterIndex = 0,
                    damageBonus = 0
                )
            }
        }
    }

    private fun applySkillEffects(
        players: MutableList<PlayerState>,
        actorPlayerId: String,
        targetPlayerId: String,
        targetCharacterIndex: Int?,
        skill: SkillDefinition,
        fallbackTargetCharacterIndex: Int,
        damageBonus: Int
    ): MutableList<PlayerState> {
        val actorIndex = players.indexOfFirst { it.playerId == actorPlayerId }
        val enemyIndex = players.indexOfFirst { it.playerId != actorPlayerId }

        val targetPlayerIndex = when (skill.targetCamp) {
            TargetCamp.SELF,
            TargetCamp.ALLY -> actorIndex
            TargetCamp.ENEMY -> players.indexOfFirst { it.playerId == targetPlayerId }.takeIf { it >= 0 } ?: enemyIndex
        }

        if (targetPlayerIndex < 0) return players

        val player = players[targetPlayerIndex]
        if (player.characters.isEmpty()) return players
        val indices = when (skill.targetSlot) {
            TargetSlot.SINGLE -> listOf((targetCharacterIndex ?: fallbackTargetCharacterIndex).coerceIn(0, player.characters.lastIndex))
            TargetSlot.ALL -> player.characters.indices.toList()
        }

        val updatedCharacters = player.characters.toMutableList()
        var nextPlayerCost = player.cost

        for (idx in indices) {
            val oldChar = updatedCharacters.getOrNull(idx) ?: continue
            if (!oldChar.isAlive && skill.effects.any { it.type == EffectType.DAMAGE }) {
                continue
            }

            var newChar = oldChar
            skill.effects.forEach { effect ->
                when (effect.type) {
                    EffectType.DAMAGE -> {
                        val totalDamage = (effect.value + damageBonus).coerceAtLeast(0)
                        newChar = applyDamageToCharacter(newChar, totalDamage)
                    }

                    EffectType.HEAL -> {
                        if (!newChar.isAlive) return@forEach
                        val hp = (newChar.currentHp + effect.value).coerceAtMost(newChar.maxHp)
                        newChar = newChar.copy(currentHp = hp)
                    }

                    EffectType.SHIELD -> {
                        if (!newChar.isAlive) return@forEach
                        val shield = (newChar.shield + effect.value).coerceAtLeast(0)
                        newChar = newChar.copy(shield = shield)
                    }

                    EffectType.CHARGE -> {
                        if (!newChar.isAlive) return@forEach
                        val charge = addCharge(newChar.ultimateCharge, effect.value, newChar.ultimateRequired)
                        newChar = newChar.copy(ultimateCharge = charge)
                    }

                    EffectType.COST_GAIN -> {
                        nextPlayerCost += effect.value
                    }
                }
            }
            updatedCharacters[idx] = newChar
        }

        players[targetPlayerIndex] = player.copy(
            cost = nextPlayerCost.coerceAtLeast(0),
            characters = updatedCharacters
        )

        return players
    }

    private fun applyStatusToSingleTarget(
        players: MutableList<PlayerState>,
        action: GameAction.UseCard,
        targetCamp: TargetCamp,
        update: (CharacterState) -> CharacterState
    ): MutableList<PlayerState> {
        val actorIndex = players.indexOfFirst { it.playerId == action.actorPlayerId }
        val enemyIndex = players.indexOfFirst { it.playerId != action.actorPlayerId }
        if (actorIndex < 0) return players

        val targetPlayerIndex = when (targetCamp) {
            TargetCamp.SELF,
            TargetCamp.ALLY -> actorIndex
            TargetCamp.ENEMY -> players.indexOfFirst { it.playerId == action.targetPlayerId }.takeIf { it >= 0 } ?: enemyIndex
        }
        if (targetPlayerIndex < 0) return players

        val targetPlayer = players[targetPlayerIndex]
        if (targetPlayer.characters.isEmpty()) return players

        val targetIndex = (action.targetCharacterIndex ?: 0).coerceIn(0, targetPlayer.characters.lastIndex)
        val targetCharacters = targetPlayer.characters.toMutableList()
        val old = targetCharacters[targetIndex]
        targetCharacters[targetIndex] = update(old)
        players[targetPlayerIndex] = targetPlayer.copy(characters = targetCharacters)

        return players
    }

    private fun applyToAllCharacters(
        players: MutableList<PlayerState>,
        update: (CharacterState) -> CharacterState
    ): MutableList<PlayerState> {
        players.indices.forEach { playerIndex ->
            val oldPlayer = players[playerIndex]
            players[playerIndex] = oldPlayer.copy(
                characters = oldPlayer.characters.map(update)
            )
        }
        return players
    }

    private fun applyDamageToCharacter(character: CharacterState, rawDamage: Int): CharacterState {
        var damage = rawDamage.coerceAtLeast(0)
        if (damage <= 0) return character

        var next = character

        if (next.nextDamageLimit != null) {
            damage = minOf(damage, next.nextDamageLimit.coerceAtLeast(0))
            next = next.copy(nextDamageLimit = null)
        }

        val remainingShield = (next.shield - damage).coerceAtLeast(0)
        val damageToHp = (damage - next.shield).coerceAtLeast(0)

        var hp = (next.currentHp - damageToHp).coerceAtLeast(0)
        var immortality = next.immortalityCharges

        if (hp <= 0 && next.currentHp > 0 && immortality > 0) {
            hp = 1
            immortality -= 1
        }

        return next.copy(
            currentHp = hp,
            shield = remainingShield,
            immortalityCharges = immortality
        )
    }

    private fun applyTurnStartEffects(character: CharacterState): CharacterState {
        var next = character

        if (next.isAlive && next.poisonTurns > 0 && next.poisonDamage > 0) {
            next = applyDamageToCharacter(next, next.poisonDamage)
        }
        if (next.isAlive && next.regenTurns > 0 && next.regenHeal > 0) {
            val healed = (next.currentHp + next.regenHeal).coerceAtMost(next.maxHp)
            next = next.copy(currentHp = healed)
        }

        val nextDamageBoostTurns = if (next.damageBoostTurns > 0) next.damageBoostTurns - 1 else 0
        val nextSilenceTurns = if (next.silenceTurns > 0) next.silenceTurns - 1 else 0
        val nextControlTurns = if (next.controlTurns > 0) next.controlTurns - 1 else 0
        val nextPoisonTurns = if (next.poisonTurns > 0) next.poisonTurns - 1 else 0
        val nextRegenTurns = if (next.regenTurns > 0) next.regenTurns - 1 else 0

        return next.copy(
            damageBoostTurns = nextDamageBoostTurns,
            damageBoostValue = if (nextDamageBoostTurns == 0) 0 else next.damageBoostValue,
            silenceTurns = nextSilenceTurns,
            controlTurns = nextControlTurns,
            poisonTurns = nextPoisonTurns,
            poisonDamage = if (nextPoisonTurns == 0) 0 else next.poisonDamage,
            regenTurns = nextRegenTurns,
            regenHeal = if (nextRegenTurns == 0) 0 else next.regenHeal
        )
    }

    private fun attachWinnerIfNeeded(state: GameRoomState, actionName: String): GameRoomState {
        val alivePlayers = state.players.filter { player ->
            player.characters.any { it.isAlive }
        }

        return if (alivePlayers.size == 1) {
            state.copy(
                status = GameStatus.FINISHED,
                winnerPlayerId = alivePlayers.first().playerId,
                lastActionName = actionName
            )
        } else {
            state
        }
    }

    private fun createPlayerState(playerId: String, characterIds: List<String>): PlayerState {
        val characters = characterIds.map { id ->
            val template: CharacterTemplate = characterRepository.getById(id)
            CharacterState(
                templateId = template.id,
                maxHp = template.maxHp,
                currentHp = template.maxHp,
                ultimateRequired = template.ultimateRequired,
                ultimateCharge = 0,
                shield = 0,
                usedNormalThisTurn = false,
                usedPowerThisTurn = false
            )
        }

        val deck = cardRepository.createDeck()
        val hand = deck.take(3)
        val rest = deck.drop(3)

        return PlayerState(
            playerId = playerId,
            cost = 3,
            characters = characters,
            deckCardIds = rest,
            handCardIds = hand,
            discardCardIds = emptyList()
        )
    }

    private fun drawCards(deck: List<String>, hand: List<String>, count: Int): Pair<List<String>, List<String>> {
        if (count <= 0 || deck.isEmpty()) return deck to hand
        val actual = count.coerceAtMost(deck.size)
        return deck.drop(actual) to (hand + deck.take(actual))
    }

    private fun drawCardsPreferNonExcluded(
        deck: List<String>,
        hand: List<String>,
        count: Int,
        excludedCardIds: Set<String>
    ): Pair<List<String>, List<String>> {
        if (count <= 0 || deck.isEmpty()) return deck to hand

        val pool = deck.toMutableList()
        val picked = mutableListOf<String>()

        var index = 0
        while (index < pool.size && picked.size < count) {
            val cardId = pool[index]
            if (cardId !in excludedCardIds) {
                picked += cardId
                pool.removeAt(index)
            } else {
                index += 1
            }
        }

        while (picked.size < count && pool.isNotEmpty()) {
            picked += pool.removeAt(0)
        }

        return pool to (hand + picked)
    }

    private fun addCharge(current: Int, gain: Int, required: Int): Int {
        if (current >= required) return required
        return (current + gain).coerceAtMost(required)
    }

    private fun applyDamageToOtherEnemy(
        players: MutableList<PlayerState>,
        actorPlayerId: String,
        targetPlayerId: String,
        primaryTargetIndex: Int?,
        damage: Int
    ): MutableList<PlayerState> {
        if (damage <= 0) return players

        val enemyIndex = players.indexOfFirst { it.playerId == targetPlayerId }
            .takeIf { it >= 0 }
            ?: players.indexOfFirst { it.playerId != actorPlayerId }
        if (enemyIndex < 0) return players

        val enemy = players[enemyIndex]
        if (enemy.characters.isEmpty()) return players

        val primary = (primaryTargetIndex ?: 0).coerceIn(0, enemy.characters.lastIndex)
        val secondaryIndex = enemy.characters.indices
            .firstOrNull { it != primary && enemy.characters[it].isAlive }
            ?: enemy.characters.indices.firstOrNull { it != primary }
            ?: return players

        val nextCharacters = enemy.characters.toMutableList()
        val secondary = nextCharacters[secondaryIndex]
        if (!secondary.isAlive) return players

        nextCharacters[secondaryIndex] = applyDamageToCharacter(secondary, damage)
        players[enemyIndex] = enemy.copy(characters = nextCharacters)
        return players
    }

    private companion object {
        const val MAX_COST = 10
    }
}
