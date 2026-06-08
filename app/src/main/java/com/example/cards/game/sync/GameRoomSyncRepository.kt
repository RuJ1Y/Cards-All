package com.example.cards.game.sync

import com.example.cards.data.SupabaseNet
import com.example.cards.game.model.GameRoomState
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json

class GameRoomSyncRepository(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    suspend fun upsertState(state: GameRoomState, lastActionBy: String): Result<Unit> {
        return runCatching {
            val row = RoomStateSyncRow(
                roomId = state.roomId,
                version = state.version,
                stateJson = json.encodeToString(GameRoomState.serializer(), state),
                lastActionBy = lastActionBy
            )
            SupabaseNet.client.postgrest["game_room_state"].upsert(row)
        }
    }

    suspend fun fetchState(roomId: String): GameRoomState? {
        return runCatching {
            val row = SupabaseNet.client.postgrest["game_room_state"].select {
                filter {
                    eq("room_id", roomId)
                }
            }.decodeSingle<RoomStateSyncRow>()
            json.decodeFromString<GameRoomState>(row.stateJson)
        }.getOrNull()
    }
}

