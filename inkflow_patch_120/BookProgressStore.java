package app.inkflow.reader;

import android.content.Context;
import android.content.SharedPreferences;

public final class BookProgressStore {
    private final SharedPreferences p;
    public BookProgressStore(Context c){p=c.getSharedPreferences("inkflow_books",Context.MODE_PRIVATE);}    
    private String key(String prefix,String uri){return prefix+uri.hashCode();}
    public int getRatio(String uri){return p.getInt(key("ratio_",uri),0);}    
    public void setRatio(String uri,int ratio){p.edit().putInt(key("ratio_",uri),Math.max(0,Math.min(1000000,ratio))).commit();}
    public int getFontSp(){return p.getInt("font_sp",20);}    
    public void setFontSp(int sp){p.edit().putInt("font_sp",Math.max(14,Math.min(34,sp))).apply();}
    public int getTheme(){return p.getInt("theme",0);}    
    public void setTheme(int theme){p.edit().putInt("theme",theme).apply();}
}
