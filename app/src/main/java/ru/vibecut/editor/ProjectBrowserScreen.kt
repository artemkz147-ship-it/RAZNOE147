package ru.vibecut.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectBrowserScreen(onOpen:(String)->Unit){
    val context=LocalContext.current;var name by remember{mutableStateOf("")};var projects by remember{mutableStateOf(ProjectStore.list(context))};fun refresh(){projects=ProjectStore.list(context)}
    Column(Modifier.fillMaxSize().background(Color(0xFF09090C)).padding(horizontal=14.dp)){
        Text("VibeCut",color=Color.White,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.padding(top=18.dp));Text("Полноценный монтаж на телефоне · проекты хранятся локально",color=Color(0xFF9A9AA8),modifier=Modifier.padding(bottom=14.dp))
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF15151A)),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(12.dp)){Text("Новый проект",color=Color.White,style=MaterialTheme.typography.titleMedium);OutlinedTextField(name,{name=it.take(60)},Modifier.fillMaxWidth(),label={Text("Название")},placeholder={Text("Например: Отпуск 2026")},singleLine=true);Button(onClick={val p=ProjectStore.create(context,name);name="";refresh();onOpen(p.id)},modifier=Modifier.padding(top=7.dp)){Text("Создать проект")}}}
        Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("Мои проекты",color=Color.White,style=MaterialTheme.typography.titleLarge);Text("${projects.size}",color=Color(0xFF9A9AA8))}
        if(projects.isEmpty())Column(Modifier.fillMaxWidth().padding(vertical=36.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("Пока нет проектов",color=Color(0xFFB9B9C5));Text("Создайте первый проект выше",color=Color(0xFF777783))}
        else LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){items(projects,key={it.id}){p->Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF15151A)),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(12.dp)){Text(p.name,color=Color.White,style=MaterialTheme.typography.titleMedium);Text("Клипов: ${p.clipCount} · ${formatDuration(p.durationMs)} · ${formatDate(p.updatedAt)}",color=Color(0xFF9A9AA8));Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){Button(onClick={onOpen(p.id)}){Text("Открыть")};Button(onClick={ProjectStore.duplicate(context,p.id);refresh()},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF292934))){Text("Копия")};Button(onClick={ProjectStore.delete(context,p.id);refresh()},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF4A242B))){Text("Удалить")}}}}};item{Spacer(Modifier.height(24.dp))}}
    }
}
private fun formatDuration(ms:Long):String{val s=ms.coerceAtLeast(0)/1000;val m=s/60;return if(m>0)"$m мин ${s%60} сек" else "$s сек"}
private fun formatDate(t:Long)=SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(Date(t))
