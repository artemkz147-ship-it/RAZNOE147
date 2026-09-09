package app.inkflow.reader;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;

public final class Ui {
    public static int dp(Context c,float v){return (int)(v*c.getResources().getDisplayMetrics().density+0.5f);}    
    public static GradientDrawable rounded(int color,float r, Context c){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,r));return g;}
    public static GradientDrawable stroke(int color,int stroke,int strokeColor,float r,Context c){GradientDrawable g=rounded(color,r,c);g.setStroke(dp(c,stroke),strokeColor);return g;}
    public static GradientDrawable gradient(int[] colors,float r,Context c){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);g.setCornerRadius(dp(c,r));return g;}
    public static int alpha(int color,int a){return (color&0x00FFFFFF)|((a&255)<<24);}
    public static GradientDrawable panel(Context c){GradientDrawable g=gradient(new int[]{SURFACE2,SURFACE},20,c);g.setStroke(dp(c,1),BORDER);return g;}
    public static GradientDrawable hero(Context c){GradientDrawable g=gradient(new int[]{SURFACE3,SURFACE2,BG2},24,c);g.setStroke(dp(c,1),alpha(GOLD,80));return g;}
    public static GradientDrawable topBar(Context c){GradientDrawable g=gradient(new int[]{alpha(SURFACE2,246),alpha(BG2,242)},0,c);g.setStroke(dp(c,1),alpha(GOLD,46));return g;}
    public static GradientDrawable bottomBar(Context c){GradientDrawable g=gradient(new int[]{alpha(SURFACE2,246),alpha(BG2,246)},0,c);g.setStroke(dp(c,1),alpha(BORDER,120));return g;}
    public static GradientDrawable accentButton(Context c){GradientDrawable g=gradient(new int[]{ACCENT,HOT},18,c);g.setStroke(dp(c,1),alpha(GOLD,85));return g;}
    public static GradientDrawable goldButton(Context c){GradientDrawable g=gradient(new int[]{GOLD,GOLD2},18,c);g.setStroke(dp(c,1),0x66FFF2C2);return g;}
    public static GradientDrawable softButton(Context c){GradientDrawable g=gradient(new int[]{SURFACE3,SURFACE2},16,c);g.setStroke(dp(c,1),BORDER);return g;}
    public static TextView text(Context c,String s,float sp,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    public static void pad(View v, Context c,int l,int t,int r,int b){v.setPadding(dp(c,l),dp(c,t),dp(c,r),dp(c,b));}
    public static void safeInsets(View v, Context c,int l,int t,int r,int b){
        final int baseL=dp(c,l),baseT=dp(c,t),baseR=dp(c,r),baseB=dp(c,b);
        v.setOnApplyWindowInsetsListener((view,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=30){
                Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                left=safe.left;top=safe.top;right=safe.right;bottom=safe.bottom;
            }else{
                left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();
                if(Build.VERSION.SDK_INT>=28){
                    DisplayCutout cutout=insets.getDisplayCutout();
                    if(cutout!=null){left=Math.max(left,cutout.getSafeInsetLeft());top=Math.max(top,cutout.getSafeInsetTop());right=Math.max(right,cutout.getSafeInsetRight());bottom=Math.max(bottom,cutout.getSafeInsetBottom());}
                }
            }
            view.setPadding(baseL+left,baseT+top,baseR+right,baseB+bottom);
            return insets;
        });
        v.requestApplyInsets();
    }

    public static int BG=Color.rgb(7,10,16), BG2=Color.rgb(12,16,24), SURFACE=Color.rgb(17,22,34), SURFACE2=Color.rgb(24,31,46), SURFACE3=Color.rgb(31,38,56), TEXT=Color.rgb(247,248,251), MUTED=Color.rgb(152,161,183), BORDER=0xFF31384C;
    public static int ACCENT=Color.rgb(129,95,255), HOT=Color.rgb(255,97,155), MINT=Color.rgb(67,235,186), GOLD=Color.rgb(255,210,106), GOLD2=Color.rgb(255,154,72);
}
