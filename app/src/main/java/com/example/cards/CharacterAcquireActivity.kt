package com.example.cards

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.example.cards.auth.AuthRepository
import com.example.cards.base.BaseActivity
import com.example.cards.databinding.ActivityCharacterAcquireBinding
import com.example.cards.databinding.DialogDrawConfirmBinding
import com.example.cards.game.model.CharacterTemplate
import com.example.cards.game.repo.DefaultCharacterRepository
import com.example.cards.game.repo.PlayerCharacterCloudRepository
import com.example.cards.session.SessionManager
import com.example.cards.util.CharacterArtResolver
import kotlinx.coroutines.launch
import kotlin.random.Random

class CharacterAcquireActivity : BaseActivity<ActivityCharacterAcquireBinding>() {

    private lateinit var sessionManager: SessionManager
    private val authRepository = AuthRepository()
    private val cloudRepository = PlayerCharacterCloudRepository()
    private val characterRepository = DefaultCharacterRepository()

    private var playerId: String = ""
    private var goldCoins: Int = 0
    private var exchangeCoins: Int = 0
    private var ownedCharacterIds: MutableSet<String> = mutableSetOf()

    private var tenDrawReveals: List<DrawReveal> = emptyList()
    private var tenDrawIndex: Int = 0

    override fun initUI() {
        sessionManager = SessionManager(this)
        sessionManager.ensureCharacterCacheDefaults()

        playerId = sessionManager.getPlayerId().orEmpty()
        if (playerId.isBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        goldCoins = sessionManager.getCachedLotteryPoints() ?: 0
        exchangeCoins = sessionManager.getCachedExchangeCoins() ?: DEFAULT_EXCHANGE_COINS
        ownedCharacterIds = sessionManager.getOwnedCharacterIds().toMutableSet()

        renderCurrencies()
        renderPreview(FEATURED_CHARACTER_ID, "Fate / saber")
        showStatus("限定角色 Fate / saber 已上架")
        updateActionHint()

        mBinding.ivBack.setOnClickListener { finish() }
        mBinding.btnDrawExchange.setOnClickListener { showActionConfirmDialog(forTenDraw = false) }
        mBinding.btnDrawTen.setOnClickListener { showActionConfirmDialog(forTenDraw = true) }
        mBinding.overlayTenDraw.setOnClickListener { onTenDrawOverlayTapped() }
        mBinding.root.clipToPadding = false
        expandTenDrawOverlayToWindow()

        lifecycleScope.launch {
            syncFromCloud()
        }
    }

    private suspend fun syncFromCloud() {
        authRepository.fetchProfile(playerId)?.let { profile ->
            goldCoins = profile.lotteryPoints.coerceAtLeast(0)
            sessionManager.saveCachedLotteryPoints(goldCoins)
        }

        val ownedFromCloud = cloudRepository.fetchOwnedCharacterIds(playerId)
        if (ownedFromCloud.isNotEmpty()) {
            ownedCharacterIds = ownedFromCloud.toMutableSet()
            sessionManager.saveOwnedCharacterIds(ownedCharacterIds)
        }

        renderCurrencies()
        updateActionHint()
    }

    /**
     * 主按钮逻辑：兑换币够时自动变为兑换；否则是抽取。
     * 十连仅在抽取模式可用。
     */
    private fun showActionConfirmDialog(forTenDraw: Boolean) {
        val candidates = buildUnlockCandidates()
        val canExchange = exchangeCoins >= EXCHANGE_COST && candidates.isNotEmpty()

        if (candidates.isEmpty()) {
            showStatus("暂无可获取新角色，等待新角色入池")
            return
        }

        if (forTenDraw && canExchange) {
            showStatus("当前为兑换模式，请使用兑换")
            return
        }

        val actionText: String
        val messageText: String

        when {
            forTenDraw -> {
                if (goldCoins < TEN_DRAW_COUNT) {
                    showStatus("金币不足，十连需要10金币")
                    return
                }
                actionText = "十连"
                messageText = "当前金币：$goldCoins    兑换币：$exchangeCoins\n确认消耗10金币进行十连抽吗？"
            }

            canExchange -> {
                actionText = "兑换"
                messageText = "当前金币：$goldCoins    兑换币：$exchangeCoins\n确认消耗150兑换币直接兑换角色吗？"
            }

            else -> {
                if (goldCoins < DRAW_COST) {
                    showStatus("金币不足")
                    return
                }
                actionText = "抽取"
                messageText = "当前金币：$goldCoins    兑换币：$exchangeCoins\n确认消耗1金币进行抽取吗？"
            }
        }

        val dialogBinding = DialogDrawConfirmBinding.inflate(layoutInflater)
        dialogBinding.tvMessage.text = messageText
        dialogBinding.btnConfirm.text = actionText

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            when {
                forTenDraw -> drawTenCharacters()
                canExchange -> exchangeCharacter(candidates)
                else -> drawCharacter(candidates)
            }
        }

        dialog.show()
        styleDialogWindow(dialog, 0.52f)
    }

