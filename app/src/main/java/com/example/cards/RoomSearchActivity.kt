package com.example.cards

import android.content.Intent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.example.cards.auth.AuthRepository
import com.example.cards.base.BaseActivity
import com.example.cards.data.SupabaseNet
import com.example.cards.databinding.ActivityRoomSearchBinding
import com.example.cards.game.engine.GameEngine
import com.example.cards.game.model.GameRoomState
import com.example.cards.game.model.GameStatus
import com.example.cards.game.repo.DefaultCardRepository
import com.example.cards.game.repo.DefaultCharacterRepository
import com.example.cards.game.repo.PlayerCharacterCloudRepository
import com.example.cards.game.sync.RoomExitCoordinator
import com.example.cards.game.sync.GameRoomSyncRepository
import com.example.cards.game.sync.RoomStateSyncRow
import com.example.cards.session.SessionManager
import com.example.cards.util.AvatarCodec
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RoomSearchActivity : BaseActivity<ActivityRoomSearchBinding>() {

    private lateinit var sessionManager: SessionManager

    private val normalSet = ConstraintSet()
    private val roomSet = ConstraintSet()

    private val authRepository = AuthRepository()
    private val syncRepository = GameRoomSyncRepository()
    private val cloudRepository = PlayerCharacterCloudRepository()
    private val roomExitCoordinator = RoomExitCoordinator()
    private val gameEngine = GameEngine(DefaultCharacterRepository(), DefaultCardRepository())
    private val json = Json { ignoreUnknownKeys = true }

    private var roomMode = false
    private var activeRoomId: String? = null
    private var currentOwnerId: String? = null
    private var currentGuestId: String? = null

    private var pollingJob: Job? = null
    private var enteringMatch = false
    private var leavingRoom = false
    private var handledQuickReenter = false

    private val nameCache = mutableMapOf<String, String>()
    private val avatarCache = mutableMapOf<String, String?>()

    override fun initUI() {
        sessionManager = SessionManager(this)

        val playerId = sessionManager.getPlayerId()
        if (playerId.isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        prepareConstraintSets()
        showSearchTips("查找结果")

        mBinding.ivBack.setOnClickListener { finish() }
        mBinding.btnSearch.setOnClickListener { searchRoom() }
        mBinding.btnJoin.setOnClickListener { joinRoom() }
        mBinding.btnCreateRoom.setOnClickListener { createRoom() }
        mBinding.btnRoomAction.setOnClickListener { onRoomActionButtonClick() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (roomMode) {
                    val roomId = activeRoomId.orEmpty()
                    if (roomId.isNotBlank()) {
                        leaveRoom(roomId)
                        return
                    }
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        tryQuickReenterFromIntent()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }

    private fun prepareConstraintSets() {
        normalSet.clone(mBinding.roomRoot)
        roomSet.clone(mBinding.roomRoot)

        roomSet.setVisibility(R.id.et_room_id, View.GONE)
        roomSet.setVisibility(R.id.v_room_line, View.GONE)
        roomSet.setVisibility(R.id.btn_search, View.GONE)
        roomSet.setVisibility(R.id.btn_create_room, View.GONE)
        roomSet.setVisibility(R.id.btn_join, View.GONE)
        roomSet.setVisibility(R.id.iv_back, View.GONE)
        roomSet.setVisibility(R.id.btn_room_action, View.VISIBLE)

        roomSet.clear(R.id.panel_result, ConstraintSet.START)
        roomSet.clear(R.id.panel_result, ConstraintSet.END)
        roomSet.clear(R.id.panel_result, ConstraintSet.TOP)
        roomSet.clear(R.id.panel_result, ConstraintSet.BOTTOM)
        roomSet.connect(R.id.panel_result, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
        roomSet.connect(R.id.panel_result, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
        roomSet.connect(R.id.panel_result, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0)
        roomSet.connect(R.id.panel_result, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
        roomSet.constrainWidth(R.id.panel_result, ConstraintSet.MATCH_CONSTRAINT)
        roomSet.constrainHeight(R.id.panel_result, ConstraintSet.MATCH_CONSTRAINT)
    }

    private fun searchRoom() {
        val roomId = mBinding.etRoomId.text?.toString().orEmpty().trim()
        if (roomId.isEmpty()) {
            showSearchTips("查找结果\n请输入房间号")
            return
        }

        lifecycleScope.launch {
            showSearchTips("查找结果\n正在查找...")

            val row = fetchRoom(roomId)
            if (row == null) {
                showSearchTips("查找结果\n未找到房间：$roomId")
                return@launch
            }

            if (decodeGameState(row) != null) {
                showSearchTips("查找结果\n房间号：$roomId\n该房间对局已开始")
                return@launch
            }

            val waiting = decodeWaitingState(row) ?: WaitingRoomState(ownerId = row.lastActionBy)
            val ownerName = getUsername(waiting.ownerId)
            val status = if (waiting.guestId.isNullOrBlank()) "可加入" else "已有人加入"
            showSearchTips("查找结果\n房间号：$roomId\n房主：$ownerName\n状态：$status")
        }
    }

    private fun tryQuickReenterFromIntent() {
        if (handledQuickReenter) return

        val roomId = intent.getStringExtra("room_id").orEmpty().trim()
        if (roomId.isBlank()) return

        handledQuickReenter = true
        val fromResult = intent.getBooleanExtra("from_result", false)
        mBinding.etRoomId.setText(roomId)

        lifecycleScope.launch {
            quickEnterRoom(roomId, fromResult)
        }
    }

    private suspend fun quickEnterRoom(roomId: String, fromResult: Boolean) {
        val me = sessionManager.getPlayerId().orEmpty()
        if (me.isBlank()) return

        showSearchTips(
            if (fromResult) {
                "\u67E5\u627E\u7ED3\u679C\n\u6B63\u5728\u8FD4\u56DE\u623F\u95F4..."
            } else {
                "\u67E5\u627E\u7ED3\u679C\n\u6B63\u5728\u8FDB\u5165\u623F\u95F4..."
            }
        )

        val row = fetchRoom(roomId)
        if (row == null) {
            showSearchTips("\u67E5\u627E\u7ED3\u679C\n\u623F\u95F4\u4E0D\u5B58\u5728\uFF1A$roomId")
            return
        }

        val gameState = decodeGameState(row)
            if (gameState != null) {
                if (fromResult && gameState.status == GameStatus.FINISHED && gameState.players.size >= 2) {
                    val leftSet = gameState.leftPlayerIds.toSet()
                    val remained = gameState.players.map { it.playerId }.filterNot { it in leftSet }
                    val ownerId = remained.firstOrNull() ?: gameState.players.first().playerId
                    val guestId = remained.getOrNull(1)
                    val waiting = WaitingRoomState(ownerId = ownerId, guestId = guestId)
                    val resetRow = row.copy(
                        version = row.version + 1,
                    stateJson = json.encodeToString(WaitingRoomState.serializer(), waiting),
                    lastActionBy = me
                )
                val resetOk = runCatching {
                    SupabaseNet.client.postgrest["game_room_state"].upsert(resetRow)
                }.isSuccess

                if (!resetOk) {
                    showSearchTips("\u67E5\u627E\u7ED3\u679C\n\u8FD4\u56DE\u623F\u95F4\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5")
                    return
                }

                enterRoomMode(roomId, waiting, animateMs = 240L)
                return
            }

            if (gameState.players.any { it.playerId == me }) {
                openMatchLoading(roomId)
            } else {
                showSearchTips("\u67E5\u627E\u7ED3\u679C\n\u8BE5\u623F\u95F4\u6E38\u620F\u5DF2\u5F00\u59CB")
            }
            return
        }

        val waiting = decodeWaitingState(row) ?: WaitingRoomState(ownerId = row.lastActionBy)
        when {
            waiting.ownerId == me || waiting.guestId == me -> {
                enterRoomMode(roomId, waiting, animateMs = 240L)
            }

            waiting.guestId.isNullOrBlank() -> {
                val joined = waiting.copy(guestId = me)
                val joinedRow = row.copy(
                    version = row.version + 1,
                    stateJson = json.encodeToString(WaitingRoomState.serializer(), joined),
                    lastActionBy = me
                )
                val ok = runCatching {
                    SupabaseNet.client.postgrest["game_room_state"].upsert(joinedRow)
                }.isSuccess

                if (!ok) {
                    showSearchTips("\u67E5\u627E\u7ED3\u679C\n\u52A0\u5165\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5")
                    return
                }

                enterRoomMode(roomId, joined, animateMs = 240L)
            }

            else -> {
                showSearchTips("\u67E5\u627E\u7ED3\u679C\n\u623F\u95F4\u5DF2\u6EE1")
            }
        }
    }

    private fun createRoom() {
        if (roomMode) return

        val playerId = sessionManager.getPlayerId().orEmpty()
        if (playerId.isBlank()) {
            showSearchTips("请先登录")
            return
        }

        val input = mBinding.etRoomId.text?.toString().orEmpty().trim()
        val roomId = if (input.isBlank()) randomRoomId() else input
        mBinding.etRoomId.setText(roomId)

        lifecycleScope.launch {
            showSearchTips("查找结果\n正在创建房间...")

            val exists = fetchRoom(roomId) != null
            if (exists) {
                showSearchTips("查找结果\n房间号已存在：$roomId\n请换一个房间号")
                return@launch
            }

            val waitingState = WaitingRoomState(ownerId = playerId)
            val row = RoomStateSyncRow(
                roomId = roomId,
                version = 1,
                stateJson = json.encodeToString(WaitingRoomState.serializer(), waitingState),
                lastActionBy = playerId
            )

            val ok = runCatching {
                SupabaseNet.client.postgrest["game_room_state"].insert(row)
            }.isSuccess

            if (!ok) {
                showSearchTips("查找结果\n创建失败，请稍后重试")
                return@launch
            }

            enterRoomMode(roomId, waitingState, animateMs = 260L)
        }
    }

    private fun joinRoom() {
        if (roomMode) return

        val input = mBinding.etRoomId.text?.toString().orEmpty().trim()
        if (input.isBlank()) {
            showSearchTips("查找结果\n请先输入房间号")
            return
        }

        lifecycleScope.launch {
            showSearchTips("查找结果\n正在加入房间...")

            val me = sessionManager.getPlayerId().orEmpty()
            val row = fetchRoom(input)
            if (row == null) {
                showSearchTips("查找结果\n房间不存在：$input")
                return@launch
            }

            val startedState = decodeGameState(row)
            if (startedState != null && startedState.players.size >= 2) {
                if (startedState.players.any { it.playerId == me }) {
                    openMatchLoading(input)
                } else {
                    showSearchTips("查找结果\n该房间游戏已开始")
                }
                return@launch
            }

            val waiting = decodeWaitingState(row) ?: WaitingRoomState(ownerId = row.lastActionBy)
            if (waiting.ownerId == me) {
                enterRoomMode(input, waiting, animateMs = 300L)
                return@launch
            }

            if (!waiting.guestId.isNullOrBlank() && waiting.guestId != me) {
                showSearchTips("查找结果\n房间已满")
                return@launch
            }

            val joined = waiting.copy(guestId = me)
            val joinedRow = row.copy(
                version = row.version + 1,
                stateJson = json.encodeToString(WaitingRoomState.serializer(), joined),
                lastActionBy = me
            )

            val ok = runCatching {
                SupabaseNet.client.postgrest["game_room_state"].upsert(joinedRow)
            }.isSuccess

            if (!ok) {
                showSearchTips("查找结果\n加入失败，请重试")
                return@launch
            }

            enterRoomMode(input, joined, animateMs = 300L)
        }
    }

    private fun onRoomActionButtonClick() {
        val me = sessionManager.getPlayerId().orEmpty()
        val roomId = activeRoomId.orEmpty()
        if (roomId.isBlank() || me.isBlank()) return

        val isOwner = currentOwnerId == me
        val hasGuest = !currentGuestId.isNullOrBlank()

        if (isOwner && hasGuest) {
            startGameAsOwner(roomId)
        } else {
            leaveRoom(roomId)
        }
    }

    private fun startGameAsOwner(roomId: String) {
        val ownerId = sessionManager.getPlayerId().orEmpty()
        val guestId = currentGuestId.orEmpty()
        if (ownerId.isBlank() || guestId.isBlank()) {
            mBinding.tvRoomCenter.text = "房间号：$roomId\n等待玩家加入..."
            return
        }

        mBinding.btnRoomAction.isEnabled = false
        mBinding.tvRoomCenter.text = "房间号：$roomId\n正在初始化游戏..."

        lifecycleScope.launch {
            runCatching {
                cloudRepository.ensureDefaultState(ownerId)
                cloudRepository.ensureDefaultState(guestId)

                val ownerSelected = cloudRepository.fetchSelectedCharacterIds(ownerId) ?: ("swordsman" to "mage")
                val guestSelected = cloudRepository.fetchSelectedCharacterIds(guestId) ?: ("swordsman" to "mage")

                val initialState = gameEngine.createInitialState(
                    roomId = roomId,
                    playerAId = ownerId,
                    playerBId = guestId,
                    playerACharacterIds = listOf(ownerSelected.first, ownerSelected.second),
                    playerBCharacterIds = listOf(guestSelected.first, guestSelected.second)
                )

                val syncResult = syncRepository.upsertState(initialState, ownerId)
                if (syncResult.isFailure) {
                    error("初始化失败，请重试")
                }
            }.onSuccess {
                openMatchLoading(roomId)
            }.onFailure {
                mBinding.btnRoomAction.isEnabled = true
                mBinding.tvRoomCenter.text = "房间号：$roomId\n启动失败：${it.message ?: "请重试"}"
            }
        }
    }

    private fun leaveRoom(roomId: String) {
        if (leavingRoom) return
        leavingRoom = true
        pollingJob?.cancel()
        mBinding.btnRoomAction.isEnabled = false

        lifecycleScope.launch {
            val me = sessionManager.getPlayerId().orEmpty()
            if (me.isNotBlank()) {
                roomExitCoordinator.leaveRoom(roomId, me)
            }
            leavingRoom = false
            exitRoomMode("查找结果")
        }
    }

    private fun enterRoomMode(roomId: String, state: WaitingRoomState, animateMs: Long) {
        roomMode = true
        activeRoomId = roomId
        currentOwnerId = state.ownerId
        currentGuestId = state.guestId

        TransitionManager.beginDelayedTransition(mBinding.roomRoot, buildRoomTransition(animateMs))
        roomSet.applyTo(mBinding.roomRoot)

        mBinding.layoutRoomMembers.visibility = View.VISIBLE
        mBinding.tvTips.visibility = View.GONE

        lifecycleScope.launch {
            renderRoomMembers(roomId, state)
        }

        updateRoomActionButton(state)
        startPollingRoom(roomId)
    }

    private fun exitRoomMode(tips: String) {
        roomMode = false
        activeRoomId = null
        currentOwnerId = null
        currentGuestId = null
        enteringMatch = false

        TransitionManager.beginDelayedTransition(mBinding.roomRoot, buildRoomTransition(220L))
        normalSet.applyTo(mBinding.roomRoot)

        showSearchTips(tips)
        mBinding.btnCreateRoom.text = "创建房间"
        mBinding.btnCreateRoom.isEnabled = true
        mBinding.btnJoin.text = "加入房间"
        mBinding.btnJoin.isEnabled = true
        mBinding.btnRoomAction.text = "取消"
        mBinding.btnRoomAction.isEnabled = true
    }

    private fun startPollingRoom(roomId: String) {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            val me = sessionManager.getPlayerId().orEmpty()
            while (isActive && roomMode && activeRoomId == roomId && !enteringMatch) {
                val row = fetchRoom(roomId)
                if (row == null) {
                    exitRoomMode("房间已关闭")
                    return@launch
                }

                val gameState = decodeGameState(row)
                if (gameState != null && gameState.players.size >= 2) {
                    if (gameState.players.any { it.playerId == me }) {
                        openMatchLoading(roomId)
                    } else {
                        exitRoomMode("房间已开始游戏")
                    }
                    return@launch
                }

                val waiting = decodeWaitingState(row) ?: WaitingRoomState(ownerId = row.lastActionBy)
                currentOwnerId = waiting.ownerId
                currentGuestId = waiting.guestId
                renderRoomMembers(roomId, waiting)
                updateRoomActionButton(waiting)

                delay(800L)
            }
        }
    }

    private fun updateRoomActionButton(waiting: WaitingRoomState) {
        val me = sessionManager.getPlayerId().orEmpty()
        val isOwner = waiting.ownerId == me
        val hasGuest = !waiting.guestId.isNullOrBlank()

        if (isOwner && hasGuest) {
            mBinding.btnRoomAction.text = "开始游戏"
            mBinding.btnRoomAction.isEnabled = true
        } else {
            mBinding.btnRoomAction.text = "取消"
            mBinding.btnRoomAction.isEnabled = true
        }
    }

    private suspend fun renderRoomMembers(roomId: String, waiting: WaitingRoomState) {
        val ownerName = getUsername(waiting.ownerId)
        val ownerAvatar = getAvatar(waiting.ownerId)
        mBinding.tvOwnerName.text = ownerName
        AvatarCodec.setAvatarOrDefault(mBinding.ivOwnerAvatar, ownerAvatar)

        val guestId = waiting.guestId
        if (guestId.isNullOrBlank()) {
            mBinding.tvGuestName.text = "等待玩家"
            AvatarCodec.setAvatarOrDefault(mBinding.ivGuestAvatar, null)
        } else {
            val guestName = getUsername(guestId)
            val guestAvatar = getAvatar(guestId)
            mBinding.tvGuestName.text = guestName
            AvatarCodec.setAvatarOrDefault(mBinding.ivGuestAvatar, guestAvatar)
        }

        val centerStatus = when {
            guestId.isNullOrBlank() -> "等待玩家加入..."
            waiting.ownerId == sessionManager.getPlayerId().orEmpty() -> "玩家已加入，点击开始游戏"
            else -> "等待房主开始游戏..."
        }
        mBinding.tvRoomCenter.text = "房间号：$roomId\n$centerStatus"
    }

    private fun openMatchLoading(roomId: String) {
        if (enteringMatch) return
        enteringMatch = true
        pollingJob?.cancel()

        startActivity(
            Intent(this@RoomSearchActivity, MatchLoadingActivity::class.java)
                .putExtra("room_id", roomId)
        )
        finish()
    }

    private fun showSearchTips(text: String) {
        mBinding.tvTips.visibility = View.VISIBLE
        mBinding.layoutRoomMembers.visibility = View.GONE
        mBinding.tvTips.text = text
    }

    private suspend fun fetchRoom(roomId: String): RoomStateSyncRow? {
        return runCatching {
            SupabaseNet.client.postgrest["game_room_state"].select {
                filter { eq("room_id", roomId) }
            }.decodeList<RoomStateSyncRow>().firstOrNull()
        }.getOrNull()
    }

    private fun decodeWaitingState(row: RoomStateSyncRow): WaitingRoomState? {
        val raw = row.stateJson.trim()
        if (raw.isBlank() || raw == "{}") {
            return WaitingRoomState(ownerId = row.lastActionBy)
        }
        return runCatching {
            json.decodeFromString(WaitingRoomState.serializer(), raw)
        }.getOrNull()
    }

    private fun decodeGameState(row: RoomStateSyncRow): GameRoomState? {
        return runCatching {
            json.decodeFromString(GameRoomState.serializer(), row.stateJson)
        }.getOrNull()
    }

    private suspend fun getUsername(playerId: String): String {
        nameCache[playerId]?.let { return it }
        val name = authRepository.fetchUsername(playerId)
        nameCache[playerId] = name
        return name
    }

    private suspend fun getAvatar(playerId: String): String? {
        if (avatarCache.containsKey(playerId)) return avatarCache[playerId]
        val avatar = authRepository.fetchProfile(playerId)?.avatarBase64
        avatarCache[playerId] = avatar
        return avatar
    }

    private fun buildRoomTransition(durationMs: Long): TransitionSet {
        val interpolator = AccelerateDecelerateInterpolator()

        val bounds = ChangeBounds().apply {
            duration = durationMs
            resizeClip = true
            this.interpolator = interpolator
        }

        val fade = Fade().apply {
            duration = (durationMs * 0.8f).toLong()
            this.interpolator = interpolator

            addTarget(R.id.iv_back)
            addTarget(R.id.et_room_id)
            addTarget(R.id.v_room_line)
            addTarget(R.id.btn_search)
            addTarget(R.id.btn_create_room)
            addTarget(R.id.btn_join)
            addTarget(R.id.btn_room_action)
            addTarget(R.id.tv_tips)
            addTarget(R.id.layout_room_members)
        }

        return TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(bounds)
            addTransition(fade)
        }
    }

    private fun randomRoomId(): String {
        return (100000..999999).random().toString()
    }
}

@Serializable
data class RoomOwnerRow(
    @SerialName("player_id")
    val playerId: String,
    val username: String
)

@Serializable
data class WaitingRoomState(
    @SerialName("phase")
    val phase: String = "waiting",
    @SerialName("owner_id")
    val ownerId: String,
    @SerialName("guest_id")
    val guestId: String? = null
)
