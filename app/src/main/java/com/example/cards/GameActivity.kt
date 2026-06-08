package com.example.cards

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.content.Intent
import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.cards.base.BaseActivity
import com.example.cards.databinding.ActivityGameBinding
import com.example.cards.game.engine.GameAction
import com.example.cards.game.model.CardDefinition
import com.example.cards.game.model.GameRoomState
import com.example.cards.game.model.GameStatus
import com.example.cards.game.model.PlayerState
import com.example.cards.game.model.SkillDefinition
import com.example.cards.game.model.SkillType
import com.example.cards.game.model.TargetCamp
import com.example.cards.game.model.TargetSlot
import com.example.cards.game.sync.RoomExitCoordinator
import com.example.cards.session.SessionManager
import com.example.cards.ui.viewmodel.GameViewModel
import com.example.cards.util.AvatarCodec
import com.example.cards.util.CharacterArtResolver
import kotlinx.coroutines.launch

class GameActivity : BaseActivity<ActivityGameBinding>() {
    companion object {
        private const val REVIVE_CARD_ID = "c14_revive"
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var gameViewModel: GameViewModel
    private val roomExitCoordinator = RoomExitCoordinator()

    private var roomId: String = ""
    private var myPlayerId: String = ""

    private var currentState: GameRoomState? = null

    private var selectedActorIndex: Int? = null
    private var selectedEnemyTargetIndex: Int? = null
    private var selectedHandIndex = 0
    private var pendingSkillConfirmIndex: Int? = null
    private var pendingTargetAction: PendingTargetAction? = null

    private var submitting = false
    private var resultHandled = false

    private var overlayMode = OverlayMode.NONE
    private var handExpanded = false
    private var handExpandAnimating = false
    private var handCollapseAnimating = false
    private var actionHintVersion = -1
    private var actionHintText: String? = null
    private var previousRenderedState: GameRoomState? = null
    private var lastCombatFeedbackVersion = -1
    private var lastCardCastFeedbackVersion = -1
    private var enemyControlIndex: Int? = null
    private val battleLogs = mutableListOf<String>()
    private var lastLoggedVersion = -1
    private var selfTurnPulseAnimator: ValueAnimator? = null
    private var enemyTurnPulseAnimator: ValueAnimator? = null
    private var costWarnAnimator: ValueAnimator? = null
    private var statusWarnAnimator: ValueAnimator? = null
    private val argbEvaluator = ArgbEvaluator()

    override fun initUI() {
        sessionManager = SessionManager(this)
        gameViewModel = ViewModelProvider(this)[GameViewModel::class.java]

        roomId = intent.getStringExtra("room_id").orEmpty()
        myPlayerId = sessionManager.getPlayerId().orEmpty()
        if (roomId.isBlank() || myPlayerId.isBlank()) {
            finish()
            return
        }
        gameViewModel.primeSelfMeta(
            playerId = myPlayerId,
            username = sessionManager.getCachedUsername() ?: myPlayerId,
            avatarBase64 = sessionManager.getCachedAvatarBase64()
        )

        bindListeners()
        observeViewModel()
        mBinding.tvRoomState.text = "\u623f\u95f4: $roomId"
        mBinding.tvPauseMeta.text = "\u623f\u95f4: $roomId"
        mBinding.tvStatus.text = "\u7b49\u5f85\u540c\u6b65..."
        setOverlayMode(OverlayMode.NONE)
        setHandPanelExpanded(false)
        updateTurnIndicators(selfActive = false, enemyActive = false)
        applyGameRoleCardSizing()
        gameViewModel.startPolling(roomId)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyGameRoleCardSizing()
        }
    }

    override fun onStart() {
        super.onStart()
        gameViewModel.startPolling(roomId)
    }

    override fun onStop() {
        super.onStop()
        gameViewModel.stopPolling()
        stopPulseAnimation(
            mBinding.viewSelfTurnLine,
            mBinding.viewSelfTurnLineGlow,
            mBinding.viewSelfTurnGlow,
            true
        )
        stopPulseAnimation(
            mBinding.viewEnemyTurnLine,
            mBinding.viewEnemyTurnLineGlow,
            mBinding.viewEnemyTurnGlow,
            false
        )
    }

