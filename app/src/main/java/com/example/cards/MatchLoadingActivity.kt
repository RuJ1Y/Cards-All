package com.example.cards

import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.cards.auth.AuthRepository
import com.example.cards.base.BaseActivity
import com.example.cards.databinding.ActivityMatchLoadingBinding
import com.example.cards.game.model.GameRoomState
import com.example.cards.game.repo.DefaultCharacterRepository
import com.example.cards.game.sync.GameRoomSyncRepository
import com.example.cards.session.SessionManager
import com.example.cards.util.AvatarCodec
import com.example.cards.util.CharacterArtResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MatchLoadingActivity : BaseActivity<ActivityMatchLoadingBinding>() {

    private lateinit var sessionManager: SessionManager
    private val syncRepository = GameRoomSyncRepository()
    private val authRepository = AuthRepository()
    private val characterRepository = DefaultCharacterRepository()

    override fun initUI() {
        sessionManager = SessionManager(this)

        val roomId = intent.getStringExtra("room_id").orEmpty()
        val playerId = sessionManager.getPlayerId().orEmpty()
        if (roomId.isBlank() || playerId.isBlank()) {
            finish()
            return
        }

        mBinding.rowPlayers.post { applyRoleCardAspectRatio() }

        lifecycleScope.launch {
            waitForReadyState(roomId, playerId)
        }
    }

    private suspend fun waitForReadyState(roomId: String, playerId: String) {
        while (lifecycleScope.isActive) {
            val state = syncRepository.fetchState(roomId)
            if (state != null && state.players.size >= 2) {
                bindState(state, playerId)
                mBinding.tvLoading.text = "正在加载对局..."
                delay(1500L)
                startActivity(
                    Intent(this@MatchLoadingActivity, GameActivity::class.java)
                        .putExtra("room_id", roomId)
                )
                finish()
                return
            }

            mBinding.tvLoading.text = "等待对手进入房间..."
            delay(700L)
        }
    }

    private suspend fun bindState(state: GameRoomState, playerId: String) {
        val selfPlayer = state.players.firstOrNull { it.playerId == playerId } ?: state.players.first()
        val enemyPlayer = state.players.firstOrNull { it.playerId != selfPlayer.playerId } ?: selfPlayer

        val selfName = authRepository.fetchUsername(selfPlayer.playerId)
        val enemyName = authRepository.fetchUsername(enemyPlayer.playerId)

        val selfProfile = authRepository.fetchProfile(selfPlayer.playerId)
        val enemyProfile = authRepository.fetchProfile(enemyPlayer.playerId)

        mBinding.tvSelfName.text = selfName
        mBinding.tvEnemyName.text = enemyName

        AvatarCodec.setAvatarOrDefault(mBinding.ivSelfAvatar, selfProfile?.avatarBase64)
        AvatarCodec.setAvatarOrDefault(mBinding.ivEnemyAvatar, enemyProfile?.avatarBase64)

        bindRoleCard(mBinding.tvSelfRole1, selfPlayer.characters.getOrNull(0)?.templateId)
        bindRoleCard(mBinding.tvSelfRole2, selfPlayer.characters.getOrNull(1)?.templateId)
        bindRoleCard(mBinding.tvEnemyRole1, enemyPlayer.characters.getOrNull(0)?.templateId)
        bindRoleCard(mBinding.tvEnemyRole2, enemyPlayer.characters.getOrNull(1)?.templateId)

        mBinding.rowPlayers.post { applyRoleCardAspectRatio() }
    }

    private fun bindRoleCard(cardView: TextView, templateId: String?) {
        val id = templateId?.trim().orEmpty()
        val fallbackName = if (id.isBlank()) "-" else toCharacterName(id)

        CharacterArtResolver.applyRoleCardVisual(
            context = this,
            cardView = cardView,
            characterId = id.ifBlank { null },
            fallbackName = fallbackName,
            stateBackgroundRes = R.drawable.bg_role_slot,
            preferTopCrop = true
        )
    }

    private fun applyRoleCardAspectRatio() {
        val rowWidth = mBinding.rowPlayers.width
        val vsWidth = mBinding.tvVs.width
        if (rowWidth <= 0 || vsWidth <= 0) return

        val sideWidth = ((rowWidth - vsWidth - 28.dp()) / 2f).toInt().coerceAtLeast(120.dp())
        val roleGap = 12.dp()

        val maxCardWidthBySide = ((sideWidth - roleGap) / 2f).toInt().coerceAtLeast(56.dp())
        val maxCardHeightByScreen = (resources.displayMetrics.heightPixels * 0.30f).toInt().coerceAtLeast(96.dp())
        val maxCardWidthByHeight = (maxCardHeightByScreen * 3f / 5f).toInt()

        val cardWidth = minOf(maxCardWidthBySide, maxCardWidthByHeight).coerceAtLeast(56.dp())
        val cardHeight = (cardWidth * 5f / 3f).toInt().coerceAtLeast(94.dp())

        updateRoleCardLayout(mBinding.tvSelfRole1, cardWidth, cardHeight, endMargin = roleGap / 2)
        updateRoleCardLayout(mBinding.tvSelfRole2, cardWidth, cardHeight, startMargin = roleGap / 2)
        updateRoleCardLayout(mBinding.tvEnemyRole1, cardWidth, cardHeight, endMargin = roleGap / 2)
        updateRoleCardLayout(mBinding.tvEnemyRole2, cardWidth, cardHeight, startMargin = roleGap / 2)
    }

    private fun updateRoleCardLayout(
        cardView: TextView,
        width: Int,
        height: Int,
        startMargin: Int = 0,
        endMargin: Int = 0
    ) {
        val lp = cardView.layoutParams as LinearLayout.LayoutParams
        lp.width = width
        lp.height = height
        lp.marginStart = startMargin
        lp.marginEnd = endMargin
        cardView.layoutParams = lp
    }

    private fun toCharacterName(templateId: String): String {
        return runCatching {
            characterRepository.getById(templateId).name
        }.getOrDefault(templateId)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
