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
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String TAG = "Offline100";
    private WebView webView;
    private String testGame;
    private boolean testFinish;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            testGame = getIntent() == null ? null : getIntent().getStringExtra("testGame");
            testFinish = getIntent() != null && getIntent().getBooleanExtra("testFinish", false);
            Log.i(TAG,"TEST_REQUEST game="+testGame+" finish="+testFinish);
            webView = new WebView(this);
            webView.setBackgroundColor(Color.rgb(10,14,28));
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
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(false);
        }
        view.setWebChromeClient(new WebChromeClient());
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                Uri uri=request.getUrl();
                return uri!=null && !"file".equalsIgnoreCase(uri.getScheme());
            }
            @Override public void onPageFinished(WebView v,String url) {
                Log.i(TAG,"PAGE_FINISHED "+url);
                v.postDelayed(() -> {
                    v.evaluateJavascript("String(document.querySelectorAll('.game-card').length)", value -> Log.i(TAG,"GAME_CARD_COUNT="+value));
                    if(testGame!=null && !testGame.isEmpty()){
                        String js="Boolean(window.__openGameForTest && window.__openGameForTest("+JSONObject.quote(testGame)+"))";
                        v.evaluateJavascript(js, result -> {
                            Log.i(TAG,"OPEN_TEST_RESULT="+result+" game="+testGame);
                            v.postDelayed(() -> {
                                if(testFinish) v.evaluateJavascript("Boolean(window.__finishForTest && window.__finishForTest())", x -> Log.i(TAG,"FINISH_TEST_RESULT="+x));
                                v.postDelayed(() -> {
                                    v.evaluateJavascript("JSON.stringify(window.__qaSnapshot && window.__qaSnapshot())", snap -> Log.i(TAG,"QA_SNAPSHOT="+snap));
                                    if(testFinish) v.evaluateJavascript("JSON.stringify(window.__resultSnapshot && window.__resultSnapshot())", snap -> Log.i(TAG,"RESULT_SNAPSHOT="+snap));
                                },500);
                            },700);
                        });
                    }
                },900);
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                Log.e(TAG,"WEBVIEW_RENDERER_GONE didCrash="+detail.didCrash());
                return false;
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
