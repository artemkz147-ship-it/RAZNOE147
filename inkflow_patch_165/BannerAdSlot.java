package app.inkflow.reader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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
    public static final String AD_UNIT_ID="R-M-20002801-1";
    public static final long REFRESH_MS=35_000L;
    private static final int RESERVED_SLOT_DP=88;
    private static final int FALLBACK_BANNER_DP=50;

    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ImageView placeholder;
    private BannerAdView banner;
    private boolean active=false,initRequested=false;
    private int actualBannerHeightDp=FALLBACK_BANNER_DP;

    private final Runnable refresh=new Runnable(){
        @Override public void run(){
            if(!active||!isShown())return;
            safeLoadBanner();
            handler.postDelayed(this,REFRESH_MS);
        }
    };

    public BannerAdSlot(Activity a){
        super(a);
        activity=a;
        setClipChildren(true);
        setClipToPadding(true);
        setBackgroundColor(Ui.BG2);

        // One single placeholder image always remains behind Yandex Ads.
        // Its important text is deliberately placed in the TOP 40% so that
        // it still looks complete when a 50dp Yandex banner covers the bottom.
        placeholder=new ImageView(a);
        placeholder.setImageBitmap(buildCropSafePlaceholder(a));
        placeholder.setScaleType(ImageView.ScaleType.FIT_XY);
        placeholder.setAdjustViewBounds(false);
        placeholder.setContentDescription("Спасибо, что вы с нами. Приятного чтения");
        addView(placeholder,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
    }

    private Bitmap buildCropSafePlaceholder(Context c){
        final int w=1600,h=360;
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        canvas.drawColor(Color.rgb(6,8,15));

        Bitmap src=null;
        try{src=BitmapFactory.decodeResource(c.getResources(),R.drawable.ad_loading);}catch(Throwable ignored){}
        if(src!=null && src.getWidth()>20 && src.getHeight()>20){
            int sw=src.getWidth(),sh=src.getHeight();
            // Cat scene on the left. Central text from the source asset is not used.
            Rect catSrc=new Rect(Math.max(0,sw*8/100),Math.max(0,sh*43/100),Math.min(sw,sw*43/100),sh);
            RectF catDst=new RectF(0,0,w*0.46f,h);
            canvas.drawBitmap(src,catSrc,catDst,p);
            // Glowing book/comic scene on the right.
            Rect bookSrc=new Rect(Math.max(0,sw*59/100),Math.max(0,sh*28/100),sw,sh);
            RectF bookDst=new RectF(w*0.54f,0,w,h);
            canvas.drawBitmap(src,bookSrc,bookDst,p);
        }

        // Dark center/top keeps the message readable in both full and partly-covered states.
        Paint shade=new Paint(Paint.ANTI_ALIAS_FLAG);
        shade.setColor(0xA6060912);
        canvas.drawRect(0,0,w,h,shade);
        shade.setColor(0xD9060912);
        canvas.drawRect(w*0.23f,0,w*0.77f,h*0.46f,shade);

        Paint title=new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(0xFFFFD36A);
        title.setTextAlign(Paint.Align.CENTER);
        title.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        title.setTextSize(48f);
        title.setShadowLayer(4f,0,2f,0xCC000000);
        canvas.drawText("СПАСИБО, ЧТО ВЫ С НАМИ",w/2f,62f,title);

        Paint sub=new Paint(Paint.ANTI_ALIAS_FLAG);
        sub.setColor(0xFFF5F7FB);
        sub.setTextAlign(Paint.Align.CENTER);
        sub.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.NORMAL));
        sub.setTextSize(28f);
        sub.setShadowLayer(3f,0,2f,0xCC000000);
        canvas.drawText("Приятного чтения",w/2f,108f,sub);

        Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0xFFFFC24B);
        line.setStrokeWidth(5f);
        line.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(w/2f-105f,128f,w/2f+105f,128f,line);

        return out;
    }

    public static View wrap(Activity a,View content){
        if(content instanceof LinearLayout&&"ad_wrapped".equals(content.getTag()))return content;
        content.setTag("ad_content");
        ViewGroup parent=content.getParent() instanceof ViewGroup?(ViewGroup)content.getParent():null;
        if(parent!=null)parent.removeView(content);

        LinearLayout shell=new LinearLayout(a);
        shell.setTag("ad_wrapped");
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Ui.BG);
        shell.addView(content,new LinearLayout.LayoutParams(-1,0,1f));

        // Visual ad area remains EXACTLY 88dp unless Yandex itself returns a taller ad.
        BannerAdSlot slot=new BannerAdSlot(a);
        shell.addView(slot,new LinearLayout.LayoutParams(-1,dp(a,RESERVED_SLOT_DP)));

        // Android navigation-bar inset is a separate spacer BELOW the ad slot.
        // Therefore the full 88dp slot is always visible above virtual buttons.
        View navGuard=new View(a);
        navGuard.setTag("nav_guard");
        navGuard.setBackgroundColor(Ui.BG);
        shell.addView(navGuard,new LinearLayout.LayoutParams(-1,0));
        shell.setOnApplyWindowInsetsListener((v,insets)->{
            try{
                int bottom=Build.VERSION.SDK_INT>=30
                        ?insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                        :insets.getSystemWindowInsetBottom();
                ViewGroup.LayoutParams lp=navGuard.getLayoutParams();
                int h=Math.max(0,bottom);
                if(lp.height!=h){lp.height=h;navGuard.setLayoutParams(lp);}
            }catch(Throwable ignored){}
            return insets;
        });
        shell.requestApplyInsets();
        return shell;
    }

    private static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    private int visualSlotHeightDp(){return Math.max(RESERVED_SLOT_DP,actualBannerHeightDp);}

    private void updateGeometry(){
        ViewGroup.LayoutParams lp=getLayoutParams();
        if(lp!=null){
            int wanted=dp(activity,visualSlotHeightDp());
            if(lp.height!=wanted){lp.height=wanted;setLayoutParams(lp);}
        }
        if(banner!=null){
            try{
                FrameLayout.LayoutParams blp=(FrameLayout.LayoutParams)banner.getLayoutParams();
                blp.width=-1;
                blp.height=dp(activity,Math.max(1,actualBannerHeightDp));
                blp.gravity=Gravity.BOTTOM;
                banner.setLayoutParams(blp);
                banner.bringToFront();
            }catch(Throwable ignored){}
        }
        requestLayout();
    }

    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();try{updateGeometry();start();}catch(Throwable ignored){}}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)start();else stopRefreshOnly();}

    private void start(){
        if(active)return;
        active=true;
        if(!initRequested){
            initRequested=true;
            handler.postDelayed(()->{
                try{
                    YandexAds.initialize(activity.getApplicationContext(),()->activity.runOnUiThread(()->{
                        if(!active)return;
                        safeLoadBanner();
                        handler.removeCallbacks(refresh);
                        handler.postDelayed(refresh,REFRESH_MS);
                    }));
                }catch(Throwable ignored){}
            },700);
        }else{
            safeLoadBanner();
            handler.removeCallbacks(refresh);
            handler.postDelayed(refresh,REFRESH_MS);
        }
    }

    private void stopRefreshOnly(){active=false;handler.removeCallbacks(refresh);}
    private void stop(){active=false;handler.removeCallbacksAndMessages(null);destroyBanner();}
    private void destroyBanner(){
        try{
            if(banner!=null){
                banner.setBannerAdEventListener(null);
                removeView(banner);
                banner.destroy();
                banner=null;
            }
        }catch(Throwable ignored){banner=null;}
    }
    private void safeLoadBanner(){try{loadBanner();}catch(Throwable ignored){destroyBanner();}}

    private void loadBanner(){
        if(!active||activity.isFinishing()||activity.isDestroyed())return;
        destroyBanner();
        actualBannerHeightDp=FALLBACK_BANNER_DP;
        updateGeometry();

        DisplayMetrics dm=getResources().getDisplayMetrics();
        int widthPx=getWidth()>0?getWidth():dm.widthPixels;
        int widthDp=Math.max(1,Math.round(widthPx/dm.density));
        BannerAdSize size=BannerAdSize.inline(activity,widthDp,RESERVED_SLOT_DP);

        BannerAdView view=new BannerAdView(activity);
        banner=view;
        view.setVisibility(INVISIBLE);
        view.setAdSize(size);
        view.setBannerAdEventListener(new BannerAdEventListener(){
            @Override public void onAdLoaded(){
                if(banner!=view||activity.isDestroyed()){
                    try{view.destroy();}catch(Throwable ignored){}
                    return;
                }
                int actual=FALLBACK_BANNER_DP;
                try{int h=view.getAdSize().getHeight();if(h>0)actual=h;}catch(Throwable ignored){}
                actualBannerHeightDp=Math.max(1,actual);
                updateGeometry();
                view.setVisibility(VISIBLE);
                view.bringToFront();
            }
            @Override public void onAdFailedToLoad(AdRequestError e){
                if(banner==view)view.setVisibility(INVISIBLE);
                actualBannerHeightDp=FALLBACK_BANNER_DP;
                updateGeometry();
            }
            @Override public void onAdClicked(){}
            @Override public void onImpression(ImpressionData data){}
        });
        addView(view,new FrameLayout.LayoutParams(-1,dp(activity,FALLBACK_BANNER_DP),Gravity.BOTTOM));
        view.bringToFront();
        view.loadAd(new AdRequest.Builder(AD_UNIT_ID).build());
    }
}
