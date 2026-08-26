package ru.vibecut.editor

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private data class TimelineBlock(
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val accent: Color,
)

@Composable
internal fun ProTimeline(
    clips: List<VideoClip>,
    selectedId: String?,
    positionMs: Long,
    music: AudioTrack?,
    audioTracks: List<PositionedAudioTrack>,
    subtitles: List<SubtitleCue>,
    onSelect: (String) -> Unit,
    onSnapshot: () -> Unit,
    onUpdateClip: (VideoClip) -> Unit,
    onMoveClip: (String, Int) -> Unit,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    val scroll = rememberScrollState()
    val pixelsPerSecond = 54f * zoom
    val totalDuration = clips.sumOf { it.durationMs }.coerceAtLeast(1L)
    val projectWidth = clips.sumOf { clipDisplayWidthDp(it, pixelsPerSecond).toDouble() }.toFloat().coerceAtLeast(320f)
    val layerBlocks = remember(clips, pixelsPerSecond) { buildLayerBlocks(clips) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0C0C12))
            .padding(top = 4.dp, bottom = 5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Таймлайн", color = Color(0xFFE7E7ED), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text("Удерживайте клип для переноса · края — обрезка", color = Color(0xFF747480), fontSize = 8.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                TimelineZoomButton("−") { zoom = (zoom / 1.22f).coerceIn(.55f, 3f) }
                Text("${(zoom * 100).roundToInt()}%", color = Color(0xFFB9B1CA), fontSize = 9.sp)
                TimelineZoomButton("+") { zoom = (zoom * 1.22f).coerceIn(.55f, 3f) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(vertical = 2.dp),
        ) {
            TimelineRuler(clips, totalDuration, projectWidth, pixelsPerSecond) { gestureZoom ->
                zoom = (zoom * gestureZoom).coerceIn(.55f, 3f)
            }
            VideoLane(
                clips = clips,
                selectedId = selectedId,
                positionMs = positionMs,
                pixelsPerSecond = pixelsPerSecond,
                onSelect = onSelect,
                onSnapshot = onSnapshot,
                onUpdateClip = onUpdateClip,
                onMoveClip = onMoveClip,
            )

            val audioBlocks = buildList {
                if (music != null) add(TimelineBlock(0L, totalDuration, "Музыка · ${music.name}", Color(0xFF2F7D65)))
                audioTracks.forEach { track ->
                    add(
                        TimelineBlock(
                            startMs = track.startAtMs.coerceAtLeast(0L),
                            endMs = (track.startAtMs + track.sourceDurationMs).coerceAtMost(totalDuration),
                            label = track.name,
                            accent = Color(0xFF286B86),
                        )
                    )
                }
            }
            TrackLane("Звук", audioBlocks, clips, projectWidth, pixelsPerSecond)

            val subtitleBlocks = subtitles.map { cue ->
                TimelineBlock(cue.startMs, cue.endMs, cue.text, Color(0xFF7A5AB4))
            }
            TrackLane("Текст", subtitleBlocks, clips, projectWidth, pixelsPerSecond)
            TrackLane("Слои", layerBlocks, clips, projectWidth, pixelsPerSecond)
        }
    }
}

