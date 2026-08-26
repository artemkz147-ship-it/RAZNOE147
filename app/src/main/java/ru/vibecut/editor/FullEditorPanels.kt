package ru.vibecut.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AutoMontageStyle(val title: String) {
    DYNAMIC("Динамично"),
    CALM("Спокойно"),
    TRAVEL("Путешествие"),
    REELS("Короткий ролик"),
}

@Composable
private fun RangeControl(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String = value.roundToInt().toString(),
    onChange: (Float) -> Unit,
    onFinished: () -> Unit = {},
) {
    Text("$title: $display", color = Color.White)
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        onValueChangeFinished = onFinished,
        valueRange = range,
    )
}

@Composable
internal fun FullEditorHeader(
    projectName: String,
    clipCount: Int,
    exportState: ExportState,
    exportProgress: Int,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262E)),
            ) {
                Text("Проекты")
            }
            OutlinedTextField(
                value = projectName,
                onValueChange = { onNameChange(it.take(60)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Название проекта") },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (clipCount == 0) "Новый монтаж" else "Клипов: $clipCount",
                color = Color(0xFF9A9AA8),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(
                    onClick = onImport,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262E)),
                ) {
                    Text("Добавить видео")
                }
                Button(
                    onClick = onExport,
                    enabled = clipCount > 0 && exportState != ExportState.EXPORTING,
                ) {
                    if (exportState == ExportState.EXPORTING) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(" $exportProgress%")
                    } else {
                        Text("Экспорт")
                    }
                }
            }
        }
    }
}

@Composable
internal fun AutoMontagePanel(onApply: (AutoMontageStyle) -> Unit) {
    SectionCard("Монтаж в один тап") {
        Text(
            "Готовая комбинация переходов, движения и цветокоррекции. После применения всё можно менять вручную.",
            color = Color(0xFF9A9AA8),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AutoMontageStyle.entries.forEach { style ->
                ToolButton(text = style.title, onClick = { onApply(style) })
            }
        }
    }
}

@Composable
internal fun ColorEffectsPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    fun beginEdit() {
        if (!editing) {
            onSnapshot()
            editing = true
        }
    }

    SectionCard("Цветовые эффекты") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ColorEffect.entries.forEach { effect ->
                ChoiceButton(effect.title, clip.colorEffect == effect) {
                    onSnapshot()
                    onUpdate(clip.copy(colorEffect = effect))
                }
            }
        }
        RangeControl(
            title = "Красный канал",
            value = clip.redScale,
            range = 0f..2f,
            display = "${(clip.redScale * 100).roundToInt()}%",
            onChange = { beginEdit(); onUpdate(clip.copy(redScale = it)) },
            onFinished = { editing = false },
        )
        RangeControl(
            title = "Зелёный канал",
            value = clip.greenScale,
            range = 0f..2f,
            display = "${(clip.greenScale * 100).roundToInt()}%",
            onChange = { beginEdit(); onUpdate(clip.copy(greenScale = it)) },
            onFinished = { editing = false },
        )
        RangeControl(
            title = "Синий канал",
            value = clip.blueScale,
            range = 0f..2f,
            display = "${(clip.blueScale * 100).roundToInt()}%",
            onChange = { beginEdit(); onUpdate(clip.copy(blueScale = it)) },
            onFinished = { editing = false },
        )
        ToolButton(
            text = "Сбросить",
            onClick = {
                onSnapshot()
                onUpdate(
                    clip.copy(
                        colorEffect = ColorEffect.NONE,
                        redScale = 1f,
                        greenScale = 1f,
                        blueScale = 1f,
                    )
                )
            },
        )
    }
}

@Composable
internal fun TransitionPanel(
    clip: VideoClip,
    hasNext: Boolean,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    SectionCard("Переход к следующему клипу") {
        if (!hasNext) {
            Text("Это последний клип.", color = Color(0xFF9A9AA8))
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            TransitionType.entries.forEach { transition ->
                ChoiceButton(transition.title, clip.transitionOut == transition) {
                    if (hasNext || transition == TransitionType.NONE) {
                        onSnapshot()
                        onUpdate(clip.copy(transitionOut = transition))
                    }
                }
            }
        }
        if (clip.transitionOut != TransitionType.NONE) {
            RangeControl(
                title = "Длительность",
                value = clip.transitionDurationMs.toFloat(),
                range = 200f..2000f,
                display = "${"%.1f".format(clip.transitionDurationMs / 1000f)} с",
                onChange = {
                    if (!editing) {
                        onSnapshot()
                        editing = true
                    }
                    onUpdate(clip.copy(transitionDurationMs = it.toLong()))
                },
                onFinished = { editing = false },
            )
        }
    }
}

