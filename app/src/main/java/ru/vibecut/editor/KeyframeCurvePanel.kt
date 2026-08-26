package ru.vibecut.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun KeyframeCurvePanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    SectionCard("Кривые ключевых кадров") {
        Text(
            "Тип кривой задаётся для движения от выбранного ключа к следующему: позиция, масштаб и поворот используют одну интерполяцию.",
            color = Color(0xFF9A9AA8),
        )
        if (clip.keyframes.isEmpty()) {
            Text("Добавьте ключевые кадры выше.", color = Color(0xFF777783))
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyframeEasing.entries.forEach { easing ->
                    ToolButton("Все: ${easing.title}", {
                        onSnapshot()
                        onUpdate(clip.copy(keyframes = clip.keyframes.map { it.copy(easing = easing) }))
                    })
                }
            }
            clip.keyframes.sortedBy { it.timeMs }.forEachIndexed { index, frame ->
                Text(
                    "Ключ ${index + 1} · ${formatTime(frame.timeMs)} → ${frame.easing.title}",
                    color = Color.White,
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    KeyframeEasing.entries.forEach { easing ->
                        ChoiceButton(easing.title, frame.easing == easing) {
                            onSnapshot()
                            onUpdate(
                                clip.copy(
                                    keyframes = clip.keyframes.map {
                                        if (it.id == frame.id) it.copy(easing = easing) else it
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
