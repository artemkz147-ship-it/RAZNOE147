package ru.vibecut.editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import kotlin.math.max

@OptIn(UnstableApi::class)
fun buildTrackedObjectOverlayEffects(clip: VideoClip): List<Effect> {
    if (clip.trackedOverlays.isEmpty()) return emptyList()
    return listOf(OverlayEffect(clip.trackedOverlays.map { TrackedObjectCanvasOverlay(it) }))
}

@OptIn(UnstableApi::class)
private class TrackedObjectCanvasOverlay(
    private val layer: TrackedObjectOverlay,
) : CanvasOverlay(true) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        if (layer.trackingPath.isEmpty()) return
        val point = sampleTrackingPoint(layer.trackingPath, presentationTimeUs / 1000L)
        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        val cx = (point.x + 1f) * .5f * w
        val cy = (1f - point.y) * .5f * h
        val pad = layer.padding.coerceIn(0f, .65f)
        val bw = (point.width.coerceIn(.015f, 1f) * w * (1f + pad)).coerceAtLeast(w * .025f)
        val bh = (point.height.coerceIn(.015f, 1f) * h * (1f + pad)).coerceAtLeast(h * .025f)
        val rect = RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
        val alpha = (255f * layer.alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        when (layer.style) {
            TrackedOverlayStyle.BLACK_BOX -> {
                fill.color = Color.argb(alpha, 0, 0, 0)
                canvas.drawRoundRect(rect, max(4f, bw * .05f), max(4f, bh * .05f), fill)
            }
            TrackedOverlayStyle.MOSAIC -> drawMosaic(canvas, rect, alpha)
            TrackedOverlayStyle.FRAME -> {
                stroke.color = withAlpha(layer.color, alpha)
                stroke.strokeWidth = max(3f, minOf(w, h) * .008f)
                canvas.drawRoundRect(rect, max(4f, bw * .06f), max(4f, bh * .06f), stroke)
                drawCorners(canvas, rect, stroke)
            }
            TrackedOverlayStyle.HIGHLIGHT -> {
                fill.color = withAlpha(layer.color, (alpha * .26f).toInt())
                stroke.color = withAlpha(layer.color, alpha)
                stroke.strokeWidth = max(3f, minOf(w, h) * .006f)
                canvas.drawRoundRect(rect, max(8f, bw * .12f), max(8f, bh * .12f), fill)
                canvas.drawRoundRect(rect, max(8f, bw * .12f), max(8f, bh * .12f), stroke)
            }
        }
    }

    private fun drawMosaic(canvas: Canvas, rect: RectF, alpha: Int) {
        val cols = 7
        val rows = 7
        val cellW = rect.width() / cols
        val cellH = rect.height() / rows
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val seed = (x * 31 + y * 17) % 5
                val shade = when (seed) { 0 -> 40; 1 -> 75; 2 -> 110; 3 -> 145; else -> 185 }
                fill.color = Color.argb(alpha, shade, shade, shade)
                val left = rect.left + x * cellW
                val top = rect.top + y * cellH
                canvas.drawRect(left, top, left + cellW + 1f, top + cellH + 1f, fill)
            }
        }
    }

    private fun drawCorners(canvas: Canvas, r: RectF, p: Paint) {
        val len = minOf(r.width(), r.height()) * .18f
        canvas.drawLine(r.left, r.top, r.left + len, r.top, p); canvas.drawLine(r.left, r.top, r.left, r.top + len, p)
        canvas.drawLine(r.right, r.top, r.right - len, r.top, p); canvas.drawLine(r.right, r.top, r.right, r.top + len, p)
        canvas.drawLine(r.left, r.bottom, r.left + len, r.bottom, p); canvas.drawLine(r.left, r.bottom, r.left, r.bottom - len, p)
        canvas.drawLine(r.right, r.bottom, r.right - len, r.bottom, p); canvas.drawLine(r.right, r.bottom, r.right, r.bottom - len, p)
    }
}

private fun sampleTrackingPoint(path: List<TrackingPoint>, timeMs: Long): TrackingPoint {
    val sorted = path.sortedBy { it.timeMs }
    val a = sorted.lastOrNull { it.timeMs <= timeMs } ?: sorted.first()
    val b = sorted.firstOrNull { it.timeMs >= timeMs } ?: sorted.last()
    if (a.timeMs == b.timeMs) return a
    val f = ((timeMs - a.timeMs).toFloat() / (b.timeMs - a.timeMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
    fun l(x: Float, y: Float) = x + (y - x) * f
    return TrackingPoint(timeMs, l(a.x,b.x), l(a.y,b.y), l(a.objectScale,b.objectScale), l(a.width,b.width), l(a.height,b.height))
}

private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha.coerceIn(0,255), Color.red(color), Color.green(color), Color.blue(color))
