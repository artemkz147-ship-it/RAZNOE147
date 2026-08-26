package ru.vibecut.editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.Contrast
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import java.util.Random

@OptIn(UnstableApi::class)
fun buildSpecialEffectEffects(clip: VideoClip): List<Effect> {
    val type = clip.specialEffect
    if (type == SpecialEffect.NONE) return emptyList()
    val strength = clip.specialEffectStrength.coerceIn(0f, 1f)
    val effects = mutableListOf<Effect>()

    when (type) {
        SpecialEffect.NONE -> Unit
        SpecialEffect.VHS -> {
            effects += MatrixTransformation { us ->
                val t = us / 1_000_000f
                val wobble = (sin(t * 17.3f) * .012f + sin(t * 4.7f) * .008f) * strength
                Matrix().apply { postTranslate(wobble, 0f) }
            }
            effects += Contrast(.08f * strength)
            effects += RgbAdjustment.Builder().setRedScale(1f + .07f * strength).setGreenScale(1f).setBlueScale(1f + .10f * strength).build()
            effects += OverlayEffect(listOf(AnalogNoiseOverlay(type, strength)))
        }
        SpecialEffect.CRT -> {
            effects += Contrast(.12f * strength)
            effects += RgbAdjustment.Builder().setRedScale(1.03f).setGreenScale(1.02f).setBlueScale(.96f).build()
            effects += OverlayEffect(listOf(AnalogNoiseOverlay(type, strength)))
        }
        SpecialEffect.FILM_GRAIN,
        SpecialEffect.OLD_FILM,
        SpecialEffect.SCRATCHES,
        SpecialEffect.LIGHT_LEAK,
        SpecialEffect.FLASHES -> {
            if (type == SpecialEffect.OLD_FILM) {
                effects += Contrast(-.06f * strength)
                effects += RgbAdjustment.Builder().setRedScale(1.10f).setGreenScale(.98f).setBlueScale(.78f).build()
            }
            effects += OverlayEffect(listOf(AnalogNoiseOverlay(type, strength)))
        }
        SpecialEffect.GLITCH -> {
            effects += MatrixTransformation { us ->
                val frame = us / 45_000L
                val active = frame % 11L == 0L || frame % 17L == 0L
                val shift = if (active) (((frame * 37L) % 9L) - 4L).toFloat() * .012f * strength else 0f
                Matrix().apply { postTranslate(shift, 0f) }
            }
            effects += dynamicDiagonal { t ->
                val pulse = if (((t * 1000).toLong() / 70L) % 13L == 0L) .30f * strength else 0f
                1f + pulse
            }
            effects += OverlayEffect(listOf(AnalogNoiseOverlay(type, strength)))
        }
        SpecialEffect.RGB_PULSE -> {
            effects += object : RgbMatrix {
                override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
                    val t = presentationTimeUs / 1_000_000f
                    val r = 1f + sin(t * 4.7f) * .30f * strength
                    val g = 1f + sin(t * 4.7f + 2.1f) * .24f * strength
                    val b = 1f + sin(t * 4.7f + 4.2f) * .30f * strength
                    return rgbDiagonal(r, g, b)
                }
            }
        }
        SpecialEffect.STROBE -> effects += dynamicDiagonal { t -> if ((t * 8f).toInt() % 2 == 0) 1f else (1f - .88f * strength).coerceAtLeast(.08f) }
        SpecialEffect.FLICKER -> effects += dynamicDiagonal { t ->
            val f = 1f + (sin(t * 27.1f) * .10f + sin(t * 8.3f) * .06f) * strength
            f.coerceIn(.72f, 1.22f)
        }
        SpecialEffect.CAMERA_SHAKE -> effects += MatrixTransformation { us ->
            val t = us / 1_000_000f
            val x = (sin(t * 31f) + sin(t * 13.7f) * .6f) * .018f * strength
            val y = (cos(t * 27f) + sin(t * 9.4f) * .5f) * .012f * strength
            val rot = sin(t * 19f) * 1.4f * strength
            Matrix().apply { postRotate(rot); postTranslate(x, y) }
        }
        SpecialEffect.ZOOM_PULSE -> effects += MatrixTransformation { us ->
            val t = us / 1_000_000f
            val scale = 1f + ((sin(t * 2f * PI.toFloat() * 1.35f) + 1f) * .5f) * .12f * strength
            Matrix().apply { postScale(scale, scale) }
        }
        SpecialEffect.DREAM -> {
            effects += dynamicDiagonal { t -> 1f + (.06f + .05f * sin(t * 2.3f)) * strength }
            effects += OverlayEffect(listOf(AnalogNoiseOverlay(type, strength)))
        }
        SpecialEffect.NIGHT_VISION -> {
            effects += RgbFilter.createGrayscaleFilter()
            effects += RgbAdjustment.Builder().setRedScale(.12f).setGreenScale(1.72f).setBlueScale(.18f).build()
            effects += Contrast(.16f * strength)
            effects += OverlayEffect(listOf(AnalogNoiseOverlay(type, strength)))
        }
        SpecialEffect.SECURITY_CAM -> {
            effects += RgbFilter.createGrayscaleFilter()
            effects += RgbAdjustment.Builder().setRedScale(.78f).setGreenScale(1.02f).setBlueScale(.88f).build()
            effects += Contrast(.10f * strength)
            effects += OverlayEffect(listOf(AnalogNoiseOverlay(type, strength)))
        }
    }
    return effects
}

