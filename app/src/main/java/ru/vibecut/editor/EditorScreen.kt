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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.io.File
import java.util.Locale
import java.util.UUID

@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val restored = remember(projectId) { ProjectStore.load(context, projectId) ?: SavedProject(id = projectId) }
    val clips = remember(projectId) { mutableStateListOf<VideoClip>().apply { addAll(restored.clips) } }
    val audioTracks = remember(projectId) { mutableStateListOf<PositionedAudioTrack>().apply { addAll(restored.positionedAudioTracks) } }
    val subtitles = remember(projectId) { mutableStateListOf<SubtitleCue>().apply { addAll(restored.subtitles) } }
    val history = remember { mutableStateListOf<EditorSnapshot>() }
    val redo = remember { mutableStateListOf<EditorSnapshot>() }

    var name by remember(projectId) { mutableStateOf(restored.name) }
    var selectedId by remember(projectId) { mutableStateOf(restored.selectedId?.takeIf { id -> clips.any { it.id == id } } ?: clips.firstOrNull()?.id) }
    var position by remember { mutableLongStateOf(0L) }
    var activeTab by remember(projectId) { mutableStateOf(WorkspaceTab.EDIT) }
    var pendingAudioStart by remember { mutableLongStateOf(0L) }
    var pendingStickerClip by remember { mutableStateOf<String?>(null) }
    var voiceStart by remember { mutableLongStateOf(0L) }
    var recording by remember { mutableStateOf(false) }
    var mediaBusy by remember { mutableStateOf(false) }
    var pendingImageDuration by remember { mutableLongStateOf(3000L) }
    var beatMap by remember { mutableStateOf<BeatMap?>(null) }
    var beatBusy by remember { mutableStateOf(false) }
    var pendingRhythmStyle by remember { mutableStateOf<AutoMontageStyle?>(null) }
    var pipBusy by remember { mutableStateOf(false) }
    var pendingPipOptions by remember { mutableStateOf<PipOptions?>(null) }
    var pendingPipBaseId by remember { mutableStateOf<String?>(null) }
    var exportState by remember { mutableStateOf(ExportState.IDLE) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var lastExport by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf(if (clips.isEmpty()) "Добавьте фото или видео из галереи" else "Проект открыт") }
    var music by remember { mutableStateOf(restored.backgroundAudio) }
    var subtitleStyle by remember { mutableStateOf(restored.subtitleStyle) }
    var exportSettings by remember { mutableStateOf(restored.exportSettings) }

    val exporter = remember { ExportManager(context) }
    val recorder = remember { VoiceRecorder(context) }
    val imageMaker = remember { ImageClipMaker(context) }
    val beatAnalyzer = remember { AudioBeatAnalyzer(context) }
    val overlayMaker = remember { VideoOverlayMaker(context) }

    DisposableEffect(Unit) {
        onDispose {
            exporter.cancel(); imageMaker.cancel(); beatAnalyzer.cancel(); overlayMaker.cancel()
            if (recording) recorder.cancel()
        }
    }

    fun snap() {
        history += EditorSnapshot(clips.toList(), selectedId)
        redo.clear()
        if (history.size > 80) history.removeAt(0)
    }
    fun restore(snapshot: EditorSnapshot) {
        clips.clear(); clips.addAll(snapshot.clips)
        selectedId = snapshot.selectedId?.takeIf { id -> clips.any { it.id == id } } ?: clips.firstOrNull()?.id
        position = 0L
    }
    fun undo() {
        if (history.isNotEmpty()) {
            redo += EditorSnapshot(clips.toList(), selectedId)
            restore(history.removeAt(history.lastIndex)); message = "Изменение отменено"
        }
    }
    fun redoAction() {
        if (redo.isNotEmpty()) {
            history += EditorSnapshot(clips.toList(), selectedId)
            restore(redo.removeAt(redo.lastIndex)); message = "Изменение возвращено"
        }
    }
    fun replace(clip: VideoClip) {
        val i = clips.indexOfFirst { it.id == clip.id }
        if (i >= 0) clips[i] = clip
    }
    fun insertAfterSelected(newClip: VideoClip) {
        snap()
        val currentIndex = clips.indexOfFirst { it.id == selectedId }
        clips.add(if (currentIndex < 0) clips.size else currentIndex + 1, newClip)
        selectedId = newClip.id; position = 0L
    }
    fun state() = SavedProject(
        id = projectId, name = name.trim().ifBlank { "Новый проект" }, createdAt = restored.createdAt,
        clips = clips.toList(), selectedId = selectedId, backgroundAudio = music,
        positionedAudioTracks = audioTracks.toList(), subtitles = subtitles.toList(), subtitleStyle = subtitleStyle, exportSettings = exportSettings,
    )
    fun startExport() {
        if (clips.isEmpty() || exportState == ExportState.EXPORTING) return
        ProjectStore.save(context, state()); exportState = ExportState.EXPORTING; exportProgress = 0; lastExport = null; message = "Экспорт начат"
        exporter.export(
            clips.toList(), music, audioTracks.toList(), subtitles.toList(), subtitleStyle, exportSettings,
            { exportProgress = it },
            { uri -> exportState = ExportState.DONE; exportProgress = 100; lastExport = uri; message = "Видео сохранено в Movies/VibeCut" },
            { error -> exportState = ExportState.ERROR; message = error },
        )
    }
    fun applyRhythm(style: AutoMontageStyle, map: BeatMap) {
        if (clips.isEmpty()) return
        snap(); val made = RhythmMontageEngine.build(clips.toList(), map, style)
        clips.clear(); clips.addAll(made); selectedId = clips.firstOrNull()?.id; position = 0L
        message = "Ритм-монтаж «${style.title}»: ${made.size} фрагментов · ${map.bpm} BPM"
    }
    fun analyzeMusic(track: AudioTrack, styleAfter: AutoMontageStyle? = null) {
        beatAnalyzer.cancel(); beatBusy = true; beatMap = null; pendingRhythmStyle = styleAfter; message = "Анализируется ритм «${track.name}»"
        beatAnalyzer.analyze(Uri.parse(track.uri), { map ->
            beatBusy = false; beatMap = map
            val pending = pendingRhythmStyle; pendingRhythmStyle = null
            if (pending != null) applyRhythm(pending, map) else message = "Ритм найден: ${map.bpm} BPM · ${map.beats.size} точек"
        }, { error -> beatBusy = false; pendingRhythmStyle = null; message = error })
    }
    fun importVisualUri(uri: Uri) {
        persist(context, uri)
        when (mediaKind(context, uri)) {
            ImportedMediaKind.IMAGE -> {
                if (mediaBusy) {
                    message = "Дождитесь завершения обработки изображения"
                    return
                }
                mediaBusy = true
                message = "Подготавливается фото"
                imageMaker.createPhotoClip(
                    uri,
                    displayName(context, uri, "Фото"),
                    pendingImageDuration,
                    { clip -> mediaBusy = false; insertAfterSelected(clip); message = "Фото добавлено" },
                    { error -> mediaBusy = false; message = error },
                )
            }
            ImportedMediaKind.VIDEO -> {
                runCatching { readClip(context, uri) }
                    .onSuccess { clip ->
                        snap()
                        clips += clip
                        selectedId = clip.id
                        position = 0L
                        message = "Видео добавлено: ${clip.name}"
                    }
                    .onFailure { message = "Не удалось прочитать видео: ${it.message ?: "формат не поддержан устройством"}" }
            }
            ImportedMediaKind.UNKNOWN -> message = "Не удалось определить тип файла. Попробуйте выбрать его через «Файлы»"
        }
    }

    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startExport() else message = "Нет разрешения на сохранение" }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::importVisualUri)
    }
    val mediaFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importVisualUri)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            snap(); var count = 0; var skipped = 0
            uris.forEach { uri ->
                persist(context, uri)
                if (mediaKind(context, uri) == ImportedMediaKind.VIDEO) {
                    runCatching { readClip(context, uri) }.onSuccess { clip -> clips += clip; if (selectedId == null) selectedId = clip.id; count++ }.onFailure { skipped++ }
                } else skipped++
            }
            message = "Добавлено видео: $count${if (skipped > 0) " · пропущено: $skipped" else ""}"
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            pendingImageDuration = pendingImageDuration.coerceIn(1000L, 10_000L)
            importVisualUri(it)
        }
    }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persist(context, it); val track = AudioTrack(it.toString(), displayName(context, it, "Музыка"), .65f); music = track; analyzeMusic(track) }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persist(context, it); audioTracks += PositionedAudioTrack(UUID.randomUUID().toString(), it.toString(), displayName(context, it, "Звук"), duration(context, it), pendingAudioStart, .85f); message = "Звук добавлен с ${formatTime(pendingAudioStart)}" }
    }
    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pendingStickerClip
        if (uri != null && id != null) {
            persist(context, uri)
            clips.firstOrNull { it.id == id }?.let { clip -> snap(); replace(clip.copy(stickers = clip.stickers + StickerLayer(UUID.randomUUID().toString(), uri.toString(), displayName(context, uri, "Изображение")))) }
        }
        pendingStickerClip = null
    }
    val pipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val baseId = pendingPipBaseId; val options = pendingPipOptions
        if (uri != null && baseId != null && options != null) {
            persist(context, uri); val base = clips.firstOrNull { it.id == baseId }
            if (base != null) {
                pipBusy = true; message = "Создаётся видео поверх видео"
                overlayMaker.create(base, uri, displayName(context, uri, "Видео поверх"), options,
                    { result -> pipBusy = false; snap(); replace(result); selectedId = result.id; position = 0L; message = "Видео поверх видео добавлено" },
                    { error -> pipBusy = false; message = error })
            }
        }
        pendingPipBaseId = null; pendingPipOptions = null
    }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persist(context, it); val list = runCatching { SrtTools.read(context, it) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) { subtitles.clear(); subtitles.addAll(list); message = "Импортировано субтитров: ${list.size}" } else message = "Не удалось прочитать SRT"
        }
    }
    fun beginVoice(start: Long) {
        voiceStart = start
        runCatching { recorder.start() }.onSuccess { recording = true; message = "Запись озвучки начата" }.onFailure { message = "Не удалось начать запись" }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) beginVoice(voiceStart) else message = "Для озвучки нужен микрофон" }

    LaunchedEffect(name, clips.toList(), selectedId, music, audioTracks.toList(), subtitles.toList(), subtitleStyle, exportSettings) {
        delay(300); ProjectStore.save(context, state())
    }

    val selected = clips.firstOrNull { it.id == selectedId }
    val index = clips.indexOfFirst { it.id == selectedId }
    val offset = if (index > 0) clips.take(index).sumOf { it.durationMs } else 0L
    val cursor = if (selected != null) offset + (position / selected.speed.coerceAtLeast(.05f)).toLong() else 0L
    val incoming = clips.getOrNull(index - 1)?.let { previous -> if (previous.transitionOut == TransitionType.NONE) null else TransitionSpec(previous.transitionOut, previous.transitionDurationMs) }

    Column(Modifier.fillMaxSize().background(Color(0xFF08080C))) {
        ProEditorTopBar(
            projectName = name, clipCount = clips.size, exportState = exportState, exportProgress = exportProgress,
            canUndo = history.isNotEmpty(), canRedo = redo.isNotEmpty(),
            onBack = { ProjectStore.save(context, state()); onBack() }, onNameChange = { name = it }, onUndo = ::undo, onRedo = ::redoAction,
            onImport = { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onExport = {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                else startExport()
            },
        )

        if (selected == null) {
            Box(Modifier.weight(1f)) {
                EmptyEditor { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
            }
        } else {
            EditorPreview(selected, incoming, exportSettings, offset, subtitles, subtitleStyle) { position = it }
            ProTimeline(
                clips = clips,
                selectedId = selectedId,
                positionMs = position,
                music = music,
                audioTracks = audioTracks,
                subtitles = subtitles,
                onSelect = { selectedId = it; position = 0L },
                onSnapshot = { snap() },
                onUpdateClip = { updated ->
                    replace(updated)
                    position = position.coerceAtMost((updated.sourceSliceDurationMs - 1L).coerceAtLeast(0L))
                    message = "Обрезка клипа обновлена"
                },
                onMoveClip = { id, target ->
                    val from = clips.indexOfFirst { it.id == id }
                    if (from >= 0 && target != from) {
                        snap()
                        val moving = clips.removeAt(from)
                        clips.add(target.coerceIn(0, clips.size), moving)
                        selectedId = id
                        position = 0L
                        message = "Клип перемещён"
                    }
                },
            )
            EditorMessageBar(message)

            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 18.dp)) {
                    WorkspaceSectionHeader(activeTab, selected, cursor)
                    when (activeTab) {
                        WorkspaceTab.EDIT -> {
                            BasicTools(selected, position, history.isNotEmpty(), redo.isNotEmpty(), index > 0, index in 0 until clips.lastIndex,
                                onSplit = {
                                    val absolute = selected.trimStartMs + position
                                    if (absolute > selected.trimStartMs + 100 && absolute < selected.trimEndMs - 100) {
                                        snap()
                                        val left = selected.copy(trimEndMs = absolute, transitionOut = TransitionType.NONE, keyframes = selected.keyframes.filter { it.timeMs <= position })
                                        val right = selected.copy(id = UUID.randomUUID().toString(), name = "${selected.name} · 2", trimStartMs = absolute,
                                            keyframes = selected.keyframes.filter { it.timeMs >= position }.map { it.copy(id = UUID.randomUUID().toString(), timeMs = (it.timeMs - position).coerceAtLeast(0L)) })
                                        clips[index] = left; clips.add(index + 1, right); selectedId = right.id; position = 0L
                                    }
                                },
                                onTrimStart = { val a = selected.trimStartMs + position; if (a < selected.trimEndMs - 100) { snap(); replace(selected.copy(trimStartMs = a)); position = 0L } },
                                onTrimEnd = { val a = selected.trimStartMs + position; if (a > selected.trimStartMs + 100) { snap(); replace(selected.copy(trimEndMs = a)) } },
                                onMute = { snap(); replace(selected.copy(muted = !selected.muted)) }, onRotate = { snap(); replace(selected.copy(rotationDegrees = (selected.rotationDegrees + 90) % 360)) },
                                onFlipHorizontal = { snap(); replace(selected.copy(flipHorizontal = !selected.flipHorizontal)) }, onFlipVertical = { snap(); replace(selected.copy(flipVertical = !selected.flipVertical)) },
                                onDuplicate = { snap(); val copy = selected.copy(id = UUID.randomUUID().toString(), name = "${selected.name} · копия"); clips.add(index + 1, copy); selectedId = copy.id },
                                onMoveLeft = { if (index > 0) { snap(); val clip = clips.removeAt(index); clips.add(index - 1, clip) } }, onMoveRight = { if (index in 0 until clips.lastIndex) { snap(); val clip = clips.removeAt(index); clips.add(index + 1, clip) } },
                                onDelete = { snap(); clips.removeAt(index); selectedId = if (clips.isEmpty()) null else clips[index.coerceAtMost(clips.lastIndex)].id; position = 0L },
                                onUndo = ::undo, onRedo = ::redoAction,
                                onImport = { mediaFilePicker.launch(arrayOf("*/*")) })
                            MediaCreationPanel(mediaBusy,
                                { durationMs ->
                                    pendingImageDuration = durationMs
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                { durationMs -> if (!mediaBusy) clips.firstOrNull { it.id == selectedId }?.let { source -> mediaBusy = true; message = "Создаётся стоп-кадр"; imageMaker.createFreezeFrame(source, position, durationMs, { clip -> mediaBusy = false; insertAfterSelected(clip); message = "Стоп-кадр добавлен" }, { error -> mediaBusy = false; message = error }) } })
                            RhythmMontagePanel(music, beatMap, beatBusy,
                                { music?.let { analyzeMusic(it) } ?: run { message = "Сначала выберите музыку" } },
                                { style -> beatMap?.let { applyRhythm(style, it) } ?: music?.let { analyzeMusic(it, style) } ?: run { message = "Сначала выберите музыку" } })
                            BulkEditPanel(clips, selected, { snap() }, { message = it })
                        }
                        WorkspaceTab.LOOK -> {
                            AdjustmentsPanel(selected, { snap() }, { replace(it) }); FilterPanel(selected, { snap() }, { replace(it) }); ColorEffectsPanel(selected, { snap() }, { replace(it) })
                            CreativeStylePanel(selected, { snap() }, { replace(it) }); SpecialEffectPanel(selected, { snap() }, { replace(it) })
                        }
                        WorkspaceTab.MOTION -> {
                            MotionPanel(selected, { snap() }, { replace(it) }); TransitionPanel(selected, index in 0 until clips.lastIndex, { snap() }, { replace(it) })
                            KeyframePanel(selected, position, { snap() }, { replace(it) }); KeyframeCurvePanel(selected, { snap() }, { replace(it) })
                        }
                        WorkspaceTab.AUDIO -> {
                            ClipAudioPanel(selected, { snap() }, { replace(it) })
                            PositionedAudioPanel(audioTracks, cursor, { pendingAudioStart = cursor; audioPicker.launch(arrayOf("audio/*")) },
                                { audio -> val i = audioTracks.indexOfFirst { it.id == audio.id }; if (i >= 0) audioTracks[i] = audio }, { id -> audioTracks.removeAll { it.id == id } })
                            VoiceoverPanel(recording, if (recording) voiceStart else cursor,
                                { voiceStart = cursor; if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginVoice(cursor) else micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                                { val file = recorder.stop(); recording = false; if (file != null) { audioTracks += PositionedAudioTrack(UUID.randomUUID().toString(), Uri.fromFile(file).toString(), "Озвучка", duration(context, Uri.fromFile(file)), voiceStart, 1f); message = "Озвучка добавлена" } })
                            BackgroundMusicQuickPanel(music, beatMap, beatBusy, { musicPicker.launch(arrayOf("audio/*")) },
                                { music = null; beatAnalyzer.cancel(); beatBusy = false; beatMap = null; pendingRhythmStyle = null }, { music = it },
                                { music?.let { analyzeMusic(it) } ?: run { message = "Сначала выберите музыку" } })
                        }
                        WorkspaceTab.TEXT -> {
                            TextPanel(selected, { snap() }, { replace(it) })
                            SubtitleStudioPanel(subtitles, subtitleStyle, cursor, { srtPicker.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) },
                                { text -> subtitles += SubtitleCue(UUID.randomUUID().toString(), cursor, cursor + 2000L, text) }, { id -> subtitles.removeAll { it.id == id } }, { subtitles.clear() }, { subtitleStyle = it })
                            SubtitleExportPanel(subtitles)
                        }
                        WorkspaceTab.LAYERS -> {
                            StickerPanel(selected, { pendingStickerClip = selected.id; stickerPicker.launch(arrayOf("image/*")) }, { snap() }, { replace(it) })
                            AnimatedStickerPanel(selected, position, { snap() }, { replace(it) }); GifStickerPanel(selected, position, { snap() }, { replace(it) })
                            VideoOverlayPanel(pipBusy, (position / selected.speed.coerceAtLeast(.05f)).toLong()) { options -> pendingPipBaseId = selected.id; pendingPipOptions = options; pipPicker.launch(arrayOf("video/*", "application/octet-stream")) }
                        }
                        WorkspaceTab.AI -> { ObjectTrackingPanel(selected, { snap() }, { replace(it) }); PersonCutoutPanel(selected, { snap() }, { replace(it) }) }
                        WorkspaceTab.PROJECT -> {
                            AdvancedProjectPanel(music, exportSettings, { musicPicker.launch(arrayOf("audio/*")) },
                                { music = null; beatAnalyzer.cancel(); beatBusy = false; beatMap = null; pendingRhythmStyle = null }, { music = it }, { exportSettings = it })
                            ExportResultPanel(lastExport) {
                                val uri = lastExport ?: return@ExportResultPanel
                                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, Uri.parse(uri)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Поделиться видео")) }
                            }
                        }
                    }
                }
            }
            WorkspaceTabBar(activeTab) { activeTab = it }
        }
    }
}

