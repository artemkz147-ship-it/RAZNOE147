package ru.vibecut.editor

import android.content.Context
import android.graphics.Matrix
import android.graphics.Typeface
import android.net.Uri
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
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@OptIn(UnstableApi::class)
fun buildVideoEffects(
    context: Context,
    clip: VideoClip,
    incomingTransition: TransitionSpec? = null,
): List<Effect> {
    val effects = mutableListOf<Effect>()

    if (clip.rotationDegrees % 360 != 0 || clip.flipHorizontal || clip.flipVertical) {
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
        effects += Crop(-1f + inset, 1f - inset, -1f + inset, 1f - inset)
    }

    if (abs(clip.brightness) > 0.001f) {
        effects += Brightness(clip.brightness.coerceIn(-1f, 1f))
    }
    if (abs(clip.contrast) > 0.001f) {
        effects += Contrast(clip.contrast.coerceIn(-1f, 1f))
    }
    if (
        abs(clip.hue) > 0.001f ||
        abs(clip.saturation) > 0.001f ||
        abs(clip.lightness) > 0.001f
    ) {
        effects += HslAdjustment.Builder()
            .adjustHue(clip.hue)
            .adjustSaturation(clip.saturation.coerceIn(-100f, 100f))
            .adjustLightness(clip.lightness.coerceIn(-100f, 100f))
            .build()
    }

    when (clip.colorEffect) {
        ColorEffect.NONE -> Unit
        ColorEffect.GRAYSCALE -> effects += RgbFilter.createGrayscaleFilter()
        ColorEffect.INVERT -> effects += RgbFilter.createInvertedFilter()
        ColorEffect.SEPIA -> effects += sepiaEffect()
        ColorEffect.WARM -> effects += rgbScaleEffect(1.13f, 1.03f, 0.86f)
        ColorEffect.COLD -> effects += rgbScaleEffect(0.86f, 1.03f, 1.16f)
        ColorEffect.VINTAGE -> {
            effects += sepiaEffect()
            effects += Contrast(-0.08f)
            effects += rgbScaleEffect(1.02f, 0.96f, 0.84f)
        }
        ColorEffect.NIGHT -> effects += rgbScaleEffect(0.62f, 0.82f, 1.22f)
        ColorEffect.CYAN -> effects += rgbScaleEffect(0.82f, 1.12f, 1.18f)
        ColorEffect.PINK -> effects += rgbScaleEffect(1.18f, 0.86f, 1.08f)
    }

    if (
        abs(clip.redScale - 1f) > 0.001f ||
        abs(clip.greenScale - 1f) > 0.001f ||
        abs(clip.blueScale - 1f) > 0.001f
    ) {
        effects += RgbAdjustment.Builder()
            .setRedScale(clip.redScale.coerceIn(0f, 2f))
            .setGreenScale(clip.greenScale.coerceIn(0f, 2f))
            .setBlueScale(clip.blueScale.coerceIn(0f, 2f))
            .build()
    }

    createMotionEffect(clip)?.let(effects::add)
    createKeyframeEffect(clip)?.let(effects::add)

    val overlays = mutableListOf<TextureOverlay>()
    buildTextOverlay(clip)?.let(overlays::add)
    clip.stickers.forEach { sticker ->
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(
                sticker.x.coerceIn(-1f, 1f),
                sticker.y.coerceIn(-1f, 1f),
            )
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(
                sticker.scale.coerceIn(0.05f, 2.5f),
                sticker.scale.coerceIn(0.05f, 2.5f),
            )
            .setRotationDegrees(sticker.rotation.coerceIn(-180f, 180f))
            .setAlphaScale(sticker.alpha.coerceIn(0f, 1f))
            .build()
        overlays += BitmapOverlay.createStaticBitmapOverlay(
            context,
            Uri.parse(sticker.uri),
            settings,
        )
    }
    if (clip.maskType != MaskType.NONE || clip.vignette > 0.001f) {
        overlays += ClipMaskOverlay(clip.maskType, clip.maskSize, clip.vignette)
    }
    if (overlays.isNotEmpty()) {
        effects += OverlayEffect(overlays)
    }

    val outgoing = TransitionSpec(clip.transitionOut, clip.transitionDurationMs)
    createTransitionGeometryEffect(clip, incomingTransition, outgoing)?.let(effects::add)
    createTransitionColorEffect(clip, incomingTransition, outgoing)?.let(effects::add)
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
    if (includeResolution) effects += Presentation.createForHeight(settings.height)
    return effects
}

