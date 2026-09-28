package com.example.smokeinterval;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = 0xFF08110D;
    private static final int SURFACE = 0xFF111C17;
    private static final int SURFACE_2 = 0xFF17251D;
    private static final int TEXT = 0xFFF4F8F5;
    private static final int MUTED = 0xFF92A099;
    private static final int GREEN = 0xFF54E68A;
    private static final int LINE = 0xFF26352E;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout heroCard;
    private TextView dayPill, statusLabel, statusText, detailText, intervalChip, allowedChip;
    private ProgressBar timerProgress;
    private TextView statsCaption, todayStat, adherenceStat, averageStat, bestStat;
    private StatsChartView chartView;
    private Button startStopButton, smokedButton, snoozeButton;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getWindow().getDecorView().setSystemUiVisibility(0);
        NotificationHelper.ensureChannel(this);
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        NotificationHelper.ensureChannel(this);
        if (PlanStore.isRunning(this)) AlarmScheduler.scheduleNext(this);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(38));
        scroll.addView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(text("SmokeInterval", 27f, TEXT, Typeface.BOLD), matchWrap());
        TextView subtitle = text("Меньше сигарет. Больше контроля.", 13f, MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(2), 0, 0);
        brand.addView(subtitle, matchWrap());
        top.addView(brand, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button settings = new Button(this);
        settings.setText("⚙");
        settings.setTextSize(23f);
        settings.setTextColor(TEXT);
        settings.setAllCaps(false);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(0,0,0,0);
        settings.setMinWidth(dp(46));
        settings.setMinHeight(dp(46));
        settings.setStateListAnimator(null);
        settings.setBackground(roundRect(SURFACE_2, 16, LINE, 1));
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(settings, new LinearLayout.LayoutParams(dp(46), dp(46)));
        root.addView(top, matchWrap());

        heroCard = new LinearLayout(this);
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setGravity(Gravity.CENTER_HORIZONTAL);
        heroCard.setPadding(dp(20), dp(18), dp(20), dp(20));
        heroCard.setElevation(dp(3));
        root.addView(heroCard, matchWrapWithTop(dp(24)));

        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        statusLabel = text("ДО СЛЕДУЮЩЕЙ СИГАРЕТЫ", 11f, 0xFFB9C8C0, Typeface.BOLD);
        statusLabel.setLetterSpacing(0.08f);
        heroTop.addView(statusLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dayPill = text("День 1", 12f, TEXT, Typeface.BOLD);
        dayPill.setGravity(Gravity.CENTER);
        dayPill.setPadding(dp(10), dp(6), dp(10), dp(6));
        dayPill.setBackground(roundRect(0x3329D373, 14, 0, 0));
        heroTop.addView(dayPill, wrapWrap());
        heroCard.addView(heroTop, matchWrap());

        statusText = text("60:00", 54f, Color.WHITE, Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(22), 0, dp(8));
        heroCard.addView(statusText, matchWrap());

        detailText = text("Первая пауза — 1 час", 14f, 0xFFCBD7D1, Typeface.NORMAL);
        detailText.setGravity(Gravity.CENTER);
        detailText.setLineSpacing(dp(3), 1f);
        heroCard.addView(detailText, matchWrap());

        timerProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        timerProgress.setMax(1000);
        timerProgress.setProgressTintList(ColorStateList.valueOf(GREEN));
        timerProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF284039));
        LinearLayout.LayoutParams pp = matchWrapWithTop(dp(20));
        pp.height = dp(9);
        heroCard.addView(timerProgress, pp);

        LinearLayout chips = horizontalRow();
        chips.setGravity(Gravity.CENTER);
        intervalChip = chip("Интервал · 60 мин");
        allowedChip = chip("Разрешено · —");
        chips.addView(intervalChip, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams chip2 = new LinearLayout.LayoutParams(0, dp(42), 1f);
        chip2.leftMargin = dp(8);
        chips.addView(allowedChip, chip2);
        heroCard.addView(chips, matchWrapWithTop(dp(16)));

        smokedButton = primaryButton("✓   Покурил сейчас");
        smokedButton.setOnClickListener(v -> {
            if (!PlanStore.isRunning(this)) return;
            PlanStore.markSmoked(this, System.currentTimeMillis());
            NotificationHelper.cancel(this);
            AlarmScheduler.scheduleNext(this);
            render();
        });
        root.addView(smokedButton, matchWrapWithTop(dp(14)));

        snoozeButton = secondaryButton("◷   Отложить на 10 минут");
        snoozeButton.setOnClickListener(v -> {
            if (!PlanStore.isRunning(this)) return;
            long now = System.currentTimeMillis();
            if (now >= PlanStore.getNextAllowedMs(this)) {
                PlanStore.snooze(this, now);
                NotificationHelper.cancel(this);
                AlarmScheduler.scheduleNext(this);
                render();
            }
        });
        root.addView(snoozeButton, matchWrapWithTop(dp(9)));

        startStopButton = textButton("Начать план");
        startStopButton.setOnClickListener(v -> togglePlan());
        root.addView(startStopButton, matchWrapWithTop(dp(3)));

        addSectionHeader(root, "Статистика", "Видно не только количество, но и качество интервалов");

        LinearLayout statsRow1 = horizontalRow();
        todayStat = addMetric(statsRow1, "Сегодня", "●");
        adherenceStat = addMetric(statsRow1, "По плану", "✓");
        root.addView(statsRow1, matchWrap());

        LinearLayout statsRow2 = horizontalRow();
        averageStat = addMetric(statsRow2, "Средний интервал", "↔");
        bestStat = addMetric(statsRow2, "Лучший интервал", "★");
        root.addView(statsRow2, matchWrapWithTop(dp(10)));

        LinearLayout chartCard = card(SURFACE, 22, LINE);
        chartCard.setPadding(dp(16), dp(16), dp(12), dp(12));
        chartCard.addView(text("Последние 7 дней", 17f, TEXT, Typeface.BOLD), matchWrap());
        TextView chartHint = text("Отмеченные сигареты по дням", 13f, MUTED, Typeface.NORMAL);
        chartHint.setPadding(0, dp(3), 0, dp(7));
        chartCard.addView(chartHint, matchWrap());
        chartView = new StatsChartView(this);
        chartCard.addView(chartView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(185)));
        root.addView(chartCard, matchWrapWithTop(dp(12)));

        statsCaption = text("", 13f, MUTED, Typeface.NORMAL);
        statsCaption.setLineSpacing(dp(3), 1f);
        statsCaption.setPadding(dp(4), dp(12), dp(4), 0);
        root.addView(statsCaption, matchWrap());

        LinearLayout info = card(SURFACE_2, 20, LINE);
        info.setPadding(dp(16), dp(15), dp(16), dp(15));
        LinearLayout infoRow = horizontalRow();
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.addView(text("Настройки и разрешения", 15f, TEXT, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        infoRow.addView(text("›", 28f, MUTED, Typeface.NORMAL), wrapWrap());
        info.addView(infoRow, matchWrap());
        TextView infoHint = text("Точное время, уведомления и «Не беспокоить» находятся в отдельном меню.", 13f, MUTED, Typeface.NORMAL);
        infoHint.setPadding(0, dp(5), 0, 0);
        info.addView(infoHint, matchWrap());
        info.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(info, matchWrapWithTop(dp(20)));
        return scroll;
    }

    private TextView chip(String value) {
        TextView t = text(value, 12f, 0xFFD8E3DD, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(roundRect(0x331B392B, 14, 0, 0));
        return t;
    }

    private void addSectionHeader(LinearLayout root, String heading, String hint) {
        TextView h = text(heading, 22f, TEXT, Typeface.BOLD);
        h.setPadding(0, dp(29), 0, 0);
        root.addView(h, matchWrap());
        TextView s = text(hint, 13f, MUTED, Typeface.NORMAL);
        s.setPadding(0, dp(3), 0, dp(13));
        root.addView(s, matchWrap());
    }

    private TextView addMetric(LinearLayout row, String label, String icon) {
        LinearLayout box = card(SURFACE, 20, LINE);
        box.setPadding(dp(14), dp(13), dp(14), dp(14));
        LinearLayout top = horizontalRow();
        TextView value = text("—", 25f, TEXT, Typeface.BOLD);
        top.addView(value, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView badge = text(icon, 15f, GREEN, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundRect(0x2229D373, 12, 0, 0));
        top.addView(badge, new LinearLayout.LayoutParams(dp(32), dp(32)));
        box.addView(top, matchWrap());
        TextView l = text(label, 12f, MUTED, Typeface.NORMAL);
        l.setPadding(0, dp(4), 0, 0);
        box.addView(l, matchWrap());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) p.leftMargin = dp(10);
        row.addView(box, p);
        return value;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        return row;
    }

    private void togglePlan() {
        if (!PlanStore.isRunning(this)) {
            long now = System.currentTimeMillis();
            PlanStore.start(this, now);
            AlarmScheduler.scheduleNext(this);
            render();
            if (!SettingsActivity.notificationsAllowed(this) || !AlarmScheduler.canScheduleExact(this)) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Остановить план?")
                    .setMessage("Таймер остановится. Накопленная статистика останется.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Остановить", (d, w) -> {
                        PlanStore.stop(this);
                        AlarmScheduler.cancel(this);
                        NotificationHelper.cancel(this);
                        render();
                    }).show();
        }
    }

    private void render() {
        boolean running = PlanStore.isRunning(this);
        long now = System.currentTimeMillis();

        if (!running) {
            styleHero(false);
            dayPill.setText("Готов к старту");
            statusLabel.setText("ПЛАН НЕ ЗАПУЩЕН");
            statusText.setText("60:00");
            detailText.setText("Первая пауза — 1 час\nКаждые сутки +10 минут");
            timerProgress.setProgress(0);
            intervalChip.setText("Интервал · 60 мин");
            allowedChip.setText("Разрешено · —");
            startStopButton.setText("Начать план");
            smokedButton.setEnabled(false);
            smokedButton.setAlpha(0.42f);
            snoozeButton.setEnabled(false);
            snoozeButton.setAlpha(0.42f);
        } else {
            long next = PlanStore.getNextAllowedMs(this);
            long intervalMs = PlanStore.currentIntervalMs(this, now);
            long intervalMin = intervalMs / 60000L;
            long day = PlanStore.dayNumber(this, now);
            dayPill.setText("День " + day);
            startStopButton.setText("Остановить план");
            smokedButton.setEnabled(true);
            smokedButton.setAlpha(1f);
            intervalChip.setText("Интервал · " + intervalMin + " мин");
            allowedChip.setText("Разрешено · " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(next)));

            long countdownStart = PlanStore.getCountdownStartMs(this);
            long span = Math.max(1L, next - countdownStart);
            long elapsed = Math.max(0L, Math.min(span, now - countdownStart));
            timerProgress.setProgress((int) Math.min(1000L, 1000L * elapsed / span));

            if (now >= next) {
                styleHero(true);
                statusLabel.setText("РАЗРЕШЕНО ПО ПЛАНУ");
                statusText.setText("Можно");
                long snooze = PlanStore.getSnoozeUntilMs(this);
                if (snooze > now) {
                    detailText.setText("Напоминание отложено\nещё на " + formatDuration(snooze - now));
                } else {
                    detailText.setText("Интервал выдержан\nНовая сигарета не копится");
                }
                timerProgress.setProgress(1000);
                snoozeButton.setEnabled(true);
                snoozeButton.setAlpha(1f);
            } else {
                styleHero(false);
                statusLabel.setText("ДО СЛЕДУЮЩЕЙ СИГАРЕТЫ");
                statusText.setText(formatDuration(next - now));
                detailText.setText("Держишь темп · день " + day + "\nследующая пауза " + intervalMin + " минут");
                snoozeButton.setEnabled(false);
                snoozeButton.setAlpha(0.42f);
            }
        }
        renderStats();
    }

    private void renderStats() {
        int total = StatsStore.totalSmokes(this);
        int today = StatsStore.countToday(this);
        int adherence = StatsStore.adherencePercent(this);
        long average = StatsStore.averageIntervalMs(this);
        long best = StatsStore.bestIntervalMs(this);
        todayStat.setText(String.valueOf(today));
        adherenceStat.setText(total == 0 ? "—" : adherence + "%");
        averageStat.setText(average <= 0L ? "—" : formatMinutes(average));
        bestStat.setText(best <= 0L ? "—" : formatMinutes(best));
        statsCaption.setText(total == 0
                ? "Статистика появится после первой отметки «Покурил»."
                : "Всего отмечено: " + total + ". «По плану» означает, что сигарета была отмечена не раньше разрешённого времени.");
        chartView.setData(StatsStore.last7DayCounts(this), StatsStore.last7DayLabels());
    }

    private void styleHero(boolean allowed) {
        int[] colors = allowed
                ? new int[]{0xFF14492E, 0xFF0E271B}
                : new int[]{0xFF13251C, 0xFF0D1914};
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        g.setCornerRadius(dp(28));
        g.setStroke(dp(1), allowed ? 0xFF2E9C5C : LINE);
        heroCard.setBackground(g);
        statusLabel.setTextColor(allowed ? GREEN : 0xFFB9C8C0);
        timerProgress.setProgressTintList(ColorStateList.valueOf(allowed ? GREEN : 0xFF47C979));
        timerProgress.setProgressBackgroundTintList(ColorStateList.valueOf(allowed ? 0xFF2B6744 : 0xFF284039));
    }

    private TextView text(String value, float size, int color, int style) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", style));
        return t;
    }

    private Button primaryButton(String value) {
        Button b = baseButton(value);
        b.setTextColor(0xFF062712);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setBackground(roundRect(GREEN, 19, 0, 0));
        b.setElevation(dp(2));
        return b;
    }

    private Button secondaryButton(String value) {
        Button b = baseButton(value);
        b.setTextColor(TEXT);
        b.setBackground(roundRect(SURFACE_2, 19, LINE, 1));
        return b;
    }

    private Button textButton(String value) {
        Button b = baseButton(value);
        b.setTextColor(MUTED);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinHeight(dp(42));
        return b;
    }

    private Button baseButton(String value) {
        Button b = new Button(this);
        b.setText(value); b.setTextSize(16f); b.setAllCaps(false); b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(56)); b.setPadding(dp(14), dp(8), dp(14), dp(8)); b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout card(int color, int radiusDp, int strokeColor) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(roundRect(color, radiusDp, strokeColor, strokeColor == 0 ? 0 : 1));
        return layout;
    }

    private GradientDrawable roundRect(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0 && strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private static String formatDuration(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long hours = total / 3600L, minutes = (total % 3600L) / 60L, seconds = total % 60L;
        if (hours > 0) return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private static String formatMinutes(long ms) {
        long totalMinutes = Math.max(0L, Math.round(ms / 60000.0));
        long h = totalMinutes / 60L, m = totalMinutes % 60L;
        if (h > 0 && m > 0) return h + "ч " + m + "м";
        if (h > 0) return h + "ч";
        return totalMinutes + "м";
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams wrapWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams matchWrapWithTop(int top) { LinearLayout.LayoutParams p = matchWrap(); p.topMargin = top; return p; }
}
