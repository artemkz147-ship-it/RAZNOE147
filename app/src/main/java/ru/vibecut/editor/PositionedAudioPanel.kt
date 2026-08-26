package ru.vibecut.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun PositionedAudioPanel(
    tracks: List<PositionedAudioTrack>,
    projectCursorMs: Long,
    onAddAtCursor: () -> Unit,
    onUpdate: (PositionedAudioTrack) -> Unit,
    onDelete: (String) -> Unit,
) {
    SectionCard("Дополнительные звуковые дорожки") {
        Text(
            "Курсор проекта: ${formatTime(projectCursorMs)}",
            color = Color.White,
        )
        Text(
            "Добавленный звук начнётся ровно с текущей позиции курсора.",
            color = Color(0xFF8F8F9C),
            modifier = Modifier.padding(top = 3.dp),
        )
        Spacer(Modifier.height(8.dp))
        ToolButton("Добавить звук с курсора", onAddAtCursor)

        if (tracks.isEmpty()) {
            Text(
                "Дополнительных дорожек пока нет",
                color = Color(0xFF8F8F9C),
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            Spacer(Modifier.height(10.dp))
            tracks.sortedBy { it.startAtMs }.forEachIndexed { index, track ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF1D1D24), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "${index + 1}. ${track.name}",
                        color = Color.White,
                    )
                    Text(
                        "Старт ${formatTime(track.startAtMs)} · длительность ${formatTime(track.sourceDurationMs)}",
                        color = Color(0xFF9A9AA8),
                    )
                    Text(
                        "Громкость: ${(track.volume * 100).roundToInt()}%",
                        color = Color(0xFFC4B5FD),
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    Slider(
                        value = track.volume.coerceIn(0f, 1f),
                        onValueChange = { onUpdate(track.copy(volume = it.coerceIn(0f, 1f))) },
                        valueRange = 0f..1f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        ToolButton("Удалить дорожку", { onDelete(track.id) })
                        ToolButton("Тише", { onUpdate(track.copy(volume = (track.volume - 0.1f).coerceAtLeast(0f))) })
                        ToolButton("Громче", { onUpdate(track.copy(volume = (track.volume + 0.1f).coerceAtMost(1f))) })
                    }
                }
            }
        }
    }
}
