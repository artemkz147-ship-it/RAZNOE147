package com.openai.bodyrunner

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

internal class OneEuroFilter(
    private val minCutoff: Float,
    private val beta: Float,
    private val derivativeCutoff: Float,
) {
    private var lastTime: Float? = null
    private var lastRaw: Float? = null
    private var lastFiltered: Float? = null
    private var filteredDerivative = 0f

    fun filter(value: Float, timeSeconds: Float): Float {
        val previousTime = lastTime
        if (previousTime == null) {
            lastTime = timeSeconds
            lastRaw = value
            lastFiltered = value
            filteredDerivative = 0f
            return value
        }

        val dt = (timeSeconds - previousTime).coerceIn(1f / 240f, 0.25f)
        val derivative = (value - (lastRaw ?: value)) / dt
        filteredDerivative = lowPass(
            filteredDerivative,
            derivative,
            alpha(derivativeCutoff, dt),
        )

        val cutoff = minCutoff + beta * abs(filteredDerivative)
        val euroAlpha = alpha(cutoff, dt)
        // Fast deliberate motion gets a response floor so filtering never feels sticky.
        val responseFloor = (abs(derivative) * 0.16f).coerceIn(0f, 0.62f)
        val out = lowPass(lastFiltered ?: value, value, max(euroAlpha, responseFloor))

        lastTime = timeSeconds
        lastRaw = value
        lastFiltered = out
        return out
    }

    fun reset() {
        lastTime = null
        lastRaw = null
        lastFiltered = null
        filteredDerivative = 0f
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff.coerceAtLeast(0.01f))
        return 1f / (1f + tau / dt)
    }

    private fun lowPass(previous: Float, current: Float, alpha: Float): Float =
        previous + alpha * (current - previous)
}