private enum class ImportedMediaKind { IMAGE, VIDEO, UNKNOWN }

private fun persist(context: Context, uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

private fun mediaKind(context: Context, uri: Uri): ImportedMediaKind {
    val mime = runCatching { context.contentResolver.getType(uri)?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
    if (mime.startsWith("image/")) return ImportedMediaKind.IMAGE
    if (mime.startsWith("video/")) return ImportedMediaKind.VIDEO

    val ext = displayName(context, uri, "").substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "bmp", "dng")) return ImportedMediaKind.IMAGE
    if (ext in setOf("mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "3g2", "ts", "mts", "m2ts", "mpg", "mpeg", "flv", "vob", "ogv")) return ImportedMediaKind.VIDEO

    val retriever = MediaMetadataRetriever()
    return try {
        if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(context, uri)
        if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO).equals("yes", ignoreCase = true)) ImportedMediaKind.VIDEO
        else ImportedMediaKind.UNKNOWN
    } catch (_: Throwable) {
        ImportedMediaKind.UNKNOWN
    } finally {
        runCatching { retriever.release() }
    }
}

private fun readClip(context: Context, uri: Uri): VideoClip {
    val d = duration(context, uri)
    if (d <= 1L) error("не удалось определить длительность")
    return VideoClip(UUID.randomUUID().toString(), uri.toString(), displayName(context, uri, "Видео"), d)
}

private fun duration(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(context, uri)
        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1L).coerceAtLeast(1L)
    } finally { retriever.release() }
}
private fun displayName(context: Context, uri: Uri, fallback: String): String {
    if (uri.scheme == "file") return File(uri.path.orEmpty()).name.ifBlank { fallback }
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: fallback
}
