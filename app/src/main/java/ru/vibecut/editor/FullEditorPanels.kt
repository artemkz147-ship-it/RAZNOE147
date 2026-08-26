package ru.vibecut.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.roundToInt

enum class AutoMontageStyle(val title:String){DYNAMIC("Динамично"),CALM("Спокойно"),TRAVEL("Путешествие"),REELS("Короткий ролик")}

@Composable internal fun FullEditorHeader(projectName:String,clipCount:Int,exportState:ExportState,exportProgress:Int,onBack:()->Unit,onNameChange:(String)->Unit,onImport:()->Unit,onExport:()->Unit){
    Column(Modifier.fillMaxWidth().padding(12.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick=onBack,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF26262E))){Text("Проекты")}
            OutlinedTextField(projectName,{onNameChange(it.take(60))},Modifier.weight(1f),singleLine=true,label={Text("Название проекта")})
        }
        Row(Modifier.fillMaxWidth().padding(top=7.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text(if(clipCount==0)"Новый монтаж" else "Клипов: $clipCount",color=Color(0xFF9A9AA8))
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){Button(onClick=onImport,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF26262E))){Text("Добавить видео")};Button(onClick=onExport,enabled=clipCount>0&&exportState!=ExportState.EXPORTING){if(exportState==ExportState.EXPORTING){CircularProgressIndicator(strokeWidth=2.dp);Text(" $exportProgress%") }else Text("Экспорт")}}
        }
    }
}

@Composable internal fun AutoMontagePanel(onApply:(AutoMontageStyle)->Unit)=SectionCard("Монтаж в один тап"){
    Text("Готовая комбинация переходов, движения и цветокоррекции. После применения всё можно менять вручную.",color=Color(0xFF9A9AA8));Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){AutoMontageStyle.entries.forEach{ToolButton(it.title){onApply(it)}}}
}

@Composable internal fun ColorEffectsPanel(clip:VideoClip,onSnapshot:()->Unit,onUpdate:(VideoClip)->Unit){var edit by remember{mutableStateOf(false)};SectionCard("Цветовые эффекты"){
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){ColorEffect.entries.forEach{e->ChoiceButton(e.title,clip.colorEffect==e){onSnapshot();onUpdate(clip.copy(colorEffect=e))}}}
    fun slider(title:String,value:Float,update:(Float)->VideoClip){Text("$title: ${(value*100).roundToInt()}%",color=Color.White);Slider(value.coerceIn(0f,2f),{if(!edit){onSnapshot();edit=true};onUpdate(update(it))},onValueChangeFinished={edit=false},valueRange=0f..2f)}
    slider("Красный канал",clip.redScale){clip.copy(redScale=it)};slider("Зелёный канал",clip.greenScale){clip.copy(greenScale=it)};slider("Синий канал",clip.blueScale){clip.copy(blueScale=it)}
    ToolButton("Сбросить"){onSnapshot();onUpdate(clip.copy(colorEffect=ColorEffect.NONE,redScale=1f,greenScale=1f,blueScale=1f))}
}}

@Composable internal fun TransitionPanel(clip:VideoClip,hasNext:Boolean,onSnapshot:()->Unit,onUpdate:(VideoClip)->Unit){var edit by remember{mutableStateOf(false)};SectionCard("Переход к следующему клипу"){
    if(!hasNext)Text("Это последний клип.",color=Color(0xFF9A9AA8));Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){TransitionType.entries.forEach{t->ChoiceButton(t.title,clip.transitionOut==t){if(hasNext||t==TransitionType.NONE){onSnapshot();onUpdate(clip.copy(transitionOut=t))}}}}
    if(clip.transitionOut!=TransitionType.NONE){Text("Длительность: ${"%.1f".format(clip.transitionDurationMs/1000f)} с",color=Color.White);Slider(clip.transitionDurationMs.toFloat().coerceIn(200f,2000f),{if(!edit){onSnapshot();edit=true};onUpdate(clip.copy(transitionDurationMs=it.toLong()))},onValueChangeFinished={edit=false},valueRange=200f..2000f)}
}}

