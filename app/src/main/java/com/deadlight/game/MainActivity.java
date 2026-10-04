package com.deadlight.game;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;

public final class MainActivity extends Activity {
    private WebView game;
    private static final String ORIGIN = "appassets.androidplatform.net";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fullscreen();
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        game = new WebView(this);
        game.setBackgroundColor(0xff10191b);
        WebSettings settings = game.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        final WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        game.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if ("https".equals(request.getUrl().getScheme()) && ORIGIN.equals(request.getUrl().getHost())) {
                    WebResourceResponse result = assets.shouldInterceptRequest(request.getUrl());
                    if (result != null) return result;
                }
                return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found", java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !("https".equals(request.getUrl().getScheme()) && ORIGIN.equals(request.getUrl().getHost()) && request.getUrl().getPath().startsWith("/assets/"));
            }
        });
        setContentView(game);
        game.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }
    @SuppressWarnings("deprecation") private void fullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (focus) fullscreen(); }
    @Override protected void onPause() {
        if (game != null) {
            game.evaluateJavascript("if(window.__game&&window.__game.state==='play'){window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));}", null);
            game.onPause();
        }
        super.onPause();
    }
    @Override protected void onResume() { super.onResume(); if (game != null) game.onResume(); }
    @Override @SuppressWarnings("deprecation") public void onBackPressed() {
        game.evaluateJavascript("(function(){if(window.__game&&['play','paused','panel'].includes(window.__game.state)){window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));return true;}return false;})()", result -> { if (!"true".equals(result)) finish(); });
    }
    @Override protected void onDestroy() { if (game != null) { game.stopLoading(); game.destroy(); game = null; } super.onDestroy(); }
}
