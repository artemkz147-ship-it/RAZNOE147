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
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun MotionPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    var editingStrength by remember { mutableStateOf(false) }
    var editingMask by remember { mutableStateOf(false) }
    var editingVignette by remember { mutableStateOf(false) }

    SectionCard("Анимация клипа") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
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

    SectionCard("Маска и виньетка") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            MaskType.entries.forEach { mask ->
                ChoiceButton(mask.title, clip.maskType == mask) {
                    onSnapshot()
                    onUpdate(clip.copy(maskType = mask))
                }
            }
        }

        if (clip.maskType != MaskType.NONE) {
            Text(
                "Размер маски: ${(clip.maskSize * 100).roundToInt()}%",
                color = Color.White,
            )
            Slider(
                value = clip.maskSize.coerceIn(0.25f, 1f),
                onValueChange = {
                    if (!editingMask) {
                        onSnapshot()
                        editingMask = true
                    }
                    onUpdate(clip.copy(maskSize = it))
                },
                onValueChangeFinished = { editingMask = false },
                valueRange = 0.25f..1f,
            )
        }

        Text(
            "Виньетка: ${(clip.vignette * 100).roundToInt()}%",
            color = Color.White,
        )
        Slider(
            value = clip.vignette.coerceIn(0f, 1f),
            onValueChange = {
                if (!editingVignette) {
                    onSnapshot()
                    editingVignette = true
                }
                onUpdate(clip.copy(vignette = it))
            },
            onValueChangeFinished = { editingVignette = false },
            valueRange = 0f..1f,
        )
        ToolButton(
            text = "Сбросить маску",
            onClick = {
                onSnapshot()
                onUpdate(clip.copy(maskType = MaskType.NONE, maskSize = 0.82f, vignette = 0f))
            },
        )
    }

    CreativeStylePanel(clip, onSnapshot, onUpdate)
    PersonCutoutPanel(clip, onSnapshot, onUpdate)
}
