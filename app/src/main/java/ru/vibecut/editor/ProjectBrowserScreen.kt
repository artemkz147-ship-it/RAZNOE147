package ru.vibecut.editor

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectBrowserScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf(ProjectStore.list(context)) }
    var pendingExportId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ProjectSummary?>(null) }
    var status by remember { mutableStateOf("") }

    fun refresh() { projects = ProjectStore.list(context) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val id = pendingExportId
        pendingExportId = null
        if (uri != null && id != null) {
            val raw = ProjectBackup.exportProject(context, id)
            val ok = raw != null && runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(raw) }
            }.isSuccess
            status = if (ok) "Резервная копия сохранена" else "Не удалось сохранить резервную копию"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val importedId = raw?.let { ProjectBackup.importProject(context, it) }
            if (importedId != null) {
                refresh()
                status = "Проект импортирован"
            } else status = "Не удалось импортировать проект"
        }
    }

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить проект?") },
            text = { Text("«${project.name}» будет удалён из VibeCut. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    ProjectStore.delete(context, project.id)
                    pendingDelete = null
                    refresh()
                    status = "Проект удалён"
                }) { Text("Удалить", color = Color(0xFFFF7A87)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
    }

    val filtered = projects.filter { it.name.contains(query.trim(), ignoreCase = true) }

    Column(Modifier.fillMaxSize().background(Color(0xFF08080C))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF171126), Color(0xFF08080C))),
                        )
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("VibeCut", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                            Text("Мобильная монтажная студия", color = Color(0xFFAAA4B8), fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(Color(0xFF8B5CF6), RoundedCornerShape(15.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("V", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FeaturePill("Биты")
                        FeaturePill("AI")
                        FeaturePill("4K")
                        FeaturePill("Офлайн")
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121219)),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Новый проект", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Начните монтаж или восстановите резервную копию", color = Color(0xFF8F8F9B), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 9.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(60) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Название, например «Отпуск»") },
                            singleLine = true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Button(
                                onClick = {
                                    val project = ProjectStore.create(context, name)
                                    name = ""
                                    refresh()
                                    onOpen(project.id)
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(15.dp),
                            ) {
                                Text("＋ Создать", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24242E)),
                            ) { Text("Импорт") }
                        }
                        if (status.isNotBlank()) {
                            Text(status, color = Color(0xFF67E8A8), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Мои проекты", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${projects.size}", color = Color(0xFF8E8E9A), fontSize = 12.sp)
                }
                if (projects.size > 3) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        placeholder = { Text("Поиск проекта") },
                        singleLine = true,
                        leadingIcon = { Text("⌕", color = Color(0xFFAAAAB6), fontSize = 20.sp) },
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (projects.isEmpty()) "Первый монтаж начинается здесь" else "Ничего не найдено", color = Color(0xFFD0D0D8), fontWeight = FontWeight.SemiBold)
                        Text(if (projects.isEmpty()) "Создайте проект и добавьте видео или фото" else "Попробуйте другое название", color = Color(0xFF777783), fontSize = 11.sp)
                    }
                }
            } else {
                items(filtered, key = { it.id }) { project ->
                    ProjectLibraryCard(
                        project = project,
                        onOpen = { onOpen(project.id) },
                        onDuplicate = { ProjectStore.duplicate(context, project.id); refresh(); status = "Создана копия проекта" },
                        onBackup = {
                            pendingExportId = project.id
                            exportLauncher.launch("${safeProjectName(project.name)}.vibecut.json")
                        },
                        onDelete = { pendingDelete = project },
                    )
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun FeaturePill(text: String) {
    Text(
        text,
        color = Color(0xFFD7CCF9),
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(Color(0xFF251C39), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3C2B61), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun ProjectLibraryCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onBackup: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val firstClip = remember(project.id, project.updatedAt) { ProjectStore.load(context, project.id)?.clips?.firstOrNull() }

    Card(
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121219)),
        shape = RoundedCornerShape(19.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF1D1D27)),
                contentAlignment = Alignment.Center,
            ) {
                if (firstClip != null) ProjectThumbnail(firstClip, Modifier.fillMaxSize())
                else Text("＋", color = Color(0xFF595967), fontSize = 24.sp)
                Text(
                    formatDuration(project.durationMs),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).background(Color(0xC8000000), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(project.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${project.clipCount} клип. · ${formatDate(project.updatedAt)}", color = Color(0xFF8C8C98), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    SmallProjectAction("Открыть", Color(0xFF36265D), onOpen)
                    SmallProjectAction("Копия", Color(0xFF23232C), onDuplicate)
                    SmallProjectAction("⋯", Color(0xFF23232C), onBackup)
                    SmallProjectAction("×", Color(0xFF3A2026), onDelete)
                }
            }
        }
    }
}

@Composable
private fun SmallProjectAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun ProjectThumbnail(clip: VideoClip, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, clip.uri, clip.trimStartMs, clip.trimEndMs) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(clip.uri)
                if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(context, uri)
                val frameMs = clip.trimStartMs + clip.sourceSliceDurationMs / 2L
                retriever.getFrameAtTime(frameMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(Color(0xFF202029)), contentAlignment = Alignment.Center) {
            Text("▶", color = Color(0xFF656572), fontSize = 15.sp)
        }
    }
}

private fun safeProjectName(name: String): String = name
    .replace(Regex("[^A-Za-zА-Яа-яЁё0-9 _.-]"), "_")
    .trim()
    .ifBlank { "VibeCut_project" }

private fun formatDuration(ms: Long): String {
    val s = ms.coerceAtLeast(0L) / 1000L
    val m = s / 60L
    return if (m > 0L) "$m:${(s % 60L).toString().padStart(2, '0')}" else "0:${s.toString().padStart(2, '0')}"
}

private fun formatDate(t: Long): String =
    SimpleDateFormat("dd.MM · HH:mm", Locale.getDefault()).format(Date(t))
