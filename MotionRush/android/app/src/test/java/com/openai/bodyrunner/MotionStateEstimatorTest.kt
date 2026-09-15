package com.openai.bodyrunner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MotionStateEstimatorTest {
    private fun pose(
        centerX: Float = 0.50f,
        hipY: Float = 0.58f,
        wristLeftX: Float = 0.39f,
        wristRightX: Float = 0.61f,
        confidence: Float = 0.95f,
    ) = MotionPose(
        nose = MotionPoint(centerX, 0.16f, confidence = confidence),
        leftShoulder = MotionPoint(centerX - 0.10f, 0.30f, confidence = confidence),
        rightShoulder = MotionPoint(centerX + 0.10f, 0.30f, confidence = confidence),
        leftElbow = MotionPoint(centerX - 0.13f, 0.40f, confidence = confidence),
        rightElbow = MotionPoint(centerX + 0.13f, 0.40f, confidence = confidence),
        leftWrist = MotionPoint(wristLeftX, 0.48f, confidence = confidence),
        rightWrist = MotionPoint(wristRightX, 0.48f, confidence = confidence),
        leftHip = MotionPoint(centerX - 0.07f, hipY, confidence = confidence),
        rightHip = MotionPoint(centerX + 0.07f, hipY, confidence = confidence),
        leftKnee = MotionPoint(centerX - 0.07f, hipY + 0.17f, confidence = confidence),
        rightKnee = MotionPoint(centerX + 0.07f, hipY + 0.17f, confidence = confidence),
        leftAnkle = MotionPoint(centerX - 0.07f, hipY + 0.34f, confidence = confidence),
        rightAnkle = MotionPoint(centerX + 0.07f, hipY + 0.34f, confidence = confidence),
    )

    private fun calibrated(): MotionStateEstimator {
        val estimator = MotionStateEstimator()
        repeat(10) { index ->
            val jitter = if (index % 2 == 0) 0.002f else -0.002f
            assertTrue(estimator.addCalibrationSample(pose(centerX = 0.50f + jitter)))
        }
        assertTrue(estimator.finishCalibration())
        return estimator
    }

    @Test
    fun calibrationRequiresStableMultiFrameSample() {
        val estimator = MotionStateEstimator()
        repeat(9) { assertTrue(estimator.addCalibrationSample(pose())) }
        assertFalse(estimator.finishCalibration())
        assertTrue(estimator.addCalibrationSample(pose()))
        assertTrue(estimator.finishCalibration())
    }

    @Test
    fun calibrationRejectsLargeOutlier() {
        val estimator = MotionStateEstimator()
        repeat(5) { assertTrue(estimator.addCalibrationSample(pose())) }
        assertFalse(estimator.addCalibrationSample(pose(centerX = 0.68f)))
        repeat(5) { assertTrue(estimator.addCalibrationSample(pose())) }
        assertTrue(estimator.finishCalibration())
    }

    @Test
    fun lowConfidencePoseIsSuppressed() {
        val estimator = calibrated()
        assertNull(estimator.push(pose(confidence = 0.30f), 1_000L))
        assertNotNull(estimator.push(pose(confidence = 0.90f), 1_033L))
    }

    @Test
    fun sustainedLeanDoesNotRapidlyBecomeNeutral() {
        val estimator = calibrated()
        var frame: MotionFrame? = null
        repeat(30) { index ->
            frame = estimator.push(pose(centerX = 0.59f), 1_000L + index * 33L)
        }
        assertNotNull(frame)
        assertTrue(abs(frame!!.state.centerX) > 0.25f)
        assertTrue(frame!!.state.torsoLean > 0.20f)
    }

    @Test
    fun upwardBodyImpulseIsVisibleBeforePeakJump() {
        val estimator = calibrated()
        estimator.push(pose(hipY = 0.58f), 1_000L)
        val frame = estimator.push(pose(hipY = 0.53f), 1_033L)
        assertNotNull(frame)
        assertTrue(frame!!.state.verticalVelocity < -0.40f)
        assertTrue(frame!!.state.jumpImpulse > 0.25f)
    }

    @Test
    fun fastWristExtensionProducesVelocityButStaticReachDoesNot() {
        val estimator = calibrated()
        estimator.push(pose(wristRightX = 0.61f), 1_000L)
        val fast = estimator.push(pose(wristRightX = 0.82f), 1_033L)
        assertNotNull(fast)
        assertTrue(fast!!.state.rightWristVelocity > 1.0f)

        var held: MotionFrame? = null
        repeat(5) { index ->
            held = estimator.push(pose(wristRightX = 0.82f), 1_066L + index * 33L)
        }
        assertNotNull(held)
        assertTrue(abs(held!!.state.rightWristVelocity) < 0.45f)
    }
}
