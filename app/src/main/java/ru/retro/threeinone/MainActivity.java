package ru.retro.threeinone;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.webkit.WebViewAssetLoader;

public final class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 701;
    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";

    private FrameLayout root;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private WebViewAssetLoader assetLoader;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 9, 12));
        setContentView(root);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        createWebView();
        root.post(this::enterImmersiveSafely);
    }

    private void enterImmersiveSafely() {
        try {
            final View decor = getWindow().getDecorView();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Throwable ignored) {
            // Fullscreen is cosmetic; never allow it to crash app startup.
        }
    }

    private void focusWebView() {
        try {
            if (webView != null) {
                webView.setFocusable(true);
                webView.setFocusableInTouchMode(true);
                webView.requestFocus(View.FOCUS_DOWN);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && root != null) {
            root.post(this::enterImmersiveSafely);
            root.post(this::focusWebView);
        }
    }

    private void createWebView() {
        try {
            if (webView != null) {
                root.removeView(webView);
                webView.destroy();
            }

            webView = new WebView(this);
            webView.setBackgroundColor(Color.rgb(8, 9, 12));
            webView.setFocusable(true);
            webView.setFocusableInTouchMode(true);
            root.addView(webView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
            s.setSupportZoom(false);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(false);
            s.setAllowFileAccess(false);
            s.setAllowContentAccess(false);
            s.setAllowFileAccessFromFileURLs(false);
            s.setAllowUniversalAccessFromFileURLs(false);

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                    if (fileCallback != null) fileCallback.onReceiveValue(null);
                    fileCallback = callback;
                    Intent intent;
                    try {
                        intent = params.createIntent();
                    } catch (Throwable ignored) {
                        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                    }
                    try {
                        startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                        return true;
                    } catch (Throwable failure) {
                        fileCallback.onReceiveValue(null);
                        fileCallback = null;
                        return false;
                    }
                }
            });

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    WebResourceResponse response = assetLoader.shouldInterceptRequest(request.getUrl());
                    return response != null ? response : super.shouldInterceptRequest(view, request);
                }

                @Override
                @SuppressWarnings("deprecation")
                public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                    WebResourceResponse response = assetLoader.shouldInterceptRequest(Uri.parse(url));
                    return response != null ? response : super.shouldInterceptRequest(view, url);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    focusWebView();
                }

                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    showRendererRecovery();
                    return true;
                }
            });

            webView.loadUrl(START_URL);
            root.post(this::focusWebView);
        } catch (Throwable failure) {
            showFatalError("Не удалось создать системный WebView: " + failure.getClass().getSimpleName());
        }
    }

    private void showRendererRecovery() {
        runOnUiThread(() -> showRecoveryMessage(
                "Процесс WebView был остановлен системой. Нажмите здесь, чтобы перезапустить меню."));
    }

    private void showFatalError(String text) {
        runOnUiThread(() -> showRecoveryMessage(text + "\n\nНажмите здесь, чтобы попробовать снова."));
    }

    private void showRecoveryMessage(String text) {
        if (webView != null) {
            try {
                root.removeView(webView);
                webView.destroy();
            } catch (Throwable ignored) {}
            webView = null;
        }
        root.removeAllViews();
        TextView message = new TextView(MainActivity.this);
        message.setText(text);
        message.setTextColor(Color.WHITE);
        message.setTextSize(18f);
        message.setGravity(android.view.Gravity.CENTER);
        message.setPadding(48, 48, 48, 48);
        message.setOnClickListener(v -> {
            root.removeAllViews();
            createWebView();
            root.post(this::enterImmersiveSafely);
        });
        root.addView(message, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;
        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        fileCallback.onReceiveValue(result);
        fileCallback = null;
        if (root != null) root.post(this::focusWebView);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        if (root != null) {
            root.post(this::enterImmersiveSafely);
            root.post(this::focusWebView);
        }
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (webView != null) {
            try {
                root.removeView(webView);
                webView.destroy();
            } catch (Throwable ignored) {}
            webView = null;
        }
        super.onDestroy();
    }
}
