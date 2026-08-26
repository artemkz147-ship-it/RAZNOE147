package ru.vibecut.editor

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun VideoOverlayPanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    val context = LocalContext.current
    val maker = remember { VideoOverlayMaker(context) }
    var scale by remember { mutableFloatStateOf(0.38f) }
    var alpha by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var x by remember { mutableFloatStateOf(0.62f) }
    var y by remember { mutableFloatStateOf(0.62f) }
    var startAt by remember(clip.id) { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(PipOptions()) }

    DisposableEffect(maker) { onDispose { maker.cancel() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "второе видео"
            onSnapshot()
            busy = true
            status = "Создаётся картинка-в-картинке"
            maker.create(
                base = clip,
                overlayUri = uri,
                overlayName = name,
                options = pending,
                onDone = {
                    busy = false
                    status = "Видео поверх видео готово"
                    onUpdate(it)
                },
                onError = {
                    busy = false
                    status = it
                },
            )
        }
    }

    SectionCard("Видео поверх видео") {
        Text(
            "Второй ролик реально композится поверх выбранного клипа. После обработки результат остаётся обычным редактируемым MP4.",
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
        val maxStart = (clip.durationMs - 100L).coerceAtLeast(0L).toFloat()
        Text("Начало наложения: ${formatTime(startAt.toLong())}", color = Color.White)
        Slider(
            value = startAt.coerceIn(0f, maxStart.coerceAtLeast(1f)),
            onValueChange = { startAt = it },
            valueRange = 0f..maxStart.coerceAtLeast(1f),
            enabled = maxStart > 0f,
        )
        ToolButton(
            text = "Выбрать второе видео",
            onClick = {
                pending = PipOptions(
                    x = x,
                    y = y,
                    scale = scale,
                    alpha = alpha,
                    rotation = rotation,
                    startAtMs = startAt.toLong(),
                )
                picker.launch(arrayOf("video/*"))
            },
            enabled = !busy,
        )
        if (busy) Text("Обрабатывается видео…", color = Color(0xFFC4B5FD))
        if (status.isNotBlank()) Text(status, color = Color(0xFFB9B9C5))
    }
}
