package ru.vibecut.editor

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0B0B0E),
                    surface = Color(0xFF15151A),
                    primary = Color(0xFF8B5CF6),
                    secondary = Color(0xFF22D3EE),
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) { VideoEditorApp() }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoEditorApp() {
    val context = LocalContext.current
    val clips = remember { mutableStateListOf<VideoClip>() }
    val history = remember { mutableStateListOf<EditorSnapshot>() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var exportState by remember { mutableStateOf(ExportState.IDLE) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Добавьте видео, чтобы начать монтаж") }
    val exportManager = remember { ExportManager(context) }

    DisposableEffect(Unit) { onDispose { exportManager.cancel() } }

    fun snapshot() {
        history += EditorSnapshot(clips.toList(), selectedId)
        if (history.size > 30) history.removeAt(0)
    }

    fun replaceClip(updated: VideoClip) {
        val index = clips.indexOfFirst { it.id == updated.id }
        if (index >= 0) clips[index] = updated
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        snapshot()
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val clip = readClip(context, uri)
            clips += clip
            if (selectedId == null) selectedId = clip.id
        }
        message = "Добавлено видео: ${uris.size}"
    }

    val selected = clips.firstOrNull { it.id == selectedId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0E))
    ) {
        EditorHeader(
            clipCount = clips.size,
            exportState = exportState,
            exportProgress = exportProgress,
            onImport = { importer.launch(arrayOf("video/*")) },
            onExport = {
                if (clips.isNotEmpty() && exportState != ExportState.EXPORTING) {
                    exportState = ExportState.EXPORTING
                    exportProgress = 0
                    message = "Экспорт начат"
                    exportManager.export(
                        clips = clips.toList(),
                        onProgress = { exportProgress = it },
                        onDone = {
                            exportState = ExportState.DONE
                            exportProgress = 100
                            message = "Готово. Видео сохранено в Movies/VibeCut"
                        },
                        onError = {
                            exportState = ExportState.ERROR
                            message = it
                        },
                    )
                }
            },
        )

        if (selected == null) {
            EmptyEditor { importer.launch(arrayOf("video/*")) }
        } else {
            Preview(clip = selected, onPosition = { positionMs = it })

            Timeline(
                clips = clips,
                selectedId = selectedId,
                onSelect = {
                    selectedId = it
                    positionMs = 0L
                },
            )

            ClipTools(
                clip = selected,
                positionMs = positionMs,
                canUndo = history.isNotEmpty(),
                onSplit = {
                    val absolute = selected.trimStartMs + positionMs
                    if (absolute > selected.trimStartMs + 100 && absolute < selected.trimEndMs - 100) {
                        snapshot()
                        val index = clips.indexOfFirst { it.id == selected.id }
                        val left = selected.copy(trimEndMs = absolute)
                        val right = selected.copy(
                            id = UUID.randomUUID().toString(),
                            name = "${selected.name} · 2",
                            trimStartMs = absolute,
                        )
                        clips[index] = left
                        clips.add(index + 1, right)
                        selectedId = right.id
                        positionMs = 0L
                        message = "Клип разделён"
                    }
                },
                onTrimStart = {
                    val absolute = selected.trimStartMs + positionMs
                    if (absolute < selected.trimEndMs - 100) {
                        snapshot()
                        replaceClip(selected.copy(trimStartMs = absolute))
                        positionMs = 0L
                        message = "Начало обрезано"
                    }
                },
                onTrimEnd = {
                    val absolute = selected.trimStartMs + positionMs
                    if (absolute > selected.trimStartMs + 100) {
                        snapshot()
                        replaceClip(selected.copy(trimEndMs = absolute))
                        message = "Конец обрезан"
                    }
                },
                onMute = {
                    snapshot()
                    replaceClip(selected.copy(muted = !selected.muted))
                },
                onRotate = {
                    snapshot()
                    replaceClip(selected.copy(rotationDegrees = (selected.rotationDegrees + 90) % 360))
                },
                onDuplicate = {
                    snapshot()
                    val index = clips.indexOfFirst { it.id == selected.id }
                    val copy = selected.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${selected.name} · копия",
                    )
                    clips.add(index + 1, copy)
                    selectedId = copy.id
                },
                onDelete = {
                    snapshot()
                    val index = clips.indexOfFirst { it.id == selected.id }
                    clips.removeAt(index)
                    selectedId = if (clips.isEmpty()) null else clips[index.coerceAtMost(clips.lastIndex)].id
                    positionMs = 0L
                },
                onUndo = {
                    if (history.isNotEmpty()) {
                        val saved = history.removeAt(history.lastIndex)
                        clips.clear()
                        clips.addAll(saved.clips)
                        selectedId = saved.selectedId
                        positionMs = 0L
                        message = "Последнее действие отменено"
                    }
                },
                onImport = { importer.launch(arrayOf("video/*")) },
            )
        }

        Text(
            text = message,
            color = Color(0xFFB9B9C5),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EditorHeader(
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
        Column(modifier = Modifier.width(125.dp)) {
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
                    Spacer(Modifier.width(6.dp))
                    Text("$exportProgress%")
                } else {
                    Text("Экспорт")
                }
            }
        }
    }
}

