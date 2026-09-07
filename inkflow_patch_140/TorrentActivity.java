package app.inkflow.reader;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class TorrentActivity extends Activity {
    private static final int PICK_TORRENT=901;
    private LinearLayout root,statusBox,listBox,searchBox;
    private EditText search;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService pool=Executors.newFixedThreadPool(3);
    private Runnable ticker;

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);build();handleIntent(getIntent());startEngine();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIntent(i);}

    private void build(){
        ScrollView sc=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,32));root.setBackgroundColor(Ui.BG);Ui.safeInsets(root,this,0,0,0,0);sc.addView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=btn("‹",true);back.setTextSize(32);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);TextView h=Ui.text(this,"ТОРРЕНТ-ЦЕНТР",22,Ui.GOLD);h.setTypeface(Typeface.DEFAULT_BOLD);tx.addView(h);tx.addView(Ui.text(this,"DHT • magnet • .torrent • загрузчик",11,Ui.MUTED));LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.leftMargin=Ui.dp(this,10);top.addView(tx,xp);root.addView(top);

        statusBox=new LinearLayout(this);statusBox.setOrientation(LinearLayout.VERTICAL);statusBox.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14));statusBox.setBackground(Ui.panel(this));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=Ui.dp(this,16);root.addView(statusBox,sp);

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);TextView magnet=btn("＋ MAGNET",true);magnet.setOnClickListener(v->magnetDialog());TextView file=btn("ОТКРЫТЬ .TORRENT",false);file.setOnClickListener(v->pickTorrent());LinearLayout.LayoutParams a1=new LinearLayout.LayoutParams(0,Ui.dp(this,50),1);a1.setMargins(0,Ui.dp(this,12),Ui.dp(this,6),0);actions.addView(magnet,a1);LinearLayout.LayoutParams a2=new LinearLayout.LayoutParams(0,Ui.dp(this,50),1);a2.setMargins(Ui.dp(this,6),Ui.dp(this,12),0,0);actions.addView(file,a2);root.addView(actions);

        TextView st=Ui.text(this,"ПОИСК ТОРРЕНТОВ",12,Ui.MUTED);st.setTypeface(Typeface.DEFAULT_BOLD);st.setLetterSpacing(.12f);LinearLayout.LayoutParams sth=new LinearLayout.LayoutParams(-1,-2);sth.topMargin=Ui.dp(this,22);root.addView(st,sth);
        TextView note=Ui.text(this,"Встроенный поиск использует открытый каталог Internet Archive. Любой magnet или .torrent можно добавить вручную.",11,Ui.MUTED);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=Ui.dp(this,5);root.addView(note,np);
        LinearLayout sr=new LinearLayout(this);sr.setGravity(Gravity.CENTER_VERTICAL);search=new EditText(this);search.setHint("Книга, комикс, автор…");search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED);search.setSingleLine(true);search.setBackground(Ui.softButton(this));search.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0);TextView go=btn("ИСКАТЬ",true);go.setOnClickListener(v->doSearch());LinearLayout.LayoutParams se=new LinearLayout.LayoutParams(0,Ui.dp(this,52),1);se.setMargins(0,Ui.dp(this,10),Ui.dp(this,6),0);sr.addView(search,se);LinearLayout.LayoutParams sg=new LinearLayout.LayoutParams(Ui.dp(this,96),Ui.dp(this,52));sg.setMargins(Ui.dp(this,6),Ui.dp(this,10),0,0);sr.addView(go,sg);root.addView(sr);
        searchBox=new LinearLayout(this);searchBox.setOrientation(LinearLayout.VERTICAL);root.addView(searchBox);

        TextView qh=Ui.text(this,"ЗАГРУЗКИ",12,Ui.MUTED);qh.setTypeface(Typeface.DEFAULT_BOLD);qh.setLetterSpacing(.12f);LinearLayout.LayoutParams qhp=new LinearLayout.LayoutParams(-1,-2);qhp.topMargin=Ui.dp(this,24);root.addView(qh,qhp);
        listBox=new LinearLayout(this);listBox.setOrientation(LinearLayout.VERTICAL);root.addView(listBox);

        LinearLayout ctl=new LinearLayout(this);TextView pause=btn("ПАУЗА",false);pause.setOnClickListener(v->send(TorrentService.ACTION_PAUSE,null));TextView resume=btn("ПРОДОЛЖИТЬ",true);resume.setOnClickListener(v->send(TorrentService.ACTION_RESUME,null));LinearLayout.LayoutParams c1=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);c1.setMargins(0,Ui.dp(this,14),Ui.dp(this,6),0);ctl.addView(pause,c1);LinearLayout.LayoutParams c2=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);c2.setMargins(Ui.dp(this,6),Ui.dp(this,14),0,0);ctl.addView(resume,c2);root.addView(ctl);
        setContentView(sc);startTicker();
    }

    private TextView btn(String s,boolean gold){TextView t=Ui.text(this,s,13,gold?0xFF211500:Ui.TEXT);t.setGravity(Gravity.CENTER);t.setTypeface(Typeface.DEFAULT_BOLD);t.setBackground(gold?Ui.goldButton(this):Ui.softButton(this));return t;}
    private void startEngine(){Intent i=new Intent(this,TorrentService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    private void send(String action,String v){Intent i=new Intent(this,TorrentService.class);i.setAction(action);if(v!=null)i.putExtra(TorrentService.EXTRA_VALUE,v);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}

    private void magnetDialog(){final EditText e=new EditText(this);e.setHint("magnet:?xt=urn:btih:…");e.setSingleLine(false);e.setMinLines(3);new AlertDialog.Builder(this).setTitle("Добавить magnet").setView(e).setNegativeButton("Отмена",null).setPositiveButton("Загрузить",(d,w)->{String m=e.getText().toString().trim();if(m.startsWith("magnet:?")){send(TorrentService.ACTION_ADD_MAGNET,m);Toast.makeText(this,"Добавлено",Toast.LENGTH_SHORT).show();}else Toast.makeText(this,"Это не magnet-ссылка",Toast.LENGTH_LONG).show();}).show();}
    private void pickTorrent(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/x-bittorrent");startActivityForResult(i,PICK_TORRENT);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==PICK_TORRENT&&c==RESULT_OK&&d!=null&&d.getData()!=null)copyTorrent(d.getData());}
    private void copyTorrent(Uri u){pool.submit(()->{try{File dir=new File(getFilesDir(),"torrents");dir.mkdirs();File out=new File(dir,"manual_"+System.currentTimeMillis()+".torrent");try(InputStream in=getContentResolver().openInputStream(u);OutputStream o=new FileOutputStream(out)){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)o.write(b,0,n);}runOnUiThread(()->send(TorrentService.ACTION_ADD_TORRENT,out.getAbsolutePath()));}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Ошибка: "+e.getMessage(),Toast.LENGTH_LONG).show());}});}
    private void handleIntent(Intent i){if(i==null)return;Uri u=i.getData();if(u!=null&&"magnet".equalsIgnoreCase(u.getScheme()))send(TorrentService.ACTION_ADD_MAGNET,u.toString());else if(u!=null&&Intent.ACTION_VIEW.equals(i.getAction()))copyTorrent(u);}

    private void doSearch(){String q=search.getText().toString().trim();if(q.isEmpty())return;searchBox.removeAllViews();searchBox.addView(Ui.text(this,"Ищу в открытом архиве…",12,Ui.GOLD));pool.submit(()->{try{JSONArray docs=InternetArchiveSearch.search(q);runOnUiThread(()->renderSearch(docs));}catch(Exception e){runOnUiThread(()->{searchBox.removeAllViews();searchBox.addView(Ui.text(this,"Ошибка поиска: "+e.getMessage(),12,Ui.HOT));});}});}
    private void renderSearch(JSONArray docs){searchBox.removeAllViews();if(docs.length()==0){searchBox.addView(Ui.text(this,"Ничего не найдено",12,Ui.MUTED));return;}for(int i=0;i<docs.length();i++){JSONObject o=docs.optJSONObject(i);if(o==null)continue;String id=o.optString("identifier"),title=o.optString("title",id),year=o.optString("year","");LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));card.setBackground(Ui.panel(this));TextView t=Ui.text(this,title,14,Ui.TEXT);t.setTypeface(Typeface.DEFAULT_BOLD);card.addView(t);card.addView(Ui.text(this,(year.isEmpty()?"Internet Archive":year+" • Internet Archive")+" • нажми для загрузки",11,Ui.GOLD));card.setOnClickListener(v->downloadArchiveTorrent(id,title));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=Ui.dp(this,9);searchBox.addView(card,p);}}
    private void downloadArchiveTorrent(String id,String title){Toast.makeText(this,"Получаю .torrent…",Toast.LENGTH_SHORT).show();pool.submit(()->{try{File dir=new File(getFilesDir(),"torrents");dir.mkdirs();File f=InternetArchiveSearch.downloadTorrent(id,dir);runOnUiThread(()->{send(TorrentService.ACTION_ADD_TORRENT,f.getAbsolutePath());Toast.makeText(this,"Загрузка добавлена: "+title,Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"У этого элемента нет доступного .torrent: "+e.getMessage(),Toast.LENGTH_LONG).show());}});}

    private void startTicker(){ticker=new Runnable(){public void run(){refreshStatus();handler.postDelayed(this,1200);}};handler.post(ticker);}
    private void refreshStatus(){try{JSONObject r=new JSONObject(getSharedPreferences(TorrentService.PREFS,MODE_PRIVATE).getString(TorrentService.STATUS,"{}"));statusBox.removeAllViews();TextView h=Ui.text(this,r.optBoolean("engine")?"Сеть активна":"Запуск сети…",16,r.optBoolean("engine")?Ui.MINT:Ui.GOLD);h.setTypeface(Typeface.DEFAULT_BOLD);statusBox.addView(h);statusBox.addView(Ui.text(this,"↓ "+speed(r.optLong("down"))+"   ↑ "+speed(r.optLong("up"))+"   • DHT: "+r.optLong("dht")+"   • Пиры: "+r.optInt("peers"),12,Ui.TEXT));String tr=r.optString("tracker","");if(!tr.isEmpty())statusBox.addView(Ui.text(this,"Трекер: "+tr,10,Ui.MUTED));String er=r.optString("error","");if(!er.isEmpty())statusBox.addView(Ui.text(this,er,11,Ui.HOT));listBox.removeAllViews();JSONArray a=r.optJSONArray("items");if(a==null||a.length()==0){listBox.addView(Ui.text(this,"Активных загрузок пока нет",12,Ui.MUTED));return;}for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));card.setBackground(Ui.panel(this));TextView name=Ui.text(this,o.optString("name","Торрент"),14,Ui.TEXT);name.setTypeface(Typeface.DEFAULT_BOLD);card.addView(name);ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);pb.setProgress(o.optInt("progress"));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,Ui.dp(this,8));pp.topMargin=Ui.dp(this,8);card.addView(pb,pp);String state=o.optBoolean("finished")?"Готово":o.optBoolean("paused")?"Пауза":"Загрузка";card.addView(Ui.text(this,state+" • "+o.optInt("progress")+"% • ↓ "+speed(o.optLong("down"))+" • "+o.optInt("peers")+" пиров",11,o.optBoolean("finished")?Ui.MINT:Ui.GOLD));String tt=o.optString("tracker","");if(!tt.isEmpty())card.addView(Ui.text(this,tt,9,Ui.MUTED));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=Ui.dp(this,9);listBox.addView(card,cp);}}catch(Exception ignored){}}
    private String speed(long b){if(b<1024)return b+" Б/с";if(b<1048576)return String.format(Locale.US,"%.1f КБ/с",b/1024f);return String.format(Locale.US,"%.1f МБ/с",b/1048576f);}
    @Override protected void onDestroy(){if(ticker!=null)handler.removeCallbacks(ticker);pool.shutdownNow();super.onDestroy();}
}
