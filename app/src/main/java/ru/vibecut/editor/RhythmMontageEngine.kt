package ru.vibecut.editor

import java.util.UUID
import kotlin.math.max
import kotlin.math.min

object RhythmMontageEngine {
    fun build(
        sourceClips: List<VideoClip>,
        beatMap: BeatMap,
        style: AutoMontageStyle,
    ): List<VideoClip> {
        if (sourceClips.isEmpty()) return emptyList()
        val totalSourceMs = sourceClips.sumOf { it.durationMs }
        val targetMs = min(totalSourceMs, beatMap.durationMs).coerceAtLeast(500L)
        val cuts = selectCuts(beatMap, style, targetMs)
        if (cuts.size < 2) return applyStyleOnly(sourceClips, style)

        val result = ArrayList<VideoClip>()
        var sourceIndex = 0
        var sourceOffsetMs = 0L
        var segmentIndex = 0

        for (i in 0 until cuts.lastIndex) {
            if (sourceIndex >= sourceClips.size) break
            val desiredOutputMs = (cuts[i + 1] - cuts[i]).coerceAtLeast(260L)

            while (sourceIndex < sourceClips.size) {
                val src = sourceClips[sourceIndex]
                val sourceAvailableOutputMs = (src.durationMs - sourceOffsetMs).coerceAtLeast(0L)
                if (sourceAvailableOutputMs >= desiredOutputMs * 0.62f || sourceIndex == sourceClips.lastIndex) break
                sourceIndex++
                sourceOffsetMs = 0L
            }
            if (sourceIndex >= sourceClips.size) break

            val src = sourceClips[sourceIndex]
            val availableOutput = (src.durationMs - sourceOffsetMs).coerceAtLeast(1L)
            val actualOutput = min(desiredOutputMs, availableOutput).coerceAtLeast(220L)
            val sourceStartOffset = (sourceOffsetMs * src.speed.coerceAtLeast(.05f)).toLong()
            val sourceDurationNeeded = (actualOutput * src.speed.coerceAtLeast(.05f)).toLong()
            val trimStart = (src.trimStartMs + sourceStartOffset).coerceIn(src.trimStartMs, src.trimEndMs - 1L)
            val trimEnd = (trimStart + sourceDurationNeeded).coerceIn(trimStart + 1L, src.trimEndMs)
            val beat = beatMap.beats.minByOrNull { kotlin.math.abs(it.timeMs - cuts[i + 1]) }
            val styled = styleSegment(
                src.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${src.name} · ритм ${segmentIndex + 1}",
                    trimStartMs = trimStart,
                    trimEndMs = trimEnd,
                    keyframes = emptyList(),
                    stickers = src.stickers,
                ),
                style,
                segmentIndex,
                beat?.strength ?: .5f,
                beat?.strong == true,
            )
            result += styled
            sourceOffsetMs += actualOutput
            if (sourceOffsetMs >= src.durationMs - 180L) {
                sourceIndex++
                sourceOffsetMs = 0L
            }
            segmentIndex++
        }

