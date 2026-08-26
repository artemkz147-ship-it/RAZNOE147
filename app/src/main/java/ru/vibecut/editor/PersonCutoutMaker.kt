package ru.vibecut.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class CutoutBackground(val title: String) {
    BLUR("Размытый фон"),
    BLACK("Чёрный"),
    WHITE("Белый"),
    GREEN("Зелёный"),
    BLUE("Синий"),
    WARM("Тёплый"),
}

internal data class CutoutOptions(
    val background: CutoutBackground = CutoutBackground.BLUR,
    val threshold: Float = 0.50f,
    val feather: Float = 0.18f,
    val fps: Int = 15,
    val maxSide: Int = 720,
)

@OptIn(UnstableApi::class)
internal class PersonCutoutMaker(private val context: Context) {
    private val generation = AtomicInteger(0)
    private val main = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    private var tempFramesDir: File? = null
    private var pendingOutput: File? = null

    fun cancel() {
        generation.incrementAndGet()
        transformer?.cancel()
        transformer = null
        pendingOutput?.delete()
        pendingOutput = null
        tempFramesDir?.deleteRecursively()
        tempFramesDir = null
    }

    fun create(
        clip: VideoClip,
        options: CutoutOptions,
        onProgress: (Int) -> Unit,
        onDone: (VideoClip) -> Unit,
        onError: (String) -> Unit,
    ) {
        cancel()
        val token = generation.incrementAndGet()
        Thread {
            runCatching { prepareFrames(clip, options, token, onProgress) }
                .onSuccess { prepared ->
                    if (token != generation.get()) {
                        prepared.frameDir.deleteRecursively()
                        return@onSuccess
                    }
                    main.post { encodePrepared(clip, options, prepared, token, onProgress, onDone, onError) }
                }
                .onFailure { error ->
                    main.post { if (token == generation.get()) onError(error.message ?: "Не удалось вырезать человека") }
                }
        }.apply { name = "VibeCut-Cutout"; isDaemon = true }.start()
    }

    private data class PreparedFrames(
        val files: List<File>,
        val frameDir: File,
        val frameDurationMs: Long,
        val sourceDurationMs: Long,
    )

