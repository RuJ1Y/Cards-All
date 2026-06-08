package com.example.cards

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import com.example.cards.base.BaseActivity
import com.example.cards.databinding.ActivityCharacterDetailBinding
import com.example.cards.game.model.CharacterTemplate
import com.example.cards.game.repo.DefaultCharacterRepository
import com.example.cards.util.CharacterArtResolver
import kotlin.math.roundToInt

class CharacterDetailActivity : BaseActivity<ActivityCharacterDetailBinding>() {

    private val characterRepository = DefaultCharacterRepository()

    private var sourceBounds: SourceBounds? = null
    private var sourceLabel: String = ""
    private var enterPlayed = false
    private var closing = false

    override fun initUI() {
        val characterId = intent.getStringExtra(EXTRA_CHARACTER_ID)
        val character = characterId
            ?.let { runCatching { characterRepository.getById(it) }.getOrNull() }

        if (character == null) {
            finish()
            overridePendingTransition(0, 0)
            return
        }

        sourceBounds = readSourceBounds()
        sourceLabel = intent.getStringExtra(EXTRA_SOURCE_TEXT).orEmpty()

        bindCharacter(character)

        val showBackIcon = shouldShowCompactBackIcon()
        mBinding.ivBack.isVisible = showBackIcon
        if (showBackIcon) {
            mBinding.ivBack.setOnClickListener { startCloseAnimation() }
        }
        mBinding.btnClose.setOnClickListener { startCloseAnimation() }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    startCloseAnimation()
                }
            }
        )

        prepareInitialVisualState()
        mBinding.rootDetail.post { playEnterAnimation() }
    }

    private fun shouldShowCompactBackIcon(): Boolean {
        val widthDp = resources.configuration.screenWidthDp
        val heightDp = resources.configuration.screenHeightDp
        return widthDp in 1..620 || heightDp in 1..300
    }

    private fun bindCharacter(character: CharacterTemplate) {
        CharacterArtResolver.applyDetailPortraitVisual(
            context = this,
            portraitView = mBinding.tvArtPlaceholder,
            characterId = character.id,
            fallbackName = character.name
        )
        mBinding.tvCharacterName.text = character.name
        mBinding.tvCharacterRole.text = "定位：${character.role}"
        mBinding.tvCharacterHp.text = "HP：${character.maxHp}"
        mBinding.tvCharacterPoint.text = "终极点数：${character.ultimateRequired}"
        mBinding.tvNormalSkill.text =
            "普通技能｜${character.normalSkill.name}\n${character.normalSkill.description}"
        mBinding.tvPowerSkill.text =
            "特殊技能｜${character.powerSkill.name}\n${character.powerSkill.description}"
        mBinding.tvUltimateSkill.text =
            "终极技能｜${character.ultimateSkill.name}\n${character.ultimateSkill.description}"
    }

    private fun prepareInitialVisualState() {
        val hasSource = sourceBounds != null
        if (!hasSource) {
            mBinding.layoutHeader.alpha = 1f
            mBinding.vHeaderLine.alpha = 1f
            mBinding.layoutInfoPanel.alpha = 1f
            mBinding.tvArtPlaceholder.alpha = 1f
            mBinding.tvCardMorphOverlay.visibility = View.GONE
            return
        }

        mBinding.layoutHeader.alpha = 0f
        mBinding.vHeaderLine.alpha = 0f
        mBinding.layoutInfoPanel.alpha = 0f
        mBinding.tvArtPlaceholder.alpha = 0f

        mBinding.tvCardMorphOverlay.visibility = View.VISIBLE
        mBinding.tvCardMorphOverlay.alpha = 1f
        mBinding.tvCardMorphOverlay.text = sourceLabel.ifBlank { mBinding.tvCharacterName.text.toString() }
    }

    private fun playEnterAnimation() {
        if (enterPlayed) return
        enterPlayed = true

        val source = sourceBounds ?: run {
            mBinding.layoutHeader.alpha = 1f
            mBinding.vHeaderLine.alpha = 1f
            mBinding.layoutInfoPanel.alpha = 1f
            mBinding.tvArtPlaceholder.alpha = 1f
            return
        }

        val startFrame = frameFromSource(source)
        val endFrame = frameFromView(mBinding.tvArtPlaceholder) ?: run {
            mBinding.layoutHeader.alpha = 1f
            mBinding.vHeaderLine.alpha = 1f
            mBinding.layoutInfoPanel.alpha = 1f
            mBinding.tvArtPlaceholder.alpha = 1f
            mBinding.tvCardMorphOverlay.visibility = View.GONE
            return
        }

        val overlay = mBinding.tvCardMorphOverlay
        val startTextSize = source.textSizePx.takeIf { it > 0f } ?: overlay.textSize
        val endTextSize = mBinding.tvArtPlaceholder.textSize

        applyOverlayFrame(overlay, startFrame)
        overlay.setTextSize(TypedValue.COMPLEX_UNIT_PX, startTextSize)
        overlay.visibility = View.VISIBLE
        overlay.alpha = 1f

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val p = animator.animatedValue as Float
                val frame = RectFrame(
                    left = lerp(startFrame.left, endFrame.left, p),
                    top = lerp(startFrame.top, endFrame.top, p),
                    width = lerp(startFrame.width, endFrame.width, p),
                    height = lerp(startFrame.height, endFrame.height, p)
                )
                applyOverlayFrame(overlay, frame)
                overlay.alpha = (1f - p).coerceIn(0f, 1f)
                overlay.setTextSize(TypedValue.COMPLEX_UNIT_PX, lerp(startTextSize, endTextSize, p))
                mBinding.tvArtPlaceholder.alpha = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlay.visibility = View.GONE
                    overlay.alpha = 0f
                    mBinding.tvArtPlaceholder.alpha = 1f
                }
            })
            start()
        }

        mBinding.layoutHeader.animate()
            .alpha(1f)
            .setStartDelay(40L)
            .setDuration(150L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        mBinding.vHeaderLine.animate()
            .alpha(1f)
            .setStartDelay(50L)
            .setDuration(150L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        mBinding.layoutInfoPanel.animate()
            .alpha(1f)
            .setStartDelay(60L)
            .setDuration(160L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun startCloseAnimation() {
        if (closing) return
        closing = true

        val source = sourceBounds
        if (source == null) {
            finish()
            overridePendingTransition(0, 0)
            return
        }

        val startFrame = frameFromSource(source)
        val endFrame = frameFromView(mBinding.tvArtPlaceholder)
        if (endFrame == null) {
            finish()
            overridePendingTransition(0, 0)
            return
        }

        mBinding.btnClose.isEnabled = false
        mBinding.ivBack.isEnabled = false

        val overlay = mBinding.tvCardMorphOverlay
        val startTextSize = mBinding.tvArtPlaceholder.textSize
        val endTextSize = source.textSizePx.takeIf { it > 0f } ?: startTextSize

        overlay.text = sourceLabel.ifBlank { mBinding.tvCharacterName.text.toString() }
        applyOverlayFrame(overlay, endFrame)
        overlay.setTextSize(TypedValue.COMPLEX_UNIT_PX, startTextSize)
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0.2f

        mBinding.layoutHeader.animate()
            .alpha(0f)
            .setDuration(90L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        mBinding.vHeaderLine.animate()
            .alpha(0f)
            .setDuration(90L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        mBinding.layoutInfoPanel.animate()
            .alpha(0f)
            .setDuration(100L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        mBinding.tvArtPlaceholder.animate()
            .alpha(0f)
            .setDuration(90L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 190L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val p = animator.animatedValue as Float
                val frame = RectFrame(
                    left = lerp(endFrame.left, startFrame.left, p),
                    top = lerp(endFrame.top, startFrame.top, p),
                    width = lerp(endFrame.width, startFrame.width, p),
                    height = lerp(endFrame.height, startFrame.height, p)
                )
                applyOverlayFrame(overlay, frame)
                overlay.alpha = (0.2f + 0.8f * p).coerceIn(0f, 1f)
                overlay.setTextSize(TypedValue.COMPLEX_UNIT_PX, lerp(startTextSize, endTextSize, p))
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                    overridePendingTransition(0, 0)
                }
            })
            start()
        }
    }

    private fun frameFromSource(source: SourceBounds): RectFrame {
        val rootPos = rootScreenPosition()
        return RectFrame(
            left = source.left - rootPos.first,
            top = source.top - rootPos.second,
            width = source.width.toFloat(),
            height = source.height.toFloat()
        )
    }

    private fun frameFromView(view: View): RectFrame? {
        if (view.width <= 0 || view.height <= 0) return null
        val rootPos = rootScreenPosition()
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return RectFrame(
            left = loc[0] - rootPos.first,
            top = loc[1] - rootPos.second,
            width = view.width.toFloat(),
            height = view.height.toFloat()
        )
    }

    private fun applyOverlayFrame(overlay: View, frame: RectFrame) {
        val w = frame.width.roundToInt().coerceAtLeast(1)
        val h = frame.height.roundToInt().coerceAtLeast(1)
        val lp = overlay.layoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            overlay.layoutParams = lp
        }
        overlay.x = frame.left
        overlay.y = frame.top
    }

    private fun rootScreenPosition(): Pair<Float, Float> {
        val loc = IntArray(2)
        mBinding.rootDetail.getLocationOnScreen(loc)
        return loc[0].toFloat() to loc[1].toFloat()
    }

    private fun readSourceBounds(): SourceBounds? {
        if (!intent.hasExtra(EXTRA_SOURCE_LEFT)) return null

        val left = intent.getIntExtra(EXTRA_SOURCE_LEFT, -1)
        val top = intent.getIntExtra(EXTRA_SOURCE_TOP, -1)
        val width = intent.getIntExtra(EXTRA_SOURCE_WIDTH, 0)
        val height = intent.getIntExtra(EXTRA_SOURCE_HEIGHT, 0)
        val textSizePx = intent.getFloatExtra(EXTRA_SOURCE_TEXT_SIZE_PX, 0f)

        if (left < 0 || top < 0 || width <= 0 || height <= 0) return null
        return SourceBounds(left, top, width, height, textSizePx)
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + (end - start) * progress
    }

    private data class SourceBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val textSizePx: Float
    )

    private data class RectFrame(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float
    )

    companion object {
        const val EXTRA_CHARACTER_ID = "character_id"
        const val EXTRA_SOURCE_LEFT = "source_left"
        const val EXTRA_SOURCE_TOP = "source_top"
        const val EXTRA_SOURCE_WIDTH = "source_width"
        const val EXTRA_SOURCE_HEIGHT = "source_height"
        const val EXTRA_SOURCE_TEXT_SIZE_PX = "source_text_size_px"
        const val EXTRA_SOURCE_TEXT = "source_text"
    }
}
