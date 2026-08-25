package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.util.List;

final class SignaturePdfTools {
    private SignaturePdfTools(){}
    static Uri apply(Context ctx,Uri pdfUri,File signaturePng,List<SignaturePlacement> placements)throws Exception{
        if(placements==null||placements.isEmpty())throw new IllegalArgumentException("Не размещена ни одна подпись");File input=FileStore.copyUriToTemp(ctx,pdfUri,".pdf");File out=File.createTempFile("signed_place_",".pdf",ctx.getCacheDir());Bitmap signature=BitmapFactory.decodeFile(signaturePng.getAbsolutePath());if(signature==null){input.delete();out.delete();throw new IllegalArgumentException("Не удалось прочитать подпись");}
        try(PDDocument doc=PDDocument.load(input)){PDImageXObject image=LosslessFactory.createFromImage(doc,signature);for(SignaturePlacement place:placements){if(place.page<0||place.page>=doc.getNumberOfPages())continue;PDPage page=doc.getPage(place.page);float pw=page.getMediaBox().getWidth(),ph=page.getMediaBox().getHeight();float x=place.x*pw,w=place.width*pw,h=place.height*ph;float y=ph-(place.y*ph)-h;x=Math.max(0,Math.min(x,pw-w));y=Math.max(0,Math.min(y,ph-h));try(PDPageContentStream cs=new PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true)){cs.drawImage(image,x,y,w,h);}}doc.save(out);}finally{signature.recycle();input.delete();}
        try{return FileStore.publishFile(ctx,out,"Подписано_свободно_"+System.currentTimeMillis()+".pdf","application/pdf",null);}finally{out.delete();}
    }
}