    private fun prepareFrames(
        clip: VideoClip,
        options: CutoutOptions,
        token: Int,
        onProgress: (Int) -> Unit,
    ): PreparedFrames {
        val fps = options.fps.coerceIn(8, 24)
        val frameDurationMs = (1000L / fps).coerceAtLeast(1L)
        val sourceDuration = clip.sourceSliceDurationMs.coerceAtLeast(frameDurationMs)
        val count = ((sourceDuration + frameDurationMs - 1L) / frameDurationMs).toInt().coerceAtLeast(1)
        val frameDir = File(context.cacheDir, "cutout_${System.currentTimeMillis()}_${UUID.randomUUID()}").apply { mkdirs() }
        tempFramesDir = frameDir

        val optionsMl = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build()
        val segmenter = Segmentation.getClient(optionsMl)
        val retriever = MediaMetadataRetriever()
        val uri = Uri.parse(clip.uri)
        val files = ArrayList<File>(count)
        try {
            if (uri.scheme == "file") retriever.setDataSource(uri.path)
            else retriever.setDataSource(context, uri)

            for (i in 0 until count) {
                if (token != generation.get()) error("Обработка отменена")
                val relativeMs = min(sourceDuration - 1L, i * frameDurationMs)
                val absoluteMs = clip.trimStartMs + relativeMs
                val raw = retriever.getFrameAtTime(absoluteMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: error("Не удалось получить кадр ${i + 1}")
                val frame = scaleDown(raw, options.maxSide.coerceIn(480, 1080))
                if (frame !== raw) raw.recycle()

                val image = InputImage.fromBitmap(frame, 0)
                val mask = Tasks.await(segmenter.process(image), 20, TimeUnit.SECONDS)
                val composite = compositeFrame(frame, mask.buffer, mask.width, mask.height, options)
                frame.recycle()
                val out = File(frameDir, "frame_${i.toString().padStart(6, '0')}.jpg")
                FileOutputStream(out).use { stream ->
                    if (!composite.compress(Bitmap.CompressFormat.JPEG, 92, stream)) error("Не удалось сохранить обработанный кадр")
                }
                composite.recycle()
                files += out
                val percent = ((i + 1) * 82 / count).coerceIn(1, 82)
                main.post { if (token == generation.get()) onProgress(percent) }
            }
        } finally {
            runCatching { retriever.release() }
            runCatching { segmenter.close() }
        }
        return PreparedFrames(files, frameDir, frameDurationMs, sourceDuration)
    }

    private fun encodePrepared(
        original: VideoClip,
        options: CutoutOptions,
        prepared: PreparedFrames,
        token: Int,
        onProgress: (Int) -> Unit,
        onDone: (VideoClip) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (prepared.files.isEmpty()) {
            prepared.frameDir.deleteRecursively()
            onError("Нет обработанных кадров")
            return
        }
        val frameItems = prepared.files.mapIndexed { index, file ->
            val duration = if (index == prepared.files.lastIndex) {
                (prepared.sourceDurationMs - prepared.frameDurationMs * index).coerceAtLeast(1L)
            } else prepared.frameDurationMs
            EditedMediaItem.Builder(
                MediaItem.Builder().setUri(Uri.fromFile(file)).setImageDurationMs(duration).build()
            ).setFrameRate(options.fps.coerceIn(8,24)).build()
        }
        val videoSequence = EditedMediaItemSequence.withVideoFrom(frameItems)

        val audioMedia = MediaItem.Builder()
            .setUri(original.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(original.trimStartMs)
                    .setEndPositionMs(original.trimEndMs)
                    .build()
            )
            .build()
        val audioEdited = EditedMediaItem.Builder(audioMedia).setRemoveVideo(true).build()
        val audioSequence = EditedMediaItemSequence.withAudioFrom(listOf(audioEdited))
        val composition = Composition.Builder(listOf(videoSequence, audioSequence)).build()
        val outDir = File(context.filesDir, "generated_clips").apply { mkdirs() }
        val output = File(outDir, "cutout_${System.currentTimeMillis()}_${UUID.randomUUID()}.mp4")
        pendingOutput = output
        tempFramesDir = prepared.frameDir

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                transformer = null
                pendingOutput = null
                prepared.frameDir.deleteRecursively()
                tempFramesDir = null
                if (token != generation.get()) {
                    output.delete()
                    return
                }
                if (!output.exists() || output.length() == 0L) {
                    onError("Не удалось собрать видео после вырезки")
                    return
                }
                onProgress(100)
                onDone(
                    original.copy(
                        uri = Uri.fromFile(output).toString(),
                        name = "${original.name} · вырезка",
                        sourceDurationMs = prepared.sourceDurationMs,
                        trimStartMs = 0L,
                        trimEndMs = prepared.sourceDurationMs,
                    )
                )
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                transformer = null
                pendingOutput = null
                prepared.frameDir.deleteRecursively()
                tempFramesDir = null
                output.delete()
                if (token == generation.get()) onError(exportException.message ?: "Ошибка сборки видео после вырезки")
            }
        }
        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()
        runCatching { transformer?.start(composition, output.absolutePath) }
            .onFailure {
                transformer = null
                pendingOutput = null
                prepared.frameDir.deleteRecursively()
                tempFramesDir = null
                output.delete()
                onError(it.message ?: "Не удалось запустить сборку вырезанного видео")
            }
        onProgress(84)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest.toFloat()
        val width = max(2, (bitmap.width * scale).roundToInt())
        val height = max(2, (bitmap.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun compositeFrame(
        source: Bitmap,
        buffer: java.nio.ByteBuffer,
        maskWidth: Int,
        maskHeight: Int,
        options: CutoutOptions,
    ): Bitmap {
        buffer.rewind()
        val maskPixels = IntArray(maskWidth * maskHeight)
        val threshold = options.threshold.coerceIn(.20f,.80f)
        val feather = options.feather.coerceIn(.02f,.40f)
        for (i in maskPixels.indices) {
            val confidence = buffer.float
            val alpha = smoothstep(threshold - feather, threshold + feather, confidence)
            maskPixels[i] = Color.argb((alpha * 255f).roundToInt().coerceIn(0,255),255,255,255)
        }
        val smallMask = Bitmap.createBitmap(maskPixels, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val mask = Bitmap.createScaledBitmap(smallMask, source.width, source.height, true)
        if (mask !== smallMask) smallMask.recycle()

        val foreground = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val fgCanvas = Canvas(foreground)
        fgCanvas.drawBitmap(source, 0f, 0f, null)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        fgCanvas.drawBitmap(mask, 0f, 0f, maskPaint)
        mask.recycle()

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        when (options.background) {
            CutoutBackground.BLUR -> {
                val tinyW = max(16, source.width / 18)
                val tinyH = max(16, source.height / 18)
                val tiny = Bitmap.createScaledBitmap(source, tinyW, tinyH, true)
                val blurred = Bitmap.createScaledBitmap(tiny, source.width, source.height, true)
                if (tiny !== blurred) tiny.recycle()
                canvas.drawBitmap(blurred, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                if (blurred !== source) blurred.recycle()
            }
            CutoutBackground.BLACK -> canvas.drawColor(Color.BLACK)
            CutoutBackground.WHITE -> canvas.drawColor(Color.WHITE)
            CutoutBackground.GREEN -> canvas.drawColor(Color.rgb(0,177,64))
            CutoutBackground.BLUE -> canvas.drawColor(Color.rgb(24,82,190))
            CutoutBackground.WARM -> canvas.drawColor(Color.rgb(215,178,139))
        }
        canvas.drawBitmap(foreground, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        foreground.recycle()
        return result
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val x = ((value - edge0) / (edge1 - edge0).coerceAtLeast(.0001f)).coerceIn(0f,1f)
        return x * x * (3f - 2f * x)
    }
}
