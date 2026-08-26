package ru.vibecut.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

internal data class PipOptions(
    val x: Float = 0.62f,
    val y: Float = 0.62f,
    val scale: Float = 0.38f,
    val alpha: Float = 1f,
    val rotation: Float = 0f,
    val startAtMs: Long = 0L,
)

@OptIn(UnstableApi::class)
class VideoOverlayMaker(private val context: Context) {
    private var transformer: Transformer? = null
    private var pendingOutput: File? = null

    fun cancel() {
        transformer?.cancel()
        transformer = null
        pendingOutput?.delete()
        pendingOutput = null
    }

    fun create(
        base: VideoClip,
        overlayUri: Uri,
        overlayName: String,
        options: PipOptions,
        onDone: (VideoClip) -> Unit,
        onError: (String) -> Unit,
    ) {
        cancel()
        val overlayDuration = mediaDuration(overlayUri)
        val startAt = options.startAtMs.coerceIn(0L, (base.durationMs - 100L).coerceAtLeast(0L))
        val available = (base.durationMs - startAt).coerceAtLeast(100L)
        val playDuration = min(overlayDuration, available).coerceAtLeast(100L)

        val baseMedia = MediaItem.Builder()
            .setUri(base.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(base.trimStartMs)
                    .setEndPositionMs(base.trimEndMs)
                    .build()
            )
            .build()
        val bakedBase = base.copy(transitionOut = TransitionType.NONE)
        val baseEdited = EditedMediaItem.Builder(baseMedia)
            .setRemoveAudio(base.muted)
            .setSpeed(ConstantSpeedProvider(base.speed))
            .setFrameRate(30)
            .setEffects(Effects(buildClipAudioEffects(base), buildVideoEffects(context, bakedBase, null)))
            .build()

        val overlayMedia = MediaItem.Builder()
            .setUri(overlayUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0L)
                    .setEndPositionMs(playDuration)
                    .build()
            )
            .build()
        val overlayEdited = EditedMediaItem.Builder(overlayMedia)
            .setRemoveAudio(true)
            .setFrameRate(30)
            .build()

        val overlayItems = mutableListOf<EditedMediaItem>()
        if (startAt > 0L) overlayItems += transparentLeadIn(startAt)
        overlayItems += overlayEdited

        val overlaySequence = EditedMediaItemSequence.withVideoFrom(overlayItems)
        val baseSequence = EditedMediaItemSequence.withAudioAndVideoFrom(listOf(baseEdited))
        val compositor = object : VideoCompositorSettings {
            override fun getOutputSize(inputSizes: List<Size>): Size = inputSizes.getOrElse(1) { inputSizes[0] }
            override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings =
                if (inputId == 0) {
                    StaticOverlaySettings.Builder()
                        .setScale(options.scale.coerceIn(0.12f, 0.9f), options.scale.coerceIn(0.12f, 0.9f))
                        .setOverlayFrameAnchor(0f, 0f)
                        .setBackgroundFrameAnchor(options.x.coerceIn(-0.85f, 0.85f), options.y.coerceIn(-0.85f, 0.85f))
                        .setRotationDegrees(options.rotation.coerceIn(-180f, 180f))
                        .setAlphaScale(options.alpha.coerceIn(0.1f, 1f))
                        .build()
                } else {
                    StaticOverlaySettings.Builder().build()
                }
        }

        val composition = Composition.Builder(listOf(overlaySequence, baseSequence))
            .setVideoCompositorSettings(compositor)
            .build()
        val outDir = File(context.filesDir, "generated_clips").apply { mkdirs() }
        val output = File(outDir, "pip_${System.currentTimeMillis()}_${UUID.randomUUID()}.mp4")
        pendingOutput = output
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                transformer = null
                pendingOutput = null
                if (!output.exists() || output.length() == 0L) {
                    onError("Не удалось создать клип с наложением")
                    return
                }
                onDone(
                    VideoClip(
                        id = base.id,
                        uri = Uri.fromFile(output).toString(),
                        name = "${base.name} · ${overlayName.ifBlank { "видео поверх" }}",
                        sourceDurationMs = base.durationMs,
                        muted = base.muted,
                        transitionOut = base.transitionOut,
                        transitionDurationMs = base.transitionDurationMs,
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
                onError(exportException.message ?: "Ошибка наложения видео")
            }
        }
        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(listener)
            .build()
        runCatching { transformer?.start(composition, output.absolutePath) }
            .onFailure {
                transformer = null
                pendingOutput = null
                output.delete()
                onError(it.message ?: "Не удалось запустить наложение видео")
            }
    }

    private fun transparentLeadIn(durationMs: Long): EditedMediaItem {
        val dir = File(context.cacheDir, "pip").apply { mkdirs() }
        val file = File(dir, "transparent.png")
        if (!file.exists() || file.length() == 0L) {
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).also { bitmap ->
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        val media = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setImageDurationMs(durationMs.coerceAtLeast(1L))
            .build()
        return EditedMediaItem.Builder(media).setFrameRate(30).build()
    }

    private fun mediaDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") retriever.setDataSource(uri.path)
            else retriever.setDataSource(context, uri)
            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1L).coerceAtLeast(1L)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
