package ru.vibecut.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private data class SubtitlePreset(
    val title: String,
    val style: SubtitleStyle,
)

private fun subtitlePresets(): List<SubtitlePreset> = listOf(
    SubtitlePreset("Классика", SubtitleStyle()),
    SubtitlePreset("Кино", SubtitleStyle(backgroundEnabled=false,fontKey="serif",fontDisplayName="С засечками",fontScale=1.05f,outlineWidth=2.2f,shadowRadius=2.5f)),
    SubtitlePreset("Мем", SubtitleStyle(backgroundEnabled=false,fontKey="sans-serif-black",fontDisplayName="Рубленый жирный",fontScale=1.35f,uppercase=true,outlineWidth=5.2f,shadowRadius=0f,verticalPosition=.22f)),
    SubtitlePreset("Караоке", SubtitleStyle(backgroundEnabled=false,fontKey="sans-serif-black",fontDisplayName="Рубленый жирный",fontScale=1.16f,outlineWidth=3f,accentColor=0xFFFFD54F.toInt(),animation=SubtitleAnimation.KARAOKE)),
    SubtitlePreset("Короткие ролики", SubtitleStyle(backgroundEnabled=false,fontKey="sans-serif-black",fontDisplayName="Рубленый жирный",fontScale=1.22f,outlineWidth=3.2f,accentColor=0xFF69F0AE.toInt(),animation=SubtitleAnimation.WORD_BY_WORD)),
    SubtitlePreset("Большие слова", SubtitleStyle(backgroundEnabled=false,fontKey="sans-serif-black",fontDisplayName="Рубленый жирный",fontScale=1.55f,textColor=0xFFFFD54F.toInt(),outlineWidth=4.2f,uppercase=true,animation=SubtitleAnimation.POP,verticalPosition=.74f)),
    SubtitlePreset("Новости", SubtitleStyle(backgroundColor=0xDD123A73.toInt(),fontKey="sans-serif-medium",fontDisplayName="Рубленый средний",fontScale=.95f,outlineWidth=0f,shadowRadius=0f,verticalPosition=.88f)),
    SubtitlePreset("Неон", SubtitleStyle(backgroundEnabled=false,textColor=0xFF7DF9FF.toInt(),accentColor=0xFFFF59D6.toInt(),fontKey="sans-serif-medium",fontDisplayName="Рубленый средний",outlineColor=0xFF0A1020.toInt(),outlineWidth=2.2f,shadowColor=0xFFFF2FD1.toInt(),shadowRadius=6f,animation=SubtitleAnimation.FADE)),
    SubtitlePreset("Игровой", SubtitleStyle(backgroundEnabled=false,textColor=0xFFFFFFFF.toInt(),accentColor=0xFF69F0AE.toInt(),fontKey="sans-serif-black",fontDisplayName="Рубленый жирный",outlineColor=0xFF111111.toInt(),outlineWidth=4f,animation=SubtitleAnimation.BOUNCE)),
    SubtitlePreset("Минимализм", SubtitleStyle(backgroundEnabled=false,fontKey="sans-serif-light",fontDisplayName="Рубленый лёгкий",bold=false,fontScale=.92f,outlineWidth=0f,shadowRadius=1f)),
    SubtitlePreset("Белый контур", SubtitleStyle(backgroundEnabled=false,textColor=0xFF111111.toInt(),outlineColor=0xFFFFFFFF.toInt(),outlineWidth=4f,fontKey="sans-serif-black",fontDisplayName="Рубленый жирный",shadowRadius=0f)),
    SubtitlePreset("Плашка", SubtitleStyle(backgroundEnabled=true,backgroundColor=0xE6FFFFFF.toInt(),textColor=0xFF111111.toInt(),fontKey="sans-serif-medium",fontDisplayName="Рубленый средний",outlineWidth=0f,shadowRadius=0f)),
    SubtitlePreset("Печатная машинка", SubtitleStyle(backgroundEnabled=false,fontKey="monospace",fontDisplayName="Моноширинный",fontScale=1f,outlineWidth=1.4f,animation=SubtitleAnimation.TYPEWRITER)),
    SubtitlePreset("Снизу вверх", SubtitleStyle(backgroundEnabled=false,fontKey="sans-serif-medium",fontDisplayName="Рубленый средний",outlineWidth=2.4f,animation=SubtitleAnimation.SLIDE_UP)),
)