    private fun styleDialogWindow(dialog: AlertDialog, widthPercent: Float) {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val width = (resources.displayMetrics.widthPixels * widthPercent).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun drawCharacter(candidates: List<CharacterTemplate>) {
        if (goldCoins < DRAW_COST) {
            showStatus("金币不足")
            return
        }

        goldCoins -= DRAW_COST
        exchangeCoins += DRAW_EXCHANGE_GAIN

        val hit = candidates.isNotEmpty() && Random.nextDouble() < DRAW_SUCCESS_RATE
        if (hit) {
            val character = candidates.random()
            grantCharacter(character, fromExchange = false)
        } else {
            persistCurrencies()
            showStatus("本次未抽中，兑换币 +1")
            renderCurrencies()
            updateActionHint()
        }
    }

    private fun exchangeCharacter(candidates: List<CharacterTemplate>) {
        if (exchangeCoins < EXCHANGE_COST || candidates.isEmpty()) {
            showStatus("兑换条件不足")
            return
        }

        val character = candidates.random()
        exchangeCoins = (exchangeCoins - EXCHANGE_COST).coerceAtLeast(0)
        grantCharacter(character, fromExchange = true)
    }

    private fun drawTenCharacters() {
        if (goldCoins < TEN_DRAW_COUNT) {
            showStatus("金币不足，十连需要10金币")
            return
        }

        val reveals = mutableListOf<DrawReveal>()
        val grantedCharacters = mutableListOf<CharacterTemplate>()

        repeat(TEN_DRAW_COUNT) {
            goldCoins -= DRAW_COST
            exchangeCoins += DRAW_EXCHANGE_GAIN

            val candidates = buildUnlockCandidates()
            val hit = candidates.isNotEmpty() && Random.nextDouble() < DRAW_SUCCESS_RATE
            if (hit) {
                val character = candidates.random()
                ownedCharacterIds += character.id
                grantedCharacters += character
                reveals += DrawReveal(character.id, "获得 ${character.name}")
            } else {
                reveals += DrawReveal(null, "未抽中")
            }
        }

        if (grantedCharacters.isNotEmpty()) {
            sessionManager.saveOwnedCharacterIds(ownedCharacterIds)
            val latest = grantedCharacters.last()
            renderPreview(latest.id, "Fate / saber")

            lifecycleScope.launch {
                grantedCharacters.forEach { character ->
                    cloudRepository.addOwnedCharacterId(playerId, character.id)
                }
            }
        }

        persistCurrencies()
        renderCurrencies()
        updateActionHint()
        showStatus("十连完成，点击屏幕逐张查看")
        showTenDrawOverlay(reveals)
    }

    private fun grantCharacter(character: CharacterTemplate, fromExchange: Boolean) {
        ownedCharacterIds += character.id
        sessionManager.saveOwnedCharacterIds(ownedCharacterIds)

        persistCurrencies()

        // 命中后直接展示立绘，不叠加结果文案。
        renderPreview(character.id, "Fate / saber")

        val action = if (fromExchange) "兑换" else "抽取"
        showStatus("${action}成功：${character.name}")
        renderCurrencies()
        updateActionHint()

        lifecycleScope.launch {
            cloudRepository.addOwnedCharacterId(playerId, character.id)
        }
    }

    private fun showTenDrawOverlay(reveals: List<DrawReveal>) {
        if (reveals.isEmpty()) return

        tenDrawReveals = reveals
        tenDrawIndex = 0
        mBinding.overlayTenDraw.visibility = View.VISIBLE
        mBinding.overlayTenDraw.alpha = 0f
        mBinding.overlayTenDraw.animate().alpha(1f).setDuration(120L).start()
        renderTenDrawReveal()
    }

    private fun onTenDrawOverlayTapped() {
        if (mBinding.overlayTenDraw.visibility != View.VISIBLE) return
        if (tenDrawReveals.isEmpty()) {
            hideTenDrawOverlay()
            return
        }

        if (tenDrawIndex >= tenDrawReveals.lastIndex) {
            hideTenDrawOverlay()
            return
        }
        animateToNextTenDrawReveal()
    }

    /**
     * 下一张动画：当前卡片先向左滑出，再让下一张从右侧滑入。
     */
    private fun animateToNextTenDrawReveal() {
        val panel = mBinding.panelTenDrawPreview
        if (panel.width <= 0) {
            tenDrawIndex += 1
            renderTenDrawReveal()
            return
        }

        panel.animate()
            .translationX(-panel.width.toFloat())
            .alpha(0f)
            .setDuration(130L)
            .withEndAction {
                tenDrawIndex += 1
                renderTenDrawReveal()
                panel.translationX = panel.width.toFloat()
                panel.alpha = 0f
                panel.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(150L)
                    .start()
            }
            .start()
    }

    private fun renderTenDrawReveal() {
        val reveal = tenDrawReveals.getOrNull(tenDrawIndex) ?: return

        mBinding.tvTenDrawIndex.text = "${tenDrawIndex + 1}/${tenDrawReveals.size}"

        val artId = CharacterArtResolver.bigDrawableId(this, reveal.characterId)
        if (reveal.characterId != null && artId != null) {
            mBinding.ivTenDrawArt.setImageResource(artId)
            mBinding.ivTenDrawArt.visibility = View.VISIBLE
            mBinding.tvTenDrawText.text = ""
        } else {
            mBinding.ivTenDrawArt.setImageDrawable(null)
            mBinding.ivTenDrawArt.visibility = View.GONE
            mBinding.tvTenDrawText.text = reveal.tipText
        }

        mBinding.tvTenDrawHint.text = if (tenDrawIndex >= tenDrawReveals.lastIndex) {
            "点击任意位置关闭"
        } else {
            "点击任意位置查看下一张"
        }
    }

    /**
     * 根布局带安全区 padding，这里把十连遮罩反向扩展出去，保证灰层全屏覆盖。
     */
    private fun expandTenDrawOverlayToWindow() {
        mBinding.root.post {
            val lp = mBinding.overlayTenDraw.layoutParams as? ConstraintLayout.LayoutParams ?: return@post
            lp.marginStart = -mBinding.root.paddingStart
            lp.topMargin = -mBinding.root.paddingTop
            lp.marginEnd = -mBinding.root.paddingEnd
            lp.bottomMargin = -mBinding.root.paddingBottom
            mBinding.overlayTenDraw.layoutParams = lp
        }
    }

    private fun hideTenDrawOverlay() {
        mBinding.overlayTenDraw.animate()
            .alpha(0f)
            .setDuration(100L)
            .withEndAction {
                mBinding.overlayTenDraw.visibility = View.GONE
                tenDrawReveals = emptyList()
                tenDrawIndex = 0
            }
            .start()
    }

    private fun persistCurrencies() {
        sessionManager.saveCachedLotteryPoints(goldCoins)
        sessionManager.saveCachedExchangeCoins(exchangeCoins)
        lifecycleScope.launch {
            authRepository.saveLotteryPoints(playerId, goldCoins)
        }
    }

    private fun buildUnlockCandidates(): List<CharacterTemplate> {
        return characterRepository.all().filter { template ->
            template.id !in INITIAL_CHARACTER_IDS && template.id !in ownedCharacterIds
        }
    }

    private fun renderCurrencies() {
        mBinding.tvGold.text = "金币：$goldCoins"
        mBinding.tvExchange.text = "兑换币：$exchangeCoins"
    }

    private fun showStatus(text: String) {
        mBinding.tvStatus.text = text
    }

    private fun renderPreview(characterId: String?, fallbackText: String) {
        val artId = CharacterArtResolver.bigDrawableId(this, characterId)
        if (artId == null) {
            mBinding.ivDisplayArt.setImageDrawable(null)
            mBinding.ivDisplayArt.visibility = View.GONE
            mBinding.tvDisplay.text = fallbackText
            return
        }

        mBinding.ivDisplayArt.setImageResource(artId)
        mBinding.ivDisplayArt.visibility = View.VISIBLE
        mBinding.tvDisplay.text = ""
    }

    private fun updateActionHint() {
        val candidates = buildUnlockCandidates()
        val canExchange = exchangeCoins >= EXCHANGE_COST && candidates.isNotEmpty()

        mBinding.btnDrawExchange.text = if (canExchange) "兑 换" else "抽 取"

        mBinding.btnDrawExchange.isEnabled = candidates.isNotEmpty() && (canExchange || goldCoins >= DRAW_COST)
        mBinding.btnDrawExchange.alpha = if (mBinding.btnDrawExchange.isEnabled) 1f else 0.45f

        mBinding.btnDrawTen.visibility = if (canExchange) View.GONE else View.VISIBLE
        mBinding.btnDrawTen.isEnabled = candidates.isNotEmpty() && goldCoins >= TEN_DRAW_COUNT
        mBinding.btnDrawTen.alpha = if (mBinding.btnDrawTen.isEnabled) 1f else 0.45f

        if (candidates.isEmpty()) {
            showStatus("暂无可获取新角色，等待新角色入池")
        }
    }

    private data class DrawReveal(
        val characterId: String?,
        val tipText: String
    )

    private companion object {
        const val FEATURED_CHARACTER_ID = "saber"
        const val DRAW_COST = 1
        const val DRAW_EXCHANGE_GAIN = 1
        const val EXCHANGE_COST = 150
        const val DRAW_SUCCESS_RATE = 0.01
        const val TEN_DRAW_COUNT = 10
        const val DEFAULT_EXCHANGE_COINS = 20

        val INITIAL_CHARACTER_IDS = setOf("swordsman", "mage", "priest")
    }
}
