package app.inkflow.reader;

import android.app.*;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class LibraryActivity extends Activity {
    private LibraryStore store;
    private GridView grid;
    private LibraryAdapter adapter;
    private final ExecutorService pool=Executors.newFixedThreadPool(3);
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private LinearLayout selectBar;
    private TextView selectCount;
    private EditText search;
    private boolean lastDay;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);AppTheme.apply(this);lastDay=AppTheme.isDay(this);store=new LibraryStore(this);build();
    }
    @Override protected void onResume(){
        super.onResume();boolean day=AppTheme.isDay(this);if(day!=lastDay){lastDay=day;AppTheme.apply(this);build();return;}if(adapter!=null)adapter.reload();
    }

    private void build(){
        AppTheme.apply(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);Ui.safeInsets(root,this,14,8,14,8);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=button("‹",true,32);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);TextView h=Ui.text(this,"Вся библиотека",23,Ui.TEXT);h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);titles.addView(h);TextView sub=Ui.text(this,"Последнее открытое всегда сверху",11,Ui.MUTED);titles.addView(sub);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=Ui.dp(this,10);head.addView(titles,hp);
        TextView theme=button(AppTheme.isDay(this)?"☀":"☾",false,20);theme.setOnClickListener(v->{AppTheme.toggle(this);lastDay=AppTheme.isDay(this);build();});head.addView(theme,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        root.addView(head,new LinearLayout.LayoutParams(-1,Ui.dp(this,64)));

        search=new EditText(this);search.setSingleLine(true);search.setHint("Поиск по библиотеке");search.setHintTextColor(Ui.MUTED);search.setTextColor(Ui.TEXT);search.setTextSize(14);search.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0);search.setBackground(Ui.softButton(this));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,Ui.dp(this,48));sp.bottomMargin=Ui.dp(this,10);root.addView(search,sp);

        selectBar=new LinearLayout(this);selectBar.setGravity(Gravity.CENTER_VERTICAL);selectBar.setPadding(Ui.dp(this,10),Ui.dp(this,5),Ui.dp(this,10),Ui.dp(this,5));selectBar.setBackground(Ui.panel(this));selectBar.setVisibility(View.GONE);
        selectCount=Ui.text(this,"Выбрано: 0",13,Ui.TEXT);selectCount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);selectBar.addView(selectCount,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));
        TextView all=button("ВСЕ",false,11);all.setOnClickListener(v->selectAll());selectBar.addView(all,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,38)));
        TextView remove=button("УБРАТЬ",true,11);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(Ui.dp(this,92),Ui.dp(this,38));rp.leftMargin=Ui.dp(this,7);remove.setOnClickListener(v->removeSelected());selectBar.addView(remove,rp);
        TextView cancel=button("×",false,22);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,38));cp.leftMargin=Ui.dp(this,7);cancel.setOnClickListener(v->clearSelection());selectBar.addView(cancel,cp);
        LinearLayout.LayoutParams sbp=new LinearLayout.LayoutParams(-1,Ui.dp(this,50));sbp.bottomMargin=Ui.dp(this,8);root.addView(selectBar,sbp);

        grid=new GridView(this);grid.setNumColumns(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?4:2);grid.setHorizontalSpacing(Ui.dp(this,12));grid.setVerticalSpacing(Ui.dp(this,14));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setClipToPadding(false);grid.setPadding(0,0,0,Ui.dp(this,18));grid.setSelector(android.R.color.transparent);
        adapter=new LibraryAdapter();grid.setAdapter(adapter);
        grid.setOnItemClickListener((a,v,pos,id)->{LibraryStore.Item it=adapter.visible.get(pos);if(!selected.isEmpty())toggle(it.uri);else open(it);});
        grid.setOnItemLongClickListener((a,v,pos,id)->{toggle(adapter.visible.get(pos).uri);return true;});
        root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));

        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){if(adapter!=null)adapter.filter(s.toString());}public void afterTextChanged(Editable e){}});
        setContentView(BannerAdSlot.wrap(this,root));
    }

    private TextView button(String s,boolean gold,float size){TextView t=Ui.text(this,s,size,gold?0xFF211500:Ui.TEXT);t.setGravity(Gravity.CENTER);t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);t.setBackground(gold?Ui.goldButton(this):Ui.softButton(this));return t;}
    private void open(LibraryStore.Item it){store.upsert(it.uri,it.name);Intent i=new Intent(this,SourceFactory.isBookFormat(it.name)?BookReaderActivity.class:ReaderActivity.class);i.setData(Uri.parse(it.uri));i.putExtra("name",it.name);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);adapter.reload();}
    private void toggle(String uri){if(selected.contains(uri))selected.remove(uri);else selected.add(uri);updateSelection();adapter.notifyDataSetChanged();}
    private void selectAll(){selected.clear();for(LibraryStore.Item it:adapter.visible)selected.add(it.uri);updateSelection();adapter.notifyDataSetChanged();}
    private void clearSelection(){selected.clear();updateSelection();adapter.notifyDataSetChanged();}
    private void updateSelection(){selectBar.setVisibility(selected.isEmpty()?View.GONE:View.VISIBLE);selectCount.setText("Выбрано: "+selected.size());}
    private void removeSelected(){
        if(selected.isEmpty())return;int count=selected.size();
        new AlertDialog.Builder(this).setTitle("Убрать из библиотеки?").setMessage("Выбрано: "+count+"\n\nФайлы останутся на устройстве.").setNegativeButton("Отмена",null).setPositiveButton("Убрать",(d,w)->{store.removeMany(selected);selected.clear();updateSelection();adapter.reload();Toast.makeText(this,"Убрано из библиотеки: "+count,Toast.LENGTH_SHORT).show();}).show();
    }

    private final class LibraryAdapter extends BaseAdapter {
        List<LibraryStore.Item> allItems=store.load();List<LibraryStore.Item> visible=new ArrayList<>(allItems);String query="";final Map<String,Bitmap> covers=new ConcurrentHashMap<>();
        void reload(){allItems=store.load();filter(query);}
        void filter(String q){query=q==null?"":q.trim().toLowerCase(Locale.ROOT);visible=new ArrayList<>();for(LibraryStore.Item it:allItems)if(query.isEmpty()||it.name.toLowerCase(Locale.ROOT).contains(query))visible.add(it);notifyDataSetChanged();}
        public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View convert,ViewGroup parent){
            LibraryStore.Item it=visible.get(p);boolean checked=selected.contains(it.uri);
            LinearLayout card=new LinearLayout(LibraryActivity.this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(Ui.dp(LibraryActivity.this,7),Ui.dp(LibraryActivity.this,7),Ui.dp(LibraryActivity.this,7),Ui.dp(LibraryActivity.this,9));card.setBackground(checked?Ui.stroke(Ui.SURFACE2,2,Ui.GOLD,18,LibraryActivity.this):Ui.panel(LibraryActivity.this));card.setElevation(Ui.dp(LibraryActivity.this,4));
            FrameLayout coverWrap=new FrameLayout(LibraryActivity.this);coverWrap.setBackground(Ui.gradient(new int[]{Ui.SURFACE3,Ui.SURFACE},16,LibraryActivity.this));card.addView(coverWrap,new LinearLayout.LayoutParams(-1,Ui.dp(LibraryActivity.this,232)));
            CoverPlaceholderView ph=new CoverPlaceholderView(LibraryActivity.this,it.name);coverWrap.addView(ph,new FrameLayout.LayoutParams(-1,-1));
            ImageView cover=new ImageView(LibraryActivity.this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setVisibility(View.INVISIBLE);coverWrap.addView(cover,new FrameLayout.LayoutParams(-1,-1));
            if(checked){TextView mark=button("✓",true,17);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(Ui.dp(LibraryActivity.this,38),Ui.dp(LibraryActivity.this,38),Gravity.TOP|Gravity.END);mp.setMargins(0,Ui.dp(LibraryActivity.this,8),Ui.dp(LibraryActivity.this,8),0);coverWrap.addView(mark,mp);}
            TextView title=Ui.text(LibraryActivity.this,it.name,14,Ui.TEXT);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,Ui.dp(LibraryActivity.this,48));tp.setMargins(Ui.dp(LibraryActivity.this,4),Ui.dp(LibraryActivity.this,8),Ui.dp(LibraryActivity.this,4),0);card.addView(title,tp);
            String line=it.progress>0?"Продолжить • стр. "+(it.progress+1):(p==0?"Последнее открытое":"В библиотеке");TextView status=Ui.text(LibraryActivity.this,line,11,it.progress>0?Ui.MINT:Ui.GOLD);status.setPadding(Ui.dp(LibraryActivity.this,4),0,0,0);card.addView(status,new LinearLayout.LayoutParams(-1,Ui.dp(LibraryActivity.this,22)));
            Bitmap cached=covers.get(it.uri);if(cached!=null){cover.setImageBitmap(cached);cover.setVisibility(View.VISIBLE);}else{cover.setTag(it.uri);pool.submit(()->{try(ComicSource src=SourceFactory.open(LibraryActivity.this,Uri.parse(it.uri))){Bitmap b=src.renderPage(0,560);if(b!=null){covers.put(it.uri,b);runOnUiThread(()->{if(it.uri.equals(cover.getTag())){cover.setImageBitmap(b);cover.setVisibility(View.VISIBLE);}});}}catch(Exception ignored){}});}            
            return card;
        }
    }

    @Override public void onBackPressed(){if(!selected.isEmpty()){clearSelection();return;}super.onBackPressed();}
    @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
