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
import kotlin.math.abs

@OptIn(UnstableApi::class)
fun buildVideoEffects(context: Context, clip: VideoClip, incomingTransition: TransitionSpec? = null): List<Effect> {
    val effects = mutableListOf<Effect>()
    if (clip.rotationDegrees % 360 != 0 || clip.flipHorizontal || clip.flipVertical) {
        effects += ScaleAndRotateTransformation.Builder()
            .setScale(if (clip.flipHorizontal) -1f else 1f, if (clip.flipVertical) -1f else 1f)
            .setRotationDegrees(clip.rotationDegrees.toFloat()).build()
    }
    if (clip.crop > 0.001f) {
        val inset = clip.crop.coerceIn(0f, 0.45f) * 2f
        effects += Crop(-1f + inset, 1f - inset, -1f + inset, 1f - inset)
    }
    if (abs(clip.brightness) > 0.001f) effects += Brightness(clip.brightness.coerceIn(-1f, 1f))
    if (abs(clip.contrast) > 0.001f) effects += Contrast(clip.contrast.coerceIn(-1f, 1f))
    if (abs(clip.hue) > 0.001f || abs(clip.saturation) > 0.001f || abs(clip.lightness) > 0.001f) {
        effects += HslAdjustment.Builder().adjustHue(clip.hue)
            .adjustSaturation(clip.saturation.coerceIn(-100f, 100f))
            .adjustLightness(clip.lightness.coerceIn(-100f, 100f)).build()
    }
    when (clip.colorEffect) {
        ColorEffect.NONE -> Unit
        ColorEffect.GRAYSCALE -> effects += RgbFilter.createGrayscaleFilter()
        ColorEffect.INVERT -> effects += RgbFilter.createInvertedFilter()
        ColorEffect.SEPIA -> effects += sepiaEffect()
    }
    if (abs(clip.redScale - 1f) > 0.001f || abs(clip.greenScale - 1f) > 0.001f || abs(clip.blueScale - 1f) > 0.001f) {
        effects += RgbAdjustment.Builder().setRedScale(clip.redScale.coerceIn(0f, 2f))
            .setGreenScale(clip.greenScale.coerceIn(0f, 2f)).setBlueScale(clip.blueScale.coerceIn(0f, 2f)).build()
    }
    createMotionEffect(clip)?.let(effects::add)
    createKeyframeEffect(clip)?.let(effects::add)
    val overlays = mutableListOf<TextureOverlay>()
    buildTextOverlay(clip)?.let(overlays::add)
    clip.stickers.forEach { sticker ->
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(sticker.x.coerceIn(-1f, 1f), sticker.y.coerceIn(-1f, 1f))
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(sticker.scale.coerceIn(0.05f, 2.5f), sticker.scale.coerceIn(0.05f, 2.5f))
            .setRotationDegrees(sticker.rotation.coerceIn(-180f, 180f))
            .setAlphaScale(sticker.alpha.coerceIn(0f, 1f)).build()
        overlays += BitmapOverlay.createStaticBitmapOverlay(context, Uri.parse(sticker.uri), settings)
    }
    if (overlays.isNotEmpty()) effects += OverlayEffect(overlays)
    val outgoing = TransitionSpec(clip.transitionOut, clip.transitionDurationMs)
    createTransitionGeometryEffect(clip, incomingTransition, outgoing)?.let(effects::add)
    createTransitionColorEffect(clip, incomingTransition, outgoing)?.let(effects::add)
    return effects
}

@OptIn(UnstableApi::class)
fun buildCanvasEffects(settings: ExportSettings, includeResolution: Boolean): List<Effect> {
    val effects = mutableListOf<Effect>()
    settings.aspectRatio?.let { ratio -> effects += Presentation.createForAspectRatio(ratio, if (settings.cropToFill) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT) }
    if (includeResolution) effects += Presentation.createForHeight(settings.height)
    return effects
}

