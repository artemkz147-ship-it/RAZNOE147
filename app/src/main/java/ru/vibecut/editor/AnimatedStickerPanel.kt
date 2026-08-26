package ru.vibecut.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun AnimatedStickerPanel(
    clip: VideoClip,
    positionMs: Long,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    var selectedId by remember(clip.id) { mutableStateOf(clip.animatedStickers.firstOrNull()?.id) }
    val selected = clip.animatedStickers.firstOrNull { it.id == selectedId }

    fun updateLayer(layer: AnimatedStickerLayer) {
        onUpdate(clip.copy(animatedStickers = clip.animatedStickers.map { if (it.id == layer.id) layer else it }))
    }

    SectionCard("Анимированные стикеры") {
        Text("Векторные анимации без интернета · слоёв: ${clip.animatedStickers.size}", color = Color(0xFF9A9AA8))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnimatedStickerKind.entries.forEach { kind ->
                ToolButton(kind.title, {
                    onSnapshot()
                    val layer = AnimatedStickerLayer(
                        id = UUID.randomUUID().toString(),
                        kind = kind,
                        startMs = positionMs.coerceAtLeast(0L),
                        endMs = clip.sourceSliceDurationMs,
                    )
                    onUpdate(clip.copy(animatedStickers = clip.animatedStickers + layer))
                    selectedId = layer.id
                })
            }
        }

        if (clip.animatedStickers.isNotEmpty()) {
            Text("Слои", color = Color.White)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                clip.animatedStickers.forEachIndexed { index, layer ->
                    ChoiceButton("${index + 1}. ${layer.kind.title}", selectedId == layer.id) { selectedId = layer.id }
                }
            }
        }

        selected?.let { layer ->
            Text("X: ${(layer.x * 100).roundToInt()}", color = Color.White)
            Slider(layer.x, { updateLayer(layer.copy(x = it)) }, valueRange = -1f..1f)
            Text("Y: ${(layer.y * 100).roundToInt()}", color = Color.White)
            Slider(layer.y, { updateLayer(layer.copy(y = it)) }, valueRange = -1f..1f)
            Text("Размер: ${(layer.scale * 100).roundToInt()}%", color = Color.White)
            Slider(layer.scale.coerceIn(.05f,2.5f), { updateLayer(layer.copy(scale = it)) }, valueRange = .05f..2.5f)
            Text("Поворот: ${layer.rotation.roundToInt()}°", color = Color.White)
            Slider(layer.rotation, { updateLayer(layer.copy(rotation = it)) }, valueRange = -180f..180f)
            Text("Прозрачность: ${(layer.alpha * 100).roundToInt()}%", color = Color.White)
            Slider(layer.alpha.coerceIn(0f,1f), { updateLayer(layer.copy(alpha = it)) }, valueRange = 0f..1f)
            Text("Скорость: ${"%.1f".format(layer.speed)}×", color = Color.White)
            Slider(layer.speed.coerceIn(.1f,5f), { updateLayer(layer.copy(speed = it)) }, valueRange = .1f..5f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Повторять", color = Color.White)
                Switch(layer.loop, { updateLayer(layer.copy(loop = it)) })
            }
            Text("Начало: ${formatTime(layer.startMs)}", color = Color.White)
            Slider(layer.startMs.toFloat().coerceIn(0f,clip.sourceSliceDurationMs.toFloat()), { updateLayer(layer.copy(startMs = it.toLong().coerceAtMost(layer.endMs - 50L))) }, valueRange = 0f..clip.sourceSliceDurationMs.coerceAtLeast(1L).toFloat())
            Text("Конец: ${formatTime(layer.endMs.coerceAtMost(clip.sourceSliceDurationMs))}", color = Color.White)
            Slider(layer.endMs.coerceAtMost(clip.sourceSliceDurationMs).toFloat(), { updateLayer(layer.copy(endMs = it.toLong().coerceAtLeast(layer.startMs + 50L))) }, valueRange = 0f..clip.sourceSliceDurationMs.coerceAtLeast(1L).toFloat())

            Text("Ключевые кадры с кривыми", color = Color.White)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolButton("Ключ в курсоре", {
                    onSnapshot()
                    val frame = TransformKeyframe(
                        id = UUID.randomUUID().toString(),
                        timeMs = positionMs.coerceIn(0L,clip.sourceSliceDurationMs),
                        x = layer.x,
                        y = layer.y,
                        scale = layer.scale,
                        rotation = layer.rotation,
                    )
                    updateLayer(layer.copy(keyframes = (layer.keyframes + frame).sortedBy { it.timeMs }))
                })
                ToolButton("Очистить ключи", { onSnapshot(); updateLayer(layer.copy(keyframes = emptyList())) }, enabled = layer.keyframes.isNotEmpty())
            }
            layer.keyframes.sortedBy { it.timeMs }.forEachIndexed { index, frame ->
                Text("Ключ ${index + 1} · ${formatTime(frame.timeMs)} · ${frame.easing.title}", color = Color(0xFFB9B9C5))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyframeEasing.entries.forEach { easing ->
                        ChoiceButton(easing.title, frame.easing == easing) {
                            updateLayer(layer.copy(keyframes = layer.keyframes.map { if (it.id == frame.id) it.copy(easing = easing) else it }))
                        }
                    }
                    ToolButton("Удалить ключ", { onSnapshot(); updateLayer(layer.copy(keyframes = layer.keyframes.filterNot { it.id == frame.id })) })
                }
            }

            ToolButton("Удалить анимированный слой", {
                onSnapshot()
                onUpdate(clip.copy(animatedStickers = clip.animatedStickers.filterNot { it.id == layer.id }))
                selectedId = clip.animatedStickers.firstOrNull { it.id != layer.id }?.id
            })
        }
    }
}
