package ru.vibecut.editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private data class StickerTransform(
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float,
    val alpha: Float,
)

@OptIn(UnstableApi::class)
fun buildAnimatedStickerEffects(clip: VideoClip): List<Effect> {
    if (clip.animatedStickers.isEmpty()) return emptyList()
    return listOf(OverlayEffect(clip.animatedStickers.map { AnimatedStickerCanvasOverlay(it) }))
}

@OptIn(UnstableApi::class)
private class AnimatedStickerCanvasOverlay(
    private val layer: AnimatedStickerLayer,
) : CanvasOverlay(true) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val timeMs = presentationTimeUs / 1000L
        val end = if (layer.endMs == Long.MAX_VALUE) Long.MAX_VALUE else layer.endMs
        if (timeMs < layer.startMs || timeMs > end) return
        val elapsed = ((timeMs - layer.startMs).coerceAtLeast(0L) * layer.speed.coerceIn(0.1f, 5f)).toLong()
        val cycle = 1200L
        if (!layer.loop && elapsed > cycle) return
        val phase = ((if (layer.loop) elapsed % cycle else elapsed).toFloat() / cycle.toFloat()).coerceIn(0f, 1f)
        val t = transformAt(layer, timeMs)
        if (t.alpha <= 0.001f) return
        val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
        val cx = ((t.x.coerceIn(-1f,1f) + 1f) * .5f) * w
        val cy = ((1f - t.y.coerceIn(-1f,1f)) * .5f) * h
        val base = min(w,h) * .12f * t.scale.coerceIn(.05f, 4f)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(t.rotation)
        fill.alpha = (255f * t.alpha.coerceIn(0f,1f)).toInt()
        stroke.alpha = fill.alpha
        text.alpha = fill.alpha
        when(layer.kind) {
            AnimatedStickerKind.HEART -> drawHeart(canvas, base * (1f + .14f * sin(phase * 2f * PI).toFloat()))
            AnimatedStickerKind.STAR -> drawStar(canvas, base, phase * 360f)
            AnimatedStickerKind.SPARKLE -> drawSparkle(canvas, base, phase)
            AnimatedStickerKind.ARROW -> drawArrow(canvas, base, phase)
            AnimatedStickerKind.RING -> drawRing(canvas, base, phase)
            AnimatedStickerKind.LIGHTNING -> drawLightning(canvas, base, phase)
            AnimatedStickerKind.CONFETTI -> drawConfetti(canvas, base, phase)
            AnimatedStickerKind.FIRE -> drawFire(canvas, base, phase)
            AnimatedStickerKind.CHECK -> drawCheck(canvas, base, phase)
            AnimatedStickerKind.QUESTION -> drawText(canvas, "?", base, phase)
            AnimatedStickerKind.WOW -> drawText(canvas, "WOW!", base, phase)
            AnimatedStickerKind.TARGET -> drawTarget(canvas, base, phase)
        }
        canvas.restore()
    }

    private fun drawHeart(c:Canvas,s:Float){
        fill.color=Color.rgb(255,64,105);val p=Path();p.moveTo(0f,s*.82f);p.cubicTo(-s*1.2f,s*.15f,-s*.9f,-s*.9f,-s*.35f,-s*.72f);p.cubicTo(-s*.08f,-s*.63f,0f,-s*.42f,0f,-s*.42f);p.cubicTo(0f,-s*.42f,s*.08f,-s*.63f,s*.35f,-s*.72f);p.cubicTo(s*.9f,-s*.9f,s*1.2f,s*.15f,0f,s*.82f);c.drawPath(p,fill)
    }
    private fun drawStar(c:Canvas,s:Float,rot:Float){c.save();c.rotate(rot);fill.color=Color.rgb(255,213,79);val p=Path();for(i in 0 until 10){val a=-PI/2+i*PI/5;val r=if(i%2==0)s else s*.42f;val x=(cos(a)*r).toFloat();val y=(sin(a)*r).toFloat();if(i==0)p.moveTo(x,y)else p.lineTo(x,y)};p.close();c.drawPath(p,fill);c.restore()}
    private fun drawSparkle(c:Canvas,s:Float,phase:Float){stroke.color=Color.WHITE;stroke.strokeWidth=s*.14f;val pulse=.55f+.45f*abs(sin(phase*2f*PI).toFloat());c.drawLine(-s*pulse,0f,s*pulse,0f,stroke);c.drawLine(0f,-s*pulse,0f,s*pulse,stroke);c.save();c.rotate(45f);c.drawLine(-s*.55f*pulse,0f,s*.55f*pulse,0f,stroke);c.drawLine(0f,-s*.55f*pulse,0f,s*.55f*pulse,stroke);c.restore()}
    private fun drawArrow(c:Canvas,s:Float,phase:Float){val bounce=sin(phase*2f*PI).toFloat()*s*.18f;c.translate(0f,bounce);stroke.color=Color.rgb(64,196,255);stroke.strokeWidth=s*.18f;c.drawLine(-s*.8f,0f,s*.55f,0f,stroke);c.drawLine(s*.55f,0f,s*.15f,-s*.38f,stroke);c.drawLine(s*.55f,0f,s*.15f,s*.38f,stroke)}
    private fun drawRing(c:Canvas,s:Float,phase:Float){stroke.color=Color.rgb(125,249,255);stroke.strokeWidth=s*.12f;stroke.alpha=(fill.alpha*(1f-phase)).toInt();c.drawCircle(0f,0f,s*(.35f+.7f*phase),stroke)}
    private fun drawLightning(c:Canvas,s:Float,phase:Float){fill.color=if(phase<.5f)Color.rgb(255,235,59)else Color.WHITE;val p=Path();p.moveTo(-s*.2f,-s);p.lineTo(s*.35f,-s*.15f);p.lineTo(s*.05f,-s*.12f);p.lineTo(s*.28f,s);p.lineTo(-s*.42f,s*.08f);p.lineTo(-s*.08f,s*.02f);p.close();c.drawPath(p,fill)}
    private fun drawConfetti(c:Canvas,s:Float,phase:Float){val colors=intArrayOf(Color.MAGENTA,Color.YELLOW,Color.CYAN,Color.GREEN,Color.RED);for(i in 0 until 16){val a=i*2f*PI/16f;val r=s*(.2f+phase*1.15f);val x=cos(a).toFloat()*r;val y=sin(a).toFloat()*r+phase*phase*s*.35f;fill.color=colors[i%colors.size];fill.alpha=(255f*layer.alpha*(1f-phase)).toInt().coerceIn(0,255);c.save();c.translate(x,y);c.rotate(i*37f+phase*180f);c.drawRect(-s*.07f,-s*.025f,s*.07f,s*.025f,fill);c.restore()}}
    private fun drawFire(c:Canvas,s:Float,phase:Float){val wobble=sin(phase*4f*PI).toFloat()*s*.12f;fill.color=Color.rgb(255,111,0);val p=Path();p.moveTo(0f,s);p.cubicTo(-s*.85f,s*.45f,-s*.45f,-s*.25f,-s*.12f,-s*.95f);p.cubicTo(s*.08f,-s*.45f,s*.5f,-s*.45f,s*.22f,-s*1.15f);p.cubicTo(s*.95f,-s*.25f,s*.75f,s*.55f,0f,s);p.close();c.save();c.translate(wobble,0f);c.drawPath(p,fill);fill.color=Color.rgb(255,213,79);c.drawOval(-s*.25f,-s*.15f,s*.25f,s*.72f,fill);c.restore()}
    private fun drawCheck(c:Canvas,s:Float,phase:Float){stroke.color=Color.rgb(105,240,174);stroke.strokeWidth=s*.2f;val p=phase.coerceIn(0f,1f);if(p<.45f)c.drawLine(-s*.72f,0f,-s*.72f+s*.72f*(p/.45f),s*.48f*(p/.45f),stroke)else{c.drawLine(-s*.72f,0f,0f,s*.48f,stroke);val q=(p-.45f)/.55f;c.drawLine(0f,s*.48f,s*.82f*q,-s*.55f*q,stroke)}}
    private fun drawText(c:Canvas,value:String,s:Float,phase:Float){val pop=1f+.16f*sin(phase*PI).toFloat();c.scale(pop,pop);text.color=Color.WHITE;text.textSize=if(value.length>2)s*.72f else s*1.7f;text.setShadowLayer(s*.08f,0f,s*.04f,Color.BLACK);c.drawText(value,0f,text.textSize*.32f,text);text.clearShadowLayer()}
    private fun drawTarget(c:Canvas,s:Float,phase:Float){stroke.color=Color.rgb(255,82,82);stroke.strokeWidth=s*.08f;val r=s*(.72f+.08f*sin(phase*2f*PI).toFloat());c.drawCircle(0f,0f,r,stroke);c.drawCircle(0f,0f,r*.42f,stroke);c.drawLine(-s,0f,-r*.62f,0f,stroke);c.drawLine(r*.62f,0f,s,0f,stroke);c.drawLine(0f,-s,0f,-r*.62f,stroke);c.drawLine(0f,r*.62f,0f,s,stroke)}
}

