package app.inkflow.reader;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.Locale;

/** Illustrated cover used only when the file itself has no usable cover. */
public final class CoverPlaceholderView extends FrameLayout {
    public CoverPlaceholderView(Context c, String name){
        super(c);
        setBackgroundColor(Ui.SURFACE2);
        ImageView art=new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setAdjustViewBounds(false);
        art.setImageResource(pickArtwork(name));
        art.setContentDescription("Обложка-заглушка");
        addView(art,new FrameLayout.LayoutParams(-1,-1, Gravity.CENTER));
    }

    private static int pickArtwork(String name){
        String e=ext(name);
        if(e.equals("doc")||e.equals("docx")||e.equals("docm")||e.equals("odt")||e.equals("rtf")||e.equals("pdf"))
            return R.drawable.cover_placeholder_document;
        if(e.equals("cbz")||e.equals("cbr")||e.equals("cb7")||e.equals("cbt"))
            return R.drawable.cover_placeholder_comic;
        if(e.equals("zip")||e.equals("rar")||e.equals("7z")||e.equals("tar"))
            return R.drawable.cover_placeholder_archive;
        if(e.equals("epub")||e.equals("fb2")||e.equals("txt")||e.equals("html")||e.equals("htm"))
            return R.drawable.cover_placeholder_book;
        return R.drawable.cover_placeholder_book;
    }

    private static String ext(String n){
        if(n==null)return "";
        int i=n.lastIndexOf('.');
        return i>=0?n.substring(i+1).toLowerCase(Locale.ROOT):"";
    }
}