    /**
     * 闂備浇宕垫慨鎶芥⒔瀹ュ鍨傞柦妯猴級?ViewModel 闂傚倷娴囧銊╂嚄閼稿灚娅犳俊銈傚亾闁伙絽鐏氶幏鍛嫚閵夘煈娼愰柟宄版噽閸犲﹤螣鏉炴澘顥?     * Activity 闂傚倷绀侀幉锟犳偡椤栨稓顩叉繝濠傚椤╅攱绻涢幋鐐冩粓鍩€椤掍礁娴€规洩绲惧鍕沪閽樺鍔橀梻鍌欑閹诧紕鍒掔仦鍓ч檮闁哄倹顑欏鈺呮煥閺冨倹娅曟い鈺傜叀閺岀喖鎼归悷棰佹埛缂備讲鍋撳鑸靛姈閻撶喐淇婇姘变虎闁绘挻鍔欓弻宥堫檨闁稿繑绋戦湁闁搞儺鍓涘畵渚€鐓崶銊р槈闁活厽顨婇弻鈥愁吋韫囨搩娲梺鑽ゅ枂閸ㄨ崵妲愰幘璇茬＜婵炴垶鐟ョ喊宥夋⒑鐎癸附婢樻俊鐣岀磼閸屾氨效濠碘€崇埣瀹曞爼鍩￠崟顐ゅ幀 VM闂?     */
    private fun observeViewModel() {
        gameViewModel.roomState.observe(this) { state ->
            state ?: return@observe
            currentState = state
            appendBattleLog(state)
            renderState(state)
            if (state.status == GameStatus.FINISHED && !resultHandled) {
                resultHandled = true
                openResult(state)
            }
        }

        gameViewModel.syncMessage.observe(this) { msg ->
            if (currentState == null && !msg.isNullOrBlank()) {
                mBinding.tvStatus.text = msg
            }
        }

        gameViewModel.metaVersion.observe(this) {
            val (cachedName, cachedAvatar) = gameViewModel.currentSelfMeta(myPlayerId)
            if (!cachedName.isNullOrBlank()) {
                sessionManager.saveCachedUsername(cachedName)
            }
            sessionManager.saveCachedAvatarBase64(cachedAvatar)
            currentState?.let { renderState(it) }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            if (overlayMode == OverlayMode.SKILL && mBinding.layoutSkillPopup.visibility == View.VISIBLE) {
                val rect = android.graphics.Rect()
                mBinding.layoutSkillPopup.getGlobalVisibleRect(rect)
                val tapInsidePopup = rect.contains(ev.rawX.toInt(), ev.rawY.toInt())
                if (!tapInsidePopup) {
                    setOverlayMode(OverlayMode.NONE)
                    currentState?.let { renderState(it) }
                    return true
                }
            }

            if (overlayMode == OverlayMode.HAND && handExpanded && mBinding.panelHandPopup.visibility == View.VISIBLE) {
                val handRect = android.graphics.Rect()
                mBinding.panelHandPopup.getGlobalVisibleRect(handRect)
                val tapInsideHandPopup = handRect.contains(ev.rawX.toInt(), ev.rawY.toInt())
                if (!tapInsideHandPopup) {
                    setOverlayMode(OverlayMode.NONE)
                    currentState?.let { renderState(it) }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun bindListeners() {
        mBinding.btnBack.setOnClickListener { setOverlayMode(OverlayMode.PAUSE) }
        mBinding.overlayScrim.setOnClickListener {
            when (overlayMode) {
                OverlayMode.LOG -> setOverlayMode(OverlayMode.PAUSE)
                OverlayMode.PAUSE -> setOverlayMode(OverlayMode.NONE)
                OverlayMode.HAND,
                OverlayMode.SKILL -> {
                    setOverlayMode(OverlayMode.NONE)
                    setHandPanelExpanded(false)
                    mBinding.layoutSkillPopup.visibility = android.view.View.GONE
                }
                OverlayMode.NONE -> Unit
            }
        }

        mBinding.tvSelfChar1.setOnClickListener { onSelfCharacterCardClicked(0) }
        mBinding.tvSelfChar2.setOnClickListener { onSelfCharacterCardClicked(1) }
        mBinding.tvEnemyChar1.setOnClickListener { onEnemyCharacterCardClicked(0) }
        mBinding.tvEnemyChar2.setOnClickListener { onEnemyCharacterCardClicked(1) }

        mBinding.btnSkillNormal.setOnClickListener { useSkill(SkillType.NORMAL) }
        mBinding.btnSkillPower.setOnClickListener { useSkill(SkillType.POWER) }
        mBinding.btnSkillUltimate.setOnClickListener { useSkill(SkillType.ULTIMATE) }
        mBinding.tvHandCount.setOnClickListener {
            if (overlayMode == OverlayMode.HAND && handExpanded) {
                setOverlayMode(OverlayMode.NONE)
            } else {
                pendingSkillConfirmIndex = null
                pendingTargetAction = null
                selectedEnemyTargetIndex = null
                mBinding.layoutSkillPopup.visibility = android.view.View.GONE
                setHandPanelExpanded(true)
                setOverlayMode(OverlayMode.HAND)
            }
            currentState?.let { renderState(it) }
        }

        mBinding.btnNextCard.setOnClickListener {
            val state = currentState ?: return@setOnClickListener
            val me = state.players.firstOrNull { it.playerId == myPlayerId } ?: return@setOnClickListener
            if (me.handCardIds.isNotEmpty()) {
                selectedHandIndex = if (selectedHandIndex >= me.handCardIds.lastIndex) 0 else selectedHandIndex + 1
                renderState(state)
            }
        }

        mBinding.btnUseCard.setOnClickListener { useCard() }
        mBinding.btnDiscardCard.setOnClickListener { discardCard() }
        mBinding.btnEndTurn.setOnClickListener { endTurn() }

        mBinding.btnPauseResume.setOnClickListener { setOverlayMode(OverlayMode.NONE) }
        mBinding.btnPauseExit.setOnClickListener { exitRoomFromMatch() }
        mBinding.btnPauseLog.setOnClickListener {
            setOverlayMode(OverlayMode.LOG)
            renderLogPanel()
        }
        mBinding.btnPauseSurrender.setOnClickListener { surrender() }
        mBinding.btnLogClose.setOnClickListener { setOverlayMode(OverlayMode.PAUSE) }
    }

    private fun onSelfCharacterCardClicked(index: Int) {
        val pending = pendingTargetAction
        if (pending != null) {
            val me = currentState?.players?.firstOrNull { it.playerId == myPlayerId }
            val target = me?.characters?.getOrNull(index)
            val allowDefeated = pending.allowDefeatedAllyTarget()
            when (pending.targetCamp) {
                TargetCamp.ALLY,
                TargetCamp.SELF -> {
                    val available = when (pending.targetCamp) {
                        TargetCamp.SELF -> target?.isAlive == true
                        TargetCamp.ALLY -> target?.isAlive == true || allowDefeated
                        TargetCamp.ENEMY -> false
                    }
                    if (!available) {
                        mBinding.tvStatus.text = pendingTargetPromptText() ?: "\u8BF7\u9009\u62E9\u53EF\u7528\u76EE\u6807"
                        return
                    }
                    selectedActorIndex = index
                    executePendingTargetAction(selectedIndex = index)
                }

                TargetCamp.ENEMY -> {
                    mBinding.tvStatus.text = pendingTargetPromptText() ?: "\u8BF7\u5148\u9009\u62E9\u654C\u65B9\u76EE\u6807"
                }
            }
            return
        }

        selectedActorIndex = index
        pendingSkillConfirmIndex = null
        setHandPanelExpanded(false)
        mBinding.layoutSkillPopup.visibility = android.view.View.VISIBLE
        setOverlayMode(OverlayMode.SKILL)
        currentState?.let { renderState(it) }
        positionSkillPopupAboveSelectedCard()
    }

    private fun onEnemyCharacterCardClicked(index: Int) {
        val pending = pendingTargetAction
        if (pending != null) {
            if (pending.targetCamp == TargetCamp.ENEMY) {
                val enemy = currentState?.players?.firstOrNull { it.playerId != myPlayerId }
                val target = enemy?.characters?.getOrNull(index)
                if (target?.isAlive != true) {
                    mBinding.tvStatus.text = pendingTargetPromptText() ?: "\u8BF7\u9009\u62E9\u53EF\u7528\u76EE\u6807"
                    return
                }
                selectedEnemyTargetIndex = index
                executePendingTargetAction(selectedIndex = index)
            } else {
                mBinding.tvStatus.text = pendingTargetPromptText() ?: "\u8BF7\u5148\u9009\u62E9\u6211\u65B9\u76EE\u6807"
            }
            return
        }

        // \u975E\u9009\u76EE\u6807\u72B6\u6001\u4E0B\u4E0D\u9ED8\u8BA4\u9009\u4E2D\u654C\u65B9\u89D2\u8272\uFF0C\u907F\u514D\u8BEF\u5BFC\u3002
        if (selectedEnemyTargetIndex != null) {
            selectedEnemyTargetIndex = null
            currentState?.let { renderState(it) }
        }
    }

    private fun setOverlayMode(mode: OverlayMode) {
        overlayMode = mode
        if (mode == OverlayMode.PAUSE || mode == OverlayMode.LOG) {
            pendingTargetAction = null
            selectedEnemyTargetIndex = null
        }
        val fullOverlay = mode == OverlayMode.PAUSE || mode == OverlayMode.LOG
        mBinding.overlayScrim.visibility = if (fullOverlay) android.view.View.VISIBLE else android.view.View.GONE
        mBinding.panelPausePopup.visibility = if (mode == OverlayMode.PAUSE) android.view.View.VISIBLE else android.view.View.GONE
        mBinding.panelLogPopup.visibility = if (mode == OverlayMode.LOG) android.view.View.VISIBLE else android.view.View.GONE

        if (mode != OverlayMode.SKILL) {
            pendingSkillConfirmIndex = null
            mBinding.layoutSkillPopup.visibility = android.view.View.GONE
        }
        if (mode != OverlayMode.HAND) {
            setHandPanelExpanded(false)
        }
        applyBlur(mode)
    }

    private fun setHandPanelExpanded(expanded: Boolean) {
        val popupVisible = mBinding.panelHandPopup.visibility == View.VISIBLE
        if (expanded == handExpanded) {
            if (expanded && popupVisible) return
            if (!expanded && !popupVisible) return
        }
        handExpanded = expanded
        if (expanded) {
            showHandPanelWithExpandAnimation()
        } else {
            hideHandPanelWithCollapseAnimation()
        }
    }

    private fun showHandPanelWithExpandAnimation() {
        if (handExpandAnimating) return
        handExpandAnimating = true
        handCollapseAnimating = false
        val popup = mBinding.panelHandPopup
        val miniHand = mBinding.tvHandCount
        popup.animate().cancel()
        setHandPanelChildrenVisibility(View.VISIBLE)
        popup.alpha = 0f
        popup.scaleX = 1f
        popup.scaleY = 1f
        popup.translationX = 0f
        popup.translationY = 0f
        popup.visibility = View.INVISIBLE

        popup.post {
            if (!handExpanded) {
                handExpandAnimating = false
                return@post
            }
            if (popup.width <= 0 || popup.height <= 0) {
                popup.alpha = 1f
                popup.scaleX = 1f
                popup.scaleY = 1f
                popup.translationX = 0f
                popup.translationY = 0f
                miniHand.alpha = 0f
                miniHand.visibility = View.INVISIBLE
                popup.visibility = View.VISIBLE
                handExpandAnimating = false
                return@post
            }

            val (startScaleX, startScaleY, startTx, startTy) = computeHandPopupTransformFromCounter()
            miniHand.alpha = 0f
            miniHand.visibility = View.INVISIBLE
            popup.pivotX = popup.width / 2f
            popup.pivotY = popup.height / 2f
            popup.scaleX = startScaleX
            popup.scaleY = startScaleY
            popup.translationX = startTx
            popup.translationY = startTy
            popup.alpha = 0.24f
            popup.visibility = View.VISIBLE

            popup.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(210L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    handExpandAnimating = false
                }
                .start()
        }
    }

    private fun hideHandPanelWithCollapseAnimation() {
        if (handCollapseAnimating) return
        handCollapseAnimating = true
        handExpandAnimating = false
        val popup = mBinding.panelHandPopup
        val miniHand = mBinding.tvHandCount
        popup.animate().cancel()
        if (popup.visibility != View.VISIBLE) {
            setHandPanelChildrenVisibility(View.GONE)
            popup.alpha = 1f
            popup.scaleX = 1f
            popup.scaleY = 1f
            popup.translationX = 0f
            popup.translationY = 0f
            popup.visibility = View.GONE
            miniHand.visibility = View.VISIBLE
            miniHand.alpha = 1f
            handCollapseAnimating = false
            return
        }

        val (endScaleX, endScaleY, endTx, endTy) = computeHandPopupTransformFromCounter()
        popup.animate()
            .alpha(0f)
            .scaleX(endScaleX)
            .scaleY(endScaleY)
            .translationX(endTx)
            .translationY(endTy)
            .setDuration(150L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (!handExpanded) {
                    setHandPanelChildrenVisibility(View.GONE)
                    popup.visibility = View.GONE
                    popup.alpha = 1f
                    popup.scaleX = 1f
                    popup.scaleY = 1f
                    popup.translationX = 0f
                    popup.translationY = 0f
                    miniHand.visibility = View.VISIBLE
                    miniHand.alpha = 1f
                }
                handCollapseAnimating = false
            }
            .start()
    }

    private fun setHandPanelChildrenVisibility(visibility: Int) {
        mBinding.tvHandIndex.visibility = visibility
        mBinding.tvHandCardTitle.visibility = visibility
        mBinding.tvHandCardDesc.visibility = visibility
        mBinding.layoutCardNav.visibility = visibility
        mBinding.layoutCardActions.visibility = visibility
    }

    private fun computeHandPopupTransformFromCounter(): FloatArray {
        val popup = mBinding.panelHandPopup
        val anchor = mBinding.tvHandCount

        if (popup.width <= 0 || popup.height <= 0 || anchor.width <= 0 || anchor.height <= 0) {
            return floatArrayOf(1f, 1f, 0f, 0f)
        }

        val popupLoc = IntArray(2)
        val anchorLoc = IntArray(2)
        popup.getLocationOnScreen(popupLoc)
        anchor.getLocationOnScreen(anchorLoc)

        val popupCenterX = popupLoc[0] + popup.width / 2f
        val popupCenterY = popupLoc[1] + popup.height / 2f
        val anchorCenterX = anchorLoc[0] + anchor.width / 2f
        val anchorCenterY = anchorLoc[1] + anchor.height / 2f

        val scaleX = (anchor.width.toFloat() / popup.width.toFloat()).coerceIn(0.20f, 1f)
        val scaleY = (anchor.height.toFloat() / popup.height.toFloat()).coerceIn(0.20f, 1f)
        val translationX = anchorCenterX - popupCenterX
        val translationY = anchorCenterY - popupCenterY
        return floatArrayOf(scaleX, scaleY, translationX, translationY)
    }

    private fun applyBlur(mode: OverlayMode) {
        val blurEnabled = mode == OverlayMode.PAUSE || mode == OverlayMode.LOG
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val effect = if (blurEnabled) RenderEffect.createBlurEffect(14f, 14f, Shader.TileMode.CLAMP) else null
            mBinding.panelLeft.setRenderEffect(effect)
            mBinding.panelCenter.setRenderEffect(effect)
            mBinding.panelRight.setRenderEffect(effect)
            mBinding.topBar.setRenderEffect(effect)
        }

        val passiveAlpha = 0.42f
        when (mode) {
            OverlayMode.NONE,
            OverlayMode.PAUSE,
            OverlayMode.LOG -> {
                mBinding.panelLeft.alpha = 1f
                mBinding.panelCenter.alpha = 1f
                mBinding.panelRight.alpha = 1f
                mBinding.topBar.alpha = 1f
            }

            OverlayMode.SKILL -> {
                mBinding.panelLeft.alpha = passiveAlpha
                mBinding.panelCenter.alpha = 1f
                mBinding.panelRight.alpha = passiveAlpha
                mBinding.topBar.alpha = passiveAlpha
            }

            OverlayMode.HAND -> {
                mBinding.panelLeft.alpha = passiveAlpha
                mBinding.panelCenter.alpha = passiveAlpha
                mBinding.panelRight.alpha = 1f
                mBinding.topBar.alpha = passiveAlpha
            }
        }
    }

    private fun renderLogPanel() {
        val text = if (battleLogs.isEmpty()) "\u6682\u65e0\u65e5\u5fd7" else battleLogs.joinToString("\n")
        mBinding.tvLogContent.text = text
    }

    private fun exitRoomFromMatch() {
        val room = roomId
        val me = myPlayerId
        if (room.isBlank() || me.isBlank()) {
            finish()
            return
        }
        mBinding.btnPauseExit.isEnabled = false
        mBinding.tvStatus.text = "\u6B63\u5728\u9000\u51FA\u623F\u95F4..."
        lifecycleScope.launch {
            roomExitCoordinator.leaveRoom(room, me)
            finish()
        }
    }

    private fun appendBattleLog(state: GameRoomState) {
        if (state.version == lastLoggedVersion) return
        lastLoggedVersion = state.version
        val actionName = state.lastActionName ?: "\u7b49\u5f85\u73a9\u5bb6\u64cd\u4f5c"
        battleLogs.add("T${state.turnNumber} V${state.version}  $actionName")
        if (battleLogs.size > 120) {
            battleLogs.removeAt(0)
        }
    }

    private fun surrender() {
        val state = currentState ?: return
        if (state.status != GameStatus.PLAYING || submitting) {
            setOverlayMode(OverlayMode.NONE)
            return
        }

        val enemyId = state.players.firstOrNull { it.playerId != myPlayerId }?.playerId ?: return
        val nextState = state.copy(
            status = GameStatus.FINISHED,
            winnerPlayerId = enemyId,
            version = state.version + 1,
            lastActionName = "${displayName(myPlayerId)} \u6295\u964d"
        )

        submitting = true
        lifecycleScope.launch {
            val pushed = gameViewModel.pushState(nextState, myPlayerId)
            submitting = false
            if (!pushed) {
                mBinding.tvStatus.text = "\u6295\u964d\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
                return@launch
            }

            currentState = nextState
            appendBattleLog(nextState)
            setOverlayMode(OverlayMode.NONE)
            renderState(nextState)
            if (!resultHandled) {
                resultHandled = true
                openResult(nextState)
            }
        }
    }

    private fun useSkill(skillType: SkillType) {
        val state = currentState ?: run {
            mBinding.tvStatus.text = "\u623F\u95F4\u72B6\u6001\u672A\u5C31\u7EEA"
            return
        }
        val me = state.players.firstOrNull { it.playerId == myPlayerId } ?: run {
            mBinding.tvStatus.text = "\u73A9\u5BB6\u6570\u636E\u5F02\u5E38"
            return
        }
        val actorIndex = selectedActorIndex ?: run {
            mBinding.tvStatus.text = "\u8BF7\u5148\u9009\u62E9\u6211\u65B9\u89D2\u8272"
            return
        }
        val actor = me.characters.getOrNull(actorIndex) ?: run {
            mBinding.tvStatus.text = "\u89D2\u8272\u6570\u636E\u5F02\u5E38"
            return
        }
        if (!actor.isAlive) {
            mBinding.tvStatus.text = "\u8BE5\u89D2\u8272\u5DF2\u88AB\u51FB\u8D25"
            return
        }

        val template = gameViewModel.getCharacterTemplate(actor.templateId)
        val skill = when (skillType) {
            SkillType.NORMAL -> template.normalSkill
            SkillType.POWER -> template.powerSkill
            SkillType.ULTIMATE -> template.ultimateSkill
            SkillType.CARD -> return
        }

        closeActionPanels(clearPendingTarget = false)

        if (requiresManualTargetSelection(skill)) {
            beginPendingTargetAction(
                PendingTargetAction.Skill(
                    skillType = skillType,
                    actorIndex = actorIndex,
                    skillName = skill.name,
                    targetCamp = skill.targetCamp
                )
            )
            return
        }

        runAction("\u91ca\u653e\u6280\u80fd") { _, latestMe, latestEnemy ->
            val latestActor = latestMe.characters.getOrNull(actorIndex) ?: return@runAction null
            if (!latestActor.isAlive) {
                mBinding.tvStatus.text = "\u8BE5\u89D2\u8272\u5DF2\u88AB\u51FB\u8D25"
                return@runAction null
            }

            val latestTemplate = gameViewModel.getCharacterTemplate(latestActor.templateId)
            val latestSkill = when (skillType) {
                SkillType.NORMAL -> latestTemplate.normalSkill
                SkillType.POWER -> latestTemplate.powerSkill
                SkillType.ULTIMATE -> latestTemplate.ultimateSkill
                SkillType.CARD -> return@runAction null
            }

            val (targetPlayerId, targetCharacterIndex) = resolveTargetForSkill(
                skill = latestSkill,
                me = latestMe,
                enemy = latestEnemy,
                actorIndex = actorIndex,
                selectedTargetIndex = null,
                requireExplicitSelection = false
            ) ?: return@runAction null

            GameAction.UseCharacterSkill(
                actorPlayerId = myPlayerId,
                actorCharacterIndex = actorIndex,
                skillType = skillType,
                targetPlayerId = targetPlayerId,
                targetCharacterIndex = targetCharacterIndex
            )
        }
    }

    private fun useCard() {
        val state = currentState ?: run {
            mBinding.tvStatus.text = "\u623F\u95F4\u72B6\u6001\u672A\u5C31\u7EEA"
            return
        }
        val me = state.players.firstOrNull { it.playerId == myPlayerId } ?: run {
            mBinding.tvStatus.text = "\u73A9\u5BB6\u6570\u636E\u5F02\u5E38"
            return
        }
        if (state.status != GameStatus.PLAYING || state.activePlayerId != myPlayerId || submitting) {
            mBinding.tvStatus.text = "\u73B0\u5728\u662F\u5BF9\u624B\u56DE\u5408"
            return
        }
        if (me.handCardIds.isEmpty()) {
            mBinding.tvStatus.text = "\u6CA1\u6709\u624B\u724C"
            return
        }
        if (me.cost <= 0) {
            showCostZeroWarning(me.cost)
            return
        }
        selectedHandIndex = selectedHandIndex.coerceIn(0, me.handCardIds.lastIndex)
        val cardId = me.handCardIds[selectedHandIndex]
        val card = cardById(cardId) ?: run {
            mBinding.tvStatus.text = "\u5361\u724C\u6570\u636E\u5F02\u5E38"
            return
        }
        val preferredActorIndex = resolvePreferredActorIndex(me, selectedActorIndex) ?: run {
            mBinding.tvStatus.text = "\u6CA1\u6709\u53EF\u884C\u52A8\u89D2\u8272"
            return
        }

        closeActionPanels(clearPendingTarget = false)

        if (requiresManualTargetSelection(card.skill)) {
            beginPendingTargetAction(
                PendingTargetAction.Card(
                    handIndex = selectedHandIndex,
                    preferredActorIndex = preferredActorIndex,
                    cardName = card.name,
                    allowDefeatedAllyTarget = card.id == REVIVE_CARD_ID,
                    targetCamp = card.skill.targetCamp
                )
            )
            return
        }

        runAction("\u6253\u51fa\u5361\u724c") { _, latestMe, latestEnemy ->
            val latestCardId = latestMe.handCardIds.getOrNull(selectedHandIndex) ?: run {
                mBinding.tvStatus.text = "\u624B\u724C\u5E8F\u53F7\u65E0\u6548"
                return@runAction null
            }
            val latestCard = cardById(latestCardId) ?: return@runAction null
            val actorIndex = resolvePreferredActorIndex(latestMe, selectedActorIndex) ?: run {
                mBinding.tvStatus.text = "\u6CA1\u6709\u53EF\u884C\u52A8\u89D2\u8272"
                return@runAction null
            }

            val (targetPlayerId, targetCharacterIndex) = resolveTargetForSkill(
                skill = latestCard.skill,
                me = latestMe,
                enemy = latestEnemy,
                actorIndex = actorIndex,
                selectedTargetIndex = null,
                requireExplicitSelection = false,
                allowDefeatedAllyTarget = latestCard.id == REVIVE_CARD_ID
            ) ?: return@runAction null

            GameAction.UseCard(
                actorPlayerId = myPlayerId,
                handIndex = selectedHandIndex,
                targetPlayerId = targetPlayerId,
                targetCharacterIndex = targetCharacterIndex
            )
        }
    }

    private fun beginPendingTargetAction(action: PendingTargetAction) {
        pendingTargetAction = action
        selectedEnemyTargetIndex = null
        pendingSkillConfirmIndex = null
        closeActionPanels(clearPendingTarget = false)
        mBinding.tvStatus.text = pendingTargetPromptText() ?: "\u8BF7\u9009\u62E9\u76EE\u6807"
        currentState?.let { renderState(it) }
    }

    private fun executePendingTargetAction(selectedIndex: Int) {
        val pending = pendingTargetAction ?: return
        when (pending) {
            is PendingTargetAction.Skill -> {
                val skillType = pending.skillType
                val actorIndex = pending.actorIndex
                runAction("\u91ca\u653e\u6280\u80fd") { _, me, enemy ->
                    val actor = me.characters.getOrNull(actorIndex) ?: return@runAction null
                    if (!actor.isAlive) {
                        mBinding.tvStatus.text = "\u8BE5\u89D2\u8272\u5DF2\u88AB\u51FB\u8D25"
                        return@runAction null
                    }

                    val template = gameViewModel.getCharacterTemplate(actor.templateId)
                    val skill = when (skillType) {
                        SkillType.NORMAL -> template.normalSkill
                        SkillType.POWER -> template.powerSkill
                        SkillType.ULTIMATE -> template.ultimateSkill
                        SkillType.CARD -> return@runAction null
                    }

                    val (targetPlayerId, targetCharacterIndex) = resolveTargetForSkill(
                        skill = skill,
                        me = me,
                        enemy = enemy,
                        actorIndex = actorIndex,
                        selectedTargetIndex = selectedIndex,
                        requireExplicitSelection = true
                    ) ?: return@runAction null

                    GameAction.UseCharacterSkill(
                        actorPlayerId = myPlayerId,
                        actorCharacterIndex = actorIndex,
                        skillType = skillType,
                        targetPlayerId = targetPlayerId,
                        targetCharacterIndex = targetCharacterIndex
                    )
                }
            }

            is PendingTargetAction.Card -> {
                val handIndex = pending.handIndex
                val preferredActorIndex = pending.preferredActorIndex
                runAction("\u6253\u51fa\u5361\u724c") { _, me, enemy ->
                    val cardId = me.handCardIds.getOrNull(handIndex) ?: run {
                        mBinding.tvStatus.text = "\u624B\u724C\u5E8F\u53F7\u65E0\u6548"
                        return@runAction null
                    }
                    val card = cardById(cardId) ?: return@runAction null
                    val actorIndex = resolvePreferredActorIndex(me, preferredActorIndex) ?: run {
                        mBinding.tvStatus.text = "\u6CA1\u6709\u53EF\u884C\u52A8\u89D2\u8272"
                        return@runAction null
                    }

                    val (targetPlayerId, targetCharacterIndex) = resolveTargetForSkill(
                        skill = card.skill,
                        me = me,
                        enemy = enemy,
                        actorIndex = actorIndex,
                        selectedTargetIndex = selectedIndex,
                        requireExplicitSelection = true,
                        allowDefeatedAllyTarget = pending.allowDefeatedAllyTarget
                    ) ?: return@runAction null

                    GameAction.UseCard(
                        actorPlayerId = myPlayerId,
                        handIndex = handIndex,
                        targetPlayerId = targetPlayerId,
                        targetCharacterIndex = targetCharacterIndex
                    )
                }
            }
        }
    }

    private fun discardCard() {
        closeActionPanels(clearPendingTarget = true)
        runAction("\u5f03\u724c") { _, me, _ ->
            if (me.handCardIds.isEmpty()) {
                mBinding.tvStatus.text = "\u6ca1\u6709\u624b\u724c"
                return@runAction null
            }

            selectedHandIndex = selectedHandIndex.coerceIn(0, me.handCardIds.lastIndex)
            GameAction.DiscardCard(
                actorPlayerId = myPlayerId,
                handIndex = selectedHandIndex
            )
        }
    }

    private fun endTurn() {
        closeActionPanels(clearPendingTarget = true)
        runAction("\u56de\u5408\u7ed3\u675f") { _, _, _ ->
            GameAction.EndTurn(actorPlayerId = myPlayerId)
        }
    }

    /**
     * 缂傚倸鍊搁崐鐑芥嚄閸洖绐楃€广儱娲ㄩ崡姘舵煙缂併垹鏋涚紒鈧崒鐐寸厵闁诡垱婢樿闂佹寧绋掑Λ鍐蓟閻旂厧鍨傛繛鎴灻兼竟鏇㈡⒒?闂傚倷绀佺紞濠傤焽瑜旈、鏍炊閵婏附鐝峰┑鐘绘涧椤戝懐绮婚懖鈹惧亾楠炲灝鍔氶悗姘煎枤缁瑦绻濋崶銊㈡嫽闂佸壊鍋呯粙鎴︽倶椤忓懐绡€闁靛繆妲勯懓鍧楁煛娴ｅ摜孝闁伙絾绻堝畷鐔碱敂閸℃瑧顦梻鍌欒兌椤牏鎹㈤幇顔剧煋闁圭虎鍠栫粻鏍煙閹规劦鍤欓柛鎰ㄥ亾濠碉紕鍋涢鍛偓娑掓櫊閹箖鏌嗗鍡欏幐闂侀€炲苯澧伴柟鐟板瀹曨偊宕熼鐘灃婵犵數鍋涢顓熷垔鐎电绶ら柛褎顨嗛崑澶嬬節婵犲倻澧曢柡瀣╃窔閺屻劌鈹戦崱娆忣杸闂佺绨奸崡鎶藉蓟濞戙垺鏅滈柛婵嗗椤牆顪冮妶蹇涙閻庣瑳鍛煓濠㈣埖鍔﹂弫鍌炴煕閺囥劌浜滄慨瑙勭叀濮婃椽宕ㄦ繝鍕殏缂備焦褰冮…鐑藉春閳?     */
    private fun closeActionPanels(clearPendingTarget: Boolean) {
        if (clearPendingTarget) {
            pendingTargetAction = null
        }
        selectedEnemyTargetIndex = null
        mBinding.layoutSkillPopup.visibility = View.GONE
        setOverlayMode(OverlayMode.NONE)
        setHandPanelExpanded(false)
    }

    private fun runAction(
        actionName: String,
        builder: (GameRoomState, PlayerState, PlayerState) -> GameAction?
    ) {
        if (submitting) return

        submitting = true
        mBinding.tvStatus.text = "\u6b63\u5728\u540c\u6b65: $actionName"

        lifecycleScope.launch {
            try {
                val latest = gameViewModel.pullLatestState(roomId)
                if (latest == null) {
                    mBinding.tvStatus.text = "\u540c\u6b65\u5931\u8d25\uff1a\u623f\u95f4\u72b6\u6001\u4e0d\u5b58\u5728"
                    return@launch
                }

                val me = latest.players.firstOrNull { it.playerId == myPlayerId }
                val enemy = latest.players.firstOrNull { it.playerId != myPlayerId }
                if (me == null || enemy == null) {
                    mBinding.tvStatus.text = "\u73a9\u5bb6\u6570\u636e\u5f02\u5e38"
                    return@launch
                }

                if (latest.status != GameStatus.PLAYING) {
                    mBinding.tvStatus.text = "\u5f53\u524d\u5bf9\u5c40\u4e0d\u53ef\u64cd\u4f5c"
                    return@launch
                }

                if (latest.activePlayerId != myPlayerId) {
                    mBinding.tvStatus.text = "\u73b0\u5728\u662f\u5bf9\u624b\u56de\u5408"
                    return@launch
                }

                val action = builder(latest, me, enemy) ?: return@launch

                val result = gameViewModel.reduceState(latest, action)
                val error = result.errorMessage
                if (!error.isNullOrBlank()) {
                    mBinding.tvStatus.text = error
                    return@launch
                }

                val pushed = gameViewModel.pushState(result.newState, myPlayerId)
                if (!pushed) {
                    mBinding.tvStatus.text = "\u540c\u6b65\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
                    return@launch
                }

                actionHintText = buildActionHintText(action, me)
                actionHintVersion = result.newState.version
                pendingTargetAction = null
                selectedEnemyTargetIndex = null
                currentState = result.newState
                appendBattleLog(result.newState)
                if (overlayMode == OverlayMode.LOG) {
                    renderLogPanel()
                }
                mBinding.tvStatus.text = "$actionName \u5df2\u540c\u6b65"
                renderState(result.newState)
            } catch (t: Throwable) {
                mBinding.tvStatus.text = "\u64cd\u4f5c\u5931\u8d25\uff1a${t.message ?: "\u672a\u77e5\u9519\u8bef"}"
                toast("\u64cd\u4f5c\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5")
            } finally {
                submitting = false
            }
        }
    }

    private fun renderState(state: GameRoomState) {
        val me = state.players.firstOrNull { it.playerId == myPlayerId }
        val enemy = state.players.firstOrNull { it.playerId != myPlayerId }
        if (me == null || enemy == null) {
            mBinding.tvStatus.text = "\u73a9\u5bb6\u6570\u636e\u4e0d\u5b8c\u6574"
            return
        }
        val oldState = previousRenderedState

        if (pendingTargetAction != null && (state.status != GameStatus.PLAYING || state.activePlayerId != myPlayerId)) {
            pendingTargetAction = null
            selectedEnemyTargetIndex = null
        }
        if (state.activePlayerId == myPlayerId) {
            enemyControlIndex = null
        }

        selectedActorIndex = normalizeSelectedIndex(me, selectedActorIndex)
        if (selectedActorIndex == null) {
            pendingSkillConfirmIndex = null
        }
        selectedEnemyTargetIndex = if (pendingTargetAction?.targetCamp == TargetCamp.ENEMY) {
            normalizeSelectedIndex(enemy, selectedEnemyTargetIndex)
        } else {
            null
        }
        selectedHandIndex = if (me.handCardIds.isEmpty()) 0 else selectedHandIndex.coerceIn(0, me.handCardIds.lastIndex)

        // 闂備礁鎼ˇ顐﹀疾濠婂懐鐭欓柟杈剧畱閻鏌涢埄鍐︿粻闁逞屽墯鐢€崇暦閿濆棗绶為悘鐐舵閼搭垶姊绘担鍛婃儓闁哥噥鍨跺畷鏇㈡嚌闁附婢撻梻鍌欑閹诧繝鎮烽敃鍌氬瀭鐎规洖娲ㄩ崡姘舵煛婢跺鐏嶉柡瀣叄濮婁粙宕堕埡鍐劸濡炪倖鍔х粻鎴澪涘Ο鑽ょ闁糕剝蓱閹嫰鏌￠崱妯荤闁哄矉缍侀獮瀣敇閻愯弓绱熺紓鍌欐祰妞村摜鎹㈤崟顓燁潟闁哄啫鐗嗙粻锝夋煟濡櫣浠涚€规洜鍠栧娲焻濞戞埃鏁€闂佹悶鍔岄幖顐︹€旈崘顔肩＜婵炴垶鍩冮弸娆撴⒑缂佹ɑ顥嗘い鏇嗗洤鍌ㄩ柟缁㈠枟閸婂爼鐓崶銊ь暡妞ゃ儱顦甸弻鐔兼倻閳哄倹鐝氶悗娈垮枙閸楀啿鐣峰鍡╂Ч婵炲濮甸惄顖炲蓟濞戞﹩娼╂い鎺戝€愰妶鍥ㄥ仏?婵犵數濮伴崹濂稿春閺嵮岀劷婵炲棙鎸哥壕鎸庢叏濡じ鍚痪鍙ョ矙閺岀喖骞嗚閿涘秹鏌熼悾灞解枅闁?        state.players.forEach { gameViewModel.warmupPlayerMeta(it.playerId) }

        val activeName = displayName(state.activePlayerId)
        val roomMeta = "\u623f\u95f4: $roomId   \u56de\u5408: ${state.turnNumber}   \u8f6e\u5230: $activeName"
        mBinding.tvRoomState.text = roomMeta
        mBinding.tvPauseMeta.text = roomMeta
        mBinding.btnBack.contentDescription = roomMeta
        mBinding.tvEnemyName.text = displayName(enemy.playerId)
        mBinding.tvSelfName.text = displayName(me.playerId)
        AvatarCodec.setAvatarOrDefault(mBinding.ivEnemyAvatar, gameViewModel.avatarBase64(enemy.playerId))
        AvatarCodec.setAvatarOrDefault(mBinding.ivSelfAvatar, gameViewModel.avatarBase64(me.playerId))

        renderCostText(mBinding.tvEnemyCost, enemy.cost)
        renderCostText(mBinding.tvSelfCost, me.cost)
        val canShowTurn = state.status != GameStatus.FINISHED
        val selfActive = canShowTurn && state.activePlayerId == myPlayerId
        val enemyActive = canShowTurn && state.activePlayerId == enemy.playerId
        updateTurnIndicators(selfActive, enemyActive)

        renderCharacterCard(
            mBinding.tvEnemyChar1,
            mBinding.tvEnemyChar1Ult,
            mBinding.tvEnemyChar1Hp,
            enemy,
            0,
            selectedEnemyTargetIndex == 0 || (
                state.activePlayerId != myPlayerId &&
                    pendingTargetAction == null &&
                    enemyControlIndex == 0
                ),
            isSelfCamp = false
        )
        renderCharacterCard(
            mBinding.tvEnemyChar2,
            mBinding.tvEnemyChar2Ult,
            mBinding.tvEnemyChar2Hp,
            enemy,
            1,
            selectedEnemyTargetIndex == 1 || (
                state.activePlayerId != myPlayerId &&
                    pendingTargetAction == null &&
                    enemyControlIndex == 1
                ),
            isSelfCamp = false
        )
        renderCharacterCard(
            mBinding.tvSelfChar1,
            mBinding.tvSelfChar1Ult,
            mBinding.tvSelfChar1Hp,
            me,
            0,
            selectedActorIndex == 0,
            isSelfCamp = true
        )
        renderCharacterCard(
            mBinding.tvSelfChar2,
            mBinding.tvSelfChar2Ult,
            mBinding.tvSelfChar2Hp,
            me,
            1,
            selectedActorIndex == 1,
            isSelfCamp = true
        )

        renderHand(me, enemy)
        updateCardShowcase(state)
        renderSkillButtons(state, me, enemy)
        applyPendingTargetFocusUi(me, enemy)
        if (overlayMode == OverlayMode.SKILL && mBinding.layoutSkillPopup.visibility == View.VISIBLE) {
            positionSkillPopupAboveSelectedCard()
        }

        val skillHintText = if (pendingTargetAction != null) {
            pendingTargetPromptText() ?: "\u8BF7\u9009\u62E9\u76EE\u6807"
        } else {
            val localHint = if (actionHintVersion == state.version) actionHintText else null
            if (!localHint.isNullOrBlank()) {
                localHint
            } else {
                state.lastActionName ?: "\u6280\u80FD\u540D"
            }
        }
        mBinding.tvSkillHint.text = skillHintText

        if (state.status == GameStatus.FINISHED) {
            val isWinner = state.winnerPlayerId == myPlayerId
            mBinding.tvStatus.text = if (isWinner) "\u5bf9\u5c40\u7ed3\u675f\uff1a\u4f60\u83b7\u80dc\u4e86" else "\u5bf9\u5c40\u7ed3\u675f\uff1a\u4f60\u5931\u8d25\u4e86"
        } else if (pendingTargetAction != null) {
            mBinding.tvStatus.text = pendingTargetPromptText() ?: "\u8BF7\u9009\u62E9\u76EE\u6807"
        } else if (!submitting) {
            mBinding.tvStatus.text = if (state.activePlayerId == myPlayerId) "\u8f6e\u5230\u4f60\u884c\u52a8" else "\u7b49\u5f85\u5bf9\u624b\u884c\u52a8..."
        }

        playCombatFeedback(oldState, state)
        playCardCastFeedback(oldState, state)
        previousRenderedState = state
    }

    private fun renderHand(me: PlayerState, enemy: PlayerState) {
        mBinding.tvDeckCount.text = "\u724c\u5806\n${me.deckCardIds.size}"
        mBinding.tvDiscardCount.text = "\u5f03\u724c\n${me.discardCardIds.size}"
        mBinding.tvEnemyHandCount.text = "\u5bf9\u624b\u624b\u724c\n${enemy.handCardIds.size}"
        mBinding.tvEnemyHandBadge.text = enemy.handCardIds.size.toString()
        mBinding.tvHandCount.text = me.handCardIds.size.toString()

        if (me.handCardIds.isEmpty()) {
            val indexText = "0/0"
            val titleText = "\u5f53\u524d\u624b\u724c"
            val descText = "\u6ca1\u6709\u624b\u724c"
            mBinding.tvHandIndex.text = indexText
            mBinding.tvHandCardTitle.text = titleText
            mBinding.tvHandCardDesc.text = descText
            return
        }

        selectedHandIndex = selectedHandIndex.coerceIn(0, me.handCardIds.lastIndex)
        val cardId = me.handCardIds[selectedHandIndex]
        val card = cardById(cardId)
        val indexText = "${selectedHandIndex + 1}/${me.handCardIds.size}"
        val titleText = card?.name ?: cardId
        val descText = card?.description ?: "\u672a\u77e5\u5361\u724c"

        mBinding.tvHandIndex.text = indexText
        mBinding.tvHandCardTitle.text = titleText
        mBinding.tvHandCardDesc.text = descText
    }

    private fun updateCardShowcase(state: GameRoomState) {
        val actionName = state.lastActionName
        if (actionName.isNullOrBlank()) {
            mBinding.tvLastAction.text = ""
            return
        }

        val card = gameViewModel.findCardByName(actionName)
        if (card != null) {
        mBinding.tvLastAction.text = "${card.name}\n${card.description}"
        } else {
            mBinding.tvLastAction.text = actionName
        }
    }

    private fun renderSkillButtons(state: GameRoomState, me: PlayerState, enemy: PlayerState) {
        val actor = selectedActorIndex?.let { me.characters.getOrNull(it) }
        val template = actor?.let { templateOf(it.templateId) }

        mBinding.btnSkillNormal.text = "CS\n${template?.normalSkill?.name ?: "-"}"
        mBinding.btnSkillPower.text = "SS\n${template?.powerSkill?.name ?: "-"}"
        mBinding.btnSkillUltimate.text = "FS\n${template?.ultimateSkill?.name ?: "-"}"

        val myTurn = state.status == GameStatus.PLAYING && state.activePlayerId == myPlayerId && !submitting
        val actorAlive = actor?.isAlive == true
        val handNotEmpty = me.handCardIds.isNotEmpty()

        val canNormal = myTurn && actorAlive && actor?.usedNormalThisTurn == false
        val canPower = myTurn && actorAlive && actor?.usedPowerThisTurn == false && me.cost >= 2
        val canUltimate = myTurn && actorAlive && (actor?.ultimateCharge ?: 0) >= (actor?.ultimateRequired ?: Int.MAX_VALUE)

        setEnabledStyle(mBinding.btnSkillNormal, canNormal)
        setEnabledStyle(mBinding.btnSkillPower, canPower)
        setEnabledStyle(mBinding.btnSkillUltimate, canUltimate)
        // 婵犵數鍎戠徊钘壝洪敂鐐床闁告劦浜栭崑鎾诲垂椤愶綆妫冨Δ鐘靛仦閸旀牕顕ラ崟顖氱疀妞ゆ挾鍋涢弨顓㈡⒒娴ｅ憡鍟為柣鐕佸灦瀹曞綊骞庨挊澶岋紱闂佹寧绻傞ˇ顖滅矆閸曨個褰掑礂閼测晜鍣紓浣插亾濠靛绠皊t 婵犵數鍋為崹鍫曞箰閸濄儳鐭撻柣鎴ｆ缁€鍐ㄢ攽閻樺疇澹樼紒顐㈢Ч閹綊鎼归悷鏉垮闂傚鍓﹂崜鐔煎蓟閿濆绠涙い鏃囨濞堝矂姊洪幖鐐测偓銈夊闯閿濆懐鏆﹂柕澶涢檮閸庣喖鏌ㄥ┑鍡樼┛闁糕晜顨婂娲川婵犲倻浼囧┑鐐叉嫅缁插潡鍩€椤掍胶鈻撻柡鍛█楠炲﹤鈹戠€ｎ亣鎽曢梺闈涱檧鐠侊絿妲愰敓鐘斥拺缂備焦锚閻忥綁鏌ｉ幒鐐电暢闁靛洦鍔栭幏鍛存惞閸︻厾鍔堕梻浣虹帛閼归箖顢氶鐘电焼濠㈣埖鍔栭悡娑㈡煕閵夋垵鑻▓璺衡攽閻愯泛钄兼繛澶嬫礋楠炲骞橀鑲╋紲濠碘槅鍨伴崲鍙夌?
        setEnabledStyle(mBinding.btnUseCard, myTurn && handNotEmpty)
        setEnabledStyle(mBinding.btnDiscardCard, myTurn && handNotEmpty)
        setEnabledStyle(mBinding.btnEndTurn, myTurn)
        setEnabledStyle(mBinding.btnNextCard, handNotEmpty)

        if (enemy.characters.none { it.isAlive }) {
            setEnabledStyle(mBinding.btnSkillNormal, false)
            setEnabledStyle(mBinding.btnSkillPower, false)
            setEnabledStyle(mBinding.btnSkillUltimate, false)
        }
    }

    private fun applyPendingTargetFocusUi(me: PlayerState, enemy: PlayerState) {
        val pending = pendingTargetAction
        if (pending == null || overlayMode != OverlayMode.NONE) {
            mBinding.topBar.alpha = 1f
            mBinding.panelLeft.alpha = 1f
            mBinding.panelRight.alpha = 1f
            mBinding.enemyTopRow.alpha = 1f
            mBinding.tvEnemyHandBadge.alpha = 1f
            mBinding.selfBottomRow.alpha = 1f
            resetCardScale()
            return
        }

        mBinding.topBar.alpha = 0.18f
        mBinding.panelLeft.alpha = 0.10f
        mBinding.panelRight.alpha = 0.14f

        val focusEnemy = pending.targetCamp == TargetCamp.ENEMY
        val allowDefeatedAllyTarget = pending.allowDefeatedAllyTarget()
        mBinding.enemyTopRow.alpha = if (focusEnemy) 1f else 0.30f
        mBinding.tvEnemyHandBadge.alpha = if (focusEnemy) 1f else 0.30f
        mBinding.selfBottomRow.alpha = if (focusEnemy) 0.30f else 1f

        applyTargetableStyle(
            view = mBinding.tvEnemyChar1,
            hpView = mBinding.tvEnemyChar1Hp,
            ultView = mBinding.tvEnemyChar1Ult,
            alive = enemy.characters.getOrNull(0)?.isAlive == true,
            targetable = focusEnemy,
            chosen = selectedEnemyTargetIndex == 0,
            allowDefeatedTarget = false
        )
        applyTargetableStyle(
            view = mBinding.tvEnemyChar2,
            hpView = mBinding.tvEnemyChar2Hp,
            ultView = mBinding.tvEnemyChar2Ult,
            alive = enemy.characters.getOrNull(1)?.isAlive == true,
            targetable = focusEnemy,
            chosen = selectedEnemyTargetIndex == 1,
            allowDefeatedTarget = false
        )
        applyTargetableStyle(
            view = mBinding.tvSelfChar1,
            hpView = mBinding.tvSelfChar1Hp,
            ultView = mBinding.tvSelfChar1Ult,
            alive = me.characters.getOrNull(0)?.isAlive == true,
            targetable = !focusEnemy,
            chosen = selectedActorIndex == 0,
            allowDefeatedTarget = !focusEnemy && allowDefeatedAllyTarget
        )
        applyTargetableStyle(
            view = mBinding.tvSelfChar2,
            hpView = mBinding.tvSelfChar2Hp,
            ultView = mBinding.tvSelfChar2Ult,
            alive = me.characters.getOrNull(1)?.isAlive == true,
            targetable = !focusEnemy,
            chosen = selectedActorIndex == 1,
            allowDefeatedTarget = !focusEnemy && allowDefeatedAllyTarget
        )
    }

    private fun applyTargetableStyle(
        view: TextView,
        hpView: TextView,
        ultView: TextView,
        alive: Boolean,
        targetable: Boolean,
        chosen: Boolean,
        allowDefeatedTarget: Boolean
    ) {
        val selectable = targetable && (alive || allowDefeatedTarget)
        val alpha = when {
            alive && targetable -> 1f
            !alive && selectable -> 0.82f
            !alive -> 0.34f
            else -> 0.24f
        }
        view.alpha = alpha
        hpView.alpha = alpha
        ultView.alpha = alpha

        if (selectable) {
            setCardBackground(view, R.drawable.bg_role_slot_selected)
            view.scaleX = if (chosen) 1.06f else 1.03f
            view.scaleY = if (chosen) 1.06f else 1.03f
        } else {
            view.scaleX = 1f
            view.scaleY = 1f
        }
    }

    private fun resetCardScale() {
        mBinding.tvEnemyChar1.scaleX = 1f
        mBinding.tvEnemyChar1.scaleY = 1f
        mBinding.tvEnemyChar2.scaleX = 1f
        mBinding.tvEnemyChar2.scaleY = 1f
        mBinding.tvSelfChar1.scaleX = 1f
        mBinding.tvSelfChar1.scaleY = 1f
        mBinding.tvSelfChar2.scaleX = 1f
        mBinding.tvSelfChar2.scaleY = 1f
    }

    private fun renderCharacterCard(
        view: TextView,
        ultView: TextView,
        hpView: TextView,
        player: PlayerState,
        index: Int,
        selected: Boolean,
        isSelfCamp: Boolean
    ) {
        val character = player.characters.getOrNull(index)
        if (character == null) {
            view.setTag(R.id.tag_character_id, null)
            view.text = "-"
            ultView.text = "0/0"
            hpView.text = "0"
            view.alpha = 0.45f
            view.isEnabled = false
            view.isClickable = false
            view.translationY = 0f
            ultView.translationY = 0f
            hpView.translationY = 0f
            setCardBackground(view, R.drawable.bg_role_slot)
            return
        }

        val template = templateOf(character.templateId)
        view.setTag(R.id.tag_character_id, template?.id ?: character.templateId)
        val title = template?.name ?: character.templateId
        val deadText = if (!character.isAlive) "\n\u5df2\u51fb\u8d25" else ""
        view.text = "$title$deadText"
        ultView.text = formatUltimateDots(character.ultimateCharge, character.ultimateRequired)
        hpView.text = "HP ${character.currentHp}"

        val aliveAlpha = if (character.isAlive) 1f else 0.62f
        view.alpha = aliveAlpha
        ultView.alpha = aliveAlpha
        hpView.alpha = aliveAlpha
        val canClick = canClickCharacterCard(isSelfCamp, character.isAlive)
        view.isEnabled = canClick
        view.isClickable = canClick
        view.isFocusable = canClick

        val deadOffset = dp(42f)
        val translateY = if (character.isAlive) {
            0f
        } else {
            if (isSelfCamp) deadOffset else -deadOffset
        }
        view.translationY = translateY
        ultView.translationY = translateY
        hpView.translationY = translateY

        view.scaleX = if (character.isAlive) 1f else 0.92f
        view.scaleY = if (character.isAlive) 1f else 0.92f
        val backgroundRes = if (selected && character.isAlive) {
            R.drawable.bg_role_slot_selected
        } else {
            R.drawable.bg_role_slot
        }
        setCardBackground(view, backgroundRes)
    }

    /**
     * 娓告垙椤佃鑹插崱缁熶竴鎸?3:5 甯冨眬锛屽苟鎸夊悓涓€鍙姣斾緥璐磋竟鏄剧ず锛岄伩鍏嶄笉鍚屾満鍨嬪嚭鐜扳€滃簳閮ㄩ湶鍑衡€濅笉涓€鑷淬€?     */
    private fun applyGameRoleCardSizing() {
        mBinding.panelCenter.post {
            val panelHeight = mBinding.panelCenter.height
            if (panelHeight <= 0) return@post

            val cardHeight = (panelHeight * 0.28f)
                .toInt()
                .coerceIn(dp(120f).toInt(), dp(180f).toInt())
            val cardWidth = (cardHeight * 3f / 5f).toInt().coerceAtLeast(dp(70f).toInt())

            setCardContainerSize(mBinding.containerEnemyChar1, cardWidth, cardHeight)
            setCardContainerSize(mBinding.containerEnemyChar2, cardWidth, cardHeight)
            setCardContainerSize(mBinding.containerSelfChar1, cardWidth, cardHeight)
            setCardContainerSize(mBinding.containerSelfChar2, cardWidth, cardHeight)

            // 上下两排角色卡贴边显示，只露出约 88% 以保持沉浸感。
            val edgeOffset = cardHeight * 0.12f
            mBinding.enemyTopRow.translationY = -edgeOffset
            mBinding.selfBottomRow.translationY = edgeOffset
        }
    }

    private fun setCardContainerSize(container: View, width: Int, height: Int) {
        val lp = container.layoutParams as LinearLayout.LayoutParams
        lp.width = width
        lp.height = height
        container.layoutParams = lp
    }

    private fun renderCostText(
        view: TextView,
        cost: Int,
        labelColor: Int = ContextCompat.getColor(this, R.color.ui_text_secondary),
        valueColor: Int = ContextCompat.getColor(this, R.color.black)
    ) {
        val label = "Cost:"
        val value = " $cost"
        val fullText = SpannableString(label + value)

        fullText.setSpan(
            ForegroundColorSpan(labelColor),
            0,
            label.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        fullText.setSpan(
            ForegroundColorSpan(valueColor),
            label.length,
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        fullText.setSpan(
            StyleSpan(Typeface.BOLD),
            label.length,
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        view.text = fullText
    }

    private fun showCostZeroWarning(cost: Int) {
        mBinding.tvStatus.text = "cost\u4E3A0\uFF0C\u65E0\u6CD5\u6253\u51FA\u5361\u724C"

        val normalStatus = ContextCompat.getColor(this, R.color.black)
        val warningStatus = ContextCompat.getColor(this, R.color.ui_danger)
        statusWarnAnimator?.cancel()
        statusWarnAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 110L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = 3
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val color = blendColor(normalStatus, warningStatus, progress)
                mBinding.tvStatus.setTextColor(color)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    mBinding.tvStatus.setTextColor(normalStatus)
                }
            })
            start()
        }

        val normalLabel = ContextCompat.getColor(this, R.color.ui_text_secondary)
        val normalValue = ContextCompat.getColor(this, R.color.black)
        costWarnAnimator?.cancel()
        costWarnAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 110L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = 3
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val animLabel = blendColor(normalLabel, warningStatus, progress * 0.6f)
                val animValue = blendColor(normalValue, warningStatus, progress)
                renderCostText(mBinding.tvSelfCost, cost, animLabel, animValue)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    renderCostText(mBinding.tvSelfCost, cost)
                }
            })
            start()
        }
    }

    private fun blendColor(from: Int, to: Int, fraction: Float): Int {
        return argbEvaluator.evaluate(fraction.coerceIn(0f, 1f), from, to) as Int
    }

    private fun formatUltimateDots(charge: Int, required: Int): String {
        val safeRequired = required.coerceIn(1, 7)
        val safeCharge = charge.coerceIn(0, safeRequired)
        return (0 until safeRequired).joinToString("\n") { index ->
            if (index < safeCharge) "\u25CF" else "\u25CB"
        }
    }

    private fun positionSkillPopupAboveSelectedCard() {
        val actorIndex = selectedActorIndex ?: return
        val anchorView = if (actorIndex == 0) mBinding.tvSelfChar1 else mBinding.tvSelfChar2
        val popup = mBinding.layoutSkillPopup
        val root = mBinding.root

        popup.post {
            if (popup.visibility != View.VISIBLE || popup.width == 0 || popup.height == 0) return@post

            val rootLoc = IntArray(2)
            val anchorLoc = IntArray(2)
            root.getLocationOnScreen(rootLoc)
            anchorView.getLocationOnScreen(anchorLoc)

            val popupWidth = popup.width.toFloat()
            val popupHeight = popup.height.toFloat()
            val anchorCenterXOnRoot = (anchorLoc[0] - rootLoc[0]).toFloat() + (anchorView.width / 2f)

            val sideSafe = dp(8f)
            val minX = sideSafe
            val maxX = (root.width.toFloat() - popupWidth - sideSafe).coerceAtLeast(minX)
            val targetX = anchorCenterXOnRoot - (popupWidth / 2f)
            popup.x = targetX.coerceIn(minX, maxX)

            val topSafe = dp(6f)
            val bottomSafe = dp(6f)
            val gap = dp(8f)
            val anchorTopOnRoot = (anchorLoc[1] - rootLoc[1]).toFloat()
            val targetY = anchorTopOnRoot - popupHeight - gap
            val maxY = (root.height.toFloat() - popupHeight - bottomSafe).coerceAtLeast(topSafe)
            popup.y = targetY.coerceIn(topSafe, maxY)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun updateTurnIndicators(selfActive: Boolean, enemyActive: Boolean) {
        val activeColor = ContextCompat.getColor(this, R.color.ui_turn_sage_60)
        val idleColor = ContextCompat.getColor(this, R.color.ui_turn_line_idle)

        mBinding.viewSelfTurnLine.setBackgroundColor(if (selfActive) activeColor else idleColor)
        mBinding.viewEnemyTurnLine.setBackgroundColor(if (enemyActive) activeColor else idleColor)

        if (selfActive) {
            startPulseAnimation(
                mBinding.viewSelfTurnLine,
                mBinding.viewSelfTurnLineGlow,
                mBinding.viewSelfTurnGlow,
                true
            )
        } else {
            stopPulseAnimation(
                mBinding.viewSelfTurnLine,
                mBinding.viewSelfTurnLineGlow,
                mBinding.viewSelfTurnGlow,
                true
            )
        }

        if (enemyActive) {
            startPulseAnimation(
                mBinding.viewEnemyTurnLine,
                mBinding.viewEnemyTurnLineGlow,
                mBinding.viewEnemyTurnGlow,
                false
            )
        } else {
            stopPulseAnimation(
                mBinding.viewEnemyTurnLine,
                mBinding.viewEnemyTurnLineGlow,
                mBinding.viewEnemyTurnGlow,
                false
            )
        }
    }

    private fun startPulseAnimation(
        targetLine: View,
        targetLineGlow: View,
        targetAvatarGlow: View,
        selfLine: Boolean
    ) {
        val existing = if (selfLine) selfTurnPulseAnimator else enemyTurnPulseAnimator
        if (existing != null) return

        targetLineGlow.visibility = View.VISIBLE
        targetAvatarGlow.visibility = View.VISIBLE
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 980L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { valueAnimator ->
                val progress = valueAnimator.animatedValue as Float
                targetLine.alpha = 0.62f + (0.30f * progress)
                targetLineGlow.alpha = 0.28f + (0.42f * progress)
                targetAvatarGlow.alpha = 0.18f + (0.42f * progress)
                val scale = 1f + (0.018f * progress)
                targetAvatarGlow.scaleX = scale
                targetAvatarGlow.scaleY = scale
            }
            start()
        }

        if (selfLine) {
            selfTurnPulseAnimator = animator
        } else {
            enemyTurnPulseAnimator = animator
        }
    }

    private fun stopPulseAnimation(
        targetLine: View,
        targetLineGlow: View,
        targetAvatarGlow: View,
        selfLine: Boolean
    ) {
        if (selfLine) {
            selfTurnPulseAnimator?.cancel()
            selfTurnPulseAnimator = null
        } else {
            enemyTurnPulseAnimator?.cancel()
            enemyTurnPulseAnimator = null
        }
        targetLine.alpha = 1f
        targetLineGlow.alpha = 0f
        targetAvatarGlow.alpha = 0f
        targetAvatarGlow.scaleX = 1f
        targetAvatarGlow.scaleY = 1f
    }

    private fun resolveTargetForSkill(
        skill: SkillDefinition,
        me: PlayerState,
        enemy: PlayerState,
        actorIndex: Int,
        selectedTargetIndex: Int?,
        requireExplicitSelection: Boolean,
        allowDefeatedAllyTarget: Boolean = false
    ): Pair<String, Int?>? {
        val targetPlayer = when (skill.targetCamp) {
            TargetCamp.ENEMY -> enemy
            TargetCamp.SELF,
            TargetCamp.ALLY -> me
        }

        val targetIndex = when (skill.targetSlot) {
            TargetSlot.ALL -> null
            TargetSlot.SINGLE -> when (skill.targetCamp) {
                TargetCamp.ENEMY -> {
                    val selectedEnemy = selectedTargetIndex ?: selectedEnemyTargetIndex ?: run {
                        mBinding.tvStatus.text = "\u8BF7\u5148\u9009\u62E9\u654C\u65B9\u76EE\u6807"
                        return null
                    }
                    val target = enemy.characters.getOrNull(selectedEnemy)
                    if (target == null || !target.isAlive) {
                        mBinding.tvStatus.text = "\u8BE5\u654C\u65B9\u76EE\u6807\u4E0D\u53EF\u7528"
                        return null
                    }
                    selectedEnemy
                }
                TargetCamp.SELF -> actorIndex
                TargetCamp.ALLY -> {
                    val allyIndex = when {
                        selectedTargetIndex != null -> selectedTargetIndex
                        requireExplicitSelection -> null
                        else -> resolveAliveIndex(me, actorIndex)
                    } ?: run {
                        mBinding.tvStatus.text = "\u8BF7\u5148\u9009\u62E9\u6211\u65B9\u76EE\u6807"
                        return null
                    }
                    val target = me.characters.getOrNull(allyIndex)
                    val allyUnavailable = target == null || (!target.isAlive && !allowDefeatedAllyTarget)
                    if (allyUnavailable) {
                        mBinding.tvStatus.text = "\u8BE5\u6211\u65B9\u76EE\u6807\u4E0D\u53EF\u7528"
                        return null
                    }
                    allyIndex
                }
            }
        }

        return targetPlayer.playerId to targetIndex
    }

    private fun requiresManualTargetSelection(skill: SkillDefinition): Boolean {
        return skill.targetSlot == TargetSlot.SINGLE && skill.targetCamp != TargetCamp.SELF
    }

    private fun PendingTargetAction.allowDefeatedAllyTarget(): Boolean {
        return this is PendingTargetAction.Card && allowDefeatedAllyTarget
    }

    private fun canClickCharacterCard(isSelfCamp: Boolean, isAlive: Boolean): Boolean {
        val pending = pendingTargetAction ?: return isAlive
        return when (pending.targetCamp) {
            TargetCamp.ENEMY -> !isSelfCamp && isAlive
            TargetCamp.SELF -> isSelfCamp && isAlive
            TargetCamp.ALLY -> isSelfCamp && (isAlive || pending.allowDefeatedAllyTarget())
        }
    }

    private fun pendingTargetPromptText(): String? {
        val action = pendingTargetAction ?: return null
        return when (action) {
            is PendingTargetAction.Skill -> "\u8BF7\u9009\u62E9\u6280\u80FD\u91CA\u653E\u5BF9\u8C61"
            is PendingTargetAction.Card -> "\u8BF7\u9009\u62E9\u5361\u724C\u4F5C\u7528\u5BF9\u8C61"
        }
    }

    private fun normalizeSelectedIndex(player: PlayerState, selected: Int?): Int? {
        if (selected == null) return null
        val character = player.characters.getOrNull(selected) ?: return null
        return if (character.isAlive) selected else null
    }

    private fun resolveAliveIndex(player: PlayerState, preferred: Int): Int {
        if (player.characters.isEmpty()) return 0

        val safe = preferred.coerceIn(0, player.characters.lastIndex)
        if (player.characters[safe].isAlive) return safe

        val firstAlive = player.characters.indexOfFirst { it.isAlive }
        return if (firstAlive >= 0) firstAlive else safe
    }

    private fun resolvePreferredActorIndex(player: PlayerState, preferred: Int?): Int? {
        if (player.characters.isEmpty()) return null
        val preferredIndex = preferred?.coerceIn(0, player.characters.lastIndex)
        if (preferredIndex != null && player.characters[preferredIndex].isAlive) return preferredIndex
        val firstAlive = player.characters.indexOfFirst { it.isAlive }
        if (firstAlive >= 0) return firstAlive
        return preferredIndex ?: 0
    }

    private fun templateOf(templateId: String) = runCatching {
        gameViewModel.getCharacterTemplate(templateId)
    }.getOrNull()

    private fun cardById(cardId: String): CardDefinition? = gameViewModel.getCardById(cardId)

    private fun displayName(playerId: String): String {
        return gameViewModel.displayName(playerId)
    }

    private fun setEnabledStyle(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.45f
    }

    private fun buildActionHintText(action: GameAction, me: PlayerState): String? {
        return when (action) {
            is GameAction.UseCharacterSkill -> {
                val actor = me.characters.getOrNull(action.actorCharacterIndex) ?: return null
                val template = templateOf(actor.templateId) ?: return null
                val skillName = when (action.skillType) {
                    SkillType.NORMAL -> template.normalSkill.name
                    SkillType.POWER -> template.powerSkill.name
                    SkillType.ULTIMATE -> template.ultimateSkill.name
                    SkillType.CARD -> return null
                }
                "${template.name}: $skillName"
            }

            is GameAction.UseCard -> {
                val cardId = me.handCardIds.getOrNull(action.handIndex) ?: return null
                val card = cardById(cardId) ?: return null
                "卡牌: ${card.name}"
            }

            is GameAction.DiscardCard -> "\u5F03\u724C"
            is GameAction.EndTurn -> "\u56DE\u5408\u7ED3\u675F"
        }
    }

    private fun playCombatFeedback(oldState: GameRoomState?, newState: GameRoomState) {
        if (oldState == null) return
        if (newState.version <= oldState.version) return
        if (newState.version == lastCombatFeedbackVersion) return

        val damageEvents = collectDamageEvents(oldState, newState)
        if (damageEvents.isEmpty()) {
            lastCombatFeedbackVersion = newState.version
            return
        }

        val actorPlayerId = oldState.activePlayerId
        val oldActor = oldState.players.firstOrNull { it.playerId == actorPlayerId }
        val newActor = newState.players.firstOrNull { it.playerId == actorPlayerId }
        val actorIndex = guessActorCharacterIndex(oldActor, newActor)
        if (actorIndex != null) {
            if (actorPlayerId != myPlayerId) {
                enemyControlIndex = actorIndex
            }
            characterViews(actorPlayerId, actorIndex)?.let { views ->
                playAttackFeedback(views.card, views.hp, views.ult)
            }
        }

        damageEvents.forEach { event ->
            characterViews(event.playerId, event.index)?.let { views ->
                playHitFeedback(views.card, views.hp, views.ult)
            }
        }

        val firstDamage = damageEvents.firstOrNull()
        if (firstDamage != null) {
            val attackerName = resolveCharacterName(newState, actorPlayerId, actorIndex)
            val targetName = resolveCharacterName(newState, firstDamage.playerId, firstDamage.index)
            val sideLabel = if (actorPlayerId == myPlayerId) "\u6211\u65B9" else "\u654C\u65B9"
            val effectName = newState.lastActionName ?: "\u6280\u80FD\u6548\u679C"
            mBinding.tvStatus.text =
                targetName + "\u53D7" + sideLabel + attackerName + "\u653B\u51FB-" + firstDamage.damage + "\uFF08" + effectName + "\uFF09"
        }

        lastCombatFeedbackVersion = newState.version
    }

    private fun playCardCastFeedback(oldState: GameRoomState?, newState: GameRoomState) {
        if (oldState == null) return
        if (newState.version <= oldState.version) return
        if (newState.version == lastCardCastFeedbackVersion) return

        val actorPlayerId = oldState.activePlayerId
        val oldActor = oldState.players.firstOrNull { it.playerId == actorPlayerId } ?: return
        val newActor = newState.players.firstOrNull { it.playerId == actorPlayerId } ?: return
        val actionName = newState.lastActionName ?: return

        val cardPlayed = oldActor.handCardIds.size == newActor.handCardIds.size + 1 &&
            oldActor.discardCardIds.size + 1 == newActor.discardCardIds.size &&
            actionName != "\u5F03\u724C"
        if (!cardPlayed) return

        val card = gameViewModel.findCardByName(actionName) ?: return
        val sourceView = if (actorPlayerId == myPlayerId) mBinding.tvHandCount else mBinding.tvEnemyHandBadge
        animateCardToShowcase(card.name, sourceView, mBinding.tvLastAction)
        mBinding.tvLastAction.text = "${card.name}\n${card.description}"
        lastCardCastFeedbackVersion = newState.version
    }

    private fun collectDamageEvents(
        oldState: GameRoomState,
        newState: GameRoomState
    ): List<DamageEvent> {
        val events = mutableListOf<DamageEvent>()
        newState.players.forEach { newPlayer ->
            val oldPlayer = oldState.players.firstOrNull { it.playerId == newPlayer.playerId } ?: return@forEach
            val charCount = minOf(oldPlayer.characters.size, newPlayer.characters.size)
            for (index in 0 until charCount) {
                val oldHp = oldPlayer.characters[index].currentHp
                val newHp = newPlayer.characters[index].currentHp
                if (newHp < oldHp) {
                    events += DamageEvent(
                        playerId = newPlayer.playerId,
                        index = index,
                        damage = oldHp - newHp
                    )
                }
            }
        }
        return events
    }

    private fun guessActorCharacterIndex(oldActor: PlayerState?, newActor: PlayerState?): Int? {
        if (oldActor == null || newActor == null) return null
        val size = minOf(oldActor.characters.size, newActor.characters.size)
        for (index in 0 until size) {
            val oldChar = oldActor.characters[index]
            val newChar = newActor.characters[index]
            if (!oldChar.usedNormalThisTurn && newChar.usedNormalThisTurn) return index
            if (!oldChar.usedPowerThisTurn && newChar.usedPowerThisTurn) return index
            if (oldChar.ultimateCharge > 0 && newChar.ultimateCharge == 0) return index
        }
        return null
    }

    private fun characterViews(playerId: String, index: Int): CharacterViews? {
        val isSelf = playerId == myPlayerId
        return if (isSelf) {
            when (index) {
                0 -> CharacterViews(mBinding.tvSelfChar1, mBinding.tvSelfChar1Hp, mBinding.tvSelfChar1Ult)
                1 -> CharacterViews(mBinding.tvSelfChar2, mBinding.tvSelfChar2Hp, mBinding.tvSelfChar2Ult)
                else -> null
            }
        } else {
            when (index) {
                0 -> CharacterViews(mBinding.tvEnemyChar1, mBinding.tvEnemyChar1Hp, mBinding.tvEnemyChar1Ult)
                1 -> CharacterViews(mBinding.tvEnemyChar2, mBinding.tvEnemyChar2Hp, mBinding.tvEnemyChar2Ult)
                else -> null
            }
        }
    }

    private fun resolveCharacterName(state: GameRoomState, playerId: String, index: Int?): String {
        if (index == null) return "\u89D2\u8272"
        val player = state.players.firstOrNull { it.playerId == playerId } ?: return "\u89D2\u8272"
        val character = player.characters.getOrNull(index) ?: return "\u89D2\u8272"
        return templateOf(character.templateId)?.name ?: character.templateId
    }

    private fun animateCardToShowcase(cardName: String, source: View, target: View) {
        source.post {
            if (source.width == 0 || source.height == 0 || target.width == 0 || target.height == 0) return@post

            val root = mBinding.root
            val rootLoc = IntArray(2)
            val sourceLoc = IntArray(2)
            val targetLoc = IntArray(2)
            root.getLocationOnScreen(rootLoc)
            source.getLocationOnScreen(sourceLoc)
            target.getLocationOnScreen(targetLoc)

            val chip = TextView(this).apply {
                text = cardName
                setTextColor(ContextCompat.getColor(this@GameActivity, R.color.white))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                setPadding(dp(8f).toInt(), dp(6f).toInt(), dp(8f).toInt(), dp(6f).toInt())
                background = ContextCompat.getDrawable(this@GameActivity, R.drawable.bg_btn_primary)
                alpha = 0.96f
                elevation = dp(12f)
            }

            val width = (source.width * 1.1f).toInt().coerceAtLeast(dp(68f).toInt())
            val height = (source.height * 0.62f).toInt().coerceAtLeast(dp(34f).toInt())
            root.addView(chip, android.view.ViewGroup.LayoutParams(width, height))

            val sourceCenterX = sourceLoc[0] - rootLoc[0] + source.width / 2f
            val sourceCenterY = sourceLoc[1] - rootLoc[1] + source.height / 2f
            val targetCenterX = targetLoc[0] - rootLoc[0] + target.width / 2f
            val targetCenterY = targetLoc[1] - rootLoc[1] + target.height / 2f

            chip.x = sourceCenterX - width / 2f
            chip.y = sourceCenterY - height / 2f
            chip.scaleX = 0.92f
            chip.scaleY = 0.92f

            chip.animate()
                .x(targetCenterX - width / 2f)
                .y(targetCenterY - height / 2f)
                .scaleX(1.12f)
                .scaleY(1.12f)
                .alpha(0.94f)
                .setDuration(280L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    chip.animate()
                        .alpha(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(110L)
                        .withEndAction {
                            root.removeView(chip)
                            target.animate()
                                .alpha(0.70f)
                                .setDuration(70L)
                                .withEndAction { target.animate().alpha(1f).setDuration(120L).start() }
                                .start()
                        }
                        .start()
                }
                .start()
        }
    }

    private fun playAttackFeedback(card: TextView, hp: TextView, ult: TextView) {
        val baseScaleX = card.scaleX
        val baseScaleY = card.scaleY
        card.animate().cancel()
        hp.animate().cancel()
        ult.animate().cancel()

        card.animate()
            .scaleX(baseScaleX * 1.06f)
            .scaleY(baseScaleY * 1.06f)
            .setDuration(80L)
            .withEndAction {
                card.animate()
                    .scaleX(baseScaleX)
                    .scaleY(baseScaleY)
                    .setDuration(110L)
                    .start()
            }
            .start()

        hp.animate()
            .alpha(0.62f)
            .setDuration(70L)
            .withEndAction {
                hp.animate().alpha(1f).setDuration(120L).start()
            }
            .start()
        ult.animate()
            .alpha(0.72f)
            .setDuration(70L)
            .withEndAction {
                ult.animate().alpha(1f).setDuration(120L).start()
            }
            .start()
    }

    private fun playHitFeedback(card: TextView, hp: TextView, ult: TextView) {
        val shake = dp(4f)
        card.animate().cancel()
        hp.animate().cancel()
        ult.animate().cancel()
        card.translationX = 0f

        val restoreBgRes = (card.getTag(R.id.tag_card_background_res) as? Int) ?: R.drawable.bg_role_slot
        setCardBackground(card, R.drawable.bg_role_slot_hit)

        card.animate()
            .translationX(shake)
            .setDuration(45L)
            .withEndAction {
                card.animate()
                    .translationX(-shake)
                    .setDuration(55L)
                    .withEndAction {
                        card.animate()
                            .translationX(0f)
                            .setDuration(45L)
                            .start()
                    }
                    .start()
            }
            .start()

        card.postDelayed({
            setCardBackground(card, restoreBgRes)
        }, 150L)

        hp.animate()
            .alpha(0.26f)
            .setDuration(65L)
            .withEndAction {
                hp.animate().alpha(1f).setDuration(120L).start()
            }
            .start()
        ult.animate()
            .alpha(0.40f)
            .setDuration(65L)
            .withEndAction {
                ult.animate().alpha(1f).setDuration(120L).start()
            }
            .start()
    }

    private fun setCardBackground(view: TextView, backgroundRes: Int) {
        view.setTag(R.id.tag_card_background_res, backgroundRes)
        val characterId = view.getTag(R.id.tag_character_id) as? String
        val fallbackName = view.text?.toString().orEmpty()
        CharacterArtResolver.applyRoleCardVisual(
            context = this,
            cardView = view,
            characterId = characterId,
            fallbackName = fallbackName,
            stateBackgroundRes = backgroundRes,
            preferTopCrop = true
        )
    }

    private enum class OverlayMode {
        NONE,
        SKILL,
        HAND,
        PAUSE,
        LOG
    }

    private sealed interface PendingTargetAction {
        val targetCamp: TargetCamp

        data class Skill(
            val skillType: SkillType,
            val actorIndex: Int,
            val skillName: String,
            override val targetCamp: TargetCamp
        ) : PendingTargetAction

        data class Card(
            val handIndex: Int,
            val preferredActorIndex: Int,
            val cardName: String,
            val allowDefeatedAllyTarget: Boolean,
            override val targetCamp: TargetCamp
        ) : PendingTargetAction
    }

    private data class CharacterViews(
        val card: TextView,
        val hp: TextView,
        val ult: TextView
    )

    private data class DamageEvent(
        val playerId: String,
        val index: Int,
        val damage: Int
    )

    private fun openResult(state: GameRoomState) {
        val isWin = state.winnerPlayerId == myPlayerId
        val me = state.players.firstOrNull { it.playerId == myPlayerId }
        val role1Name = me?.characters?.getOrNull(0)?.let { templateOf(it.templateId)?.name ?: it.templateId } ?: "-"
        val role2Name = me?.characters?.getOrNull(1)?.let { templateOf(it.templateId)?.name ?: it.templateId } ?: "-"
        val role1Id = me?.characters?.getOrNull(0)?.templateId.orEmpty()
        val role2Id = me?.characters?.getOrNull(1)?.templateId.orEmpty()
        val isOwner = state.players.firstOrNull()?.playerId == myPlayerId

        startActivity(
            Intent(this, ResultActivity::class.java)
                .putExtra("is_win", isWin)
                .putExtra("room_id", roomId)
                .putExtra("is_owner", isOwner)
                .putExtra("display_name", displayName(myPlayerId))
                .putExtra("avatar_base64", gameViewModel.avatarBase64(myPlayerId))
                .putExtra("role_1_name", role1Name)
                .putExtra("role_1_id", role1Id)
                .putExtra("role_2_name", role2Name)
                .putExtra("role_2_id", role2Id)
        )
        finish()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}







