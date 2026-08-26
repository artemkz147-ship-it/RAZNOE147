package ru.vibecut.editor

import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation

@OptIn(UnstableApi::class)
fun buildEasedKeyframeEffects(clip: VideoClip): List<Effect> {
    val frames = clip.keyframes.sortedBy { it.timeMs }
    if (frames.isEmpty()) return emptyList()
    return listOf(MatrixTransformation { presentationTimeUs ->
        val timeMs = (presentationTimeUs / 1000L).coerceIn(0L, clip.sourceSliceDurationMs)
        val first = frames.lastOrNull { it.timeMs <= timeMs } ?: frames.first()
        val second = frames.firstOrNull { it.timeMs >= timeMs } ?: frames.last()
        val raw = if (first.id == second.id) 0f else {
            ((timeMs - first.timeMs).toFloat() / (second.timeMs - first.timeMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        }
        val fraction = ease(raw, first.easing)
        fun lerp(a: Float, b: Float) = a + (b - a) * fraction
        Matrix().apply {
            val scale = lerp(first.scale, second.scale).coerceIn(.1f, 4f)
            postScale(scale, scale)
            postRotate(lerp(first.rotation, second.rotation))
            postTranslate(lerp(first.x, second.x), lerp(first.y, second.y))
        }
    })
}
