package app.inkflow.reader;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.LinearGradient;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.yandex.mobile.ads.banner.BannerAdEventListener;
import com.yandex.mobile.ads.banner.BannerAdSize;
import com.yandex.mobile.ads.banner.BannerAdView;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.common.YandexAds;

public final class BannerAdSlot extends FrameLayout {
    public static final String AD_UNIT_ID="R-M-20002801-1";
    public static final long REFRESH_MS=35_000L;
    private static final int RESERVED_SLOT_DP=88;
    private static final int FALLBACK_AD_DP=50;

    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AdBackdrop backdrop;
    private BannerAdView banner;
    private boolean active=false,initRequested=false;
    private int adHeightDp=FALLBACK_AD_DP,navigationInsetPx=0;

    private final Runnable refresh=new Runnable(){@Override public void run(){if(!active||!isShown())return;loadBanner();handler.postDelayed(this,REFRESH_MS);}};

    public BannerAdSlot(Activity activity){
        super(activity);this.activity=activity;setBackgroundColor(Ui.BG2);setClipChildren(true);setClipToPadding(true);
        backdrop=new AdBackdrop(activity);addView(backdrop,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        setOnApplyWindowInsetsListener((v,insets)->{int bottom=Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();if(bottom!=navigationInsetPx){navigationInsetPx=Math.max(0,bottom);updateReservedHeight();}return insets;});
    }

    public static View wrap(Activity activity,View content){
        if(content instanceof LinearLayout&&"ad_wrapped".equals(content.getTag()))return content;
        ViewGroup parent=content.getParent() instanceof ViewGroup?(ViewGroup)content.getParent():null;if(parent!=null)parent.removeView(content);
        LinearLayout shell=new LinearLayout(activity);shell.setTag("ad_wrapped");shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(Ui.BG);
        shell.addView(content,new LinearLayout.LayoutParams(-1,0,1f));BannerAdSlot slot=new BannerAdSlot(activity);shell.addView(slot,new LinearLayout.LayoutParams(-1,dp(activity,RESERVED_SLOT_DP)));return shell;
    }
    private static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}

    private void updateReservedHeight(){
        int contentDp=Math.max(RESERVED_SLOT_DP,adHeightDp);setPadding(0,0,0,navigationInsetPx);
        ViewGroup.LayoutParams lp=getLayoutParams();if(lp!=null){int wanted=dp(activity,contentDp)+navigationInsetPx;if(lp.height!=wanted){lp.height=wanted;setLayoutParams(lp);}}
        if(banner!=null){FrameLayout.LayoutParams blp=(FrameLayout.LayoutParams)banner.getLayoutParams();if(blp!=null){blp.height=dp(activity,Math.max(1,adHeightDp));blp.gravity=Gravity.BOTTOM;banner.setLayoutParams(blp);}}
        requestLayout();
    }
    private void syncLoadedBannerHeight(BannerAdView view){try{int h=view.getAdSize().getHeight();if(h>0){adHeightDp=h;updateReservedHeight();}}catch(Throwable ignored){}}

    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();requestApplyInsets();updateReservedHeight();start();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)start();else stopRefreshOnly();}
    private void start(){if(active)return;active=true;if(!initRequested){initRequested=true;YandexAds.initialize(activity.getApplicationContext(),()->activity.runOnUiThread(()->{if(!active)return;loadBanner();handler.removeCallbacks(refresh);handler.postDelayed(refresh,REFRESH_MS);}));}else{loadBanner();handler.removeCallbacks(refresh);handler.postDelayed(refresh,REFRESH_MS);}}
    private void stopRefreshOnly(){active=false;handler.removeCallbacks(refresh);}
    private void stop(){active=false;handler.removeCallbacks(refresh);destroyBanner();}
    private void destroyBanner(){if(banner!=null){banner.setBannerAdEventListener(null);removeView(banner);banner.destroy();banner=null;}}

    private void loadBanner(){
        if(!active||activity.isFinishing()||activity.isDestroyed())return;destroyBanner();
        final DisplayMetrics dm=getResources().getDisplayMetrics();int widthPx=getWidth()>0?getWidth():dm.widthPixels;int widthDp=Math.max(1,Math.round(widthPx/dm.density));
        BannerAdSize size=BannerAdSize.inline(activity,widthDp,RESERVED_SLOT_DP);
        BannerAdView view=new BannerAdView(activity);banner=view;view.setVisibility(INVISIBLE);view.setAdSize(size);
        view.setBannerAdEventListener(new BannerAdEventListener(){
            @Override public void onAdLoaded(){if(activity.isDestroyed()||banner!=view){view.destroy();return;}syncLoadedBannerHeight(view);view.setVisibility(VISIBLE);backdrop.invalidate();}
            @Override public void onAdFailedToLoad(AdRequestError error){if(banner==view)view.setVisibility(INVISIBLE);}
            @Override public void onAdClicked(){}
            @Override public void onImpression(ImpressionData data){}
        });
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(activity,FALLBACK_AD_DP),Gravity.BOTTOM);addView(view,lp);view.loadAd(new AdRequest.Builder(AD_UNIT_ID).build());
    }

    private static final class AdBackdrop extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);
        AdBackdrop(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
            paint.setShader(new LinearGradient(0,0,w,h,new int[]{Ui.BG2,Ui.SURFACE2,Ui.alpha(Ui.ACCENT,120)},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,paint);paint.setShader(null);
            paint.setColor(Ui.alpha(Ui.GOLD,32));c.drawCircle(w*.84f,h*.05f,w*.26f,paint);paint.setColor(Ui.alpha(Ui.ACCENT,28));c.drawCircle(w*.1f,h*.9f,w*.35f,paint);
            text.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));text.setColor(Ui.GOLD);text.setTextSize(Math.max(11f,getResources().getDisplayMetrics().scaledDensity*11f));text.setLetterSpacing(.08f);c.drawText("ЧИТАЙ ВСЁ  •  СПАСИБО, ЧТО ВЫ С НАМИ",dp(getContext(),14),dp(getContext(),19),text);
            text.setTypeface(Typeface.DEFAULT);text.setLetterSpacing(0);text.setColor(Ui.MUTED);text.setTextSize(Math.max(9f,getResources().getDisplayMetrics().scaledDensity*9.5f));c.drawText("Пусть каждая история находит тебя",dp(getContext(),14),dp(getContext(),36),text);
            paint.setColor(Ui.alpha(Ui.GOLD,95));c.drawRoundRect(dp(getContext(),14),dp(getContext(),44),Math.min(w-dp(getContext(),14),dp(getContext(),94)),dp(getContext(),46),dp(getContext(),1),dp(getContext(),1),paint);
        }
    }
}
