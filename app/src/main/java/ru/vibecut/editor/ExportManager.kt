package ru.vibecut.editor

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream
import kotlin.math.min

@OptIn(UnstableApi::class)
class ExportManager(private val context:Context){private var transformer:Transformer?=null;private var tempFile:File?=null;private var active=false
    fun export(clips:List<VideoClip>,backgroundAudio:AudioTrack?,positionedAudioTracks:List<PositionedAudioTrack>,subtitles:List<SubtitleCue>,subtitleStyle:SubtitleStyle,settings:ExportSettings,onProgress:(Int)->Unit,onDone:(String)->Unit,onError:(String)->Unit){
        if(clips.isEmpty()){onError("Добавьте хотя бы один ролик");return};cancel();active=true
        val items=clips.mapIndexed{index,clip->
            val media=MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.trimStartMs).setEndPositionMs(clip.trimEndMs).build()).build()
            val incoming=clips.getOrNull(index-1)?.let{p->if(p.transitionOut==TransitionType.NONE)null else TransitionSpec(p.transitionOut,p.transitionDurationMs)}
            EditedMediaItem.Builder(media)
                .setRemoveAudio(clip.muted)
                .setSpeed(ConstantSpeedProvider(clip.speed))
                .setFrameRate(settings.maxFrameRate)
                .setEffects(Effects(
                    buildClipAudioEffects(clip),
                    buildVideoEffects(context,clip.copy(keyframes=emptyList(),stickers=emptyList()),incoming)+
                        buildEasedKeyframeEffects(clip)+
                        buildSpecialEffectEffects(clip)+
                        buildDynamicImageStickerEffects(context,clip)+
                        buildAnimatedStickerEffects(clip)
                )).build()
        }
        val sequences=mutableListOf(EditedMediaItemSequence.withAudioAndVideoFrom(items));val duration=clips.sumOf{it.durationMs}.coerceAtLeast(1L)
        backgroundAudio?.let{a->val item=EditedMediaItem.Builder(MediaItem.fromUri(a.uri)).setEffects(Effects(buildBackgroundAudioEffects(a),emptyList())).build();sequences+=EditedMediaItemSequence.withAudioFrom(listOf(item)).buildUpon().setIsLooping(true).build()}
        positionedAudioTracks.forEach{track->if(track.startAtMs>=duration)return@forEach;val play=min(track.sourceDurationMs,(duration-track.startAtMs).coerceAtLeast(1L)).coerceAtLeast(1L);val media=MediaItem.Builder().setUri(track.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(play).build()).build();val edited=EditedMediaItem.Builder(media).setEffects(Effects(buildPositionedAudioEffects(track),emptyList())).build();val b=EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO));if(track.startAtMs>0)b.addGap(track.startAtMs*1000L);b.addItem(edited);sequences+=b.build()}
        val composition=Composition.Builder(sequences).setEffects(Effects(emptyList(),buildCompositionVideoEffects(settings,subtitles,subtitleStyle))).build();val output=File(context.cacheDir,"vibecut_${System.currentTimeMillis()}.mp4");tempFile=output
        val listener=object:Transformer.Listener{override fun onCompleted(composition:Composition,exportResult:ExportResult){active=false;runCatching{saveToGallery(output)}.onSuccess{uri->output.delete();onProgress(100);onDone(uri)}.onFailure{onError("Видео готово, но сохранить его не удалось: ${it.message}")}};override fun onError(composition:Composition,exportResult:ExportResult,exportException:ExportException){active=false;onError(exportException.message?:"Ошибка экспорта")}}
        transformer=Transformer.Builder(context).setVideoMimeType(settings.videoCodec.mimeType).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(listener).build();transformer?.start(composition,output.absolutePath);pollProgress(onProgress)
    }
    private fun pollProgress(onProgress:(Int)->Unit){val current=transformer?:return;val holder=ProgressHolder();val handler=android.os.Handler(android.os.Looper.getMainLooper());val r=object:Runnable{override fun run(){if(!active||transformer!==current)return;val s=current.getProgress(holder);if(s==Transformer.PROGRESS_STATE_AVAILABLE)onProgress(holder.progress);handler.postDelayed(this,300)}};handler.post(r)}
    fun cancel(){active=false;transformer?.cancel();tempFile?.delete();transformer=null;tempFile=null}
    private fun saveToGallery(file:File):String{val resolver=context.contentResolver;val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,"VibeCut_${System.currentTimeMillis()}.mp4");put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/VibeCut");put(MediaStore.Video.Media.IS_PENDING,1)}};val collection=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)else MediaStore.Video.Media.EXTERNAL_CONTENT_URI;val uri=resolver.insert(collection,values)?:error("Не удалось создать файл в галерее");resolver.openOutputStream(uri)?.use{o->FileInputStream(file).use{i->i.copyTo(o)}}?:error("Не удалось открыть файл для записи");if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){values.clear();values.put(MediaStore.Video.Media.IS_PENDING,0);resolver.update(uri,values,null,null)};return uri.toString()}
}
