package ru.vibecut.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun MotionPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    var editingStrength by remember { mutableStateOf(false) }
    var editingMask by remember { mutableStateOf(false) }
    var editingVignette by remember { mutableStateOf(false) }
    val liveClip by rememberUpdatedState(clip)
    val liveSnapshot by rememberUpdatedState(onSnapshot)
    val liveUpdate by rememberUpdatedState(onUpdate)

    DisposableEffect(clip.id) {
        var workingClip = clip
        var activeFrameId: String? = null
        var gestureTimeMs = 0L

        PreviewGestureBridge.enabled = true
        PreviewGestureBridge.onGestureStart = {
            workingClip = liveClip
            gestureTimeMs = EditorCursorState.clipPositionMs.coerceIn(0L, workingClip.sourceSliceDurationMs)
            activeFrameId = workingClip.keyframes
                .minByOrNull { abs(it.timeMs - gestureTimeMs) }
                ?.takeIf { abs(it.timeMs - gestureTimeMs) <= 100L }
                ?.id
            liveSnapshot()
        }
        PreviewGestureBridge.onTransform = { panX, panY, zoom, rotation ->
            var current = workingClip
            var frames = current.keyframes
            var id = activeFrameId

            if (id == null) {
                if (frames.isEmpty() && gestureTimeMs > 100L) {
                    frames = frames + TransformKeyframe(
                        id = UUID.randomUUID().toString(),
                        timeMs = 0L,
                    )
                }
                id = UUID.randomUUID().toString()
                frames = frames + TransformKeyframe(
                    id = id,
                    timeMs = gestureTimeMs,
                )
                activeFrameId = id
            }

            val targetId = id
            frames = frames.map { frame ->
                if (frame.id != targetId) frame else frame.copy(
                    x = (frame.x + panX / 220f).coerceIn(-1f, 1f),
                    y = (frame.y + panY / 220f).coerceIn(-1f, 1f),
                    scale = (frame.scale * zoom).coerceIn(.25f, 3f),
                    rotation = normalizeRotation(frame.rotation + rotation),
                )
            }.sortedBy { it.timeMs }

            current = current.copy(keyframes = frames)
            workingClip = current
            liveUpdate(current)
        }
        PreviewGestureBridge.onGestureEnd = {
            activeFrameId = null
        }

        onDispose { PreviewGestureBridge.clear() }
    }

    SectionCard("Жесты прямо на видео") {
        Text(
            "В этом разделе предпросмотр интерактивный: тяните кадр пальцем, щипком меняйте масштаб, двумя пальцами поворачивайте.",
            color = Color(0xFFD8CCFF),
        )
        Text(
            "Изменение записывается в ключевой кадр на текущей позиции ${formatTime(EditorCursorState.clipPositionMs)}.",
            color = Color(0xFF8F8F9C),
        )
    }

    SectionCard("Анимация клипа") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ClipMotion.entries.forEach { motion ->
                ChoiceButton(motion.title, clip.motion == motion) {
                    onSnapshot()
                    onUpdate(clip.copy(motion = motion))
                }
            }
        }

        if (clip.motion != ClipMotion.NONE) {
            Text("Сила анимации: ${(clip.motionStrength * 100).roundToInt()}%", color = Color.White)
            Slider(
                value = clip.motionStrength.coerceIn(0.03f, 0.35f),
                onValueChange = {
                    if (!editingStrength) { onSnapshot(); editingStrength = true }
                    onUpdate(clip.copy(motionStrength = it))
                },
                onValueChangeFinished = { editingStrength = false },
                valueRange = 0.03f..0.35f,
            )
            Text("Анимация рассчитывается по времени каждого кадра и сохраняется в экспорт.", color = Color(0xFF8F8F9C))
        }
    }

    SectionCard("Маска и виньетка") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            MaskType.entries.forEach { mask ->
                ChoiceButton(mask.title, clip.maskType == mask) {
                    onSnapshot()
                    onUpdate(clip.copy(maskType = mask))
                }
            }
        }
        if (clip.maskType != MaskType.NONE) {
            Text("Размер маски: ${(clip.maskSize * 100).roundToInt()}%", color = Color.White)
            Slider(
                value = clip.maskSize.coerceIn(0.25f, 1f),
                onValueChange = {
                    if (!editingMask) { onSnapshot(); editingMask = true }
                    onUpdate(clip.copy(maskSize = it))
                },
                onValueChangeFinished = { editingMask = false },
                valueRange = 0.25f..1f,
            )
        }
        Text("Виньетка: ${(clip.vignette * 100).roundToInt()}%", color = Color.White)
        Slider(
            value = clip.vignette.coerceIn(0f, 1f),
            onValueChange = {
                if (!editingVignette) { onSnapshot(); editingVignette = true }
                onUpdate(clip.copy(vignette = it))
            },
            onValueChangeFinished = { editingVignette = false },
            valueRange = 0f..1f,
        )
        ToolButton("Сбросить маску", {
            onSnapshot()
            onUpdate(clip.copy(maskType = MaskType.NONE, maskSize = 0.82f, vignette = 0f))
        })
    }
}

private fun normalizeRotation(value: Float): Float {
    var result = value
    while (result > 180f) result -= 360f
    while (result < -180f) result += 360f
    return result
}
