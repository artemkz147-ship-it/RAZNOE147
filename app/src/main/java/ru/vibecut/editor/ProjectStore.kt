package ru.vibecut.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ProjectStore {
    private const val PREFS = "vibecut_project"
    private const val KEY = "autosave"

    fun save(context: Context, project: SavedProject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, projectToJson(project).toString())
            .apply()
    }

    fun load(context: Context): SavedProject? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return null
        return runCatching { projectFromJson(JSONObject(raw)) }.getOrNull()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun projectToJson(project: SavedProject): JSONObject = JSONObject().apply {
        put("selectedId", project.selectedId)
        put("clips", JSONArray().apply { project.clips.forEach { put(clipToJson(it)) } })
        put("backgroundAudio", project.backgroundAudio?.let(::audioToJson))
        put(
            "positionedAudioTracks",
            JSONArray().apply { project.positionedAudioTracks.forEach { put(positionedAudioToJson(it)) } }
        )
        put("export", exportToJson(project.exportSettings))
    }

    private fun projectFromJson(json: JSONObject): SavedProject {
        val clipsJson = json.optJSONArray("clips") ?: JSONArray()
        val clips = buildList {
            for (index in 0 until clipsJson.length()) add(clipFromJson(clipsJson.getJSONObject(index)))
        }
        val tracksJson = json.optJSONArray("positionedAudioTracks") ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until tracksJson.length()) add(positionedAudioFromJson(tracksJson.getJSONObject(index)))
        }
        val audio = json.optJSONObject("backgroundAudio")?.let(::audioFromJson)
        val export = json.optJSONObject("export")?.let(::exportFromJson) ?: ExportSettings()
        return SavedProject(
            clips = clips,
            selectedId = json.optString("selectedId").takeIf { it.isNotBlank() && it != "null" },
            backgroundAudio = audio,
            positionedAudioTracks = tracks,
            exportSettings = export,
        )
    }

    private fun clipToJson(clip: VideoClip) = JSONObject().apply {
        put("id", clip.id)
        put("uri", clip.uri)
        put("name", clip.name)
        put("sourceDurationMs", clip.sourceDurationMs)
        put("trimStartMs", clip.trimStartMs)
        put("trimEndMs", clip.trimEndMs)
        put("muted", clip.muted)
        put("audioVolume", clip.audioVolume.toDouble())
        put("audioFadeInMs", clip.audioFadeInMs)
        put("audioFadeOutMs", clip.audioFadeOutMs)
        put("rotationDegrees", clip.rotationDegrees)
        put("speed", clip.speed.toDouble())
        put("brightness", clip.brightness.toDouble())
        put("contrast", clip.contrast.toDouble())
        put("saturation", clip.saturation.toDouble())
        put("hue", clip.hue.toDouble())
        put("lightness", clip.lightness.toDouble())
        put("crop", clip.crop.toDouble())
        put("flipHorizontal", clip.flipHorizontal)
        put("flipVertical", clip.flipVertical)
        put("overlayText", clip.overlayText)
        put("textX", clip.textX.toDouble())
        put("textY", clip.textY.toDouble())
        put("textScale", clip.textScale.toDouble())
        put("textRotation", clip.textRotation.toDouble())
        put("textColor", clip.textColor)
        put("textBackground", clip.textBackground)
        put("textBold", clip.textBold)
        put("textItalic", clip.textItalic)
    }

    private fun clipFromJson(json: JSONObject) = VideoClip(
        id = json.getString("id"),
        uri = json.getString("uri"),
        name = json.optString("name", "Видео"),
        sourceDurationMs = json.optLong("sourceDurationMs", 1L).coerceAtLeast(1L),
        trimStartMs = json.optLong("trimStartMs", 0L),
        trimEndMs = json.optLong("trimEndMs", json.optLong("sourceDurationMs", 1L)),
        muted = json.optBoolean("muted", false),
        audioVolume = json.optDouble("audioVolume", 1.0).toFloat(),
        audioFadeInMs = json.optLong("audioFadeInMs", 0L),
        audioFadeOutMs = json.optLong("audioFadeOutMs", 0L),
        rotationDegrees = json.optInt("rotationDegrees", 0),
        speed = json.optDouble("speed", 1.0).toFloat(),
        brightness = json.optDouble("brightness", 0.0).toFloat(),
        contrast = json.optDouble("contrast", 0.0).toFloat(),
        saturation = json.optDouble("saturation", 0.0).toFloat(),
        hue = json.optDouble("hue", 0.0).toFloat(),
        lightness = json.optDouble("lightness", 0.0).toFloat(),
        crop = json.optDouble("crop", 0.0).toFloat(),
        flipHorizontal = json.optBoolean("flipHorizontal", false),
        flipVertical = json.optBoolean("flipVertical", false),
        overlayText = json.optString("overlayText", ""),
        textX = json.optDouble("textX", 0.0).toFloat(),
        textY = json.optDouble("textY", -0.72).toFloat(),
        textScale = json.optDouble("textScale", 0.72).toFloat(),
        textRotation = json.optDouble("textRotation", 0.0).toFloat(),
        textColor = json.optInt("textColor", -1),
        textBackground = json.optBoolean("textBackground", true),
        textBold = json.optBoolean("textBold", true),
        textItalic = json.optBoolean("textItalic", false),
    )

    private fun audioToJson(track: AudioTrack) = JSONObject().apply {
        put("uri", track.uri)
        put("name", track.name)
        put("volume", track.volume.toDouble())
    }

    private fun audioFromJson(json: JSONObject) = AudioTrack(
        uri = json.getString("uri"),
        name = json.optString("name", "Музыка"),
        volume = json.optDouble("volume", 0.65).toFloat(),
    )

    private fun positionedAudioToJson(track: PositionedAudioTrack) = JSONObject().apply {
        put("id", track.id)
        put("uri", track.uri)
        put("name", track.name)
        put("sourceDurationMs", track.sourceDurationMs)
        put("startAtMs", track.startAtMs)
        put("volume", track.volume.toDouble())
    }

    private fun positionedAudioFromJson(json: JSONObject) = PositionedAudioTrack(
        id = json.getString("id"),
        uri = json.getString("uri"),
        name = json.optString("name", "Звук"),
        sourceDurationMs = json.optLong("sourceDurationMs", 1L).coerceAtLeast(1L),
        startAtMs = json.optLong("startAtMs", 0L).coerceAtLeast(0L),
        volume = json.optDouble("volume", 0.85).toFloat(),
    )

    private fun exportToJson(settings: ExportSettings) = JSONObject().apply {
        put("height", settings.height)
        put("maxFrameRate", settings.maxFrameRate)
        if (settings.aspectRatio == null) put("aspectRatio", JSONObject.NULL)
        else put("aspectRatio", settings.aspectRatio.toDouble())
        put("cropToFill", settings.cropToFill)
        put("videoCodec", settings.videoCodec.name)
    }

    private fun exportFromJson(json: JSONObject) = ExportSettings(
        height = json.optInt("height", 1080),
        maxFrameRate = json.optInt("maxFrameRate", 30),
        aspectRatio = if (json.isNull("aspectRatio")) null else json.optDouble("aspectRatio").toFloat(),
        cropToFill = json.optBoolean("cropToFill", false),
        videoCodec = runCatching {
            VideoCodec.valueOf(json.optString("videoCodec", VideoCodec.H264.name))
        }.getOrDefault(VideoCodec.H264),
    )
}
