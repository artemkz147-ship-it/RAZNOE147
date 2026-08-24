package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfVisualEditorActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<Integer, List<Mark>> pageMarks = new HashMap<>();
    private Uri pdfUri;
    private int currentPage = 1;
    private int totalPages = 1;
    private EditorView editor;
    private TextView status;
    private Button prev, next;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        String raw = getIntent().getStringExtra("pdf_uri");
        if (raw == null || raw.isBlank()) { finish(); return; }
        pdfUri = Uri.parse(raw);
        buildUi();
        loadPage(1);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(239, 241, 246));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(dp(10), safe.top + dp(6), dp(10), safe.bottom + dp(10));
            return insets;
        });

        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("←", 30, Color.rgb(25, 28, 36), false); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Редактор PDF", 23, Color.rgb(25, 28, 36), true));
        status = text("Открываю документ…", 13, Color.rgb(93, 99, 112), false); titles.addView(status);
        header.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); root.addView(header);

        LinearLayout pager = new LinearLayout(this); pager.setGravity(Gravity.CENTER_VERTICAL);
        prev = button("← Страница"); prev.setOnClickListener(v -> changePage(-1)); pager.addView(prev, weightedButton());
        next = button("Страница →"); next.setOnClickListener(v -> changePage(1)); pager.addView(next, weightedButton());
        root.addView(pager);

        editor = new EditorView(this);
        root.addView(editor, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        HorizontalScrollView toolsScroll = new HorizontalScrollView(this); toolsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(this); tools.setOrientation(LinearLayout.HORIZONTAL); tools.setPadding(0, dp(6), 0, dp(4));
        tools.addView(toolButton("Ручка", () -> editor.setMode(Mark.PEN)));
        tools.addView(toolButton("Маркер", () -> editor.setMode(Mark.HIGHLIGHT)));
        tools.addView(toolButton("Прямоугольник", () -> editor.setMode(Mark.RECT)));
        tools.addView(toolButton("Линия", () -> editor.setMode(Mark.LINE)));
        tools.addView(toolButton("Текст", this::askText));
        tools.addView(toolButton("Штамп", this::chooseStamp));
        tools.addView(toolButton("Отмена", editor::undo));
        tools.addView(toolButton("Очистить страницу", editor::clearMarks));
        toolsScroll.addView(tools); root.addView(toolsScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        Button save = button("Сохранить новый PDF"); save.setTextSize(16); save.setOnClickListener(v -> savePdf());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)); saveLp.setMargins(0, dp(4), 0, 0); root.addView(save, saveLp);
        setContentView(root);
    }

    private View toolButton(String label, Runnable run) {
        Button b = button(label); b.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)); lp.setMargins(dp(2), 0, dp(2), 0); b.setLayoutParams(lp); return b;
    }

    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private LinearLayout.LayoutParams weightedButton() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f); lp.setMargins(dp(2), 0, dp(2), dp(4)); return lp; }

    private void askText() {
        EditText input = new EditText(this); input.setHint("Текст для вставки"); input.setSingleLine(false); input.setMaxLines(3);
        new AlertDialog.Builder(this).setTitle("Добавить текст").setView(input)
                .setMessage("После подтверждения коснитесь места на странице, куда вставить текст.")
                .setPositiveButton("Разместить", (d,w) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) editor.setPendingText(value, false);
                }).setNegativeButton("Отмена", null).show();
    }

    private void chooseStamp() {
        String[] stamps = {"КОПИЯ", "ПРОВЕРЕНО", "СОГЛАСОВАНО", "ОПЛАЧЕНО", "КОНФИДЕНЦИАЛЬНО", "ЧЕРНОВИК"};
        new AlertDialog.Builder(this).setTitle("Штамп").setItems(stamps, (d,w) -> editor.setPendingText(stamps[w], true)).setNegativeButton("Отмена", null).show();
    }

    private void storeCurrent() {
        if (editor != null) {
            List<Mark> marks = editor.snapshot();
            if (marks.isEmpty()) pageMarks.remove(currentPage); else pageMarks.put(currentPage, marks);
        }
    }

    private void changePage(int delta) {
        int target = currentPage + delta;
        if (target < 1 || target > totalPages) return;
        storeCurrent();
        currentPage = target;
        loadPage(target);
    }

    private void loadPage(int page) {
        status.setText("Страница " + page + " • загружаю…");
        prev.setEnabled(false); next.setEnabled(false);
        worker.submit(() -> {
            File input = null;
            try {
                input = FileStore.copyUriToTemp(this, pdfUri, ".pdf");
                try (PDDocument doc = PDDocument.load(input)) {
                    totalPages = doc.getNumberOfPages();
                    if (totalPages == 0) throw new IllegalArgumentException("PDF без страниц");
                    if (page > totalPages) currentPage = totalPages;
                    PDFRenderer renderer = new PDFRenderer(doc);
                    Bitmap bitmap = renderer.renderImageWithDPI(currentPage - 1, 105, ImageType.RGB);
                    List<Mark> restored = copyMarks(pageMarks.get(currentPage));
                    runOnUiThread(() -> {
                        editor.setPage(bitmap, restored);
                        status.setText("Страница " + currentPage + " из " + totalPages + " • изменений в документе: " + pageMarks.size() + " стр.");
                        prev.setEnabled(currentPage > 1); next.setEnabled(currentPage < totalPages);
                    });
                }
            } catch (Exception e) { runOnUiThread(() -> showError(e)); }
            finally { if (input != null) input.delete(); }
        });
    }

    private void savePdf() {
        storeCurrent();
        if (pageMarks.isEmpty()) { new AlertDialog.Builder(this).setTitle("Нет изменений").setMessage("Добавьте разметку, текст, фигуру или штамп хотя бы на одну страницу.").setPositiveButton("OK", null).show(); return; }
        Map<Integer,List<Mark>> snapshot = new HashMap<>();
        for (Map.Entry<Integer,List<Mark>> e : pageMarks.entrySet()) snapshot.put(e.getKey(), copyMarks(e.getValue()));
        ProgressDialog dialog = ProgressDialog.show(this, "ФайлМастер", "Сохраняю изменения на всех страницах…", true, false);
        worker.submit(() -> {
            File input = null, out = null;
            try {
                input = FileStore.copyUriToTemp(this, pdfUri, ".pdf"); out = File.createTempFile("pdf_editor_", ".pdf", getCacheDir());
                try (PDDocument src = PDDocument.load(input); PDDocument dst = new PDDocument()) {
                    PDFRenderer renderer = new PDFRenderer(src);
                    for (int i = 0; i < src.getNumberOfPages(); i++) {
                        List<Mark> marks = snapshot.get(i + 1);
                        if (marks == null || marks.isEmpty()) { dst.importPage(src.getPage(i)); continue; }
                        Bitmap rendered = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
                        Bitmap edited = rendered.copy(Bitmap.Config.ARGB_8888, true); rendered.recycle();
                        drawMarks(edited, marks);
                        try {
                            PDPage original = src.getPage(i); PDRectangle crop = original.getCropBox(); float w = crop.getWidth(), h = crop.getHeight();
                            int rotation = ((original.getRotation() % 360) + 360) % 360; if (rotation == 90 || rotation == 270) { float t=w;w=h;h=t; }
                            PDPage page = new PDPage(new PDRectangle(w,h)); dst.addPage(page);
                            PDImageXObject image = JPEGFactory.createFromImage(dst, edited, .94f);
                            try (PDPageContentStream cs = new PDPageContentStream(dst, page)) { cs.drawImage(image, 0,0,w,h); }
                        } finally { edited.recycle(); }
                    }
                    dst.save(out);
                }
                Uri result = FileStore.publishFile(this, out, "Отредактирован_PDF_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
                runOnUiThread(() -> { dialog.dismiss(); new AlertDialog.Builder(this).setTitle("PDF готов").setMessage("Изменено страниц: " + snapshot.size())
                        .setPositiveButton("Открыть", (d,w) -> openResult(result)).setNeutralButton("Поделиться", (d,w) -> shareResult(result)).setNegativeButton("Закрыть", (d,w) -> finish()).show(); });
            } catch (Exception e) { runOnUiThread(() -> { dialog.dismiss(); showError(e); }); }
            finally { if (input != null) input.delete(); if (out != null) out.delete(); }
        });
    }

    private static void drawMarks(Bitmap bitmap, List<Mark> marks) {
        Canvas c = new Canvas(bitmap); float min = Math.min(bitmap.getWidth(), bitmap.getHeight());
        for (Mark m : marks) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            if (m.type == Mark.PEN || m.type == Mark.HIGHLIGHT) {
                p.setStyle(Paint.Style.STROKE); p.setColor(m.type == Mark.HIGHLIGHT ? Color.argb(100,255,215,0) : Color.rgb(220,30,45)); p.setStrokeWidth(min * (m.type == Mark.HIGHLIGHT ? .028f : .0055f));
                if (!m.points.isEmpty()) { Path path = new Path(); PointF f=m.points.get(0);path.moveTo(f.x*bitmap.getWidth(),f.y*bitmap.getHeight());for(int i=1;i<m.points.size();i++){PointF q=m.points.get(i);path.lineTo(q.x*bitmap.getWidth(),q.y*bitmap.getHeight());}c.drawPath(path,p); }
            } else if (m.type == Mark.RECT) {
                p.setStyle(Paint.Style.STROKE); p.setColor(Color.rgb(40,105,220)); p.setStrokeWidth(min*.005f); RectF r=scaled(m.rect,bitmap); c.drawRect(r,p);
            } else if (m.type == Mark.LINE) {
                p.setStyle(Paint.Style.STROKE); p.setColor(Color.rgb(40,105,220)); p.setStrokeWidth(min*.005f); c.drawLine(m.a.x*bitmap.getWidth(),m.a.y*bitmap.getHeight(),m.b.x*bitmap.getWidth(),m.b.y*bitmap.getHeight(),p);
            } else if (m.type == Mark.TEXT || m.type == Mark.STAMP) {
                p.setStyle(Paint.Style.FILL); p.setFakeBoldText(m.type==Mark.STAMP); p.setColor(m.type==Mark.STAMP?Color.rgb(185,35,45):Color.rgb(25,28,36)); p.setTextSize(min*(m.type==Mark.STAMP?.045f:.033f));
                float x=m.a.x*bitmap.getWidth(), y=m.a.y*bitmap.getHeight(); if(m.type==Mark.STAMP){Paint box=new Paint(Paint.ANTI_ALIAS_FLAG);box.setStyle(Paint.Style.STROKE);box.setStrokeWidth(min*.004f);box.setColor(Color.rgb(185,35,45));float tw=p.measureText(m.text);c.drawRect(x-min*.012f,y-p.getTextSize()-min*.012f,x+tw+min*.012f,y+min*.012f,box);}c.drawText(m.text,x,y,p);
            }
        }
    }

    private static RectF scaled(RectF r, Bitmap b) { return new RectF(r.left*b.getWidth(),r.top*b.getHeight(),r.right*b.getWidth(),r.bottom*b.getHeight()); }
    private static List<Mark> copyMarks(List<Mark> src) { List<Mark> out=new ArrayList<>(); if(src!=null)for(Mark m:src)out.add(m.copy());return out; }

    private void openResult(Uri uri){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/pdf");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Открыть PDF"));}catch(Exception e){showError(new IllegalStateException("PDF сохранён, но открыть его не удалось"));}}
    private void shareResult(Uri uri){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Поделиться PDF"));}catch(Exception e){showError(new IllegalStateException("Не удалось открыть меню отправки"));}}
    private void showError(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()).setPositiveButton("OK",null).show();}
    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(v.getTypeface(),android.graphics.Typeface.BOLD);return v;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onDestroy(){super.onDestroy();if(editor!=null)editor.release();if(isFinishing())worker.shutdownNow();}

    static final class Mark {
        static final int PEN=1,HIGHLIGHT=2,RECT=3,LINE=4,TEXT=5,STAMP=6;
        int type; final List<PointF> points=new ArrayList<>(); RectF rect; PointF a,b; String text="";
        Mark(int t){type=t;}
        Mark copy(){Mark m=new Mark(type);for(PointF p:points)m.points.add(new PointF(p.x,p.y));if(rect!=null)m.rect=new RectF(rect);if(a!=null)m.a=new PointF(a.x,a.y);if(b!=null)m.b=new PointF(b.x,b.y);m.text=text;return m;}
    }

    static final class EditorView extends View {
        private Bitmap bitmap; private final RectF imageRect=new RectF(); private final List<Mark> marks=new ArrayList<>(); private Mark active; private int mode=Mark.PEN; private String pendingText; private boolean pendingStamp;
        EditorView(android.content.Context ctx){super(ctx);setBackgroundColor(Color.rgb(220,223,230));}
        void setPage(Bitmap b,List<Mark> restored){release();bitmap=b;marks.clear();if(restored!=null)marks.addAll(copyMarks(restored));active=null;pendingText=null;invalidate();}
        void setMode(int m){mode=m;pendingText=null;active=null;invalidate();}
        void setPendingText(String text,boolean stamp){pendingText=text;pendingStamp=stamp;active=null;invalidate();}
        void undo(){if(!marks.isEmpty())marks.remove(marks.size()-1);invalidate();}
        void clearMarks(){marks.clear();active=null;pendingText=null;invalidate();}
        List<Mark> snapshot(){return copyMarks(marks);}
        void release(){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();bitmap=null;}

        @Override protected void onDraw(Canvas c){super.onDraw(c);if(bitmap==null||bitmap.isRecycled())return;calcRect();c.drawBitmap(bitmap,null,imageRect,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));for(Mark m:marks)drawPreview(c,m);if(active!=null)drawPreview(c,active);if(pendingText!=null){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setTextSize(14*getResources().getDisplayMetrics().scaledDensity);p.setShadowLayer(3,1,1,Color.BLACK);c.drawText("Коснитесь страницы для размещения: "+pendingText,12,getHeight()-16,p);}}
        private void calcRect(){float s=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());float w=bitmap.getWidth()*s,h=bitmap.getHeight()*s;imageRect.set((getWidth()-w)/2f,(getHeight()-h)/2f,(getWidth()+w)/2f,(getHeight()+h)/2f);}
        private PointF norm(float x,float y){return new PointF(Math.max(0,Math.min(1,(x-imageRect.left)/imageRect.width())),Math.max(0,Math.min(1,(y-imageRect.top)/imageRect.height())));}
        private boolean inside(float x,float y){return imageRect.contains(x,y);}

        @Override public boolean onTouchEvent(MotionEvent e){if(bitmap==null)return false;calcRect();if(!inside(e.getX(),e.getY()))return true;PointF n=norm(e.getX(),e.getY());
            if(pendingText!=null&&e.getAction()==MotionEvent.ACTION_UP){Mark m=new Mark(pendingStamp?Mark.STAMP:Mark.TEXT);m.a=n;m.text=pendingText;marks.add(m);pendingText=null;invalidate();return true;}
            if(e.getAction()==MotionEvent.ACTION_DOWN){active=new Mark(mode);if(mode==Mark.PEN||mode==Mark.HIGHLIGHT)active.points.add(n);else if(mode==Mark.RECT){active.rect=new RectF(n.x,n.y,n.x,n.y);}else if(mode==Mark.LINE){active.a=n;active.b=new PointF(n.x,n.y);}invalidate();return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE&&active!=null){if(active.type==Mark.PEN||active.type==Mark.HIGHLIGHT)active.points.add(n);else if(active.type==Mark.RECT)active.rect.set(active.rect.left,active.rect.top,n.x,n.y);else if(active.type==Mark.LINE)active.b=n;invalidate();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP&&active!=null){if(active.type==Mark.PEN||active.type==Mark.HIGHLIGHT)active.points.add(n);else if(active.type==Mark.RECT){float l=Math.min(active.rect.left,n.x),r=Math.max(active.rect.left,n.x),t=Math.min(active.rect.top,n.y),b=Math.max(active.rect.top,n.y);active.rect.set(l,t,r,b);}else if(active.type==Mark.LINE)active.b=n;marks.add(active.copy());active=null;invalidate();return true;}return true;}

        private void drawPreview(Canvas c,Mark m){float min=Math.min(imageRect.width(),imageRect.height());Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(m.type==Mark.PEN||m.type==Mark.HIGHLIGHT){p.setStyle(Paint.Style.STROKE);p.setColor(m.type==Mark.HIGHLIGHT?Color.argb(100,255,215,0):Color.rgb(220,30,45));p.setStrokeWidth(min*(m.type==Mark.HIGHLIGHT?.028f:.0055f));if(!m.points.isEmpty()){Path path=new Path();PointF f=m.points.get(0);path.moveTo(imageRect.left+f.x*imageRect.width(),imageRect.top+f.y*imageRect.height());for(int i=1;i<m.points.size();i++){PointF q=m.points.get(i);path.lineTo(imageRect.left+q.x*imageRect.width(),imageRect.top+q.y*imageRect.height());}c.drawPath(path,p);}}
            else if(m.type==Mark.RECT&&m.rect!=null){p.setStyle(Paint.Style.STROKE);p.setColor(Color.rgb(40,105,220));p.setStrokeWidth(min*.005f);c.drawRect(imageRect.left+m.rect.left*imageRect.width(),imageRect.top+m.rect.top*imageRect.height(),imageRect.left+m.rect.right*imageRect.width(),imageRect.top+m.rect.bottom*imageRect.height(),p);}
            else if(m.type==Mark.LINE&&m.a!=null&&m.b!=null){p.setStyle(Paint.Style.STROKE);p.setColor(Color.rgb(40,105,220));p.setStrokeWidth(min*.005f);c.drawLine(imageRect.left+m.a.x*imageRect.width(),imageRect.top+m.a.y*imageRect.height(),imageRect.left+m.b.x*imageRect.width(),imageRect.top+m.b.y*imageRect.height(),p);}
            else if((m.type==Mark.TEXT||m.type==Mark.STAMP)&&m.a!=null){p.setStyle(Paint.Style.FILL);p.setFakeBoldText(m.type==Mark.STAMP);p.setColor(m.type==Mark.STAMP?Color.rgb(185,35,45):Color.rgb(25,28,36));p.setTextSize(min*(m.type==Mark.STAMP?.045f:.033f));float x=imageRect.left+m.a.x*imageRect.width(),y=imageRect.top+m.a.y*imageRect.height();if(m.type==Mark.STAMP){Paint b=new Paint(Paint.ANTI_ALIAS_FLAG);b.setStyle(Paint.Style.STROKE);b.setStrokeWidth(min*.004f);b.setColor(Color.rgb(185,35,45));float tw=p.measureText(m.text);c.drawRect(x-min*.012f,y-p.getTextSize()-min*.012f,x+tw+min*.012f,y+min*.012f,b);}c.drawText(m.text,x,y,p);}
        }
    }
}
