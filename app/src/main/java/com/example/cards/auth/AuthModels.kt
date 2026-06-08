package com.example.cards.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerAccountRow(
    @SerialName("player_id")
    val playerId: String,
    val username: String,
    @SerialName("password_hash")
    val passwordHash: String,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class PlayerProfileRow(
    @SerialName("player_id")
    val playerId: String,
    @SerialName("lottery_points")
    val lotteryPoints: Int = 0,
    @SerialName("avatar_base64")
    val avatarBase64: String? = null,
    @SerialName("selected_character_1_id")
    val selectedCharacter1Id: String? = null,
    @SerialName("selected_character_2_id")
    val selectedCharacter2Id: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class PlayerProfileSelectionPatchRow(
    @SerialName("player_id")
    val playerId: String,
    @SerialName("selected_character_1_id")
    val selectedCharacter1Id: String,
    @SerialName("selected_character_2_id")
    val selectedCharacter2Id: String
)

@Serializable
data class PlayerProfileLotteryPatchRow(
    @SerialName("player_id")
    val playerId: String,
    @SerialName("lottery_points")
    val lotteryPoints: Int
)

@Serializable
data class PlayerCharacterRow(
    @SerialName("player_id")
    val playerId: String,
    @SerialName("character_id")
    val characterId: String,
    @SerialName("owned_at")
    val ownedAt: String? = null
)
