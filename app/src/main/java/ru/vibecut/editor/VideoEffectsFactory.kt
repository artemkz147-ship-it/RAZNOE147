package ru.vibecut.editor

import android.graphics.Matrix
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay

@OptIn(UnstableApi::class)
fun buildVideoEffects(clip: VideoClip): List<Effect> {
    val effects = mutableListOf<Effect>()

    if (
        clip.rotationDegrees % 360 != 0 ||
        clip.flipHorizontal ||
        clip.flipVertical
    ) {
        effects += ScaleAndRotateTransformation.Builder()
            .setScale(
                if (clip.flipHorizontal) -1f else 1f,
                if (clip.flipVertical) -1f else 1f,
            )
            .setRotationDegrees(clip.rotationDegrees.toFloat())
            .build()
    }

    if (clip.crop > 0.001f) {
        val inset = clip.crop.coerceIn(0f, 0.45f) * 2f
        effects += Crop(
            -1f + inset,
            1f - inset,
            -1f + inset,
            1f - inset,
        )
    }

    if (kotlin.math.abs(clip.brightness) > 0.001f) {
        effects += Brightness(clip.brightness.coerceIn(-1f, 1f))
    }

    if (kotlin.math.abs(clip.contrast) > 0.001f) {
        effects += Contrast(clip.contrast.coerceIn(-1f, 1f))
    }

    if (
        kotlin.math.abs(clip.hue) > 0.001f ||
        kotlin.math.abs(clip.saturation) > 0.001f ||
        kotlin.math.abs(clip.lightness) > 0.001f
    ) {
        effects += HslAdjustment.Builder()
            .adjustHue(clip.hue)
            .adjustSaturation(clip.saturation.coerceIn(-100f, 100f))
            .adjustLightness(clip.lightness.coerceIn(-100f, 100f))
            .build()
    }

    if (clip.motion != ClipMotion.NONE) {
        val durationUs = (clip.sourceSliceDurationMs * 1000L).coerceAtLeast(1L)
        val strength = clip.motionStrength.coerceIn(0.03f, 0.35f)
        effects += MatrixTransformation { presentationTimeUs ->
            val progress = (presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
            Matrix().apply {
                when (clip.motion) {
                    ClipMotion.NONE -> Unit
                    ClipMotion.ZOOM_IN -> {
                        val scale = 1f + strength * progress
                        postScale(scale, scale)
                    }
                    ClipMotion.ZOOM_OUT -> {
                        val scale = 1f + strength * (1f - progress)
                        postScale(scale, scale)
                    }
                    ClipMotion.PAN_LEFT -> {
                        val scale = 1f + strength
                        postScale(scale, scale)
                        postTranslate(strength * (0.5f - progress), 0f)
                    }
                    ClipMotion.PAN_RIGHT -> {
                        val scale = 1f + strength
                        postScale(scale, scale)
                        postTranslate(strength * (progress - 0.5f), 0f)
                    }
                    ClipMotion.PAN_UP -> {
                        val scale = 1f + strength
                        postScale(scale, scale)
                        postTranslate(0f, strength * (progress - 0.5f))
                    }
                    ClipMotion.PAN_DOWN -> {
                        val scale = 1f + strength
                        postScale(scale, scale)
                        postTranslate(0f, strength * (0.5f - progress))
                    }
                }
            }
        }
    }

    val text = clip.overlayText.trim()
    if (text.isNotEmpty()) {
        val styled = SpannableString("  $text  ").apply {
            setSpan(
                ForegroundColorSpan(clip.textColor),
                0,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (clip.textBackground) {
                setSpan(
                    BackgroundColorSpan(0x99000000.toInt()),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            when {
                clip.textBold && clip.textItalic -> setSpan(
                    StyleSpan(Typeface.BOLD_ITALIC),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                clip.textBold -> setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                clip.textItalic -> setSpan(
                    StyleSpan(Typeface.ITALIC),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(
                clip.textX.coerceIn(-1f, 1f),
                clip.textY.coerceIn(-1f, 1f),
            )
            .setOverlayFrameAnchor(0f, -1f)
            .setScale(
                clip.textScale.coerceIn(0.25f, 1.6f),
                clip.textScale.coerceIn(0.25f, 1.6f),
            )
            .setRotationDegrees(clip.textRotation.coerceIn(-180f, 180f))
            .build()
        effects += OverlayEffect(
            listOf(TextOverlay.createStaticTextOverlay(styled, settings))
        )
    }

    return effects
}

@OptIn(UnstableApi::class)
fun buildCanvasEffects(settings: ExportSettings, includeResolution: Boolean): List<Effect> {
    val effects = mutableListOf<Effect>()
    settings.aspectRatio?.let { ratio ->
        effects += Presentation.createForAspectRatio(
            ratio,
            if (settings.cropToFill) {
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            } else {
                Presentation.LAYOUT_SCALE_TO_FIT
            },
        )
    }
    if (includeResolution) {
        effects += Presentation.createForHeight(settings.height)
    }
    return effects
}

@OptIn(UnstableApi::class)
class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed.coerceIn(0.25f, 4f)

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}
