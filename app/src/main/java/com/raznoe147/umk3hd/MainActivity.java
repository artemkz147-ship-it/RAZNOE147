package com.raznoe147.umk3hd;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String TAG = "UMK3HD";
    private static final String BUILD_VERSION = "0.7.0";
    private WebView webView;
    private FrameLayout root;
    private TextView diagnostic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(lp);
            }
            root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);
            setContentView(root);
            createWebView();
            enterImmersiveMode();
        } catch (Throwable t) {
            Log.e(TAG, "Fatal boot error", t);
            showDiagnostic("Не удалось запустить игру:\n" + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void createWebView() {
        webView = new WebView(getApplicationContext());
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.setBackgroundColor(Color.BLACK);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "Page finished: " + url);
                new Handler(Looper.getMainLooper()).postDelayed(() -> verifyRuntime(view), 3500);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(TAG, "JS " + cm.messageLevel() + ": " + cm.message() + " @" + cm.lineNumber());
                return true;
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void verifyRuntime(WebView view) {
        if (view == null) return;
        view.evaluateJavascript("(function(){return !!(window.__UMK3_DEBUG__&&window.__UMK3_RASTER__&&window.UMK3_BUILD&&window.UMK3_BUILD.version==='0.7.0');})()", value -> {
            Log.i(TAG, "Runtime check: " + value);
            if (!"true".equals(value)) showDiagnostic("Игра загрузилась не полностью. Код ошибки: WEB_RUNTIME_NOT_READY");
        });
    }

    private void showDiagnostic(String text) {
        if (root == null) {
            root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);
            setContentView(root);
        }
        if (diagnostic == null) {
            diagnostic = new TextView(this);
            diagnostic.setTextColor(Color.WHITE);
            diagnostic.setBackgroundColor(0xDD660000);
            diagnostic.setTextSize(16f);
            diagnostic.setGravity(Gravity.CENTER);
            diagnostic.setPadding(30,30,30,30);
            root.addView(diagnostic, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        }
        diagnostic.setText(text + "\n\nВерсия " + BUILD_VERSION);
        diagnostic.bringToFront();
    }

    private void enterImmersiveMode() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enterImmersiveMode(); }
    @Override protected void onResume() { super.onResume(); enterImmersiveMode(); if (webView != null) webView.onResume(); }
    @Override protected void onPause() { if (webView != null) webView.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if (webView != null) { webView.stopLoading(); webView.destroy(); webView=null; } super.onDestroy(); }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
}
