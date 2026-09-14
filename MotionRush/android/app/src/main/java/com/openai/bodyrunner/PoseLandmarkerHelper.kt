package com.openai.bodyrunner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

internal class PoseLandmarkerHelper(
    context: Context,
    private val listener: Listener,
) {
    private val landmarker: PoseLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .setDelegate(Delegate.CPU)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinPoseDetectionConfidence(0.55f)
            .setMinPosePresenceConfidence(0.55f)
            .setMinTrackingConfidence(0.55f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this::onResult)
            .setErrorListener { error -> listener.onError(error.message ?: "MediaPipe error") }
            .build()

        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val frameTime = SystemClock.uptimeMillis()
        val width = imageProxy.width
        val height = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees
        val bitmapBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        try {
            imageProxy.planes[0].buffer.rewind()
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        } finally {
            imageProxy.close()
        }

        val matrix = Matrix().apply {
            postRotate(rotation.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f, width.toFloat(), height.toFloat())
            }
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer,
            0,
            0,
            bitmapBuffer.width,
            bitmapBuffer.height,
            matrix,
            true,
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        landmarker.detectAsync(mpImage, frameTime)
    }

    fun close() {
        landmarker.close()
    }

    private fun onResult(result: PoseLandmarkerResult, input: MPImage) {
        if (result.landmarks().isEmpty()) {
            listener.onNoPose()
            return
        }

        val landmarks = result.landmarks().first()
        if (landmarks.size < 29) {
            listener.onNoPose()
            return
        }

        fun p(index: Int) = MotionPoint(landmarks[index].x(), landmarks[index].y())
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
