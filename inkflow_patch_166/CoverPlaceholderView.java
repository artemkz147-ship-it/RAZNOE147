package app.inkflow.reader;

import android.content.Context;
import android.widget.ImageView;
import java.util.Locale;

/**
 * Real illustrated fallback cover. No file name or format text is rendered
 * inside the image: the normal card title remains below the cover.
 */
public final class CoverPlaceholderView extends ImageView {
    public CoverPlaceholderView(Context c,String name){
        super(c);
        setScaleType(ScaleType.CENTER_CROP);
        setAdjustViewBounds(false);
        setImageResource(resourceFor(name));
        setContentDescription("Обложка-заглушка");
    }

    private static int resourceFor(String name){
        String e=ext(name);
        if(e.equals("doc")||e.equals("docx")||e.equals("docm")||e.equals("odt")||e.equals("rtf")||e.equals("pdf"))
            return R.drawable.cover_placeholder_document;
        if(e.equals("cbz")||e.equals("cbr")||e.equals("cb7")||e.equals("cbt")||e.equals("jpg")||e.equals("jpeg")||e.equals("png")||e.equals("webp")||e.equals("gif")||e.equals("bmp"))
            return R.drawable.cover_placeholder_comic;
        if(e.equals("zip")||e.equals("rar")||e.equals("7z")||e.equals("tar"))
            return R.drawable.cover_placeholder_archive;
        return R.drawable.cover_placeholder_book;
    }

    private static String ext(String n){
        if(n==null)return "";
        int i=n.lastIndexOf('.');
        return i>=0?n.substring(i+1).toLowerCase(Locale.ROOT):"";
    }
}
