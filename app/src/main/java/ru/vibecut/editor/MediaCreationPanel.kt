package ru.vibecut.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun MediaCreationPanel(
    busy: Boolean,
    onAddPhoto: (Long) -> Unit,
    onFreezeFrame: (Long) -> Unit,
) {
    var durationMs by remember { mutableLongStateOf(3000L) }

    SectionCard("Фото и стоп-кадр") {
        Text(
            "Изображение превращается в обычный видеоклип и дальше редактируется на таймлайне.",
            color = Color(0xFF9A9AA8),
        )
        Text("Длительность нового клипа", color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(1000L, 2000L, 3000L, 5000L, 10_000L).forEach { ms ->
                ChoiceButton(
                    text = if (ms < 10_000L) "${ms / 1000} с" else "10 с",
                    selected = durationMs == ms,
                ) { durationMs = ms }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ToolButton("Добавить фото", { onAddPhoto(durationMs) }, enabled = !busy)
            ToolButton("Стоп-кадр из курсора", { onFreezeFrame(durationMs) }, enabled = !busy)
        }
        if (busy) Text("Создаётся клип…", color = Color(0xFFC4B5FD))
    }
}
