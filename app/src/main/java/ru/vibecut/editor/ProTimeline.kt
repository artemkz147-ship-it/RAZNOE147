package ru.vibecut.editor

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ProTimeline(
    clips: List<VideoClip>,
    selectedId: String?,
    positionMs: Long,
    onSelect: (String) -> Unit,
) {
    val totalDuration = clips.sumOf { it.durationMs }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0C0C12))
            .padding(top = 4.dp, bottom = 5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Таймлайн", color = Color(0xFFE7E7ED), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text("${clips.size} клип. · ${formatTime(totalDuration)}", color = Color(0xFF848491), fontSize = 10.sp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 9.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            clips.forEachIndexed { index, clip ->
                val selected = clip.id == selectedId
                val cardWidth = (82f + clip.durationMs / 1000f * 6f).coerceIn(82f, 205f).dp
                val progress = if (selected) {
                    (positionMs.toFloat() / clip.sourceSliceDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                } else 0f

                Column(
                    modifier = Modifier
                        .width(cardWidth)
                        .height(72.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) Color(0xFF211934) else Color(0xFF17171D))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) Color(0xFFA88BFF) else Color(0xFF272730),
                            shape = RoundedCornerShape(11.dp),
                        )
                        .clickable { onSelect(clip.id) },
                ) {
                    Box(Modifier.fillMaxWidth().height(43.dp)) {
                        ClipTimelineThumbnail(clip, Modifier.fillMaxWidth().fillMaxHeight())
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .background(Color(0x26000000))
                        )
                        Text(
                            "${index + 1}",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .background(Color(0xB8000000), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            if (clip.muted) TimelineBadge("M")
                            if (clip.speed != 1f) TimelineBadge("${"%.1f".format(clip.speed)}×")
                            if (hasVisualEdits(clip)) TimelineBadge("FX")
                            if (clip.transitionOut != TransitionType.NONE) TimelineBadge("↔")
                        }
                        if (selected) {
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
                            fontSize = 9.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(formatTime(clip.durationMs), color = Color(0xFF8D8D99), fontSize = 8.sp)
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun TimelineBadge(text: String) {
    Text(
        text,
        color = Color(0xFFF2EDFF),
        fontSize = 7.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(Color(0xCC2B1F45), RoundedCornerShape(5.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
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
                val frameMs = clip.trimStartMs + (clip.sourceSliceDurationMs / 2L)
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
            Text("▶", color = Color(0xFF6F6F7D), fontSize = 13.sp)
        }
    }
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
        clip.trackedObjectOverlays.isNotEmpty() ||
        clip.overlayText.isNotBlank()
