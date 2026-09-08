package app.inkflow.reader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import java.util.Locale;

/** Receives Android ACTION_VIEW intents and routes supported files to the correct reader. */
public class OpenFileActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        open(getIntent());
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        open(intent);
    }

    private void open(Intent source){
        Uri uri=source==null?null:source.getData();
        if(uri==null){ finish(); return; }
        int grants=source.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{
            if((source.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0)
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(Exception ignored){}

        String name;
        try{name=SourceFactory.fileName(this,uri);}catch(Exception e){name="Файл";}
        String mime=null;
        try{mime=getContentResolver().getType(uri);}catch(Exception ignored){}
        if(!supported(name)){
            String ext=extensionForMime(mime);
            if(ext!=null && !name.toLowerCase(Locale.ROOT).endsWith(ext)) name=name+ext;
        }
        if(!supported(name)){
            Toast.makeText(this,"Этот формат пока не поддерживается",Toast.LENGTH_LONG).show();
            finish(); return;
        }

        new LibraryStore(this).upsert(uri.toString(),name);
        Intent target=new Intent(this, SourceFactory.isBookFormat(name)?BookReaderActivity.class:ReaderActivity.class);
        target.setData(uri);
        target.putExtra("name",name);
        target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | grants);
        startActivity(target);
        finish();
    }

    private boolean supported(String n){
        String l=n==null?"":n.toLowerCase(Locale.ROOT);
        return l.matches(".*\\.(cbz|cbr|cb7|cbt|zip|rar|7z|tar|pdf|epub|fb2|txt|html|htm|jpg|jpeg|png|webp|gif|bmp)$");
    }

    private String extensionForMime(String m){
        if(m==null)return null;
        m=m.toLowerCase(Locale.ROOT);
        if(m.equals("application/pdf"))return ".pdf";
        if(m.equals("application/epub+zip"))return ".epub";
        if(m.contains("fictionbook")||m.contains("fb2"))return ".fb2";
        if(m.equals("text/plain"))return ".txt";
        if(m.equals("text/html")||m.equals("application/xhtml+xml"))return ".html";
        if(m.contains("rar"))return ".rar";
        if(m.contains("7z"))return ".7z";
        if(m.contains("tar"))return ".tar";
        if(m.contains("zip"))return ".zip";
        if(m.equals("image/jpeg"))return ".jpg";
        if(m.equals("image/png"))return ".png";
        if(m.equals("image/webp"))return ".webp";
        if(m.equals("image/gif"))return ".gif";
        if(m.equals("image/bmp"))return ".bmp";
        return null;
    }
}