@Composable
private fun TimelineRuler(
    clips: List<VideoClip>,
    totalDuration: Long,
    projectWidth: Float,
    pixelsPerSecond: Float,
    onZoom: (Float) -> Unit,
) {
    val stepMs = when {
        pixelsPerSecond < 42f -> 5000L
        pixelsPerSecond < 86f -> 2000L
        else -> 1000L
    }
    val markers = remember(totalDuration, stepMs) {
        buildList {
            var t = 0L
            while (t <= totalDuration) {
                add(t)
                t += stepMs
            }
            if (lastOrNull() != totalDuration) add(totalDuration)
        }
    }

    Row(
        modifier = Modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, gestureZoom, _ ->
                    if (abs(gestureZoom - 1f) > .004f) onZoom(gestureZoom)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text("⇔", color = Color(0xFF6E6E7B), fontSize = 10.sp)
        }
        Box(Modifier.width(projectWidth.dp).fillMaxHeight()) {
            markers.forEach { time ->
                val x = projectTimeToDp(time, clips, pixelsPerSecond)
                Box(
                    Modifier
                        .offset(x = x.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF2D2D36))
                )
                Text(
                    formatTime(time),
                    color = Color(0xFF777784),
                    fontSize = 7.sp,
                    modifier = Modifier.offset(x = (x + 3f).dp, y = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoLane(
    clips: List<VideoClip>,
    selectedId: String?,
    positionMs: Long,
    pixelsPerSecond: Float,
    onSelect: (String) -> Unit,
    onSnapshot: () -> Unit,
    onUpdateClip: (VideoClip) -> Unit,
    onMoveClip: (String, Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LaneLabel("Видео")
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            clips.forEachIndexed { index, clip ->
                val selected = clip.id == selectedId
                val widthDp = clipDisplayWidthDp(clip, pixelsPerSecond)
                TimelineClip(
                    clip = clip,
                    index = index,
                    clipCount = clips.size,
                    selected = selected,
                    widthDp = widthDp,
                    pixelsPerSecond = pixelsPerSecond,
                    positionMs = if (selected) positionMs else 0L,
                    onSelect = onSelect,
                    onSnapshot = onSnapshot,
                    onUpdateClip = onUpdateClip,
                    onMoveClip = onMoveClip,
                )
            }
        }
    }
}

@Composable
private fun TimelineClip(
    clip: VideoClip,
    index: Int,
    clipCount: Int,
    selected: Boolean,
    widthDp: Float,
    pixelsPerSecond: Float,
    positionMs: Long,
    onSelect: (String) -> Unit,
    onSnapshot: () -> Unit,
    onUpdateClip: (VideoClip) -> Unit,
    onMoveClip: (String, Int) -> Unit,
) {
    val density = LocalDensity.current
    val currentClip by rememberUpdatedState(clip)
    val currentIndex by rememberUpdatedState(index)
    val widthPx = with(density) { widthDp.dp.toPx().coerceAtLeast(1f) }
    val progress = if (selected) {
        (positionMs.toFloat() / clip.sourceSliceDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(74.dp)
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF211934) else Color(0xFF17171D))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFFA88BFF) else Color(0xFF272730),
                shape = RoundedCornerShape(10.dp),
            )
            .pointerInput(clip.id, widthDp) {
                var dragX = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragX = 0f },
                    onDragEnd = {
                        val shift = (dragX / widthPx).roundToInt()
                        if (shift != 0) {
                            val target = (currentIndex + shift).coerceIn(0, clipCount - 1)
                            if (target != currentIndex) onMoveClip(clip.id, target)
                        }
                    },
                    onDragCancel = { dragX = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                    },
                )
            }
            .clickable { onSelect(clip.id) },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(46.dp)) {
                ClipTimelineThumbnail(clip, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color(0x24000000)))
                Text(
                    "${index + 1}",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color(0xB8000000), RoundedCornerShape(5.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (clip.muted) TimelineBadge("M")
                    if (clip.speed != 1f) TimelineBadge("${"%.1f".format(clip.speed)}×")
                    if (hasVisualEdits(clip)) TimelineBadge("FX")
                    if (clip.transitionOut != TransitionType.NONE) TimelineBadge("↔")
                }
                if (selected && progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(Color(0xFFB59CFF))
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    clip.name,
                    color = if (selected) Color.White else Color(0xFFC6C6CF),
                    fontSize = 8.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(3.dp))
                Text(formatTime(clip.durationMs), color = Color(0xFF8D8D99), fontSize = 7.sp)
            }
        }

        if (selected) {
            TrimHandle(
                left = true,
                modifier = Modifier.align(Alignment.CenterStart),
                currentClip = { currentClip },
                pixelsPerSecond = pixelsPerSecond,
                cursorPositionMs = positionMs,
                onSnapshot = onSnapshot,
                onUpdateClip = onUpdateClip,
            )
            TrimHandle(
                left = false,
                modifier = Modifier.align(Alignment.CenterEnd),
                currentClip = { currentClip },
                pixelsPerSecond = pixelsPerSecond,
                cursorPositionMs = positionMs,
                onSnapshot = onSnapshot,
                onUpdateClip = onUpdateClip,
            )
        }
    }
}

