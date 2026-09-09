package app.inkflow.reader;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

public final class CoverPlaceholderView extends LinearLayout {
    public CoverPlaceholderView(Context c,String name){
        super(c); setOrientation(VERTICAL); setGravity(Gravity.CENTER); setPadding(Ui.dp(c,16),Ui.dp(c,18),Ui.dp(c,16),Ui.dp(c,18));
        String ext=ext(name), badge=badge(ext);
        int a=accent(ext), b=Ui.SURFACE;
        setBackground(Ui.gradient(new int[]{Ui.alpha(a,210),Ui.SURFACE2,b},18,c));
        TextView icon=Ui.text(c,badge,badge.length()>3?24:34,Ui.TEXT); icon.setTypeface(Typeface.DEFAULT_BOLD); icon.setGravity(Gravity.CENTER); icon.setLetterSpacing(.05f); addView(icon,new LayoutParams(-1,0,1));
        TextView line=Ui.text(c,"ЧИТАЙ ВСЁ",10,Ui.alpha(Ui.TEXT,190)); line.setGravity(Gravity.CENTER); line.setTypeface(Typeface.DEFAULT_BOLD); line.setLetterSpacing(.14f); addView(line,new LayoutParams(-1,Ui.dp(c,24)));
        TextView title=Ui.text(c,clean(name),12,Ui.TEXT); title.setGravity(Gravity.CENTER); title.setTypeface(Typeface.DEFAULT_BOLD); title.setMaxLines(3); title.setEllipsize(android.text.TextUtils.TruncateAt.END); addView(title,new LayoutParams(-1,Ui.dp(c,58)));
    }
    private static String ext(String n){if(n==null)return "";int i=n.lastIndexOf('.');return i>=0?n.substring(i+1).toLowerCase(Locale.ROOT):"";}
    private static String clean(String n){return n==null||n.trim().isEmpty()?"Без обложки":n;}
    private static String badge(String e){
        if(e.equals("doc")||e.equals("docx")||e.equals("docm")||e.equals("odt")||e.equals("rtf"))return "DOC";
        if(e.equals("pdf"))return "PDF";
        if(e.equals("zip")||e.equals("rar")||e.equals("7z")||e.equals("tar")||e.equals("cbz")||e.equals("cbr")||e.equals("cb7")||e.equals("cbt"))return "ARCHIVE";
        if(e.equals("epub")||e.equals("fb2")||e.equals("txt")||e.equals("html")||e.equals("htm"))return "Aa";
        if(e.equals("jpg")||e.equals("jpeg")||e.equals("png")||e.equals("webp")||e.equals("gif")||e.equals("bmp"))return "IMAGE";
        return "BOOK";
    }
    private static int accent(String e){
        if(e.equals("doc")||e.equals("docx")||e.equals("docm")||e.equals("odt")||e.equals("rtf"))return 0xFF3767D6;
        if(e.equals("pdf"))return 0xFFC64B49;
        if(e.equals("zip")||e.equals("rar")||e.equals("7z")||e.equals("tar")||e.startsWith("cb"))return Ui.GOLD2;
        if(e.equals("epub")||e.equals("fb2")||e.equals("txt")||e.equals("html")||e.equals("htm"))return Ui.ACCENT;
        return Ui.MINT;
    }
}
