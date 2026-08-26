package ru.vibecut.editor

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream

@OptIn(UnstableApi::class)
class ExportManager(private val context: Context) {
    private var transformer: Transformer? = null
    private var tempFile: File? = null
    private var active = false

    fun export(
        clips: List<VideoClip>,
        backgroundAudio: AudioTrack?,
        settings: ExportSettings,
        onProgress: (Int) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (clips.isEmpty()) {
            onError("Добавьте хотя бы один ролик")
            return
        }

        cancel()
        active = true

        val items = clips.map { clip ->
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.trimStartMs)
                .setEndPositionMs(clip.trimEndMs)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(clipping)
                .build()

            EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(clip.muted)
                .setSpeed(ConstantSpeedProvider(clip.speed))
                .setFrameRate(settings.maxFrameRate)
                .setEffects(
                    Effects(
                        buildClipAudioEffects(clip),
                        buildVideoEffects(clip),
                    )
                )
                .build()
        }

        val videoSequence = EditedMediaItemSequence.withAudioAndVideoFrom(items)
        val sequences = mutableListOf(videoSequence)

        if (backgroundAudio != null) {
            val musicItem = EditedMediaItem.Builder(
                MediaItem.fromUri(backgroundAudio.uri)
            )
                .setEffects(Effects(buildBackgroundAudioEffects(backgroundAudio), emptyList()))
                .build()
            val musicSequence = EditedMediaItemSequence
                .withAudioFrom(listOf(musicItem))
                .buildUpon()
                .setIsLooping(true)
                .build()
            sequences += musicSequence
        }

        val composition = Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), buildCanvasEffects(settings, includeResolution = true)))
            .build()

        val output = File(context.cacheDir, "vibecut_${System.currentTimeMillis()}.mp4")
        tempFile = output

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                active = false
                runCatching { saveToGallery(output) }
                    .onSuccess { uri ->
                        output.delete()
                        onProgress(100)
                        onDone(uri)
                    }
                    .onFailure { error ->
                        onError("Экспорт завершён, но файл не удалось сохранить: ${error.message}")
                    }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                active = false
                onError(exportException.message ?: "Ошибка экспорта")
            }
        }

        transformer = Transformer.Builder(context)
            .setVideoMimeType(settings.videoCodec.mimeType)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()

        transformer?.start(composition, output.absolutePath)
        pollProgress(onProgress)
    }

    private fun pollProgress(onProgress: (Int) -> Unit) {
        val current = transformer ?: return
        val holder = ProgressHolder()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (!active || transformer !== current) return
                val state = current.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress)
                }
                handler.postDelayed(this, 300L)
            }
        }
        handler.post(runnable)
    }

    fun cancel() {
        active = false
        transformer?.cancel()
        tempFile?.delete()
        transformer = null
        tempFile = null
    }

    private fun saveToGallery(file: File): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "VibeCut_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VibeCut")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values)
            ?: error("Не удалось создать файл в галерее")

        resolver.openOutputStream(uri)?.use { output ->
            FileInputStream(file).use { input -> input.copyTo(output) }
        } ?: error("Не удалось открыть файл для записи")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri.toString()
    }
}
