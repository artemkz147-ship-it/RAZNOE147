package ru.filemaster.offline;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.yandex.mobile.ads.common.YandexAds;

import java.util.ArrayList;
import java.util.List;

public final class DokiApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Runnable> readyCallbacks = new ArrayList<>();
    private boolean adsReady;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        YandexAds.initialize(this, () -> mainHandler.post(() -> {
            synchronized (readyCallbacks) {
                adsReady = true;
                AdsManager.get(this).onSdkReady();
                for (Runnable callback : new ArrayList<>(readyCallbacks)) callback.run();
                readyCallbacks.clear();
            }
        }));
    }

    void whenAdsReady(Runnable callback) {
        if (callback == null) return;
        mainHandler.post(() -> {
            synchronized (readyCallbacks) {
                if (adsReady) callback.run();
                else readyCallbacks.add(callback);
            }
        });
    }

    boolean isAdsReady() {
        return adsReady;
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}

    @Override
    public void onActivityResumed(Activity activity) {
        AdsManager.get(this).onActivityResumed(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        AdsManager.get(this).onActivityPaused(activity);
    }

    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    @Override
    public void onActivityDestroyed(Activity activity) {
        AdsManager.get(this).onActivityDestroyed(activity);
    }
}
