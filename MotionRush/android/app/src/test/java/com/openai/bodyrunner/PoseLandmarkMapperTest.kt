package com.openai.bodyrunner

import org.junit.Assert.assertEquals
import org.junit.Test

class PoseLandmarkMapperTest {
    @Test
    fun mirrorsFrontCameraAndUsesWorldDepthAndWeakestConfidence() {
        val normalized = RawLandmark(
            x = 0.20f,
            y = 0.30f,
            z = 0.40f,
            visibility = 0.80f,
            presence = 0.60f,
        )
        val world = RawLandmark(x = 0.01f, y = 0.02f, z = -0.12f, visibility = 0.9f, presence = 0.9f)
        val point = PoseLandmarkMapper.mapPoint(normalized, world, mirrorX = true)
        assertEquals(0.80f, point.x, 0.0001f)
        assertEquals(0.30f, point.y, 0.0001f)
        assertEquals(-0.12f, point.z, 0.0001f)
        assertEquals(0.60f, point.confidence, 0.0001f)
    }

    @Test
    fun fallsBackToNormalizedDepthAndAvailableConfidence() {
        val normalized = RawLandmark(
            x = 0.65f,
            y = 0.42f,
            z = -0.07f,
            visibility = 0.74f,
            presence = null,
        )
        val point = PoseLandmarkMapper.mapPoint(normalized, world = null, mirrorX = false)
        assertEquals(0.65f, point.x, 0.0001f)
        assertEquals(-0.07f, point.z, 0.0001f)
        assertEquals(0.74f, point.confidence, 0.0001f)
    }
}
