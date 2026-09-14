package com.openai.bodyrunner

import kotlin.math.abs
import kotlin.math.max

data class MotionPoint(
    val x: Float,
    val y: Float,
)

data class MotionPose(
    val nose: MotionPoint,
    val leftShoulder: MotionPoint,
    val rightShoulder: MotionPoint,
    val leftElbow: MotionPoint,
    val rightElbow: MotionPoint,
    val leftWrist: MotionPoint,
    val rightWrist: MotionPoint,
    val leftHip: MotionPoint,
    val rightHip: MotionPoint,
    val leftKnee: MotionPoint,
    val rightKnee: MotionPoint,
    val leftAnkle: MotionPoint,
    val rightAnkle: MotionPoint,
)

internal class MotionGestureEngine {
    private data class Baseline(
        val centerX: Float,
        val hipY: Float,
        val shoulderWidth: Float,
        val bodyHeight: Float,
    )

    private var baseline: Baseline? = null
    private var smoothedPose: MotionPose? = null
    private var previousRawPose: MotionPose? = null
    private var previousPoseAtMs: Long? = null

    private val lastActionAt = mutableMapOf<String, Long>()

    private var laneCandidate: String? = null
    private var laneCandidateSince: Long? = null
    private var laneArmed = true

    private var jumpSince: Long? = null
    private var jumpLatched = false
    private var crouchSince: Long? = null
    private var crouchLatched = false

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
        smoothedPose = pose
        previousRawPose = null
        previousPoseAtMs = null
        lastActionAt.clear()
        clearTemporalState()
        return true
    }

    @Synchronized
    fun update(pose: MotionPose, nowMs: Long): List<String> {
        val b = baseline ?: return emptyList()
        val actions = mutableListOf<String>()
        val filtered = smooth(smoothedPose, pose, 0.82f).also { smoothedPose = it }
        val hip = midpoint(filtered.leftHip, filtered.rightHip)
        val rawHip = midpoint(pose.leftHip, pose.rightHip)

        updateLane(filtered, rawHip, hip, b, nowMs, actions)
        updateJump(filtered, hip, b, nowMs, actions)
        updateCrouch(filtered, hip, b, nowMs, actions)
        updatePunches(filtered, pose, b, nowMs, actions)
        updateRaisedHands(filtered, b, nowMs, actions)

        previousRawPose = pose
        previousPoseAtMs = nowMs
        return actions
    }

    private fun updateLane(
        pose: MotionPose,
        rawHip: MotionPoint,
        hip: MotionPoint,
        b: Baseline,
        nowMs: Long,
        actions: MutableList<String>,
    ) {
        val laneThreshold = max(0.07f, b.shoulderWidth * 0.52f)
        val releaseThreshold = laneThreshold * 0.48f
        val rawDx = rawHip.x - b.centerX
        val dx = hip.x - b.centerX

        if (abs(rawDx) < releaseThreshold) {
            laneArmed = true
            laneCandidate = null
            laneCandidateSince = null
        }

        val side = when {
            dx < -laneThreshold -> "LEFT"
            dx > laneThreshold -> "RIGHT"
            else -> null
        }

        if (side == null || !laneArmed) {
            if (side == null) {
                laneCandidate = null
                laneCandidateSince = null
            }
            return
        }

        if (laneCandidate != side) {
            laneCandidate = side
            laneCandidateSince = nowMs
            return
        }

        val since = laneCandidateSince ?: nowMs
        if (nowMs - since >= 50L) {
            actions += if (side == "LEFT") "MOVE_LEFT" else "MOVE_RIGHT"
            laneArmed = false
            laneCandidate = null
            laneCandidateSince = null
        }
    }

    private fun updateJump(
        pose: MotionPose,
        hip: MotionPoint,
        b: Baseline,
        nowMs: Long,
        actions: MutableList<String>,
    ) {
        val threshold = max(0.075f, b.bodyHeight * 0.33f)
        val rise = b.hipY - hip.y
        val ankleY = (pose.leftAnkle.y + pose.rightAnkle.y) / 2f
        val kneeY = (pose.leftKnee.y + pose.rightKnee.y) / 2f
        val legsLifted = ankleY < b.hipY + b.bodyHeight * 0.84f || kneeY < b.hipY + b.bodyHeight * 0.43f
        val active = rise > threshold && legsLifted

        if (!active) {
            jumpSince = null
            if (rise < threshold * 0.42f) jumpLatched = false
            return
        }
        if (jumpLatched) return
        if (jumpSince == null) jumpSince = nowMs
        if (nowMs - (jumpSince ?: nowMs) >= 35L && ready("JUMP", nowMs, 220L)) {
            actions += "JUMP"
            mark("JUMP", nowMs)
            jumpLatched = true
            jumpSince = null
        }
    }

    private fun updateCrouch(
        pose: MotionPose,
        hip: MotionPoint,
        b: Baseline,
        nowMs: Long,
        actions: MutableList<String>,
    ) {
        val dropThreshold = max(0.07f, b.bodyHeight * 0.30f)
        val drop = hip.y - b.hipY
        val kneeGap = ((pose.leftKnee.y - pose.leftHip.y) + (pose.rightKnee.y - pose.rightHip.y)) / 2f
        val active = drop > dropThreshold && kneeGap < b.bodyHeight * 0.90f

        if (!active) {
            crouchSince = null
            if (drop < dropThreshold * 0.45f) crouchLatched = false
            return
        }
        if (crouchLatched) return
        if (crouchSince == null) crouchSince = nowMs
        if (nowMs - (crouchSince ?: nowMs) >= 45L && ready("CROUCH", nowMs, 420L)) {
            actions += "CROUCH"
            mark("CROUCH", nowMs)
            crouchLatched = true
            crouchSince = null
        }
    }

    private fun updatePunches(
        filtered: MotionPose,
        raw: MotionPose,
        b: Baseline,
        nowMs: Long,
        actions: MutableList<String>,
    ) {
        val previous = previousRawPose ?: return
        val previousAt = previousPoseAtMs ?: return
        val dtSeconds = (nowMs - previousAt).coerceAtLeast(1L) / 1000f
        if (dtSeconds > 0.22f) return

        val raiseThreshold = max(0.05f, b.bodyHeight * 0.20f)
        val leftRaised = filtered.leftWrist.y < filtered.leftShoulder.y - raiseThreshold
        val rightRaised = filtered.rightWrist.y < filtered.rightShoulder.y - raiseThreshold
        val reach = max(0.16f, b.shoulderWidth * 0.95f)
        val elbowReach = max(0.085f, b.shoulderWidth * 0.40f)
        val verticalWindow = max(0.12f, b.bodyHeight * 0.62f)

        fun speed(current: MotionPoint, old: MotionPoint) = abs(current.x - old.x) / dtSeconds

        val leftPunch = !leftRaised &&
            abs(filtered.leftWrist.x - filtered.leftShoulder.x) > reach &&
            abs(filtered.leftElbow.x - filtered.leftShoulder.x) > elbowReach &&
            abs(filtered.leftWrist.y - filtered.leftShoulder.y) < verticalWindow &&
            speed(raw.leftWrist, previous.leftWrist) > 1.65f

        val rightPunch = !rightRaised &&
            abs(filtered.rightWrist.x - filtered.rightShoulder.x) > reach &&
            abs(filtered.rightElbow.x - filtered.rightShoulder.x) > elbowReach &&
            abs(filtered.rightWrist.y - filtered.rightShoulder.y) < verticalWindow &&
            speed(raw.rightWrist, previous.rightWrist) > 1.65f

        if (leftPunch && ready("PUNCH_LEFT", nowMs, 220L)) {
            actions += "PUNCH_LEFT"
            mark("PUNCH_LEFT", nowMs)
        }
        if (rightPunch && ready("PUNCH_RIGHT", nowMs, 220L)) {
            actions += "PUNCH_RIGHT"
            mark("PUNCH_RIGHT", nowMs)
        }
    }

    private fun updateRaisedHands(
        pose: MotionPose,
        b: Baseline,
        nowMs: Long,
        actions: MutableList<String>,
    ) {
        val threshold = max(0.05f, b.bodyHeight * 0.20f)
        val leftRaised = pose.leftWrist.y < pose.leftShoulder.y - threshold &&
            pose.leftElbow.y < pose.leftShoulder.y + b.bodyHeight * 0.02f
        val rightRaised = pose.rightWrist.y < pose.rightShoulder.y - threshold &&
            pose.rightElbow.y < pose.rightShoulder.y + b.bodyHeight * 0.02f

        val bothHeld = held("both", leftRaised && rightRaised, nowMs, 75L)
        val leftHeld = held("left", leftRaised && !rightRaised, nowMs, 75L)
        val rightHeld = held("right", rightRaised && !leftRaised, nowMs, 75L)

        if (bothHeld && ready("RAISE_BOTH", nowMs, 300L)) {
            actions += "RAISE_BOTH"
            mark("RAISE_BOTH", nowMs)
        } else if (leftHeld && ready("RAISE_LEFT", nowMs, 300L)) {
            actions += "RAISE_LEFT"
            mark("RAISE_LEFT", nowMs)
        } else if (rightHeld && ready("RAISE_RIGHT", nowMs, 300L)) {
            actions += "RAISE_RIGHT"
            mark("RAISE_RIGHT", nowMs)
        }
    }

    @Synchronized
    fun reset() {
        baseline = null
        smoothedPose = null
        previousRawPose = null
        previousPoseAtMs = null
        lastActionAt.clear()
        clearTemporalState()
    }

    private fun clearTemporalState() {
        laneCandidate = null
        laneCandidateSince = null
        laneArmed = true
        jumpSince = null
        jumpLatched = false
        crouchSince = null
        crouchLatched = false
        leftRaiseSince = null
        rightRaiseSince = null
        bothRaiseSince = null
    }

    private fun midpoint(a: MotionPoint, b: MotionPoint) = MotionPoint(
        x = (a.x + b.x) / 2f,
        y = (a.y + b.y) / 2f,
    )

    private fun smooth(previous: MotionPose?, current: MotionPose, alpha: Float): MotionPose {
        if (previous == null) return current
        fun p(old: MotionPoint, fresh: MotionPoint) = MotionPoint(
            x = old.x + (fresh.x - old.x) * alpha,
            y = old.y + (fresh.y - old.y) * alpha,
        )
        return MotionPose(
            nose = p(previous.nose, current.nose),
            leftShoulder = p(previous.leftShoulder, current.leftShoulder),
            rightShoulder = p(previous.rightShoulder, current.rightShoulder),
            leftElbow = p(previous.leftElbow, current.leftElbow),
            rightElbow = p(previous.rightElbow, current.rightElbow),
            leftWrist = p(previous.leftWrist, current.leftWrist),
            rightWrist = p(previous.rightWrist, current.rightWrist),
            leftHip = p(previous.leftHip, current.leftHip),
            rightHip = p(previous.rightHip, current.rightHip),
            leftKnee = p(previous.leftKnee, current.leftKnee),
            rightKnee = p(previous.rightKnee, current.rightKnee),
            leftAnkle = p(previous.leftAnkle, current.leftAnkle),
            rightAnkle = p(previous.rightAnkle, current.rightAnkle),
        )
    }

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
