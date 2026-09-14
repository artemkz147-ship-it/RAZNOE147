package com.openai.bodyrunner

import kotlin.math.abs
import kotlin.math.max

internal data class MotionPoint(
    val x: Float,
    val y: Float,
)

internal data class MotionPose(
    val nose: MotionPoint,
    val leftShoulder: MotionPoint,
    val rightShoulder: MotionPoint,
    val leftWrist: MotionPoint,
    val rightWrist: MotionPoint,
    val leftHip: MotionPoint,
    val rightHip: MotionPoint,
    val leftKnee: MotionPoint,
    val rightKnee: MotionPoint,
)

internal class MotionGestureEngine {
    private data class Baseline(
        val centerX: Float,
        val hipY: Float,
        val shoulderWidth: Float,
        val bodyHeight: Float,
    )

    private var baseline: Baseline? = null
    private val lastActionAt = mutableMapOf<String, Long>()
    private var leftRaiseSince: Long? = null
    private var rightRaiseSince: Long? = null
    private var bothRaiseSince: Long? = null

    @Synchronized
    fun calibrate(pose: MotionPose): Boolean {
        val shoulder = midpoint(pose.leftShoulder, pose.rightShoulder)
        val hip = midpoint(pose.leftHip, pose.rightHip)
        val shoulderWidth = abs(pose.rightShoulder.x - pose.leftShoulder.x)
        val bodyHeight = abs(hip.y - shoulder.y)
        if (shoulderWidth < 0.05f || bodyHeight < 0.08f) return false

        baseline = Baseline(
            centerX = hip.x,
            hipY = hip.y,
            shoulderWidth = shoulderWidth,
            bodyHeight = bodyHeight,
        )
        lastActionAt.clear()
        leftRaiseSince = null
        rightRaiseSince = null
        bothRaiseSince = null
        return true
    }

    @Synchronized
    fun update(pose: MotionPose, nowMs: Long): List<String> {
        val b = baseline ?: return emptyList()
        val actions = mutableListOf<String>()
        val hip = midpoint(pose.leftHip, pose.rightHip)

        val laneThreshold = max(0.075f, b.shoulderWidth * 0.62f)
        val dx = hip.x - b.centerX
        if (dx < -laneThreshold && ready("LANE", nowMs, 350)) {
            actions += "MOVE_LEFT"
            mark("LANE", nowMs)
        } else if (dx > laneThreshold && ready("LANE", nowMs, 350)) {
            actions += "MOVE_RIGHT"
            mark("LANE", nowMs)
        }

        val jumpThreshold = max(0.09f, b.bodyHeight * 0.42f)
        if (b.hipY - hip.y > jumpThreshold && ready("JUMP", nowMs, 220)) {
            actions += "JUMP"
            mark("JUMP", nowMs)
        }

        val crouchDrop = hip.y - b.hipY
        val kneeGap = ((pose.leftKnee.y - pose.leftHip.y) + (pose.rightKnee.y - pose.rightHip.y)) / 2f
        if (
            crouchDrop > max(0.085f, b.bodyHeight * 0.36f) &&
            kneeGap < b.bodyHeight * 0.78f &&
            ready("CROUCH", nowMs, 500)
        ) {
            actions += "CROUCH"
            mark("CROUCH", nowMs)
        }

        val punchReach = max(0.18f, b.shoulderWidth * 1.15f)
        val punchWindow = max(0.11f, b.bodyHeight * 0.58f)
        val leftRaised = pose.leftWrist.y < pose.leftShoulder.y - max(0.07f, b.bodyHeight * 0.28f)
        val rightRaised = pose.rightWrist.y < pose.rightShoulder.y - max(0.07f, b.bodyHeight * 0.28f)

        val leftPunch = !leftRaised &&
            abs(pose.leftWrist.x - pose.leftShoulder.x) > punchReach &&
            abs(pose.leftWrist.y - pose.leftShoulder.y) < punchWindow
        val rightPunch = !rightRaised &&
            abs(pose.rightWrist.x - pose.rightShoulder.x) > punchReach &&
            abs(pose.rightWrist.y - pose.rightShoulder.y) < punchWindow

        if (leftPunch && ready("PUNCH_LEFT", nowMs, 300)) {
            actions += "PUNCH_LEFT"
            mark("PUNCH_LEFT", nowMs)
        }
        if (rightPunch && ready("PUNCH_RIGHT", nowMs, 300)) {
            actions += "PUNCH_RIGHT"
            mark("PUNCH_RIGHT", nowMs)
        }

        val bothHeld = held("both", leftRaised && rightRaised, nowMs, 130)
        val leftHeld = held("left", leftRaised && !rightRaised, nowMs, 130)
        val rightHeld = held("right", rightRaised && !leftRaised, nowMs, 130)

        if (bothHeld && ready("RAISE_BOTH", nowMs, 420)) {
            actions += "RAISE_BOTH"
            mark("RAISE_BOTH", nowMs)
        } else if (leftHeld && ready("RAISE_LEFT", nowMs, 420)) {
            actions += "RAISE_LEFT"
            mark("RAISE_LEFT", nowMs)
        } else if (rightHeld && ready("RAISE_RIGHT", nowMs, 420)) {
            actions += "RAISE_RIGHT"
            mark("RAISE_RIGHT", nowMs)
        }

        return actions
    }

    @Synchronized
    fun reset() {
        baseline = null
        lastActionAt.clear()
        leftRaiseSince = null
        rightRaiseSince = null
        bothRaiseSince = null
    }

    private fun midpoint(a: MotionPoint, b: MotionPoint) = MotionPoint(
        x = (a.x + b.x) / 2f,
        y = (a.y + b.y) / 2f,
    )

    private fun ready(action: String, nowMs: Long, cooldownMs: Long): Boolean {
        val previous = lastActionAt[action] ?: return true
        return nowMs - previous >= cooldownMs
    }

    private fun mark(action: String, nowMs: Long) {
        lastActionAt[action] = nowMs
    }

    private fun held(name: String, active: Boolean, nowMs: Long, holdMs: Long): Boolean {
        var since = when (name) {
            "left" -> leftRaiseSince
            "right" -> rightRaiseSince
            else -> bothRaiseSince
        }
        if (!active) {
            since = null
        } else if (since == null) {
            since = nowMs
        }
        when (name) {
            "left" -> leftRaiseSince = since
            "right" -> rightRaiseSince = since
            else -> bothRaiseSince = since
        }
        return active && since != null && nowMs - since >= holdMs
    }
}
