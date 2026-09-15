package com.openai.bodyrunner

data class MotionPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val confidence: Float = 1f,
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

data class MotionState(
    val centerX: Float,
    val centerY: Float,
    val lateralVelocity: Float,
    val verticalVelocity: Float,
    val torsoLean: Float,
    val leftArmElevation: Float,
    val rightArmElevation: Float,
    val leftArmExtension: Float,
    val rightArmExtension: Float,
    val leftWristVelocity: Float,
    val rightWristVelocity: Float,
    val leftElbowAngle: Float,
    val rightElbowAngle: Float,
    val hipDelta: Float,
    val kneeCompression: Float,
    val jumpImpulse: Float,
    val trackingConfidence: Float,
    val timestampMs: Long,
)

data class MotionFrame(
    val state: MotionState,
    val actions: List<String>,
)
