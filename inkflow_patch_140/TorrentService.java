package app.inkflow.reader;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import org.libtorrent4j.*;
import org.libtorrent4j.swig.torrent_flags_t;
import java.io.*;
import java.util.*;

public class TorrentService extends Service {
    public static final String ACTION_ADD_MAGNET="app.inkflow.reader.ADD_MAGNET";
    public static final String ACTION_ADD_TORRENT="app.inkflow.reader.ADD_TORRENT";
    public static final String ACTION_PAUSE="app.inkflow.reader.PAUSE_TORRENTS";
    public static final String ACTION_RESUME="app.inkflow.reader.RESUME_TORRENTS";
    public static final String ACTION_STOP="app.inkflow.reader.STOP_TORRENTS";
    public static final String EXTRA_VALUE="value";
    public static final String PREFS="torrent_center";
    public static final String STATUS="status_json";
    public static final String SOURCES="sources_json";
    private static final String CHANNEL="torrent_downloads";
    private static final int NOTIFICATION_ID=4401;

    private SessionManager session;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> restored=new HashSet<>();
    private File downloadDir;
    private boolean engineOk=false;
    private Runnable ticker;

    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID,notification("Торрент-центр запускается…",0));
        downloadDir=new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"Читай Всё");
        if(!downloadDir.exists()) downloadDir.mkdirs();
        new Thread(()->{
            try{
                session=new SessionManager(false);
                session.start();
                session.startDht();
                engineOk=true;
                restoreSources();
            }catch(Throwable e){
                saveError("Ошибка торрент-движка: "+e.getMessage());
            }
            runOnMain(this::startTicker);
        },"TorrentEngineStart").start();
    }

    private void runOnMain(Runnable r){handler.post(r);}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null){
            String a=intent.getAction();
            if(ACTION_ADD_MAGNET.equals(a)){
                String v=intent.getStringExtra(EXTRA_VALUE); if(v!=null&&!v.trim().isEmpty()) addSource("magnet:"+v.trim(),true);
            }else if(ACTION_ADD_TORRENT.equals(a)){
                String v=intent.getStringExtra(EXTRA_VALUE); if(v!=null&&!v.trim().isEmpty()) addSource("file:"+v,true);
            }else if(ACTION_PAUSE.equals(a)){
                if(session!=null) session.pause();
            }else if(ACTION_RESUME.equals(a)){
                if(session!=null) session.resume();
            }else if(ACTION_STOP.equals(a)){
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private synchronized void addSource(String source,boolean persist){
        if(restored.contains(source)) return;
        restored.add(source);
        if(persist) persistSources();
        new Thread(()->{
            try{
                waitEngine();
                if(source.startsWith("magnet:")){
                    String magnet=source.substring(7);
                    session.download(magnet,downloadDir,new torrent_flags_t());
                }else if(source.startsWith("file:")){
                    File f=new File(source.substring(5));
                    TorrentInfo ti=new TorrentInfo(f);
                    session.download(ti,downloadDir);
                }
            }catch(Throwable e){saveError("Не удалось добавить торрент: "+e.getMessage());}
        },"TorrentAdd").start();
    }

    private void waitEngine() throws InterruptedException {
        int i=0; while(!engineOk && i++<100) Thread.sleep(100);
        if(!engineOk) throw new IllegalStateException("движок не запущен");
    }

    private void restoreSources(){
        try{
            JSONArray arr=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString(SOURCES,"[]"));
            for(int i=0;i<arr.length();i++){String s=arr.optString(i); if(!s.isEmpty()) addSource(s,false);}
        }catch(Exception ignored){}
    }

    private synchronized void persistSources(){
        JSONArray a=new JSONArray(); for(String s:restored)a.put(s);
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(SOURCES,a.toString()).apply();
    }

    private void startTicker(){
        if(ticker!=null) return;
        ticker=new Runnable(){public void run(){updateStatus();handler.postDelayed(this,1200);}};
        handler.post(ticker);
    }

    private void updateStatus(){
        JSONArray arr=new JSONArray();
        long down=0,up=0,dht=0; int totalPeers=0,active=0; String tracker="";
        try{
            if(session!=null&&session.isRunning()){
                down=session.downloadRate();up=session.uploadRate();dht=session.dhtNodes();
                List<TorrentHandle> hs=new SessionHandle(session.swig()).torrents();
                boolean paused=session.isPaused();
                for(TorrentHandle h:hs){
                    try{
                        TorrentStatus s=h.status();
                        JSONObject o=new JSONObject();
                        String name=s.name(); if(name==null||name.isEmpty())name="Получение метаданных…";
                        o.put("name",name);o.put("progress",Math.max(0,Math.min(100,(int)(s.progress()*100f))));
                        o.put("down",s.downloadRate());o.put("up",s.uploadRate());o.put("peers",s.numPeers());
                        o.put("finished",s.isFinished());o.put("paused",paused);
                        String tr=s.currentTracker(); if(tr!=null&&!tr.isEmpty()){o.put("tracker",tr);if(tracker.isEmpty())tracker=tr;}else o.put("tracker","");
                        o.put("done",s.totalWantedDone());o.put("total",s.totalWanted());
                        arr.put(o);totalPeers+=s.numPeers();active++;
                    }catch(Throwable ignored){}
                }
            }
            JSONObject root=new JSONObject();root.put("items",arr);root.put("down",down);root.put("up",up);root.put("dht",dht);root.put("peers",totalPeers);root.put("active",active);root.put("tracker",tracker);root.put("dir",downloadDir.getAbsolutePath());root.put("engine",engineOk);
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(STATUS,root.toString()).apply();
            updateNotification(root);
            autoImportCompleted(arr);
        }catch(Throwable e){saveError(e.getMessage());}
    }

    private void autoImportCompleted(JSONArray arr){
        boolean any=false; for(int i=0;i<arr.length();i++) if(arr.optJSONObject(i)!=null&&arr.optJSONObject(i).optBoolean("finished")){any=true;break;}
        if(!any)return;
        new Thread(()->scanFiles(downloadDir),"TorrentImport").start();
    }

    private void scanFiles(File f){
        if(f==null||!f.exists())return;
        if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File x:a)scanFiles(x);return;}
        String n=f.getName().toLowerCase(Locale.ROOT);
        if(!n.matches(".*\\.(cbz|cbr|cb7|cbt|zip|rar|7z|tar|pdf|epub|fb2|txt|html|htm|jpg|jpeg|png|webp|gif|bmp)$"))return;
        try{new LibraryStore(this).upsert(Uri.fromFile(f).toString(),f.getName());}catch(Exception ignored){}
    }

    private void updateNotification(JSONObject root){
        String text=root.optInt("active")+" • ↓ "+speed(root.optLong("down"))+" • ↑ "+speed(root.optLong("up"));
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); if(nm!=null)nm.notify(NOTIFICATION_ID,notification(text,root.optInt("active")));
    }

    private Notification notification(String text,int count){
        Intent open=new Intent(this,TorrentActivity.class);PendingIntent pi=PendingIntent.getActivity(this,4401,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle("Читай Всё • Торренты").setContentText(text).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();
    }

    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(n!=null)n.createNotificationChannel(new NotificationChannel(CHANNEL,"Торрент-загрузки",NotificationManager.IMPORTANCE_LOW));}}
    private void saveError(String e){try{JSONObject r=new JSONObject(getSharedPreferences(PREFS,MODE_PRIVATE).getString(STATUS,"{}"));r.put("error",e==null?"Неизвестная ошибка":e);getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(STATUS,r.toString()).apply();}catch(Exception ignored){}}
    private String speed(long b){if(b<1024)return b+" Б/с";if(b<1024*1024)return String.format(Locale.US,"%.1f КБ/с",b/1024f);return String.format(Locale.US,"%.1f МБ/с",b/1048576f);}

    @Override public void onDestroy(){
        if(ticker!=null)handler.removeCallbacks(ticker);
        if(session!=null)new Thread(()->{try{session.stop();}catch(Throwable ignored){}}).start();
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
