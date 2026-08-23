package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String action = "";
    private Uri pendingPdf;
    private Uri pendingCryptoPdf;
    private Uri cameraUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        renderHome();

        Intent incoming = getIntent();
        if (Intent.ACTION_VIEW.equals(incoming.getAction()) && incoming.getData() != null) {
            pendingPdf = incoming.getData();
            showPdfQuickActions(pendingPdf);
        }
    }

    private void renderHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        scroll.addView(root);

        root.addView(text("ФайлМастер", 30, Color.rgb(25, 28, 36), true));
        TextView subtitle = text("PDF • Word • Excel • PowerPoint • ODT • Фото • OCR • Подписи • Архивы", 15, Color.DKGRAY, false);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        TextView offline = text("✓ Полностью офлайн  •  Никаких серверов и докачиваемых пакетов", 14, Color.rgb(0, 128, 96), true);
        offline.setPadding(dp(12), dp(10), dp(12), dp(10));
        offline.setBackground(rounded(Color.rgb(235, 250, 245), dp(12)));
        root.addView(offline);

        root.addView(section("Сканер и OCR"));
        root.addView(tool("Сканировать камерой", "Фото документа → автообрезка/контраст → JPG + PDF", v -> captureScan()));
        root.addView(tool("Улучшить фото документа", "Автообрезка полей, Ч/Б и повышение контраста", v -> pick(false, "image/*", "scan_photo")));
        root.addView(tool("OCR фото RU + EN", "Фото → TXT. Обе модели уже внутри APK", v -> pick(false, "image/*", "ocr")));
        root.addView(tool("OCR целого PDF RU + EN", "Каждая страница рендерится и распознаётся локально", v -> pick(false, "application/pdf", "ocr_pdf")));

        root.addView(section("PDF — страницы и конвертация"));
        root.addView(tool("Объединить PDF", "Склеить несколько PDF в один", v -> pick(true, "application/pdf", "merge_pdf")));
        root.addView(tool("Разделить PDF", "Каждая страница — отдельный PDF", v -> pick(false, "application/pdf", "split_pdf")));
        root.addView(tool("Извлечь страницы", "Например: 1,3,5-8", v -> pick(false, "application/pdf", "extract_pages")));
        root.addView(tool("Удалить страницы", "Удалить выбранные номера/диапазоны", v -> pick(false, "application/pdf", "remove_pages")));
        root.addView(tool("Повернуть PDF на 90°", "Повернуть все страницы", v -> pick(false, "application/pdf", "rotate_pdf")));
        root.addView(tool("Обратный порядок страниц", "Последняя страница станет первой", v -> pick(false, "application/pdf", "reverse_pdf")));
        root.addView(tool("Добавить номера страниц", "Нумерация по центру снизу", v -> pick(false, "application/pdf", "page_numbers")));
        root.addView(tool("Фото → PDF", "Несколько JPG/PNG в один документ", v -> pick(true, "image/*", "images_pdf")));
        root.addView(tool("PDF → JPG", "Экспорт всех страниц в изображения", v -> pick(false, "application/pdf", "pdf_jpg")));
        root.addView(tool("PDF → TXT", "Извлечь встроенный текст без OCR", v -> pick(false, "application/pdf", "pdf_text")));

        root.addView(section("PDF — обработка"));
        root.addView(tool("Сжать PDF", "3 уровня. Для сильного сжатия страницы пересобираются в JPEG", v -> pick(false, "application/pdf", "compress_pdf")));
        root.addView(tool("Пересобрать / восстановить PDF", "Повторно разобрать структуру и сохранить новый файл", v -> pick(false, "application/pdf", "repair_pdf")));
        root.addView(tool("Водяной знак", "Текст на каждой странице, включая кириллицу", v -> pick(false, "application/pdf", "watermark_pdf")));
        root.addView(tool("Очистить метаданные PDF", "Удалить свойства документа и XMP-метаданные", v -> pick(false, "application/pdf", "clean_metadata")));
        root.addView(tool("Сравнить два PDF", "Текстовое сравнение и отчёт о различиях", v -> pick(true, "application/pdf", "compare_pdf")));
        root.addView(tool("Защитить PDF паролем", "Локальное 128-битное шифрование PDF", v -> pick(false, "application/pdf", "protect_pdf")));
        root.addView(tool("Снять известный пароль PDF", "Нужен действующий пароль документа", v -> pick(false, "application/pdf", "unlock_pdf")));

        root.addView(section("Подпись PDF"));
        root.addView(tool("Рукописная подпись", "Нарисовать пальцем и встроить видимую подпись", v -> pick(false, "application/pdf", "sign_pdf")));
        root.addView(tool("Электронная подпись PKCS#12", "PDF + .p12/.pfx + пароль → CMS/PKCS#7 подпись", v -> pick(false, "application/pdf", "crypto_sign_pdf")));
        root.addView(tool("Проверить электронные подписи", "Локальная криптографическая проверка CMS внутри PDF", v -> pick(false, "application/pdf", "verify_signatures")));

        root.addView(section("Документы и текст"));
        root.addView(tool("DOCX → TXT", "Извлечение текста Word полностью офлайн", v -> pick(false, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx_txt")));
        root.addView(tool("DOCX → PDF", "Текстовый экспорт с Unicode; сложная верстка упрощается", v -> pick(false, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx_pdf")));
        root.addView(tool("ODT → TXT", "Извлечь текст OpenDocument", v -> pick(false, "application/vnd.oasis.opendocument.text", "odt_txt")));
        root.addView(tool("ODT → PDF", "Локальный текстовый экспорт OpenDocument", v -> pick(false, "application/vnd.oasis.opendocument.text", "odt_pdf")));
        root.addView(tool("TXT / HTML / Markdown / RTF → PDF", "Локальная печать текста в PDF", v -> pick(false, "*/*", "text_pdf")));
        root.addView(tool("HTML / Markdown / RTF → TXT", "Очистить разметку и сохранить текст", v -> pick(false, "*/*", "text_txt")));
        root.addView(tool("TXT / Markdown / HTML → DOCX", "Создать обычный Word-документ без облака", v -> pick(false, "*/*", "text_docx")));
        root.addView(tool("TXT / Markdown / HTML → ODT", "Создать OpenDocument Text", v -> pick(false, "*/*", "text_odt")));

        root.addView(section("Презентации"));
        root.addView(tool("PPTX → TXT", "Извлечь текст по слайдам", v -> pick(false, "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx_txt")));
        root.addView(tool("PPTX → PDF", "Текст слайдов → PDF; визуальная верстка упрощается", v -> pick(false, "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx_pdf")));

        root.addView(section("Таблицы"));
        root.addView(tool("XLSX → CSV", "Первый лист → UTF-8 CSV", v -> pick(false, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx_csv")));
        root.addView(tool("CSV / TSV → XLSX", "Автоопределение запятой, ; или табуляции", v -> pick(false, "*/*", "csv_xlsx")));
        root.addView(tool("XLSX → PDF", "Первый лист → читаемый текстовый PDF", v -> pick(false, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx_pdf")));

        root.addView(section("Изображения"));
        root.addView(tool("Сжать изображение", "JPEG 78% для быстрой отправки", v -> pick(false, "image/*", "image_compress")));
        root.addView(tool("Изменить размер", "Указать максимальную сторону в пикселях", v -> pick(false, "image/*", "image_resize")));
        root.addView(tool("Повернуть на 90°", "Сохранить новую копию", v -> pick(false, "image/*", "image_rotate")));
        root.addView(tool("Сделать Ч/Б", "Локальная конвертация в оттенки серого", v -> pick(false, "image/*", "image_gray")));

        root.addView(section("Архиватор"));
        root.addView(tool("Создать ZIP", "Упаковать несколько файлов", v -> pick(true, "*/*", "zip")));
        root.addView(tool("ZIP с паролем AES-256", "Защищённый архив без внешних сервисов", v -> pick(true, "*/*", "zip_password")));
        root.addView(tool("Создать 7Z", "Локальное LZMA2-сжатие", v -> pick(true, "*/*", "7z")));
        root.addView(tool("Создать TAR.GZ", "Совместимый gzip-архив", v -> pick(true, "*/*", "targz")));
        root.addView(tool("Распаковать ZIP / 7Z / RAR", "Распаковка в Загрузки/ФайлМастер", v -> pick(false, "*/*", "extract")));
        root.addView(tool("Распаковать ZIP с паролем", "AES/ZipCrypto при известном пароле", v -> pick(false, "application/zip", "extract_zip_password")));
        root.addView(tool("Распаковать TAR / GZ / BZ2 / XZ", "Также TAR.GZ, TAR.BZ2, TAR.XZ и короткие варианты", v -> pick(false, "*/*", "extract_extra")));

        root.addView(section("Следующие улучшения"));
        TextView roadmap = text("Дальше остаются прежде всего визуальный редактор PDF/форм, ручная коррекция четырёх углов скана, полноценное сохранение сложной верстки Office и работа со всеми листами XLSX. Базовые операции уже выполняются полностью на устройстве.", 15, Color.DKGRAY, false);
        roadmap.setPadding(dp(4), 0, dp(4), dp(20));
        root.addView(roadmap);

        setContentView(scroll);
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
                    List<Uri> one = new ArrayList<>(); one.add(enhanced);
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
                case "merge_pdf" -> runTask("Объединяю PDF…", () -> PdfTools.merge(this, uris), "PDF объединён");
                case "split_pdf" -> runTask("Разделяю PDF…", () -> PdfTools.split(this, first), "Страницы сохранены");
                case "extract_pages" -> ask("Какие страницы извлечь?", "Например: 1,3,5-8", false, value -> runTask("Извлекаю страницы…", () -> PdfTools.extractPages(this, first, value), "Новый PDF создан"));
                case "remove_pages" -> ask("Какие страницы удалить?", "Например: 2,4-6", false, value -> runTask("Удаляю страницы…", () -> PdfTools.removePages(this, first, value), "PDF сохранён"));
                case "rotate_pdf" -> runTask("Поворачиваю страницы…", () -> PdfTools.rotateAll(this, first, 90), "PDF повёрнут");
                case "reverse_pdf" -> runTask("Меняю порядок…", () -> PdfTools.reversePages(this, first), "Порядок страниц изменён");
                case "page_numbers" -> runTask("Добавляю номера…", () -> PdfTools.addPageNumbers(this, first), "Номера страниц добавлены");
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
                case "sign_pdf" -> { pendingPdf = first; startActivityForResult(new Intent(this, SignatureActivity.class), DRAW_SIGNATURE); }
                case "crypto_sign_pdf" -> { pendingCryptoPdf = first; pick(false, "*/*", "crypto_pick_cert"); }
                case "crypto_pick_cert" -> {
                    Uri pdf = pendingCryptoPdf;
                    Uri cert = first;
                    if (pdf == null) throw new IllegalStateException("Сначала выберите PDF");
                    ask("Пароль сертификата", "Пароль .p12/.pfx", true, value -> runTask("Создаю электронную подпись…", () -> DigitalSignatureTools.signPdf(this, pdf, cert, value), "Электронно подписанный PDF сохранён"));
                }
                case "verify_signatures" -> runTask("Проверяю электронные подписи…", () -> DigitalSignatureTools.verifyPdf(this, first), "Отчёт проверки сохранён");
                case "ocr" -> runOcr(first);
                case "ocr_pdf" -> runTask("Распознаю страницы PDF…", () -> OcrTools.recognizePdfToTxt(this, first), "OCR PDF сохранён в TXT");
                case "scan_photo" -> runTask("Улучшаю документ…", () -> ImageTools.enhanceDocument(this, first), "Улучшенный скан сохранён");
                case "image_compress" -> runTask("Сжимаю изображение…", () -> ImageTools.compress(this, first, 78), "Сжатая копия сохранена");
                case "image_rotate" -> runTask("Поворачиваю изображение…", () -> ImageTools.rotate90(this, first), "Повернутая копия сохранена");
                case "image_gray" -> runTask("Преобразую изображение…", () -> ImageTools.grayscale(this, first), "Ч/Б копия сохранена");
                case "image_resize" -> ask("Максимальная сторона", "Например: 1600", false, value -> {
                    try {
                        int size = Integer.parseInt(value.trim());
                        if (size < 320 || size > 8000) throw new NumberFormatException();
                        runTask("Меняю размер…", () -> ImageTools.resize(this, first, size), "Изображение сохранено");
                    } catch (NumberFormatException e) { showError(new IllegalArgumentException("Введите число от 320 до 8000")); }
                });
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
                FileStore.publishBytes(this, result.getBytes(StandardCharsets.UTF_8), "OCR_" + System.currentTimeMillis() + ".txt", "text/plain", null);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    new AlertDialog.Builder(this).setTitle("Распознанный текст")
                            .setMessage(result.isBlank() ? "Текст не найден" : result)
                            .setPositiveButton("Готово", null).setNeutralButton("TXT сохранён", null).show();
                });
            } catch (Exception e) { runOnUiThread(() -> { dialog.dismiss(); showError(e); }); }
        });
    }

    private interface Work { Object run() throws Exception; }
    private interface InputCallback { void onValue(String value); }

    private void runTask(String label, Work work, String success) {
        ProgressDialog dialog = ProgressDialog.show(this, "ФайлМастер", label, true, false);
        worker.submit(() -> {
            try {
                Object result = work.run();
                runOnUiThread(() -> {
                    dialog.dismiss();
                    String suffix = result instanceof Integer ? " (" + result + ")" : "";
                    Toast.makeText(this, success + suffix, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) { runOnUiThread(() -> { dialog.dismiss(); showError(e); }); }
        });
    }

    private void ask(String title, String hint, boolean password, InputCallback callback) {
        EditText edit = new EditText(this);
        edit.setHint(hint); edit.setSingleLine(true); edit.setPadding(dp(16), dp(8), dp(16), dp(8));
        if (password) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle(title).setView(edit)
                .setPositiveButton("Продолжить", (d, w) -> callback.onValue(edit.getText().toString()))
                .setNegativeButton("Отмена", null).show();
    }

    private List<Uri> collectUris(Intent data) {
        List<Uri> list = new ArrayList<>();
        ClipData clips = data.getClipData();
        if (clips != null) for (int i = 0; i < clips.getItemCount(); i++) list.add(clips.getItemAt(i).getUri());
        else if (data.getData() != null) list.add(data.getData());
        return list;
    }

    private void showPdfQuickActions(Uri uri) {
        new AlertDialog.Builder(this).setTitle("PDF открыт в ФайлМастер")
                .setItems(new String[]{"Подписать пальцем", "Электронная подпись", "Сжать", "Разделить", "PDF → JPG", "PDF → TXT", "OCR PDF", "Проверить подписи"}, (d, which) -> {
                    pendingPdf = uri;
                    if (which == 0) startActivityForResult(new Intent(this, SignatureActivity.class), DRAW_SIGNATURE);
                    else if (which == 1) { pendingCryptoPdf = uri; pick(false, "*/*", "crypto_pick_cert"); }
                    else if (which == 2) chooseCompression(uri);
                    else if (which == 3) runTask("Разделяю PDF…", () -> PdfTools.split(this, uri), "Страницы сохранены");
                    else if (which == 4) runTask("Экспортирую страницы…", () -> PdfTools.pdfToJpeg(this, uri), "JPG сохранены");
                    else if (which == 5) runTask("Извлекаю текст…", () -> PdfTools.extractText(this, uri), "TXT сохранён");
                    else if (which == 6) runTask("Распознаю PDF…", () -> OcrTools.recognizePdfToTxt(this, uri), "OCR сохранён");
                    else runTask("Проверяю подписи…", () -> DigitalSignatureTools.verifyPdf(this, uri), "Отчёт сохранён");
                }).setNegativeButton("Закрыть", null).show();
    }

    private void showError(Exception e) {
        new AlertDialog.Builder(this).setTitle("Не получилось")
                .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                .setPositiveButton("OK", null).show();
    }

    private TextView section(String value) {
        TextView v = text(value, 21, Color.rgb(25, 28, 36), true);
        v.setPadding(0, dp(26), 0, dp(10)); return v;
    }

    private View tool(String title, String subtitle, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(rounded(Color.WHITE, dp(15))); box.setElevation(dp(2)); box.setOnClickListener(listener);
        box.addView(text(title, 17, Color.rgb(25, 28, 36), true));
        TextView s = text(subtitle, 14, Color.DKGRAY, false); s.setPadding(0, dp(3), 0, 0); box.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10)); box.setLayoutParams(lp); return box;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD); return v;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); return g;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) worker.shutdownNow();
    }
}
