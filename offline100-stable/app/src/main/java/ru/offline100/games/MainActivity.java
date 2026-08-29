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
    private static final String[] QA_GAMES = {
            "ttt","connect4","snake","mines","fifteen","g2048","memory","pipes","sudoku","blocks","breakout","words","parking","liquid","maze","reversi","gomoku","nim","lights","hanoi",
            "simon","numbers","target","reaction","math","guess","dots","chain","pong","dodge","rps","higher","blackjack","war","hangman","anagram","wordchain","odd","colormatch","memgrid",
            "route","queens","magic","oneline","stack","flappy","catch","space","runner","compare","pyramid13","suithunt","cardmemory","exact21","tenpairs","cipher","proverb","missing",
            "wordbuild","categoryword","takuzu","latin","knight","arrows","flood","gears","balance","numsort","timer","ballsort","shells","mole","lanes","zigzag","precision","solpairs",
            "redblack","cardfour","cardstairs","cardsum","wordfrom","oddletter","alphabet","syllables","wordlength","sequence","parity","colorlinks","tiles3","codebreak","tap30",
            "stopsignal","orbit","coinfall","minigolf","emojimem","changed","battleship","checkers","escape"
    };

    private WebView webView;
    private String testGame;
    private boolean testFinish;
    private boolean testAll;
    private int testFrom;
    private int readyAttempts;
    private boolean qaReady;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            testGame = getIntent() == null ? null : getIntent().getStringExtra("testGame");
            testFinish = getIntent() != null && getIntent().getBooleanExtra("testFinish", false);
            testAll = getIntent() != null && getIntent().getBooleanExtra("testAll", false);
            testFrom = getIntent() == null ? 0 : getIntent().getIntExtra("testFrom", 0);
            if (testFrom < 0) testFrom = 0;
            if (testFrom > QA_GAMES.length) testFrom = QA_GAMES.length;
            Log.i(TAG,"TEST_REQUEST game="+testGame+" finish="+testFinish+" all="+testAll+" from="+testFrom);
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
                if (!qaReady) v.postDelayed(() -> waitForReady(v), 250);
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request != null && request.isForMainFrame()) Log.e(TAG,"MAIN_FRAME_ERROR "+error);
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

    private void waitForReady(WebView v) {
        if (qaReady || v == null) return;
        String js = "document.readyState==='complete' && document.querySelectorAll('.game-card').length===100 && typeof window.__openGameForTest==='function' && typeof window.__finishForTest==='function'";
        v.evaluateJavascript(js, value -> {
            if ("true".equals(value)) {
                qaReady = true;
                Log.i(TAG,"GAME_CARD_COUNT=\"100\"");
                logHomeQa(v);
                return;
            }
            readyAttempts++;
            if (readyAttempts < 80) v.postDelayed(() -> waitForReady(v), 500);
            else Log.e(TAG,"QA_READY_TIMEOUT attempts="+readyAttempts+" value="+value);
        });
    }

    private void logHomeQa(WebView v) {
        String js = "JSON.stringify((()=>{"+
                "const cards=[...document.querySelectorAll('.game-card')];"+
                "const ids=cards.map(c=>c.dataset.id||'');"+
                "const titleIssues=cards.filter(c=>{const h=c.querySelector('h3');if(!h)return true;return h.scrollWidth>h.clientWidth+1||h.scrollHeight>h.clientHeight+1}).map(c=>c.dataset.id);"+
                "return {cards:cards.length,unique:new Set(ids).size,aiCards:cards.filter(c=>c.querySelector('.ai-card-badge')).length,bodyOverflow:document.documentElement.scrollWidth>window.innerWidth+2,titleIssues,viewport:[innerWidth,innerHeight]};"+
                "})())";
        v.evaluateJavascript(js, snap -> {
            Log.i(TAG,"HOME_QA="+snap);
            if (testAll) v.postDelayed(() -> runQaAll(v, testFrom), 250);
            else if (testGame != null && !testGame.isEmpty()) v.postDelayed(() -> runQaProbe(v), 750);
        });
    }

    private String gameSnapshotJs() {
        return "JSON.stringify((()=>{"+
                "const m=document.querySelector('#gameMount');"+
                "const s=document.querySelector('.opponent-selector');"+
                "const a=s&&s.querySelector('[data-mode].active');"+
                "const visible=e=>{if(!e)return false;const r=e.getBoundingClientRect(),cs=getComputedStyle(e);return cs.display!=='none'&&cs.visibility!=='hidden'&&r.width>0&&r.height>0};"+
                "return {"+
                "game:(document.body.dataset.game||''),"+
                "title:(document.querySelector('#gameTitle')?.textContent||'').trim(),"+
                "objective:(document.querySelector('.game-objective b')?.textContent||'').trim(),"+
                "mountChars:m?m.innerHTML.length:0,"+
                "mountChildren:m?m.querySelectorAll('*').length:0,"+
                "bodyOverflow:document.documentElement.scrollWidth>window.innerWidth+2,"+
                "mountOverflow:m?m.scrollWidth>m.clientWidth+2:false,"+
                "aiSelector:!!s,"+
                "aiMode:a?.dataset.mode||'',"+
                "parkingExit:visible(document.querySelector('.parking-exit')),"+
                "pipeTerminals:[...document.querySelectorAll('.pipe-terminal')].filter(visible).length,"+
                "viewport:[innerWidth,innerHeight]"+
                "};})())";
    }

    private String resultSnapshotJs() {
        return "JSON.stringify((()=>{"+
                "const m=document.querySelector('#resultModal');"+
                "const msg=document.querySelector('#resultMessage');"+
                "const buttons=['#resultReplayBtn','#resultNextBtn','#resultHomeBtn'].map(x=>(document.querySelector(x)?.textContent||'').trim());"+
                "return {open:!!m?.classList.contains('open'),title:(document.querySelector('#resultTitle')?.textContent||'').trim(),message:(msg?.innerText||'').trim(),goal:(msg?.querySelector('small')?.textContent||'').trim(),buttons,bodyOverflow:document.documentElement.scrollWidth>window.innerWidth+2};"+
                "})())";
    }

    private void runQaProbe(WebView v) {
        String id = JSONObject.quote(testGame);
        String openJs = "Boolean(window.__openGameForTest&&window.__openGameForTest("+id+"))";
        v.evaluateJavascript(openJs, opened -> {
            Log.i(TAG,"QA_OPEN game="+testGame+" opened="+opened);
            v.postDelayed(() -> v.evaluateJavascript(gameSnapshotJs(), snap -> {
                Log.i(TAG,"QA_SNAPSHOT="+snap);
                if (testFinish) finishQaProbe(v);
            }), 400);
        });
    }

    private void finishQaProbe(WebView v) {
        v.postDelayed(() -> v.evaluateJavascript("Boolean(window.__finishForTest&&window.__finishForTest())", finished -> {
            Log.i(TAG,"QA_FINISH game="+testGame+" finished="+finished);
            v.postDelayed(() -> v.evaluateJavascript(resultSnapshotJs(), snap -> Log.i(TAG,"RESULT_SNAPSHOT="+snap)), 300);
        }), 200);
    }

    private void runQaAll(WebView v, int index) {
        if (v == null) return;
        if (index >= QA_GAMES.length) {
            Log.i(TAG, "QA_ALL_DONE count="+(QA_GAMES.length-testFrom)+" start="+testFrom);
            return;
        }
        final String game = QA_GAMES[index];
        final String id = JSONObject.quote(game);
        v.evaluateJavascript("Boolean(window.__openGameForTest&&window.__openGameForTest("+id+"))", opened -> {
            Log.i(TAG,"QA_ALL_OPEN "+game+"="+opened);
            v.postDelayed(() -> v.evaluateJavascript(gameSnapshotJs(), snap -> {
                Log.i(TAG,"QA_ALL_GAME "+game+"="+snap);
                v.evaluateJavascript("Boolean(window.__finishForTest&&window.__finishForTest())", finished -> {
                    Log.i(TAG,"QA_ALL_FINISH "+game+"="+finished);
                    v.postDelayed(() -> v.evaluateJavascript(resultSnapshotJs(), result -> {
                        Log.i(TAG,"QA_ALL_RESULT "+game+"="+result);
                        v.postDelayed(() -> runQaAll(v, index+1), 60);
                    }), 170);
                });
            }), 260);
        });
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