package ru.vibecut.editor

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
internal fun EditorPreview(
    clip: VideoClip,
    incomingTransition: TransitionSpec?,
    exportSettings: ExportSettings,
    projectOffsetMs: Long,
    subtitles: List<SubtitleCue>,
    subtitleStyle: SubtitleStyle,
    onPosition: (Long) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(clip.sourceSliceDurationMs) }
    var playing by remember { mutableStateOf(false) }
    val gesturesEnabled = PreviewGestureBridge.enabled

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    LaunchedEffect(clip.id, clip.trimStartMs, clip.trimEndMs) {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.trimStartMs)
            .setEndPositionMs(clip.trimEndMs)
            .build()
        player.setMediaItem(MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(clipping).build())
        player.prepare()
        player.seekTo(0L)
        position = 0L
        duration = clip.sourceSliceDurationMs
        EditorCursorState.clipPositionMs = 0L
        onPosition(0L)
    }

    LaunchedEffect(clip, incomingTransition, exportSettings) {
        player.setVideoEffects(
            buildVideoEffects(context, clip.copy(keyframes = emptyList(), stickers = emptyList()), incomingTransition) +
                buildEasedKeyframeEffects(clip) +
                buildSpecialEffectEffects(clip) +
                buildDynamicImageStickerEffects(context, clip) +
                buildGifStickerEffects(context, clip) +
                buildAnimatedStickerEffects(clip) +
                buildTrackedObjectOverlayEffects(clip) +
                buildCanvasEffects(exportSettings, false)
        )
        player.setPlaybackSpeed(clip.speed)
        player.volume = if (clip.muted) 0f else clip.audioVolume.coerceIn(0f, 1f)
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0) duration = player.duration
            EditorCursorState.clipPositionMs = position
            onPosition(position)
            delay(80L)
        }
    }

    val globalPositionMs = projectOffsetMs + (position / clip.speed.coerceAtLeast(0.05f)).toLong()
    val cue = subtitles.lastOrNull { globalPositionMs in it.startMs until it.endMs }
    val effectsCount = listOf(
        clip.colorEffect != ColorEffect.NONE,
        clip.specialEffect != SpecialEffect.NONE,
        clip.motion != ClipMotion.NONE,
        clip.maskType != MaskType.NONE,
        clip.keyframes.isNotEmpty(),
        clip.stickers.isNotEmpty() || clip.animatedStickers.isNotEmpty() || clip.gifStickers.isNotEmpty(),
    ).count { it }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(218.dp)
                .background(Color.Black, RoundedCornerShape(20.dp))
                .border(
                    1.dp,
                    if (gesturesEnabled) Color(0xFF7C63C9) else Color(0xFF282832),
                    RoundedCornerShape(20.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; this.player = player } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    clip.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(Color(0xA6000000), RoundedCornerShape(9.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                if (effectsCount > 0) {
                    Text(
                        "$effectsCount эфф.",
                        color = Color(0xFFE1D5FF),
                        fontSize = 10.sp,
                        modifier = Modifier.background(Color(0xCC241B3A), RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }

            if (cue != null) {
                Text(
                    text = cue.text,
                    color = Color(subtitleStyle.textColor),
                    fontSize = (18f * subtitleStyle.fontScale.coerceIn(0.55f, 2.2f)).sp,
                    fontWeight = if (subtitleStyle.bold) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(if (subtitleStyle.verticalPosition < 0.72f) Alignment.Center else Alignment.BottomCenter)
                        .padding(horizontal = 22.dp, vertical = 22.dp)
                        .then(
                            if (subtitleStyle.backgroundEnabled) {
                                Modifier.background(Color(subtitleStyle.backgroundColor), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                            } else Modifier
                        ),
                )
            }

            if (gesturesEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(clip.id) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var started = false
                                do {
                                    val event = awaitPointerEvent()
                                    val pan = event.calculatePan()
                                    val zoom = event.calculateZoom()
                                    val rotation = event.calculateRotation()
                                    val meaningful = pan.getDistance() > .35f || abs(zoom - 1f) > .002f || abs(rotation) > .08f
                                    if (meaningful) {
                                        if (!started) {
                                            started = true
                                            player.pause()
                                            PreviewGestureBridge.onGestureStart?.invoke()
                                        }
                                        PreviewGestureBridge.onTransform?.invoke(pan.x, pan.y, zoom, rotation)
                                        event.changes.forEach { change -> if (change.pressed) change.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                                if (started) PreviewGestureBridge.onGestureEnd?.invoke()
                            }
                        }
                )
                Text(
                    "Жесты: перемещение · масштаб · поворот",
                    color = Color(0xFFF0EBFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(9.dp)
                        .background(Color(0xCC241B3A), RoundedCornerShape(9.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .size(44.dp)
                    .background(Color(0xCC17171F), CircleShape)
                    .border(1.dp, Color(0x667C63C9), CircleShape)
                    .clickable { if (playing) player.pause() else player.play() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (playing) "Ⅱ" else "▶",
                    color = Color.White,
                    fontSize = if (playing) 17.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Slider(
            value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
            onValueChange = {
                val target = it.toLong()
                player.seekTo(target)
                position = target
                EditorCursorState.clipPositionMs = target
                onPosition(target)
            },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth().height(26.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFB69CFF),
                activeTrackColor = Color(0xFF8B5CF6),
                inactiveTrackColor = Color(0xFF292932),
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${formatTime(position)} / ${formatTime(duration)}",
                color = Color(0xFFE1E1E8),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                formatSpeed(clip.speed),
                color = Color(0xFFB69CFF),
                fontSize = 10.sp,
                modifier = Modifier.background(Color(0xFF211A31), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }
    }
}