@Composable
private fun EmptyEditor(onImport: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(480.dp),
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
                Text("Монтаж без лишнего", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    "Выберите один или несколько роликов. Обрезайте, разделяйте, поворачивайте, отключайте звук и собирайте всё в один файл.",
                    color = Color(0xFFB9B9C5),
                )
                Button(onClick = onImport) { Text("Выбрать видео") }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Preview(clip: VideoClip, onPosition: (Long) -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(clip.durationMs) }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(clip.id, clip.trimStartMs, clip.trimEndMs, clip.muted) {
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
        player.volume = if (clip.muted) 0f else 1f
        player.prepare()
        player.seekTo(0L)
        position = 0L
        duration = clip.durationMs
        onPosition(0L)
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
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = clip.rotationDegrees.toFloat() },
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
                "${formatTime(position)} / ${formatTime(duration)}",
                color = Color(0xFFCACAD3),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun Timeline(
    clips: List<VideoClip>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
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
                val cardWidth = (90f + clip.durationMs / 1000f * 9f).coerceIn(90f, 260f).dp
                val isSelected = clip.id == selectedId
                Column(
                    modifier = Modifier
                        .width(cardWidth)
                        .height(66.dp)
                        .background(
                            if (isSelected) Color(0xFF3B2A67) else Color(0xFF202027),
                            RoundedCornerShape(10.dp),
                        )
                        .then(
                            if (isSelected) Modifier.border(
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
                        if (clip.rotationDegrees != 0) Text("${clip.rotationDegrees}°", color = Color(0xFFC4B5FD), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipTools(
    clip: VideoClip,
    positionMs: Long,
    canUndo: Boolean,
    onSplit: () -> Unit,
    onTrimStart: () -> Unit,
    onTrimEnd: () -> Unit,
    onMute: () -> Unit,
    onRotate: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onImport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(
            "Инструменты клипа · курсор ${formatTime(positionMs)}",
            color = Color(0xFFB9B9C5),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton("Разделить", onSplit)
            ToolButton("Обрезать начало", onTrimStart)
            ToolButton("Обрезать конец", onTrimEnd)
            ToolButton("Повернуть 90°", onRotate)
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
            ToolButton("Дублировать", onDuplicate)
            ToolButton("Удалить", onDelete)
            ToolButton("Отменить", onUndo, enabled = canUndo)
            ToolButton("Добавить видео", onImport)
        }
    }
}

@Composable
private fun ToolButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24242C)),
    ) { Text(text) }
}

private fun readClip(context: Context, uri: Uri): VideoClip {
    val retriever = MediaMetadataRetriever()
    val duration = try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1L
    } finally {
        retriever.release()
    }

    val name = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "Видео"

    return VideoClip(
        id = UUID.randomUUID().toString(),
        uri = uri.toString(),
        name = name,
        sourceDurationMs = duration.coerceAtLeast(1L),
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
