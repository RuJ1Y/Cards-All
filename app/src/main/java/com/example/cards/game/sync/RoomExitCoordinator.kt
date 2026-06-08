package com.example.cards.game.sync

import com.example.cards.data.SupabaseNet
import com.example.cards.game.model.GameRoomState
import com.example.cards.game.model.GameStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 房间离开协调器：
 * 1) 单人离开时保留房间（便于另一人处理收尾）
 * 2) 两名玩家都离开时删除房间行
 */
class RoomExitCoordinator(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    enum class LeaveOutcome {
        NOOP,
        UPDATED,
        DELETED
    }

    suspend fun leaveRoom(roomId: String, playerId: String): Result<LeaveOutcome> {
        return runCatching {
            if (roomId.isBlank() || playerId.isBlank()) return@runCatching LeaveOutcome.NOOP

            val row = fetchRoomRow(roomId) ?: return@runCatching LeaveOutcome.NOOP

            val gameState = decodeGameState(row.stateJson)
            if (gameState != null) {
                handleGameStateLeave(row, gameState, playerId)
            } else {
                val waiting = decodeWaitingState(row.stateJson)
                    ?: WaitingRoomState(ownerId = row.lastActionBy)
                handleWaitingStateLeave(row, waiting, playerId)
            }
        }
    }

    private suspend fun handleGameStateLeave(
        row: RoomStateSyncRow,
        gameState: GameRoomState,
        playerId: String
    ): LeaveOutcome {
        val playerIds = gameState.players.map { it.playerId }.distinct()
        if (playerIds.isEmpty() || playerId !in playerIds) {
            return LeaveOutcome.NOOP
        }

        val leftSet = gameState.leftPlayerIds.toMutableSet()
        leftSet.add(playerId)

        if (playerIds.all { it in leftSet }) {
            deleteRoomRow(row.roomId)
            return LeaveOutcome.DELETED
        }

        val forcedFinish = gameState.status == GameStatus.PLAYING
        val fallbackWinner = playerIds.firstOrNull { it != playerId }
        val nextState = gameState.copy(
            version = maxOf(gameState.version, row.version) + 1,
            status = if (forcedFinish) GameStatus.FINISHED else gameState.status,
            winnerPlayerId = if (gameState.winnerPlayerId != null) {
                gameState.winnerPlayerId
            } else if (forcedFinish) {
                fallbackWinner
            } else {
                null
            },
            lastActionName = "$playerId 已退出房间",
            leftPlayerIds = leftSet.toList()
        )

        upsertRoomRow(
            row.copy(
                version = nextState.version,
                stateJson = json.encodeToString(GameRoomState.serializer(), nextState),
                lastActionBy = playerId
            )
        )
        return LeaveOutcome.UPDATED
    }

    private suspend fun handleWaitingStateLeave(
        row: RoomStateSyncRow,
        waiting: WaitingRoomState,
        playerId: String
    ): LeaveOutcome {
        return when {
            waiting.ownerId == playerId && waiting.guestId.isNullOrBlank() -> {
                deleteRoomRow(row.roomId)
                LeaveOutcome.DELETED
            }

            waiting.ownerId == playerId && !waiting.guestId.isNullOrBlank() -> {
                val transferred = waiting.copy(ownerId = waiting.guestId.orEmpty(), guestId = null)
                upsertRoomRow(
                    row.copy(
                        version = row.version + 1,
                        stateJson = json.encodeToString(WaitingRoomState.serializer(), transferred),
                        lastActionBy = playerId
                    )
                )
                LeaveOutcome.UPDATED
            }

            waiting.guestId == playerId -> {
                val cleared = waiting.copy(guestId = null)
                upsertRoomRow(
                    row.copy(
                        version = row.version + 1,
                        stateJson = json.encodeToString(WaitingRoomState.serializer(), cleared),
                        lastActionBy = playerId
                    )
                )
                LeaveOutcome.UPDATED
            }

            else -> LeaveOutcome.NOOP
        }
    }

    private suspend fun fetchRoomRow(roomId: String): RoomStateSyncRow? {
        return runCatching {
            SupabaseNet.client.postgrest["game_room_state"].select {
                filter { eq("room_id", roomId) }
            }.decodeList<RoomStateSyncRow>().firstOrNull()
        }.getOrNull()
    }

    private suspend fun upsertRoomRow(row: RoomStateSyncRow) {
        SupabaseNet.client.postgrest["game_room_state"].upsert(row)
    }

    private suspend fun deleteRoomRow(roomId: String) {
        SupabaseNet.client.postgrest["game_room_state"].delete {
            filter { eq("room_id", roomId) }
        }
    }

    private fun decodeGameState(stateJson: String): GameRoomState? {
        return runCatching {
            json.decodeFromString(GameRoomState.serializer(), stateJson)
        }.getOrNull()
    }

    private fun decodeWaitingState(stateJson: String): WaitingRoomState? {
        val raw = stateJson.trim()
        if (raw.isBlank() || raw == "{}") return null
        return runCatching {
            json.decodeFromString(WaitingRoomState.serializer(), raw)
        }.getOrNull()
    }
}

@Serializable
private data class WaitingRoomState(
    @SerialName("phase")
    val phase: String = "waiting",
    @SerialName("owner_id")
    val ownerId: String,
    @SerialName("guest_id")
    val guestId: String? = null
)

