package ru.offline100.games;

import android.app.Activity;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

public final class AndroidBridge {
    private final Activity activity;
    private final WebView webView;

    AndroidBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public void vibrate(int milliseconds) {
        int duration = Math.max(10, Math.min(milliseconds, 500));
        activity.runOnUiThread(() -> {
            Vibrator vibrator = (Vibrator) activity.getSystemService(Activity.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        });
    }

    @JavascriptInterface
    public void setKeepScreenOn(boolean enabled) {
        activity.runOnUiThread(() -> {
            if (enabled) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    @JavascriptInterface public boolean isPremium() { return false; }

    @JavascriptInterface
    public void purchasePremium() {
        notifyPremium("Premium будет подключён после настройки RuStore");
    }

    @JavascriptInterface
    public void restorePremium() {
        notifyPremium("Покупки RuStore пока не настроены");
    }

    @JavascriptInterface public void showInterstitial() { }

    private void notifyPremium(String message) {
        String escaped = message.replace("\\", "\\\\").replace("'", "\\'");
        webView.post(() -> webView.evaluateJavascript(
                "window.onPremiumStatus && window.onPremiumStatus(false, '" + escaped + "');", null));
    }
}
