package ru.vibecut.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object SrtTools {
    private val timePattern = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

    fun read(context: Context, uri: Uri): List<SubtitleCue> {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return emptyList()
        return parse(text)
    }

    fun parse(text: String): List<SubtitleCue> = text.replace("\r\n", "\n").replace('\r', '\n')
        .split(Regex("\\n\\s*\\n")).mapNotNull { block ->
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            val timingIndex = lines.indexOfFirst { timePattern.containsMatchIn(it) }
            if (timingIndex < 0) return@mapNotNull null
            val match = timePattern.find(lines[timingIndex]) ?: return@mapNotNull null
            val start = toMs(match.groupValues, 1)
            val end = toMs(match.groupValues, 5).coerceAtLeast(start + 100L)
            val cueText = lines.drop(timingIndex + 1).joinToString("\n").trim()
            if (cueText.isBlank()) null else SubtitleCue(UUID.randomUUID().toString(), start, end, cueText)
        }.sortedBy { it.startMs }

    fun encode(cues: List<SubtitleCue>): String = buildString {
        cues.sortedBy { it.startMs }.forEachIndexed { index, cue ->
            append(index + 1).append('\n')
            append(formatSrtTime(cue.startMs)).append(" --> ").append(formatSrtTime(cue.endMs)).append('\n')
            append(cue.text.trim()).append("\n\n")
        }
    }

    private fun toMs(v: List<String>, o: Int): Long = ((((v[o].toLongOrNull() ?: 0L) * 60L + (v[o + 1].toLongOrNull() ?: 0L)) * 60L + (v[o + 2].toLongOrNull() ?: 0L)) * 1000L + (v[o + 3].toLongOrNull() ?: 0L)).coerceAtLeast(0L)
    private fun formatSrtTime(ms: Long): String { val s=ms.coerceAtLeast(0L); return "%02d:%02d:%02d,%03d".format(s/3_600_000L,(s/60_000L)%60L,(s/1000L)%60L,s%1000L) }
}