@Composable internal fun KeyframePanel(clip:VideoClip,positionMs:Long,onSnapshot:()->Unit,onUpdate:(VideoClip)->Unit){var selectedId by remember(clip.id){mutableStateOf<String?>(clip.keyframes.firstOrNull()?.id)};var edit by remember{mutableStateOf(false)};val frames=clip.keyframes.sortedBy{it.timeMs};val selected=frames.firstOrNull{it.id==selectedId};fun update(f:TransformKeyframe)=onUpdate(clip.copy(keyframes=clip.keyframes.map{if(it.id==f.id)f else it}));SectionCard("Ключевые кадры"){
    Text("Курсор: ${formatTime(positionMs)} · плавная интерполяция X/Y/масштаба/поворота",color=Color(0xFF9A9AA8));Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){ToolButton("Добавить в курсоре"){onSnapshot();val f=TransformKeyframe(UUID.randomUUID().toString(),positionMs.coerceIn(0L,clip.sourceSliceDurationMs));onUpdate(clip.copy(keyframes=(clip.keyframes+f).sortedBy{it.timeMs}));selectedId=f.id};ToolButton("Очистить все",{onSnapshot();onUpdate(clip.copy(keyframes=emptyList()));selectedId=null},clip.keyframes.isNotEmpty())}
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){frames.forEachIndexed{i,f->ChoiceButton("${i+1} · ${formatTime(f.timeMs)}",f.id==selectedId){selectedId=f.id}}}
    selected?.let{f->fun sf(title:String,value:Float,range:ClosedFloatingPointRange<Float>,change:(Float)->TransformKeyframe){Text("$title: ${if(title=="Масштаб")"${(value*100).roundToInt()}%" else value.roundToInt().toString()}",color=Color.White);Slider(value.coerceIn(range.start,range.endInclusive),{if(!edit){onSnapshot();edit=true};update(change(it))},onValueChangeFinished={edit=false},valueRange=range)};sf("X",f.x,-1f..1f){f.copy(x=it)};sf("Y",f.y,-1f..1f){f.copy(y=it)};sf("Масштаб",f.scale,.25f..3f){f.copy(scale=it)};sf("Поворот",f.rotation,-180f..180f){f.copy(rotation=it)};ToolButton("Удалить выбранный"){onSnapshot();onUpdate(clip.copy(keyframes=clip.keyframes.filterNot{it.id==f.id}));selectedId=null}}
}}

@Composable internal fun StickerPanel(clip:VideoClip,onChooseSticker:()->Unit,onSnapshot:()->Unit,onUpdate:(VideoClip)->Unit)=SectionCard("Изображения и стикеры"){
    ToolButton("Добавить PNG / JPG / WebP",onChooseSticker);if(clip.stickers.isEmpty())Text("Наложений нет",color=Color(0xFF8F8F9C));clip.stickers.forEachIndexed{i,s->Column(Modifier.fillMaxWidth().padding(top=8.dp).background(Color(0xFF1D1D24),RoundedCornerShape(12.dp)).padding(10.dp)){Text("${i+1}. ${s.name}",color=Color.White);fun sl(title:String,v:Float,r:ClosedFloatingPointRange<Float>,c:(Float)->StickerLayer){Text("$title: ${(v*100).roundToInt()}",color=Color(0xFFC4B5FD));Slider(v.coerceIn(r.start,r.endInclusive),{x->onUpdate(clip.copy(stickers=clip.stickers.map{if(it.id==s.id)c(x) else it}))},valueRange=r)};sl("X",s.x,-1f..1f){s.copy(x=it)};sl("Y",s.y,-1f..1f){s.copy(y=it)};sl("Размер",s.scale,.05f..2f){s.copy(scale=it)};sl("Поворот",s.rotation,-180f..180f){s.copy(rotation=it)};sl("Прозрачность",s.alpha,0f..1f){s.copy(alpha=it)};ToolButton("Удалить"){onSnapshot();onUpdate(clip.copy(stickers=clip.stickers.filterNot{it.id==s.id}))}}}
}

@Composable internal fun SubtitlePanel(cues:List<SubtitleCue>,style:SubtitleStyle,projectCursorMs:Long,onImportSrt:()->Unit,onAddCue:(String)->Unit,onDeleteCue:(String)->Unit,onClear:()->Unit,onStyleChange:(SubtitleStyle)->Unit){var draft by remember{mutableStateOf("")};SectionCard("Субтитры"){
    Text("Курсор проекта: ${formatTime(projectCursorMs)} · строк: ${cues.size}",color=Color.White);Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){ToolButton("Импорт SRT",onImportSrt);ToolButton("Очистить",onClear,cues.isNotEmpty())};OutlinedTextField(draft,{draft=it.take(180)},Modifier.fillMaxWidth(),label={Text("Новая строка")},maxLines=3);ToolButton("Добавить на 2 секунды",{if(draft.isNotBlank()){onAddCue(draft.trim());draft=""}},draft.isNotBlank());Text("Размер: ${(style.fontScale*100).roundToInt()}%",color=Color.White);Slider(style.fontScale,{onStyleChange(style.copy(fontScale=it))},valueRange=.55f..2.2f);Text("Положение: ${(style.verticalPosition*100).roundToInt()}%",color=Color.White);Slider(style.verticalPosition,{onStyleChange(style.copy(verticalPosition=it))},valueRange=.55f..0.94f);Row(verticalAlignment=Alignment.CenterVertically){Text("Подложка",color=Color.White);Switch(style.backgroundEnabled,{onStyleChange(style.copy(backgroundEnabled=it))})};Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Белый" to 0xFFFFFFFF.toInt(),"Жёлтый" to 0xFFFFD54F.toInt(),"Голубой" to 0xFF40C4FF.toInt(),"Зелёный" to 0xFF69F0AE.toInt()).forEach{(n,c)->ChoiceButton(n,style.textColor==c){onStyleChange(style.copy(textColor=c))}}};cues.sortedBy{it.startMs}.takeLast(8).forEach{cue->Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically){Text("${formatTime(cue.startMs)}–${formatTime(cue.endMs)}  ${cue.text}",color=Color(0xFFD4D4DB),modifier=Modifier.weight(1f),maxLines=2);ToolButton("Удалить"){onDeleteCue(cue.id)}}}
}}

