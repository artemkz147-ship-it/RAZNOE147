package ru.filemaster.offline;

import android.graphics.Bitmap;
import android.graphics.Color;

final class DocumentFrameDetector {
    private DocumentFrameDetector() {}

    static float[] detect(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w < 80 || h < 80) return null;

        int[] bg = cornerBackground(bitmap);
        int bgLum = luminance(bg[0], bg[1], bg[2]);
        int step = Math.max(2, Math.min(w, h) / 520);
        int minX = w, minY = h, maxX = -1, maxY = -1, count = 0;

        int borderX = Math.max(step, w / 100);
        int borderY = Math.max(step, h / 100);
        for (int y = borderY; y < h - borderY; y += step) {
            for (int x = borderX; x < w - borderX; x += step) {
                int c = bitmap.getPixel(x, y);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                int dist = Math.abs(r-bg[0]) + Math.abs(g-bg[1]) + Math.abs(b-bg[2]);
                int lum = luminance(r,g,b);
                boolean different = dist >= 72 || lum >= bgLum + 32 || lum <= bgLum - 45;
                boolean paperLike = lum >= 145 && bgLum < 135;
                if (!different && !paperLike) continue;
                count++;
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
            }
        }

        if (count < 80 || maxX <= minX || maxY <= minY) return null;
        float rw = maxX - minX, rh = maxY - minY;
        float area = rw * rh / (float)(w * h);
        if (area < .20f || area > .985f) return null;

        float padX = Math.max(2f, rw * .018f), padY = Math.max(2f, rh * .018f);
        float l = clamp((minX - padX) / w), t = clamp((minY - padY) / h);
        float r = clamp((maxX + padX) / w), b = clamp((maxY + padY) / h);
        if (r-l < .28f || b-t < .28f) return null;
        return new float[]{l,t, r,t, r,b, l,b};
    }

    private static int[] cornerBackground(Bitmap b) {
        int w=b.getWidth(),h=b.getHeight();
        long rr=0,gg=0,bb=0,n=0;
        int sizeX=Math.max(6,w/18),sizeY=Math.max(6,h/18),sx=Math.max(1,sizeX/6),sy=Math.max(1,sizeY/6);
        int[][] origins={{0,0},{w-sizeX,0},{0,h-sizeY},{w-sizeX,h-sizeY}};
        for(int[] o:origins) for(int y=o[1];y<o[1]+sizeY;y+=sy) for(int x=o[0];x<o[0]+sizeX;x+=sx){int c=b.getPixel(Math.max(0,Math.min(w-1,x)),Math.max(0,Math.min(h-1,y)));rr+=Color.red(c);gg+=Color.green(c);bb+=Color.blue(c);n++;}
        if(n==0)return new int[]{128,128,128};
        return new int[]{(int)(rr/n),(int)(gg/n),(int)(bb/n)};
    }
    private static int luminance(int r,int g,int b){return (r*299+g*587+b*114)/1000;}
    private static float clamp(float v){return Math.max(.015f,Math.min(.985f,v));}
}
