package com.example.cards.game.repo

import com.example.cards.auth.PlayerCharacterRow
import com.example.cards.auth.PlayerProfileRow
import com.example.cards.auth.PlayerProfileSelectionPatchRow
import com.example.cards.data.SupabaseNet
import io.github.jan.supabase.postgrest.postgrest

class PlayerCharacterCloudRepository {

    suspend fun ensureDefaultState(playerId: String) {
        runCatching {
            SupabaseNet.client.postgrest["player_characters"].upsert(
                DEFAULT_OWNED_ROLE_IDS.map { roleId ->
                    PlayerCharacterRow(playerId = playerId, characterId = roleId)
                }
            )
        }

        val selected = fetchSelectedCharacterIds(playerId)
        if (selected == null) {
            runCatching {
                SupabaseNet.client.postgrest["player_profile"].upsert(
                    PlayerProfileSelectionPatchRow(
                        playerId = playerId,
                        selectedCharacter1Id = DEFAULT_SELECTED_ROLE_1,
                        selectedCharacter2Id = DEFAULT_SELECTED_ROLE_2
                    )
                )
            }
        }
    }

    suspend fun fetchOwnedCharacterIds(playerId: String): List<String> {
        return runCatching {
            SupabaseNet.client.postgrest["player_characters"].select {
                filter { eq("player_id", playerId) }
            }.decodeList<PlayerCharacterRow>()
                .map { it.characterId }
                .distinct()
        }.getOrDefault(emptyList())
    }

    suspend fun fetchSelectedCharacterIds(playerId: String): Pair<String, String>? {
        return runCatching {
            SupabaseNet.client.postgrest["player_profile"].select {
                filter { eq("player_id", playerId) }
            }.decodeSingle<PlayerProfileRow>()
        }.getOrNull()?.let { profile ->
            val first = profile.selectedCharacter1Id
            val second = profile.selectedCharacter2Id
            if (first.isNullOrBlank() || second.isNullOrBlank()) {
                null
            } else {
                first to second
            }
        }
    }

    suspend fun saveSelectedCharacterIds(playerId: String, role1Id: String, role2Id: String) {
        val ids = listOf(role1Id, role2Id).distinct()
        SupabaseNet.client.postgrest["player_characters"].upsert(
            ids.map { id -> PlayerCharacterRow(playerId = playerId, characterId = id) }
        )
        SupabaseNet.client.postgrest["player_profile"].upsert(
            PlayerProfileSelectionPatchRow(
                playerId = playerId,
                selectedCharacter1Id = role1Id,
                selectedCharacter2Id = role2Id
            )
        )
    }

    suspend fun addOwnedCharacterId(playerId: String, roleId: String) {
        if (playerId.isBlank() || roleId.isBlank()) return
        runCatching {
            SupabaseNet.client.postgrest["player_characters"].upsert(
                PlayerCharacterRow(playerId = playerId, characterId = roleId)
            )
        }
    }

    private companion object {
        const val DEFAULT_SELECTED_ROLE_1 = "swordsman"
        const val DEFAULT_SELECTED_ROLE_2 = "mage"
        val DEFAULT_OWNED_ROLE_IDS = listOf("swordsman", "mage", "priest")
    }
}
