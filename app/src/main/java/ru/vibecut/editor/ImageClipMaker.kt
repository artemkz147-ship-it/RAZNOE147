package ru.vibecut.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(UnstableApi::class)
class ImageClipMaker(private val context: Context) {
    private var transformer: Transformer? = null
    private var pendingOutput: File? = null
    private var pendingFrame: File? = null

    fun cancel() {
        transformer?.cancel()
        transformer = null
        pendingOutput?.delete()
        pendingFrame?.delete()
        pendingOutput = null
        pendingFrame = null
    }

    fun createPhotoClip(
        imageUri: Uri,
        displayName: String,
        durationMs: Long,
        onDone: (VideoClip) -> Unit,
        onError: (String) -> Unit,
    ) {
        createVideoFromImage(
            imageUri = imageUri,
            displayName = displayName.substringBeforeLast('.').ifBlank { "Фото" },
            durationMs = durationMs,
            deleteSourceAfterwards = false,
            onDone = onDone,
            onError = onError,
        )
    }

    fun createFreezeFrame(
        clip: VideoClip,
        positionMs: Long,
        durationMs: Long,
        onDone: (VideoClip) -> Unit,
        onError: (String) -> Unit,
    ) {
        cancel()
        val sourceUri = Uri.parse(clip.uri)
        val retriever = MediaMetadataRetriever()
        val bitmap = try {
            if (sourceUri.scheme == "file") retriever.setDataSource(sourceUri.path)
            else retriever.setDataSource(context, sourceUri)
            val absoluteMs = (clip.trimStartMs + positionMs).coerceIn(clip.trimStartMs, clip.trimEndMs)
            retriever.getFrameAtTime(absoluteMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }

        if (bitmap == null) {
            onError("Не удалось получить кадр из видео")
            return
        }

        val frameDir = File(context.filesDir, "freeze_frames").apply { mkdirs() }
        val frameFile = File(frameDir, "frame_${System.currentTimeMillis()}.jpg")
        val saved = runCatching {
            FileOutputStream(frameFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
        }.getOrDefault(false)
        bitmap.recycle()

        if (!saved || !frameFile.exists() || frameFile.length() == 0L) {
            frameFile.delete()
            onError("Не удалось сохранить стоп-кадр")
            return
        }

        pendingFrame = frameFile
        createVideoFromImage(
            imageUri = Uri.fromFile(frameFile),
            displayName = "Стоп-кадр",
            durationMs = durationMs,
            deleteSourceAfterwards = true,
            onDone = onDone,
            onError = onError,
        )
    }

    private fun createVideoFromImage(
        imageUri: Uri,
        displayName: String,
        durationMs: Long,
        deleteSourceAfterwards: Boolean,
        onDone: (VideoClip) -> Unit,
        onError: (String) -> Unit,
    ) {
        transformer?.cancel()
        transformer = null
        pendingOutput?.delete()

        val duration = durationMs.coerceIn(500L, 30_000L)
        val outDir = File(context.filesDir, "generated_clips").apply { mkdirs() }
        val output = File(outDir, "clip_${System.currentTimeMillis()}_${UUID.randomUUID()}.mp4")
        pendingOutput = output

        val mediaItem = MediaItem.Builder()
            .setUri(imageUri)
            .setImageDurationMs(duration)
            .build()
        val edited = EditedMediaItem.Builder(mediaItem)
            .setFrameRate(30)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                transformer = null
                pendingOutput = null
                if (deleteSourceAfterwards) {
                    pendingFrame?.delete()
                    pendingFrame = null
                }
                if (!output.exists() || output.length() == 0L) {
                    onError("Не удалось создать видеоклип из изображения")
                    return
                }
                onDone(
                    VideoClip(
                        id = UUID.randomUUID().toString(),
                        uri = Uri.fromFile(output).toString(),
                        name = displayName,
                        sourceDurationMs = duration,
                        muted = true,
                    )
                )
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                transformer = null
                pendingOutput = null
                output.delete()
                if (deleteSourceAfterwards) {
                    pendingFrame?.delete()
                    pendingFrame = null
                }
                onError(exportException.message ?: "Ошибка создания клипа")
            }
        }

        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(listener)
            .build()
        runCatching { transformer?.start(edited, output.absolutePath) }
            .onFailure {
                transformer = null
                pendingOutput = null
                output.delete()
                if (deleteSourceAfterwards) {
                    pendingFrame?.delete()
                    pendingFrame = null
                }
                onError(it.message ?: "Не удалось запустить создание клипа")
            }
    }
}
