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
        leftElbowX: Float = 0.40f,
        rightElbowX: Float = 0.60f,
        leftElbowY: Float = 0.41f,
        rightElbowY: Float = 0.41f,
        kneeY: Float = 0.83f,
        ankleY: Float = 0.96f,
    ): MotionPose {
        return MotionPose(
            nose = MotionPoint(centerX, 0.20f),
            leftShoulder = MotionPoint(centerX - 0.12f, shoulderY),
            rightShoulder = MotionPoint(centerX + 0.12f, shoulderY),
            leftElbow = MotionPoint(leftElbowX, leftElbowY),
            rightElbow = MotionPoint(rightElbowX, rightElbowY),
            leftWrist = MotionPoint(leftWristX, leftWristY),
            rightWrist = MotionPoint(rightWristX, rightWristY),
            leftHip = MotionPoint(centerX - 0.08f, hipY),
            rightHip = MotionPoint(centerX + 0.08f, hipY),
            leftKnee = MotionPoint(centerX - 0.08f, kneeY),
            rightKnee = MotionPoint(centerX + 0.08f, kneeY),
            leftAnkle = MotionPoint(centerX - 0.08f, ankleY),
            rightAnkle = MotionPoint(centerX + 0.08f, ankleY),
        )
    }

    @Test
    fun calibrationAcceptsNormalStandingPose() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
    }

    @Test
    fun sustainedBodyShiftChangesLane() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        assertFalse(engine.update(pose(centerX = 0.30f), 1000).contains("MOVE_LEFT"))
        val actions = engine.update(pose(centerX = 0.30f), 1060)
        assertTrue(actions.contains("MOVE_LEFT"))
    }

    @Test
    fun oneFrameLaneSpikeIsIgnored() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        assertFalse(engine.update(pose(centerX = 0.30f), 1000).contains("MOVE_LEFT"))
        assertFalse(engine.update(pose(centerX = 0.50f), 1035).contains("MOVE_LEFT"))
    }

    @Test
    fun heldLaneShiftFiresOnlyOnceUntilRecentred() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        engine.update(pose(centerX = 0.30f), 1000)
        assertTrue(engine.update(pose(centerX = 0.30f), 1060).contains("MOVE_LEFT"))
        assertFalse(engine.update(pose(centerX = 0.30f), 1500).contains("MOVE_LEFT"))
        engine.update(pose(centerX = 0.50f), 1560)
        engine.update(pose(centerX = 0.30f), 1640)
        assertTrue(engine.update(pose(centerX = 0.30f), 1700).contains("MOVE_LEFT"))
    }

    @Test
    fun raisedHandNeedsOnlyShortHoldAndThenCatchesBonus() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        val raised = pose(leftWristY = 0.20f, leftElbowY = 0.28f)
        assertFalse(engine.update(raised, 1000).contains("RAISE_LEFT"))
        assertTrue(engine.update(raised, 1090).contains("RAISE_LEFT"))
    }

    @Test
    fun bothRaisedHandsEmitSingleBothAction() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        val raised = pose(
            leftWristY = 0.18f,
            rightWristY = 0.18f,
            leftElbowY = 0.27f,
            rightElbowY = 0.27f,
        )
        engine.update(raised, 2000)
        val actions = engine.update(raised, 2090)
        assertTrue(actions.contains("RAISE_BOTH"))
        assertFalse(actions.contains("RAISE_LEFT"))
        assertFalse(actions.contains("RAISE_RIGHT"))
    }

    @Test
    fun staticExtendedArmDoesNotCountAsPunch() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        val extended = pose(leftWristX = 0.08f, leftElbowX = 0.24f)
        assertFalse(engine.update(extended, 1000).contains("PUNCH_LEFT"))
        assertFalse(engine.update(extended, 1080).contains("PUNCH_LEFT"))
    }

    @Test
    fun fastArmExtensionCountsAsPunch() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        engine.update(pose(leftWristX = 0.34f, leftElbowX = 0.39f), 1000)
        val actions = engine.update(pose(leftWristX = 0.08f, leftElbowX = 0.24f), 1070)
        assertTrue(actions.contains("PUNCH_LEFT"))
    }

    @Test
    fun jumpDetectedFromHipRise() {
        val engine = MotionGestureEngine()
        assertTrue(engine.calibrate(pose()))
        engine.update(pose(hipY = 0.49f, kneeY = 0.70f, ankleY = 0.83f), 3000)
        val actions = engine.update(pose(hipY = 0.47f, kneeY = 0.68f, ankleY = 0.80f), 3045)
        assertTrue(actions.contains("JUMP"))
    }
}