private fun transformAt(layer:AnimatedStickerLayer,timeMs:Long):StickerTransform{
    var x=layer.x;var y=layer.y;var scale=layer.scale;var rotation=layer.rotation
    if(layer.keyframes.isNotEmpty()){
        val f=sampleKeyframes(layer.keyframes,timeMs);x=f.x;y=f.y;scale=f.scale;rotation=f.rotation
    }
    if(layer.trackingPath.isNotEmpty()){
        val t=sampleTracking(layer.trackingPath,timeMs);x=(t.x+x*.25f).coerceIn(-1f,1f);y=(t.y+y*.25f).coerceIn(-1f,1f);scale*=t.objectScale.coerceIn(.45f,2.2f)
    }
    return StickerTransform(x,y,scale,rotation,layer.alpha)
}

private fun sampleKeyframes(frames:List<TransformKeyframe>,timeMs:Long):TransformKeyframe{
    val sorted=frames.sortedBy{it.timeMs};val a=sorted.lastOrNull{it.timeMs<=timeMs}?:sorted.first();val b=sorted.firstOrNull{it.timeMs>=timeMs}?:sorted.last();if(a.id==b.id)return a
    val raw=((timeMs-a.timeMs).toFloat()/(b.timeMs-a.timeMs).coerceAtLeast(1L)).coerceIn(0f,1f);val f=ease(raw,a.easing)
    fun lerp(v1:Float,v2:Float)=v1+(v2-v1)*f
    return a.copy(timeMs=timeMs,x=lerp(a.x,b.x),y=lerp(a.y,b.y),scale=lerp(a.scale,b.scale),rotation=lerp(a.rotation,b.rotation))
}

