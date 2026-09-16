package com.openai.bodyrunner

internal object MotionPacketEncoder {
    fun encode(
        state: MotionState,
        backend: String,
        poseFps: Float,
        latencyMs: Long,
    ): String = buildString(320) {
        append("{\"t\":").append(state.timestampMs)
        append(",\"cx\":").append(state.centerX)
        append(",\"cy\":").append(state.centerY)
        append(",\"vx\":").append(state.lateralVelocity)
        append(",\"vy\":").append(state.verticalVelocity)
        append(",\"lean\":").append(state.torsoLean)
        append(",\"la\":").append(state.leftArmElevation)
        append(",\"ra\":").append(state.rightArmElevation)
        append(",\"le\":").append(state.leftArmExtension)
        append(",\"re\":").append(state.rightArmExtension)
        append(",\"lwv\":").append(state.leftWristVelocity)
        append(",\"rwv\":").append(state.rightWristVelocity)
        append(",\"lk\":").append(state.leftElbowAngle)
        append(",\"rk\":").append(state.rightElbowAngle)
        append(",\"hip\":").append(state.hipDelta)
        append(",\"knee\":").append(state.kneeCompression)
        append(",\"jump\":").append(state.jumpImpulse)
        append(",\"conf\":").append(state.trackingConfidence)
        append(",\"backend\":").append(quote(backend))
        append(",\"poseFps\":").append(poseFps)
        append(",\"latency\":").append(latencyMs)
        append('}')
    }

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else append(char)
            }
        }
        append('"')
    }
}
