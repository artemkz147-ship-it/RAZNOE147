package ru.offline100.games;

import android.app.Activity;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;

public final class AndroidBridge {
    private final Activity activity;
    public AndroidBridge(Activity activity) { this.activity = activity; }

    @JavascriptInterface public void vibrate(int milliseconds) {
        final int duration = Math.max(10, Math.min(milliseconds, 300));
        activity.runOnUiThread(() -> {
            try {
                Vibrator vibrator = (Vibrator) activity.getSystemService(Activity.VIBRATOR_SERVICE);
                if (vibrator == null || !vibrator.hasVibrator()) return;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                else vibrator.vibrate(duration);
            } catch (Throwable ignored) { }
        });
    }

    @JavascriptInterface public void setKeepScreenOn(boolean enabled) {
        activity.runOnUiThread(() -> {
            if (enabled) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }
}
