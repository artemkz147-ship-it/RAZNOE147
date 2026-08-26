package ru.vibecut.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun RhythmMontagePanel(
    music: AudioTrack?,
    beatMap: BeatMap?,
    analyzing: Boolean,
    onAnalyze: () -> Unit,
    onApply: (AutoMontageStyle) -> Unit,
) {
    SectionCard("Автомонтаж под музыку") {
        if (music == null) {
            Text(
                "Сначала выберите фоновую музыку. VibeCut режет клипы по реально найденным ударам, а не по случайному таймеру.",
                color = Color(0xFF9A9AA8),
            )
            return@SectionCard
        }

        Text("Музыка: ${music.name}", color = Color.White)
        when {
            analyzing -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Анализируется ритм и акценты…", color = Color(0xFFC4B5FD))
                }
            }
            beatMap != null -> {
                val confidence = (beatMap.confidence * 100f).roundToInt()
                Text(
                    "${beatMap.bpm} BPM · битов: ${beatMap.beats.size} · сильных акцентов: ${beatMap.strongBeatCount} · уверенность: $confidence%",
                    color = Color(0xFF86EFAC),
                )
                Text(
                    "Точки склеек строятся по карте ритма. «Спокойно» берёт редкие сильные акценты, «Короткий ролик» — более плотную сетку.",
                    color = Color(0xFF9A9AA8),
                )
            }
            else -> Text(
                "Музыка ещё не проанализирована.",
                color = Color(0xFF9A9AA8),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ToolButton(
                text = if (beatMap == null) "Найти биты" else "Пересчитать биты",
                onClick = onAnalyze,
                enabled = !analyzing,
            )
            AutoMontageStyle.entries.forEach { style ->
                ToolButton(
                    text = style.title,
                    onClick = { onApply(style) },
                    enabled = !analyzing,
                )
            }
        }
    }
}
