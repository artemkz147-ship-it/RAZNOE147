package ru.vibecut.editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import kotlin.math.min

@OptIn(UnstableApi::class)
class ClipMaskOverlay(
    private val maskType: MaskType,
    private val maskSize: Float,
    private val vignette: Float,
) : CanvasOverlay(true) {
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        if (canvas.width <= 0 || canvas.height <= 0) return
        drawMask(canvas)
        drawVignette(canvas)
    }

    private fun drawMask(canvas: Canvas) {
        if (maskType == MaskType.NONE) return
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val size = maskSize.coerceIn(0.25f, 1f)

        when (maskType) {
            MaskType.NONE -> Unit
            MaskType.CIRCLE -> {
                val path = Path().apply {
                    fillType = Path.FillType.EVEN_ODD
                    addRect(0f, 0f, width, height, Path.Direction.CW)
                    addCircle(width / 2f, height / 2f, min(width, height) * 0.5f * size, Path.Direction.CW)
                }
                canvas.drawPath(path, maskPaint)
            }
            MaskType.ROUNDED_RECT -> {
                val visibleWidth = width * size
                val visibleHeight = height * size
                val left = (width - visibleWidth) / 2f
                val top = (height - visibleHeight) / 2f
                val radius = min(visibleWidth, visibleHeight) * 0.08f
                val path = Path().apply {
                    fillType = Path.FillType.EVEN_ODD
                    addRect(0f, 0f, width, height, Path.Direction.CW)
                    addRoundRect(
                        RectF(left, top, left + visibleWidth, top + visibleHeight),
                        radius,
                        radius,
                        Path.Direction.CW,
                    )
                }
                canvas.drawPath(path, maskPaint)
            }
            MaskType.CINEMA -> {
                val visibleFraction = (0.52f + size * 0.43f).coerceIn(0.62f, 0.95f)
                val barHeight = height * (1f - visibleFraction) / 2f
                canvas.drawRect(0f, 0f, width, barHeight, maskPaint)
                canvas.drawRect(0f, height - barHeight, width, height, maskPaint)
            }
        }
    }

    private fun drawVignette(canvas: Canvas) {
        val amount = vignette.coerceIn(0f, 1f)
        if (amount <= 0.001f) return
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val radius = maxOf(width, height) * 0.72f
        val edgeAlpha = (225f * amount).toInt().coerceIn(0, 225)
        vignettePaint.shader = RadialGradient(
            width / 2f,
            height / 2f,
            radius,
            intArrayOf(Color.TRANSPARENT, Color.argb((edgeAlpha * 0.18f).toInt(), 0, 0, 0), Color.argb(edgeAlpha, 0, 0, 0)),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, vignettePaint)
        vignettePaint.shader = null
    }
}
