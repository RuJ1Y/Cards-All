package com.example.cards

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.example.cards.auth.AuthRepository
import com.example.cards.base.BaseActivity
import com.example.cards.databinding.ActivityResultBinding
import com.example.cards.session.SessionManager
import com.example.cards.util.AvatarCodec
import com.example.cards.util.CharacterArtResolver
import kotlinx.coroutines.launch

class ResultActivity : BaseActivity<ActivityResultBinding>() {

    private var navigating = false
    private var rewardApplying = false
    private lateinit var sessionManager: SessionManager
    private val authRepository = AuthRepository()

    override fun initUI() {
        sessionManager = SessionManager(this)
        val isWin = intent.getBooleanExtra("is_win", false)
        val roomId = intent.getStringExtra("room_id").orEmpty()
        val isOwner = intent.getBooleanExtra("is_owner", false)

        val displayName = intent.getStringExtra("display_name").orEmpty().ifBlank { "\u73A9\u5BB6\u540D" }
        val avatarBase64 = intent.getStringExtra("avatar_base64")
        val role1Id = intent.getStringExtra("role_1_id").orEmpty()
        val role2Id = intent.getStringExtra("role_2_id").orEmpty()
        val role1Name = intent.getStringExtra("role_1_name").orEmpty().ifBlank { "\u89D2\u8272\u5361" }
        val role2Name = intent.getStringExtra("role_2_name").orEmpty().ifBlank { "\u89D2\u8272\u5361" }

        mBinding.tvPlayerName.text = displayName
        AvatarCodec.setAvatarOrDefault(mBinding.ivAvatar, avatarBase64)
        CharacterArtResolver.applyRoleCardVisual(
            context = this,
            cardView = mBinding.tvRole1,
            characterId = role1Id,
            fallbackName = role1Name,
            stateBackgroundRes = R.drawable.bg_role_slot
        )
        CharacterArtResolver.applyRoleCardVisual(
            context = this,
            cardView = mBinding.tvRole2,
            characterId = role2Id,
            fallbackName = role2Name,
            stateBackgroundRes = R.drawable.bg_role_slot
        )
        applyRoleCardAspectRatio()

        mBinding.tvResult.text = if (isWin) "\u80DC" else "\u8D25"
        mBinding.tvReward.text = if (isWin) "+15\u91D1\u5E01" else "+0\u91D1\u5E01"
        if (isWin) {
            grantWinReward()
        }
        mBinding.tvTapHint.text = if (isOwner) {
            "\u70B9\u51FB\u4EFB\u610F\u4F4D\u7F6E\u8FD4\u56DE\u623F\u95F4\uFF0C\u623F\u4E3B\u53EF\u76F4\u63A5\u518D\u5F00\u4E00\u628A"
        } else {
            "\u70B9\u51FB\u4EFB\u610F\u4F4D\u7F6E\u8FD4\u56DE\u623F\u95F4"
        }

        val allAreaClick = View.OnClickListener {
            openRoom(roomId)
        }
        mBinding.root.setOnClickListener(allAreaClick)
    }

    /**
     * 结算发奖：胜利方 +15 金币（映射到 lottery_points）。
     * 先读云端当前值再累加，避免本地缓存不一致导致覆盖。
     */
    private fun grantWinReward() {
        if (rewardApplying) return
        val playerId = sessionManager.getPlayerId().orEmpty()
        if (playerId.isBlank()) return

        rewardApplying = true
        lifecycleScope.launch {
            val fallback = sessionManager.getCachedLotteryPoints() ?: 0
            val currentPoints = authRepository.fetchProfile(playerId)?.lotteryPoints ?: fallback
            val nextPoints = currentPoints + WIN_REWARD_COINS

            val saved = authRepository.saveLotteryPoints(playerId, nextPoints).isSuccess
            if (saved) {
                sessionManager.saveCachedLotteryPoints(nextPoints)
                mBinding.tvReward.text = "+$WIN_REWARD_COINS\u91D1\u5E01"
            }
            rewardApplying = false
        }
    }

    private fun applyRoleCardAspectRatio() {
        mBinding.layoutRoleCards.post {
            val rowWidth = mBinding.layoutRoleCards.width.coerceAtLeast(1)
            val rowHeight = mBinding.layoutRoleCards.height.coerceAtLeast(1)
            val lp1 = mBinding.tvRole1.layoutParams as LinearLayout.LayoutParams
            val lp2 = mBinding.tvRole2.layoutParams as LinearLayout.LayoutParams
            val spacing = (lp1.marginEnd + lp2.marginStart).coerceAtLeast(0)

            val maxHeight = (rowHeight * 0.96f).toInt().coerceAtLeast(1)
            val maxWidthEach = ((rowWidth - spacing) / 2).coerceAtLeast(1)
            val byHeight = (maxHeight * 3f / 5f).toInt().coerceAtLeast(1)
            val cardWidth = minOf(byHeight, maxWidthEach)
            val cardHeight = (cardWidth * 5f / 3f).toInt().coerceAtLeast(1)

            updateRoleCardLayout(mBinding.tvRole1, cardWidth, cardHeight)
            updateRoleCardLayout(mBinding.tvRole2, cardWidth, cardHeight)
        }
    }

    private fun updateRoleCardLayout(cardView: View, width: Int, height: Int) {
        val lp = cardView.layoutParams as LinearLayout.LayoutParams
        lp.width = width
        lp.height = height
        lp.weight = 0f
        cardView.layoutParams = lp
    }

    private fun openRoom(roomId: String) {
        if (navigating) return
        navigating = true

        val next = Intent(this, RoomSearchActivity::class.java)
        if (roomId.isNotBlank()) {
            next.putExtra("room_id", roomId)
            next.putExtra("from_result", true)
        }
        startActivity(next)
        finish()
    }

    private companion object {
        const val WIN_REWARD_COINS = 15
    }
}
