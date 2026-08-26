package ru.vibecut.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings

private data class ImageStickerTransform(
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float,
)

@OptIn(UnstableApi::class)
fun buildDynamicImageStickerEffects(context: Context, clip: VideoClip): List<Effect> {
    if (clip.stickers.isEmpty()) return emptyList()
    val overlays = clip.stickers.mapNotNull { layer ->
        runCatching { DynamicImageStickerOverlay(context, layer) }.getOrNull()
    }
    return if (overlays.isEmpty()) emptyList() else listOf(OverlayEffect(overlays))
}

@OptIn(UnstableApi::class)
private class DynamicImageStickerOverlay(
    context: Context,
    private val layer: StickerLayer,
) : BitmapOverlay() {
    private val bitmap: Bitmap = loadBitmap(context, Uri.parse(layer.uri))

    override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val timeMs = presentationTimeUs / 1000L
        val visible = timeMs >= layer.startMs && (layer.endMs == Long.MAX_VALUE || timeMs <= layer.endMs)
        val transform = imageTransformAt(layer, timeMs)
        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(transform.x.coerceIn(-1f, 1f), transform.y.coerceIn(-1f, 1f))
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(transform.scale.coerceIn(.02f, 4f), transform.scale.coerceIn(.02f, 4f))
            .setRotationDegrees(transform.rotation.coerceIn(-360f, 360f))
            .setAlphaScale(if (visible) layer.alpha.coerceIn(0f, 1f) else 0f)
            .build()
    }

    override fun release() {
        super.release()
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    companion object {
        private fun loadBitmap(context: Context, uri: Uri): Bitmap {
            fun stream() = if (uri.scheme == "file") uri.path?.let { java.io.FileInputStream(it) }
            else context.contentResolver.openInputStream(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            stream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            return stream()?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Не удалось прочитать стикер")
        }
    }
}

private fun imageTransformAt(layer: StickerLayer, timeMs: Long): ImageStickerTransform {
    var x = layer.x
    var y = layer.y
    var scale = layer.scale
    var rotation = layer.rotation
    if (layer.keyframes.isNotEmpty()) {
        val frame = sampleImageKeyframes(layer.keyframes, timeMs)
        x = frame.x; y = frame.y; scale = frame.scale; rotation = frame.rotation
    }
    if (layer.trackingPath.isNotEmpty()) {
        val point = sampleImageTracking(layer.trackingPath, timeMs)
        x = (point.x + x * .22f).coerceIn(-1f, 1f)
        y = (point.y + y * .22f).coerceIn(-1f, 1f)
        scale *= point.objectScale.coerceIn(.45f, 2.2f)
    }
    return ImageStickerTransform(x, y, scale, rotation)
}

private fun sampleImageKeyframes(frames: List<TransformKeyframe>, timeMs: Long): TransformKeyframe {
    val sorted = frames.sortedBy { it.timeMs }
    val first = sorted.lastOrNull { it.timeMs <= timeMs } ?: sorted.first()
    val second = sorted.firstOrNull { it.timeMs >= timeMs } ?: sorted.last()
    if (first.id == second.id) return first
    val raw = ((timeMs - first.timeMs).toFloat() / (second.timeMs - first.timeMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    val fraction = ease(raw, first.easing)
    fun lerp(a: Float, b: Float) = a + (b - a) * fraction
    return first.copy(timeMs = timeMs, x = lerp(first.x, second.x), y = lerp(first.y, second.y), scale = lerp(first.scale, second.scale), rotation = lerp(first.rotation, second.rotation))
}

private fun sampleImageTracking(path: List<TrackingPoint>, timeMs: Long): TrackingPoint {
    val sorted = path.sortedBy { it.timeMs }
    val first = sorted.lastOrNull { it.timeMs <= timeMs } ?: sorted.first()
    val second = sorted.firstOrNull { it.timeMs >= timeMs } ?: sorted.last()
    if (first.timeMs == second.timeMs) return first
    val fraction = ((timeMs - first.timeMs).toFloat() / (second.timeMs - first.timeMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float) = a + (b - a) * fraction
    return TrackingPoint(timeMs, lerp(first.x, second.x), lerp(first.y, second.y), lerp(first.objectScale, second.objectScale))
}
