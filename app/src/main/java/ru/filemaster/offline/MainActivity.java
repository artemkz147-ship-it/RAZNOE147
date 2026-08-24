package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_FILES = 1001;
    private static final int PICK_ONE = 1002;
    private static final int DRAW_SIGNATURE = 1003;
    private static final int CAMERA_CAPTURE = 1004;

    private static final int BG = Color.rgb(247, 248, 252);
    private static final int TEXT = Color.rgb(25, 28, 36);
    private static final int MUTED = Color.rgb(93, 99, 112);
    private static final int BLUE = Color.rgb(49, 87, 213);
    private static final int GREEN = Color.rgb(0, 150, 112);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String action = "";
    private Uri pendingPdf;
    private Uri pendingCryptoPdf;
    private Uri cameraUri;
    private boolean categoryOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        renderHome();
        Intent incoming = getIntent();
        if (Intent.ACTION_VIEW.equals(incoming.getAction()) && incoming.getData() != null) {
            pendingPdf = incoming.getData();
            showPdfQuickActions(pendingPdf);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!categoryOpen && getWindow().getDecorView().isAttachedToWindow()) renderHome();
    }

    private void renderHome() {
        categoryOpen = false;
        ScrollView scroll = baseScroll();
        LinearLayout root = contentRoot();
        scroll.addView(root);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(gradient(new int[]{Color.rgb(42, 76, 194), Color.rgb(91, 111, 232)}, dp(22)));
        hero.setElevation(dp(2));
        root.addView(hero, marginParams(0, 0, 0, 18));
        hero.addView(text("ФайлМастер", 30, Color.WHITE, true));
        TextView tagline = text("PDF, документы, фото, подписи и архивы — без сервера", 15, Color.rgb(234, 238, 255), false);
        tagline.setPadding(0, dp(5), 0, dp(14));
        hero.addView(tagline);
        TextView offline = text("✓  Работает офлайн   •   файлы никуда не отправляются", 13, Color.WHITE, true);
        offline.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable offlineBg = rounded(Color.argb(42, 255, 255, 255), dp(12));
        offlineBg.setStroke(dp(1), Color.argb(70, 255, 255, 255));
        offline.setBackground(offlineBg);
        hero.addView(offline);

        root.addView(section("Быстрые действия", "Самое нужное — в один тап"));
        root.addView(quickRow(
                quick("Сканировать", "Несколько страниц → PDF", v -> startActivity(new Intent(this, ScanSessionActivity.class))),
                quick("Объединить PDF", "Несколько → один", v -> pick(true, "application/pdf", "merge_pdf"))
        ));
        root.addView(quickRow(
                quick("Подписать PDF", "Пальцем", v -> pick(false, "application/pdf", "sign_pdf")),
                quick("Создать ZIP", "Несколько файлов", v -> pick(true, "*/*", "zip"))
        ));
        root.addView(quickRow(
                quick("Рисовать PDF", "Ручка и маркер", v -> pick(false, "application/pdf", "annotate_pdf")),
                quick("Мои файлы", "Все результаты", v -> startActivity(new Intent(this, ResultsActivity.class)))
        ));

        addRecent(root);

        root.addView(section("Все инструменты", "Разложено по понятным категориям"));
        root.addView(category("PDF", "PDF", "Редактор, формы, страницы и защита", "22 инструмента", Color.rgb(235, 239, 255), BLUE, v -> renderCategory("pdf")));
        root.addView(category("SCAN", "Сканер и OCR", "Камера и распознавание текста", "5 инструментов", Color.rgb(231, 249, 244), GREEN, v -> renderCategory("scan")));
        root.addView(category("SIGN", "Подписи PDF", "Рукописная и электронная подпись", "3 инструмента", Color.rgb(255, 244, 227), Color.rgb(199, 116, 0), v -> renderCategory("sign")));
        root.addView(category("DOC", "Документы и текст", "Word, ODT, TXT, HTML, Markdown", "8 инструментов", Color.rgb(236, 245, 255), Color.rgb(28, 105, 184), v -> renderCategory("docs")));
        root.addView(category("XLS", "Таблицы", "XLSX, CSV и TSV", "3 инструмента", Color.rgb(232, 248, 238), Color.rgb(36, 132, 72), v -> renderCategory("sheets")));
        root.addView(category("PPT", "Презентации", "PPTX → TXT/PDF", "2 инструмента", Color.rgb(255, 239, 231), Color.rgb(204, 83, 26), v -> renderCategory("slides")));
        root.addView(category("IMG", "Изображения", "Crop, flip, convert и batch", "9 инструментов", Color.rgb(245, 237, 255), Color.rgb(126, 68, 187), v -> renderCategory("images")));
        root.addView(category("ZIP", "Архиватор", "ZIP, 7Z, RAR, TAR и пароли", "8 инструментов", Color.rgb(241, 243, 246), Color.rgb(82, 91, 107), v -> renderCategory("archives")));

        TextView allFiles = text("Открыть все файлы ФайлМастер", 14, BLUE, true);
        allFiles.setGravity(Gravity.CENTER);
        allFiles.setPadding(dp(10), dp(14), dp(10), dp(8));
        allFiles.setOnClickListener(v -> startActivity(new Intent(this, ResultsActivity.class)));
        root.addView(allFiles);

        TextView where = text("Результаты: «Загрузки / ФайлМастер»", 13, MUTED, false);
        where.setGravity(Gravity.CENTER);
        where.setPadding(dp(8), dp(6), dp(8), dp(8));
        root.addView(where);
        TextView about = text("О приложении и приватности", 14, BLUE, true);
        about.setGravity(Gravity.CENTER);
        about.setPadding(dp(10), dp(12), dp(10), dp(18));
        about.setOnClickListener(v -> showAbout());
        root.addView(about);
        setContentView(scroll);
    }

    private void addRecent(LinearLayout root) {
        List<RecentStore.Entry> recent = RecentStore.list(this);
        if (recent.isEmpty()) return;
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView((View) section("Недавние файлы", "Последние результаты приложения"), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = text("Очистить", 13, BLUE, true);
        clear.setPadding(dp(8), dp(14), 0, dp(8));
        clear.setOnClickListener(v -> {
            RecentStore.clear(this);
            renderHome();
        });
        titleRow.addView(clear);
        root.addView(titleRow);
        int max = Math.min(4, recent.size());
        for (int i = 0; i < max; i++) root.addView(recentRow(recent.get(i)));
    }

    private View recentRow(RecentStore.Entry entry) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), dp(12), dp(12), dp(12));
        GradientDrawable bg = rounded(Color.WHITE, dp(15));
        bg.setStroke(dp(1), Color.rgb(231, 233, 240));
        box.setBackground(bg);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(entry.name.isBlank() ? "Файл" : entry.name, 15, TEXT, true));
        TextView sub = text(entry.mime.isBlank() ? "Результат" : entry.mime, 12, MUTED, false);
        sub.setPadding(0, dp(2), 0, 0);
        copy.addView(sub);
        box.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView menu = text("⋮", 26, BLUE, true);
        menu.setGravity(Gravity.CENTER);
        box.addView(menu, new LinearLayout.LayoutParams(dp(42), dp(46)));
        box.setOnClickListener(v -> showRecentActions(entry));
        box.setLayoutParams(marginParams(0, 0, 0, 8));
        return box;
    }

    private void showRecentActions(RecentStore.Entry entry) {
        new AlertDialog.Builder(this).setTitle(entry.name)
                .setItems(new String[]{"Открыть", "Поделиться"}, (d, which) -> {
                    if (which == 0) openResult(entry.uri); else shareResult(entry.uri);
                }).setNegativeButton("Закрыть", null).show();
    }

    private void renderCategory(String id) {
        categoryOpen = true;
        ScrollView scroll = baseScroll();
        LinearLayout root = contentRoot();
        scroll.addView(root);
        String title;
        String subtitle;
        switch (id) {
            case "scan" -> { title = "Сканер и OCR"; subtitle = "Сканирование и распознавание на устройстве"; }
            case "sign" -> { title = "Подписи PDF"; subtitle = "Рукописная и электронная подпись"; }
            case "docs" -> { title = "Документы и текст"; subtitle = "Word, ODT, TXT, HTML, Markdown и RTF"; }
            case "sheets" -> { title = "Таблицы"; subtitle = "XLSX, CSV и TSV"; }
            case "slides" -> { title = "Презентации"; subtitle = "Извлечение текста и экспорт PPTX"; }
            case "images" -> { title = "Изображения"; subtitle = "Сжатие, crop, flip, convert и batch"; }
            case "archives" -> { title = "Архиватор"; subtitle = "Создание, просмотр и распаковка архивов"; }
            default -> { title = "PDF"; subtitle = "Разметка, формы, страницы, crop и защита"; }
        }
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, dp(4), 0, dp(10));
        TextView back = text("←", 30, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> renderHome());
        top.addView(back, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(title, 27, TEXT, true));
        TextView sub = text(subtitle, 14, MUTED, false);
        sub.setPadding(0, dp(2), 0, 0);
        names.addView(sub);
        top.addView(names, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top);
        TextView local = text("✓  Без загрузки файлов в интернет", 13, GREEN, true);
        local.setPadding(dp(12), dp(9), dp(12), dp(9));
        local.setBackground(rounded(Color.rgb(233, 249, 244), dp(12)));
        root.addView(local, marginParams(0, 0, 0, 16));
        switch (id) {
            case "scan" -> addScanTools(root);
            case "sign" -> addSignTools(root);
            case "docs" -> addDocumentTools(root);
            case "sheets" -> addSheetTools(root);
            case "slides" -> addPresentationTools(root);
            case "images" -> addImageTools(root);
            case "archives" -> addArchiveTools(root);
            default -> addPdfTools(root);
        }
        setContentView(scroll);
    }

    private void addScanTools(LinearLayout root) {
        root.addView(tool("Многостраничный скан", "Снять несколько страниц и собрать один PDF", v -> startActivity(new Intent(this, ScanSessionActivity.class))));
        root.addView(tool("Быстрый скан одной страницы", "Фото документа → улучшение → JPG + PDF", v -> captureScan()));
        root.addView(tool("Улучшить готовое фото документа", "Автообрезка полей, Ч/Б и контраст", v -> pick(false, "image/*", "scan_photo")));
        root.addView(tool("OCR фото RU + EN", "Фото → TXT, модели внутри APK", v -> pick(false, "image/*", "ocr")));
        root.addView(tool("OCR целого PDF RU + EN", "Распознать каждую страницу PDF", v -> pick(false, "application/pdf", "ocr_pdf")));
    }

    private void addPdfTools(LinearLayout root) {
        root.addView(section("Визуально и формы", null));
        root.addView(tool("Ручка / маркер по PDF", "Выберите страницу и рисуйте пальцем", v -> pick(false, "application/pdf", "annotate_pdf")));
        root.addView(tool("Надёжно скрыть область", "Выделить прямоугольники и уничтожить данные под ними", v -> pick(false, "application/pdf", "redact_pdf")));
        root.addView(tool("Заполнить PDF-форму", "Редактировать существующие текстовые поля AcroForm", v -> pick(false, "application/pdf", "fill_pdf_form")));
        root.addView(section("Страницы и конвертация", null));
        root.addView(tool("Объединить PDF", "Склеить несколько PDF", v -> pick(true, "application/pdf", "merge_pdf")));
        root.addView(tool("Разделить PDF", "Каждая страница — отдельный PDF", v -> pick(false, "application/pdf", "split_pdf")));
        root.addView(tool("Извлечь страницы", "Например: 1,3,5-8", v -> pick(false, "application/pdf", "extract_pages")));
        root.addView(tool("Удалить страницы", "Удалить номера или диапазоны", v -> pick(false, "application/pdf", "remove_pages")));
        root.addView(tool("Повернуть PDF на 90°", "Повернуть все страницы", v -> pick(false, "application/pdf", "rotate_pdf")));
        root.addView(tool("Обратный порядок страниц", "Последняя станет первой", v -> pick(false, "application/pdf", "reverse_pdf")));
        root.addView(tool("Добавить номера страниц", "По центру снизу", v -> pick(false, "application/pdf", "page_numbers")));
        root.addView(tool("Обрезать поля PDF", "Убрать одинаковый процент со всех краёв", v -> pick(false, "application/pdf", "crop_pdf")));
        root.addView(tool("Извлечь встроенные изображения", "Сохранить картинки из PDF как PNG", v -> pick(false, "application/pdf", "extract_pdf_images")));
        root.addView(tool("Фото → PDF", "Несколько JPG/PNG в один документ", v -> pick(true, "image/*", "images_pdf")));
        root.addView(tool("PDF → JPG", "Экспорт всех страниц", v -> pick(false, "application/pdf", "pdf_jpg")));
        root.addView(tool("PDF → TXT", "Извлечь встроенный текст", v -> pick(false, "application/pdf", "pdf_text")));
        root.addView(section("Обработка и защита", null));
        root.addView(tool("Сжать PDF", "Три уровня сжатия", v -> pick(false, "application/pdf", "compress_pdf")));
        root.addView(tool("Пересобрать / восстановить PDF", "Повторно сохранить структуру", v -> pick(false, "application/pdf", "repair_pdf")));
        root.addView(tool("Водяной знак", "Текст на каждой странице", v -> pick(false, "application/pdf", "watermark_pdf")));
        root.addView(tool("Очистить метаданные", "Удалить свойства и XMP", v -> pick(false, "application/pdf", "clean_metadata")));
        root.addView(tool("Сравнить два PDF", "Текстовый отчёт различий", v -> pick(true, "application/pdf", "compare_pdf")));
        root.addView(tool("Защитить PDF паролем", "Локальное шифрование", v -> pick(false, "application/pdf", "protect_pdf")));
        root.addView(tool("Снять известный пароль", "Нужен действующий пароль", v -> pick(false, "application/pdf", "unlock_pdf")));
    }

    private void addSignTools(LinearLayout root) {
        root.addView(tool("Рукописная подпись", "Нарисовать пальцем и встроить в PDF", v -> pick(false, "application/pdf", "sign_pdf")));
        root.addView(tool("Электронная подпись PKCS#12", ".p12/.pfx + пароль → CMS/PKCS#7", v -> pick(false, "application/pdf", "crypto_sign_pdf")));
        root.addView(tool("Проверить электронные подписи", "Локальная криптографическая проверка", v -> pick(false, "application/pdf", "verify_signatures")));
    }

    private void addDocumentTools(LinearLayout root) {
        root.addView(tool("DOCX → TXT", "Извлечь текст Word", v -> pick(false, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx_txt")));
        root.addView(tool("DOCX → PDF", "Текстовый экспорт; сложная верстка упрощается", v -> pick(false, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx_pdf")));
        root.addView(tool("ODT → TXT", "Извлечь текст OpenDocument", v -> pick(false, "application/vnd.oasis.opendocument.text", "odt_txt")));
        root.addView(tool("ODT → PDF", "Локальный текстовый экспорт", v -> pick(false, "application/vnd.oasis.opendocument.text", "odt_pdf")));
        root.addView(tool("TXT / HTML / Markdown / RTF → PDF", "Создать PDF из текста", v -> pick(false, "*/*", "text_pdf")));
        root.addView(tool("HTML / Markdown / RTF → TXT", "Очистить разметку", v -> pick(false, "*/*", "text_txt")));
        root.addView(tool("TXT / Markdown / HTML → DOCX", "Создать Word-документ", v -> pick(false, "*/*", "text_docx")));
        root.addView(tool("TXT / Markdown / HTML → ODT", "Создать OpenDocument Text", v -> pick(false, "*/*", "text_odt")));
    }

    private void addPresentationTools(LinearLayout root) {
        root.addView(tool("PPTX → TXT", "Извлечь текст по слайдам", v -> pick(false, "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx_txt")));
        root.addView(tool("PPTX → PDF", "Текст слайдов → PDF", v -> pick(false, "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx_pdf")));
    }

    private void addSheetTools(LinearLayout root) {
        root.addView(tool("XLSX → CSV", "Первый лист → UTF-8 CSV", v -> pick(false, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx_csv")));
        root.addView(tool("CSV / TSV → XLSX", "Автоопределение разделителя", v -> pick(false, "*/*", "csv_xlsx")));
        root.addView(tool("XLSX → PDF", "Первый лист → PDF", v -> pick(false, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx_pdf")));
    }

    private void addImageTools(LinearLayout root) {
        root.addView(section("Один файл", null));
        root.addView(tool("Сжать изображение", "JPEG 78%", v -> pick(false, "image/*", "image_compress")));
        root.addView(tool("Изменить размер", "Указать максимальную сторону", v -> pick(false, "image/*", "image_resize")));
        root.addView(tool("Обрезать края", "Убрать одинаковый процент со всех сторон", v -> pick(false, "image/*", "image_crop")));
        root.addView(tool("Повернуть на 90°", "Сохранить новую копию", v -> pick(false, "image/*", "image_rotate")));
        root.addView(tool("Отразить по горизонтали", "Зеркальная копия", v -> pick(false, "image/*", "image_flip")));
        root.addView(tool("Сделать Ч/Б", "Оттенки серого", v -> pick(false, "image/*", "image_gray")));
        root.addView(tool("Конвертировать JPG / PNG / WebP", "Выбрать выходной формат", v -> pick(false, "image/*", "image_convert")));
        root.addView(section("Пакетно", null));
        root.addView(tool("Сжать несколько изображений", "Обработать выбранные файлы подряд", v -> pick(true, "image/*", "image_batch_compress")));
        root.addView(tool("Конвертировать несколько", "JPG / PNG / WebP", v -> pick(true, "image/*", "image_batch_convert")));
    }

    private void addArchiveTools(LinearLayout root) {
        root.addView(tool("Посмотреть содержимое ZIP / 7Z / RAR", "Список файлов без распаковки", v -> pick(false, "*/*", "archive_list")));
        root.addView(section("Создать архив", null));
        root.addView(tool("Создать ZIP", "Упаковать несколько файлов", v -> pick(true, "*/*", "zip")));
        root.addView(tool("ZIP с паролем AES-256", "Защищённый архив", v -> pick(true, "*/*", "zip_password")));
        root.addView(tool("Создать 7Z", "Локальное LZMA2-сжатие", v -> pick(true, "*/*", "7z")));
        root.addView(tool("Создать TAR.GZ", "Совместимый gzip-архив", v -> pick(true, "*/*", "targz")));
        root.addView(section("Распаковать", null));
        root.addView(tool("Распаковать ZIP / 7Z / RAR", "В Загрузки/ФайлМастер", v -> pick(false, "*/*", "extract")));
        root.addView(tool("Распаковать ZIP с паролем", "AES/ZipCrypto при известном пароле", v -> pick(false, "application/zip", "extract_zip_password")));
        root.addView(tool("Распаковать TAR / GZ / BZ2 / XZ", "Также TAR.GZ, TAR.BZ2 и TAR.XZ", v -> pick(false, "*/*", "extract_extra")));
    }

    private ScrollView baseScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(0, safe.top, 0, safe.bottom);
            return insets;
        });
        return scroll;
    }

    private LinearLayout contentRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(28));
        return root;
    }

    private View quickRow(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMargins(0, 0, dp(5), dp(10));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp2.setMargins(dp(5), 0, 0, dp(10));
        row.addView(left, lp1);
        row.addView(right, lp2);
        return row;
    }

    private View quick(String title, String subtitle, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setMinimumHeight(dp(86));
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = rounded(Color.WHITE, dp(17));
        bg.setStroke(dp(1), Color.rgb(230, 232, 239));
        box.setBackground(bg);
        box.setOnClickListener(listener);
        box.addView(text(title, 16, TEXT, true));
        TextView s = text(subtitle, 13, MUTED, false);
        s.setPadding(0, dp(3), 0, 0);
        box.addView(s);
        return box;
    }

    private View category(String badge, String title, String subtitle, String count, int tint, int accent, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = rounded(Color.WHITE, dp(17));
        bg.setStroke(dp(1), Color.rgb(231, 233, 240));
        box.setBackground(bg);
        box.setOnClickListener(listener);
        TextView icon = text(badge, badge.length() > 3 ? 11 : 12, accent, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(tint, dp(14)));
        box.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(13), 0, dp(8), 0);
        names.addView(text(title, 17, TEXT, true));
        TextView s = text(subtitle, 13, MUTED, false);
        s.setPadding(0, dp(2), 0, 0);
        names.addView(s);
        names.addView(text(count, 11, accent, true));
        box.addView(names, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = text("›", 29, Color.rgb(160, 165, 177), false);
        arrow.setGravity(Gravity.CENTER);
        box.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(54)));
        box.setLayoutParams(marginParams(0, 0, 0, 10));
        return box;
    }

    private void captureScan() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "Скан_исходник_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ФайлМастер/Сканы");
            cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (cameraUri == null) throw new IllegalStateException("Не удалось подготовить файл для камеры");
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) == null) throw new IllegalStateException("На устройстве не найдено приложение камеры");
            startActivityForResult(intent, CAMERA_CAPTURE);
        } catch (Exception e) { showError(e); }
    }

    private void pick(boolean multi, String mime, String nextAction) {
        action = nextAction;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multi);
        startActivityForResult(i, multi ? PICK_FILES : PICK_ONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_CAPTURE) {
            if (resultCode == RESULT_OK && cameraUri != null) {
                Uri raw = cameraUri;
                runTask("Улучшаю скан и создаю PDF…", () -> {
                    Uri enhanced = ImageTools.enhanceDocument(this, raw);
                    List<Uri> one = new ArrayList<>();
                    one.add(enhanced);
                    PdfTools.imagesToPdf(this, one);
                    try { getContentResolver().delete(raw, null, null); } catch (Exception ignored) {}
                    return enhanced;
                }, "Скан сохранён как JPG и PDF");
            } else if (cameraUri != null) {
                try { getContentResolver().delete(cameraUri, null, null); } catch (Exception ignored) {}
            }
            cameraUri = null;
            return;
        }
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == DRAW_SIGNATURE) {
            String path = data.getStringExtra("signature_path");
            if (path != null && pendingPdf != null) {
                File sig = new File(path);
                runTask("Добавляю подпись…", () -> PdfTools.addVisibleSignature(this, pendingPdf, sig), "Подписанный PDF сохранён");
            }
            return;
        }
        List<Uri> uris = collectUris(data);
        if (uris.isEmpty()) return;
        Uri first = uris.get(0);
        try {
            switch (action) {
                case "annotate_pdf" -> launchPagedPdfActivity(first, PdfAnnotateActivity.class, "Номер страницы для рисования");
                case "redact_pdf" -> launchPagedPdfActivity(first, PdfRedactActivity.class, "Номер страницы для скрытия данных");
                case "fill_pdf_form" -> {
                    Intent form = new Intent(this, PdfFormActivity.class);
                    form.putExtra("pdf_uri", first.toString());
                    startActivity(form);
                }
                case "merge_pdf" -> runTask("Объединяю PDF…", () -> PdfTools.merge(this, uris), "PDF объединён");
                case "split_pdf" -> runTask("Разделяю PDF…", () -> PdfTools.split(this, first), "Страницы сохранены");
                case "extract_pages" -> ask("Какие страницы извлечь?", "Например: 1,3,5-8", false, value -> runTask("Извлекаю страницы…", () -> PdfTools.extractPages(this, first, value), "Новый PDF создан"));
                case "remove_pages" -> ask("Какие страницы удалить?", "Например: 2,4-6", false, value -> runTask("Удаляю страницы…", () -> PdfTools.removePages(this, first, value), "PDF сохранён"));
                case "rotate_pdf" -> runTask("Поворачиваю страницы…", () -> PdfTools.rotateAll(this, first, 90), "PDF повёрнут");
                case "reverse_pdf" -> runTask("Меняю порядок…", () -> PdfTools.reversePages(this, first), "Порядок страниц изменён");
                case "page_numbers" -> runTask("Добавляю номера…", () -> PdfTools.addPageNumbers(this, first), "Номера страниц добавлены");
                case "crop_pdf" -> ask("Обрезать поля на сколько %?", "Например: 5", false, value -> {
                    try {
                        float p = Float.parseFloat(value.trim().replace(',', '.'));
                        runTask("Обрезаю поля PDF…", () -> PdfExtraTools.cropMargins(this, first, p), "PDF обрезан");
                    } catch (NumberFormatException e) { showError(new IllegalArgumentException("Введите число от 1 до 34")); }
                });
                case "extract_pdf_images" -> runTask("Извлекаю изображения…", () -> PdfExtraTools.extractEmbeddedImages(this, first), "Изображения сохранены");
                case "pdf_text" -> runTask("Извлекаю текст…", () -> PdfTools.extractText(this, first), "TXT сохранён");
                case "images_pdf" -> runTask("Создаю PDF…", () -> PdfTools.imagesToPdf(this, uris), "PDF создан");
                case "pdf_jpg" -> runTask("Экспортирую страницы…", () -> PdfTools.pdfToJpeg(this, first), "Страницы сохранены как JPG");
                case "compress_pdf" -> chooseCompression(first);
                case "repair_pdf" -> runTask("Пересобираю PDF…", () -> PdfExtraTools.repair(this, first), "Новый PDF сохранён");
                case "watermark_pdf" -> ask("Текст водяного знака", "Например: КОПИЯ", false, value -> runTask("Добавляю водяной знак…", () -> PdfExtraTools.watermark(this, first, value), "Водяной знак добавлен"));
                case "clean_metadata" -> runTask("Удаляю метаданные…", () -> PdfExtraTools.removeMetadata(this, first), "PDF без метаданных сохранён");
                case "compare_pdf" -> runTask("Сравниваю PDF…", () -> PdfExtraTools.compareText(this, uris), "Отчёт сравнения сохранён");
                case "protect_pdf" -> ask("Пароль для PDF", "Минимум 4 символа", true, value -> runTask("Шифрую PDF…", () -> PdfTools.protect(this, first, value), "Защищённый PDF сохранён"));
                case "unlock_pdf" -> ask("Текущий пароль PDF", "Введите известный пароль", true, value -> runTask("Снимаю защиту…", () -> PdfTools.unlock(this, first, value), "PDF без пароля сохранён"));
                case "sign_pdf" -> {
                    pendingPdf = first;
                    startActivityForResult(new Intent(this, SignatureActivity.class), DRAW_SIGNATURE);
                }
                case "crypto_sign_pdf" -> {
                    pendingCryptoPdf = first;
                    pick(false, "*/*", "crypto_pick_cert");
                }
                case "crypto_pick_cert" -> {
                    Uri pdf = pendingCryptoPdf;
                    if (pdf == null) throw new IllegalStateException("Сначала выберите PDF");
                    ask("Пароль сертификата", "Пароль .p12/.pfx", true, value -> runTask("Создаю электронную подпись…", () -> DigitalSignatureTools.signPdf(this, pdf, first, value), "Электронно подписанный PDF сохранён"));
                }
                case "verify_signatures" -> runTask("Проверяю подписи…", () -> DigitalSignatureTools.verifyPdf(this, first), "Отчёт проверки сохранён");
                case "ocr" -> runOcr(first);
                case "ocr_pdf" -> runTask("Распознаю страницы PDF…", () -> OcrTools.recognizePdfToTxt(this, first), "OCR PDF сохранён в TXT");
                case "scan_photo" -> runTask("Улучшаю документ…", () -> ImageTools.enhanceDocument(this, first), "Улучшенный скан сохранён");
                case "image_compress" -> runTask("Сжимаю изображение…", () -> ImageTools.compress(this, first, 78), "Сжатая копия сохранена");
                case "image_batch_compress" -> runTask("Сжимаю изображения…", () -> ImageTools.compressBatch(this, uris, 78), "Изображения сжаты");
                case "image_rotate" -> runTask("Поворачиваю изображение…", () -> ImageTools.rotate90(this, first), "Повернутая копия сохранена");
                case "image_flip" -> runTask("Отражаю изображение…", () -> ImageTools.flipHorizontal(this, first), "Зеркальная копия сохранена");
                case "image_gray" -> runTask("Преобразую изображение…", () -> ImageTools.grayscale(this, first), "Ч/Б копия сохранена");
                case "image_crop" -> ask("Обрезать края на сколько %?", "Например: 10", false, value -> {
                    try {
                        int p = Integer.parseInt(value.trim());
                        runTask("Обрезаю изображение…", () -> ImageTools.cropMargins(this, first, p), "Обрезанная копия сохранена");
                    } catch (NumberFormatException e) { showError(new IllegalArgumentException("Введите число от 1 до 35")); }
                });
                case "image_resize" -> ask("Максимальная сторона", "Например: 1600", false, value -> {
                    try {
                        int size = Integer.parseInt(value.trim());
                        if (size < 320 || size > 8000) throw new NumberFormatException();
                        runTask("Меняю размер…", () -> ImageTools.resize(this, first, size), "Изображение сохранено");
                    } catch (NumberFormatException e) { showError(new IllegalArgumentException("Введите число от 320 до 8000")); }
                });
                case "image_convert" -> chooseImageFormat(false, uris);
                case "image_batch_convert" -> chooseImageFormat(true, uris);
                case "docx_txt" -> runTask("Извлекаю текст DOCX…", () -> DocxTools.toTxt(this, first), "TXT сохранён");
                case "docx_pdf" -> runTask("Создаю PDF из DOCX…", () -> DocxTools.toPdf(this, first), "PDF сохранён");
                case "odt_txt" -> runTask("Извлекаю текст ODT…", () -> OpenDocumentTools.odtToTxt(this, first), "TXT сохранён");
                case "odt_pdf" -> runTask("Создаю PDF из ODT…", () -> OpenDocumentTools.odtToPdf(this, first), "PDF сохранён");
                case "text_pdf" -> runTask("Создаю PDF…", () -> TextTools.toPdf(this, first), "PDF сохранён");
                case "text_txt" -> runTask("Извлекаю текст…", () -> TextTools.toTxt(this, first), "TXT сохранён");
                case "text_docx" -> runTask("Создаю DOCX…", () -> OfficeCreateTools.textToDocx(this, first), "DOCX сохранён");
                case "text_odt" -> runTask("Создаю ODT…", () -> OfficeCreateTools.textToOdt(this, first), "ODT сохранён");
                case "pptx_txt" -> runTask("Извлекаю текст PPTX…", () -> PresentationTools.toTxt(this, first), "TXT сохранён");
                case "pptx_pdf" -> runTask("Создаю PDF из PPTX…", () -> PresentationTools.toPdf(this, first), "PDF сохранён");
                case "xlsx_csv" -> runTask("Конвертирую XLSX…", () -> SheetTools.xlsxToCsv(this, first), "CSV сохранён");
                case "csv_xlsx" -> runTask("Создаю XLSX…", () -> SheetTools.csvToXlsx(this, first), "XLSX сохранён");
                case "xlsx_pdf" -> runTask("Создаю PDF таблицы…", () -> SheetTools.xlsxToPdf(this, first), "PDF сохранён");
                case "archive_list" -> runTextTask("Читаю архив…", () -> ArchiveTools.listContents(this, first), "Содержимое архива");
                case "zip" -> runTask("Создаю ZIP…", () -> ArchiveTools.createZip(this, uris), "ZIP создан");
                case "zip_password" -> ask("Пароль ZIP", "AES-256, минимум 4 символа", true, value -> runTask("Создаю защищённый ZIP…", () -> ArchiveTools.createEncryptedZip(this, uris, value), "Защищённый ZIP создан"));
                case "7z" -> runTask("Создаю 7Z…", () -> ArchiveTools.create7z(this, uris), "7Z создан");
                case "targz" -> runTask("Создаю TAR.GZ…", () -> ArchiveTools.createTarGz(this, uris), "TAR.GZ создан");
                case "extract" -> runTask("Распаковываю архив…", () -> ArchiveTools.extract(this, first), "Архив распакован");
                case "extract_zip_password" -> ask("Пароль ZIP", "Введите пароль архива", true, value -> runTask("Распаковываю ZIP…", () -> ArchiveTools.extractEncryptedZip(this, first, value), "Архив распакован"));
                case "extract_extra" -> runTask("Распаковываю архив…", () -> ArchiveExtraTools.extract(this, first), "Архив распакован");
            }
        } catch (Exception e) { showError(e); }
    }

    private void launchPagedPdfActivity(Uri uri, Class<?> activityClass, String title) {
        ask(title, "Например: 1", false, value -> {
            try {
                int page = Integer.parseInt(value.trim());
                if (page < 1) throw new NumberFormatException();
                Intent editor = new Intent(this, activityClass);
                editor.putExtra("pdf_uri", uri.toString());
                editor.putExtra("page", page);
                startActivity(editor);
            } catch (NumberFormatException e) { showError(new IllegalArgumentException("Введите номер страницы от 1")); }
        });
    }

    private void chooseImageFormat(boolean batch, List<Uri> uris) {
        String[] labels = {"JPG", "PNG", "WebP"};
        String[] formats = {"jpg", "png", "webp"};
        new AlertDialog.Builder(this).setTitle("Выходной формат")
                .setItems(labels, (d, which) -> {
                    if (batch) runTask("Конвертирую изображения…", () -> ImageTools.convertBatch(this, uris, formats[which]), "Изображения конвертированы");
                    else runTask("Конвертирую изображение…", () -> ImageTools.convert(this, uris.get(0), formats[which]), "Изображение конвертировано");
                }).setNegativeButton("Отмена", null).show();
    }

    private void chooseCompression(Uri uri) {
        String[] modes = {"Сильное — 90 DPI / JPEG 55%", "Среднее — 120 DPI / JPEG 70%", "Бережное — 150 DPI / JPEG 82%"};
        new AlertDialog.Builder(this).setTitle("Уровень сжатия")
                .setItems(modes, (d, which) -> {
                    int dpi = which == 0 ? 90 : which == 1 ? 120 : 150;
                    float quality = which == 0 ? 0.55f : which == 1 ? 0.70f : 0.82f;
                    runTask("Сжимаю PDF…", () -> PdfExtraTools.compressRaster(this, uri, dpi, quality), "Сжатый PDF сохранён");
                }).setNegativeButton("Отмена", null).show();
    }

    private void runOcr(Uri uri) {
        ProgressDialog dialog = ProgressDialog.show(this, "OCR", "Распознаю текст на устройстве…", true, false);
        worker.submit(() -> {
            try {
                String result = OcrTools.recognize(this, uri);
                Uri out = FileStore.publishBytes(this, result.getBytes(StandardCharsets.UTF_8), "OCR_" + System.currentTimeMillis() + ".txt", "text/plain", null);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    new AlertDialog.Builder(this).setTitle("Распознанный текст")
                            .setMessage(result.isBlank() ? "Текст не найден" : result)
                            .setPositiveButton("Готово", null)
                            .setNeutralButton("Копировать", (d, w) -> copyText(result))
                            .setNegativeButton("Открыть TXT", (d, w) -> openResult(out)).show();
                });
            } catch (Exception e) { runOnUiThread(() -> { dialog.dismiss(); showError(e); }); }
        });
    }

    private interface Work { Object run() throws Exception; }
    private interface TextWork { String run() throws Exception; }
    private interface InputCallback { void onValue(String value); }

    private void runTask(String label, Work work, String success) {
        ProgressDialog dialog = ProgressDialog.show(this, "ФайлМастер", label, true, false);
        worker.submit(() -> {
            try {
                Object result = work.run();
                runOnUiThread(() -> {
                    dialog.dismiss();
                    if (result instanceof Uri) showSuccess(success, (Uri) result);
                    else {
                        String suffix = result instanceof Integer ? " (" + result + ")" : "";
                        Toast.makeText(this, success + suffix, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> { dialog.dismiss(); showError(e); }); }
        });
    }

    private void runTextTask(String label, TextWork work, String title) {
        ProgressDialog dialog = ProgressDialog.show(this, "ФайлМастер", label, true, false);
        worker.submit(() -> {
            try {
                String result = work.run();
                runOnUiThread(() -> {
                    dialog.dismiss();
                    new AlertDialog.Builder(this).setTitle(title).setMessage(result)
                            .setPositiveButton("Закрыть", null)
                            .setNeutralButton("Копировать", (d, w) -> copyText(result)).show();
                });
            } catch (Exception e) { runOnUiThread(() -> { dialog.dismiss(); showError(e); }); }
        });
    }

    private void showSuccess(String success, Uri uri) {
        new AlertDialog.Builder(this).setTitle("Готово")
                .setMessage(success + "\n\nФайл находится в «Загрузки / ФайлМастер».")
                .setPositiveButton("Открыть", (d, w) -> openResult(uri))
                .setNeutralButton("Поделиться", (d, w) -> shareResult(uri))
                .setNegativeButton("Закрыть", null).show();
    }

    private void openResult(Uri uri) {
        try {
            String mime = getContentResolver().getType(uri);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime == null ? "*/*" : mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Открыть файл"));
        } catch (Exception e) { Toast.makeText(this, "Файл недоступен или был удалён", Toast.LENGTH_LONG).show(); }
    }

    private void shareResult(Uri uri) {
        try {
            String mime = getContentResolver().getType(uri);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime == null ? "*/*" : mime);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Поделиться файлом"));
        } catch (Exception e) { showError(new IllegalStateException("Не удалось открыть меню отправки")); }
    }

    private void copyText(String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("ФайлМастер", value));
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
    }

    private void ask(String title, String hint, boolean password, InputCallback callback) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setPadding(dp(16), dp(10), dp(16), dp(10));
        if (password) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle(title).setView(edit)
                .setPositiveButton("Продолжить", (d, w) -> {
                    String value = edit.getText().toString();
                    if (value.isBlank()) showError(new IllegalArgumentException("Поле не должно быть пустым"));
                    else callback.onValue(value);
                }).setNegativeButton("Отмена", null).show();
    }

    private List<Uri> collectUris(Intent data) {
        List<Uri> list = new ArrayList<>();
        ClipData clips = data.getClipData();
        if (clips != null) for (int i = 0; i < clips.getItemCount(); i++) list.add(clips.getItemAt(i).getUri());
        else if (data.getData() != null) list.add(data.getData());
        return list;
    }

    private void showPdfQuickActions(Uri uri) {
        new AlertDialog.Builder(this).setTitle("Что сделать с PDF?")
                .setItems(new String[]{"Рисовать / маркер", "Скрыть область", "Заполнить форму", "Подписать пальцем", "Электронная подпись", "Сжать", "Обрезать поля", "Извлечь картинки", "Разделить", "PDF → JPG", "OCR PDF", "Проверить подписи"}, (d, which) -> {
                    pendingPdf = uri;
                    if (which == 0) launchPagedPdfActivity(uri, PdfAnnotateActivity.class, "Номер страницы для рисования");
                    else if (which == 1) launchPagedPdfActivity(uri, PdfRedactActivity.class, "Номер страницы для скрытия данных");
                    else if (which == 2) {
                        Intent form = new Intent(this, PdfFormActivity.class);
                        form.putExtra("pdf_uri", uri.toString());
                        startActivity(form);
                    }
                    else if (which == 3) startActivityForResult(new Intent(this, SignatureActivity.class), DRAW_SIGNATURE);
                    else if (which == 4) { pendingCryptoPdf = uri; pick(false, "*/*", "crypto_pick_cert"); }
                    else if (which == 5) chooseCompression(uri);
                    else if (which == 6) ask("Обрезать поля на сколько %?", "Например: 5", false, value -> {
                        try {
                            float p = Float.parseFloat(value.trim().replace(',', '.'));
                            runTask("Обрезаю поля…", () -> PdfExtraTools.cropMargins(this, uri, p), "PDF обрезан");
                        } catch (Exception e) { showError(new IllegalArgumentException("Введите число от 1 до 34")); }
                    });
                    else if (which == 7) runTask("Извлекаю изображения…", () -> PdfExtraTools.extractEmbeddedImages(this, uri), "Изображения сохранены");
                    else if (which == 8) runTask("Разделяю PDF…", () -> PdfTools.split(this, uri), "Страницы сохранены");
                    else if (which == 9) runTask("Экспортирую страницы…", () -> PdfTools.pdfToJpeg(this, uri), "JPG сохранены");
                    else if (which == 10) runTask("Распознаю PDF…", () -> OcrTools.recognizePdfToTxt(this, uri), "OCR сохранён");
                    else runTask("Проверяю подписи…", () -> DigitalSignatureTools.verifyPdf(this, uri), "Отчёт сохранён");
                }).setNegativeButton("Закрыть", null).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("ФайлМастер 0.7")
                .setMessage("Основные операции выполняются локально. Файлы не загружаются на сервер. OCR русского и английского языков уже находится внутри APK.\n\nPDF: ручка/маркер, разрушительное скрытие областей, заполнение существующих текстовых форм, подписи, страницы, конвертация и защита.\n\nРезультаты: Загрузки / ФайлМастер")
                .setPositiveButton("Понятно", null).show();
    }

    private void showError(Exception e) {
        new AlertDialog.Builder(this).setTitle("Не получилось")
                .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                .setPositiveButton("OK", null).show();
    }

    private View section(String title, String subtitle) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(12), 0, dp(10));
        wrap.addView(text(title, 21, TEXT, true));
        if (subtitle != null && !subtitle.isBlank()) {
            TextView s = text(subtitle, 14, MUTED, false);
            s.setPadding(0, dp(2), 0, 0);
            wrap.addView(s);
        }
        return wrap;
    }

    private View tool(String title, String subtitle, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(16), dp(14), dp(12), dp(14));
        GradientDrawable bg = rounded(Color.WHITE, dp(16));
        bg.setStroke(dp(1), Color.rgb(231, 233, 240));
        box.setBackground(bg);
        box.setOnClickListener(listener);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 16, TEXT, true));
        TextView s = text(subtitle, 13, MUTED, false);
        s.setPadding(0, dp(3), 0, 0);
        copy.addView(s);
        box.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = text("›", 28, Color.rgb(166, 171, 183), false);
        arrow.setGravity(Gravity.CENTER);
        box.addView(arrow, new LinearLayout.LayoutParams(dp(26), dp(48)));
        box.setLayoutParams(marginParams(0, 0, 0, 9));
        return box;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setLineSpacing(0, 1.05f);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable gradient(int[] colors, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        g.setCornerRadius(radius);
        return g;
    }

    private LinearLayout.LayoutParams marginParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return lp;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onBackPressed() {
        if (categoryOpen) renderHome(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) worker.shutdownNow();
    }
}
