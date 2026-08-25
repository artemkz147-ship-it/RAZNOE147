package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConvertersActivity extends AppCompatActivity {
    private static final int PICK_ONE=4101,PICK_MANY=4102;
    private static final int BG=Color.rgb(247,248,252),TEXT=Color.rgb(25,28,36),MUTED=Color.rgb(93,99,112),BLUE=Color.rgb(49,87,213),GREEN=Color.rgb(0,150,112);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String action="";

    @Override protected void onCreate(Bundle b){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);build();}

    private void build(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(0,s.top,0,s.bottom);return i;});LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(10),dp(16),dp(28));scroll.addView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("←",30,TEXT,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(text("Конвертеры",28,TEXT,true));names.addView(text("Форматы документов, PDF, таблиц и изображений",14,MUTED,false));top.addView(names,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));root.addView(top);
        TextView local=text("✓ Конвертация выполняется на устройстве",13,GREEN,true);local.setPadding(dp(12),dp(9),dp(12),dp(9));local.setBackgroundColor(Color.rgb(233,249,244));root.addView(local);

        root.addView(section("PDF"));
        root.addView(tool("PDF → DOCX","Редактируемый текст с разрывами страниц",v->pick(false,"application/pdf","pdf_docx")));
        root.addView(tool("PDF → XLSX","Эвристическое разбиение строк и колонок",v->pick(false,"application/pdf","pdf_xlsx")));
        root.addView(tool("PDF → PPTX","Каждая страница как визуальный слайд",v->pick(false,"application/pdf","pdf_pptx")));
        root.addView(tool("PDF → JPG","Все страницы в изображения",v->pick(false,"application/pdf","pdf_jpg")));
        root.addView(tool("Несколько PDF → JPG","Пакетный экспорт всех страниц",v->pick(true,"application/pdf","batch_pdf_jpg")));
        root.addView(tool("PDF → TXT","Извлечь встроенный текст",v->pick(false,"application/pdf","pdf_text")));
        root.addView(tool("Изображения → PDF","Несколько изображений в один PDF",v->pick(true,"image/*","images_pdf")));

        root.addView(section("Документы"));
        root.addView(tool("DOCX → TXT","Извлечь текст Word",v->pick(false,"application/vnd.openxmlformats-officedocument.wordprocessingml.document","docx_txt")));
        root.addView(tool("DOCX → PDF","Текстовый экспорт",v->pick(false,"application/vnd.openxmlformats-officedocument.wordprocessingml.document","docx_pdf")));
        root.addView(tool("ODT → TXT","Извлечь текст",v->pick(false,"application/vnd.oasis.opendocument.text","odt_txt")));
        root.addView(tool("ODT → PDF","Текстовый экспорт",v->pick(false,"application/vnd.oasis.opendocument.text","odt_pdf")));
        root.addView(tool("TXT / HTML / Markdown / RTF → PDF","Создать PDF",v->pick(false,"*/*","text_pdf")));
        root.addView(tool("HTML / Markdown / RTF → TXT","Очистить разметку",v->pick(false,"*/*","text_txt")));
        root.addView(tool("TXT / Markdown / HTML → DOCX","Создать DOCX",v->pick(false,"*/*","text_docx")));
        root.addView(tool("TXT / Markdown / HTML → ODT","Создать ODT",v->pick(false,"*/*","text_odt")));

        root.addView(section("Таблицы"));
        root.addView(tool("XLSX → CSV","Первый лист",v->pick(false,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","xlsx_csv")));
        root.addView(tool("Все листы XLSX → CSV","Каждый лист отдельным CSV",v->pick(false,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","xlsx_all_csv")));
        root.addView(tool("XLSX → PDF","Первый лист",v->pick(false,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","xlsx_pdf")));
        root.addView(tool("Все листы XLSX → PDF","Один PDF со всеми листами",v->pick(false,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","xlsx_all_pdf")));
        root.addView(tool("CSV / TSV → XLSX","Формулы с = сохраняются как формулы",v->pick(false,"*/*","csv_xlsx")));

        root.addView(section("Презентации"));
        root.addView(tool("PPTX → TXT","Извлечь текст по слайдам",v->pick(false,"application/vnd.openxmlformats-officedocument.presentationml.presentation","pptx_txt")));
        root.addView(tool("PPTX → PDF","Текстовый PDF",v->pick(false,"application/vnd.openxmlformats-officedocument.presentationml.presentation","pptx_pdf")));

        root.addView(section("Изображения"));
        root.addView(tool("JPG / PNG / WebP","Перевести изображение в другой формат",v->pick(false,"image/*","image_convert")));
        root.addView(tool("Пакетно JPG / PNG / WebP","Сразу несколько изображений",v->pick(true,"image/*","image_batch_convert")));
        root.addView(tool("SVG → PNG","Растеризация SVG",v->pick(false,"*/*","svg_png")));
        root.addView(tool("SVG → JPG","Растеризация на белом фоне",v->pick(false,"*/*","svg_jpg")));
        root.addView(tool("TIFF → PNG","Первая страница TIFF",v->pick(false,"*/*","tiff_png")));
        root.addView(tool("Все страницы TIFF → PNG","Multi-page TIFF в отдельные PNG",v->pick(false,"*/*","tiff_all_png")));
        root.addView(tool("HEIC / GIF / AVIF / другое → JPG/PNG/WebP","Используется системный декодер Android",v->pick(false,"*/*","system_image_convert")));
        setContentView(scroll);}

    private void pick(boolean multi,String mime,String next){action=next;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multi);startActivityForResult(i,multi?PICK_MANY:PICK_ONE);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null)return;List<Uri> uris=collect(data);if(uris.isEmpty())return;Uri first=uris.get(0);try{switch(action){
        case"pdf_docx"->runTask("Создаю DOCX…",()->PdfOfficeTools.toDocx(this,first),"DOCX сохранён");
        case"pdf_xlsx"->runTask("Распознаю строки и колонки…",()->PdfOfficeTools.toXlsx(this,first),"XLSX сохранён");
        case"pdf_pptx"->runTask("Создаю PPTX…",()->PdfOfficeTools.toPptxVisual(this,first),"PPTX сохранён");
        case"pdf_jpg"->runTask("Экспортирую JPG…",()->PdfTools.pdfToJpeg(this,first),"JPG сохранены");
        case"batch_pdf_jpg"->runTask("Экспортирую PDF в JPG…",()->BatchPdfTools.toJpg(this,uris),"Страницы экспортированы");
        case"pdf_text"->runTask("Извлекаю текст…",()->PdfTools.extractText(this,first),"TXT сохранён");
        case"images_pdf"->runTask("Создаю PDF…",()->PdfTools.imagesToPdf(this,uris),"PDF создан");
        case"docx_txt"->runTask("Извлекаю DOCX…",()->DocxTools.toTxt(this,first),"TXT сохранён");
        case"docx_pdf"->runTask("Создаю PDF…",()->DocxTools.toPdf(this,first),"PDF сохранён");
        case"odt_txt"->runTask("Извлекаю ODT…",()->OpenDocumentTools.odtToTxt(this,first),"TXT сохранён");
        case"odt_pdf"->runTask("Создаю PDF…",()->OpenDocumentTools.odtToPdf(this,first),"PDF сохранён");
        case"text_pdf"->runTask("Создаю PDF…",()->TextTools.toPdf(this,first),"PDF сохранён");
        case"text_txt"->runTask("Извлекаю текст…",()->TextTools.toTxt(this,first),"TXT сохранён");
        case"text_docx"->runTask("Создаю DOCX…",()->OfficeCreateTools.textToDocx(this,first),"DOCX сохранён");
        case"text_odt"->runTask("Создаю ODT…",()->OfficeCreateTools.textToOdt(this,first),"ODT сохранён");
        case"xlsx_csv"->runTask("Экспортирую XLSX…",()->SheetTools.xlsxToCsv(this,first),"CSV сохранён");
        case"xlsx_all_csv"->runTask("Экспортирую листы…",()->SheetExtraTools.allSheetsToCsv(this,first),"CSV сохранены");
        case"xlsx_pdf"->runTask("Создаю PDF…",()->SheetTools.xlsxToPdf(this,first),"PDF сохранён");
        case"xlsx_all_pdf"->runTask("Создаю PDF всех листов…",()->SheetExtraTools.allSheetsToPdf(this,first),"PDF сохранён");
        case"csv_xlsx"->runTask("Создаю XLSX…",()->SheetTools.csvToXlsx(this,first),"XLSX сохранён");
        case"pptx_txt"->runTask("Извлекаю PPTX…",()->PresentationTools.toTxt(this,first),"TXT сохранён");
        case"pptx_pdf"->runTask("Создаю PDF…",()->PresentationTools.toPdf(this,first),"PDF сохранён");
        case"image_convert"->chooseImageFormat(false,uris);
        case"image_batch_convert"->chooseImageFormat(true,uris);
        case"svg_png"->runTask("Растеризую SVG…",()->ExtendedImageTools.svgToPng(this,first),"PNG сохранён");
        case"svg_jpg"->runTask("Растеризую SVG…",()->ExtendedImageTools.svgToJpg(this,first),"JPG сохранён");
        case"tiff_png"->runTask("Декодирую TIFF…",()->ExtendedImageTools.tiffFirstToPng(this,first),"PNG сохранён");
        case"tiff_all_png"->runTask("Декодирую TIFF…",()->ExtendedImageTools.tiffAllPagesToPng(this,first),"Страницы TIFF сохранены");
        case"system_image_convert"->chooseSystemImageFormat(first);
    }}catch(Exception e){error(e);}}

    private void chooseImageFormat(boolean batch,List<Uri> u){String[] labels={"JPG","PNG","WebP"},formats={"jpg","png","webp"};new AlertDialog.Builder(this).setTitle("Выходной формат").setItems(labels,(d,w)->{if(batch)runTask("Конвертирую…",()->ImageTools.convertBatch(this,u,formats[w]),"Изображения конвертированы");else runTask("Конвертирую…",()->ImageTools.convert(this,u.get(0),formats[w]),"Изображение конвертировано");}).show();}
    private void chooseSystemImageFormat(Uri u){String[] labels={"JPG","PNG","WebP"},formats={"jpg","png","webp"};new AlertDialog.Builder(this).setTitle("Формат результата").setItems(labels,(d,w)->runTask("Декодирую системным кодеком…",()->ExtendedImageTools.systemDecodeTo(this,u,formats[w]),"Изображение сохранено")).show();}
    private interface Work{Object run()throws Exception;}
    private void runTask(String label,Work work,String success){ProgressDialog d=ProgressDialog.show(this,"Доки",label,true,false);worker.submit(()->{try{Object result=work.run();runOnUiThread(()->{d.dismiss();if(result instanceof Uri)ResultDialogs.show(this,success,(Uri)result);else Toast.makeText(this,success+(result instanceof Integer?" ("+result+")":""),Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->{d.dismiss();error(e);});}});}
    private List<Uri> collect(Intent data){List<Uri> out=new ArrayList<>();ClipData c=data.getClipData();if(c!=null)for(int i=0;i<c.getItemCount();i++)out.add(c.getItemAt(i).getUri());else if(data.getData()!=null)out.add(data.getData());return out;}
    private View section(String title){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,dp(18),0,dp(9));box.addView(text(title,21,TEXT,true));return box;}
    private View tool(String title,String sub,View.OnClickListener click){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.HORIZONTAL);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(16),dp(14),dp(12),dp(14));box.setBackgroundColor(Color.WHITE);box.setOnClickListener(click);LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(title,16,TEXT,true));copy.addView(text(sub,13,MUTED,false));box.addView(copy,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));TextView arrow=text("›",28,Color.rgb(166,171,183),false);arrow.setGravity(Gravity.CENTER);box.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(48)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,0,dp(8));box.setLayoutParams(lp);return box;}
    private TextView text(String value,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private void error(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()).setPositiveButton("OK",null).show();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();}
}
