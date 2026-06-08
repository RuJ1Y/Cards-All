package com.example.cards.game.model

import kotlinx.serialization.Serializable

@Serializable
enum class SkillType {
    NORMAL,
    POWER,
    ULTIMATE,
    CARD
}

@Serializable
enum class EffectType {
    DAMAGE,
    HEAL,
    SHIELD,
    CHARGE,
    COST_GAIN
}

@Serializable
enum class TargetCamp {
    SELF,
    ALLY,
    ENEMY
}

@Serializable
enum class TargetSlot {
    SINGLE,
    ALL
}

@Serializable
enum class GameStatus {
    WAITING,
    PLAYING,
    FINISHED
}

@Serializable
data class SkillEffect(
    val type: EffectType,
    val value: Int
)

@Serializable
data class SkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val type: SkillType,
    val targetCamp: TargetCamp,
    val targetSlot: TargetSlot,
    val effects: List<SkillEffect>
)

@Serializable
data class CharacterTemplate(
    val id: String,
    val name: String,
    val role: String,
    val maxHp: Int,
    val ultimateRequired: Int,
    val normalSkill: SkillDefinition,
    val powerSkill: SkillDefinition,
    val ultimateSkill: SkillDefinition
)

@Serializable
data class CardDefinition(
    val id: String,
    val name: String,
    val description: String,
    val skill: SkillDefinition
)

@Serializable
data class CharacterState(
    val templateId: String,
    val maxHp: Int,
    val currentHp: Int,
    val ultimateRequired: Int,
    val ultimateCharge: Int,
    val shield: Int,
    val usedNormalThisTurn: Boolean,
    val usedPowerThisTurn: Boolean,
    val damageBoostTurns: Int = 0,
    val damageBoostValue: Int = 0,
    val silenceTurns: Int = 0,
    val controlTurns: Int = 0,
    val poisonTurns: Int = 0,
    val poisonDamage: Int = 0,
    val regenTurns: Int = 0,
    val regenHeal: Int = 0,
    val nextDamageLimit: Int? = null,
    val immortalityCharges: Int = 0,
    val freePowerSkillCount: Int = 0
) {
    val isAlive: Boolean
        get() = currentHp > 0
}

@Serializable
data class PlayerState(
    val playerId: String,
    val cost: Int,
    val characters: List<CharacterState>,
    val deckCardIds: List<String>,
    val handCardIds: List<String>,
    val discardCardIds: List<String>
)

@Serializable
data class GameRoomState(
    val roomId: String,
    val version: Int,
    val status: GameStatus,
    val turnNumber: Int,
    val priorityPlayerId: String,
    val activePlayerId: String,
    val players: List<PlayerState>,
    val winnerPlayerId: String?,
    val lastActionName: String?,
    val leftPlayerIds: List<String> = emptyList()
)

@Serializable
data class BattleEvent(
    val message: String
)

