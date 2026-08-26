package ru.vibecut.editor

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal enum class AdjustmentType(
    val title: String,
    val min: Float,
    val max: Float,
) {
    SPEED("Скорость", 0.25f, 4f),
    BRIGHTNESS("Яркость", -1f, 1f),
    CONTRAST("Контраст", -1f, 1f),
    SATURATION("Насыщенность", -100f, 100f),
    HUE("Оттенок", -180f, 180f),
    LIGHTNESS("Светлота", -100f, 100f),
    CROP("Кадрирование", 0f, 0.4f),
}

internal data class FilterPreset(
    val title: String,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val hue: Float = 0f,
    val lightness: Float = 0f,
)

private val filterPresets = listOf(
    FilterPreset("Оригинал"),
    FilterPreset("Яркий", brightness = 0.05f, contrast = 0.16f, saturation = 22f, lightness = 4f),
    FilterPreset("Тёплый", brightness = 0.03f, saturation = 14f, hue = 9f, lightness = 3f),
    FilterPreset("Холодный", saturation = 8f, hue = -12f, lightness = 2f),
    FilterPreset("Кино", contrast = 0.20f, saturation = -12f, lightness = -6f),
    FilterPreset("Ч/Б", contrast = 0.16f, saturation = -100f),
    FilterPreset("Мягкий", brightness = 0.04f, contrast = -0.08f, saturation = -5f, lightness = 9f),
)

@Composable
internal fun EditorHeader(
    clipCount: Int,
    exportState: ExportState,
    exportProgress: Int,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.width(132.dp)) {
            Text("VibeCut", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                if (clipCount == 0) "Новый проект" else "Клипов: $clipCount",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9292A0),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onImport,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262E)),
            ) { Text("Добавить") }
            Button(
                onClick = onExport,
                enabled = clipCount > 0 && exportState != ExportState.EXPORTING,
            ) {
                if (exportState == ExportState.EXPORTING) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(5.dp))
                    Text("$exportProgress%")
                } else {
                    Text("Экспорт")
                }
            }
        }
    }
}

