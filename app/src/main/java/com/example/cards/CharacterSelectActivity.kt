package com.example.cards

import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import com.example.cards.base.BaseActivity
import com.example.cards.databinding.ActivityCharacterSelectBinding
import com.example.cards.game.model.CharacterTemplate
import com.example.cards.game.repo.DefaultCharacterRepository
import com.example.cards.game.repo.PlayerCharacterCloudRepository
import com.example.cards.session.SessionManager
import com.example.cards.util.CharacterArtResolver
import kotlinx.coroutines.launch

class CharacterSelectActivity : BaseActivity<ActivityCharacterSelectBinding>() {

    private val characterRepository = DefaultCharacterRepository()
    private val cloudRepository = PlayerCharacterCloudRepository()

    private lateinit var sessionManager: SessionManager
    private var playerId: String = ""

    private val selectedIds = mutableListOf<String>()
    private val cardViewsById = mutableMapOf<String, TextView>()
    private var ownedTemplates: List<CharacterTemplate> = emptyList()

    override fun initUI() {
        sessionManager = SessionManager(this)
        playerId = sessionManager.getPlayerId().orEmpty()
        if (playerId.isBlank()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        mBinding.btnCancel.setOnClickListener { finish() }
        mBinding.btnConfirm.setOnClickListener { onConfirmClicked() }

        renderFromLocalCache()
        mBinding.panelOwned.post {
            if (ownedTemplates.isNotEmpty()) {
                renderRoleCards()
            }
        }

        lifecycleScope.launch {
            syncFromCloudAndRefresh()
        }
    }

    private fun renderFromLocalCache() {
        sessionManager.ensureCharacterCacheDefaults()
        val allTemplates = characterRepository.all()
        val ownedIds = sessionManager.getOwnedCharacterIds()
        val owned = allTemplates.filter { it.id in ownedIds }

        ownedTemplates = if (owned.isNotEmpty()) {
            owned
        } else {
            allTemplates
        }

        initSelection(sessionManager.getLobbyCharacterIds())
        renderRoleCards()
        updateSelectedText()
        updateConfirmState()
    }

    private suspend fun syncFromCloudAndRefresh() {
        runCatching {
            cloudRepository.ensureDefaultState(playerId)

            val allTemplates = characterRepository.all()
            val ownedFromCloud = cloudRepository.fetchOwnedCharacterIds(playerId)
            if (ownedFromCloud.isNotEmpty()) {
                sessionManager.saveOwnedCharacterIds(ownedFromCloud)
            }

            val ownedIds = sessionManager.getOwnedCharacterIds()
            val owned = allTemplates.filter { it.id in ownedIds }
            ownedTemplates = if (owned.isNotEmpty()) owned else allTemplates

            val cloudSelected = cloudRepository.fetchSelectedCharacterIds(playerId)
            if (cloudSelected != null) {
                sessionManager.saveLobbyCharacterIds(cloudSelected.first, cloudSelected.second)
            }

            initSelection(cloudSelected ?: sessionManager.getLobbyCharacterIds())
            renderRoleCards()
            updateSelectedText()
            updateConfirmState()
        }
    }

    private fun initSelection(primaryIds: Pair<String, String>? = null) {
        selectedIds.clear()
        if (ownedTemplates.isEmpty()) return

        val ownedById = ownedTemplates.associateBy { it.id }

        fun tryApply(ids: Pair<String, String>?): Boolean {
            if (ids == null) return false
            val first = ownedById[ids.first]
            val second = ownedById[ids.second]
            if (first != null && second != null && first.id != second.id) {
                selectedIds.add(first.id)
                selectedIds.add(second.id)
                return true
            }
            return false
        }

        if (tryApply(primaryIds)) return
        if (tryApply(sessionManager.getLobbyCharacterIds())) return

        val localOwnedIds = sessionManager.getOwnedCharacterIds().toMutableSet()
        if (localOwnedIds.isNotEmpty()) {
            val localOwned = ownedTemplates.filter { it.id in localOwnedIds }
            if (localOwned.size >= 2) {
                selectedIds.add(localOwned[0].id)
                selectedIds.add(localOwned[1].id)
                return
            }
        }

        val swordsman = ownedTemplates.find { it.id == "swordsman" } ?: ownedTemplates.firstOrNull()
        val mage = ownedTemplates.find { it.id == "mage" } ?: ownedTemplates.getOrNull(1)

        swordsman?.let { selectedIds.add(it.id) }
        mage?.let {
            if (selectedIds.none { id -> id == it.id }) {
                selectedIds.add(it.id)
            }
        }

        if (selectedIds.size < 2) {
            ownedTemplates.forEach { template ->
                if (selectedIds.size >= 2) return@forEach
                if (selectedIds.none { it == template.id }) {
                    selectedIds.add(template.id)
                }
            }
        }
    }

    private fun renderRoleCards() {
        mBinding.roleCardContainer.removeAllViews()
        cardViewsById.clear()
        val panelHeight = mBinding.panelOwned.height.takeIf { it > 0 }
            ?: (resources.displayMetrics.heightPixels * 0.36f).toInt()
        val cardHeight = (panelHeight - mBinding.panelOwned.paddingTop - mBinding.panelOwned.paddingBottom)
            .coerceAtLeast(120.dp())
        val cardWidth = (cardHeight * 3f / 5f).toInt().coerceAtLeast(72.dp())
        val cardGap = (resources.displayMetrics.widthPixels * 0.014f).toInt().coerceAtLeast(8.dp())
        val cardPadding = (cardWidth * 0.08f).toInt().coerceAtLeast(8.dp())

        ownedTemplates.forEach { template ->
            val card = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 22f
                setTextColor(getColor(R.color.black))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
                setOnClickListener { toggleSelection(template) }
                setOnLongClickListener {
                    openCharacterDetail(this, template)
                    true
                }
                renderRoleCardVisual(this, template.id, template.name, selectedIds.contains(template.id))
            }

            val lp = LinearLayout.LayoutParams(cardWidth, cardHeight)
            lp.marginEnd = cardGap
            mBinding.roleCardContainer.addView(card, lp)
            cardViewsById[template.id] = card
        }
    }

