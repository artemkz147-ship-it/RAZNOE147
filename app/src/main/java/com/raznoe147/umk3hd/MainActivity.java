package com.raznoe147.umk3hd;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
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
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String TAG = "UMK3HD";
    private static final String BUILD_VERSION = "0.7.0";
    private static final String START_URL = "file:///android_asset/index.html";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private FrameLayout root;
    private TextView diagnostic;
    private boolean pageFinished = false;
    private boolean runtimeReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "BOOT onCreate version=" + BUILD_VERSION + " sdk=" + Build.VERSION.SDK_INT);
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            if (Build.VERSION.SDK_INT >= 28) {
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

    private void createWebView() throws Exception {
        String[] topAssets = getAssets().list("");
        boolean hasIndex = false;
        if (topAssets != null) {
            for (String name : topAssets) if ("index.html".equals(name)) { hasIndex = true; break; }
        }
        Log.i(TAG, "BOOT asset index=" + hasIndex);
        if (!hasIndex) throw new IllegalStateException("index.html missing from APK assets");

        // Activity context is intentional: it gives WebView the correct display/configuration lifecycle.
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkLoads(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.setBackgroundColor(Color.BLACK);
        // Do not force a separate hardware layer: default WebView compositing is more stable on vendor GPUs.

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                Log.i(TAG, "BOOT pageStarted " + url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                pageFinished = true;
                Log.i(TAG, "BOOT pageFinished " + url);
                verifyRuntimeWithRetry(view, 0);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                String u = request != null && request.getUrl() != null ? request.getUrl().toString() : "unknown";
                String msg = error != null ? String.valueOf(error.getDescription()) : "unknown";
                Log.e(TAG, "WEB error url=" + u + " msg=" + msg);
                if (START_URL.equals(u)) showDiagnostic("Ошибка загрузки интерфейса:\n" + msg);
            }

            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                boolean crash = detail != null && detail.didCrash();
                int priority = detail != null ? detail.rendererPriorityAtExit() : -1;
                Log.e(TAG, "WEB rendererGone crash=" + crash + " priority=" + priority);
                showDiagnostic("WebView остановил графический процесс. Код: RENDER_PROCESS_GONE\ncrash=" + crash + " priority=" + priority);
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.v(TAG, "JS " + cm.messageLevel() + ": " + cm.message() + " @" + cm.lineNumber());
                return true;
            }
        });

        root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        Log.i(TAG, "BOOT loadUrl " + START_URL);
        webView.loadUrl(START_URL);

        mainHandler.postDelayed(() -> {
            if (!pageFinished && !isFinishing()) {
                Log.e(TAG, "BOOT watchdog page-not-finished");
                showDiagnostic("WebView не завершил загрузку. Код: PAGE_LOAD_TIMEOUT");
            }
        }, 15000);
    }

    private void verifyRuntimeWithRetry(WebView view, int attempt) {
        if (view == null || runtimeReady || isFinishing()) return;
        view.evaluateJavascript(
            "(function(){try{return !!(window.__UMK3_DEBUG__&&window.__UMK3_RASTER__&&window.UMK3_BUILD&&window.UMK3_BUILD.version==='0.7.0');}catch(e){return false;}})()",
            value -> {
                boolean ready = "true".equals(value);
                Log.i(TAG, "Runtime probe attempt=" + attempt + " value=" + value);
                if (ready) {
                    runtimeReady = true;
                    Log.i(TAG, "Runtime check: true");
                    return;
                }
                if (attempt < 19) {
                    mainHandler.postDelayed(() -> verifyRuntimeWithRetry(view, attempt + 1), 750);
                } else {
                    Log.e(TAG, "Runtime check: false after retries");
                    showDiagnostic("Игра загрузилась не полностью. Код: WEB_RUNTIME_NOT_READY");
                }
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
            diagnostic.setBackgroundColor(0xEE650000);
            diagnostic.setTextSize(16f);
            diagnostic.setGravity(Gravity.CENTER);
            diagnostic.setPadding(30, 30, 30, 30);
            root.addView(diagnostic, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        }
        diagnostic.setText(text + "\n\nВерсия " + BUILD_VERSION);
        diagnostic.bringToFront();
    }

    private void enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= 30) {
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
    @Override protected void onResume() { super.onResume(); Log.i(TAG, "LIFECYCLE onResume"); enterImmersiveMode(); if (webView != null) webView.onResume(); }
    @Override protected void onPause() { Log.i(TAG, "LIFECYCLE onPause"); if (webView != null) webView.onPause(); super.onPause(); }
    @Override protected void onDestroy() { Log.i(TAG, "LIFECYCLE onDestroy"); mainHandler.removeCallbacksAndMessages(null); if (webView != null) { webView.stopLoading(); webView.destroy(); webView=null; } super.onDestroy(); }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
}