@Composable internal fun VoiceoverPanel(isRecording:Boolean,projectCursorMs:Long,onStart:()->Unit,onStop:()->Unit)=SectionCard("Озвучка"){
    Text(if(isRecording)"Идёт запись с ${formatTime(projectCursorMs)}" else "Запись голоса с текущей позиции курсора",color=if(isRecording)Color(0xFFFF8A80) else Color(0xFF9A9AA8));if(isRecording)ToolButton("Остановить и добавить",onStop) else ToolButton("Начать запись",onStart)
}

@Composable internal fun AdvancedProjectPanel(backgroundAudio:AudioTrack?,exportSettings:ExportSettings,onChooseMusic:()->Unit,onRemoveMusic:()->Unit,onBackgroundAudioChange:(AudioTrack)->Unit,onExportSettings:(ExportSettings)->Unit)=SectionCard("Музыка, холст и экспорт"){
    Text(if(backgroundAudio==null)"Фоновая музыка: не выбрана" else "Фоновая музыка: ${backgroundAudio.name}",color=Color.White);Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){ToolButton("Выбрать музыку",onChooseMusic);ToolButton("Убрать музыку",onRemoveMusic,backgroundAudio!=null)};backgroundAudio?.let{Text("Громкость: ${(it.volume*100).roundToInt()}%",color=Color.White);Slider(it.volume,{v->onBackgroundAudioChange(it.copy(volume=v))},valueRange=0f..1f)}
    Text("Холст",color=Color(0xFF9A9AA8));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Исходный" to null,"16:9" to 16f/9f,"9:16" to 9f/16f,"1:1" to 1f,"4:5" to 4f/5f,"3:4" to 3f/4f).forEach{(n,r)->val selected=if(r==null)exportSettings.aspectRatio==null else exportSettings.aspectRatio?.let{kotlin.math.abs(it-r)<.001f}==true;ChoiceButton(n,selected){onExportSettings(exportSettings.copy(aspectRatio=r))}}};if(exportSettings.aspectRatio!=null)Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){ChoiceButton("Вписать",!exportSettings.cropToFill){onExportSettings(exportSettings.copy(cropToFill=false))};ChoiceButton("Заполнить",exportSettings.cropToFill){onExportSettings(exportSettings.copy(cropToFill=true))}}
    Text("Разрешение",color=Color(0xFF9A9AA8));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf(480,720,1080,1440,2160).forEach{h->ChoiceButton(if(h==2160)"4K" else "${h}p",exportSettings.height==h){onExportSettings(exportSettings.copy(height=h))}}};Text("Частота кадров",color=Color(0xFF9A9AA8));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf(24,25,30,50,60).forEach{f->ChoiceButton("$f FPS",exportSettings.maxFrameRate==f){onExportSettings(exportSettings.copy(maxFrameRate=f))}}};Text("Кодек",color=Color(0xFF9A9AA8));Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){VideoCodec.entries.forEach{c->ChoiceButton(c.title,exportSettings.videoCodec==c){onExportSettings(exportSettings.copy(videoCodec=c))}}};Text("${exportSettings.videoCodec.title} · AAC · ${if(exportSettings.height==2160)"4K" else "${exportSettings.height}p"} · ${exportSettings.maxFrameRate} FPS",color=Color(0xFF86EFAC),style=MaterialTheme.typography.labelSmall)
}

@Composable internal fun ExportResultPanel(lastExportUri:String?,onShare:()->Unit){if(lastExportUri!=null)SectionCard("Последний экспорт"){Text("Видео сохранено в Movies/VibeCut",color=Color(0xFF86EFAC));ToolButton("Поделиться видео",onShare)}}
