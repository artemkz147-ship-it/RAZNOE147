package com.openai.bodyrunner

import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPacketEncoderTest {
    @Test
    fun encodesCompactContinuousMotionPayload() {
        val state = MotionState(
            centerX = 0.25f,
            centerY = -0.10f,
            lateralVelocity = 1.5f,
            verticalVelocity = -0.8f,
            torsoLean = 0.22f,
            leftArmElevation = 0.7f,
            rightArmElevation = 0.6f,
            leftArmExtension = 1.1f,
            rightArmExtension = 1.0f,
            leftWristVelocity = 2.2f,
            rightWristVelocity = -1.8f,
            leftElbowAngle = 162f,
            rightElbowAngle = 158f,
            hipDelta = -0.12f,
            kneeCompression = 0.08f,
            jumpImpulse = 0.42f,
            trackingConfidence = 0.91f,
            timestampMs = 1234L,
        )

        val json = MotionPacketEncoder.encode(state, "GPU FULL", 29.5f, 37L)

        assertTrue(json.startsWith("{\"t\":1234"))
        assertTrue(json.contains("\"cx\":0.25"))
        assertTrue(json.contains("\"vx\":1.5"))
        assertTrue(json.contains("\"jump\":0.42"))
        assertTrue(json.contains("\"conf\":0.91"))
        assertTrue(json.contains("\"backend\":\"GPU FULL\""))
        assertTrue(json.contains("\"poseFps\":29.5"))
        assertTrue(json.contains("\"latency\":37"))
    }
}