@Composable
internal fun SubtitleStudioPanel(
    cues: List<SubtitleCue>,
    style: SubtitleStyle,
    projectCursorMs: Long,
    onImportSrt: () -> Unit,
    onAddCue: (String) -> Unit,
    onDeleteCue: (String) -> Unit,
    onClear: () -> Unit,
    onStyleChange: (SubtitleStyle) -> Unit,
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var fontStatus by remember { mutableStateOf("") }
    val systemFonts = remember { SubtitleFontCatalog.availableSystemFonts() }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { SubtitleFontCatalog.importFont(context, uri) }
                .onSuccess { imported ->
                    onStyleChange(style.copy(fontKey="file",fontDisplayName=imported.name,fontFilePath=imported.path))
                    fontStatus = "Шрифт «${imported.name}» добавлен · кириллица проверена"
                }
                .onFailure { fontStatus = it.message ?: "Не удалось добавить шрифт" }
        }
    }

    SectionCard("Субтитры · студия") {
        Text("Курсор: ${formatTime(projectCursorMs)} · строк: ${cues.size}", color = Color.White)
        Text("Шрифт: ${style.fontDisplayName}", color = Color(0xFF86EFAC))
        Row(modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            ToolButton("Импорт SRT", onImportSrt)
            ToolButton("Импорт TTF / OTF", { fontPicker.launch(arrayOf("font/ttf","font/otf","application/x-font-ttf","application/x-font-opentype","application/octet-stream")) })
            ToolButton("Очистить субтитры", onClear, enabled=cues.isNotEmpty())
        }
        if(fontStatus.isNotBlank()) Text(fontStatus,color=Color(0xFFB9B9C5),modifier=Modifier.padding(top=5.dp))

        OutlinedTextField(value=draft,onValueChange={draft=it.take(240)},modifier=Modifier.fillMaxWidth(),label={Text("Новая строка")},maxLines=4)
        ToolButton("Добавить на 2 секунды", { if(draft.isNotBlank()){onAddCue(draft.trim());draft=""} }, enabled=draft.isNotBlank())

        Text("Готовые стили",color=Color.White,modifier=Modifier.padding(top=8.dp))
        Row(modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            subtitlePresets().forEach { preset -> ToolButton(preset.title,{onStyleChange(preset.style.copy(verticalPosition=style.verticalPosition))}) }
        }

        Text("Шрифты с проверкой русской кириллицы",color=Color.White,modifier=Modifier.padding(top=8.dp))
        Row(modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            systemFonts.forEach { font -> ChoiceButton(font.title,style.fontFilePath.isBlank()&&style.fontKey==font.key){onStyleChange(style.copy(fontKey=font.key,fontDisplayName=font.title,fontFilePath=""))} }
            if(style.fontFilePath.isNotBlank()) ChoiceButton("${style.fontDisplayName} · свой",true){}
        }

        Text("Анимация",color=Color.White,modifier=Modifier.padding(top=8.dp))
        Row(modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            SubtitleAnimation.entries.forEach { animation -> ChoiceButton(animation.title,style.animation==animation){onStyleChange(style.copy(animation=animation))} }
        }

        Text("Размер: ${(style.fontScale*100).roundToInt()}%",color=Color.White)
        Slider(value=style.fontScale.coerceIn(.55f,2.2f),onValueChange={onStyleChange(style.copy(fontScale=it))},valueRange=.55f..2.2f)
        Text("Положение: ${(style.verticalPosition*100).roundToInt()}%",color=Color.White)
        Slider(value=style.verticalPosition.coerceIn(.14f,.94f),onValueChange={onStyleChange(style.copy(verticalPosition=it))},valueRange=.14f.. .94f)
        Text("Толщина контура: ${"%.1f".format(style.outlineWidth)}",color=Color.White)
        Slider(value=style.outlineWidth.coerceIn(0f,7f),onValueChange={onStyleChange(style.copy(outlineWidth=it))},valueRange=0f..7f)
        Text("Тень: ${"%.1f".format(style.shadowRadius)}",color=Color.White)
        Slider(value=style.shadowRadius.coerceIn(0f,8f),onValueChange={onStyleChange(style.copy(shadowRadius=it))},valueRange=0f..8f)
        Text("Межбуквенный интервал: ${(style.letterSpacing*100).roundToInt()}%",color=Color.White)
        Slider(value=style.letterSpacing.coerceIn(0f,.14f),onValueChange={onStyleChange(style.copy(letterSpacing=it))},valueRange=0f.. .14f)

        Row(modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            ToggleItem("Подложка",style.backgroundEnabled){onStyleChange(style.copy(backgroundEnabled=it))}
            ToggleItem("Жирный",style.bold){onStyleChange(style.copy(bold=it))}
            ToggleItem("Курсив",style.italic){onStyleChange(style.copy(italic=it))}
            ToggleItem("ВЕРХНИЙ РЕГИСТР",style.uppercase){onStyleChange(style.copy(uppercase=it))}
        }

        Text("Цвет текста",color=Color.White,modifier=Modifier.padding(top=8.dp))
        ColorChoices(style.textColor){onStyleChange(style.copy(textColor=it))}
        Text("Акцент / караоке",color=Color.White,modifier=Modifier.padding(top=5.dp))
        ColorChoices(style.accentColor){onStyleChange(style.copy(accentColor=it))}
        Text("Контур",color=Color.White,modifier=Modifier.padding(top=5.dp))
        ColorChoices(style.outlineColor){onStyleChange(style.copy(outlineColor=it))}

        cues.sortedBy{it.startMs}.takeLast(12).forEach { cue ->
            Row(modifier=Modifier.fillMaxWidth().padding(top=5.dp).background(Color(0xFF1D1D24),RoundedCornerShape(10.dp)).padding(7.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${formatTime(cue.startMs)}–${formatTime(cue.endMs)}  ${cue.text}",color=Color(0xFFD4D4DB),modifier=Modifier.weight(1f),maxLines=3)
                ToolButton("Удалить",{onDeleteCue(cue.id)})
            }
        }
    }
}

@Composable
private fun ToggleItem(title:String,checked:Boolean,onChange:(Boolean)->Unit){
    Row(verticalAlignment=Alignment.CenterVertically){Text(title,color=Color.White);Switch(checked=checked,onCheckedChange=onChange)}
}

@Composable
private fun ColorChoices(selected:Int,onSelect:(Int)->Unit){
    Row(modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        listOf(
            "Белый" to 0xFFFFFFFF.toInt(),"Чёрный" to 0xFF000000.toInt(),"Жёлтый" to 0xFFFFD54F.toInt(),
            "Красный" to 0xFFFF5252.toInt(),"Розовый" to 0xFFFF59D6.toInt(),"Голубой" to 0xFF40C4FF.toInt(),
            "Циан" to 0xFF7DF9FF.toInt(),"Зелёный" to 0xFF69F0AE.toInt(),"Оранжевый" to 0xFFFFA726.toInt(),
        ).forEach { (name,color) -> ChoiceButton(name,selected==color){onSelect(color)} }
    }
}