@OptIn(UnstableApi::class)
private fun dynamicDiagonal(value: (Float) -> Float): Effect = object : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        val v = value(presentationTimeUs / 1_000_000f).coerceIn(0f, 2.5f)
        return rgbDiagonal(v, v, v)
    }
}

private fun rgbDiagonal(r: Float, g: Float, b: Float) = floatArrayOf(
    r, 0f, 0f, 0f,
    0f, g, 0f, 0f,
    0f, 0f, b, 0f,
    0f, 0f, 0f, 1f,
)

@OptIn(UnstableApi::class)
private class AnalogNoiseOverlay(
    private val type: SpecialEffect,
    private val strength: Float,
) : CanvasOverlay(true) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        val ms = presentationTimeUs / 1000L
        when (type) {
            SpecialEffect.VHS -> drawVhs(canvas, w, h, ms)
            SpecialEffect.CRT -> drawCrt(canvas, w, h)
            SpecialEffect.FILM_GRAIN -> drawGrain(canvas, w, h, ms, .52f)
            SpecialEffect.OLD_FILM -> { drawGrain(canvas, w, h, ms, .32f); drawScratches(canvas, w, h, ms, .70f); drawOldFilmFlicker(canvas, w, h, ms) }
            SpecialEffect.SCRATCHES -> drawScratches(canvas, w, h, ms, 1f)
            SpecialEffect.GLITCH -> drawGlitch(canvas, w, h, ms)
            SpecialEffect.LIGHT_LEAK -> drawLightLeak(canvas, w, h, ms)
            SpecialEffect.FLASHES -> drawFlashes(canvas, w, h, ms)
            SpecialEffect.DREAM -> drawDream(canvas, w, h, ms)
            SpecialEffect.NIGHT_VISION -> drawNightVision(canvas, w, h, ms)
            SpecialEffect.SECURITY_CAM -> drawSecurity(canvas, w, h, ms)
            else -> Unit
        }
    }

    private fun drawVhs(c: Canvas, w: Float, h: Float, ms: Long) {
        paint.color = Color.argb((24 * strength).toInt(), 255, 255, 255)
        val step = (7f + 7f * (1f - strength)).coerceAtLeast(5f)
        var y = ((ms / 8L) % step.toLong().coerceAtLeast(1L)).toFloat()
        while (y < h) { c.drawRect(0f, y, w, y + 1f, paint); y += step }
        val bandY = ((ms * .13f) % (h + h * .22f)) - h * .11f
        paint.color = Color.argb((42 * strength).toInt(), 220, 240, 255)
        c.drawRect(0f, bandY, w, bandY + h * .055f, paint)
        drawGrain(c, w, h, ms, .18f)
    }

    private fun drawCrt(c: Canvas, w: Float, h: Float) {
        paint.color = Color.argb((52 * strength).toInt(), 0, 0, 0)
        var y = 0f
        val step = 6f
        while (y < h) { c.drawRect(0f, y, w, y + 2f, paint); y += step }
        val shader = RadialGradient(w / 2f, h / 2f, maxOf(w, h) * .72f, intArrayOf(Color.TRANSPARENT, Color.argb((145 * strength).toInt(), 0,0,0)), floatArrayOf(.52f,1f), Shader.TileMode.CLAMP)
        paint.shader = shader; c.drawRect(0f,0f,w,h,paint); paint.shader = null
    }

    private fun drawGrain(c: Canvas, w: Float, h: Float, ms: Long, multiplier: Float) {
        val rnd = Random(ms / 33L + 9187L)
        val count = (70 + 150 * strength * multiplier).toInt()
        repeat(count) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
            val size = 1f + rnd.nextFloat() * (2.8f + 3f * strength)
            val white = rnd.nextBoolean()
            val alpha = (10 + rnd.nextInt((28 + 42 * strength).toInt().coerceAtLeast(1)))
            paint.color = Color.argb(alpha, if (white) 255 else 0, if (white) 255 else 0, if (white) 255 else 0)
            c.drawRect(x, y, x + size, y + size, paint)
        }
    }

    private fun drawScratches(c: Canvas, w: Float, h: Float, ms: Long, multiplier: Float) {
        val rnd = Random(ms / 120L + 3401L)
        val count = (1 + 6 * strength * multiplier).toInt()
        repeat(count) {
            val x = rnd.nextFloat() * w
            thin.color = Color.argb((35 + rnd.nextInt(80) * strength).toInt().coerceIn(0,180), 240, 236, 220)
            thin.strokeWidth = 1f + rnd.nextFloat() * 1.5f
            val top = rnd.nextFloat() * h * .3f
            c.drawLine(x, top, x + (rnd.nextFloat() - .5f) * 9f, (top + h * (.35f + rnd.nextFloat() * .65f)).coerceAtMost(h), thin)
        }
    }

    private fun drawOldFilmFlicker(c: Canvas, w: Float, h: Float, ms: Long) {
        val f = abs(sin(ms / 1000f * 16.2f))
        paint.color = Color.argb((18f * strength * f).toInt(), 255, 225, 174)
        c.drawRect(0f,0f,w,h,paint)
    }

    private fun drawGlitch(c: Canvas, w: Float, h: Float, ms: Long) {
        val frame = ms / 55L
        if (frame % 7L != 0L && frame % 13L != 0L) return
        val rnd = Random(frame + 77L)
        repeat((2 + 6 * strength).toInt()) {
            val y = rnd.nextFloat() * h; val hh = h * (.008f + rnd.nextFloat() * .045f)
            val color = when (it % 3) { 0 -> Color.argb((70 * strength).toInt(),255,40,90); 1 -> Color.argb((70 * strength).toInt(),20,220,255); else -> Color.argb((55 * strength).toInt(),255,255,255) }
            paint.color = color
            val x = (rnd.nextFloat() - .5f) * w * .2f
            c.drawRect(x, y, w + x, y + hh, paint)
        }
    }

    private fun drawLightLeak(c: Canvas, w: Float, h: Float, ms: Long) {
        val t = ms / 1000f
        val cx = w * (.15f + .7f * ((sin(t * .58f) + 1f) * .5f))
        val cy = h * (.1f + .8f * ((cos(t * .43f) + 1f) * .5f))
        val alpha = (145 * strength).toInt()
        paint.shader = RadialGradient(cx, cy, maxOf(w,h) * .65f, intArrayOf(Color.argb(alpha,255,70,30), Color.argb((alpha*.42f).toInt(),255,190,70), Color.TRANSPARENT), floatArrayOf(0f,.38f,1f), Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,paint);paint.shader=null
    }

    private fun drawFlashes(c: Canvas, w: Float, h: Float, ms: Long) {
        val phase = (ms % 2300L).toFloat() / 2300f
        if (phase < .075f || (phase in .48f.. .515f)) {
            val p = if (phase < .075f) 1f - phase/.075f else 1f - (phase-.48f)/.035f
            paint.color = Color.argb((220f * strength * p.coerceIn(0f,1f)).toInt(),255,255,255)
            c.drawRect(0f,0f,w,h,paint)
        }
    }

    private fun drawDream(c: Canvas, w: Float, h: Float, ms: Long) {
        val t = ms / 1000f
        val alpha = (55 * strength).toInt()
        paint.shader = LinearGradient(0f,0f,w,h,intArrayOf(Color.argb(alpha,255,180,230),Color.TRANSPARENT,Color.argb(alpha,130,210,255)),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,paint);paint.shader=null
        val r = minOf(w,h) * (.18f + .04f * sin(t*1.7f))
        paint.shader = RadialGradient(w*.58f,h*.35f,r*3f,intArrayOf(Color.argb((50*strength).toInt(),255,255,255),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        c.drawCircle(w*.58f,h*.35f,r*3f,paint);paint.shader=null
    }

    private fun drawNightVision(c: Canvas, w: Float, h: Float, ms: Long) {
        drawGrain(c,w,h,ms,.32f)
        paint.color=Color.argb((38*strength).toInt(),20,255,60)
        var y=0f;while(y<h){c.drawRect(0f,y,w,y+1f,paint);y+=8f}
        paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;paint.color=Color.argb((170*strength).toInt(),130,255,130);c.drawCircle(w/2f,h/2f,minOf(w,h)*.38f,paint);paint.style=Paint.Style.FILL
    }

    private fun drawSecurity(c: Canvas, w: Float, h: Float, ms: Long) {
        paint.color=Color.argb((38*strength).toInt(),0,0,0);var y=0f;while(y<h){c.drawRect(0f,y,w,y+1.5f,paint);y+=7f}
        paint.color=Color.argb((210*strength).toInt(),230,255,235);paint.textSize=(minOf(w,h)*.035f).coerceAtLeast(18f);paint.typeface=android.graphics.Typeface.MONOSPACE
        val total=ms/1000L;val sec=total%60;val min=(total/60)%60;val hour=(total/3600)%100
        c.drawText("REC  %02d:%02d:%02d".format(hour,min,sec),w*.045f,h*.08f,paint)
        paint.color=Color.argb((210*strength).toInt(),255,45,45);c.drawCircle(w*.025f,h*.068f,minOf(w,h)*.008f,paint)
    }
}
