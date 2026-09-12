package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class PptxSlideTools {
    static final class Slide {
        final String xml;
        final String relationshipId;
        final String title;
        Slide(String xml, String relationshipId, String title) { this.xml=xml; this.relationshipId=relationshipId; this.title=title; }
    }

    private static final Pattern SLD = Pattern.compile("<(?:[A-Za-z0-9_]+:)?sldId\\b[^>]*/>");
    private static final Pattern RID = Pattern.compile("(?:r:)?id=\"([^\"]+)\"");
    private static final Pattern REL = Pattern.compile("<Relationship\\b[^>]*Id=\"([^\"]+)\"[^>]*Target=\"([^\"]+)\"[^>]*/>");
    private static final Pattern TEXT = Pattern.compile("<(?:[A-Za-z0-9_]+:)?t>(.*?)</(?:[A-Za-z0-9_]+:)?t>", Pattern.DOTALL);

    private PptxSlideTools() {}

    static List<Slide> readSlides(Context ctx, Uri uri) throws Exception {
        File f=FileStore.copyUriToTemp(ctx,uri,".pptx");
        try(ZipFile zip=new ZipFile(f)){
            String presentation=read(zip,"ppt/presentation.xml");
            String rels=read(zip,"ppt/_rels/presentation.xml.rels");
            Map<String,String> targets=new HashMap<>();
            Matcher rm=REL.matcher(rels);while(rm.find())targets.put(rm.group(1),rm.group(2));
            List<Slide> out=new ArrayList<>();Matcher sm=SLD.matcher(presentation);int n=1;
            while(sm.find()){
                String node=sm.group();Matcher im=RID.matcher(node);if(!im.find())continue;String rid=im.group(1);String target=targets.get(rid);String title="Слайд "+n;
                if(target!=null){String path=target.startsWith("/")?target.substring(1):"ppt/"+target;if(zip.getEntry(path)!=null){String sx=read(zip,path);Matcher tm=TEXT.matcher(sx);if(tm.find()){String t=unescape(tm.group(1)).replaceAll("\\s+"," ").trim();if(!t.isBlank())title=t.length()>48?t.substring(0,48)+"…":t;}}}
                out.add(new Slide(node,rid,title));n++;
            }
            if(out.isEmpty())throw new IllegalArgumentException("В презентации не найдены слайды");return out;
        }finally{f.delete();}
    }

    static Uri reorder(Context ctx,Uri uri,List<Slide> order)throws Exception{
        if(order==null||order.isEmpty())throw new IllegalArgumentException("Нет слайдов для сохранения");File input=FileStore.copyUriToTemp(ctx,uri,".pptx");File out=File.createTempFile("pptx_order_",".pptx",ctx.getCacheDir());
        try(ZipFile zip=new ZipFile(input);ZipOutputStream zos=new ZipOutputStream(new java.io.FileOutputStream(out))){String presentation=read(zip,"ppt/presentation.xml");Matcher list=Pattern.compile("(<(?:[A-Za-z0-9_]+:)?sldIdLst\\b[^>]*>)(.*?)(</(?:[A-Za-z0-9_]+:)?sldIdLst>)",Pattern.DOTALL).matcher(presentation);if(!list.find())throw new IllegalArgumentException("Не найден список слайдов PPTX");StringBuilder middle=new StringBuilder();for(Slide s:order)middle.append(s.xml);String changed=presentation.substring(0,list.start())+list.group(1)+middle+list.group(3)+presentation.substring(list.end());
            java.util.Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();ZipEntry ne=new ZipEntry(e.getName());zos.putNextEntry(ne);if(!e.isDirectory()){if("ppt/presentation.xml".equals(e.getName()))zos.write(changed.getBytes(StandardCharsets.UTF_8));else try(InputStream in=zip.getInputStream(e)){FileStore.copy(in,zos);}}zos.closeEntry();}
        }finally{input.delete();}
        try{return FileStore.publishFile(ctx,out,"Конструктор_слайдов_"+System.currentTimeMillis()+".pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation",null);}finally{out.delete();}
    }

    private static String read(ZipFile zip,String path)throws Exception{ZipEntry e=zip.getEntry(path);if(e==null)throw new IllegalArgumentException("Повреждён PPTX: нет "+path);try(InputStream in=zip.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream()){FileStore.copy(in,out);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static String unescape(String s){return s.replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'");}
}
