package com.openai.bodyrunner

import kotlin.math.min

internal data class RawLandmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float? = null,
    val presence: Float? = null,
)

internal object PoseLandmarkMapper {
    fun mapPoint(
        normalized: RawLandmark,
        world: RawLandmark?,
        mirrorX: Boolean,
    ): MotionPoint {
        val confidence = when {
            normalized.visibility != null && normalized.presence != null ->
                min(normalized.visibility, normalized.presence)
            normalized.visibility != null -> normalized.visibility
            normalized.presence != null -> normalized.presence
            else -> 1f
        }.coerceIn(0f, 1f)

        return MotionPoint(
            x = if (mirrorX) 1f - normalized.x else normalized.x,
            y = normalized.y,
            z = world?.z ?: normalized.z,
            confidence = confidence,
        )
    }
}
