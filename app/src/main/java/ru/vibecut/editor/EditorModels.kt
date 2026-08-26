package ru.vibecut.editor

enum class ClipMotion(val title: String) {
    NONE("Без анимации"),
    ZOOM_IN("Приближение"),
    ZOOM_OUT("Отдаление"),
    PAN_LEFT("Панорама влево"),
    PAN_RIGHT("Панорама вправо"),
    PAN_UP("Панорама вверх"),
    PAN_DOWN("Панорама вниз"),
}

data class VideoClip(
    val id: String,
    val uri: String,
    val name: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val muted: Boolean = false,
    val audioVolume: Float = 1f,
    val audioFadeInMs: Long = 0L,
    val audioFadeOutMs: Long = 0L,
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
    val motion: ClipMotion = ClipMotion.NONE,
    val motionStrength: Float = 0.14f,
    val overlayText: String = "",
    val textX: Float = 0f,
    val textY: Float = -0.72f,
    val textScale: Float = 0.72f,
    val textRotation: Float = 0f,
    val textColor: Int = -1,
    val textBackground: Boolean = true,
    val textBold: Boolean = true,
    val textItalic: Boolean = false,
) {
    val sourceSliceDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(1L)
    val durationMs: Long get() = (sourceSliceDurationMs / speed.coerceAtLeast(0.05f)).toLong().coerceAtLeast(1L)
}

data class AudioTrack(
    val uri: String,
    val name: String,
    val volume: Float = 0.65f,
)

data class PositionedAudioTrack(
    val id: String,
    val uri: String,
    val name: String,
    val sourceDurationMs: Long,
    val startAtMs: Long = 0L,
    val volume: Float = 0.85f,
)

enum class VideoCodec(
    val title: String,
    val mimeType: String,
) {
    H264("H.264", "video/avc"),
    H265("H.265", "video/hevc"),
}

data class ExportSettings(
    val height: Int = 1080,
    val maxFrameRate: Int = 30,
    val aspectRatio: Float? = null,
    val cropToFill: Boolean = false,
    val videoCodec: VideoCodec = VideoCodec.H264,
)

data class EditorSnapshot(
    val clips: List<VideoClip>,
    val selectedId: String?,
)

data class SavedProject(
    val clips: List<VideoClip> = emptyList(),
    val selectedId: String? = null,
    val backgroundAudio: AudioTrack? = null,
    val positionedAudioTracks: List<PositionedAudioTrack> = emptyList(),
    val exportSettings: ExportSettings = ExportSettings(),
)

enum class ExportState {
    IDLE, EXPORTING, DONE, ERROR
}
