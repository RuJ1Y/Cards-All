package com.example.cards.auth

import com.example.cards.data.SupabaseNet
import io.github.jan.supabase.postgrest.postgrest
import java.security.MessageDigest

class AuthRepository {

    suspend fun register(usernameRaw: String, passwordRaw: String, avatarBase64: String?): Result<String> {
        return runCatching {
            val username = usernameRaw.trim()
            val password = passwordRaw.trim()
            require(username.length >= 3) { "用户名至少3位" }
            require(password.length >= 6) { "密码至少6位" }

            val existed = SupabaseNet.client.postgrest["player_account"].select {
                filter { eq("username", username) }
            }.decodeList<PlayerAccountRow>()
            require(existed.isEmpty()) { "用户名已存在" }

            val playerId = username
            val account = PlayerAccountRow(
                playerId = playerId,
                username = username,
                passwordHash = hashPassword(password)
            )

            SupabaseNet.client.postgrest["player_account"].insert(account)
            SupabaseNet.client.postgrest["player_profile"].upsert(
                PlayerProfileRow(
                    playerId = playerId,
                    avatarBase64 = avatarBase64,
                    selectedCharacter1Id = "swordsman",
                    selectedCharacter2Id = "mage"
                )
            )
            SupabaseNet.client.postgrest["player_characters"].upsert(
                listOf(
                    PlayerCharacterRow(playerId = playerId, characterId = "swordsman"),
                    PlayerCharacterRow(playerId = playerId, characterId = "mage"),
                    PlayerCharacterRow(playerId = playerId, characterId = "priest")
                )
            )
            playerId
        }
    }

    suspend fun login(usernameRaw: String, passwordRaw: String): Result<String> {
        return runCatching {
            val username = usernameRaw.trim()
            val password = passwordRaw.trim()
            require(username.isNotEmpty()) { "请输入用户名" }
            require(password.isNotEmpty()) { "请输入密码" }

            val rows = SupabaseNet.client.postgrest["player_account"].select {
                filter { eq("username", username) }
            }.decodeList<PlayerAccountRow>()

            val account = rows.firstOrNull() ?: error("账号不存在")
            val inputHash = hashPassword(password)
            require(inputHash == account.passwordHash) { "密码错误" }
            account.playerId
        }
    }

    suspend fun fetchProfile(playerId: String): PlayerProfileRow? {
        return runCatching {
            SupabaseNet.client.postgrest["player_profile"].select {
                filter { eq("player_id", playerId) }
            }.decodeSingle<PlayerProfileRow>()
        }.getOrNull()
    }

    suspend fun fetchUsername(playerId: String): String {
        return runCatching {
            SupabaseNet.client.postgrest["player_account"].select {
                filter { eq("player_id", playerId) }
            }.decodeList<PlayerAccountRow>().firstOrNull()?.username ?: playerId
        }.getOrDefault(playerId)
    }

    suspend fun updateAvatar(playerId: String, avatarBase64: String?): Result<Unit> {
        return runCatching {
            require(playerId.isNotBlank()) { "player_id is empty" }

            val currentProfile = runCatching {
                SupabaseNet.client.postgrest["player_profile"].select {
                    filter { eq("player_id", playerId) }
                }.decodeSingle<PlayerProfileRow>()
            }.getOrNull()

            val newProfile = if (currentProfile != null) {
                currentProfile.copy(avatarBase64 = avatarBase64)
            } else {
                PlayerProfileRow(
                    playerId = playerId,
                    avatarBase64 = avatarBase64,
                    selectedCharacter1Id = "swordsman",
                    selectedCharacter2Id = "mage"
                )
            }
            SupabaseNet.client.postgrest["player_profile"].upsert(newProfile)
        }
    }

    suspend fun changePassword(
        playerId: String,
        oldPasswordRaw: String,
        newPasswordRaw: String
    ): Result<Unit> {
        return runCatching {
            val oldPassword = oldPasswordRaw.trim()
            val newPassword = newPasswordRaw.trim()
            require(playerId.isNotBlank()) { "player_id is empty" }
            require(oldPassword.isNotEmpty()) { "请输入当前密码" }
            require(newPassword.length >= 6) { "新密码至少 6 位" }

            val account = SupabaseNet.client.postgrest["player_account"].select {
                filter { eq("player_id", playerId) }
            }.decodeList<PlayerAccountRow>().firstOrNull()
                ?: error("账号不存在")

            require(hashPassword(oldPassword) == account.passwordHash) { "当前密码错误" }

            val newAccount = account.copy(passwordHash = hashPassword(newPassword))
            SupabaseNet.client.postgrest["player_account"].upsert(newAccount)
        }
    }

    suspend fun saveLotteryPoints(playerId: String, points: Int): Result<Unit> {
        return runCatching {
            require(playerId.isNotBlank()) { "player_id is empty" }
            SupabaseNet.client.postgrest["player_profile"].upsert(
                PlayerProfileLotteryPatchRow(
                    playerId = playerId,
                    lotteryPoints = points.coerceAtLeast(0)
                )
            )
        }
    }

    suspend fun deleteAccount(playerId: String): Result<Unit> {
        return runCatching {
            require(playerId.isNotBlank()) { "player_id is empty" }

            // Optional cleanup tables. These might not exist in every environment.
            runCatching {
                SupabaseNet.client.postgrest["game_result"].delete {
                    filter { eq("player_a_id", playerId) }
                }
            }
            runCatching {
                SupabaseNet.client.postgrest["game_result"].delete {
                    filter { eq("player_b_id", playerId) }
                }
            }
            runCatching {
                SupabaseNet.client.postgrest["game_result"].delete {
                    filter { eq("winner_player_id", playerId) }
                }
            }
            runCatching {
                SupabaseNet.client.postgrest["game_room_state"].delete {
                    filter { eq("last_action_by", playerId) }
                }
            }

            SupabaseNet.client.postgrest["player_characters"].delete {
                filter { eq("player_id", playerId) }
            }
            SupabaseNet.client.postgrest["player_profile"].delete {
                filter { eq("player_id", playerId) }
            }
            SupabaseNet.client.postgrest["player_account"].delete {
                filter { eq("player_id", playerId) }
            }
        }
    }

    private fun hashPassword(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
