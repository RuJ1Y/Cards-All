package com.example.cards.session

import android.content.Context

class SessionManager(context: Context) {

    private val sp = context.getSharedPreferences("cards_session", Context.MODE_PRIVATE)

    fun getPlayerId(): String? {
        return sp.getString(KEY_PLAYER_ID, null)
    }

    fun savePlayerId(playerId: String) {
        sp.edit().putString(KEY_PLAYER_ID, playerId).apply()
    }

    fun saveCachedUsername(username: String?) {
        if (username.isNullOrBlank()) return
        sp.edit().putString(KEY_CACHED_USERNAME, username.trim()).apply()
    }

    fun getCachedUsername(): String? {
        return sp.getString(KEY_CACHED_USERNAME, null)
    }

    fun saveCachedAvatarBase64(avatarBase64: String?) {
        if (avatarBase64.isNullOrBlank()) {
            sp.edit().remove(KEY_CACHED_AVATAR_BASE64).apply()
            return
        }
        sp.edit().putString(KEY_CACHED_AVATAR_BASE64, avatarBase64).apply()
    }

    fun getCachedAvatarBase64(): String? {
        return sp.getString(KEY_CACHED_AVATAR_BASE64, null)
    }

    fun saveLobbyCharacterIds(role1Id: String, role2Id: String) {
        sp.edit()
            .putString(KEY_LOBBY_ROLE_1, role1Id)
            .putString(KEY_LOBBY_ROLE_2, role2Id)
            .apply()
    }

    fun getLobbyCharacterIds(): Pair<String, String>? {
        val role1 = sp.getString(KEY_LOBBY_ROLE_1, null)
        val role2 = sp.getString(KEY_LOBBY_ROLE_2, null)
        if (role1.isNullOrBlank() || role2.isNullOrBlank()) return null
        return role1 to role2
    }

    fun clearLobbyCharacterIds() {
        sp.edit()
            .remove(KEY_LOBBY_ROLE_1)
            .remove(KEY_LOBBY_ROLE_2)
            .apply()
    }

    fun saveOwnedCharacterIds(characterIds: Collection<String>) {
        val normalized = characterIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (normalized.isEmpty()) return
        sp.edit().putStringSet(KEY_OWNED_ROLE_IDS, normalized).apply()
    }

    fun getOwnedCharacterIds(): Set<String> {
        return sp.getStringSet(KEY_OWNED_ROLE_IDS, null)?.toSet().orEmpty()
    }

    fun ensureCharacterCacheDefaults() {
        if (getOwnedCharacterIds().isEmpty()) {
            saveOwnedCharacterIds(DEFAULT_OWNED_ROLE_IDS)
        }
        if (getLobbyCharacterIds() == null) {
            saveLobbyCharacterIds(DEFAULT_SELECTED_ROLE_1, DEFAULT_SELECTED_ROLE_2)
        }
        if (getCachedLotteryPoints() == null) {
            saveCachedLotteryPoints(DEFAULT_LOTTERY_POINTS)
        }
        if (getCachedExchangeCoins() == null) {
            saveCachedExchangeCoins(DEFAULT_EXCHANGE_COINS)
        }
    }

    fun saveCachedLotteryPoints(points: Int) {
        sp.edit().putInt(KEY_LOTTERY_POINTS, points).apply()
    }

    fun getCachedLotteryPoints(): Int? {
        return if (sp.contains(KEY_LOTTERY_POINTS)) {
            sp.getInt(KEY_LOTTERY_POINTS, DEFAULT_LOTTERY_POINTS)
        } else {
            null
        }
    }

    fun saveCachedExchangeCoins(coins: Int) {
        sp.edit().putInt(KEY_EXCHANGE_COINS, coins.coerceAtLeast(0)).apply()
    }

    fun getCachedExchangeCoins(): Int? {
        return if (sp.contains(KEY_EXCHANGE_COINS)) {
            sp.getInt(KEY_EXCHANGE_COINS, DEFAULT_EXCHANGE_COINS)
        } else {
            null
        }
    }

    fun clear() {
        sp.edit()
            .remove(KEY_PLAYER_ID)
            .remove(KEY_LOBBY_ROLE_1)
            .remove(KEY_LOBBY_ROLE_2)
            .remove(KEY_OWNED_ROLE_IDS)
            .remove(KEY_LOTTERY_POINTS)
            .remove(KEY_EXCHANGE_COINS)
            .remove(KEY_CACHED_USERNAME)
            .remove(KEY_CACHED_AVATAR_BASE64)
            .apply()
    }

    private companion object {
        const val KEY_PLAYER_ID = "player_id"
        const val KEY_CACHED_USERNAME = "cached_username"
        const val KEY_CACHED_AVATAR_BASE64 = "cached_avatar_base64"
        const val KEY_LOBBY_ROLE_1 = "lobby_role_1"
        const val KEY_LOBBY_ROLE_2 = "lobby_role_2"
        const val KEY_OWNED_ROLE_IDS = "owned_role_ids"
        const val KEY_LOTTERY_POINTS = "lottery_points"
        const val KEY_EXCHANGE_COINS = "exchange_coins"
        const val DEFAULT_SELECTED_ROLE_1 = "swordsman"
        const val DEFAULT_SELECTED_ROLE_2 = "mage"
        const val DEFAULT_LOTTERY_POINTS = 0
        const val DEFAULT_EXCHANGE_COINS = 20
        val DEFAULT_OWNED_ROLE_IDS = setOf("swordsman", "mage", "priest")
    }
}
