package com.example.cards.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cards.data.repository.GameRepository
import com.example.cards.game.engine.GameAction
import com.example.cards.game.engine.GameEngine
import com.example.cards.game.model.CardDefinition
import com.example.cards.game.model.CharacterTemplate
import com.example.cards.game.model.GameRoomState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 对战页面 ViewModel（MVVM 中的 VM 层）。
 * 负责对战状态轮询、动作提交辅助、玩家资料缓存。
 */
class GameViewModel(
    private val repository: GameRepository = GameRepository()
) : ViewModel() {

    private val _roomState = MutableLiveData<GameRoomState?>()
    val roomState: LiveData<GameRoomState?> = _roomState

    private val _syncMessage = MutableLiveData<String>()
    val syncMessage: LiveData<String> = _syncMessage

    // 资料缓存变更序号，用于通知界面刷新用户名/头像。
    private val _metaVersion = MutableLiveData(0)
    val metaVersion: LiveData<Int> = _metaVersion

    private val nameCache = mutableMapOf<String, String>()
    private val avatarCache = mutableMapOf<String, String?>()
    private val loadingMeta = mutableSetOf<String>()

    private var pollingJob: Job? = null
    private var pollingRoomId: String? = null

    fun primeSelfMeta(playerId: String, username: String?, avatarBase64: String?) {
        if (playerId.isBlank()) return
        nameCache[playerId] = username?.takeIf { it.isNotBlank() } ?: playerId
        avatarCache[playerId] = avatarBase64
        bumpMetaVersion()
    }

    fun startPolling(roomId: String) {
        if (roomId.isBlank()) return
        if (pollingRoomId == roomId && pollingJob?.isActive == true) return

        stopPolling()
        pollingRoomId = roomId
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val state = repository.pullRoomState(roomId)
                if (state == null) {
                    if (_roomState.value == null) {
                        _syncMessage.postValue("正在连接房间...")
                    }
                    delay(700L)
                    continue
                }

                _roomState.postValue(state)
                state.players.forEach { warmupPlayerMeta(it.playerId) }
                delay(650L)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    suspend fun pullLatestState(roomId: String): GameRoomState? {
        return repository.pullRoomState(roomId)
    }

    suspend fun pushState(state: GameRoomState, operatorPlayerId: String): Boolean {
        return repository.pushRoomState(state, operatorPlayerId)
    }

    fun reduceState(state: GameRoomState, action: GameAction): GameEngine.EngineResult {
        return repository.reduceState(state, action)
    }

    fun getCharacterTemplate(templateId: String): CharacterTemplate {
        return repository.getCharacterTemplate(templateId)
    }

    fun getCardById(cardId: String): CardDefinition? {
        return repository.getCardById(cardId)
    }

    fun findCardByName(cardName: String): CardDefinition? {
        return repository.findCardByName(cardName)
    }

    fun displayName(playerId: String): String {
        return nameCache[playerId] ?: playerId
    }

    fun avatarBase64(playerId: String): String? {
        return avatarCache[playerId]
    }

    fun warmupPlayerMeta(playerId: String) {
        if (playerId.isBlank()) return
        if (loadingMeta.contains(playerId)) return

        val hasName = nameCache.containsKey(playerId)
        val hasAvatar = avatarCache.containsKey(playerId)
        if (hasName && hasAvatar) return

        loadingMeta.add(playerId)
        viewModelScope.launch {
            try {
                if (!nameCache.containsKey(playerId)) {
                    nameCache[playerId] = repository.fetchUsername(playerId)
                }
                if (!avatarCache.containsKey(playerId)) {
                    avatarCache[playerId] = repository.fetchAvatarBase64(playerId)
                }
            } catch (_: Throwable) {
                // 拉取资料失败不影响主流程，保持静默重试即可。
            } finally {
                loadingMeta.remove(playerId)
            }
            bumpMetaVersion()
        }
    }

    fun currentSelfMeta(playerId: String): Pair<String?, String?> {
        return nameCache[playerId] to avatarCache[playerId]
    }

    private fun bumpMetaVersion() {
        _metaVersion.value = (_metaVersion.value ?: 0) + 1
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
