package app.inkflow.reader;

import org.json.*;
import java.io.*;
import java.net.*;

public final class InternetArchiveSearch {
    public static JSONArray search(String query)throws Exception{
        String q="("+query+") AND mediatype:texts";
        String url="https://archive.org/advancedsearch.php?q="+URLEncoder.encode(q,"UTF-8")+"&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year&fl%5B%5D=downloads&sort%5B%5D=downloads+desc&rows=25&page=1&output=json";
        JSONObject root=new JSONObject(get(url));return root.getJSONObject("response").getJSONArray("docs");
    }
    public static File downloadTorrent(String identifier,File dir)throws Exception{
        String safe=identifier.replaceAll("[^A-Za-z0-9._-]","_");File out=new File(dir,safe+".torrent");
        String url="https://archive.org/download/"+identifier+"/"+identifier+"_archive.torrent";
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","ChitaiVse/1.4");int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);try(InputStream in=c.getInputStream();OutputStream o=new FileOutputStream(out)){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)o.write(b,0,n);}if(out.length()<32)throw new IOException("пустой torrent-файл");return out;
    }
    private static String get(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","ChitaiVse/1.4");int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);try(InputStream in=c.getInputStream();ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toString("UTF-8");}}
}
