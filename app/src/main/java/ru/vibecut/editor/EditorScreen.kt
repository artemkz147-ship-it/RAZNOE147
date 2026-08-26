package ru.vibecut.editor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(projectId:String,onBack:()->Unit){
    val context=LocalContext.current;val restored=remember(projectId){ProjectStore.load(context,projectId)?:SavedProject(id=projectId)}
    val clips=remember(projectId){mutableStateListOf<VideoClip>().apply{addAll(restored.clips)}};val audioTracks=remember(projectId){mutableStateListOf<PositionedAudioTrack>().apply{addAll(restored.positionedAudioTracks)}};val subtitles=remember(projectId){mutableStateListOf<SubtitleCue>().apply{addAll(restored.subtitles)}};val history=remember{mutableStateListOf<EditorSnapshot>()};val redo=remember{mutableStateListOf<EditorSnapshot>()}
    var name by remember(projectId){mutableStateOf(restored.name)};var selectedId by remember(projectId){mutableStateOf(restored.selectedId?.takeIf{id->clips.any{it.id==id}}?:clips.firstOrNull()?.id)};var position by remember{mutableLongStateOf(0L)};var pendingAudioStart by remember{mutableLongStateOf(0L)};var pendingStickerClip by remember{mutableStateOf<String?>(null)};var voiceStart by remember{mutableLongStateOf(0L)};var recording by remember{mutableStateOf(false)};var mediaBusy by remember{mutableStateOf(false)};var pendingImageDuration by remember{mutableLongStateOf(3000L)}
    var exportState by remember{mutableStateOf(ExportState.IDLE)};var exportProgress by remember{mutableIntStateOf(0)};var lastExport by remember{mutableStateOf<String?>(null)};var message by remember{mutableStateOf(if(clips.isEmpty())"Добавьте видео, чтобы начать монтаж" else "Проект открыт")};var music by remember{mutableStateOf(restored.backgroundAudio)};var subtitleStyle by remember{mutableStateOf(restored.subtitleStyle)};var exportSettings by remember{mutableStateOf(restored.exportSettings)}
    val exporter=remember{ExportManager(context)};val recorder=remember{VoiceRecorder(context)};val imageMaker=remember{ImageClipMaker(context)}
    DisposableEffect(Unit){onDispose{exporter.cancel();imageMaker.cancel();if(recording)recorder.cancel()}}
    fun snap(){history+=EditorSnapshot(clips.toList(),selectedId);redo.clear();if(history.size>80)history.removeAt(0)}
    fun restore(s:EditorSnapshot){clips.clear();clips.addAll(s.clips);selectedId=s.selectedId?.takeIf{id->clips.any{it.id==id}}?:clips.firstOrNull()?.id;position=0}
    fun replace(c:VideoClip){val i=clips.indexOfFirst{it.id==c.id};if(i>=0)clips[i]=c}
    fun insertAfterSelected(newClip:VideoClip){snap();val currentIndex=clips.indexOfFirst{it.id==selectedId};val insertAt=if(currentIndex<0)clips.size else currentIndex+1;clips.add(insertAt,newClip);selectedId=newClip.id;position=0}
    fun state()=SavedProject(id=projectId,name=name.trim().ifBlank{"Новый проект"},createdAt=restored.createdAt,clips=clips.toList(),selectedId=selectedId,backgroundAudio=music,positionedAudioTracks=audioTracks.toList(),subtitles=subtitles.toList(),subtitleStyle=subtitleStyle,exportSettings=exportSettings)
    fun startExport(){if(clips.isEmpty()||exportState==ExportState.EXPORTING)return;ProjectStore.save(context,state());exportState=ExportState.EXPORTING;exportProgress=0;lastExport=null;message="Экспорт начат";exporter.export(clips.toList(),music,audioTracks.toList(),subtitles.toList(),subtitleStyle,exportSettings,{exportProgress=it},{uri->exportState=ExportState.DONE;exportProgress=100;lastExport=uri;message="Видео сохранено в Movies/VibeCut"},{exportState=ExportState.ERROR;message=it})}
    val writePermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)startExport()else message="Нет разрешения на сохранение"}
    val videoPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->if(uris.isNotEmpty()){snap();var count=0;uris.forEach{u->persist(context,u);runCatching{readClip(context,u)}.onSuccess{clips+=it;if(selectedId==null)selectedId=it.id;count++}};message="Добавлено видео: $count"}}
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{persist(context,it);mediaBusy=true;message="Создаётся клип из фото";imageMaker.createPhotoClip(it,displayName(context,it,"Фото"),pendingImageDuration,{clip->mediaBusy=false;insertAfterSelected(clip);message="Фото добавлено как клип"},{error->mediaBusy=false;message=error})}}
    val musicPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{persist(context,it);music=AudioTrack(it.toString(),displayName(context,it,"Музыка"),.65f)}}
    val audioPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{persist(context,it);audioTracks+=PositionedAudioTrack(UUID.randomUUID().toString(),it.toString(),displayName(context,it,"Звук"),duration(context,it),pendingAudioStart,.85f);message="Звук добавлен с ${formatTime(pendingAudioStart)}"}}
    val stickerPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->val id=pendingStickerClip;if(u!=null&&id!=null){persist(context,u);clips.firstOrNull{it.id==id}?.let{c->snap();replace(c.copy(stickers=c.stickers+StickerLayer(UUID.randomUUID().toString(),u.toString(),displayName(context,u,"Изображение"))))}};pendingStickerClip=null}
    val srtPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{persist(context,it);val list=runCatching{SrtTools.read(context,it)}.getOrDefault(emptyList());if(list.isNotEmpty()){subtitles.clear();subtitles.addAll(list);message="Импортировано субтитров: ${list.size}"}else message="Не удалось прочитать SRT"}}
    fun beginVoice(start:Long){voiceStart=start;runCatching{recorder.start()}.onSuccess{recording=true;message="Запись озвучки начата"}.onFailure{message="Не удалось начать запись"}}
    val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)beginVoice(voiceStart)else message="Для озвучки нужен микрофон"}
    LaunchedEffect(name,clips.toList(),selectedId,music,audioTracks.toList(),subtitles.toList(),subtitleStyle,exportSettings){delay(300);ProjectStore.save(context,state())}
    val selected=clips.firstOrNull{it.id==selectedId};val index=clips.indexOfFirst{it.id==selectedId};val offset=if(index>0)clips.take(index).sumOf{it.durationMs}else 0L;val cursor=if(selected!=null)offset+(position/selected.speed.coerceAtLeast(.05f)).toLong()else 0L;val incoming=clips.getOrNull(index-1)?.let{p->if(p.transitionOut==TransitionType.NONE)null else TransitionSpec(p.transitionOut,p.transitionDurationMs)}
    Column(Modifier.fillMaxSize().background(Color(0xFF09090C)).verticalScroll(rememberScrollState())){
        FullEditorHeader(name,clips.size,exportState,exportProgress,{ProjectStore.save(context,state());onBack()},{name=it},{videoPicker.launch(arrayOf("video/*"))},{if(Build.VERSION.SDK_INT<=Build.VERSION_CODES.P&&context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)else startExport()})
        if(selected==null)EmptyEditor{videoPicker.launch(arrayOf("video/*"))}else{
            EditorPreview(selected,incoming,exportSettings,offset,subtitles,subtitleStyle){position=it};Timeline(clips,selectedId,{selectedId=it;position=0})
            BasicTools(selected,position,history.isNotEmpty(),redo.isNotEmpty(),index>0,index in 0 until clips.lastIndex,
                onSplit={val absolute=selected.trimStartMs+position;if(absolute>selected.trimStartMs+100&&absolute<selected.trimEndMs-100){snap();val left=selected.copy(trimEndMs=absolute,transitionOut=TransitionType.NONE,keyframes=selected.keyframes.filter{it.timeMs<=position});val right=selected.copy(id=UUID.randomUUID().toString(),name="${selected.name} · 2",trimStartMs=absolute,keyframes=selected.keyframes.filter{it.timeMs>=position}.map{it.copy(id=UUID.randomUUID().toString(),timeMs=(it.timeMs-position).coerceAtLeast(0))});clips[index]=left;clips.add(index+1,right);selectedId=right.id;position=0}},
                onTrimStart={val a=selected.trimStartMs+position;if(a<selected.trimEndMs-100){snap();replace(selected.copy(trimStartMs=a));position=0}},onTrimEnd={val a=selected.trimStartMs+position;if(a>selected.trimStartMs+100){snap();replace(selected.copy(trimEndMs=a))}},onMute={snap();replace(selected.copy(muted=!selected.muted))},onRotate={snap();replace(selected.copy(rotationDegrees=(selected.rotationDegrees+90)%360))},onFlipHorizontal={snap();replace(selected.copy(flipHorizontal=!selected.flipHorizontal))},onFlipVertical={snap();replace(selected.copy(flipVertical=!selected.flipVertical))},onDuplicate={snap();val c=selected.copy(id=UUID.randomUUID().toString(),name="${selected.name} · копия");clips.add(index+1,c);selectedId=c.id},onMoveLeft={if(index>0){snap();val c=clips.removeAt(index);clips.add(index-1,c)}},onMoveRight={if(index in 0 until clips.lastIndex){snap();val c=clips.removeAt(index);clips.add(index+1,c)}},onDelete={snap();clips.removeAt(index);selectedId=if(clips.isEmpty())null else clips[index.coerceAtMost(clips.lastIndex)].id;position=0},onUndo={if(history.isNotEmpty()){redo+=EditorSnapshot(clips.toList(),selectedId);restore(history.removeAt(history.lastIndex))}},onRedo={if(redo.isNotEmpty()){history+=EditorSnapshot(clips.toList(),selectedId);restore(redo.removeAt(redo.lastIndex))}},onImport={videoPicker.launch(arrayOf("video/*"))})
            MediaCreationPanel(mediaBusy,{durationMs->pendingImageDuration=durationMs;imagePicker.launch(arrayOf("image/*"))},{durationMs->if(!mediaBusy){val source=clips.firstOrNull{it.id==selectedId};if(source!=null){mediaBusy=true;message="Создаётся стоп-кадр";imageMaker.createFreezeFrame(source,position,durationMs,{clip->mediaBusy=false;insertAfterSelected(clip);message="Стоп-кадр добавлен"},{error->mediaBusy=false;message=error})}}})
            AutoMontagePanel{style->snap();clips.indices.forEach{i->val c=clips[i];val last=i==clips.lastIndex;clips[i]=when(style){AutoMontageStyle.DYNAMIC->c.copy(transitionOut=if(last)TransitionType.NONE else listOf(TransitionType.SLIDE_LEFT,TransitionType.ZOOM,TransitionType.SPIN)[i%3],transitionDurationMs=450,motion=listOf(ClipMotion.ZOOM_IN,ClipMotion.PAN_RIGHT,ClipMotion.ZOOM_OUT)[i%3],motionStrength=.16f,contrast=.12f,saturation=16f);AutoMontageStyle.CALM->c.copy(transitionOut=if(last)TransitionType.NONE else TransitionType.FADE,transitionDurationMs=850,motion=ClipMotion.ZOOM_IN,motionStrength=.08f,contrast=-.03f,saturation=-4f,lightness=4f);AutoMontageStyle.TRAVEL->c.copy(transitionOut=if(last)TransitionType.NONE else if(i%2==0)TransitionType.SLIDE_LEFT else TransitionType.SLIDE_RIGHT,transitionDurationMs=600,motion=if(i%2==0)ClipMotion.PAN_RIGHT else ClipMotion.PAN_LEFT,motionStrength=.11f,hue=7f,saturation=14f,lightness=3f);AutoMontageStyle.REELS->c.copy(transitionOut=if(last)TransitionType.NONE else listOf(TransitionType.SLIDE_LEFT,TransitionType.FLASH,TransitionType.ZOOM)[i%3],transitionDurationMs=320,motion=ClipMotion.ZOOM_IN,motionStrength=.20f,contrast=.16f,saturation=22f)}};message="Стиль «${style.title}» применён"}
            BulkEditPanel(clips,selected,{snap()},{message=it})
            AdjustmentsPanel(selected,{snap()},{replace(it)});FilterPanel(selected,{snap()},{replace(it)});ColorEffectsPanel(selected,{snap()},{replace(it)});MotionPanel(selected,{snap()},{replace(it)});TransitionPanel(selected,index in 0 until clips.lastIndex,{snap()},{replace(it)});KeyframePanel(selected,position,{snap()},{replace(it)});ClipAudioPanel(selected,{snap()},{replace(it)})
            PositionedAudioPanel(audioTracks,cursor,{pendingAudioStart=cursor;audioPicker.launch(arrayOf("audio/*"))},{a->val i=audioTracks.indexOfFirst{it.id==a.id};if(i>=0)audioTracks[i]=a},{id->audioTracks.removeAll{it.id==id}})
            VoiceoverPanel(recording,if(recording)voiceStart else cursor,{voiceStart=cursor;if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)beginVoice(cursor)else micPermission.launch(Manifest.permission.RECORD_AUDIO)},{val file=recorder.stop();recording=false;if(file!=null){audioTracks+=PositionedAudioTrack(UUID.randomUUID().toString(),Uri.fromFile(file).toString(),"Озвучка",duration(context,Uri.fromFile(file)),voiceStart,1f);message="Озвучка добавлена"}})
            StickerPanel(selected,{pendingStickerClip=selected.id;stickerPicker.launch(arrayOf("image/*"))},{snap()},{replace(it)});TextPanel(selected,{snap()},{replace(it)})
            SubtitlePanel(subtitles,subtitleStyle,cursor,{srtPicker.launch(arrayOf("application/x-subrip","text/plain","*/*"))},{text->subtitles+=SubtitleCue(UUID.randomUUID().toString(),cursor,cursor+2000,text)},{id->subtitles.removeAll{it.id==id}},{subtitles.clear()},{subtitleStyle=it})
            SubtitleExportPanel(subtitles)
            AdvancedProjectPanel(music,exportSettings,{musicPicker.launch(arrayOf("audio/*"))},{music=null},{music=it},{exportSettings=it})
            ExportResultPanel(lastExport){val u=lastExport?:return@ExportResultPanel;runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="video/mp4";putExtra(Intent.EXTRA_STREAM,Uri.parse(u));addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Поделиться видео"))}}
        }
        Text(message,color=Color(0xFFB9B9C5),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(16.dp));Spacer(Modifier.height(28.dp))
    }
}
private fun persist(context:Context,uri:Uri){runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}}
private fun readClip(context:Context,uri:Uri)=VideoClip(UUID.randomUUID().toString(),uri.toString(),displayName(context,uri,"Видео"),duration(context,uri))
private fun duration(context:Context,uri:Uri):Long{val r=MediaMetadataRetriever();return try{if(uri.scheme=="file")r.setDataSource(uri.path)else r.setDataSource(context,uri);(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:1).coerceAtLeast(1)}finally{r.release()}}
private fun displayName(context:Context,uri:Uri,fallback:String):String{if(uri.scheme=="file")return File(uri.path.orEmpty()).name.ifBlank{fallback};return context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0)else null}?:fallback}