@Composable
private fun TrimHandle(
    left: Boolean,
    modifier: Modifier,
    currentClip: () -> VideoClip,
    pixelsPerSecond: Float,
    cursorPositionMs: Long,
    onSnapshot: () -> Unit,
    onUpdateClip: (VideoClip) -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .width(14.dp)
            .fillMaxHeight()
            .background(Color(0xDDA88BFF))
            .pointerInput(left, pixelsPerSecond) {
                var dragPx = 0f
                var base = currentClip()
                detectDragGestures(
                    onDragStart = {
                        base = currentClip()
                        dragPx = 0f
                        onSnapshot()
                    },
                    onDragEnd = {},
                    onDragCancel = {},
                    onDrag = { change, amount ->
                        change.consume()
                        dragPx += amount.x
                        val dragDp = with(density) { dragPx.toDp().value }
                        val deltaSourceMs = (dragDp * 1000f * base.speed.coerceAtLeast(.05f) / pixelsPerSecond).roundToLong()
                        val cursorSource = (base.trimStartMs + cursorPositionMs).coerceIn(0L, base.sourceDurationMs)
                        if (left) {
                            val raw = (base.trimStartMs + deltaSourceMs).coerceIn(0L, base.trimEndMs - 150L)
                            val snapped = magneticSnap(raw, cursorSource, 0L, base.trimEndMs - 150L)
                            onUpdateClip(base.copy(trimStartMs = snapped))
                        } else {
                            val raw = (base.trimEndMs + deltaSourceMs).coerceIn(base.trimStartMs + 150L, base.sourceDurationMs)
                            val snapped = magneticSnap(raw, cursorSource, base.trimStartMs + 150L, base.sourceDurationMs)
                            onUpdateClip(base.copy(trimEndMs = snapped))
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).height(25.dp).background(Color.White, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun TrackLane(
    name: String,
    blocks: List<TimelineBlock>,
    clips: List<VideoClip>,
    projectWidth: Float,
    pixelsPerSecond: Float,
) {
    Row(modifier = Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        LaneLabel(name)
        Box(
            Modifier
                .width(projectWidth.dp)
                .height(28.dp)
                .background(Color(0xFF111117), RoundedCornerShape(7.dp))
        ) {
            blocks.forEach { block ->
                val start = projectTimeToDp(block.startMs.coerceAtLeast(0L), clips, pixelsPerSecond)
                val end = projectTimeToDp(block.endMs.coerceAtLeast(block.startMs + 1L), clips, pixelsPerSecond)
                val width = max(12f, end - start)
                Box(
                    modifier = Modifier
                        .offset(x = start.dp, y = 3.dp)
                        .width(width.dp)
                        .height(22.dp)
                        .background(block.accent.copy(alpha = .72f), RoundedCornerShape(6.dp))
                        .border(1.dp, block.accent.copy(alpha = .95f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(block.label, color = Color.White, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LaneLabel(name: String) {
    Box(Modifier.width(48.dp).height(28.dp), contentAlignment = Alignment.Center) {
        Text(name, color = Color(0xFF777784), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TimelineZoomButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(27.dp)
            .height(24.dp)
            .background(Color(0xFF1C1C24), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF2D2D37), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimelineBadge(text: String) {
    Text(
        text,
        color = Color(0xFFF2EDFF),
        fontSize = 6.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(Color(0xCC2B1F45), RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

@Composable
private fun ClipTimelineThumbnail(clip: VideoClip, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, clip.uri, clip.trimStartMs, clip.trimEndMs) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(clip.uri)
                if (uri.scheme == "file") retriever.setDataSource(uri.path)
                else retriever.setDataSource(context, uri)
                val frameMs = clip.trimStartMs + clip.sourceSliceDurationMs / 2L
                retriever.getFrameAtTime(frameMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(Color(0xFF202029)), contentAlignment = Alignment.Center) {
            Text("▶", color = Color(0xFF6F6F7D), fontSize = 12.sp)
        }
    }
}

private fun clipDisplayWidthDp(clip: VideoClip, pixelsPerSecond: Float): Float =
    max(28f, clip.durationMs.coerceAtLeast(1L) / 1000f * pixelsPerSecond)

private fun projectTimeToDp(timeMs: Long, clips: List<VideoClip>, pixelsPerSecond: Float): Float {
    var remaining = timeMs.coerceAtLeast(0L)
    var x = 0f
    for (clip in clips) {
        val duration = clip.durationMs.coerceAtLeast(1L)
        val width = clipDisplayWidthDp(clip, pixelsPerSecond)
        if (remaining <= duration) return x + width * (remaining.toFloat() / duration.toFloat())
        remaining -= duration
        x += width
    }
    return x
}

private fun magneticSnap(value: Long, cursorSource: Long, minValue: Long, maxValue: Long): Long {
    val bounded = value.coerceIn(minValue, maxValue)
    val halfSecond = ((bounded + 250L) / 500L) * 500L
    val candidates = listOf(minValue, maxValue, cursorSource.coerceIn(minValue, maxValue), halfSecond.coerceIn(minValue, maxValue))
    val nearest = candidates.minByOrNull { abs(it - bounded) } ?: bounded
    return if (abs(nearest - bounded) <= 120L) nearest else bounded
}

private fun buildLayerBlocks(clips: List<VideoClip>): List<TimelineBlock> {
    val blocks = mutableListOf<TimelineBlock>()
    var start = 0L
    clips.forEach { clip ->
        val layerCount = clip.stickers.size + clip.animatedStickers.size + clip.gifStickers.size + clip.trackedOverlays.size +
            clip.keyframes.size + if (clip.overlayText.isNotBlank()) 1 else 0
        if (layerCount > 0) {
            blocks += TimelineBlock(start, start + clip.durationMs, "$layerCount элем.", Color(0xFF8A5B9E))
        }
        start += clip.durationMs
    }
    return blocks
}

private fun hasVisualEdits(clip: VideoClip): Boolean =
    clip.colorEffect != ColorEffect.NONE ||
        clip.specialEffect != SpecialEffect.NONE ||
        clip.motion != ClipMotion.NONE ||
        clip.maskType != MaskType.NONE ||
        clip.vignette > 0f ||
        clip.keyframes.isNotEmpty() ||
        clip.stickers.isNotEmpty() ||
        clip.animatedStickers.isNotEmpty() ||
        clip.gifStickers.isNotEmpty() ||
        clip.trackedOverlays.isNotEmpty() ||
        clip.overlayText.isNotBlank()
