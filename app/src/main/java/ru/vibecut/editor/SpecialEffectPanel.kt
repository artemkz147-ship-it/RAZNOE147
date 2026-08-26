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
internal fun SpecialEffectPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    SectionCard("Спецэффекты") {
        Text(
            "Покадровые эффекты для видео и фото-клипов. Движение, шум, полосы и вспышки попадают в итоговый экспорт.",
            color = Color(0xFF9A9AA8),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpecialEffect.entries.forEach { effect ->
                ChoiceButton(effect.title, clip.specialEffect == effect) {
                    onSnapshot()
                    onUpdate(clip.copy(specialEffect = effect))
                }
            }
        }
        if (clip.specialEffect != SpecialEffect.NONE) {
            Text("Сила: ${(clip.specialEffectStrength * 100).roundToInt()}%", color = Color.White)
            Slider(
                value = clip.specialEffectStrength.coerceIn(0f, 1f),
                onValueChange = {
                    if (!editing) { onSnapshot(); editing = true }
                    onUpdate(clip.copy(specialEffectStrength = it))
                },
                onValueChangeFinished = { editing = false },
                valueRange = 0f..1f,
            )
            ToolButton("Сбросить спецэффект", {
                onSnapshot()
                onUpdate(clip.copy(specialEffect = SpecialEffect.NONE, specialEffectStrength = .65f))
            })
        }
    }
}
