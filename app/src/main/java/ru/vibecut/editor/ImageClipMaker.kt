package ru.vibecut.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
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
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class ImageClipMaker(private val context: Context) {
    private var transformer: Transformer? = null
    private var pendingOutput: File? = null
    private var pendingFrame: File? = null
    private var pendingNormalizedImage: File? = null

    fun cancel() {
        transformer?.cancel()
        transformer = null
        pendingOutput?.delete()
        pendingFrame?.delete()
        pendingNormalizedImage?.delete()
        pendingOutput = null
        pendingFrame = null
        pendingNormalizedImage = null
    }

    fun createPhotoClip(
        imageUri: Uri,
        displayName: String,
        durationMs: Long,
        onDone: (VideoClip) -> Unit,
        onError: (String) -> Unit,
    ) {
        cancel()
        val normalized = runCatching { normalizeImage(imageUri) }.getOrElse {
            onError("Не удалось прочитать изображение: ${it.message ?: "неподдерживаемый формат"}")
            return
        }
        pendingNormalizedImage = normalized
        createVideoFromImage(
            imageUri = Uri.fromFile(normalized),
            displayName = displayName.substringBeforeLast('.').ifBlank { "Фото" },
            durationMs = durationMs,
            deleteSourceAfterwards = false,
            onDone = {
                pendingNormalizedImage?.delete()
                pendingNormalizedImage = null
                onDone(it)
            },
            onError = {
                pendingNormalizedImage?.delete()
                pendingNormalizedImage = null
                onError(it)
            },
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

    private fun normalizeImage(uri: Uri): File {
        val bitmap = decodeBitmap(uri) ?: error("Android не смог декодировать файл")
        val dir = File(context.cacheDir, "vibecut_imports").apply { mkdirs() }
        val hasAlpha = bitmap.hasAlpha()
        val output = File(dir, "image_${UUID.randomUUID()}.${if (hasAlpha) "png" else "jpg"}")
        val saved = FileOutputStream(output).use { stream ->
            if (hasAlpha) bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            else bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream)
        }
        bitmap.recycle()
        if (!saved || output.length() == 0L) {
            output.delete()
            error("Не удалось подготовить изображение")
        }
        return output
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = if (uri.scheme == "file") {
                ImageDecoder.createSource(File(uri.path ?: return null))
            } else {
                ImageDecoder.createSource(context.contentResolver, uri)
            }
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width.coerceAtLeast(1)
                val height = info.size.height.coerceAtLeast(1)
                val longest = max(width, height)
                if (longest > 4096) {
                    val scale = 4096f / longest.toFloat()
                    decoder.setTargetSize(
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        }

        fun open(): InputStream? {
            return if (uri.scheme == "file") {
                val path = uri.path ?: return null
                File(path).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > 4096) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return open()?.use { BitmapFactory.decodeStream(it, null, options) }
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
