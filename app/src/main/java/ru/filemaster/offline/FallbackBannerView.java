package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Lightweight in-app fallback shown in the ad slot while no banner creative is available.
 * It intentionally has no click handling and does not imitate an advertisement.
 */
final class FallbackBannerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    FallbackBannerView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 1f || h <= 1f) return;

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0f, 0f, w, h,
                Color.rgb(248, 252, 255), Color.rgb(224, 239, 255), Shader.TileMode.CLAMP));
        rect.set(0f, 0f, w, h);
        canvas.drawRoundRect(rect, dp(14), dp(14), paint);
        paint.setShader(null);

        float iconLeft = dp(12);
        float iconTop = dp(9);
        float iconSize = Math.min(h - dp(18), dp(46));
        drawFolder(canvas, iconLeft, iconTop, iconSize);

        float textLeft = iconLeft + iconSize + dp(12);
        float available = Math.max(dp(80), w - textLeft - dp(12));

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setColor(Color.rgb(19, 58, 126));
        textPaint.setTextSize(fitText("Спасибо, что вы с нами", available, Math.min(dp(18), h * 0.30f), dp(12)));
        float titleY = Math.max(dp(20), h * 0.43f);
        canvas.drawText("Спасибо, что вы с нами", textLeft, titleY, textPaint);

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setColor(Color.rgb(93, 126, 173));
        String subtitle = w > dp(420)
                ? "Пока реклама загружается, можно продолжить работу"
                : "Реклама появится здесь после загрузки";
        textPaint.setTextSize(fitText(subtitle, available, Math.min(dp(12), h * 0.19f), dp(9)));
        canvas.drawText(subtitle, textLeft, Math.min(h - dp(9), titleY + dp(18)), textPaint);
    }

    private void drawFolder(Canvas canvas, float x, float y, float size) {
        float bottom = y + size;
        float top = y + size * 0.30f;
        float right = x + size;

        paint.setColor(Color.rgb(43, 129, 236));
        rect.set(x, top, right, bottom);
        canvas.drawRoundRect(rect, size * 0.13f, size * 0.13f, paint);

        Path tab = new Path();
        tab.moveTo(x + size * 0.08f, top + size * 0.06f);
        tab.lineTo(x + size * 0.35f, y + size * 0.14f);
        tab.lineTo(x + size * 0.57f, top + size * 0.06f);
        tab.close();
        paint.setColor(Color.rgb(83, 164, 247));
        canvas.drawPath(tab, paint);

        paint.setColor(Color.WHITE);
        rect.set(x + size * 0.25f, y, x + size * 0.76f, y + size * 0.60f);
        canvas.drawRoundRect(rect, size * 0.07f, size * 0.07f, paint);

        paint.setColor(Color.rgb(98, 168, 242));
        float lineLeft = x + size * 0.34f;
        float lineRight = x + size * 0.68f;
        float stroke = Math.max(dp(1), size * 0.045f);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(lineLeft, y + size * 0.24f, lineRight, y + size * 0.24f, paint);
        canvas.drawLine(lineLeft, y + size * 0.34f, lineRight, y + size * 0.34f, paint);
        canvas.drawLine(lineLeft, y + size * 0.44f, x + size * 0.60f, y + size * 0.44f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);

        paint.setColor(Color.rgb(255, 190, 39));
        canvas.drawCircle(x + size * 0.84f, y + size * 0.78f, size * 0.12f, paint);
    }

    private float fitText(String value, float availableWidth, float preferred, float minimum) {
        float size = preferred;
        textPaint.setTextSize(size);
        while (size > minimum && textPaint.measureText(value) > availableWidth) {
            size -= dp(0.5f);
            textPaint.setTextSize(size);
        }
        return size;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
