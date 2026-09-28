package com.example.smokeinterval;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 45;
    private static final int BG = 0xFF08110D, SURFACE = 0xFF111C17, SURFACE_2 = 0xFF17251D;
    private static final int TEXT = 0xFFF4F8F5, MUTED = 0xFF92A099, GREEN = 0xFF54E68A, LINE = 0xFF26352E;
    private TextView notifState, exactState, dndState;
    private Button notifButton, exactButton, dndButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getWindow().getDecorView().setSystemUiVisibility(0);
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        NotificationHelper.ensureChannel(this);
        renderStates();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(38));
        scroll.addView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("‹"); back.setTextSize(32f); back.setTextColor(TEXT); back.setAllCaps(false);
        back.setGravity(Gravity.CENTER); back.setPadding(0,0,0,0); back.setStateListAnimator(null);
        back.setBackground(roundRect(SURFACE_2, 15, LINE, 1)); back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text("Настройки", 26f, TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(14);
        top.addView(title, tp);
        root.addView(top, matchWrap());

        addSection(root, "План и интервалы", "Основная логика постепенного сокращения");
        LinearLayout plan = card();
        addInfoRow(plan, "Стартовый интервал", "1 час");
        addDivider(plan);
        addInfoRow(plan, "Увеличение каждые сутки", "+10 минут");
        addDivider(plan);
        addInfoRow(plan, "Отложить напоминание", "10 минут");
        addDivider(plan);
        addInfoRow(plan, "Сигареты", "Не копятся");
        root.addView(plan, matchWrap());

        addSection(root, "Уведомления", "Для точной работы в фоне Android требует три разрешения");
        LinearLayout permissions = card();
        notifState = addPermissionRow(permissions, "Уведомления", "Показывать сигнал, когда интервал завершён");
        notifButton = actionButton("Разрешить");
        notifButton.setOnClickListener(v -> requestNotifications());
        permissions.addView(notifButton, buttonParams());
        addDivider(permissions);
        exactState = addPermissionRow(permissions, "Точные будильники", "Срабатывать вовремя даже при закрытом приложении");
        exactButton = actionButton("Открыть разрешение");
        exactButton.setOnClickListener(v -> openExactAlarmSettings());
        permissions.addView(exactButton, buttonParams());
        addDivider(permissions);
        dndState = addPermissionRow(permissions, "Обход «Не беспокоить»", "Разрешить важному уведомлению проходить через DND");
        dndButton = actionButton("Настроить");
        dndButton.setOnClickListener(v -> openDndSettings());
        permissions.addView(dndButton, buttonParams());
        root.addView(permissions, matchWrap());

        addSection(root, "Системные настройки", "Дополнительный контроль Android");
        LinearLayout system = card();
        addActionRow(system, "Канал уведомлений", "Звук, вибрация и важность", v -> openChannelSettings());
        addDivider(system);
        addActionRow(system, "Настройки приложения", "Батарея, разрешения и фоновые ограничения", v -> openAppSettings());
        root.addView(system, matchWrap());

        addSection(root, "О приложении", "SmokeInterval 1.2.0");
        LinearLayout about = card();
        TextView aboutText = text("Таймер хранит абсолютное время следующей сигареты, поэтому закрытие приложения не сбивает отсчёт. После перезагрузки телефона план восстанавливается автоматически. Статистика хранится локально на устройстве.", 14f, MUTED, Typeface.NORMAL);
        aboutText.setLineSpacing(dp(4), 1f);
        aboutText.setPadding(dp(16), dp(16), dp(16), dp(16));
        about.addView(aboutText, matchWrap());
        root.addView(about, matchWrap());
        return scroll;
    }

    private void addSection(LinearLayout root, String title, String subtitle) {
        TextView t = text(title, 20f, TEXT, Typeface.BOLD); t.setPadding(0, dp(26), 0, 0); root.addView(t, matchWrap());
        TextView s = text(subtitle, 13f, MUTED, Typeface.NORMAL); s.setPadding(0, dp(3), 0, dp(11)); root.addView(s, matchWrap());
    }

    private void addInfoRow(LinearLayout card, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(15), dp(16), dp(15));
        row.addView(text(label, 14f, TEXT, Typeface.NORMAL), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text(value, 14f, GREEN, Typeface.BOLD), wrapWrap());
        card.addView(row, matchWrap());
    }

    private TextView addPermissionRow(LinearLayout card, String label, String hint) {
        LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setPadding(dp(16), dp(15), dp(16), dp(2));
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(text(label, 15f, TEXT, Typeface.BOLD), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView state = text("Проверка…", 12f, MUTED, Typeface.BOLD); row.addView(state, wrapWrap());
        wrap.addView(row, matchWrap());
        TextView h = text(hint, 13f, MUTED, Typeface.NORMAL); h.setPadding(0, dp(4), 0, 0); wrap.addView(h, matchWrap());
        card.addView(wrap, matchWrap());
        return state;
    }

    private void addActionRow(LinearLayout card, String label, String hint, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(15), dp(12), dp(15));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(label, 15f, TEXT, Typeface.BOLD), matchWrap());
        TextView h = text(hint, 13f, MUTED, Typeface.NORMAL); h.setPadding(0, dp(3), 0, 0); copy.addView(h, matchWrap());
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text("›", 28f, MUTED, Typeface.NORMAL), wrapWrap());
        row.setOnClickListener(listener); card.addView(row, matchWrap());
    }

    private void renderStates() {
        boolean n = notificationsAllowed(this), exact = AlarmScheduler.canScheduleExact(this), bypass = NotificationHelper.canBypassDnd(this);
        setState(notifState, n); setState(exactState, exact); setState(dndState, bypass);
        notifButton.setVisibility(n || Build.VERSION.SDK_INT < 33 ? View.GONE : View.VISIBLE);
        exactButton.setVisibility(exact || Build.VERSION.SDK_INT < Build.VERSION_CODES.S ? View.GONE : View.VISIBLE);
        dndButton.setText(bypass ? "Настроено" : "Настроить"); dndButton.setEnabled(!bypass); dndButton.setAlpha(bypass ? 0.5f : 1f);
    }

    private void setState(TextView view, boolean ok) {
        view.setText(ok ? "✓ Готово" : "Нужно включить");
        view.setTextColor(ok ? GREEN : 0xFFFFB35C);
    }

    public static boolean notificationsAllowed(Context context) {
        if (Build.VERSION.SDK_INT >= 33) return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        return context.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { openAppSettings(); }
    }

    private void openDndSettings() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        try {
            if (!nm.isNotificationPolicyAccessGranted()) startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            else { NotificationHelper.ensureChannel(this); openChannelSettings(); }
        } catch (Exception e) { openAppSettings(); }
    }

    private void openChannelSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            i.putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_ID);
            startActivity(i);
        } catch (Exception e) { openAppSettings(); }
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackground(roundRect(SURFACE, 20, LINE, 1)); return l;
    }

    private void addDivider(LinearLayout card) {
        View d = new View(this); d.setBackgroundColor(LINE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)); p.leftMargin = dp(16); p.rightMargin = dp(16);
        card.addView(d, p);
    }

    private Button actionButton(String label) {
        Button b = new Button(this); b.setText(label); b.setTextSize(13f); b.setTextColor(GREEN); b.setAllCaps(false); b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(42)); b.setBackground(roundRect(0x221FE178, 14, 0, 0)); b.setStateListAnimator(null); return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = matchWrap(); p.leftMargin = dp(16); p.rightMargin = dp(16); p.topMargin = dp(9); p.bottomMargin = dp(14); return p;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setTypeface(Typeface.create("sans-serif", style)); return t;
    }

    private GradientDrawable roundRect(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0 && strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor); return d;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams wrapWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
}
