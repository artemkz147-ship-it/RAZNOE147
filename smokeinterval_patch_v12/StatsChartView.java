package com.example.smokeinterval;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class StatsChartView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mutedBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] counts = new int[7];
    private String[] labels = new String[]{"", "", "", "", "", "", ""};

    public StatsChartView(Context context) { super(context); init(); }
    public StatsChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        barPaint.setColor(0xFF54E68A);
        mutedBarPaint.setColor(0xFF22312A);
        textPaint.setColor(0xFF829088);
        textPaint.setTextSize(dp(12));
        textPaint.setTextAlign(Paint.Align.CENTER);
        countPaint.setColor(0xFFEAF5EE);
        countPaint.setTextSize(dp(12));
        countPaint.setFakeBoldText(true);
        countPaint.setTextAlign(Paint.Align.CENTER);
        setMinimumHeight(dp(178));
    }

    public void setData(int[] values, String[] dayLabels) {
        if (values != null && values.length == 7) counts = values.clone();
        if (dayLabels != null && dayLabels.length == 7) labels = dayLabels.clone();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float left = dp(6), right = w - dp(6), top = dp(20), bottom = h - dp(28);
        float chartH = Math.max(dp(60), bottom - top);
        int max = 1;
        for (int v : counts) max = Math.max(max, v);
        float slot = (right - left) / 7f;
        float barW = Math.min(dp(25), slot * 0.52f);
        float radius = dp(8);
        for (int i = 0; i < 7; i++) {
            float cx = left + slot * i + slot / 2f;
            RectF bg = new RectF(cx - barW/2f, top, cx + barW/2f, bottom);
            canvas.drawRoundRect(bg, radius, radius, mutedBarPaint);
            float fraction = counts[i] / (float) max;
            float bh = counts[i] == 0 ? 0f : Math.max(dp(10), chartH * fraction);
            RectF bar = new RectF(cx - barW/2f, bottom - bh, cx + barW/2f, bottom);
            if (bh > 0f) canvas.drawRoundRect(bar, radius, radius, barPaint);
            canvas.drawText(String.valueOf(counts[i]), cx, Math.max(dp(13), bottom - bh - dp(6)), countPaint);
            canvas.drawText(labels[i], cx, h - dp(7), textPaint);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
