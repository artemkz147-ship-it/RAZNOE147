package ru.vibecut.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class WorkspaceTab(val title: String, val glyph: String, val hint: String) {
    EDIT("Монтаж", "✂", "Резка, фото, автомонтаж"),
    LOOK("Картинка", "◐", "Цвет, стили, эффекты"),
    MOTION("Движение", "◆", "Переходы и ключи"),
    AUDIO("Звук", "♪", "Музыка и озвучка"),
    TEXT("Текст", "T", "Текст и субтитры"),
    LAYERS("Слои", "▱", "Стикеры, GIF и PiP"),
    AI("AI", "✦", "Вырезка и трекинг"),
    PROJECT("Проект", "☰", "Холст и экспорт"),
}

@Composable
internal fun ProEditorTopBar(
    projectName: String,
    clipCount: Int,
    exportState: ExportState,
    exportProgress: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0B10))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            MiniSquareButton("‹", onBack, true)
            OutlinedTextField(
                value = projectName,
                onValueChange = { onNameChange(it.take(60)) },
                modifier = Modifier.weight(1f).height(48.dp),
                textStyle = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                singleLine = true,
                placeholder = { Text("Название проекта") },
            )
            Button(
                onClick = onExport,
                enabled = clipCount > 0 && exportState != ExportState.EXPORTING,
                shape = RoundedCornerShape(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B5CF6),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF292632),
                ),
            ) {
                if (exportState == ExportState.EXPORTING) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(5.dp))
                    Text("$exportProgress%", fontWeight = FontWeight.Bold)
                } else {
                    Text("Экспорт", fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniSquareButton("↶", onUndo, canUndo)
                MiniSquareButton("↷", onRedo, canRedo)
                MiniSquareButton("＋", onImport, true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$clipCount клип.", color = Color(0xFF8E8E9B), fontSize = 11.sp)
                Text("● сохранено", color = Color(0xFF67E8A8), fontSize = 11.sp)
                if (exportState == ExportState.DONE) Text("● готово", color = Color(0xFF22D3EE), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MiniSquareButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(if (enabled) Color(0xFF181820) else Color(0xFF111116), RoundedCornerShape(13.dp))
            .border(1.dp, if (enabled) Color(0xFF292934) else Color(0xFF1A1A20), RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else Color(0xFF555560), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun WorkspaceTabBar(active: WorkspaceTab, onSelect: (WorkspaceTab) -> Unit) {
    val rows = listOf(
        listOf(WorkspaceTab.EDIT, WorkspaceTab.LOOK, WorkspaceTab.MOTION, WorkspaceTab.AUDIO),
        listOf(WorkspaceTab.TEXT, WorkspaceTab.LAYERS, WorkspaceTab.AI, WorkspaceTab.PROJECT),
    )
    Surface(color = Color(0xFF0E0E14), tonalElevation = 8.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            rows.forEach { tabs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    tabs.forEach { tab ->
                        val selected = tab == active
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    brush = if (selected) {
                                        Brush.verticalGradient(listOf(Color(0xFF3B2768), Color(0xFF211B36)))
                                    } else {
                                        Brush.verticalGradient(listOf(Color(0xFF17171E), Color(0xFF15151B)))
                                    },
                                    shape = RoundedCornerShape(13.dp),
                                )
                                .border(1.dp, if (selected) Color(0xFF9B7CF7) else Color(0xFF23232C), RoundedCornerShape(13.dp))
                                .clickable { onSelect(tab) }
                                .padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                tab.glyph,
                                color = if (selected) Color(0xFFD8C8FF) else Color(0xFFB7B7C2),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                tab.title,
                                color = if (selected) Color.White else Color(0xFFAAAAB6),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkspaceSectionHeader(tab: WorkspaceTab, selectedClip: VideoClip, cursorMs: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tab.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(tab.hint, color = Color(0xFF8E8E9C), fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                selectedClip.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFFCBCBD6),
                fontSize = 11.sp,
                modifier = Modifier.width(130.dp),
            )
            Text(formatTime(cursorMs), color = Color(0xFF9B7CF7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun EditorMessageBar(message: String) {
    AnimatedVisibility(
        visible = message.isNotBlank(),
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2,
        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 2,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(Color(0xFF15151D), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF252531), RoundedCornerShape(12.dp))
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(Color(0xFF22D3EE), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(message, color = Color(0xFFC8C8D3), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
