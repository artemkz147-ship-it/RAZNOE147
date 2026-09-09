package app.inkflow.reader;

import android.content.Context;
import android.net.Uri;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public final class DocumentSourceFactory {
    private DocumentSourceFactory(){}

    public static boolean isDocumentFormat(String name){
        String l=name==null?"":name.toLowerCase(Locale.ROOT);
        return l.endsWith(".docx")||l.endsWith(".docm")||l.endsWith(".doc")||l.endsWith(".rtf")||l.endsWith(".odt");
    }

    public static BookContent open(Context c, Uri uri, String name) throws Exception{
        String l=name==null?"":name.toLowerCase(Locale.ROOT);
        if(l.endsWith(".docx")||l.endsWith(".docm")) return docx(c,uri,name);
        if(l.endsWith(".doc")) return doc(c,uri,name);
        if(l.endsWith(".odt")) return odt(c,uri,name);
        if(l.endsWith(".rtf")) return rtf(c,uri,name);
        throw new IOException("Неподдерживаемый документ: "+name);
    }

    private static BookContent docx(Context c,Uri uri,String name)throws Exception{
        File f=copy(c,uri,".docx");
        try(ZipFile z=new ZipFile(f)){
            ZipEntry e=z.getEntry("word/document.xml");
            if(e==null)throw new IOException("DOCX повреждён");
            Document d=parse(z.getInputStream(e));
            String title=strip(name); StringBuilder h=head(title);
            NodeList ps=d.getElementsByTagName("w:p");
            for(int i=0;i<ps.getLength();i++){
                Element p=(Element)ps.item(i); String t=docxText(p).trim(); if(t.isEmpty())continue;
                String st=""; NodeList sl=p.getElementsByTagName("w:pStyle");
                if(sl.getLength()>0){Element x=(Element)sl.item(0);st=x.getAttribute("w:val");if(st.isEmpty())st=x.getAttribute("val");}
                String ls=st.toLowerCase(Locale.ROOT);
                if(ls.contains("title")||ls.contains("heading1"))h.append("<h2>").append(esc(t)).append("</h2>");
                else if(ls.contains("heading"))h.append("<h3>").append(esc(t)).append("</h3>");
                else h.append("<p>").append(esc(t)).append("</p>");
            }
            return new BookContent(title,h.toString());
        }
    }

    private static String docxText(Node n){StringBuilder b=new StringBuilder();docxText(n,b);return b.toString();}
    private static void docxText(Node n,StringBuilder b){
        String k=n.getNodeName(); if("w:t".equals(k))b.append(n.getTextContent()); else if("w:tab".equals(k))b.append('\t'); else if("w:br".equals(k)||"w:cr".equals(k))b.append('\n');
        NodeList ch=n.getChildNodes();for(int i=0;i<ch.getLength();i++)docxText(ch.item(i),b);
    }

    private static BookContent doc(Context c,Uri uri,String name)throws Exception{
        String title=strip(name);StringBuilder h=head(title);
        try(InputStream in=c.getContentResolver().openInputStream(uri);HWPFDocument d=new HWPFDocument(in);WordExtractor x=new WordExtractor(d)){
            for(String p:x.getParagraphText()){String t=p==null?"":p.replace('\r',' ').trim();if(!t.isEmpty())h.append("<p>").append(esc(t)).append("</p>");}
        }
        return new BookContent(title,h.toString());
    }

    private static BookContent odt(Context c,Uri uri,String name)throws Exception{
        File f=copy(c,uri,".odt");try(ZipFile z=new ZipFile(f)){
            ZipEntry e=z.getEntry("content.xml");if(e==null)throw new IOException("ODT повреждён");
            Document d=parse(z.getInputStream(e));String title=strip(name);StringBuilder h=head(title);NodeList all=d.getElementsByTagName("*");
            for(int i=0;i<all.getLength();i++){Element x=(Element)all.item(i);String n=x.getNodeName();if(!"text:p".equals(n)&&!"text:h".equals(n))continue;String t=x.getTextContent()==null?"":x.getTextContent().trim();if(t.isEmpty())continue;h.append("text:h".equals(n)?"<h2>":"<p>").append(esc(t)).append("text:h".equals(n)?"</h2>":"</p>");}
            return new BookContent(title,h.toString());
        }
    }

    private static BookContent rtf(Context c,Uri uri,String name)throws Exception{
        byte[] data;try(InputStream in=c.getContentResolver().openInputStream(uri)){data=readAll(in);}String raw=new String(data,StandardCharsets.ISO_8859_1);
        int cp=1251;Matcher m=Pattern.compile("\\\\ansicpg(\\d+)").matcher(raw);if(m.find())try{cp=Integer.parseInt(m.group(1));}catch(Exception ignored){}
        Charset cs;try{cs=Charset.forName(cp==65001?"UTF-8":"windows-"+cp);}catch(Exception e){cs=Charset.forName("windows-1251");}
        String plain=rtfText(raw,cs);String title=strip(name);StringBuilder h=head(title);
        for(String p:plain.replace("\r","").split("\n+")){String t=p.trim();if(!t.isEmpty())h.append("<p>").append(esc(t)).append("</p>");}
        return new BookContent(title,h.toString());
    }

    private static String rtfText(String s,Charset cs){
        StringBuilder out=new StringBuilder();ArrayDeque<Boolean> stack=new ArrayDeque<>();boolean skip=false;int uc=1;
        for(int i=0;i<s.length();){char ch=s.charAt(i);if(ch=='{'){stack.push(skip);i++;continue;}if(ch=='}'){if(!stack.isEmpty())skip=stack.pop();i++;continue;}if(ch!='\\'){if(!skip&&ch!='\r'&&ch!='\n')out.append(ch);i++;continue;}i++;if(i>=s.length())break;char c=s.charAt(i);
            if(c=='\\'||c=='{'||c=='}'){if(!skip)out.append(c);i++;continue;}
            if(c==39&&i+2<s.length()){try{int b=Integer.parseInt(s.substring(i+1,i+3),16);if(!skip)out.append(new String(new byte[]{(byte)b},cs));}catch(Exception ignored){}i+=3;continue;}
            int a=i;while(i<s.length()&&Character.isLetter(s.charAt(i)))i++;String w=s.substring(a,i);int sign=1;if(i<s.length()&&s.charAt(i)=='-'){sign=-1;i++;}int ns=i;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;Integer num=null;if(i>ns)try{num=Integer.parseInt(s.substring(ns,i))*sign;}catch(Exception ignored){}if(i<s.length()&&s.charAt(i)==' ')i++;
            if(w.equals("fonttbl")||w.equals("colortbl")||w.equals("stylesheet")||w.equals("info")||w.equals("pict")||w.equals("object")||w.equals("header")||w.equals("footer")){skip=true;continue;}if(skip)continue;
            if(w.equals("par")||w.equals("line"))out.append('\n');else if(w.equals("tab"))out.append('\t');else if(w.equals("emdash"))out.append('—');else if(w.equals("endash"))out.append('–');else if(w.equals("bullet"))out.append('•');else if(w.equals("uc")&&num!=null)uc=Math.max(0,num);else if(w.equals("u")&&num!=null){int v=num<0?num+65536:num;out.append((char)v);int n=0;while(i<s.length()&&n<uc&&s.charAt(i)!='\\'&&s.charAt(i)!='{'&&s.charAt(i)!='}'){i++;n++;}}
        }
        return out.toString().replaceAll("[ \\t]+\\n","\n").trim();
    }

    private static Document parse(InputStream in)throws Exception{try(InputStream x=in){DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(false);return f.newDocumentBuilder().parse(x);}}
    private static File copy(Context c,Uri u,String ext)throws IOException{File f=new File(c.getCacheDir(),"document_"+Math.abs(u.toString().hashCode())+ext);try(InputStream in=c.getContentResolver().openInputStream(u);OutputStream out=new FileOutputStream(f)){if(in==null)throw new IOException("Не удалось открыть документ");byte[] b=new byte[16384];int n;while((n=in.read(b))>0)out.write(b,0,n);}return f;}
    private static byte[] readAll(InputStream in)throws IOException{if(in==null)throw new IOException("Не удалось открыть документ");ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toByteArray();}
    private static StringBuilder head(String t){return new StringBuilder("<h1>").append(esc(t)).append("</h1>");}
    private static String strip(String s){if(s==null)return "Документ";int i=s.lastIndexOf('.');return i>0?s.substring(0,i):s;}
    private static String esc(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
