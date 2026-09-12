package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class ImageAdvancedTools {
    static final class Size { final int width,height; Size(int w,int h){width=w;height=h;} }
    private static final long MAX_FULL_DECODE_PIXELS=30_000_000L;
    private ImageAdvancedTools(){}

    static Size size(Context ctx,Uri uri)throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(InputStream in=ctx.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}if(o.outWidth<=0||o.outHeight<=0)throw new IllegalArgumentException("Не удалось прочитать размер изображения");return new Size(o.outWidth,o.outHeight);
    }

    static Uri optimize(Context ctx,Uri uri,int quality)throws Exception{
        quality=Math.max(20,Math.min(100,quality));Bitmap bmp=decodeFullSafe(ctx,uri);try{return publishJpeg(ctx,bmp,quality,"Оптимизировано_Q"+quality+"_"+System.currentTimeMillis()+".jpg");}finally{bmp.recycle();}
    }

    static Uri optimizeLossless(Context ctx,Uri uri)throws Exception{
        Bitmap bmp=decodeFullSafe(ctx,uri);try{
            if(Build.VERSION.SDK_INT>=30)return publish(ctx,bmp,Bitmap.CompressFormat.WEBP_LOSSLESS,100,"Без_потерь_"+System.currentTimeMillis()+".webp","image/webp",".webp");
            return publish(ctx,bmp,Bitmap.CompressFormat.PNG,100,"Без_потерь_"+System.currentTimeMillis()+".png","image/png",".png");
        }finally{bmp.recycle();}
    }

    private static Bitmap decodeFullSafe(Context ctx,Uri uri)throws Exception{
        Size s=size(ctx,uri);long pixels=(long)s.width*s.height;if(pixels>MAX_FULL_DECODE_PIXELS)throw new IllegalArgumentException("Изображение больше 30 Мп. Для обработки без уменьшения сначала используйте «Размер и пропорции» или кадрирование.");Bitmap bmp;try(InputStream in=ctx.getContentResolver().openInputStream(uri)){bmp=BitmapFactory.decodeStream(in);}if(bmp==null)throw new IllegalArgumentException("Не удалось декодировать изображение");return bmp;
    }

    static Uri resizeExact(Context ctx,Uri uri,int width,int height)throws Exception{
        if(width<1||height<1||width>12000||height>12000)throw new IllegalArgumentException("Размер каждой стороны должен быть 1–12000 px");long pixels=(long)width*height;if(pixels>50_000_000L)throw new IllegalArgumentException("Результат больше 50 мегапикселей. Уменьшите ширину или высоту.");Bitmap src=ImageTools.loadScaled(ctx,uri,Math.max(width,height));Bitmap out=Bitmap.createScaledBitmap(src,width,height,true);try{return publishJpeg(ctx,out,95,"Размер_"+width+"x"+height+"_"+System.currentTimeMillis()+".jpg");}finally{if(out!=src&&!out.isRecycled())out.recycle();if(!src.isRecycled())src.recycle();}
    }

    static Uri cropRegion(Context ctx,Uri uri,int x,int y,int width,int height)throws Exception{
        Size size=size(ctx,uri);x=Math.max(0,Math.min(x,size.width-1));y=Math.max(0,Math.min(y,size.height-1));width=Math.max(1,Math.min(width,size.width-x));height=Math.max(1,Math.min(height,size.height-y));android.graphics.Rect r=new android.graphics.Rect(x,y,x+width,y+height);Bitmap region=null;try(InputStream in=ctx.getContentResolver().openInputStream(uri)){BitmapRegionDecoder d=BitmapRegionDecoder.newInstance(in,false);if(d==null)throw new IllegalArgumentException("Формат не поддерживает точное кадрирование");try{BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.ARGB_8888;region=d.decodeRegion(r,o);}finally{d.recycle();}}
        if(region==null)throw new IllegalArgumentException("Не удалось вырезать выбранную область");try{return publishJpeg(ctx,region,97,"Кадр_"+width+"x"+height+"_"+System.currentTimeMillis()+".jpg");}finally{region.recycle();}
    }

    static Uri publishTransparentPng(Context ctx,Bitmap bitmap,String prefix)throws Exception{return publish(ctx,bitmap,Bitmap.CompressFormat.PNG,100,prefix+"_"+System.currentTimeMillis()+".png","image/png",".png");}
    static Uri publishJpeg(Context ctx,Bitmap bitmap,int quality,String name)throws Exception{return publish(ctx,bitmap,Bitmap.CompressFormat.JPEG,quality,name,"image/jpeg",".jpg");}
    private static Uri publish(Context ctx,Bitmap bitmap,Bitmap.CompressFormat format,int quality,String name,String mime,String suffix)throws Exception{File f=File.createTempFile("doki_img_",suffix,ctx.getCacheDir());try(FileOutputStream out=new FileOutputStream(f)){if(!bitmap.compress(format,quality,out))throw new IllegalStateException("Не удалось сохранить изображение");}try{return FileStore.publishFile(ctx,f,name,mime,null);}finally{f.delete();}}
}
