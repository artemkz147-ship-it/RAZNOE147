package com.openai.bodyrunner

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class MotionGestureEngine {
    private data class Baseline(val centerX: Float, val hipY: Float, val shoulderWidth: Float, val bodyHeight: Float)
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

    private val stateLastActionAt = mutableMapOf<String, Long>()
    private var stateLaneCandidate: String? = null
    private var stateLaneCandidateSince: Long? = null
    private var stateLaneArmed = true
    private var stateJumpArmed = true
    private var stateCrouchArmed = true
    private var stateLeftRaiseSince: Long? = null
    private var stateRightRaiseSince: Long? = null
    private var stateBothRaiseSince: Long? = null

    @Synchronized
    fun calibrate(pose: MotionPose): Boolean {
        val shoulder = midpoint(pose.leftShoulder, pose.rightShoulder)
        val hip = midpoint(pose.leftHip, pose.rightHip)
        val shoulderWidth = abs(pose.rightShoulder.x - pose.leftShoulder.x)
        val bodyHeight = abs(hip.y - shoulder.y)
        if (shoulderWidth < 0.05f || bodyHeight < 0.08f) return false
        baseline = Baseline(hip.x, hip.y, shoulderWidth, bodyHeight)
        smoothedPose = pose
        previousRawPose = null
        previousPoseAtMs = null
        lastActionAt.clear()
        clearLegacyTemporalState()
        return true
    }

    @Synchronized
    fun update(pose: MotionPose, nowMs: Long): List<String> {
        val b = baseline ?: return emptyList()
        val actions = mutableListOf<String>()
        val filtered = smooth(smoothedPose, pose, 0.82f).also { smoothedPose = it }
        val hip = midpoint(filtered.leftHip, filtered.rightHip)
        val rawHip = midpoint(pose.leftHip, pose.rightHip)
        updateLegacyLane(rawHip, hip, b, nowMs, actions)
        updateLegacyJump(filtered, hip, b, nowMs, actions)
        updateLegacyCrouch(filtered, hip, b, nowMs, actions)
        updateLegacyPunches(filtered, pose, b, nowMs, actions)
        updateLegacyRaisedHands(filtered, b, nowMs, actions)
        previousRawPose = pose
        previousPoseAtMs = nowMs
        return actions
    }

    @Synchronized
    fun update(state: MotionState, nowMs: Long): List<String> {
        if (state.trackingConfidence < 0.45f) return emptyList()
        val actions = mutableListOf<String>()
        if (abs(state.centerX) < 0.14f && abs(state.lateralVelocity) < 0.8f) {
            stateLaneArmed = true; stateLaneCandidate = null; stateLaneCandidateSince = null
        }
        val side = when {
            state.centerX < -0.32f || (state.centerX < -0.18f && state.lateralVelocity < -1.15f) -> "LEFT"
            state.centerX > 0.32f || (state.centerX > 0.18f && state.lateralVelocity > 1.15f) -> "RIGHT"
            else -> null
        }
        if (side == null) { stateLaneCandidate = null; stateLaneCandidateSince = null }
        else if (stateLaneArmed) {
            if (stateLaneCandidate != side) { stateLaneCandidate = side; stateLaneCandidateSince = nowMs }
            else if (nowMs - (stateLaneCandidateSince ?: nowMs) >= 40L) {
                actions += if (side == "LEFT") "MOVE_LEFT" else "MOVE_RIGHT"
                stateLaneArmed = false; stateLaneCandidate = null; stateLaneCandidateSince = null
            }
        }
        if (state.jumpImpulse < 0.10f && state.hipDelta > -0.08f) stateJumpArmed = true
        if (stateJumpArmed && state.jumpImpulse > 0.25f && stateReady("JUMP", nowMs, 210L)) {
            actions += "JUMP"; stateMark("JUMP", nowMs); stateJumpArmed = false
        }
        if (state.hipDelta < 0.10f && state.kneeCompression < 0.08f) stateCrouchArmed = true
        if (stateCrouchArmed && state.hipDelta > 0.22f && state.kneeCompression > 0.10f && stateReady("CROUCH", nowMs, 380L)) {
            actions += "CROUCH"; stateMark("CROUCH", nowMs); stateCrouchArmed = false
        }
        val leftPunch = state.leftArmElevation < 0.45f && abs(state.leftWristVelocity) > 1.55f && state.leftArmExtension > 0.82f && state.leftElbowAngle > 125f
        val rightPunch = state.rightArmElevation < 0.45f && abs(state.rightWristVelocity) > 1.55f && state.rightArmExtension > 0.82f && state.rightElbowAngle > 125f
        if (leftPunch && stateReady("PUNCH_LEFT", nowMs, 210L)) { actions += "PUNCH_LEFT"; stateMark("PUNCH_LEFT", nowMs) }
        if (rightPunch && stateReady("PUNCH_RIGHT", nowMs, 210L)) { actions += "PUNCH_RIGHT"; stateMark("PUNCH_RIGHT", nowMs) }
        val leftRaised = state.leftArmElevation > 0.30f
        val rightRaised = state.rightArmElevation > 0.30f
        val bothHeld = stateHeld("both", leftRaised && rightRaised, nowMs, 65L)
        val leftHeld = stateHeld("left", leftRaised && !rightRaised, nowMs, 65L)
        val rightHeld = stateHeld("right", rightRaised && !leftRaised, nowMs, 65L)
        if (bothHeld && stateReady("RAISE_BOTH", nowMs, 260L)) { actions += "RAISE_BOTH"; stateMark("RAISE_BOTH", nowMs) }
        else if (leftHeld && stateReady("RAISE_LEFT", nowMs, 260L)) { actions += "RAISE_LEFT"; stateMark("RAISE_LEFT", nowMs) }
        else if (rightHeld && stateReady("RAISE_RIGHT", nowMs, 260L)) { actions += "RAISE_RIGHT"; stateMark("RAISE_RIGHT", nowMs) }
        return actions
    }

    private fun updateLegacyLane(rawHip: MotionPoint, hip: MotionPoint, b: Baseline, nowMs: Long, actions: MutableList<String>) {
        val threshold = max(0.07f, b.shoulderWidth * 0.52f)
        val rawDx = rawHip.x - b.centerX
        val dx = hip.x - b.centerX
        if (abs(rawDx) < threshold * 0.48f) { laneArmed = true; laneCandidate = null; laneCandidateSince = null }
        val side = when { dx < -threshold -> "LEFT"; dx > threshold -> "RIGHT"; else -> null }
        if (side == null || !laneArmed) { if (side == null) { laneCandidate = null; laneCandidateSince = null }; return }
        if (laneCandidate != side) { laneCandidate = side; laneCandidateSince = nowMs; return }
        if (nowMs - (laneCandidateSince ?: nowMs) >= 50L) {
            actions += if (side == "LEFT") "MOVE_LEFT" else "MOVE_RIGHT"
            laneArmed = false; laneCandidate = null; laneCandidateSince = null
        }
    }

    private fun updateLegacyJump(pose: MotionPose, hip: MotionPoint, b: Baseline, nowMs: Long, actions: MutableList<String>) {
        val threshold = max(0.075f, b.bodyHeight * 0.33f)
        val rise = b.hipY - hip.y
        val ankleY = (pose.leftAnkle.y + pose.rightAnkle.y) / 2f
        val kneeY = (pose.leftKnee.y + pose.rightKnee.y) / 2f
        val active = rise > threshold && (ankleY < b.hipY + b.bodyHeight * 0.84f || kneeY < b.hipY + b.bodyHeight * 0.43f)
        if (!active) { jumpSince = null; if (rise < threshold * 0.42f) jumpLatched = false; return }
        if (jumpLatched) return
        if (jumpSince == null) jumpSince = nowMs
        if (nowMs - (jumpSince ?: nowMs) >= 35L && ready("JUMP", nowMs, 220L)) { actions += "JUMP"; mark("JUMP", nowMs); jumpLatched = true; jumpSince = null }
    }

    private fun updateLegacyCrouch(pose: MotionPose, hip: MotionPoint, b: Baseline, nowMs: Long, actions: MutableList<String>) {
        val threshold = max(0.07f, b.bodyHeight * 0.30f)
        val drop = hip.y - b.hipY
        val kneeGap = ((pose.leftKnee.y - pose.leftHip.y) + (pose.rightKnee.y - pose.rightHip.y)) / 2f
        val active = drop > threshold && kneeGap < b.bodyHeight * 0.90f
        if (!active) { crouchSince = null; if (drop < threshold * 0.45f) crouchLatched = false; return }
        if (crouchLatched) return
        if (crouchSince == null) crouchSince = nowMs
        if (nowMs - (crouchSince ?: nowMs) >= 45L && ready("CROUCH", nowMs, 420L)) { actions += "CROUCH"; mark("CROUCH", nowMs); crouchLatched = true; crouchSince = null }
    }

    private fun updateLegacyPunches(filtered: MotionPose, raw: MotionPose, b: Baseline, nowMs: Long, actions: MutableList<String>) {
        val previous = previousRawPose ?: return
        val previousAt = previousPoseAtMs ?: return
        val dt = (nowMs - previousAt).coerceAtLeast(1L) / 1000f
        if (dt > 0.22f) return
        val raisedThreshold = max(0.05f, b.bodyHeight * 0.20f)
        val leftRaised = filtered.leftWrist.y < filtered.leftShoulder.y - raisedThreshold
        val rightRaised = filtered.rightWrist.y < filtered.rightShoulder.y - raisedThreshold
        val reach = max(0.16f, b.shoulderWidth * 0.95f)
        val elbowReach = max(0.085f, b.shoulderWidth * 0.40f)
        val verticalWindow = max(0.12f, b.bodyHeight * 0.62f)
        fun speed(a: MotionPoint, old: MotionPoint) = abs(a.x - old.x) / dt
        val leftPunch = !leftRaised && abs(filtered.leftWrist.x - filtered.leftShoulder.x) > reach && abs(filtered.leftElbow.x - filtered.leftShoulder.x) > elbowReach && abs(filtered.leftWrist.y - filtered.leftShoulder.y) < verticalWindow && speed(raw.leftWrist, previous.leftWrist) > 1.65f
        val rightPunch = !rightRaised && abs(filtered.rightWrist.x - filtered.rightShoulder.x) > reach && abs(filtered.rightElbow.x - filtered.rightShoulder.x) > elbowReach && abs(filtered.rightWrist.y - filtered.rightShoulder.y) < verticalWindow && speed(raw.rightWrist, previous.rightWrist) > 1.65f
        if (leftPunch && ready("PUNCH_LEFT", nowMs, 220L)) { actions += "PUNCH_LEFT"; mark("PUNCH_LEFT", nowMs) }
        if (rightPunch && ready("PUNCH_RIGHT", nowMs, 220L)) { actions += "PUNCH_RIGHT"; mark("PUNCH_RIGHT", nowMs) }
    }

    private fun updateLegacyRaisedHands(pose: MotionPose, b: Baseline, nowMs: Long, actions: MutableList<String>) {
        val threshold = max(0.05f, b.bodyHeight * 0.20f)
        val leftRaised = pose.leftWrist.y < pose.leftShoulder.y - threshold && pose.leftElbow.y < pose.leftShoulder.y + b.bodyHeight * 0.02f
        val rightRaised = pose.rightWrist.y < pose.rightShoulder.y - threshold && pose.rightElbow.y < pose.rightShoulder.y + b.bodyHeight * 0.02f
        val bothHeld = held("both", leftRaised && rightRaised, nowMs, 75L)
        val leftHeld = held("left", leftRaised && !rightRaised, nowMs, 75L)
        val rightHeld = held("right", rightRaised && !leftRaised, nowMs, 75L)
        if (bothHeld && ready("RAISE_BOTH", nowMs, 300L)) { actions += "RAISE_BOTH"; mark("RAISE_BOTH", nowMs) }
        else if (leftHeld && ready("RAISE_LEFT", nowMs, 300L)) { actions += "RAISE_LEFT"; mark("RAISE_LEFT", nowMs) }
        else if (rightHeld && ready("RAISE_RIGHT", nowMs, 300L)) { actions += "RAISE_RIGHT"; mark("RAISE_RIGHT", nowMs) }
    }

    @Synchronized
    fun reset() {
        baseline = null; smoothedPose = null; previousRawPose = null; previousPoseAtMs = null; lastActionAt.clear()
        clearLegacyTemporalState(); resetStateTracking()
    }

    @Synchronized
    fun resetStateTracking() {
        stateLastActionAt.clear(); stateLaneCandidate = null; stateLaneCandidateSince = null; stateLaneArmed = true
        stateJumpArmed = true; stateCrouchArmed = true; stateLeftRaiseSince = null; stateRightRaiseSince = null; stateBothRaiseSince = null
    }

    private fun clearLegacyTemporalState() {
        laneCandidate = null; laneCandidateSince = null; laneArmed = true; jumpSince = null; jumpLatched = false
        crouchSince = null; crouchLatched = false; leftRaiseSince = null; rightRaiseSince = null; bothRaiseSince = null
    }

    private fun midpoint(a: MotionPoint, b: MotionPoint) = MotionPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.z + b.z) / 2f, min(a.confidence, b.confidence))

    private fun smooth(previous: MotionPose?, current: MotionPose, alpha: Float): MotionPose {
        if (previous == null) return current
        fun p(old: MotionPoint, fresh: MotionPoint) = MotionPoint(old.x + (fresh.x - old.x) * alpha, old.y + (fresh.y - old.y) * alpha, old.z + (fresh.z - old.z) * alpha, min(old.confidence, fresh.confidence))
        return MotionPose(
            p(previous.nose,current.nose), p(previous.leftShoulder,current.leftShoulder), p(previous.rightShoulder,current.rightShoulder),
            p(previous.leftElbow,current.leftElbow), p(previous.rightElbow,current.rightElbow), p(previous.leftWrist,current.leftWrist), p(previous.rightWrist,current.rightWrist),
            p(previous.leftHip,current.leftHip), p(previous.rightHip,current.rightHip), p(previous.leftKnee,current.leftKnee), p(previous.rightKnee,current.rightKnee),
            p(previous.leftAnkle,current.leftAnkle), p(previous.rightAnkle,current.rightAnkle)
        )
    }

    private fun ready(action: String, nowMs: Long, cooldown: Long) = lastActionAt[action]?.let { nowMs - it >= cooldown } ?: true
    private fun mark(action: String, nowMs: Long) { lastActionAt[action] = nowMs }
    private fun stateReady(action: String, nowMs: Long, cooldown: Long) = stateLastActionAt[action]?.let { nowMs - it >= cooldown } ?: true
    private fun stateMark(action: String, nowMs: Long) { stateLastActionAt[action] = nowMs }

    private fun held(name: String, active: Boolean, nowMs: Long, hold: Long): Boolean {
        var since = when (name) { "left" -> leftRaiseSince; "right" -> rightRaiseSince; else -> bothRaiseSince }
        if (!active) since = null else if (since == null) since = nowMs
        when (name) { "left" -> leftRaiseSince = since; "right" -> rightRaiseSince = since; else -> bothRaiseSince = since }
        return active && since != null && nowMs - since >= hold
    }
    private fun stateHeld(name: String, active: Boolean, nowMs: Long, hold: Long): Boolean {
        var since = when (name) { "left" -> stateLeftRaiseSince; "right" -> stateRightRaiseSince; else -> stateBothRaiseSince }
        if (!active) since = null else if (since == null) since = nowMs
        when (name) { "left" -> stateLeftRaiseSince = since; "right" -> stateRightRaiseSince = since; else -> stateBothRaiseSince = since }
        return active && since != null && nowMs - since >= hold
    }
}
