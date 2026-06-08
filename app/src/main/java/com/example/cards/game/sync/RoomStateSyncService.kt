package com.example.cards.game.sync

import com.example.cards.game.model.GameRoomState

class RoomStateSyncService(
    private val repository: GameRoomSyncRepository
) {

    suspend fun pushState(state: GameRoomState, operatorPlayerId: String): Boolean {
        return repository.upsertState(state, operatorPlayerId).isSuccess
    }

    suspend fun pullState(roomId: String): GameRoomState? {
        return repository.fetchState(roomId)
    }
}

