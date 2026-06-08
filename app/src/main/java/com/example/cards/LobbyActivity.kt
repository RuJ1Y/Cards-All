package com.example.cards

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import com.example.cards.auth.AuthRepository
import com.example.cards.base.BaseActivity
import com.example.cards.databinding.ActivityLobbyBinding
import com.example.cards.game.model.CharacterTemplate
import com.example.cards.game.repo.DefaultCharacterRepository
import com.example.cards.game.repo.PlayerCharacterCloudRepository
import com.example.cards.session.SessionManager
import com.example.cards.util.AvatarCodec
import com.example.cards.util.CharacterArtResolver
import kotlinx.coroutines.launch

class LobbyActivity : BaseActivity<ActivityLobbyBinding>() {

    private lateinit var sessionManager: SessionManager
    private val authRepository = AuthRepository()
    private val characterRepository = DefaultCharacterRepository()
    private val cloudRepository = PlayerCharacterCloudRepository()

    private var playerId: String = ""
    private var roleOne: CharacterTemplate? = null
    private var roleTwo: CharacterTemplate? = null

    override fun initUI() {
        sessionManager = SessionManager(this)
        sessionManager.ensureCharacterCacheDefaults()

        playerId = intent.getStringExtra("player_id") ?: sessionManager.getPlayerId().orEmpty()
        if (playerId.isBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        mBinding.tvPlayer.text = sessionManager.getCachedUsername() ?: playerId
        val cachedPoints = sessionManager.getCachedLotteryPoints() ?: DEFAULT_LOTTERY_POINTS
        mBinding.tvCoin.text = formatCoinText(cachedPoints)
        AvatarCodec.setAvatarOrDefault(mBinding.ivMyAvatar, sessionManager.getCachedAvatarBase64())

        bindLobbyRolesFromLocal()
        applyRoleCardAspectRatio()
        refreshPlayerProfile(cachedPoints)

        mBinding.tvRole1.setOnClickListener {
            roleOne?.let { role -> openCharacterDetail(mBinding.tvRole1, role) }
        }
        mBinding.tvRole2.setOnClickListener {
            roleTwo?.let { role -> openCharacterDetail(mBinding.tvRole2, role) }
        }

        lifecycleScope.launch {
            syncLobbyRolesFromCloud()
        }

        mBinding.btnCharacter.setOnClickListener {
            startActivity(Intent(this, CharacterAcquireActivity::class.java))
        }
        mBinding.btnRoom.setOnClickListener {
            startActivity(Intent(this, RoomSearchActivity::class.java))
        }
        mBinding.btnChange.setOnClickListener {
            startActivity(Intent(this, CharacterSelectActivity::class.java))
        }
        mBinding.ivMyAvatar.setOnClickListener {
            startActivity(Intent(this, UserManageActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        bindLobbyRolesFromLocal()
        applyRoleCardAspectRatio()
        val cachedPoints = sessionManager.getCachedLotteryPoints() ?: DEFAULT_LOTTERY_POINTS
        refreshPlayerProfile(cachedPoints)
        lifecycleScope.launch {
            syncLobbyRolesFromCloud()
        }
    }

    private fun refreshPlayerProfile(cachedPoints: Int) {
        lifecycleScope.launch {
            val username = authRepository.fetchUsername(playerId)
            mBinding.tvPlayer.text = username
            sessionManager.saveCachedUsername(username)

            val profile = authRepository.fetchProfile(playerId)
            sessionManager.saveCachedAvatarBase64(profile?.avatarBase64)
            AvatarCodec.setAvatarOrDefault(mBinding.ivMyAvatar, sessionManager.getCachedAvatarBase64())
            val points = profile?.lotteryPoints ?: cachedPoints
            sessionManager.saveCachedLotteryPoints(points)
            mBinding.tvCoin.text = formatCoinText(points)
        }
    }

    private fun bindLobbyRolesFromLocal() {
        val selected = resolveLobbyCharactersByIds(sessionManager.getLobbyCharacterIds())
        bindRoles(selected)
    }

    private suspend fun syncLobbyRolesFromCloud() {
        runCatching {
            cloudRepository.ensureDefaultState(playerId)
            val ownedIds = cloudRepository.fetchOwnedCharacterIds(playerId)
            if (ownedIds.isNotEmpty()) {
                sessionManager.saveOwnedCharacterIds(ownedIds)
            }
            val selectedFromCloud = cloudRepository.fetchSelectedCharacterIds(playerId)
            val selected = resolveLobbyCharactersByIds(selectedFromCloud)
            bindRoles(selected)
            sessionManager.saveLobbyCharacterIds(selected.first.id, selected.second.id)
        }
    }

    private fun resolveLobbyCharactersByIds(ids: Pair<String, String>?): Pair<CharacterTemplate, CharacterTemplate> {
        val allCharacters = characterRepository.all()
        if (allCharacters.isEmpty()) {
            error("角色库为空，无法显示首页角色")
        }

        if (ids != null) {
            val first = allCharacters.find { it.id == ids.first }
            val second = allCharacters.find { it.id == ids.second }
            if (first != null && second != null && first.id != second.id) {
                return first to second
            }
        }

        val swordsman = allCharacters.find { it.id == "swordsman" } ?: allCharacters.first()
        val mage = allCharacters.find { it.id == "mage" } ?: allCharacters.getOrElse(1) { swordsman }
        return swordsman to mage
    }

    private fun bindRoles(selected: Pair<CharacterTemplate, CharacterTemplate>) {
        roleOne = selected.first
        roleTwo = selected.second
        CharacterArtResolver.applyRoleCardVisual(
            context = this,
            cardView = mBinding.tvRole1,
            characterId = selected.first.id,
            fallbackName = selected.first.name,
            stateBackgroundRes = R.drawable.bg_role_slot
        )
        CharacterArtResolver.applyRoleCardVisual(
            context = this,
            cardView = mBinding.tvRole2,
            characterId = selected.second.id,
            fallbackName = selected.second.name,
            stateBackgroundRes = R.drawable.bg_role_slot
        )
    }

    private fun openCharacterDetail(sourceView: View, character: CharacterTemplate) {
        val loc = IntArray(2)
        sourceView.getLocationOnScreen(loc)

        val intent = Intent(this, CharacterDetailActivity::class.java).apply {
            putExtra(CharacterDetailActivity.EXTRA_CHARACTER_ID, character.id)
            putExtra(CharacterDetailActivity.EXTRA_SOURCE_LEFT, loc[0])
            putExtra(CharacterDetailActivity.EXTRA_SOURCE_TOP, loc[1])
            putExtra(CharacterDetailActivity.EXTRA_SOURCE_WIDTH, sourceView.width)
            putExtra(CharacterDetailActivity.EXTRA_SOURCE_HEIGHT, sourceView.height)
            putExtra(
                CharacterDetailActivity.EXTRA_SOURCE_TEXT_SIZE_PX,
                (sourceView as? TextView)?.textSize ?: 0f
            )
            putExtra(
                CharacterDetailActivity.EXTRA_SOURCE_TEXT,
                (sourceView as? TextView)?.text?.toString().orEmpty()
            )
        }
        val options = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0)
        startActivity(intent, options.toBundle())
    }

    private fun formatCoinText(points: Int): String {
        return "\u91d1\u5e01: $points"
    }

    private fun applyRoleCardAspectRatio() {
        mBinding.rolePanel.post {
            val panelWidth = mBinding.rolePanel.width
            val panelHeight = mBinding.rolePanel.height
            if (panelWidth <= 0 || panelHeight <= 0) return@post

            val margin = 6.dp()
            val maxWidthByPanel = ((panelWidth - margin * 3f) / 2f).toInt().coerceAtLeast(0)
            val maxHeightByPanel = (panelHeight - margin * 2).coerceAtLeast(0)
            val widthByHeight = (maxHeightByPanel * 3f / 5f).toInt()
            val finalWidth = minOf(maxWidthByPanel, widthByHeight).coerceAtLeast(1)
            val finalHeight = (finalWidth * 5f / 3f).toInt().coerceAtLeast(1)

            updateRoleCardLayout(mBinding.tvRole1, finalWidth, finalHeight, margin, margin)
            updateRoleCardLayout(mBinding.tvRole2, finalWidth, finalHeight, 0, margin)
        }
    }

    private fun updateRoleCardLayout(
        cardView: TextView,
        width: Int,
        height: Int,
        startMargin: Int,
        endMargin: Int
    ) {
        val lp = cardView.layoutParams as ConstraintLayout.LayoutParams
        lp.width = width
        lp.height = height
        lp.marginStart = startMargin
        lp.marginEnd = endMargin
        lp.topMargin = 6.dp()
        lp.bottomMargin = 6.dp()
        cardView.layoutParams = lp
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val DEFAULT_LOTTERY_POINTS = 0
    }
}
