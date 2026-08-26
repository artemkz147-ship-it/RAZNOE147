package ru.vibecut.editor

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
fun buildClipAudioEffects(clip: VideoClip): List<AudioProcessor> {
    val volume = clip.audioVolume.coerceIn(0f, 1f)
    val durationMs = clip.sourceSliceDurationMs.coerceAtLeast(1L)
    val fadeInMs = clip.audioFadeInMs.coerceIn(0L, durationMs)
    val fadeOutMs = clip.audioFadeOutMs.coerceIn(0L, durationMs)

    if (volume >= 0.999f && fadeInMs == 0L && fadeOutMs == 0L) return emptyList()

    val builder = DefaultGainProvider.Builder(volume)

    if (fadeInMs > 0L) {
        builder.addFadeAt(
            0L,
            fadeInMs * 1000L,
            object : DefaultGainProvider.FadeProvider {
                override fun getGainFactorAt(index: Long, duration: Long): Float {
                    if (duration <= 0L) return volume
                    return (volume * index.toFloat() / duration.toFloat()).coerceIn(0f, volume)
                }
            },
        )
    }

    if (fadeOutMs > 0L) {
        val startMs = (durationMs - fadeOutMs).coerceAtLeast(0L)
        builder.addFadeAt(
            startMs * 1000L,
            fadeOutMs * 1000L,
            object : DefaultGainProvider.FadeProvider {
                override fun getGainFactorAt(index: Long, duration: Long): Float {
                    if (duration <= 0L) return 0f
                    return (volume * (1f - index.toFloat() / duration.toFloat())).coerceIn(0f, volume)
                }
            },
        )
    }

    return listOf(GainProcessor(builder.build()))
}

@OptIn(UnstableApi::class)
fun buildBackgroundAudioEffects(track: AudioTrack): List<AudioProcessor> {
    val volume = track.volume.coerceIn(0f, 1f)
    if (volume >= 0.999f) return emptyList()
    return listOf(GainProcessor(DefaultGainProvider.Builder(volume).build()))
}