@Composable
internal fun KeyframePanel(
    clip: VideoClip,
    positionMs: Long,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    var selectedId by remember(clip.id) { mutableStateOf<String?>(clip.keyframes.firstOrNull()?.id) }
    var editing by remember { mutableStateOf(false) }
    val frames = clip.keyframes.sortedBy { it.timeMs }
    val selected = frames.firstOrNull { it.id == selectedId }

    fun updateFrame(frame: TransformKeyframe) {
        onUpdate(
            clip.copy(
                keyframes = clip.keyframes.map { if (it.id == frame.id) frame else it }
            )
        )
    }

    fun beginEdit() {
        if (!editing) {
            onSnapshot()
            editing = true
        }
    }

    SectionCard("Ключевые кадры") {
        Text(
            "Курсор: ${formatTime(positionMs)} · плавная интерполяция X/Y/масштаба/поворота",
            color = Color(0xFF9A9AA8),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolButton(
                text = "Добавить в курсоре",
                onClick = {
                    onSnapshot()
                    val frame = TransformKeyframe(
                        id = UUID.randomUUID().toString(),
                        timeMs = positionMs.coerceIn(0L, clip.sourceSliceDurationMs),
                    )
                    onUpdate(clip.copy(keyframes = (clip.keyframes + frame).sortedBy { it.timeMs }))
                    selectedId = frame.id
                },
            )
            ToolButton(
                text = "Очистить все",
                onClick = {
                    onSnapshot()
                    onUpdate(clip.copy(keyframes = emptyList()))
                    selectedId = null
                },
                enabled = clip.keyframes.isNotEmpty(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            frames.forEachIndexed { index, frame ->
                ChoiceButton(
                    "${index + 1} · ${formatTime(frame.timeMs)}",
                    frame.id == selectedId,
                ) {
                    selectedId = frame.id
                }
            }
        }
        selected?.let { frame ->
            RangeControl(
                title = "X",
                value = frame.x,
                range = -1f..1f,
                onChange = { beginEdit(); updateFrame(frame.copy(x = it)) },
                onFinished = { editing = false },
            )
            RangeControl(
                title = "Y",
                value = frame.y,
                range = -1f..1f,
                onChange = { beginEdit(); updateFrame(frame.copy(y = it)) },
                onFinished = { editing = false },
            )
            RangeControl(
                title = "Масштаб",
                value = frame.scale,
                range = 0.25f..3f,
                display = "${(frame.scale * 100).roundToInt()}%",
                onChange = { beginEdit(); updateFrame(frame.copy(scale = it)) },
                onFinished = { editing = false },
            )
            RangeControl(
                title = "Поворот",
                value = frame.rotation,
                range = -180f..180f,
                display = "${frame.rotation.roundToInt()}°",
                onChange = { beginEdit(); updateFrame(frame.copy(rotation = it)) },
                onFinished = { editing = false },
            )
            ToolButton(
                text = "Удалить выбранный",
                onClick = {
                    onSnapshot()
                    onUpdate(clip.copy(keyframes = clip.keyframes.filterNot { it.id == frame.id }))
                    selectedId = null
                },
            )
        }
    }
}

@Composable
internal fun StickerPanel(
    clip: VideoClip,
    onChooseSticker: () -> Unit,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    SectionCard("Изображения и стикеры") {
        ToolButton(text = "Добавить PNG / JPG / WebP", onClick = onChooseSticker)
        if (clip.stickers.isEmpty()) {
            Text("Наложений нет", color = Color(0xFF8F8F9C))
        }
        clip.stickers.forEachIndexed { index, sticker ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(Color(0xFF1D1D24), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text("${index + 1}. ${sticker.name}", color = Color.White)
                RangeControl(
                    title = "X",
                    value = sticker.x,
                    range = -1f..1f,
                    onChange = { value ->
                        onUpdate(
                            clip.copy(
                                stickers = clip.stickers.map {
                                    if (it.id == sticker.id) it.copy(x = value) else it
                                }
                            )
                        )
                    },
                )
                RangeControl(
                    title = "Y",
                    value = sticker.y,
                    range = -1f..1f,
                    onChange = { value ->
                        onUpdate(
                            clip.copy(
                                stickers = clip.stickers.map {
                                    if (it.id == sticker.id) it.copy(y = value) else it
                                }
                            )
                        )
                    },
                )
                RangeControl(
                    title = "Размер",
                    value = sticker.scale,
                    range = 0.05f..2f,
                    display = "${(sticker.scale * 100).roundToInt()}%",
                    onChange = { value ->
                        onUpdate(
                            clip.copy(
                                stickers = clip.stickers.map {
                                    if (it.id == sticker.id) it.copy(scale = value) else it
                                }
                            )
                        )
                    },
                )
                RangeControl(
                    title = "Поворот",
                    value = sticker.rotation,
                    range = -180f..180f,
                    display = "${sticker.rotation.roundToInt()}°",
                    onChange = { value ->
                        onUpdate(
                            clip.copy(
                                stickers = clip.stickers.map {
                                    if (it.id == sticker.id) it.copy(rotation = value) else it
                                }
                            )
                        )
                    },
                )
                RangeControl(
                    title = "Прозрачность",
                    value = sticker.alpha,
                    range = 0f..1f,
                    display = "${(sticker.alpha * 100).roundToInt()}%",
                    onChange = { value ->
                        onUpdate(
                            clip.copy(
                                stickers = clip.stickers.map {
                                    if (it.id == sticker.id) it.copy(alpha = value) else it
                                }
                            )
                        )
                    },
                )
                ToolButton(
                    text = "Удалить",
                    onClick = {
                        onSnapshot()
                        onUpdate(clip.copy(stickers = clip.stickers.filterNot { it.id == sticker.id }))
                    },
                )
            }
        }
    }
}

@Composable
internal fun SubtitlePanel(
    cues: List<SubtitleCue>,
    style: SubtitleStyle,
    projectCursorMs: Long,
    onImportSrt: () -> Unit,
    onAddCue: (String) -> Unit,
    onDeleteCue: (String) -> Unit,
    onClear: () -> Unit,
    onStyleChange: (SubtitleStyle) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    SectionCard("Субтитры") {
        Text(
            "Курсор проекта: ${formatTime(projectCursorMs)} · строк: ${cues.size}",
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ToolButton(text = "Импорт SRT", onClick = onImportSrt)
            ToolButton(text = "Очистить", onClick = onClear, enabled = cues.isNotEmpty())
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(180) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Новая строка") },
            maxLines = 3,
        )
        ToolButton(
            text = "Добавить на 2 секунды",
            onClick = {
                if (draft.isNotBlank()) {
                    onAddCue(draft.trim())
                    draft = ""
                }
            },
            enabled = draft.isNotBlank(),
        )
        RangeControl(
            title = "Размер",
            value = style.fontScale,
            range = 0.55f..2.2f,
            display = "${(style.fontScale * 100).roundToInt()}%",
            onChange = { onStyleChange(style.copy(fontScale = it)) },
        )
        RangeControl(
            title = "Положение",
            value = style.verticalPosition,
            range = 0.55f..0.94f,
            display = "${(style.verticalPosition * 100).roundToInt()}%",
            onChange = { onStyleChange(style.copy(verticalPosition = it)) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Подложка", color = Color.White)
            Switch(
                checked = style.backgroundEnabled,
                onCheckedChange = { onStyleChange(style.copy(backgroundEnabled = it)) },
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(
                "Белый" to 0xFFFFFFFF.toInt(),
                "Жёлтый" to 0xFFFFD54F.toInt(),
                "Голубой" to 0xFF40C4FF.toInt(),
                "Зелёный" to 0xFF69F0AE.toInt(),
            ).forEach { (name, color) ->
                ChoiceButton(name, style.textColor == color) {
                    onStyleChange(style.copy(textColor = color))
                }
            }
        }
        cues.sortedBy { it.startMs }.takeLast(8).forEach { cue ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${formatTime(cue.startMs)}–${formatTime(cue.endMs)}  ${cue.text}",
                    color = Color(0xFFD4D4DB),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                ToolButton(text = "Удалить", onClick = { onDeleteCue(cue.id) })
            }
        }
    }
}

@Composable
internal fun VoiceoverPanel(
    isRecording: Boolean,
    projectCursorMs: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    SectionCard("Озвучка") {
        Text(
            if (isRecording) "Идёт запись с ${formatTime(projectCursorMs)}"
            else "Запись голоса с текущей позиции курсора",
            color = if (isRecording) Color(0xFFFF8A80) else Color(0xFF9A9AA8),
        )
        if (isRecording) {
            ToolButton(text = "Остановить и добавить", onClick = onStop)
        } else {
            ToolButton(text = "Начать запись", onClick = onStart)
        }
    }
}

@Composable
internal fun AdvancedProjectPanel(
    backgroundAudio: AudioTrack?,
    exportSettings: ExportSettings,
    onChooseMusic: () -> Unit,
    onRemoveMusic: () -> Unit,
    onBackgroundAudioChange: (AudioTrack) -> Unit,
    onExportSettings: (ExportSettings) -> Unit,
) {
    SectionCard("Музыка, холст и экспорт") {
        Text(
            if (backgroundAudio == null) "Фоновая музыка: не выбрана"
            else "Фоновая музыка: ${backgroundAudio.name}",
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ToolButton(text = "Выбрать музыку", onClick = onChooseMusic)
            ToolButton(
                text = "Убрать музыку",
                onClick = onRemoveMusic,
                enabled = backgroundAudio != null,
            )
        }
        backgroundAudio?.let { audio ->
            RangeControl(
                title = "Громкость",
                value = audio.volume,
                range = 0f..1f,
                display = "${(audio.volume * 100).roundToInt()}%",
                onChange = { onBackgroundAudioChange(audio.copy(volume = it)) },
            )
        }

        Text("Холст", color = Color(0xFF9A9AA8))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(
                "Исходный" to null,
                "16:9" to 16f / 9f,
                "9:16" to 9f / 16f,
                "1:1" to 1f,
                "4:5" to 4f / 5f,
                "3:4" to 3f / 4f,
            ).forEach { (name, ratio) ->
                val selected = if (ratio == null) {
                    exportSettings.aspectRatio == null
                } else {
                    exportSettings.aspectRatio?.let { abs(it - ratio) < 0.001f } == true
                }
                ChoiceButton(name, selected) {
                    onExportSettings(exportSettings.copy(aspectRatio = ratio))
                }
            }
        }
        if (exportSettings.aspectRatio != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ChoiceButton("Вписать", !exportSettings.cropToFill) {
                    onExportSettings(exportSettings.copy(cropToFill = false))
                }
                ChoiceButton("Заполнить", exportSettings.cropToFill) {
                    onExportSettings(exportSettings.copy(cropToFill = true))
                }
            }
        }

        Text("Разрешение", color = Color(0xFF9A9AA8))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(480, 720, 1080, 1440, 2160).forEach { height ->
                ChoiceButton(
                    if (height == 2160) "4K" else "${height}p",
                    exportSettings.height == height,
                ) {
                    onExportSettings(exportSettings.copy(height = height))
                }
            }
        }

        Text("Частота кадров", color = Color(0xFF9A9AA8))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(24, 25, 30, 50, 60).forEach { fps ->
                ChoiceButton("$fps FPS", exportSettings.maxFrameRate == fps) {
                    onExportSettings(exportSettings.copy(maxFrameRate = fps))
                }
            }
        }

        Text("Кодек", color = Color(0xFF9A9AA8))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            VideoCodec.entries.forEach { codec ->
                ChoiceButton(codec.title, exportSettings.videoCodec == codec) {
                    onExportSettings(exportSettings.copy(videoCodec = codec))
                }
            }
        }
        Text(
            "${exportSettings.videoCodec.title} · AAC · " +
                "${if (exportSettings.height == 2160) "4K" else "${exportSettings.height}p"} · " +
                "${exportSettings.maxFrameRate} FPS",
            color = Color(0xFF86EFAC),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun ExportResultPanel(
    lastExportUri: String?,
    onShare: () -> Unit,
) {
    if (lastExportUri != null) {
        SectionCard("Последний экспорт") {
            Text("Видео сохранено в Movies/VibeCut", color = Color(0xFF86EFAC))
            ToolButton(text = "Поделиться видео", onClick = onShare)
        }
    }
}
