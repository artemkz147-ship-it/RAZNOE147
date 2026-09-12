package ru.offline100.games;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public final class MainActivity extends Activity {
    private static final String TAG = "Offline100";
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            webView = new WebView(getApplicationContext());
            webView.setBackgroundColor(Color.rgb(16,19,35));
            configureWebView(webView);
            setContentView(webView);
            webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
            applyImmersiveMode();
            webView.loadUrl("file:///android_asset/index.html");
        } catch (Throwable fatal) {
            Log.e(TAG, "STARTUP_FAILED", fatal);
            throw fatal;
        }
    }

    @SuppressLint("SetJavaScriptEnabled") private void configureWebView(WebView view) {
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(false);
        }
        view.setWebChromeClient(new WebChromeClient());
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                Uri uri=request.getUrl(); return uri!=null && !"file".equalsIgnoreCase(uri.getScheme());
            }
            @Override public void onPageFinished(WebView v,String url) {
                Log.i(TAG,"PAGE_FINISHED "+url);
                v.postDelayed(() -> v.evaluateJavascript("String(document.querySelectorAll('.game-card').length)", value -> Log.i(TAG,"GAME_CARD_COUNT="+value)),700);
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                Log.e(TAG,"WEBVIEW_RENDERER_GONE didCrash="+detail.didCrash()); return false;
            }
        });
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override protected void onResume(){super.onResume();applyImmersiveMode();if(webView!=null)webView.onResume();}
    @Override protected void onPause(){if(webView!=null)webView.onPause();super.onPause();}
    @Override public void onBackPressed(){
        if(webView==null){super.onBackPressed();return;}
        webView.evaluateJavascript("Boolean(window.handleAndroidBack && window.handleAndroidBack())", value->{if(!"true".equals(value)){if(webView.canGoBack())webView.goBack();else MainActivity.super.onBackPressed();}});
    }
    @Override protected void onDestroy(){
        if(webView!=null){webView.removeJavascriptInterface("AndroidBridge");webView.loadUrl("about:blank");webView.stopLoading();webView.setWebChromeClient(null);webView.setWebViewClient(null);webView.destroy();webView=null;}
        super.onDestroy();
    }
}
