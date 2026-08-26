package ru.vibecut.editor

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

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

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    LaunchedEffect(clip.id, clip.trimStartMs, clip.trimEndMs) {
        val clipping = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.trimStartMs).setEndPositionMs(clip.trimEndMs).build()
        player.setMediaItem(MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(clipping).build())
        player.prepare(); player.seekTo(0L); position = 0L; duration = clip.sourceSliceDurationMs; onPosition(0L)
    }

    LaunchedEffect(clip, incomingTransition, exportSettings) {
        player.setVideoEffects(
            buildVideoEffects(context, clip.copy(keyframes = emptyList(), stickers = emptyList()), incomingTransition) +
                buildEasedKeyframeEffects(clip) +
                buildSpecialEffectEffects(clip) +
                buildDynamicImageStickerEffects(context, clip) +
                buildAnimatedStickerEffects(clip) +
                buildCanvasEffects(exportSettings, false)
        )
        player.setPlaybackSpeed(clip.speed)
        player.volume = if (clip.muted) 0f else clip.audioVolume.coerceIn(0f, 1f)
    }

    LaunchedEffect(player) {
        while (true) { position = player.currentPosition.coerceAtLeast(0L); if (player.duration > 0) duration = player.duration; onPosition(position); delay(80L) }
    }

    val globalPositionMs = projectOffsetMs + (position / clip.speed.coerceAtLeast(0.05f)).toLong()
    val cue = subtitles.lastOrNull { globalPositionMs in it.startMs until it.endMs }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.Black, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            AndroidView(factory = { PlayerView(it).apply { useController = false; this.player = player } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
            if (cue != null) {
                Text(
                    text = cue.text,
                    color = Color(subtitleStyle.textColor),
                    fontSize = (18f * subtitleStyle.fontScale.coerceIn(0.55f, 2.2f)).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(if (subtitleStyle.verticalPosition < 0.72f) Alignment.Center else Alignment.BottomCenter)
                        .padding(horizontal = 22.dp, vertical = 22.dp)
                        .then(if (subtitleStyle.backgroundEnabled) Modifier.background(Color(subtitleStyle.backgroundColor), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp) else Modifier),
                )
            }
        }
        Slider(value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()), onValueChange = { val target=it.toLong();player.seekTo(target);position=target;onPosition(target) }, valueRange = 0f..duration.coerceAtLeast(1L).toFloat(), modifier=Modifier.fillMaxWidth())
        Row(modifier=Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Button(onClick={if(playing)player.pause() else player.play()}){Text(if(playing)"Пауза" else "Пуск")}
            Text("${formatTime(position)} / ${formatTime(duration)} · ${formatSpeed(clip.speed)}",color=Color(0xFFCACAD3),style=MaterialTheme.typography.labelSmall)
        }
    }
}
