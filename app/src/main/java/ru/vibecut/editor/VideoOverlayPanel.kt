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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun VideoOverlayPanel(
    busy: Boolean,
    cursorOutputMs: Long,
    onChooseVideo: (PipOptions) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(0.38f) }
    var alpha by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var x by remember { mutableFloatStateOf(0.62f) }
    var y by remember { mutableFloatStateOf(0.62f) }

    SectionCard("Видео поверх видео") {
        Text(
            "Добавляет второй ролик поверх выбранного клипа и создаёт обычный редактируемый MP4.",
            color = Color(0xFF9A9AA8),
        )
        Text("Положение", color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                Triple("Слева сверху", -0.62f, 0.62f),
                Triple("Справа сверху", 0.62f, 0.62f),
                Triple("По центру", 0f, 0f),
                Triple("Слева снизу", -0.62f, -0.62f),
                Triple("Справа снизу", 0.62f, -0.62f),
            ).forEach { (name, px, py) ->
                ChoiceButton(name, kotlin.math.abs(x - px) < .01f && kotlin.math.abs(y - py) < .01f) {
                    x = px
                    y = py
                }
            }
        }
        Text("Размер: ${(scale * 100).roundToInt()}%", color = Color.White)
        Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.18f..0.8f)
        Text("Прозрачность: ${(alpha * 100).roundToInt()}%", color = Color.White)
        Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0.2f..1f)
        Text("Поворот: ${rotation.roundToInt()}°", color = Color.White)
        Slider(value = rotation, onValueChange = { rotation = it }, valueRange = -45f..45f)
        Text("Точка курсора: ${formatTime(cursorOutputMs)}", color = Color(0xFFB9B9C5))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ToolButton(
                text = "С начала клипа",
                onClick = {
                    onChooseVideo(PipOptions(x=x,y=y,scale=scale,alpha=alpha,rotation=rotation,startAtMs=0L))
                },
                enabled = !busy,
            )
            ToolButton(
                text = "С текущего курсора",
                onClick = {
                    onChooseVideo(PipOptions(x=x,y=y,scale=scale,alpha=alpha,rotation=rotation,startAtMs=cursorOutputMs))
                },
                enabled = !busy,
            )
        }
        if (busy) Text("Создаётся наложение видео…", color = Color(0xFFC4B5FD))
    }
}
