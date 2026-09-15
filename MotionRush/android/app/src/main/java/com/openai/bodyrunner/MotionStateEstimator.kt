package com.openai.bodyrunner

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal class MotionStateEstimator {
    private data class Baseline(
        var centerX: Float,
        var hipY: Float,
        var shoulderCenterX: Float,
        var shoulderWidth: Float,
        var bodyHeight: Float,
        var kneeGap: Float,
    )

    private val calibrationSamples = mutableListOf<MotionPose>()
    private var baseline: Baseline? = null
    private var previousPose: MotionPose? = null
    private var previousState: MotionState? = null
    private var previousAtMs: Long? = null

    private val hipXFilter = OneEuroFilter(1.0f, 0.08f, 1.0f)
    private val hipYFilter = OneEuroFilter(1.0f, 0.08f, 1.0f)
    private val shoulderXFilter = OneEuroFilter(1.0f, 0.08f, 1.0f)
    private val shoulderYFilter = OneEuroFilter(1.0f, 0.08f, 1.0f)
    private val leftWristXFilter = OneEuroFilter(1.35f, 0.20f, 1.0f)
    private val leftWristYFilter = OneEuroFilter(1.35f, 0.20f, 1.0f)
    private val rightWristXFilter = OneEuroFilter(1.35f, 0.20f, 1.0f)
    private val rightWristYFilter = OneEuroFilter(1.35f, 0.20f, 1.0f)

    private val gestureEngine = MotionGestureEngine()

    @Synchronized
    fun addCalibrationSample(pose: MotionPose): Boolean {
        if (poseConfidence(pose) < 0.60f) return false
        val hip = midpoint(pose.leftHip, pose.rightHip)
        val shoulderWidth = abs(pose.rightShoulder.x - pose.leftShoulder.x)
        val shoulder = midpoint(pose.leftShoulder, pose.rightShoulder)
        val bodyHeight = abs(hip.y - shoulder.y)
        if (shoulderWidth < 0.05f || bodyHeight < 0.08f) return false

        if (calibrationSamples.isNotEmpty()) {
            val meanCenter = calibrationSamples.map { midpoint(it.leftHip, it.rightHip).x }.average().toFloat()
            val meanWidth = calibrationSamples.map { abs(it.rightShoulder.x - it.leftShoulder.x) }.average().toFloat()
            if (abs(hip.x - meanCenter) > 0.04f) return false
            if (meanWidth > 0f && abs(shoulderWidth - meanWidth) / meanWidth > 0.15f) return false
        }
        calibrationSamples += pose
        return true
    }

    @Synchronized
    fun finishCalibration(): Boolean {
        if (calibrationSamples.size < 10) return false
        fun averageOf(block: (MotionPose) -> Float): Float = calibrationSamples.map(block).average().toFloat()
        baseline = Baseline(
            centerX = averageOf { midpoint(it.leftHip, it.rightHip).x },
            hipY = averageOf { midpoint(it.leftHip, it.rightHip).y },
            shoulderCenterX = averageOf { midpoint(it.leftShoulder, it.rightShoulder).x },
            shoulderWidth = averageOf { abs(it.rightShoulder.x - it.leftShoulder.x) }.coerceAtLeast(0.05f),
            bodyHeight = averageOf {
                abs(midpoint(it.leftHip, it.rightHip).y - midpoint(it.leftShoulder, it.rightShoulder).y)
            }.coerceAtLeast(0.08f),
            kneeGap = averageOf {
                ((it.leftKnee.y - it.leftHip.y) + (it.rightKnee.y - it.rightHip.y)) / 2f
            },
        )
        calibrationSamples.clear()
        resetRuntime()
        gestureEngine.resetStateTracking()
        return true
    }

    @Synchronized
    fun push(pose: MotionPose, nowMs: Long): MotionFrame? {
        val b = baseline ?: return null
        val confidence = poseConfidence(pose)
        if (confidence < 0.45f) return null

        val seconds = nowMs / 1000f
        val rawHip = midpoint(pose.leftHip, pose.rightHip)
        val rawShoulder = midpoint(pose.leftShoulder, pose.rightShoulder)
        val hipX = hipXFilter.filter(rawHip.x, seconds)
        val hipY = hipYFilter.filter(rawHip.y, seconds)
        val shoulderX = shoulderXFilter.filter(rawShoulder.x, seconds)
        val shoulderY = shoulderYFilter.filter(rawShoulder.y, seconds)
        val leftWristX = leftWristXFilter.filter(pose.leftWrist.x, seconds)
        val leftWristY = leftWristYFilter.filter(pose.leftWrist.y, seconds)
        val rightWristX = rightWristXFilter.filter(pose.rightWrist.x, seconds)
        val rightWristY = rightWristYFilter.filter(pose.rightWrist.y, seconds)

        val previousTime = previousAtMs
        val dt = if (previousTime == null) 0f else ((nowMs - previousTime).coerceIn(1L, 220L) / 1000f)
        val prevState = previousState
        val previousRaw = previousPose

        val centerX = (hipX - b.centerX) / b.shoulderWidth
        val centerY = (hipY - b.hipY) / b.bodyHeight
        val lateralVelocity = if (dt > 0f && prevState != null) (centerX - prevState.centerX) / dt else 0f
        val verticalVelocity = if (dt > 0f && prevState != null) (centerY - prevState.centerY) / dt else 0f
        val torsoLean = centerX + ((shoulderX - hipX) - (b.shoulderCenterX - b.centerX)) / b.shoulderWidth * 0.55f

        val leftArmElevation = (shoulderY - leftWristY) / b.bodyHeight
        val rightArmElevation = (shoulderY - rightWristY) / b.bodyHeight
        val leftArmExtension = hypot(leftWristX - pose.leftShoulder.x, leftWristY - pose.leftShoulder.y) / b.shoulderWidth
        val rightArmExtension = hypot(rightWristX - pose.rightShoulder.x, rightWristY - pose.rightShoulder.y) / b.shoulderWidth

        val leftWristVelocity = if (dt > 0f && previousRaw != null) {
            (pose.leftWrist.x - previousRaw.leftWrist.x) / b.shoulderWidth / dt
        } else 0f
        val rightWristVelocity = if (dt > 0f && previousRaw != null) {
            (pose.rightWrist.x - previousRaw.rightWrist.x) / b.shoulderWidth / dt
        } else 0f

        val kneeGap = ((pose.leftKnee.y - pose.leftHip.y) + (pose.rightKnee.y - pose.rightHip.y)) / 2f
        val kneeCompression = max(0f, (b.kneeGap - kneeGap) / b.bodyHeight)
        val hipDelta = centerY
        val jumpImpulse = (
            max(0f, -verticalVelocity) * 0.32f +
                max(0f, -centerY) * 0.55f
            ).coerceIn(0f, 3f)

        val state = MotionState(
            centerX = centerX,
            centerY = centerY,
            lateralVelocity = lateralVelocity,
            verticalVelocity = verticalVelocity,
            torsoLean = torsoLean,
            leftArmElevation = leftArmElevation,
            rightArmElevation = rightArmElevation,
            leftArmExtension = leftArmExtension,
            rightArmExtension = rightArmExtension,
            leftWristVelocity = leftWristVelocity,
            rightWristVelocity = rightWristVelocity,
            leftElbowAngle = elbowAngle(pose.leftShoulder, pose.leftElbow, pose.leftWrist),
            rightElbowAngle = elbowAngle(pose.rightShoulder, pose.rightElbow, pose.rightWrist),
            hipDelta = hipDelta,
            kneeCompression = kneeCompression,
            jumpImpulse = jumpImpulse,
            trackingConfidence = confidence,
            timestampMs = nowMs,
        )

        adaptBaselineIfNeutral(b, state, rawHip, rawShoulder)
        previousPose = pose
        previousState = state
        previousAtMs = nowMs
        return MotionFrame(state, gestureEngine.update(state, nowMs))
    }

    @Synchronized
    fun reset() {
        calibrationSamples.clear()
        baseline = null
        resetRuntime()
        gestureEngine.resetStateTracking()
    }

    private fun resetRuntime() {
        previousPose = null
        previousState = null
        previousAtMs = null
        hipXFilter.reset(); hipYFilter.reset(); shoulderXFilter.reset(); shoulderYFilter.reset()
        leftWristXFilter.reset(); leftWristYFilter.reset(); rightWristXFilter.reset(); rightWristYFilter.reset()
    }

    private fun adaptBaselineIfNeutral(
        b: Baseline,
        state: MotionState,
        rawHip: MotionPoint,
        rawShoulder: MotionPoint,
    ) {
        val neutral = abs(state.torsoLean) < 0.22f &&
            abs(state.centerX) < 0.18f &&
            abs(state.verticalVelocity) < 0.35f &&
            state.kneeCompression < 0.18f &&
            abs(state.leftWristVelocity) < 0.9f &&
            abs(state.rightWristVelocity) < 0.9f
        if (!neutral) return
        val rate = 0.0035f
        b.centerX += (rawHip.x - b.centerX) * rate
        b.hipY += (rawHip.y - b.hipY) * rate
        b.shoulderCenterX += (rawShoulder.x - b.shoulderCenterX) * rate
    }

    private fun poseConfidence(pose: MotionPose): Float {
        val points = listOf(
            pose.leftShoulder, pose.rightShoulder, pose.leftElbow, pose.rightElbow,
            pose.leftWrist, pose.rightWrist, pose.leftHip, pose.rightHip,
            pose.leftKnee, pose.rightKnee, pose.leftAnkle, pose.rightAnkle,
        )
        val average = points.map { it.confidence.coerceIn(0f, 1f) }.average().toFloat()
        val weakestCore = listOf(
            pose.leftShoulder, pose.rightShoulder, pose.leftHip, pose.rightHip,
            pose.leftKnee, pose.rightKnee,
        ).minOf { it.confidence.coerceIn(0f, 1f) }
        return min(average, weakestCore + 0.12f).coerceIn(0f, 1f)
    }

    private fun midpoint(a: MotionPoint, b: MotionPoint) = MotionPoint(
        x = (a.x + b.x) / 2f,
        y = (a.y + b.y) / 2f,
        z = (a.z + b.z) / 2f,
        confidence = min(a.confidence, b.confidence),
    )

    private fun elbowAngle(shoulder: MotionPoint, elbow: MotionPoint, wrist: MotionPoint): Float {
        val ax = shoulder.x - elbow.x
        val ay = shoulder.y - elbow.y
        val bx = wrist.x - elbow.x
        val by = wrist.y - elbow.y
        val aLen = hypot(ax, ay).coerceAtLeast(0.0001f)
        val bLen = hypot(bx, by).coerceAtLeast(0.0001f)
        val cosine = ((ax * bx + ay * by) / (aLen * bLen)).coerceIn(-1f, 1f)
        return (acos(cosine) * 180f / PI.toFloat())
    }
}
