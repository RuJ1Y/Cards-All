package com.example.cards.data.repository

import com.example.cards.auth.AuthRepository
import com.example.cards.game.engine.GameAction
import com.example.cards.game.engine.GameEngine
import com.example.cards.game.model.CardDefinition
import com.example.cards.game.model.CharacterTemplate
import com.example.cards.game.model.GameRoomState
import com.example.cards.game.repo.DefaultCardRepository
import com.example.cards.game.repo.DefaultCharacterRepository
import com.example.cards.game.sync.GameRoomSyncRepository
import com.example.cards.game.sync.RoomStateSyncService

/**
 * 对战数据仓库（Repository）。
 * 统一管理：
 * 1) 房间状态拉取/推送
 * 2) 本地战斗引擎计算
 * 3) 玩家资料查询
 *
 * 这样 Activity / ViewModel 不需要直接依赖多个数据源，结构更清晰。
 */
class GameRepository(
    private val syncService: RoomStateSyncService = RoomStateSyncService(GameRoomSyncRepository()),
    private val authRepository: AuthRepository = AuthRepository(),
    private val characterRepository: DefaultCharacterRepository = DefaultCharacterRepository(),
    private val cardRepository: DefaultCardRepository = DefaultCardRepository(),
    private val gameEngine: GameEngine = GameEngine(characterRepository, cardRepository)
) {

    suspend fun pullRoomState(roomId: String): GameRoomState? {
        return syncService.pullState(roomId)
    }

    suspend fun pushRoomState(state: GameRoomState, operatorPlayerId: String): Boolean {
        return syncService.pushState(state, operatorPlayerId)
    }

    fun reduceState(state: GameRoomState, action: GameAction): GameEngine.EngineResult {
        return gameEngine.reduce(state, action)
    }

    fun getCharacterTemplate(templateId: String): CharacterTemplate {
        return characterRepository.getById(templateId)
    }

    fun getCardById(cardId: String): CardDefinition? {
        return runCatching { cardRepository.getById(cardId) }.getOrNull()
    }

    fun findCardByName(cardName: String): CardDefinition? {
        return cardRepository.all().firstOrNull { it.name == cardName }
    }

    suspend fun fetchUsername(playerId: String): String {
        return authRepository.fetchUsername(playerId)
    }

    suspend fun fetchAvatarBase64(playerId: String): String? {
        return authRepository.fetchProfile(playerId)?.avatarBase64
    }
}
