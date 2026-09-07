package app.inkflow.reader;

import android.content.Context;
import android.content.SharedPreferences;

public final class BookProgressStore {
    private final SharedPreferences p;
    public BookProgressStore(Context c){ p=c.getSharedPreferences("inkflow_books",Context.MODE_PRIVATE); }
    private String key(String prefix,String uri){ return prefix+uri.hashCode(); }

    public int getRatio(String uri){ return p.getInt(key("ratio_",uri),0); }
    public void setRatio(String uri,int ratio){ p.edit().putInt(key("ratio_",uri),clamp(ratio,0,1000000)).commit(); }

    public int getFontSp(){ return p.getInt("font_sp",20); }
    public void setFontSp(int v){ p.edit().putInt("font_sp",clamp(v,14,36)).apply(); }
    public int getTheme(){ return p.getInt("theme",0); }
    public void setTheme(int v){ p.edit().putInt("theme",clamp(v,0,3)).apply(); }
    public int getMode(){ return p.getInt("reader_mode",0); }
    public void setMode(int v){ p.edit().putInt("reader_mode",clamp(v,0,1)).apply(); }
    public int getAnimation(){ return p.getInt("page_animation",0); }
    public void setAnimation(int v){ p.edit().putInt("page_animation",clamp(v,0,3)).apply(); }
    public int getTurnMs(){ return p.getInt("turn_ms",340); }
    public void setTurnMs(int v){ p.edit().putInt("turn_ms",clamp(v,160,700)).apply(); }
    public int getLinePct(){ return p.getInt("line_pct",164); }
    public void setLinePct(int v){ p.edit().putInt("line_pct",clamp(v,120,210)).apply(); }
    public int getMargins(){ return p.getInt("margins",20); }
    public void setMargins(int v){ p.edit().putInt("margins",clamp(v,6,42)).apply(); }
    public int getIndent(){ return p.getInt("indent",26); }
    public void setIndent(int v){ p.edit().putInt("indent",clamp(v,0,42)).apply(); }
    public int getFontFamily(){ return p.getInt("font_family",0); }
    public void setFontFamily(int v){ p.edit().putInt("font_family",clamp(v,0,2)).apply(); }
    public boolean getJustify(){ return p.getBoolean("justify",true); }
    public void setJustify(boolean v){ p.edit().putBoolean("justify",v).apply(); }
    public boolean getTapZones(){ return p.getBoolean("tap_zones",true); }
    public void setTapZones(boolean v){ p.edit().putBoolean("tap_zones",v).apply(); }
    public boolean getVolumeKeys(){ return p.getBoolean("volume_keys",true); }
    public void setVolumeKeys(boolean v){ p.edit().putBoolean("volume_keys",v).apply(); }
    public boolean getTwoPage(){ return p.getBoolean("two_page_landscape",true); }
    public void setTwoPage(boolean v){ p.edit().putBoolean("two_page_landscape",v).apply(); }
    public boolean getKeepScreen(){ return p.getBoolean("keep_screen",true); }
    public void setKeepScreen(boolean v){ p.edit().putBoolean("keep_screen",v).apply(); }
    public int getBrightness(){ return p.getInt("brightness",0); }
    public void setBrightness(int v){ p.edit().putInt("brightness",clamp(v,0,100)).apply(); }
    public int getOrientation(){ return p.getInt("orientation",0); }
    public void setOrientation(int v){ p.edit().putInt("orientation",clamp(v,0,2)).apply(); }

    public void resetReaderSettings(){
        p.edit()
          .putInt("font_sp",20).putInt("theme",0).putInt("reader_mode",0).putInt("page_animation",0)
          .putInt("turn_ms",340).putInt("line_pct",164).putInt("margins",20).putInt("indent",26)
          .putInt("font_family",0).putBoolean("justify",true).putBoolean("tap_zones",true)
          .putBoolean("volume_keys",true).putBoolean("two_page_landscape",true).putBoolean("keep_screen",true)
          .putInt("brightness",0).putInt("orientation",0).commit();
    }
    private static int clamp(int v,int lo,int hi){ return Math.max(lo,Math.min(hi,v)); }
}