    private fun toggleSelection(template: CharacterTemplate) {
        val id = template.id
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            if (selectedIds.size >= 2) {
                Toast.makeText(this, "最多选择两名角色", Toast.LENGTH_SHORT).show()
                return
            }
            selectedIds.add(id)
        }

        refreshCardStates()
        updateSelectedText()
        updateConfirmState()
    }

    private fun refreshCardStates() {
        cardViewsById.forEach { (id, view) ->
            val template = ownedTemplates.firstOrNull { it.id == id } ?: return@forEach
            renderRoleCardVisual(
                cardView = view,
                characterId = template.id,
                fallbackName = template.name,
                selected = selectedIds.contains(id)
            )
        }
    }

    private fun updateSelectedText() {
        val selectedNames = selectedIds.mapNotNull { selectedId ->
            ownedTemplates.firstOrNull { it.id == selectedId }?.name
        }

        mBinding.tvSelected.text = when (selectedNames.size) {
            0 -> "请选择两名角色"
            1 -> "已选：${selectedNames[0]}"
            else -> "已选：${selectedNames.joinToString("、")}"
        }
    }

    private fun updateConfirmState() {
        val enabled = selectedIds.size == 2
        mBinding.btnConfirm.isEnabled = enabled
        mBinding.btnConfirm.alpha = if (enabled) 1f else 0.55f
    }

    private fun onConfirmClicked() {
        if (selectedIds.size != 2) {
            Toast.makeText(this, "请选择两名角色后再确认", Toast.LENGTH_SHORT).show()
            return
        }

        val role1 = selectedIds[0]
        val role2 = selectedIds[1]
        lifecycleScope.launch {
            runCatching {
                cloudRepository.saveSelectedCharacterIds(playerId, role1, role2)
            }.onSuccess {
                sessionManager.saveLobbyCharacterIds(role1, role2)
                sessionManager.saveOwnedCharacterIds(sessionManager.getOwnedCharacterIds() + role1 + role2)
                setResult(RESULT_OK)
                finish()
            }.onFailure {
                Toast.makeText(
                    this@CharacterSelectActivity,
                    "同步失败：${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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

    private fun renderRoleCardVisual(
        cardView: TextView,
        characterId: String,
        fallbackName: String,
        selected: Boolean
    ) {
        CharacterArtResolver.applyRoleCardVisual(
            context = this,
            cardView = cardView,
            characterId = characterId,
            fallbackName = fallbackName,
            stateBackgroundRes = if (selected) {
                R.drawable.bg_role_slot_selected
            } else {
                R.drawable.bg_role_slot
            }
        )
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
