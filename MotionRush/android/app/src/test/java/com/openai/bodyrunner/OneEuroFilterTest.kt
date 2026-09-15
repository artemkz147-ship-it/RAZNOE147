package com.openai.bodyrunner

import org.junit.Assert.assertTrue
import org.junit.Test

class OneEuroFilterTest {
    @Test
    fun staticNoiseIsSmoothedButFastMotionRemainsResponsive() {
        val filter = OneEuroFilter(minCutoff = 1.2f, beta = 0.12f, derivativeCutoff = 1.0f)
        val quiet = listOf(0.500f, 0.507f, 0.494f, 0.503f).mapIndexed { index, value ->
            filter.filter(value, index * 0.033f)
        }
        assertTrue(quiet.maxOrNull()!! - quiet.minOrNull()!! < 0.010f)

        val fast = filter.filter(0.75f, 0.165f)
        assertTrue(fast > 0.64f)
    }

    @Test
    fun resetDropsOldHistory() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.1f, derivativeCutoff = 1.0f)
        filter.filter(0.2f, 0f)
        filter.filter(0.25f, 0.033f)
        filter.reset()
        val fresh = filter.filter(0.8f, 1f)
        assertTrue(kotlin.math.abs(fresh - 0.8f) < 0.0001f)
    }
}
