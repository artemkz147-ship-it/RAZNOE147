package ru.vibecut.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.math.max

class ObjectTrackProcessor(private val context: Context) {
    @Volatile private var cancelled = false
    private val main = Handler(Looper.getMainLooper())

    fun cancel() { cancelled = true }

    fun track(
        clip: VideoClip,
        targetX: Float,
        targetY: Float,
        sampleFps: Int = 10,
        onProgress: (Int) -> Unit,
        onDone: (List<TrackingPoint>) -> Unit,
        onError: (String) -> Unit,
    ) {
        cancelled = false
        thread(name = "VibeCutObjectTracking") {
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .build()
            val detector = ObjectDetection.getClient(options)
            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(clip.uri)
                if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(context, uri)
                val duration = clip.sourceSliceDurationMs.coerceAtLeast(1L)
                val fps = sampleFps.coerceIn(4, 20)
                val step = (1000L / fps).coerceAtLeast(45L)
                val path = mutableListOf<TrackingPoint>()
                var trackingId: Int? = null
                var lastX = targetX.coerceIn(-1f, 1f)
                var lastY = targetY.coerceIn(-1f, 1f)
                var firstObjectSize: Float? = null
                var time = 0L
                while (time <= duration && !cancelled) {
                    val absolute = (clip.trimStartMs + time).coerceAtMost(clip.trimEndMs)
                    val bitmap = retriever.getFrameAtTime(absolute * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (bitmap != null) {
                        val objects = runCatching { Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0))) }.getOrDefault(emptyList())
                        val chosen = chooseObject(objects, trackingId, lastX, lastY, bitmap)
                        if (chosen != null) {
                            if (trackingId == null) trackingId = chosen.trackingId
                            val box = chosen.boundingBox
                            val x = ((box.exactCenterX() / bitmap.width.toFloat()) * 2f - 1f).coerceIn(-1f, 1f)
                            val y = (1f - (box.exactCenterY() / bitmap.height.toFloat()) * 2f).coerceIn(-1f, 1f)
                            val objectSize = max(box.width().toFloat() / bitmap.width, box.height().toFloat() / bitmap.height).coerceAtLeast(.01f)
                            if (firstObjectSize == null) firstObjectSize = objectSize
                            val scale = (objectSize / (firstObjectSize ?: objectSize)).coerceIn(.45f, 2.2f)
                            val smoothX = if (path.isEmpty()) x else lastX * .62f + x * .38f
                            val smoothY = if (path.isEmpty()) y else lastY * .62f + y * .38f
                            lastX = smoothX; lastY = smoothY
                            path += TrackingPoint(time, smoothX, smoothY, scale)
                        }
                        bitmap.recycle()
                    }
                    val progress = ((time.toDouble() / duration.toDouble()) * 100.0).toInt().coerceIn(0, 99)
                    main.post { onProgress(progress) }
                    time += step
                }
                if (cancelled) return@thread
                if (path.size < 2) main.post { onError("Не удалось устойчиво отследить объект. Выберите точку ближе к объекту.") }
                else main.post { onProgress(100); onDone(reducePath(path)) }
            } catch (t: Throwable) {
                if (!cancelled) main.post { onError(t.message ?: "Ошибка отслеживания объекта") }
            } finally {
                runCatching { retriever.release() }
                runCatching { detector.close() }
            }
        }
    }

    private fun chooseObject(objects: List<DetectedObject>, trackingId: Int?, x: Float, y: Float, bitmap: Bitmap): DetectedObject? {
        if (objects.isEmpty()) return null
        trackingId?.let { id -> objects.firstOrNull { it.trackingId == id }?.let { return it } }
        val px = (x + 1f) * .5f * bitmap.width
        val py = (1f - y) * .5f * bitmap.height
        return objects.minByOrNull { obj ->
            val b = obj.boundingBox
            val inside = b.contains(px.toInt(), py.toInt())
            if (inside) -1_000_000.0 else hypot((b.exactCenterX() - px).toDouble(), (b.exactCenterY() - py).toDouble())
        }
    }

    private fun reducePath(path: List<TrackingPoint>): List<TrackingPoint> {
        if (path.size <= 80) return path
        val stride = (path.size / 80f).toInt().coerceAtLeast(1)
        return path.filterIndexed { index, _ -> index % stride == 0 }.let { reduced ->
            if (reduced.last().timeMs == path.last().timeMs) reduced else reduced + path.last()
        }
    }
}
