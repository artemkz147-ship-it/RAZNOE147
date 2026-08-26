package ru.vibecut.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun PersonCutoutPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    val context = LocalContext.current
    val maker = remember { PersonCutoutMaker(context) }
    var background by remember { mutableStateOf(CutoutBackground.BLUR) }
    var threshold by remember { mutableFloatStateOf(.50f) }
    var feather by remember { mutableFloatStateOf(.18f) }
    var fps by remember { mutableIntStateOf(15) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(maker) { onDispose { maker.cancel() } }

    SectionCard("Вырезка человека из видео") {
        Text(
            "Локальная нейросеть отделяет человека на каждом кадре. Видео никуда не загружается.",
            color = Color(0xFF9A9AA8),
        )
        Text("Фон после вырезки", color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CutoutBackground.entries.forEach { item ->
                ChoiceButton(item.title, background == item) { if (!busy) background = item }
            }
        }

        Text("Точность границы: ${(threshold * 100).roundToInt()}%", color = Color.White)
        Slider(value=threshold,onValueChange={if(!busy)threshold=it},valueRange=.30f.. .72f,enabled=!busy)
        Text("Мягкость края: ${(feather * 100).roundToInt()}%", color = Color.White)
        Slider(value=feather,onValueChange={if(!busy)feather=it},valueRange=.05f.. .32f,enabled=!busy)

        Text("Частота обработки", color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(12,15,18,24).forEach { value ->
                ChoiceButton("$value кадров/с",fps==value){if(!busy)fps=value}
            }
        }

        Row(
            modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp),
        ) {
            ToolButton("Вырезать человека",{
                if(!busy){
                    onSnapshot()
                    busy=true
                    progress=0
                    status="Анализ кадров…"
                    maker.create(
                        clip=clip,
                        options=CutoutOptions(background=background,threshold=threshold,feather=feather,fps=fps,maxSide=720),
                        onProgress={progress=it;status=if(it<84)"Нейросеть обрабатывает кадры" else "Собирается MP4"},
                        onDone={result->busy=false;progress=100;status="Вырезка готова";onUpdate(result)},
                        onError={error->busy=false;status=error},
                    )
                }
            },enabled=!busy)
            ToolButton("Отменить обработку",{maker.cancel();busy=false;status="Обработка отменена"},enabled=busy)
        }
        if(busy){
            CircularProgressIndicator(progress={progress.coerceIn(0,100)/100f})
            Text("$progress% · $status",color=Color(0xFFC4B5FD))
        } else if(status.isNotBlank()) {
            Text(status,color=Color(0xFFB9B9C5))
        }
        Text(
            "Для длинного видео 24 кадра/с даёт более плавный край, но обрабатывается тяжелее. 12–15 кадров/с быстрее.",
            color=Color(0xFF777783),
        )
    }
}