        if (result.isEmpty()) return applyStyleOnly(sourceClips, style)
        return result.mapIndexed { index, clip ->
            if (index == result.lastIndex) clip.copy(transitionOut = TransitionType.NONE) else clip
        }
    }

    private fun selectCuts(map: BeatMap, style: AutoMontageStyle, targetMs: Long): List<Long> {
        val minGap = when (style) {
            AutoMontageStyle.REELS -> 320L
            AutoMontageStyle.DYNAMIC -> 420L
            AutoMontageStyle.TRAVEL -> 720L
            AutoMontageStyle.CALM -> 1250L
        }
        val maxGap = when (style) {
            AutoMontageStyle.REELS -> 1500L
            AutoMontageStyle.DYNAMIC -> 1900L
            AutoMontageStyle.TRAVEL -> 2800L
            AutoMontageStyle.CALM -> 4400L
        }
        val every = when (style) {
            AutoMontageStyle.REELS -> 1
            AutoMontageStyle.DYNAMIC -> 1
            AutoMontageStyle.TRAVEL -> 2
            AutoMontageStyle.CALM -> 4
        }
        val cuts = mutableListOf(0L)
        var last = 0L
        map.beats.forEachIndexed { index, beat ->
            if (beat.timeMs <= 120L || beat.timeMs >= targetMs) return@forEachIndexed
            val dueByGrid = index % every == 0
            val dueByAccent = beat.strong && beat.timeMs - last >= minGap
            val forcedByGap = beat.timeMs - last >= maxGap
            val strongEnough = beat.strength >= when (style) {
                AutoMontageStyle.REELS -> .26f
                AutoMontageStyle.DYNAMIC -> .32f
                AutoMontageStyle.TRAVEL -> .42f
                AutoMontageStyle.CALM -> .55f
            }
            if (beat.timeMs - last >= minGap && ((dueByGrid && strongEnough) || dueByAccent || forcedByGap)) {
                cuts += beat.timeMs
                last = beat.timeMs
            }
        }
        if (targetMs - last >= 220L) cuts += targetMs
        return cuts.distinct().sorted().take(121)
    }

    private fun styleSegment(
        clip: VideoClip,
        style: AutoMontageStyle,
        index: Int,
        strength: Float,
        strongBeat: Boolean,
    ): VideoClip {
        val transition = when (style) {
            AutoMontageStyle.REELS -> when {
                strongBeat && strength > .78f -> TransitionType.FLASH
                index % 3 == 0 -> TransitionType.ZOOM
                index % 2 == 0 -> TransitionType.SLIDE_LEFT
                else -> TransitionType.SLIDE_RIGHT
            }
            AutoMontageStyle.DYNAMIC -> when {
                strongBeat && strength > .82f -> TransitionType.FLASH
                index % 4 == 0 -> TransitionType.SPIN
                index % 2 == 0 -> TransitionType.ZOOM
                else -> TransitionType.SLIDE_LEFT
            }
            AutoMontageStyle.TRAVEL -> if (index % 2 == 0) TransitionType.SLIDE_LEFT else TransitionType.SLIDE_RIGHT
            AutoMontageStyle.CALM -> TransitionType.FADE
        }
        val motion = when (style) {
            AutoMontageStyle.REELS -> when (index % 3) { 0 -> ClipMotion.ZOOM_IN; 1 -> ClipMotion.PAN_RIGHT; else -> ClipMotion.ZOOM_OUT }
            AutoMontageStyle.DYNAMIC -> when (index % 4) { 0 -> ClipMotion.ZOOM_IN; 1 -> ClipMotion.PAN_LEFT; 2 -> ClipMotion.ZOOM_OUT; else -> ClipMotion.PAN_RIGHT }
            AutoMontageStyle.TRAVEL -> if (index % 2 == 0) ClipMotion.PAN_RIGHT else ClipMotion.PAN_LEFT
            AutoMontageStyle.CALM -> ClipMotion.ZOOM_IN
        }
        val duration = when (style) {
            AutoMontageStyle.REELS -> (260 + (1f - strength) * 180f).toLong()
            AutoMontageStyle.DYNAMIC -> (340 + (1f - strength) * 220f).toLong()
            AutoMontageStyle.TRAVEL -> 560L
            AutoMontageStyle.CALM -> 850L
        }
        return when (style) {
            AutoMontageStyle.REELS -> clip.copy(
                transitionOut = transition,
                transitionDurationMs = duration,
                motion = motion,
                motionStrength = (.13f + strength * .10f).coerceIn(.10f, .28f),
                contrast = .14f + strength * .06f,
                saturation = 18f + strength * 10f,
                lightness = 1f,
            )
            AutoMontageStyle.DYNAMIC -> clip.copy(
                transitionOut = transition,
                transitionDurationMs = duration,
                motion = motion,
                motionStrength = (.11f + strength * .09f).coerceIn(.09f, .24f),
                contrast = .10f + strength * .07f,
                saturation = 12f + strength * 9f,
            )
            AutoMontageStyle.TRAVEL -> clip.copy(
                transitionOut = transition,
                transitionDurationMs = duration,
                motion = motion,
                motionStrength = .10f + strength * .035f,
                hue = 5f,
                saturation = 11f + strength * 5f,
                lightness = 3f,
            )
            AutoMontageStyle.CALM -> clip.copy(
                transitionOut = transition,
                transitionDurationMs = duration,
                motion = motion,
                motionStrength = .065f + strength * .02f,
                contrast = -.025f,
                saturation = -3f,
                lightness = 4f,
            )
        }
    }

    private fun applyStyleOnly(source: List<VideoClip>, style: AutoMontageStyle): List<VideoClip> =
        source.mapIndexed { index, clip ->
            styleSegment(clip, style, index, .5f, false).copy(
                transitionOut = if (index == source.lastIndex) TransitionType.NONE else styleSegment(clip, style, index, .5f, false).transitionOut
            )
        }
}
