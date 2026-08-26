package ru.vibecut.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import java.io.FileInputStream
import kotlin.math.min

private data class GifTransform(val x:Float,val y:Float,val scale:Float,val rotation:Float,val alpha:Float)

@OptIn(UnstableApi::class)
fun buildGifStickerEffects(context: Context, clip: VideoClip): List<Effect> {
    if (clip.gifStickers.isEmpty()) return emptyList()
    val overlays = clip.gifStickers.mapNotNull { layer ->
        runCatching { GifStickerCanvasOverlay(context, layer) }.getOrNull()
    }
    return if (overlays.isEmpty()) emptyList() else listOf(OverlayEffect(overlays))
}

@OptIn(UnstableApi::class)
private class GifStickerCanvasOverlay(
    context: Context,
    private val layer: GifStickerLayer,
) : CanvasOverlay(true) {
    private val movie: Movie
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        val uri = Uri.parse(layer.uri)
        movie = if (uri.scheme == "file") {
            FileInputStream(uri.path ?: error("Нет пути GIF")).use { Movie.decodeStream(it) }
        } else {
            context.contentResolver.openInputStream(uri)?.use { Movie.decodeStream(it) }
        } ?: error("Не удалось открыть GIF")
    }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val timeMs = presentationTimeUs / 1000L
        val end = if (layer.endMs == Long.MAX_VALUE) Long.MAX_VALUE else layer.endMs
        if (timeMs < layer.startMs || timeMs > end) return
        val duration = movie.duration().takeIf { it > 0 } ?: 1000
        val elapsed = ((timeMs - layer.startMs).coerceAtLeast(0L) * layer.speed.coerceIn(.1f,5f)).toLong()
        if (!layer.loop && elapsed >= duration) return
        val gifTime = if (layer.loop) (elapsed % duration).toInt() else elapsed.coerceAtMost((duration - 1).toLong()).toInt()
        movie.setTime(gifTime)

        val t = gifTransformAt(layer, timeMs)
        if (t.alpha <= .001f || movie.width() <= 0 || movie.height() <= 0) return
        val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
        val cx = ((t.x.coerceIn(-1f,1f) + 1f) * .5f) * w
        val cy = ((1f - t.y.coerceIn(-1f,1f)) * .5f) * h
        val target = min(w,h) * .30f * t.scale.coerceIn(.05f,4f)
        val sourceMax = maxOf(movie.width(),movie.height()).toFloat().coerceAtLeast(1f)
        val scale = target / sourceMax
        paint.alpha = (255f * t.alpha.coerceIn(0f,1f)).toInt().coerceIn(0,255)
        canvas.save()
        canvas.translate(cx,cy)
        canvas.rotate(t.rotation)
        canvas.scale(scale,scale)
        movie.draw(canvas,-movie.width()/2f,-movie.height()/2f,paint)
        canvas.restore()
    }
}

private fun gifTransformAt(layer:GifStickerLayer,timeMs:Long):GifTransform{
    var x=layer.x;var y=layer.y;var scale=layer.scale;var rotation=layer.rotation
    if(layer.keyframes.isNotEmpty()){
        val f=gifSampleKeyframes(layer.keyframes,timeMs);x=f.x;y=f.y;scale=f.scale;rotation=f.rotation
    }
    if(layer.trackingPath.isNotEmpty()){
        val t=gifSampleTracking(layer.trackingPath,timeMs);x=(t.x+x*.25f).coerceIn(-1f,1f);y=(t.y+y*.25f).coerceIn(-1f,1f);scale*=t.objectScale.coerceIn(.45f,2.2f)
    }
    return GifTransform(x,y,scale,rotation,layer.alpha)
}

private fun gifSampleKeyframes(frames:List<TransformKeyframe>,timeMs:Long):TransformKeyframe{
    val sorted=frames.sortedBy{it.timeMs};val a=sorted.lastOrNull{it.timeMs<=timeMs}?:sorted.first();val b=sorted.firstOrNull{it.timeMs>=timeMs}?:sorted.last();if(a.id==b.id)return a
    val raw=((timeMs-a.timeMs).toFloat()/(b.timeMs-a.timeMs).coerceAtLeast(1L)).coerceIn(0f,1f);val f=ease(raw,a.easing)
    fun l(v1:Float,v2:Float)=v1+(v2-v1)*f
    return a.copy(timeMs=timeMs,x=l(a.x,b.x),y=l(a.y,b.y),scale=l(a.scale,b.scale),rotation=l(a.rotation,b.rotation))
}

private fun gifSampleTracking(path:List<TrackingPoint>,timeMs:Long):TrackingPoint{
    val sorted=path.sortedBy{it.timeMs};val a=sorted.lastOrNull{it.timeMs<=timeMs}?:sorted.first();val b=sorted.firstOrNull{it.timeMs>=timeMs}?:sorted.last();if(a.timeMs==b.timeMs)return a
    val f=((timeMs-a.timeMs).toFloat()/(b.timeMs-a.timeMs).coerceAtLeast(1L)).coerceIn(0f,1f);fun l(v1:Float,v2:Float)=v1+(v2-v1)*f
    return TrackingPoint(timeMs,l(a.x,b.x),l(a.y,b.y),l(a.objectScale,b.objectScale))
}