@OptIn(UnstableApi::class)
fun buildCompositionVideoEffects(
    settings: ExportSettings,
    subtitles: List<SubtitleCue>,
    subtitleStyle: SubtitleStyle,
): List<Effect> {
    val effects = buildCanvasEffects(settings, true).toMutableList()
    if (subtitles.isNotEmpty()) {
        effects += OverlayEffect(listOf(SubtitleCanvasOverlay(subtitles, subtitleStyle)))
    }
    return effects
}

@OptIn(UnstableApi::class)
private fun createMotionEffect(clip: VideoClip): Effect? {
    if (clip.motion == ClipMotion.NONE) return null
    val durationUs = (clip.sourceSliceDurationMs * 1000L).coerceAtLeast(1L)
    val strength = clip.motionStrength.coerceIn(0.03f, 0.35f)
    return MatrixTransformation { us ->
        val progress = (us.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
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

@OptIn(UnstableApi::class)
private fun createKeyframeEffect(clip: VideoClip): Effect? {
    val frames = clip.keyframes.sortedBy { it.timeMs }
    if (frames.isEmpty()) return null
    return MatrixTransformation { us ->
        val timeMs = (us / 1000L).coerceIn(0L, clip.sourceSliceDurationMs)
        val first = frames.lastOrNull { it.timeMs <= timeMs } ?: frames.first()
        val second = frames.firstOrNull { it.timeMs >= timeMs } ?: frames.last()
        val span = (second.timeMs - first.timeMs).coerceAtLeast(1L)
        val fraction = if (first.id == second.id) {
            0f
        } else {
            ((timeMs - first.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
        Matrix().apply {
            val scale = lerp(first.scale, second.scale, fraction).coerceIn(0.1f, 4f)
            postScale(scale, scale)
            postRotate(lerp(first.rotation, second.rotation, fraction))
            postTranslate(
                lerp(first.x, second.x, fraction),
                lerp(first.y, second.y, fraction),
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun createTransitionGeometryEffect(
    clip: VideoClip,
    incoming: TransitionSpec?,
    outgoing: TransitionSpec,
): Effect? {
    val geometryTransitions = setOf(
        TransitionType.SLIDE_LEFT,
        TransitionType.SLIDE_RIGHT,
        TransitionType.SLIDE_UP,
        TransitionType.SLIDE_DOWN,
        TransitionType.ZOOM,
        TransitionType.SPIN,
        TransitionType.ROTATE_LEFT,
        TransitionType.ROTATE_RIGHT,
        TransitionType.PULSE,
        TransitionType.SHAKE,
    )
    val types = listOfNotNull(incoming?.type, outgoing.type)
    if (types.none { it in geometryTransitions }) return null

    val duration = clip.sourceSliceDurationMs.coerceAtLeast(1L)
    return MatrixTransformation { us ->
        val timeMs = us / 1000L
        Matrix().apply {
            incoming?.takeIf { it.type != TransitionType.NONE }?.let { spec ->
                val span = transitionSpan(spec.durationMs, clip.speed, duration)
                if (timeMs < span) {
                    applyTransitionMatrix(
                        this,
                        spec.type,
                        (timeMs.toFloat() / span).coerceIn(0f, 1f),
                        true,
                    )
                }
            }
            if (outgoing.type != TransitionType.NONE) {
                val span = transitionSpan(outgoing.durationMs, clip.speed, duration)
                val start = (duration - span).coerceAtLeast(0L)
                if (timeMs >= start) {
                    applyTransitionMatrix(
                        this,
                        outgoing.type,
                        ((timeMs - start).toFloat() / span).coerceIn(0f, 1f),
                        false,
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun createTransitionColorEffect(
    clip: VideoClip,
    incoming: TransitionSpec?,
    outgoing: TransitionSpec,
): Effect? {
    val colorTransitions = setOf(TransitionType.FADE, TransitionType.FLASH)
    if (incoming?.type !in colorTransitions && outgoing.type !in colorTransitions) return null
    val duration = clip.sourceSliceDurationMs.coerceAtLeast(1L)
    return object : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val timeMs = presentationTimeUs / 1000L
            var factor = 1f
            incoming?.let { spec ->
                val span = transitionSpan(spec.durationMs, clip.speed, duration)
                if (timeMs < span) {
                    val progress = (timeMs.toFloat() / span).coerceIn(0f, 1f)
                    factor *= when (spec.type) {
                        TransitionType.FADE -> progress
                        TransitionType.FLASH -> 1f + 1.6f * (1f - progress)
                        else -> 1f
                    }
                }
            }
            if (outgoing.type != TransitionType.NONE) {
                val span = transitionSpan(outgoing.durationMs, clip.speed, duration)
                val start = (duration - span).coerceAtLeast(0L)
                if (timeMs >= start) {
                    val progress = ((timeMs - start).toFloat() / span).coerceIn(0f, 1f)
                    factor *= when (outgoing.type) {
                        TransitionType.FADE -> 1f - progress
                        TransitionType.FLASH -> 1f + 1.6f * progress
                        else -> 1f
                    }
                }
            }
            return diagonalRgbMatrix(factor.coerceIn(0f, 3f))
        }
    }
}

private fun applyTransitionMatrix(
    matrix: Matrix,
    type: TransitionType,
    progress: Float,
    entering: Boolean,
) {
    val amount = if (entering) 1f - progress else progress
    when (type) {
        TransitionType.SLIDE_LEFT -> matrix.postTranslate(if (entering) 2f * amount else -2f * amount, 0f)
        TransitionType.SLIDE_RIGHT -> matrix.postTranslate(if (entering) -2f * amount else 2f * amount, 0f)
        TransitionType.SLIDE_UP -> matrix.postTranslate(0f, if (entering) -2f * amount else 2f * amount)
        TransitionType.SLIDE_DOWN -> matrix.postTranslate(0f, if (entering) 2f * amount else -2f * amount)
        TransitionType.ZOOM -> {
            val scale = if (entering) 0.25f + 0.75f * progress else 1f + 0.65f * progress
            matrix.postScale(scale, scale)
        }
        TransitionType.SPIN -> {
            val scale = 1f - 0.45f * amount
            matrix.postScale(scale, scale)
            matrix.postRotate((if (entering) -110f else 110f) * amount)
        }
        TransitionType.ROTATE_LEFT -> matrix.postRotate(-70f * amount)
        TransitionType.ROTATE_RIGHT -> matrix.postRotate(70f * amount)
        TransitionType.PULSE -> {
            val wave = sin(progress * PI).toFloat()
            val scale = if (entering) 1f - 0.28f * wave else 1f + 0.28f * wave
            matrix.postScale(scale, scale)
        }
        TransitionType.SHAKE -> {
            val damping = if (entering) amount else 1f - amount
            val x = sin(progress * PI * 10.0).toFloat() * 0.12f * damping
            val y = sin(progress * PI * 7.0).toFloat() * 0.06f * damping
            matrix.postTranslate(x, y)
        }
        else -> Unit
    }
}

@OptIn(UnstableApi::class)
private fun buildTextOverlay(clip: VideoClip): TextureOverlay? {
    val text = clip.overlayText.trim()
    if (text.isEmpty()) return null
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
    return TextOverlay.createStaticTextOverlay(styled, settings)
}

@OptIn(UnstableApi::class)
private fun sepiaEffect(): Effect = object : RgbMatrix {
    private val matrix = floatArrayOf(
        0.393f, 0.349f, 0.272f, 0f,
        0.769f, 0.686f, 0.534f, 0f,
        0.189f, 0.168f, 0.131f, 0f,
        0f, 0f, 0f, 1f,
    )
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
}

@OptIn(UnstableApi::class)
private fun rgbScaleEffect(red: Float, green: Float, blue: Float): Effect =
    RgbAdjustment.Builder()
        .setRedScale(red)
        .setGreenScale(green)
        .setBlueScale(blue)
        .build()

private fun diagonalRgbMatrix(value: Float) = floatArrayOf(
    value, 0f, 0f, 0f,
    0f, value, 0f, 0f,
    0f, 0f, value, 0f,
    0f, 0f, 0f, 1f,
)

private fun transitionSpan(ms: Long, speed: Float, duration: Long) =
    (ms.coerceIn(120L, 2500L) * speed.coerceIn(0.25f, 4f))
        .toLong()
        .coerceIn(1L, (duration / 2).coerceAtLeast(1L))

private fun lerp(a: Float, b: Float, fraction: Float) = a + (b - a) * fraction

@OptIn(UnstableApi::class)
class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long) = speed.coerceIn(0.25f, 4f)
    override fun getNextSpeedChangeTimeUs(timeUs: Long) = C.TIME_UNSET
}
