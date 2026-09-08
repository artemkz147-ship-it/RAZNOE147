package app.inkflow.reader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.yandex.mobile.ads.banner.BannerAdEventListener;
import com.yandex.mobile.ads.banner.BannerAdSize;
import com.yandex.mobile.ads.banner.BannerAdView;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.common.YandexAds;

public final class BannerAdSlot extends FrameLayout {
    public static final String AD_UNIT_ID = "R-M-20002801-1";
    public static final long REFRESH_MS = 35_000L;
    private static final int SLOT_DP = 64;

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ImageView placeholder;
    private BannerAdView banner;
    private boolean active = false;
    private boolean initRequested = false;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!active || !isShown()) return;
            loadBanner();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    public BannerAdSlot(Activity activity) {
        super(activity);
        this.activity = activity;
        setBackgroundColor(Color.rgb(7, 10, 16));
        setClipChildren(true);
        setClipToPadding(true);

        placeholder = new ImageView(activity);
        placeholder.setImageResource(R.drawable.ad_loading);
        placeholder.setScaleType(ImageView.ScaleType.CENTER_CROP);
        placeholder.setContentDescription("Спасибо, что вы с нами. Приятного чтения");
        addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
    }

    public static View wrap(Activity activity, View content) {
        if (content instanceof LinearLayout && content.getTag() != null && "ad_wrapped".equals(content.getTag())) return content;
        ViewGroup parent = content.getParent() instanceof ViewGroup ? (ViewGroup) content.getParent() : null;
        if (parent != null) parent.removeView(content);

        LinearLayout shell = new LinearLayout(activity);
        shell.setTag("ad_wrapped");
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(7, 10, 16));
        shell.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        BannerAdSlot slot = new BannerAdSlot(activity);
        shell.addView(slot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, SLOT_DP)));
        return shell;
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) start(); else stopRefreshOnly();
    }

    private void start() {
        if (active) return;
        active = true;
        placeholder.setVisibility(VISIBLE);
        if (!initRequested) {
            initRequested = true;
            YandexAds.initialize(activity.getApplicationContext(), () ->
                    activity.runOnUiThread(() -> {
                        if (!active) return;
                        loadBanner();
                        handler.removeCallbacks(refresh);
                        handler.postDelayed(refresh, REFRESH_MS);
                    }));
        } else {
            loadBanner();
            handler.removeCallbacks(refresh);
            handler.postDelayed(refresh, REFRESH_MS);
        }
    }

    private void stopRefreshOnly() {
        active = false;
        handler.removeCallbacks(refresh);
    }

    private void stop() {
        active = false;
        handler.removeCallbacks(refresh);
        destroyBanner();
    }

    private void destroyBanner() {
        if (banner != null) {
            banner.setBannerAdEventListener(null);
            removeView(banner);
            banner.destroy();
            banner = null;
        }
    }

    private void loadBanner() {
        if (!active || activity.isFinishing() || activity.isDestroyed()) return;
        destroyBanner();
        placeholder.setVisibility(VISIBLE);

        final DisplayMetrics dm = getResources().getDisplayMetrics();
        int widthPx = getWidth() > 0 ? getWidth() : dm.widthPixels;
        int widthDp = Math.max(1, Math.round(widthPx / dm.density));
        BannerAdSize size = BannerAdSize.inline(activity, widthDp, SLOT_DP);

        BannerAdView view = new BannerAdView(activity);
        banner = view;
        view.setVisibility(INVISIBLE);
        view.setAdSize(size);
        view.setBannerAdEventListener(new BannerAdEventListener() {
            @Override public void onAdLoaded() {
                if (activity.isDestroyed() || banner != view) {
                    view.destroy();
                    return;
                }
                placeholder.setVisibility(GONE);
                view.setVisibility(VISIBLE);
            }

            @Override public void onAdFailedToLoad(AdRequestError error) {
                if (banner == view) {
                    view.setVisibility(INVISIBLE);
                    placeholder.setVisibility(VISIBLE);
                }
            }

            @Override public void onAdClicked() { }
            @Override public void onImpression(ImpressionData data) { }
        });
        addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        view.loadAd(new AdRequest.Builder(AD_UNIT_ID).build());
    }
}