internal fun subtitleProgress(cue: SubtitleCue, timeMs: Long): Float =
    ((timeMs - cue.startMs).toFloat() / (cue.endMs - cue.startMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

internal fun subtitleVisibleText(cue: SubtitleCue, style: SubtitleStyle, timeMs: Long): String {
    val source = if (style.uppercase) cue.text.uppercase() else cue.text
    val progress = subtitleProgress(cue, timeMs)
    return when (style.animation) {
        SubtitleAnimation.TYPEWRITER -> source.take(max(1, (source.length * progress).roundToInt()).coerceAtMost(source.length))
        SubtitleAnimation.WORD_BY_WORD -> {
            val words = source.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) source else words.take(max(1, ceil(words.size * progress).toInt()).coerceAtMost(words.size)).joinToString(" ")
        }
        else -> source
    }
}

@OptIn(UnstableApi::class)
class SubtitleCanvasOverlay(private val cues: List<SubtitleCue>, private val style: SubtitleStyle) : CanvasOverlay(true) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val timeMs = presentationTimeUs / 1000L
        val cue = cues.lastOrNull { timeMs in it.startMs until it.endMs } ?: return
        if (canvas.width <= 0 || canvas.height <= 0) return

        val progress = subtitleProgress(cue, timeMs)
        val visible = subtitleVisibleText(cue, style, timeMs).trim()
        if (visible.isEmpty()) return
        val alphaFactor = when (style.animation) {
            SubtitleAnimation.FADE -> min((progress / .18f).coerceIn(0f,1f), ((1f-progress)/.14f).coerceIn(0f,1f))
            else -> 1f
        }
        if (alphaFactor <= .01f) return

        textPaint.typeface = SubtitleFontCatalog.resolve(style)
        textPaint.textSize = canvas.height * 0.046f * style.fontScale.coerceIn(.55f,2.2f)
        val spacingPx = textPaint.textSize * style.letterSpacing.coerceIn(0f,.14f)
        val maxWidth = canvas.width * .86f
        val lines = wrapWords(visible, maxWidth, spacingPx).take(5)
        if (lines.isEmpty()) return
        val fm = textPaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.12f
        val widths = lines.map { measureLine(it, spacingPx) }
        val widest = widths.maxOrNull() ?: return
        val totalHeight = lineHeight * lines.size
        val centerX = canvas.width / 2f
        var centerY = canvas.height * style.verticalPosition.coerceIn(.12f,.95f)
        when (style.animation) {
            SubtitleAnimation.SLIDE_UP -> centerY += canvas.height * .10f * (1f - (progress / .24f).coerceIn(0f,1f))
            SubtitleAnimation.BOUNCE -> if (progress < .40f) centerY -= sin(progress / .40f * PI).toFloat() * canvas.height * .035f
            else -> Unit
        }
        val padX = max(18f,canvas.width*.018f)
        val padY = max(10f,canvas.height*.010f)
        centerY = centerY.coerceIn(totalHeight/2f+padY,canvas.height-totalHeight/2f-padY)

        val popScale = if(style.animation==SubtitleAnimation.POP) (.72f + .28f * (progress/.22f).coerceIn(0f,1f)) else 1f
        canvas.save()
        canvas.scale(popScale,popScale,centerX,centerY)

        if(style.backgroundEnabled){
            backgroundPaint.color = withAlpha(style.backgroundColor,alphaFactor)
            val r=RectF(centerX-widest/2-padX,centerY-totalHeight/2-padY,centerX+widest/2+padX,centerY+totalHeight/2+padY)
            val radius=max(12f,canvas.height*.012f)
            canvas.drawRoundRect(r,radius,radius,backgroundPaint)
        }

        val originalWords = (if(style.uppercase) cue.text.uppercase() else cue.text).split(Regex("\\s+")).filter{it.isNotBlank()}
        val activeWord = if(style.animation==SubtitleAnimation.KARAOKE && originalWords.isNotEmpty()) min(originalWords.lastIndex,(progress*originalWords.size).toInt()) else -1
        var globalWord = 0
        val firstBaseline = centerY-totalHeight/2-fm.ascent
        lines.forEachIndexed { lineIndex, words ->
            val baseline=firstBaseline+lineIndex*lineHeight
            var x=centerX-widths[lineIndex]/2f
            words.forEachIndexed { wordIndex, word ->
                val fillColor=if(globalWord==activeWord)style.accentColor else style.textColor
                x += drawStyledWord(canvas,word,x,baseline,fillColor,alphaFactor,spacingPx)
                if(wordIndex<words.lastIndex){
                    val gap=measureText(" ",spacingPx)
                    x+=gap
                }
                globalWord++
            }
        }
        canvas.restore()
    }

    private fun drawStyledWord(canvas:Canvas,text:String,x:Float,baseline:Float,fillColor:Int,alpha:Float,spacingPx:Float):Float{
        if(style.outlineWidth>0f){
            textPaint.clearShadowLayer();textPaint.style=Paint.Style.STROKE;textPaint.strokeJoin=Paint.Join.ROUND;textPaint.strokeWidth=textPaint.textSize*(style.outlineWidth.coerceIn(0f,7f)/32f);textPaint.color=withAlpha(style.outlineColor,alpha)
            drawTextSpaced(canvas,text,x,baseline,spacingPx,textPaint)
        }
        textPaint.style=Paint.Style.FILL;textPaint.color=withAlpha(fillColor,alpha)
        if(style.shadowRadius>0f){val radius=textPaint.textSize*(style.shadowRadius.coerceIn(0f,8f)/32f);textPaint.setShadowLayer(radius,radius*.22f,radius*.30f,withAlpha(style.shadowColor,alpha))}else textPaint.clearShadowLayer()
        drawTextSpaced(canvas,text,x,baseline,spacingPx,textPaint)
        textPaint.clearShadowLayer()
        return measureText(text,spacingPx)
    }

    private fun drawTextSpaced(canvas:Canvas,text:String,startX:Float,baseline:Float,spacingPx:Float,paint:Paint){
        if(spacingPx<=.05f){canvas.drawText(text,startX,baseline,paint);return}
        var x=startX
        text.forEachIndexed{index,ch->val s=ch.toString();canvas.drawText(s,x,baseline,paint);x+=paint.measureText(s);if(index<text.lastIndex)x+=spacingPx}
    }

    private fun measureText(text:String,spacingPx:Float):Float = textPaint.measureText(text)+max(0,text.length-1)*spacingPx
    private fun measureLine(words:List<String>,spacingPx:Float):Float = words.sumOf{measureText(it,spacingPx).toDouble()}.toFloat()+max(0,words.size-1)*measureText(" ",spacingPx)

    private fun wrapWords(text:String,maxWidth:Float,spacingPx:Float):List<List<String>>{
        val result=mutableListOf<List<String>>()
        text.lines().forEach{paragraph->
            val words=paragraph.trim().split(Regex("\\s+")).filter{it.isNotBlank()}
            var current=mutableListOf<String>()
            words.forEach{word->
                val trial=current+word
                if(current.isNotEmpty()&&measureLine(trial,spacingPx)>maxWidth){result+=current.toList();current=mutableListOf()}
                current+=word
            }
            if(current.isNotEmpty())result+=current.toList()
        }
        return result
    }

    private fun withAlpha(color:Int,factor:Float):Int{
        val a=(Color.alpha(color)*factor.coerceIn(0f,1f)).roundToInt().coerceIn(0,255)
        return Color.argb(a,Color.red(color),Color.green(color),Color.blue(color))
    }
}
