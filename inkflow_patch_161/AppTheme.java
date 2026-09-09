package app.inkflow.reader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;

public final class AppTheme {
    private static final String PREFS="inkflow_app";
    private static final String KEY="day_mode";
    private AppTheme(){}
    public static boolean isDay(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(KEY,false);}
    public static void setDay(Context c,boolean day){c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(KEY,day).commit();applyPalette(day);}
    public static void toggle(Activity a){setDay(a,!isDay(a));apply(a);}
    public static String name(Context c){return isDay(c)?"ДЕНЬ":"НОЧЬ";}
    public static void apply(Context c){boolean day=isDay(c);applyPalette(day);if(c instanceof Activity){try{Activity a=(Activity)c;a.getWindow().setStatusBarColor(Ui.BG);a.getWindow().setNavigationBarColor(Ui.BG);}catch(Throwable ignored){}}}
    private static void applyPalette(boolean day){
        if(day){Ui.BG=Color.rgb(245,242,235);Ui.BG2=Color.rgb(237,232,223);Ui.SURFACE=Color.rgb(255,253,249);Ui.SURFACE2=Color.rgb(248,244,237);Ui.SURFACE3=Color.rgb(238,232,224);Ui.TEXT=Color.rgb(29,30,34);Ui.MUTED=Color.rgb(105,105,112);Ui.BORDER=0xFFD8D0C5;Ui.ACCENT=Color.rgb(115,82,238);Ui.HOT=Color.rgb(231,72,139);Ui.MINT=Color.rgb(24,151,116);Ui.GOLD=Color.rgb(198,139,28);Ui.GOLD2=Color.rgb(239,151,44);}else{Ui.BG=Color.rgb(7,10,16);Ui.BG2=Color.rgb(12,16,24);Ui.SURFACE=Color.rgb(17,22,34);Ui.SURFACE2=Color.rgb(24,31,46);Ui.SURFACE3=Color.rgb(31,38,56);Ui.TEXT=Color.rgb(247,248,251);Ui.MUTED=Color.rgb(152,161,183);Ui.BORDER=0xFF31384C;Ui.ACCENT=Color.rgb(129,95,255);Ui.HOT=Color.rgb(255,97,155);Ui.MINT=Color.rgb(67,235,186);Ui.GOLD=Color.rgb(255,210,106);Ui.GOLD2=Color.rgb(255,154,72);}
    }
}
