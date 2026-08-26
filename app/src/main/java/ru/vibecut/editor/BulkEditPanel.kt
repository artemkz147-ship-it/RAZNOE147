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
internal fun BulkEditPanel(
    clipCount: Int,
    onApplyVisualToAll: () -> Unit,
    onApplyTransitionToAll: () -> Unit,
    onMuteAll: () -> Unit,
    onUnmuteAll: () -> Unit,
    onResetVisualAll: () -> Unit,
) {
    SectionCard("Массовое редактирование") {
        Text(
            "Применить настройки выбранного клипа сразу ко всему проекту.",
            color = Color(0xFF9A9AA8),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ToolButton("Цвет и эффекты → всем", onApplyVisualToAll, enabled = clipCount > 1)
            ToolButton("Переход → всем", onApplyTransitionToAll, enabled = clipCount > 1)
            ToolButton("Выключить звук у всех", onMuteAll, enabled = clipCount > 0)
            ToolButton("Включить звук у всех", onUnmuteAll, enabled = clipCount > 0)
            ToolButton("Сбросить эффекты у всех", onResetVisualAll, enabled = clipCount > 0)
        }
    }
}
