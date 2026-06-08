package com.example.cards.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.graphics.drawable.toBitmap
import com.example.cards.R

/**
 * 统一角色立绘解析：
 * - 角色卡面：{characterId}_card
 * - 抽奖大图：{characterId}_big
 */
object CharacterArtResolver {

    private const val CARD_SUFFIX = "_card"
    private const val BIG_SUFFIX = "_big"
    private const val CARD_CORNER_RADIUS_DP = 14f

    // 缓存 getIdentifier 结果，避免重复反射查询资源表。
    private val drawableIdCache = mutableMapOf<String, Int>()

    fun cardDrawableId(context: Context, characterId: String?): Int? {
        return resolveDrawableId(context, characterId, CARD_SUFFIX)
    }

    fun bigDrawableId(context: Context, characterId: String?): Int? {
        return resolveDrawableId(context, characterId, BIG_SUFFIX)
    }

    /**
     * 角色卡渲染：
     * - 有卡面图：使用图片 + 描边框，并隐藏名字；
     * - 无卡面图：使用原有纯色卡面 + 名字。
     */
    fun applyRoleCardVisual(
        context: Context,
        cardView: TextView,
        characterId: String?,
        fallbackName: String,
        stateBackgroundRes: Int,
        preferTopCrop: Boolean = false
    ) {
        val cardDrawableId = cardDrawableId(context, characterId)
        if (cardDrawableId == null) {
            cardView.text = fallbackName
            cardView.setBackgroundResource(stateBackgroundRes)
            return
        }

        val artDrawable = AppCompatResources.getDrawable(context, cardDrawableId)
        val frameDrawable = AppCompatResources.getDrawable(
            context,
            frameDrawableForState(stateBackgroundRes)
        )
        if (artDrawable == null || frameDrawable == null) {
            cardView.text = fallbackName
            cardView.setBackgroundResource(stateBackgroundRes)
            return
        }

        val roundedArtDrawable = if (preferTopCrop) {
            topCropRoundedDrawable(context, cardView, artDrawable)
        } else {
            roundedDrawable(context, artDrawable)
        }
        cardView.text = ""
        cardView.background = LayerDrawable(arrayOf(roundedArtDrawable, frameDrawable))
    }

    /**
     * 角色详情页立绘渲染：
     * - 优先使用 *_big；
     * - 若未配置 big 图，则回退到 *_card；
     * - 都没有时显示占位文字。
     */
    fun applyDetailPortraitVisual(
        context: Context,
        portraitView: TextView,
        characterId: String?,
        fallbackName: String
    ) {
        val drawableId = bigDrawableId(context, characterId)
            ?: cardDrawableId(context, characterId)
        if (drawableId == null) {
            portraitView.text = fallbackName
            portraitView.setBackgroundResource(R.drawable.bg_role_slot)
            return
        }

        val artDrawable = AppCompatResources.getDrawable(context, drawableId)
        val frameDrawable = AppCompatResources.getDrawable(context, R.drawable.fg_role_card_frame)
        if (artDrawable == null || frameDrawable == null) {
            portraitView.text = fallbackName
            portraitView.setBackgroundResource(R.drawable.bg_role_slot)
            return
        }

        val roundedArtDrawable = topCropRoundedDrawable(context, portraitView, artDrawable)
        portraitView.text = ""
        portraitView.background = LayerDrawable(arrayOf(roundedArtDrawable, frameDrawable))
    }

    private fun frameDrawableForState(stateBackgroundRes: Int): Int {
        return when (stateBackgroundRes) {
            R.drawable.bg_role_slot_selected -> R.drawable.fg_role_card_frame_selected
            R.drawable.bg_role_slot_hit -> R.drawable.fg_role_card_frame_hit
            else -> R.drawable.fg_role_card_frame
        }
    }

    private fun resolveDrawableId(
        context: Context,
        characterId: String?,
        suffix: String
    ): Int? {
        val id = characterId?.trim().orEmpty()
        if (id.isBlank()) return null

        val drawableName = "${id.lowercase()}$suffix"
        val cached = drawableIdCache[drawableName]
        if (cached != null) {
            return cached.takeIf { it != 0 }
        }

        val resolved = context.resources.getIdentifier(
            drawableName,
            "drawable",
            context.packageName
        )
        drawableIdCache[drawableName] = resolved
        return resolved.takeIf { it != 0 }
    }

    /**
     * 角色卡立绘统一圆角，避免“有边框但卡面还是直角”的视觉割裂。
     */
    private fun roundedDrawable(context: Context, drawable: Drawable): Drawable {
        val radiusPx = CARD_CORNER_RADIUS_DP * context.resources.displayMetrics.density
        return RoundedBitmapDrawableFactory.create(
            context.resources,
            drawable.toBitmap()
        ).apply {
            cornerRadius = radiusPx
            setAntiAlias(true)
            setDither(true)
        }
    }

    /**
     * 按目标控件宽高比裁切立绘，优先保留图片上半部分内容。
     * 规则：
     * 1) 目标更宽时裁掉下半部分（y 从 0 开始）；
     * 2) 目标更窄时水平居中裁切。
     */
    private fun topCropRoundedDrawable(
        context: Context,
        cardView: TextView,
        drawable: Drawable
    ): Drawable {
        val targetAspect = resolveTargetAspect(cardView)
        val srcBitmap = drawable.toBitmap()
        val srcW = srcBitmap.width.toFloat().coerceAtLeast(1f)
        val srcH = srcBitmap.height.toFloat().coerceAtLeast(1f)
        val srcAspect = srcW / srcH

        val cropBitmap = if (srcAspect < targetAspect) {
            // 目标更宽：裁高度，且从顶部开始保留上半部分内容。
            val cropH = (srcW / targetAspect).toInt().coerceIn(1, srcBitmap.height)
            Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.width, cropH)
        } else {
            // 目标更窄：裁宽度，水平居中。
            val cropW = (srcH * targetAspect).toInt().coerceIn(1, srcBitmap.width)
            val startX = ((srcBitmap.width - cropW) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(srcBitmap, startX, 0, cropW, srcBitmap.height)
        }

        val radiusPx = CARD_CORNER_RADIUS_DP * context.resources.displayMetrics.density
        return RoundedBitmapDrawableFactory.create(context.resources, cropBitmap).apply {
            cornerRadius = radiusPx
            setAntiAlias(true)
            setDither(true)
        }
    }

    private fun resolveTargetAspect(cardView: TextView): Float {
        val width = cardView.width
        val height = cardView.height
        if (width > 0 && height > 0) {
            return width.toFloat() / height.toFloat()
        }
        // 游戏卡默认接近 98:118，未测量时用该比例兜底。
        return 98f / 118f
    }
}
