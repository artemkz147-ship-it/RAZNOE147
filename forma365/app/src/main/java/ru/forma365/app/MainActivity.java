package ru.forma365.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 365;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(247, 249, 252));
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(247, 249, 252));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(true);

        webView.addJavascriptInterface(new AndroidBridge(this), "FormaAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String inject = "(function(){" +
                        "if(!document.getElementById('f365-enh-css')){" +
                        "var l=document.createElement('link');l.id='f365-enh-css';l.rel='stylesheet';l.href='enhancements.css';document.head.appendChild(l);}" +
                        "if(!document.getElementById('f365-enh-js')){" +
                        "var s=document.createElement('script');s.id='f365-enh-js';s.src='enhancements.js';document.body.appendChild(s);}" +
                        "})();";
                view.evaluateJavascript(inject, null);
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public static class AndroidBridge {
        private final Activity activity;
        private final Context context;

        AndroidBridge(Activity activity) {
            this.activity = activity;
            this.context = activity.getApplicationContext();
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33 &&
                    activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                activity.runOnUiThread(() -> activity.requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST));
            }
        }

        @JavascriptInterface
        public void setDailyReminder(int hour, int minute, String gymDays) {
            hour = Math.max(0, Math.min(23, hour));
            minute = Math.max(0, Math.min(59, minute));
            SharedPreferences p = context.getSharedPreferences("forma365_native", Context.MODE_PRIVATE);
            p.edit()
                    .putBoolean("reminder_enabled", true)
                    .putInt("reminder_hour", hour)
                    .putInt("reminder_minute", minute)
                    .putString("gym_days", gymDays == null ? "1,3,5" : gymDays)
                    .apply();
            ReminderScheduler.schedule(context, hour, minute);
        }

        @JavascriptInterface
        public void cancelDailyReminder() {
            context.getSharedPreferences("forma365_native", Context.MODE_PRIVATE)
                    .edit().putBoolean("reminder_enabled", false).apply();
            ReminderScheduler.cancel(context);
        }

        @JavascriptInterface
        public String getAppVersion() {
            return "1.1.0";
        }
    }
}
