package app.inkflow.reader;

import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public final class InternetArchiveSearch {
    private static final String UA="Mozilla/5.0 (Linux; Android) ChitaiVse/1.4.1";

    public static JSONArray search(String query)throws Exception{
        String raw=query==null?"":query.trim();
        if(raw.isEmpty()) return new JSONArray();

        String phrase=quote(raw);
        String fielded="mediatype:(texts) AND (title:("+phrase+") OR creator:("+phrase+") OR subject:("+phrase+") OR description:("+phrase+"))";
        String broad="mediatype:(texts) AND ("+terms(raw)+")";
        String plain="mediatype:(texts) AND "+phrase;

        LinkedHashMap<String,JSONObject> merged=new LinkedHashMap<>();
        Throwable last=null;
        String[] qs=new String[]{fielded,broad,plain};

        for(String q:qs){
            try{
                merge(merged,advanced(q));
                if(merged.size()>=25) break;
            }catch(Throwable e){last=e;}
            try{
                merge(merged,scrape(q));
                if(merged.size()>=25) break;
            }catch(Throwable e){last=e;}
        }

        if(merged.isEmpty() && last!=null) throw new IOException("Internet Archive недоступен: "+message(last));
        JSONArray out=new JSONArray();
        int n=0;
        for(JSONObject o:merged.values()){
            out.put(o);
            if(++n>=40) break;
        }
        return out;
    }

    private static JSONArray advanced(String q)throws Exception{
        StringBuilder u=new StringBuilder("https://archive.org/advancedsearch.php?");
        u.append("q=").append(enc(q));
        u.append("&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=year&fl%5B%5D=downloads&fl%5B%5D=mediatype");
        u.append("&sort%5B%5D=downloads%20desc&rows=40&page=1&output=json");
        JSONObject root=getJson(u.toString());
        JSONObject response=root.optJSONObject("response");
        return response==null?new JSONArray():response.optJSONArray("docs")==null?new JSONArray():response.optJSONArray("docs");
    }

    private static JSONArray scrape(String q)throws Exception{
        String url="https://archive.org/services/search/v1/scrape?q="+enc(q)+"&fields=identifier,title,creator,year,downloads,mediatype&count=100";
        JSONObject root=getJson(url);
        JSONArray items=root.optJSONArray("items");
        return items==null?new JSONArray():items;
    }

    private static void merge(LinkedHashMap<String,JSONObject> dst,JSONArray src){
        for(int i=0;i<src.length();i++){
            JSONObject o=src.optJSONObject(i); if(o==null)continue;
            String id=o.optString("identifier","").trim(); if(id.isEmpty())continue;
            if(!dst.containsKey(id)) dst.put(id,o);
        }
    }

    private static String quote(String s){
        return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"";
    }

    private static String terms(String s){
        String[] a=s.trim().split("\\s+");
        StringBuilder b=new StringBuilder();
        int used=0;
        for(String x:a){
            x=x.trim(); if(x.isEmpty())continue;
            if(used++>0)b.append(" AND ");
            b.append(quote(x));
            if(used>=8)break;
        }
        return b.length()==0?quote(s):b.toString();
    }

    public static File downloadTorrent(String identifier,File dir)throws Exception{
        if(identifier==null||identifier.trim().isEmpty())throw new IOException("пустой identifier");
        String id=identifier.trim();
        String safe=id.replaceAll("[^A-Za-z0-9._-]","_");
        File out=new File(dir,safe+".torrent");
        String url="https://archive.org/download/"+encPath(id)+"/"+encPath(id)+"_archive.torrent";
        download(url,out);
        if(out.length()<32){out.delete();throw new IOException("пустой torrent-файл");}
        return out;
    }

    private static JSONObject getJson(String url)throws Exception{
        Exception last=null;
        for(int attempt=0;attempt<3;attempt++){
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(18000);c.setReadTimeout(35000);c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent",UA);
                c.setRequestProperty("Accept","application/json,text/plain,*/*");
                c.setRequestProperty("Accept-Language","ru,en;q=0.8");
                c.setRequestProperty("Connection","close");
                int code=c.getResponseCode();
                InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
                String body=read(in);
                if(code>=200&&code<300){
                    if(body==null||body.trim().isEmpty())throw new IOException("пустой ответ");
                    return new JSONObject(body);
                }
                if(code==429||code>=500){last=new IOException("HTTP "+code);sleep(attempt);continue;}
                throw new IOException("HTTP "+code+(body==null||body.isEmpty()?"":" • "+clip(body)));
            }catch(Exception e){last=e;sleep(attempt);}finally{if(c!=null)c.disconnect();}
        }
        throw last==null?new IOException("ошибка сети"):last;
    }

    private static void download(String url,File out)throws Exception{
        Exception last=null;
        for(int attempt=0;attempt<3;attempt++){
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(18000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept","application/x-bittorrent,*/*");
                int code=c.getResponseCode();
                if(code<200||code>=300){if(code==429||code>=500){last=new IOException("HTTP "+code);sleep(attempt);continue;}throw new IOException("HTTP "+code);}
                try(InputStream in=c.getInputStream();OutputStream o=new FileOutputStream(out)){
                    byte[] b=new byte[65536];int n;while((n=in.read(b))>0)o.write(b,0,n);
                }
                return;
            }catch(Exception e){last=e;sleep(attempt);}finally{if(c!=null)c.disconnect();}
        }
        throw last==null?new IOException("ошибка загрузки"):last;
    }

    private static String read(InputStream in)throws IOException{
        if(in==null)return "";
        try(InputStream x=in;ByteArrayOutputStream o=new ByteArrayOutputStream()){
            byte[] b=new byte[32768];int n;while((n=x.read(b))>0){o.write(b,0,n);if(o.size()>6*1024*1024)break;}
            return o.toString("UTF-8");
        }
    }
    private static void sleep(int attempt){try{Thread.sleep(350L*(attempt+1));}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}
    private static String message(Throwable e){String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;}
    private static String clip(String s){s=s.replace('\n',' ').replace('\r',' ').trim();return s.length()>120?s.substring(0,120):s;}
    private static String enc(String s)throws UnsupportedEncodingException{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}
    private static String encPath(String s)throws UnsupportedEncodingException{return URLEncoder.encode(s,"UTF-8").replace("+","%20").replace("%2F","/");}
}
