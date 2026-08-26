package ru.vibecut.editor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import java.util.UUID

@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen() {
    val context = LocalContext.current
    val restored = remember { ProjectStore.load(context) }
    val clips = remember {
        mutableStateListOf<VideoClip>().apply { addAll(restored?.clips.orEmpty()) }
    }
    val positionedAudioTracks = remember {
        mutableStateListOf<PositionedAudioTrack>().apply {
            addAll(restored?.positionedAudioTracks.orEmpty())
        }
    }
    val history = remember { mutableStateListOf<EditorSnapshot>() }
    val redoHistory = remember { mutableStateListOf<EditorSnapshot>() }

    var selectedId by remember {
        mutableStateOf(
            restored?.selectedId?.takeIf { id -> clips.any { it.id == id } }
                ?: clips.firstOrNull()?.id
        )
    }
    var positionMs by remember { mutableLongStateOf(0L) }
    var pendingAudioStartMs by remember { mutableLongStateOf(0L) }
    var exportState by remember { mutableStateOf(ExportState.IDLE) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var message by remember {
        mutableStateOf(
            if (clips.isEmpty()) "Добавьте видео, чтобы начать монтаж"
            else "Проект восстановлен из автосохранения"
        )
    }
    var backgroundAudio by remember { mutableStateOf(restored?.backgroundAudio) }
    var exportSettings by remember { mutableStateOf(restored?.exportSettings ?: ExportSettings()) }
    val exportManager = remember { ExportManager(context) }

    DisposableEffect(Unit) { onDispose { exportManager.cancel() } }

    fun currentSnapshot() = EditorSnapshot(clips.toList(), selectedId)

    fun snapshot() {
        history += currentSnapshot()
        redoHistory.clear()
        if (history.size > 50) history.removeAt(0)
    }

    fun restore(saved: EditorSnapshot) {
        clips.clear()
        clips.addAll(saved.clips)
        selectedId = saved.selectedId?.takeIf { id -> clips.any { it.id == id } }
            ?: clips.firstOrNull()?.id
        positionMs = 0L
    }

    fun replaceClip(updated: VideoClip) {
        val index = clips.indexOfFirst { it.id == updated.id }
        if (index >= 0) clips[index] = updated
    }

    fun startExport() {
        if (clips.isEmpty() || exportState == ExportState.EXPORTING) return
        exportState = ExportState.EXPORTING
        exportProgress = 0
        message = "Экспорт начат"
        exportManager.export(
            clips = clips.toList(),
            backgroundAudio = backgroundAudio,
            positionedAudioTracks = positionedAudioTracks.toList(),
            settings = exportSettings,
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

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startExport()
        else message = "Без разрешения на запись Android 8/9 не может сохранить экспорт в галерею"
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        snapshot()
        uris.forEach { uri ->
            persistReadPermission(context, uri)
            runCatching { readClip(context, uri) }
                .onSuccess { clip ->
                    clips += clip
                    if (selectedId == null) selectedId = clip.id
                }
                .onFailure { message = "Не удалось открыть один из выбранных роликов" }
        }
        if (clips.isNotEmpty()) message = "Видео добавлено"
    }

    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            backgroundAudio = AudioTrack(
                uri = uri.toString(),
                name = readDisplayName(context, uri, "Музыка"),
                volume = 0.65f,
            )
            message = "Фоновая музыка добавлена"
        }
    }

    val positionedAudioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            val duration = readMediaDuration(context, uri)
            positionedAudioTracks += PositionedAudioTrack(
                id = UUID.randomUUID().toString(),
                uri = uri.toString(),
                name = readDisplayName(context, uri, "Звук"),
                sourceDurationMs = duration,
                startAtMs = pendingAudioStartMs.coerceAtLeast(0L),
            )
            message = "Звуковая дорожка добавлена с ${formatTime(pendingAudioStartMs)}"
        }
    }

    LaunchedEffect(
        clips.toList(),
        selectedId,
        backgroundAudio,
        positionedAudioTracks.toList(),
        exportSettings,
    ) {
        delay(250L)
        ProjectStore.save(
            context,
            SavedProject(
                clips = clips.toList(),
                selectedId = selectedId,
                backgroundAudio = backgroundAudio,
                positionedAudioTracks = positionedAudioTracks.toList(),
                exportSettings = exportSettings,
            )
        )
    }

    val selected = clips.firstOrNull { it.id == selectedId }
    val selectedIndex = clips.indexOfFirst { it.id == selectedId }
    val projectCursorMs = if (selected != null && selectedIndex >= 0) {
        clips.take(selectedIndex).sumOf { it.durationMs } +
            (positionMs / selected.speed.coerceAtLeast(0.05f)).toLong()
    } else 0L

    Column(
        modifier = Modifier
            .background(Color(0xFF09090C))
            .verticalScroll(rememberScrollState())
    ) {
        EditorHeader(
            clipCount = clips.size,
            exportState = exportState,
            exportProgress = exportProgress,
            onImport = { importer.launch(arrayOf("video/*")) },
            onExport = {
                if (
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    startExport()
                }
            },
        )

        if (selected == null) {
            EmptyEditor { importer.launch(arrayOf("video/*")) }
        } else {
            EditorPreview(
                clip = selected,
                exportSettings = exportSettings,
                onPosition = { positionMs = it },
            )

            Timeline(
                clips = clips,
                selectedId = selectedId,
                onSelect = {
                    selectedId = it
                    positionMs = 0L
                },
            )

            BasicTools(
                clip = selected,
                positionMs = positionMs,
                canUndo = history.isNotEmpty(),
                canRedo = redoHistory.isNotEmpty(),
                canMoveLeft = selectedIndex > 0,
                canMoveRight = selectedIndex in 0 until clips.lastIndex,
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
                onFlipHorizontal = {
                    snapshot()
                    replaceClip(selected.copy(flipHorizontal = !selected.flipHorizontal))
                },
                onFlipVertical = {
                    snapshot()
                    replaceClip(selected.copy(flipVertical = !selected.flipVertical))
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
                onMoveLeft = {
                    val index = clips.indexOfFirst { it.id == selected.id }
                    if (index > 0) {
                        snapshot()
                        val item = clips.removeAt(index)
                        clips.add(index - 1, item)
                    }
                },
                onMoveRight = {
                    val index = clips.indexOfFirst { it.id == selected.id }
                    if (index in 0 until clips.lastIndex) {
                        snapshot()
                        val item = clips.removeAt(index)
                        clips.add(index + 1, item)
                    }
                },
                onDelete = {
                    snapshot()
                    val index = clips.indexOfFirst { it.id == selected.id }
                    clips.removeAt(index)
                    selectedId = if (clips.isEmpty()) null
                    else clips[index.coerceAtMost(clips.lastIndex)].id
                    positionMs = 0L
                },
                onUndo = {
                    if (history.isNotEmpty()) {
                        redoHistory += currentSnapshot()
                        restore(history.removeAt(history.lastIndex))
                        message = "Действие отменено"
                    }
                },
                onRedo = {
                    if (redoHistory.isNotEmpty()) {
                        history += currentSnapshot()
                        restore(redoHistory.removeAt(redoHistory.lastIndex))
                        message = "Действие повторено"
                    }
                },
                onImport = { importer.launch(arrayOf("video/*")) },
            )

            AdjustmentsPanel(
                clip = selected,
                onSnapshot = { snapshot() },
                onUpdate = { replaceClip(it) },
            )

            FilterPanel(
                clip = selected,
                onSnapshot = { snapshot() },
                onUpdate = { replaceClip(it) },
            )

            MotionPanel(
                clip = selected,
                onSnapshot = { snapshot() },
                onUpdate = { replaceClip(it) },
            )

            ClipAudioPanel(
                clip = selected,
                onSnapshot = { snapshot() },
                onUpdate = { replaceClip(it) },
            )

            PositionedAudioPanel(
                tracks = positionedAudioTracks,
                projectCursorMs = projectCursorMs,
                onAddAtCursor = {
                    pendingAudioStartMs = projectCursorMs
                    positionedAudioPicker.launch(arrayOf("audio/*"))
                },
                onUpdate = { updated ->
                    val index = positionedAudioTracks.indexOfFirst { it.id == updated.id }
                    if (index >= 0) positionedAudioTracks[index] = updated
                },
                onDelete = { id -> positionedAudioTracks.removeAll { it.id == id } },
            )

            TextPanel(
                clip = selected,
                onSnapshot = { snapshot() },
                onUpdate = { replaceClip(it) },
            )

            ProjectPanel(
                backgroundAudio = backgroundAudio,
                exportSettings = exportSettings,
                onChooseMusic = { musicPicker.launch(arrayOf("audio/*")) },
                onRemoveMusic = {
                    backgroundAudio = null
                    message = "Фоновая музыка удалена"
                },
                onBackgroundAudioChange = { backgroundAudio = it },
                onExportSettings = { exportSettings = it },
            )
        }

        Text(
            text = message,
            color = Color(0xFFB9B9C5),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private fun readClip(context: Context, uri: Uri): VideoClip {
    return VideoClip(
        id = UUID.randomUUID().toString(),
        uri = uri.toString(),
        name = readDisplayName(context, uri, "Видео"),
        sourceDurationMs = readMediaDuration(context, uri),
    )
}

private fun readMediaDuration(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1L)
            .coerceAtLeast(1L)
    } finally {
        retriever.release()
    }
}

private fun readDisplayName(context: Context, uri: Uri, fallback: String): String {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: fallback
}
