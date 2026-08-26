package ru.vibecut.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.roundToInt

private enum class TrackingApplyMode(val title: String) {
    CAMERA("Камера следует"),
    STICKER("Анимированный стикер"),
    IMAGE_STICKER("Мой стикер / картинка"),
}

@Composable
internal fun ObjectTrackingPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    val context = LocalContext.current
    val processor = remember { ObjectTrackProcessor(context) }
    var targetX by remember(clip.id) { mutableFloatStateOf(0f) }
    var targetY by remember(clip.id) { mutableFloatStateOf(0f) }
    var fps by remember { mutableIntStateOf(10) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(TrackingApplyMode.CAMERA) }
    var stickerKind by remember { mutableStateOf(AnimatedStickerKind.TARGET) }
    var imageStickerId by remember(clip.id, clip.stickers.size) { mutableStateOf(clip.stickers.firstOrNull()?.id) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(processor) { onDispose { processor.cancel() } }

    fun start() {
        if (busy) return
        if (mode == TrackingApplyMode.IMAGE_STICKER && imageStickerId == null) {
            status = "Сначала добавьте PNG / JPG / WebP в разделе «Изображения и стикеры»"
            return
        }
        busy = true
        progress = 0
        status = "Ищу объект и строю траекторию…"
        processor.track(
            clip = clip,
            targetX = targetX,
            targetY = targetY,
            sampleFps = fps,
            onProgress = { progress = it },
            onDone = { path ->
                busy = false
                onSnapshot()
                when (mode) {
                    TrackingApplyMode.CAMERA -> {
                        val keys = path.map { p ->
                            TransformKeyframe(
                                id = UUID.randomUUID().toString(),
                                timeMs = p.timeMs,
                                x = (-p.x * .38f).coerceIn(-.85f, .85f),
                                y = (-p.y * .38f).coerceIn(-.85f, .85f),
                                scale = (1.12f + (p.objectScale - 1f) * .16f).coerceIn(1.02f, 1.55f),
                                rotation = 0f,
                                easing = KeyframeEasing.EASE_IN_OUT,
                            )
                        }
                        onUpdate(clip.copy(keyframes = keys))
                        status = "Готово: камера следует за объектом · ${keys.size} ключей"
                    }
                    TrackingApplyMode.STICKER -> {
                        val layer = AnimatedStickerLayer(
                            id = UUID.randomUUID().toString(),
                            kind = stickerKind,
                            x = 0f,
                            y = 0f,
                            scale = .28f,
                            startMs = path.first().timeMs,
                            endMs = path.last().timeMs,
                            speed = 1f,
                            loop = true,
                            trackingPath = path,
                        )
                        onUpdate(clip.copy(animatedStickers = clip.animatedStickers + layer))
                        status = "Готово: «${stickerKind.title}» прикреплён к объекту"
                    }
                    TrackingApplyMode.IMAGE_STICKER -> {
                        val id = imageStickerId
                        val sticker = clip.stickers.firstOrNull { it.id == id }
                        if (sticker == null) {
                            status = "Выбранный стикер больше не найден"
                        } else {
                            onUpdate(
                                clip.copy(
                                    stickers = clip.stickers.map {
                                        if (it.id == sticker.id) it.copy(
                                            startMs = path.first().timeMs,
                                            endMs = path.last().timeMs,
                                            trackingPath = path,
                                        ) else it
                                    }
                                )
                            )
                            status = "Готово: «${sticker.name}» прикреплён к объекту"
                        }
                    }
                }
            },
            onError = { busy = false; status = it },
        )
    }

    SectionCard("Отслеживание объекта") {
        Text(
            "Укажите примерную точку объекта на первом кадре. Нейросеть найдёт объект рядом и сохранит редактируемую траекторию.",
            color = Color(0xFF9A9AA8),
        )
        Text("Точка X: ${(targetX * 100).roundToInt()}", color = Color.White)
        Slider(targetX, { targetX = it }, valueRange = -1f..1f)
        Text("Точка Y: ${(targetY * 100).roundToInt()}", color = Color.White)
        Slider(targetY, { targetY = it }, valueRange = -1f..1f)

        Text("Точность трекинга", color = Color.White)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(6, 10, 15, 20).forEach { value -> ChoiceButton("$value кадров/с", fps == value) { fps = value } }
        }

        Text("Что прикрепить к траектории", color = Color.White)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TrackingApplyMode.entries.forEach { item ->
                ChoiceButton(item.title, mode == item) {
                    mode = item
                    if (item == TrackingApplyMode.IMAGE_STICKER && imageStickerId == null) imageStickerId = clip.stickers.firstOrNull()?.id
                }
            }
        }

        if (mode == TrackingApplyMode.STICKER) {
            Text("Анимированный стикер", color = Color.White)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                AnimatedStickerKind.entries.forEach { kind -> ChoiceButton(kind.title, stickerKind == kind) { stickerKind = kind } }
            }
        }
        if (mode == TrackingApplyMode.IMAGE_STICKER) {
            Text("Импортированный стикер", color = Color.White)
            if (clip.stickers.isEmpty()) Text("Добавьте изображение в разделе «Изображения и стикеры»", color = Color(0xFFFFB4AB))
            else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                clip.stickers.forEach { sticker -> ChoiceButton(sticker.name, imageStickerId == sticker.id) { imageStickerId = sticker.id } }
            }
        }

        val action = when (mode) {
            TrackingApplyMode.CAMERA -> "Отследить и вести камерой"
            TrackingApplyMode.STICKER -> "Отследить и прикрепить анимацию"
            TrackingApplyMode.IMAGE_STICKER -> "Отследить и прикрепить картинку"
        }
        ToolButton(action, ::start, enabled = !busy)
        if (busy) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("Обработка: $progress%", color = Color(0xFFC4B5FD))
        }
        if (status.isNotBlank()) Text(status, color = Color(0xFFB9B9C5))
    }
}
