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
) {
    val durationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(1L)
}

data class EditorSnapshot(
    val clips: List<VideoClip>,
    val selectedId: String?,
)

enum class ExportState {
    IDLE, EXPORTING, DONE, ERROR
}
