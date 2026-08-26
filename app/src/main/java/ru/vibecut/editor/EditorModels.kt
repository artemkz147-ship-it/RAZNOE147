package ru.vibecut.editor

data class VideoClip(
    val id: String,
    val uri: String,
    val name: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val muted: Boolean = false,
    val rotationDegrees: Int = 0,
    val speed: Float = 1f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val hue: Float = 0f,
    val lightness: Float = 0f,
    val crop: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val overlayText: String = "",
    val textX: Float = 0f,
    val textY: Float = -0.72f,
    val textScale: Float = 0.72f,
    val textRotation: Float = 0f,
) {
    val sourceSliceDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(1L)
    val durationMs: Long get() = (sourceSliceDurationMs / speed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)
}

data class AudioTrack(
    val uri: String,
    val name: String,
)

data class ExportSettings(
    val height: Int = 1080,
    val maxFrameRate: Int = 30,
    val aspectRatio: Float? = null,
    val cropToFill: Boolean = false,
)

data class EditorSnapshot(
    val clips: List<VideoClip>,
    val selectedId: String?,
)

enum class ExportState {
    IDLE, EXPORTING, DONE, ERROR
}
