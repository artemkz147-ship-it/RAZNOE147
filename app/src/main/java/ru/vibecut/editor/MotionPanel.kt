package ru.vibecut.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

@Composable
internal fun MotionPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    var editingStrength by remember { mutableStateOf(false) }

    SectionCard("Анимация клипа") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(7f)),
        ) {
            ClipMotion.entries.forEach { motion ->
                ChoiceButton(motion.title, clip.motion == motion) {
                    onSnapshot()
                    onUpdate(clip.copy(motion = motion))
                }
            }
        }

        if (clip.motion != ClipMotion.NONE) {
            Text(
                "Сила анимации: ${(clip.motionStrength * 100).roundToInt()}%",
                color = Color.White,
            )
            Slider(
                value = clip.motionStrength.coerceIn(0.03f, 0.35f),
                onValueChange = {
                    if (!editingStrength) {
                        onSnapshot()
                        editingStrength = true
                    }
                    onUpdate(clip.copy(motionStrength = it))
                },
                onValueChangeFinished = { editingStrength = false },
                valueRange = 0.03f..0.35f,
            )
            Text(
                "Анимация рассчитывается по времени каждого кадра и сохраняется в экспорт.",
                color = Color(0xFF8F8F9C),
            )
        }
    }
}
