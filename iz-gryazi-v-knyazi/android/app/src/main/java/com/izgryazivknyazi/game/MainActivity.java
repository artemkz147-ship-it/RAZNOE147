package com.izgryazivknyazi.game;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int IMPORT_REQUEST = 7;
    private static final int EXPORT_REQUEST = 8;
    private static final String HOST = "appassets.androidplatform.net";
    private WebView webView;
    private ValueCallback<Uri[]> importCallback;
    private String exportContent;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(0);
        webView = new WebView(this);
        webView.setBackgroundColor(0xff111416);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost())) return null;
                String path = uri.getPath();
                if (path == null || !path.startsWith("/assets/game/") || path.contains("..")) return null;
                String name = path.substring("/assets/".length());
                String mime = name.endsWith(".html") ? "text/html" : name.endsWith(".css") ? "text/css" :
                    name.endsWith(".js") ? "text/javascript" : name.endsWith(".png") ? "image/png" :
                    name.endsWith(".webp") ? "image/webp" : "application/octet-stream";
                try {
                    InputStream stream = getAssets().open(name);
                    return new WebResourceResponse(mime, mime.startsWith("text/") ? "UTF-8" : null, stream);
                } catch (Exception ignored) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null,
                        new java.io.ByteArrayInputStream(new byte[0]));
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (importCallback != null) importCallback.onReceiveValue(null);
                importCallback = callback;
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("application/json");
                startActivityForResult(pick, IMPORT_REQUEST);
                return true;
            }
        });
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void saveJson(String json, String filename) {
                runOnUiThread(() -> {
                    exportContent = json;
                    Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    save.addCategory(Intent.CATEGORY_OPENABLE);
                    save.setType("application/json");
                    save.putExtra(Intent.EXTRA_TITLE, filename.replaceAll("[^a-zA-Z0-9._-]", "_"));
                    startActivityForResult(save, EXPORT_REQUEST);
                });
            }
        }, "AndroidGame");
        setContentView(webView);
        webView.loadUrl("https://" + HOST + "/assets/game/index.html");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMPORT_REQUEST && importCallback != null) {
            Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
            importCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
            importCallback = null;
        }
        if (requestCode == EXPORT_REQUEST && resultCode == RESULT_OK && data != null && exportContent != null) {
            try (OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
                if (stream != null) stream.write(exportContent.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        }
        if (requestCode == EXPORT_REQUEST) exportContent = null;
    }

    @Override public void onBackPressed() {
        webView.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))", null);
    }
}
