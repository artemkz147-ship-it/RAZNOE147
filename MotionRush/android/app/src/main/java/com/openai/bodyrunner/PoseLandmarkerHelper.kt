package com.openai.bodyrunner

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

internal class PoseLandmarkerHelper(
    context: Context,
    private val listener: Listener,
) {
    private data class Backend(
        val label: String,
        val modelAsset: String,
        val delegate: Delegate,
    )

    private data class Submission(
        val submittedAtMs: Long,
        val mirrorX: Boolean,
    )

    private val candidates = listOf(
        Backend("GPU FULL", "pose_landmarker_full.task", Delegate.GPU),
        Backend("GPU LITE", "pose_landmarker_lite.task", Delegate.GPU),
        Backend("CPU FULL", "pose_landmarker_full.task", Delegate.CPU),
        Backend("CPU LITE", "pose_landmarker_lite.task", Delegate.CPU),
    )

    private val landmarker: PoseLandmarker
    val backendLabel: String

    @Volatile
    var latestLatencyMs: Long = 0L
        private set

    private var bitmapBuffer: Bitmap? = null
    private var lastSubmittedAt = 0L
    private val submissions = LinkedHashMap<Long, Submission>()

    init {
        val failures = mutableListOf<String>()
        var selected: Pair<Backend, PoseLandmarker>? = null

        for (backend in candidates) {
            val attempt = runCatching {
                createLandmarker(
                    context = context,
                    modelAsset = backend.modelAsset,
                    delegate = backend.delegate,
                )
            }
            if (attempt.isSuccess) {
                selected = backend to attempt.getOrThrow()
                break
            }
            val message = attempt.exceptionOrNull()?.message ?: "unknown error"
            failures += "${backend.label}: $message"
        }

        val chosen = selected ?: throw IllegalStateException(
            "MediaPipe backend initialization failed: ${failures.joinToString(" | ")}",
        )
        landmarker = chosen.second
        backendLabel = chosen.first.label
    }

    private fun createLandmarker(
        context: Context,
        modelAsset: String,
        delegate: Delegate,
    ): PoseLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelAsset)
            .setDelegate(delegate)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinPoseDetectionConfidence(0.50f)
            .setMinPosePresenceConfidence(0.50f)
            .setMinTrackingConfidence(0.45f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this::onResult)
            .setErrorListener { error -> listener.onError(error.message ?: "MediaPipe error") }
            .build()

        return PoseLandmarker.createFromOptions(context, options)
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val frameTime = SystemClock.uptimeMillis()
        if (frameTime - lastSubmittedAt < 28L) {
            imageProxy.close()
            return
        }
        lastSubmittedAt = frameTime

        val width = imageProxy.width
        val height = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputBitmap = obtainBitmap(width, height)

        try {
            imageProxy.planes[0].buffer.rewind()
            inputBitmap.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        } finally {
            imageProxy.close()
        }

        val mpImage = BitmapImageBuilder(inputBitmap).build()
        val processingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(rotation)
            .build()

        rememberSubmission(frameTime, isFrontCamera)
        landmarker.detectAsync(mpImage, processingOptions, frameTime)
    }

    private fun obtainBitmap(width: Int, height: Int): Bitmap {
        val current = bitmapBuffer
        if (current != null && current.width == width && current.height == height && !current.isRecycled) {
            return current
        }
        current?.recycle()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            bitmapBuffer = it
        }
    }

    private fun rememberSubmission(timestampMs: Long, mirrorX: Boolean) {
        synchronized(submissions) {
            submissions[timestampMs] = Submission(
                submittedAtMs = SystemClock.uptimeMillis(),
                mirrorX = mirrorX,
            )
            while (submissions.size > 12) {
                val firstKey = submissions.keys.firstOrNull() ?: break
                submissions.remove(firstKey)
            }
        }
    }

    private fun consumeSubmission(timestampMs: Long): Submission? = synchronized(submissions) {
        val matched = submissions.remove(timestampMs)
        val iterator = submissions.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key <= timestampMs) iterator.remove()
        }
        matched
    }

    fun close() {
        landmarker.close()
        bitmapBuffer?.recycle()
        bitmapBuffer = null
        synchronized(submissions) { submissions.clear() }
    }

    private fun onResult(result: PoseLandmarkerResult, input: MPImage) {
        val submission = consumeSubmission(result.timestampMs())
        val now = SystemClock.uptimeMillis()
        latestLatencyMs = (now - (submission?.submittedAtMs ?: result.timestampMs())).coerceAtLeast(0L)

        if (result.landmarks().isEmpty()) {
            listener.onNoPose()
            return
        }

        val normalized = result.landmarks().first()
        if (normalized.size < 29) {
            listener.onNoPose()
            return
        }
        val world = result.worldLandmarks().firstOrNull()
        val mirrorX = submission?.mirrorX ?: true

        fun raw(landmark: NormalizedLandmark) = RawLandmark(
            x = landmark.x(),
            y = landmark.y(),
            z = landmark.z(),
            visibility = if (landmark.visibility().isPresent) landmark.visibility().get() else null,
            presence = if (landmark.presence().isPresent) landmark.presence().get() else null,
        )

        fun raw(landmark: Landmark) = RawLandmark(
            x = landmark.x(),
            y = landmark.y(),
            z = landmark.z(),
            visibility = if (landmark.visibility().isPresent) landmark.visibility().get() else null,
            presence = if (landmark.presence().isPresent) landmark.presence().get() else null,
        )

        fun p(index: Int): MotionPoint {
            val worldPoint = world?.getOrNull(index)?.let { raw(it) }
            return PoseLandmarkMapper.mapPoint(raw(normalized[index]), worldPoint, mirrorX)
        }

        val pose = MotionPose(
            nose = p(0),
            leftShoulder = p(11),
            rightShoulder = p(12),
            leftElbow = p(13),
            rightElbow = p(14),
            leftWrist = p(15),
            rightWrist = p(16),
            leftHip = p(23),
            rightHip = p(24),
            leftKnee = p(25),
            rightKnee = p(26),
            leftAnkle = p(27),
            rightAnkle = p(28),
        )
        listener.onPose(pose, result.timestampMs(), input.width, input.height)
    }

    interface Listener {
        fun onPose(pose: MotionPose, timestampMs: Long, width: Int, height: Int)
        fun onNoPose()
        fun onError(message: String)
    }
}
