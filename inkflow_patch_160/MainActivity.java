package app.inkflow.reader;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int OPEN_FILE=10, OPEN_FOLDER=11;
    private LibraryStore store; private GridView grid; private ComicAdapter adapter;
    private final ExecutorService pool=Executors.newFixedThreadPool(3);
    private boolean lastDay;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); AppTheme.apply(this); lastDay=AppTheme.isDay(this);
        store=new LibraryStore(this); build();
    }
    @Override protected void onResume(){
        super.onResume();
        boolean day=AppTheme.isDay(this);
        if(day!=lastDay){lastDay=day;AppTheme.apply(this);build();return;}
        if(adapter!=null)adapter.reload();
    }

    private void build(){
        AppTheme.apply(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);Ui.safeInsets(root,this,16,8,16,8);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(0,0,0,Ui.dp(this,8));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
        TextView brand=Ui.text(this,"ЧИТАЙ ВСЁ",29,Ui.GOLD);brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);brand.setLetterSpacing(.075f);brand.setShadowLayer(Ui.dp(this,9),0,Ui.dp(this,2),AppTheme.isDay(this)?0x16000000:0x66241700);titles.addView(brand);
        TextView sub=Ui.text(this,"Книги • манга • комиксы • документы",12,Ui.MUTED);titles.addView(sub);
        top.addView(titles,new LinearLayout.LayoutParams(0,Ui.dp(this,68),1));
        TextView theme=chip(AppTheme.isDay(this)?"☀":"☾",46,false);theme.setContentDescription("День / ночь");theme.setOnClickListener(v->{AppTheme.toggle(this);lastDay=AppTheme.isDay(this);build();});
        LinearLayout.LayoutParams thp=new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46));thp.rightMargin=Ui.dp(this,8);top.addView(theme,thp);
        TextView gear=chip("⚙",46,false);gear.setOnClickListener(v->showInfo());top.addView(gear,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));
        root.addView(top);

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,Ui.dp(this,3),0,Ui.dp(this,10));
        TextView open=action("✦  Открыть файл",true);open.setOnClickListener(v->pickFile());
        TextView folder=action("▣  Добавить папку",false);folder.setOnClickListener(v->pickFolder());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,Ui.dp(this,56),1);ap.setMargins(0,0,Ui.dp(this,6),0);actions.addView(open,ap);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(this,56),1);bp.setMargins(Ui.dp(this,6),0,0,0);actions.addView(folder,bp);
        root.addView(actions);

        TextView torrents=action("⇩  Торрент-загрузки   •   magnet   •   .torrent",false);
        torrents.setOnClickListener(v->startActivity(new Intent(this,TorrentActivity.class)));
        LinearLayout.LayoutParams tcp=new LinearLayout.LayoutParams(-1,Ui.dp(this,50));tcp.setMargins(0,0,0,Ui.dp(this,11));root.addView(torrents,tcp);

        LinearLayout shelfHead=new LinearLayout(this);shelfHead.setGravity(Gravity.CENTER_VERTICAL);shelfHead.setPadding(0,Ui.dp(this,2),0,Ui.dp(this,7));
        TextView label=Ui.text(this,"ТВОЯ БИБЛИОТЕКА",12,Ui.MUTED);label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);label.setLetterSpacing(.14f);shelfHead.addView(label,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));
        TextView all=action("ВСЯ БИБЛИОТЕКА  →",false);all.setTextSize(11);all.setOnClickListener(v->startActivity(new Intent(this,LibraryActivity.class)));
        shelfHead.addView(all,new LinearLayout.LayoutParams(Ui.dp(this,166),Ui.dp(this,42)));
        root.addView(shelfHead);

        grid=new GridView(this);grid.setNumColumns(isLandscape()?3:2);grid.setHorizontalSpacing(Ui.dp(this,12));grid.setVerticalSpacing(Ui.dp(this,16));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setClipToPadding(false);grid.setPadding(0,0,0,Ui.dp(this,18));grid.setSelector(android.R.color.transparent);
        adapter=new ComicAdapter();grid.setAdapter(adapter);grid.setOnItemClickListener((a,v,pos,id)->openItem(adapter.items.get(pos)));
        grid.setOnItemLongClickListener((a,v,pos,id)->{showItemMenu(adapter.items.get(pos));return true;});
        root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));

        setContentView(BannerAdSlot.wrap(this,root));
    }

    private boolean isLandscape(){return getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;}
    private TextView chip(String s,int h,boolean gold){TextView t=Ui.text(this,s,20,gold?0xFF201400:Ui.TEXT);t.setGravity(Gravity.CENTER);t.setBackground(gold?Ui.goldButton(this):Ui.softButton(this));return t;}
    private TextView action(String s,boolean primary){TextView t=Ui.text(this,s,15,primary?0xFFFFFFFF:Ui.TEXT);t.setGravity(Gravity.CENTER);t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);t.setShadowLayer(Ui.dp(this,3),0,Ui.dp(this,1),0x22000000);t.setBackground(primary?Ui.accentButton(this):Ui.softButton(this));return t;}
    private void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","application/epub+zip","application/zip","application/x-rar-compressed","image/*","text/plain","text/html","application/xml","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/msword","application/rtf","text/rtf","application/vnd.oasis.opendocument.text","application/octet-stream"});startActivityForResult(i,OPEN_FILE);}    
    private void pickFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,OPEN_FOLDER);}    

    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();
        try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        if(request==OPEN_FILE){String n=SourceFactory.fileName(this,u);store.upsert(u.toString(),n);adapter.reload();openUri(u,n);}else if(request==OPEN_FOLDER){scanTree(u);}    }

    private void scanTree(Uri tree){Toast.makeText(this,"Сканирую папку…",Toast.LENGTH_SHORT).show();pool.submit(()->{ArrayList<LibraryStore.Item> found=new ArrayList<>();try{scanNode(tree,DocumentsContract.getTreeDocumentId(tree),found,0);}catch(Exception ignored){}runOnUiThread(()->{for(LibraryStore.Item i:found)store.upsert(i.uri,i.name);adapter.reload();Toast.makeText(this,"Добавлено: "+found.size(),Toast.LENGTH_SHORT).show();});});}
    private void scanNode(Uri tree,String parentId,List<LibraryStore.Item> out,int depth)throws Exception{if(depth>8)return;Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId);try(Cursor c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){if(c==null)return;while(c.moveToNext()){String id=c.getString(0),name=c.getString(1),mime=c.getString(2);if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){scanNode(tree,id,out,depth+1);}else if(supported(name)){Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,id);out.add(new LibraryStore.Item(doc.toString(),name));}}}}
    private boolean supported(String n){String l=n==null?"":n.toLowerCase(Locale.ROOT);return l.matches(".*\\.(cbz|cbr|cb7|cbt|zip|rar|7z|tar|pdf|epub|fb2|txt|html|htm|docx|docm|doc|rtf|odt|jpg|jpeg|png|webp|gif|bmp)$");}
    private void openItem(LibraryStore.Item i){openUri(Uri.parse(i.uri),i.name);}    
    private void showItemMenu(LibraryStore.Item item){
        new AlertDialog.Builder(this).setTitle(item.name).setMessage("Файл останется на устройстве.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Убрать из библиотеки",(d,w)->{store.remove(item.uri);adapter.covers.remove(item.uri);adapter.reload();Toast.makeText(this,"Убрано из библиотеки",Toast.LENGTH_SHORT).show();})
            .show();
    }
    private void openUri(Uri uri,String name){store.upsert(uri.toString(),name);Intent i=new Intent(this,SourceFactory.isBookFormat(name)?BookReaderActivity.class:ReaderActivity.class);i.setData(uri);i.putExtra("name",name);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}    
    private void showInfo(){ startActivity(new Intent(this,BookSettingsActivity.class)); }

    private final class ComicAdapter extends BaseAdapter {
        List<LibraryStore.Item> items=store.load(); final Map<String,Bitmap> covers=new ConcurrentHashMap<>();
        void reload(){items=store.load();notifyDataSetChanged();}
        public int getCount(){return items.size();} public Object getItem(int p){return items.get(p);} public long getItemId(int p){return p;}
        public View getView(int p,View convert,android.view.ViewGroup parent){
            LibraryStore.Item it=items.get(p);
            LinearLayout card=new LinearLayout(MainActivity.this);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(Ui.panel(MainActivity.this));card.setPadding(Ui.dp(MainActivity.this,7),Ui.dp(MainActivity.this,7),Ui.dp(MainActivity.this,7),Ui.dp(MainActivity.this,9));card.setElevation(Ui.dp(MainActivity.this,4));
            FrameLayout coverWrap=new FrameLayout(MainActivity.this);coverWrap.setBackground(Ui.gradient(new int[]{Ui.SURFACE3,Ui.SURFACE},16,MainActivity.this));coverWrap.setForeground(Ui.stroke(0x00000000,1,Ui.alpha(Ui.TEXT,38),16,MainActivity.this));card.addView(coverWrap,new LinearLayout.LayoutParams(-1,Ui.dp(MainActivity.this,220)));
            CoverPlaceholderView placeholder=new CoverPlaceholderView(MainActivity.this,it.name);coverWrap.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
            ImageView cover=new ImageView(MainActivity.this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setVisibility(View.INVISIBLE);coverWrap.addView(cover,new FrameLayout.LayoutParams(-1,-1));
            TextView title=Ui.text(MainActivity.this,it.name,14,Ui.TEXT);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,Ui.dp(MainActivity.this,46));tp.setMargins(Ui.dp(MainActivity.this,4),Ui.dp(MainActivity.this,8),Ui.dp(MainActivity.this,4),0);card.addView(title,tp);
            String progress=it.progress>0?"Продолжить • стр. "+(it.progress+1):(p==0?"Последнее открытое":"Не начато");
            TextView prog=Ui.text(MainActivity.this,progress,11,it.progress>0?Ui.MINT:Ui.GOLD);prog.setPadding(Ui.dp(MainActivity.this,4),0,0,0);card.addView(prog,new LinearLayout.LayoutParams(-1,Ui.dp(MainActivity.this,22)));
            Bitmap cached=covers.get(it.uri);if(cached!=null){cover.setImageBitmap(cached);cover.setVisibility(View.VISIBLE);}else{cover.setTag(it.uri);pool.submit(()->{try(ComicSource src=SourceFactory.open(MainActivity.this,Uri.parse(it.uri))){Bitmap b=src.renderPage(0,500);if(b!=null){covers.put(it.uri,b);runOnUiThread(()->{if(it.uri.equals(cover.getTag())){cover.setImageBitmap(b);cover.setVisibility(View.VISIBLE);}});}}catch(Exception ignored){}});}            
            return card;
        }
    }

    @Override protected void onDestroy(){super.onDestroy();pool.shutdownNow();}
}
