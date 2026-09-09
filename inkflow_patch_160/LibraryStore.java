package app.inkflow.reader;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

public final class LibraryStore {
    public static final class Item { public String uri,name; public int progress; public long lastOpened; public Item(String u,String n){uri=u;name=n;} }
    private final SharedPreferences p;
    public LibraryStore(Context c){p=c.getSharedPreferences("inkflow_library",Context.MODE_PRIVATE);}
    public synchronized List<Item> load(){
        ArrayList<Item> out=new ArrayList<>();
        try{JSONArray a=new JSONArray(p.getString("items","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Item it=new Item(o.getString("uri"),o.optString("name","Файл"));it.progress=o.optInt("progress",0);it.lastOpened=o.optLong("lastOpened",0);out.add(it);}}catch(Exception ignored){}
        out.sort((a,b)->Long.compare(b.lastOpened,a.lastOpened)); return out;
    }
    public synchronized void upsert(String uri,String name){List<Item> list=load();Item found=null;for(Item i:list)if(i.uri.equals(uri)){found=i;break;}if(found==null){found=new Item(uri,name);list.add(0,found);}found.name=name;found.lastOpened=System.currentTimeMillis();save(list);}
    public synchronized void setProgress(String uri,int page){List<Item> list=load();Item found=null;for(Item i:list)if(i.uri.equals(uri)){found=i;break;}if(found==null){found=new Item(uri,"Комикс");list.add(0,found);}found.progress=Math.max(0,page);found.lastOpened=System.currentTimeMillis();save(list);}
    public synchronized int getProgress(String uri){for(Item i:load())if(i.uri.equals(uri))return i.progress;return 0;}
    public synchronized void remove(String uri){List<Item> list=load();list.removeIf(i->i.uri.equals(uri));save(list);}
    public synchronized void removeMany(Collection<String> uris){if(uris==null||uris.isEmpty())return;HashSet<String> set=new HashSet<>(uris);List<Item> list=load();list.removeIf(i->set.contains(i.uri));save(list);}
    private void save(List<Item> list){try{JSONArray a=new JSONArray();for(Item i:list){JSONObject o=new JSONObject();o.put("uri",i.uri);o.put("name",i.name);o.put("progress",i.progress);o.put("lastOpened",i.lastOpened);a.put(o);}p.edit().putString("items",a.toString()).commit();}catch(Exception ignored){}}
}
