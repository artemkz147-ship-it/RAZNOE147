package ru.vibecut.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun BackgroundMusicQuickPanel(
    music: AudioTrack?,
    beatMap: BeatMap?,
    beatBusy: Boolean,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    onVolume: (AudioTrack) -> Unit,
    onAnalyze: () -> Unit,
) {
    SectionCard("Фоновая музыка") {
        Text(
            music?.let { "${it.name} · ${(it.volume * 100).roundToInt()}%" } ?: "Музыка не выбрана",
            color = Color.White,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ToolButton(if (music == null) "Добавить музыку" else "Заменить музыку", onChoose)
            ToolButton("Анализ ритма", onAnalyze, enabled = music != null && !beatBusy)
            ToolButton("Убрать", onRemove, enabled = music != null)
        }
        music?.let { track ->
            Slider(
                value = track.volume.coerceIn(0f, 1f),
                onValueChange = { onVolume(track.copy(volume = it)) },
                valueRange = 0f..1f,
            )
        }
        if (beatBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Ищу биты и сильные акценты…", color = Color(0xFFB9B9C5))
        } else beatMap?.let { map ->
            Text(
                "${map.bpm} BPM · ${map.beats.size} точек ритма · ${map.strongBeatCount} сильных акцентов",
                color = Color(0xFF67E8A8),
            )
        }
    }
}
