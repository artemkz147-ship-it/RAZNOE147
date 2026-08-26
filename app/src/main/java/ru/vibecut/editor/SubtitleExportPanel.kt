package ru.vibecut.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun SubtitleExportPanel(cues: List<SubtitleCue>) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(SrtTools.encode(cues))
                } ?: error("Не удалось открыть файл")
            }.isSuccess
            status = if (ok) "SRT сохранён" else "Не удалось сохранить SRT"
        }
    }

    SectionCard("Экспорт субтитров") {
        Text(
            "Сохраните текущие субтитры отдельным SRT-файлом для другого редактора или публикации.",
            color = Color(0xFF9A9AA8),
        )
        ToolButton(
            "Сохранить SRT",
            { launcher.launch("VibeCut_subtitles.srt") },
            enabled = cues.isNotEmpty(),
        )
        if (status.isNotBlank()) Text(status, color = Color(0xFFB9B9C5))
    }
}
