package ru.vibecut.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import java.util.UUID
import kotlin.math.max

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

@OptIn(UnstableApi::class)
class SubtitleCanvasOverlay(private val cues: List<SubtitleCue>, private val style: SubtitleStyle) : CanvasOverlay(true) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val timeMs = presentationTimeUs / 1000L
        val cue = cues.lastOrNull { timeMs in it.startMs until it.endMs } ?: return
        if (canvas.width <= 0 || canvas.height <= 0) return
        textPaint.color = style.textColor
        textPaint.textSize = canvas.height * 0.046f * style.fontScale.coerceIn(0.55f, 2.2f)
        val lines = wrapText(cue.text, 40); if (lines.isEmpty()) return
        val fm=textPaint.fontMetrics; val lineHeight=(fm.descent-fm.ascent)*1.08f; val maxWidth=lines.maxOf{textPaint.measureText(it)}; val totalHeight=lineHeight*lines.size
        val centerX=canvas.width/2f; val centerY=canvas.height*style.verticalPosition.coerceIn(0.55f,0.94f); val px=max(18f,canvas.width*.018f); val py=max(10f,canvas.height*.010f)
        if(style.backgroundEnabled){backgroundPaint.color=style.backgroundColor;val r=RectF(centerX-maxWidth/2-px,centerY-totalHeight/2-py,centerX+maxWidth/2+px,centerY+totalHeight/2+py);val radius=max(12f,canvas.height*.012f);canvas.drawRoundRect(r,radius,radius,backgroundPaint)}
        val first=centerY-totalHeight/2-fm.ascent;lines.forEachIndexed{i,line->canvas.drawText(line,centerX,first+i*lineHeight,textPaint)}
    }

    private fun wrapText(text:String,maxChars:Int):List<String>{val result=mutableListOf<String>();text.lines().forEach{src->val words=src.trim().split(Regex("\\s+")).filter{it.isNotBlank()};var current=StringBuilder();words.forEach{w->if(current.isNotEmpty()&&current.length+1+w.length>maxChars){result+=current.toString();current=StringBuilder()};if(current.isNotEmpty())current.append(' ');current.append(w)};if(current.isNotEmpty())result+=current.toString()};return result.take(4)}
}