private fun sampleTracking(path:List<TrackingPoint>,timeMs:Long):TrackingPoint{
    val sorted=path.sortedBy{it.timeMs};val a=sorted.lastOrNull{it.timeMs<=timeMs}?:sorted.first();val b=sorted.firstOrNull{it.timeMs>=timeMs}?:sorted.last();if(a.timeMs==b.timeMs)return a
    val f=((timeMs-a.timeMs).toFloat()/(b.timeMs-a.timeMs).coerceAtLeast(1L)).coerceIn(0f,1f);fun l(v1:Float,v2:Float)=v1+(v2-v1)*f
    return TrackingPoint(timeMs,l(a.x,b.x),l(a.y,b.y),l(a.objectScale,b.objectScale))
}

fun ease(value:Float,type:KeyframeEasing):Float{val t=value.coerceIn(0f,1f);return when(type){KeyframeEasing.LINEAR->t;KeyframeEasing.EASE_IN->t*t;KeyframeEasing.EASE_OUT->1f-(1f-t)*(1f-t);KeyframeEasing.EASE_IN_OUT->if(t<.5f)2f*t*t else 1f-(-2f*t+2f).pow(2)/2f;KeyframeEasing.OVERSHOOT->{val c=1.70158f;1f+(c+1f)*(t-1f).pow(3)+c*(t-1f).pow(2)};KeyframeEasing.BOUNCE->{val n=7.5625f;val d=2.75f;when{t<1f/d->n*t*t;t<2f/d->{val q=t-1.5f/d;n*q*q+.75f};t<2.5f/d->{val q=t-2.25f/d;n*q*q+.9375f};else->{val q=t-2.625f/d;n*q*q+.984375f}}}}}