@Composable
internal fun EmptyEditor(onImport: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(500.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF15151A)),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Видеоредактор", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    "Выберите один или несколько роликов. Монтаж, эффекты и экспорт выполняются локально на телефоне.",
                    color = Color(0xFFB9B9C5),
                )
                Button(onClick = onImport) { Text("Выбрать видео") }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun Preview(
    clip: VideoClip,
    exportSettings: ExportSettings,
    onPosition: (Long) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(clip.sourceSliceDurationMs) }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(clip.id, clip.trimStartMs, clip.trimEndMs) {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.trimStartMs)
            .setEndPositionMs(clip.trimEndMs)
            .build()
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(clipping)
                .build()
        )
        player.prepare()
        player.seekTo(0L)
        position = 0L
        duration = clip.sourceSliceDurationMs
        onPosition(0L)
    }

    LaunchedEffect(
        clip.speed,
        clip.brightness,
        clip.contrast,
        clip.saturation,
        clip.hue,
        clip.lightness,
        clip.crop,
        clip.rotationDegrees,
        clip.flipHorizontal,
        clip.flipVertical,
        clip.overlayText,
        clip.textX,
        clip.textY,
        clip.textScale,
        clip.textRotation,
        clip.textColor,
        clip.textBackground,
        clip.textBold,
        clip.textItalic,
        clip.muted,
        clip.audioVolume,
        exportSettings.aspectRatio,
        exportSettings.cropToFill,
    ) {
        player.setVideoEffects(
            buildVideoEffects(clip) + buildCanvasEffects(exportSettings, includeResolution = false)
        )
        player.setPlaybackSpeed(clip.speed)
        player.volume = if (clip.muted) 0f else clip.audioVolume.coerceIn(0f, 1f)
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0) duration = player.duration
            onPosition(position)
            delay(100L)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Color.Black, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; this.player = player } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Slider(
            value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
            onValueChange = {
                val target = it.toLong()
                player.seekTo(target)
                position = target
                onPosition(target)
            },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Button(onClick = { if (playing) player.pause() else player.play() }) {
                Text(if (playing) "Пауза" else "Пуск")
            }
            Text(
                "${formatTime(position)} / ${formatTime(duration)} · ${formatSpeed(clip.speed)}",
                color = Color(0xFFCACAD3),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
internal fun Timeline(
    clips: List<VideoClip>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
        Text(
            "Таймлайн",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            clips.forEachIndexed { index, clip ->
                val cardWidth = (90f + clip.durationMs / 1000f * 8f).coerceIn(90f, 250f).dp
                val selected = clip.id == selectedId
                Column(
                    modifier = Modifier
                        .width(cardWidth)
                        .height(68.dp)
                        .background(
                            if (selected) Color(0xFF3B2A67) else Color(0xFF202027),
                            RoundedCornerShape(10.dp),
                        )
                        .then(
                            if (selected) Modifier.border(
                                2.dp,
                                Color(0xFF9B7CF7),
                                RoundedCornerShape(10.dp),
                            ) else Modifier
                        )
                        .clickable { onSelect(clip.id) }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${index + 1}. ${clip.name}",
                        maxLines = 1,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(formatTime(clip.durationMs), color = Color(0xFFB9B9C5), style = MaterialTheme.typography.labelSmall)
                        if (clip.muted) Text("без звука", color = Color(0xFF7DD3FC), style = MaterialTheme.typography.labelSmall)
                        if (clip.speed != 1f) Text(formatSpeed(clip.speed), color = Color(0xFFC4B5FD), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun BasicTools(
    clip: VideoClip,
    positionMs: Long,
    canUndo: Boolean,
    canRedo: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onSplit: () -> Unit,
    onTrimStart: () -> Unit,
    onTrimEnd: () -> Unit,
    onMute: () -> Unit,
    onRotate: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onImport: () -> Unit,
) {
    SectionCard("Монтаж · курсор ${formatTime(positionMs)}") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton("Разделить", onSplit)
            ToolButton("Обрезать начало", onTrimStart)
            ToolButton("Обрезать конец", onTrimEnd)
            ToolButton("Повернуть 90°", onRotate)
            ToolButton(if (clip.flipHorizontal) "↔ Отражено" else "Отразить ↔", onFlipHorizontal)
            ToolButton(if (clip.flipVertical) "↕ Отражено" else "Отразить ↕", onFlipVertical)
            Row(
                modifier = Modifier
                    .background(Color(0xFF1D1D24), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Без звука", color = Color.White)
                Spacer(Modifier.width(6.dp))
                Switch(checked = clip.muted, onCheckedChange = { onMute() })
            }
            ToolButton("Копия", onDuplicate)
            ToolButton("← Влево", onMoveLeft, enabled = canMoveLeft)
            ToolButton("Вправо →", onMoveRight, enabled = canMoveRight)
            ToolButton("Удалить", onDelete)
            ToolButton("Отменить", onUndo, enabled = canUndo)
            ToolButton("Повторить", onRedo, enabled = canRedo)
            ToolButton("Добавить видео", onImport)
        }
    }
}

@Composable
internal fun AdjustmentsPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    var active by remember { mutableStateOf(AdjustmentType.SPEED) }
    var sliderEditing by remember { mutableStateOf(false) }

    fun valueFor(type: AdjustmentType): Float = when (type) {
        AdjustmentType.SPEED -> clip.speed
        AdjustmentType.BRIGHTNESS -> clip.brightness
        AdjustmentType.CONTRAST -> clip.contrast
        AdjustmentType.SATURATION -> clip.saturation
        AdjustmentType.HUE -> clip.hue
        AdjustmentType.LIGHTNESS -> clip.lightness
        AdjustmentType.CROP -> clip.crop
    }

    fun updateValue(type: AdjustmentType, value: Float) {
        onUpdate(
            when (type) {
                AdjustmentType.SPEED -> clip.copy(speed = value.coerceIn(0.25f, 4f))
                AdjustmentType.BRIGHTNESS -> clip.copy(brightness = value.coerceIn(-1f, 1f))
                AdjustmentType.CONTRAST -> clip.copy(contrast = value.coerceIn(-1f, 1f))
                AdjustmentType.SATURATION -> clip.copy(saturation = value.coerceIn(-100f, 100f))
                AdjustmentType.HUE -> clip.copy(hue = value.coerceIn(-180f, 180f))
                AdjustmentType.LIGHTNESS -> clip.copy(lightness = value.coerceIn(-100f, 100f))
                AdjustmentType.CROP -> clip.copy(crop = value.coerceIn(0f, 0.4f))
            }
        )
    }

    SectionCard("Изображение и скорость") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AdjustmentType.entries.forEach { item ->
                ChoiceButton(item.title, item == active) { active = item }
            }
        }
        Spacer(Modifier.height(8.dp))
        val current = valueFor(active)
        Text("${active.title}: ${formatAdjustment(active, current)}", color = Color.White)
        Slider(
            value = current,
            onValueChange = { value ->
                if (!sliderEditing) {
                    onSnapshot()
                    sliderEditing = true
                }
                updateValue(active, value)
            },
            onValueChangeFinished = { sliderEditing = false },
            valueRange = active.min..active.max,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ToolButton("Сбросить параметр", {
                onSnapshot()
                updateValue(active, if (active == AdjustmentType.SPEED) 1f else 0f)
            })
            ToolButton("Сбросить всё", {
                onSnapshot()
                onUpdate(
                    clip.copy(
                        speed = 1f,
                        brightness = 0f,
                        contrast = 0f,
                        saturation = 0f,
                        hue = 0f,
                        lightness = 0f,
                        crop = 0f,
                    )
                )
            })
        }
    }
}

@Composable
internal fun FilterPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    SectionCard("Фильтры") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            filterPresets.forEach { preset ->
                ToolButton(preset.title, {
                    onSnapshot()
                    onUpdate(
                        clip.copy(
                            brightness = preset.brightness,
                            contrast = preset.contrast,
                            saturation = preset.saturation,
                            hue = preset.hue,
                            lightness = preset.lightness,
                        )
                    )
                })
            }
        }
        Text(
            "После пресета любой параметр можно отдельно поправить в разделе «Изображение и скорость».",
            color = Color(0xFF8F8F9C),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
internal fun ClipAudioPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val maxFadeMs = (clip.sourceSliceDurationMs / 2L).coerceIn(500L, 5000L)

    SectionCard("Звук клипа") {
        Text("Громкость: ${(clip.audioVolume * 100).roundToInt()}%", color = Color.White)
        Slider(
            value = clip.audioVolume.coerceIn(0f, 1f),
            onValueChange = {
                if (!editing) { onSnapshot(); editing = true }
                onUpdate(clip.copy(audioVolume = it.coerceIn(0f, 1f)))
            },
            onValueChangeFinished = { editing = false },
            valueRange = 0f..1f,
        )
        Text("Плавное появление: ${formatTime(clip.audioFadeInMs)}", color = Color.White)
        Slider(
            value = clip.audioFadeInMs.toFloat().coerceIn(0f, maxFadeMs.toFloat()),
            onValueChange = {
                if (!editing) { onSnapshot(); editing = true }
                onUpdate(clip.copy(audioFadeInMs = it.toLong()))
            },
            onValueChangeFinished = { editing = false },
            valueRange = 0f..maxFadeMs.toFloat(),
        )
        Text("Плавное затухание: ${formatTime(clip.audioFadeOutMs)}", color = Color.White)
        Slider(
            value = clip.audioFadeOutMs.toFloat().coerceIn(0f, maxFadeMs.toFloat()),
            onValueChange = {
                if (!editing) { onSnapshot(); editing = true }
                onUpdate(clip.copy(audioFadeOutMs = it.toLong()))
            },
            onValueChangeFinished = { editing = false },
            valueRange = 0f..maxFadeMs.toFloat(),
        )
        ToolButton("Сбросить звук", {
            onSnapshot()
            onUpdate(clip.copy(audioVolume = 1f, audioFadeInMs = 0L, audioFadeOutMs = 0L, muted = false))
        })
    }
}

@Composable
internal fun TextPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    var textEditing by remember(clip.id) { mutableStateOf(false) }
    var sliderEditing by remember { mutableStateOf(false) }

    SectionCard("Текст поверх видео") {
        OutlinedTextField(
            value = clip.overlayText,
            onValueChange = {
                if (!textEditing) {
                    onSnapshot()
                    textEditing = true
                }
                onUpdate(clip.copy(overlayText = it.take(160)))
            },
            label = { Text("Надпись") },
            placeholder = { Text("Введите текст") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChoiceButton("Жирный", clip.textBold) {
                onSnapshot(); onUpdate(clip.copy(textBold = !clip.textBold))
            }
            ChoiceButton("Курсив", clip.textItalic) {
                onSnapshot(); onUpdate(clip.copy(textItalic = !clip.textItalic))
            }
            ChoiceButton("Подложка", clip.textBackground) {
                onSnapshot(); onUpdate(clip.copy(textBackground = !clip.textBackground))
            }
            ToolButton("Очистить", {
                if (clip.overlayText.isNotEmpty()) {
                    onSnapshot(); onUpdate(clip.copy(overlayText = "")); textEditing = false
                }
            }, enabled = clip.overlayText.isNotEmpty())
        }

        Spacer(Modifier.height(9.dp))
        Text("Цвет текста", color = Color(0xFFB9B9C5))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "Белый" to 0xFFFFFFFF.toInt(),
                "Жёлтый" to 0xFFFFD54F.toInt(),
                "Красный" to 0xFFFF5252.toInt(),
                "Голубой" to 0xFF40C4FF.toInt(),
                "Зелёный" to 0xFF69F0AE.toInt(),
                "Чёрный" to 0xFF000000.toInt(),
            ).forEach { (title, color) ->
                ChoiceButton(title, clip.textColor == color) {
                    onSnapshot(); onUpdate(clip.copy(textColor = color))
                }
            }
        }

        Text("Размер: ${(clip.textScale * 100).roundToInt()}%", color = Color.White, modifier = Modifier.padding(top = 8.dp))
        Slider(
            value = clip.textScale.coerceIn(0.25f, 1.6f),
            onValueChange = {
                if (!sliderEditing) { onSnapshot(); sliderEditing = true }
                onUpdate(clip.copy(textScale = it))
            },
            onValueChangeFinished = { sliderEditing = false },
            valueRange = 0.25f..1.6f,
        )
        Text("Положение по горизонтали: ${(clip.textX * 100).roundToInt()}", color = Color.White)
        Slider(
            value = clip.textX.coerceIn(-1f, 1f),
            onValueChange = {
                if (!sliderEditing) { onSnapshot(); sliderEditing = true }
                onUpdate(clip.copy(textX = it))
            },
            onValueChangeFinished = { sliderEditing = false },
            valueRange = -1f..1f,
        )
        Text("Положение по вертикали: ${(clip.textY * 100).roundToInt()}", color = Color.White)
        Slider(
            value = clip.textY.coerceIn(-1f, 1f),
            onValueChange = {
                if (!sliderEditing) { onSnapshot(); sliderEditing = true }
                onUpdate(clip.copy(textY = it))
            },
            onValueChangeFinished = { sliderEditing = false },
            valueRange = -1f..1f,
        )
        Text("Поворот: ${clip.textRotation.roundToInt()}°", color = Color.White)
        Slider(
            value = clip.textRotation.coerceIn(-180f, 180f),
            onValueChange = {
                if (!sliderEditing) { onSnapshot(); sliderEditing = true }
                onUpdate(clip.copy(textRotation = it))
            },
            onValueChangeFinished = { sliderEditing = false },
            valueRange = -180f..180f,
        )
        Text(
            "${clip.overlayText.length}/160",
            color = Color(0xFF9999A6),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun ProjectPanel(
    backgroundAudio: AudioTrack?,
    exportSettings: ExportSettings,
    onChooseMusic: () -> Unit,
    onRemoveMusic: () -> Unit,
    onBackgroundAudioChange: (AudioTrack) -> Unit,
    onExportSettings: (ExportSettings) -> Unit,
) {
    var musicEditing by remember { mutableStateOf(false) }

    SectionCard("Музыка, холст и экспорт") {
        Text(
            if (backgroundAudio == null) "Фоновая музыка: не выбрана"
            else "Фоновая музыка: ${backgroundAudio.name}",
            color = Color.White,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ToolButton("Выбрать музыку", onChooseMusic)
            ToolButton("Убрать музыку", onRemoveMusic, enabled = backgroundAudio != null)
        }
        if (backgroundAudio != null) {
            Text(
                "Громкость музыки: ${(backgroundAudio.volume * 100).roundToInt()}%",
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp),
            )
            Slider(
                value = backgroundAudio.volume.coerceIn(0f, 1f),
                onValueChange = {
                    musicEditing = true
                    onBackgroundAudioChange(backgroundAudio.copy(volume = it))
                },
                onValueChangeFinished = { musicEditing = false },
                valueRange = 0f..1f,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text("Холст", color = Color(0xFFB9B9C5))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val ratios = listOf(
                "Исходный" to null,
                "16:9" to (16f / 9f),
                "9:16" to (9f / 16f),
                "1:1" to 1f,
                "4:5" to (4f / 5f),
            )
            ratios.forEach { (title, ratio) ->
                val selected = if (ratio == null) exportSettings.aspectRatio == null
                else exportSettings.aspectRatio?.let { kotlin.math.abs(it - ratio) < 0.001f } == true
                ChoiceButton(title, selected) {
                    onExportSettings(exportSettings.copy(aspectRatio = ratio))
                }
            }
        }
        if (exportSettings.aspectRatio != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) {
                ChoiceButton("Вписать", !exportSettings.cropToFill) {
                    onExportSettings(exportSettings.copy(cropToFill = false))
                }
                ChoiceButton("Заполнить", exportSettings.cropToFill) {
                    onExportSettings(exportSettings.copy(cropToFill = true))
                }
            }
        }

        Spacer(Modifier.height(11.dp))
        Text("Разрешение", color = Color(0xFFB9B9C5))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(720, 1080, 2160).forEach { height ->
                val title = when (height) {
                    720 -> "720p"
                    1080 -> "1080p"
                    else -> "4K"
                }
                ChoiceButton(title, exportSettings.height == height) {
                    onExportSettings(exportSettings.copy(height = height))
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Text("Максимальная частота кадров", color = Color(0xFFB9B9C5))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(30, 60).forEach { fps ->
                ChoiceButton("$fps FPS", exportSettings.maxFrameRate == fps) {
                    onExportSettings(exportSettings.copy(maxFrameRate = fps))
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Text("Кодек видео", color = Color(0xFFB9B9C5))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            VideoCodec.entries.forEach { codec ->
                ChoiceButton(codec.title, exportSettings.videoCodec == codec) {
                    onExportSettings(exportSettings.copy(videoCodec = codec))
                }
            }
        }
        Text(
            "${exportSettings.videoCodec.title} · AAC · ${exportSettings.height}p · до ${exportSettings.maxFrameRate} FPS. H.265 требует поддержки кодека устройством.",
            color = Color(0xFF8F8F9C),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Проект сохраняется автоматически.",
            color = Color(0xFF86EFAC),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15151A)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
internal fun ChoiceButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF6D4CCE) else Color(0xFF24242C)
        ),
    ) { Text(text) }
}

@Composable
internal fun ToolButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24242C)),
    ) { Text(text) }
}

internal fun formatAdjustment(type: AdjustmentType, value: Float): String = when (type) {
    AdjustmentType.SPEED -> formatSpeed(value)
    AdjustmentType.BRIGHTNESS, AdjustmentType.CONTRAST -> "${(value * 100).roundToInt()}%"
    AdjustmentType.CROP -> "${(value * 100).roundToInt()}% с каждой стороны"
    AdjustmentType.HUE -> "${value.roundToInt()}°"
    AdjustmentType.SATURATION, AdjustmentType.LIGHTNESS -> value.roundToInt().toString()
}

internal fun formatSpeed(speed: Float): String {
    val value = (speed * 100).roundToInt() / 100f
    return "${value}×"
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