@OptIn(UnstableApi::class)
fun buildCompositionVideoEffects(settings: ExportSettings, subtitles: List<SubtitleCue>, subtitleStyle: SubtitleStyle): List<Effect> {
    val effects = buildCanvasEffects(settings, true).toMutableList()
    if (subtitles.isNotEmpty()) effects += OverlayEffect(listOf(SubtitleCanvasOverlay(subtitles, subtitleStyle)))
    return effects
}

@OptIn(UnstableApi::class)
private fun createMotionEffect(clip: VideoClip): Effect? {
    if (clip.motion == ClipMotion.NONE) return null
    val durationUs = (clip.sourceSliceDurationMs * 1000L).coerceAtLeast(1L); val strength = clip.motionStrength.coerceIn(0.03f, 0.35f)
    return MatrixTransformation { us ->
        val p = (us.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
        Matrix().apply {
            when (clip.motion) {
                ClipMotion.NONE -> Unit
                ClipMotion.ZOOM_IN -> { val s = 1f + strength * p; postScale(s, s) }
                ClipMotion.ZOOM_OUT -> { val s = 1f + strength * (1f - p); postScale(s, s) }
                ClipMotion.PAN_LEFT -> { val s = 1f + strength; postScale(s, s); postTranslate(strength * (0.5f - p), 0f) }
                ClipMotion.PAN_RIGHT -> { val s = 1f + strength; postScale(s, s); postTranslate(strength * (p - 0.5f), 0f) }
                ClipMotion.PAN_UP -> { val s = 1f + strength; postScale(s, s); postTranslate(0f, strength * (p - 0.5f)) }
                ClipMotion.PAN_DOWN -> { val s = 1f + strength; postScale(s, s); postTranslate(0f, strength * (0.5f - p)) }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun createKeyframeEffect(clip: VideoClip): Effect? {
    val frames = clip.keyframes.sortedBy { it.timeMs }; if (frames.isEmpty()) return null
    return MatrixTransformation { us ->
        val t = (us / 1000L).coerceIn(0L, clip.sourceSliceDurationMs)
        val a = frames.lastOrNull { it.timeMs <= t } ?: frames.first(); val b = frames.firstOrNull { it.timeMs >= t } ?: frames.last()
        val span = (b.timeMs - a.timeMs).coerceAtLeast(1L); val f = if (a.id == b.id) 0f else ((t - a.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        Matrix().apply { val s = lerp(a.scale, b.scale, f).coerceIn(0.1f, 4f); postScale(s, s); postRotate(lerp(a.rotation, b.rotation, f)); postTranslate(lerp(a.x, b.x, f), lerp(a.y, b.y, f)) }
    }
}

@OptIn(UnstableApi::class)
private fun createTransitionGeometryEffect(clip: VideoClip, incoming: TransitionSpec?, outgoing: TransitionSpec): Effect? {
    val types = listOfNotNull(incoming?.type, outgoing.type)
    if (types.none { it in setOf(TransitionType.SLIDE_LEFT, TransitionType.SLIDE_RIGHT, TransitionType.ZOOM, TransitionType.SPIN) }) return null
    val duration = clip.sourceSliceDurationMs.coerceAtLeast(1L)
    return MatrixTransformation { us ->
        val t = us / 1000L
        Matrix().apply {
            incoming?.takeIf { it.type != TransitionType.NONE }?.let { spec -> val span = transitionSpan(spec.durationMs, clip.speed, duration); if (t < span) applyTransitionMatrix(this, spec.type, (t.toFloat() / span).coerceIn(0f,1f), true) }
            if (outgoing.type != TransitionType.NONE) { val span = transitionSpan(outgoing.durationMs, clip.speed, duration); val start = (duration-span).coerceAtLeast(0L); if (t >= start) applyTransitionMatrix(this, outgoing.type, ((t-start).toFloat()/span).coerceIn(0f,1f), false) }
        }
    }
}

@OptIn(UnstableApi::class)
private fun createTransitionColorEffect(clip: VideoClip, incoming: TransitionSpec?, outgoing: TransitionSpec): Effect? {
    if (incoming?.type !in setOf(TransitionType.FADE, TransitionType.FLASH) && outgoing.type !in setOf(TransitionType.FADE, TransitionType.FLASH)) return null
    val duration = clip.sourceSliceDurationMs.coerceAtLeast(1L)
    return object : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val t = presentationTimeUs / 1000L; var factor = 1f
            incoming?.let { spec -> val span=transitionSpan(spec.durationMs,clip.speed,duration); if(t<span){val p=(t.toFloat()/span).coerceIn(0f,1f); factor*=if(spec.type==TransitionType.FADE)p else if(spec.type==TransitionType.FLASH)1f+1.6f*(1f-p) else 1f}}
            if(outgoing.type!=TransitionType.NONE){val span=transitionSpan(outgoing.durationMs,clip.speed,duration);val start=(duration-span).coerceAtLeast(0L);if(t>=start){val p=((t-start).toFloat()/span).coerceIn(0f,1f);factor*=if(outgoing.type==TransitionType.FADE)1f-p else if(outgoing.type==TransitionType.FLASH)1f+1.6f*p else 1f}}
            return diagonalRgbMatrix(factor.coerceIn(0f,3f))
        }
    }
}

private fun applyTransitionMatrix(m: Matrix, type: TransitionType, progress: Float, entering: Boolean) {
    val a = if (entering) 1f-progress else progress
    when(type){
        TransitionType.SLIDE_LEFT -> m.postTranslate(if(entering)2f*a else -2f*a,0f)
        TransitionType.SLIDE_RIGHT -> m.postTranslate(if(entering)-2f*a else 2f*a,0f)
        TransitionType.ZOOM -> {val s=if(entering)0.25f+0.75f*progress else 1f+0.65f*progress;m.postScale(s,s)}
        TransitionType.SPIN -> {val s=1f-0.45f*a;m.postScale(s,s);m.postRotate((if(entering)-110f else 110f)*a)}
        else -> Unit
    }
}

@OptIn(UnstableApi::class)
private fun buildTextOverlay(c: VideoClip): TextureOverlay? {
    val text=c.overlayText.trim(); if(text.isEmpty())return null
    val styled=SpannableString("  $text  ").apply{setSpan(ForegroundColorSpan(c.textColor),0,length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);if(c.textBackground)setSpan(BackgroundColorSpan(0x99000000.toInt()),0,length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);when{c.textBold&&c.textItalic->setSpan(StyleSpan(Typeface.BOLD_ITALIC),0,length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);c.textBold->setSpan(StyleSpan(Typeface.BOLD),0,length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);c.textItalic->setSpan(StyleSpan(Typeface.ITALIC),0,length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)}}
    val s=StaticOverlaySettings.Builder().setBackgroundFrameAnchor(c.textX.coerceIn(-1f,1f),c.textY.coerceIn(-1f,1f)).setOverlayFrameAnchor(0f,-1f).setScale(c.textScale.coerceIn(.25f,1.6f),c.textScale.coerceIn(.25f,1.6f)).setRotationDegrees(c.textRotation.coerceIn(-180f,180f)).build()
    return TextOverlay.createStaticTextOverlay(styled,s)
}

@OptIn(UnstableApi::class)
private fun sepiaEffect(): Effect = object : RgbMatrix { private val m=floatArrayOf(.393f,.349f,.272f,0f,.769f,.686f,.534f,0f,.189f,.168f,.131f,0f,0f,0f,0f,1f);override fun getMatrix(presentationTimeUs:Long,useHdr:Boolean)=m }
private fun diagonalRgbMatrix(v:Float)=floatArrayOf(v,0f,0f,0f,0f,v,0f,0f,0f,0f,v,0f,0f,0f,0f,1f)
private fun transitionSpan(ms:Long,speed:Float,duration:Long)=(ms.coerceIn(120L,2500L)*speed.coerceIn(.25f,4f)).toLong().coerceIn(1L,(duration/2).coerceAtLeast(1L))
private fun lerp(a:Float,b:Float,f:Float)=a+(b-a)*f

@OptIn(UnstableApi::class)
class ConstantSpeedProvider(private val speed:Float):SpeedProvider{override fun getSpeed(timeUs:Long)=speed.coerceIn(.25f,4f);override fun getNextSpeedChangeTimeUs(timeUs:Long)=C.TIME_UNSET}
