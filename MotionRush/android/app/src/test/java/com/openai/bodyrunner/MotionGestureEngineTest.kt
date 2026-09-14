package com.openai.bodyrunner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionGestureEngineTest {
    private fun pose(
        centerX: Float = 0.50f,
        hipY: Float = 0.62f,
        shoulderY: Float = 0.34f,
        leftWristX: Float = 0.35f,
        rightWristX: Float = 0.65f,
        leftWristY: Float = 0.46f,
        rightWristY: Float = 0.46f,
        kneeY: Float = 0.83f,
    ): MotionPose {
        return MotionPose(
            nose = MotionPoint(centerX, 0.20f),
            leftShoulder = MotionPoint(centerX - 0.12f, shoulderY),
            rightShoulder = MotionPoint(centerX + 0.12f, shoulderY),
            leftWrist = MotionPoint(leftWristX, leftWristY),
            rightWrist = MotionPoint(rightWristX, rightWristY),
            leftHip = MotionPoint(centerX - 0.08f, hipY),
            rightHip = MotionPoint(centerX + 0.08f, hipY),
            leftKnee = MotionPoint(centerX - 0.08f, kneeY),
            rightKnee = MotionPoint(centerX + 0.08f, kneeY),
        )
    }

    @Test
    fun calibrationAcceptsNormalStandingPose() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
    }

    @Test
    fun bodyShiftChangesLane() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        val actions = engine.update(pose(centerX = 0.30f), 1000)
        assertTrue(actions.contains("MOVE_LEFT"))
    }

    @Test
    fun raisedHandNeedsShortHoldAndThenCatchesBonus() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        val raised = pose(leftWristY = 0.20f)
        assertFalse(engine.update(raised, 1000).contains("RAISE_LEFT"))
        assertTrue(engine.update(raised, 1140).contains("RAISE_LEFT"))
    }

    @Test
    fun bothRaisedHandsEmitSingleBothAction() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        val raised = pose(leftWristY = 0.18f, rightWristY = 0.18f)
        engine.update(raised, 2000)
        val actions = engine.update(raised, 2140)
        assertTrue(actions.contains("RAISE_BOTH"))
        assertFalse(actions.contains("RAISE_LEFT"))
        assertFalse(actions.contains("RAISE_RIGHT"))
    }

    @Test
    fun jumpDetectedFromHipRise() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        val actions = engine.update(pose(hipY = 0.47f, kneeY = 0.68f), 3000)
        assertTrue(actions.contains("JUMP"))
    }
}
