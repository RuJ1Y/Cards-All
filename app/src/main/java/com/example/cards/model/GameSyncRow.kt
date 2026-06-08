package com.example.cards.model

import kotlinx.serialization.Serializable

@Serializable
data class GameSyncRow(
    val id: Int,
    val count: Int
)
