package com.example.cards.game.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomStateSyncRow(
    @SerialName("room_id")
    val roomId: String,
    val version: Int,
    @SerialName("state_json")
    val stateJson: String,
    @SerialName("last_action_by")
    val lastActionBy: String,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

