from pathlib import Path

main_path = Path('app/src/main/java/ru/filemaster/offline/MainActivity.java')
conv_path = Path('app/src/main/java/ru/filemaster/offline/ConvertersActivity.java')
s = main_path.read_text(encoding='utf-8')


def replace_between(text, start_marker, end_marker, replacement):
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    return text[:start] + replacement + text[end:]

pdf_card = 'root.addView(category("PDF","PDF","Просмотр, конструктор, редактор, формы, Office","34+",Color.rgb(235,239,255),BLUE,v->renderCategory("pdf")));'
new_pdf_cards = 'root.addView(category("PDF","PDF","Просмотр, конструктор, редактор, формы и защита","25+",Color.rgb(235,239,255),BLUE,v->renderCategory("pdf")));root.addView(category("CONV","Конвертеры","PDF, Office, таблицы, презентации и изображения","29",Color.rgb(230,248,247),Color.rgb(0,132,123),v->startActivity(new Intent(this,ConvertersActivity.class))));'
if pdf_card not in s:
    raise SystemExit('PDF home card marker not found')
s = s.replace(pdf_card, new_pdf_cards, 1)
s = s.replace('root.addView(category("DOC","Документы","DOCX, ODT, TXT, HTML, Markdown, RTF","9",', 'root.addView(category("DOC","Документы","Редактирование DOCX, ODT и текста","1",', 1)
s = s.replace('root.addView(category("XLS","Таблицы","Редактор, все листы, формулы, CSV/TSV","7",', 'root.addView(category("XLS","Таблицы","Редактор XLSX/CSV/TSV и отчёт по формулам","2",', 1)
s = s.replace('root.addView(category("PPT","Презентации","Просмотр, порядок слайдов, PPTX ↔ PDF/TXT","3",', 'root.addView(category("PPT","Презентации","Конструктор и порядок слайдов PPTX","1",', 1)
s = s.replace('root.addView(category("IMG","Изображения","Оптимизация, размер, кадрирование, фон, форматы","22+",', 'root.addView(category("IMG","Изображения","Оптимизация, размер, кадрирование, фон и обработка","12",', 1)

new_docs = '''    private void addDocTools(LinearLayout r){r.addView(tool("Редактировать DOCX / ODT / TXT","Изменить текст и сохранить новую копию",v->pick(false,"*/*","edit_document")));}\n'''
s = replace_between(s, '    private void addDocTools', '    private void addSheetTools', new_docs)

new_sheets = '''    private void addSheetTools(LinearLayout r){r.addView(tool("Редактировать XLSX / CSV / TSV","Текстовая сетка с формулами",v->pick(false,"*/*","edit_table")));r.addView(tool("Отчёт XLSX","Листы, строки, ячейки и формулы",v->pick(false,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","xlsx_report")));}\n'''
s = replace_between(s, '    private void addSheetTools', '    private void addSlideTools', new_sheets)

new_slides = '''    private void addSlideTools(LinearLayout r){r.addView(tool("Конструктор слайдов","Менять порядок слайдов без конвертации PPTX",v->pick(false,"application/vnd.openxmlformats-officedocument.presentationml.presentation","organize_pptx")));}\n\n'''
s = replace_between(s, '    private void addSlideTools', '    private void addPdfTools', new_slides)

new_pdf = '''    private void addPdfTools(LinearLayout r){r.addView(section("Просмотр и конструкция",null));r.addView(tool("Открыть PDF в Доки","Страницы, навигация и быстрые действия",v->pick(false,"application/pdf","view_file")));r.addView(tool("Конструктор страниц","Менять порядок, поворачивать, удалять и делать копии",v->pick(false,"application/pdf","organize_pdf")));r.addView(section("Редактор и формы",null));r.addView(tool("Многостраничный визуальный редактор","Ручка, маркер, текст, прямоугольники, линии, штампы",v->pick(false,"application/pdf","annotate_pdf")));r.addView(tool("Надёжно скрыть область","Закрытая часть разрушается при пересборке страницы",v->pick(false,"application/pdf","redact_pdf")));r.addView(tool("Заполнить / создать PDF-форму","Текст, checkbox, radio, choice + конструктор новых полей",v->pick(false,"application/pdf","fill_pdf_form")));
        r.addView(section("Страницы",null));r.addView(tool("Объединить PDF","Несколько → один",v->pick(true,"application/pdf","merge_pdf")));r.addView(tool("Разделить PDF","Каждая страница отдельно",v->pick(false,"application/pdf","split_pdf")));r.addView(tool("Извлечь страницы","Например: 1,3,5-8",v->pick(false,"application/pdf","extract_pages")));r.addView(tool("Удалить страницы","Номера или диапазоны",v->pick(false,"application/pdf","remove_pages")));r.addView(tool("Повернуть на 90°","Все страницы",v->pick(false,"application/pdf","rotate_pdf")));r.addView(tool("Обратный порядок","Последняя станет первой",v->pick(false,"application/pdf","reverse_pdf")));r.addView(tool("Добавить номера страниц","По центру снизу",v->pick(false,"application/pdf","page_numbers")));r.addView(tool("Обрезать поля PDF","Процент со всех сторон",v->pick(false,"application/pdf","crop_pdf")));r.addView(tool("Извлечь встроенные изображения","Картинки → PNG",v->pick(false,"application/pdf","extract_pdf_images")));
        r.addView(section("Обработка и защита",null));r.addView(tool("Сжать PDF","Три уровня",v->pick(false,"application/pdf","compress_pdf")));r.addView(tool("Пересохранить структуру","Повторно сохранить PDF без изменения порядка",v->pick(false,"application/pdf","repair_pdf")));r.addView(tool("Водяной знак","Текст на каждой странице",v->pick(false,"application/pdf","watermark_pdf")));r.addView(tool("Очистить метаданные","Свойства и XMP",v->pick(false,"application/pdf","clean_metadata")));r.addView(tool("Сравнить два PDF","Текстовый отчёт",v->pick(true,"application/pdf","compare_pdf")));r.addView(tool("Защитить паролем","Локальное шифрование",v->pick(false,"application/pdf","protect_pdf")));r.addView(tool("Снять известный пароль","Нужен текущий пароль",v->pick(false,"application/pdf","unlock_pdf")));
        r.addView(section("Пакетно",null));r.addView(tool("Сжать несколько PDF","Один режим для всех",v->pick(true,"application/pdf","batch_pdf_compress")));r.addView(tool("Водяной знак на несколько","Один текст",v->pick(true,"application/pdf","batch_pdf_watermark")));r.addView(tool("Очистить метаданные нескольких","Privacy batch",v->pick(true,"application/pdf","batch_pdf_clean")));r.addView(tool("OCR нескольких PDF","Отдельный TXT для каждого",v->pick(true,"application/pdf","batch_pdf_ocr")));r.addView(tool("Защитить несколько PDF","Один пароль для всех",v->pick(true,"application/pdf","batch_pdf_protect")));}\n\n'''
s = replace_between(s, '    private void addPdfTools', '    private void addImageTools', new_pdf)

new_images = '''    private void addImageTools(LinearLayout r){r.addView(section("Качество, размер и кадр",null));r.addView(tool("Оптимизация","Максимум качества или настраиваемый процент • без изменения пикселей",v->pick(false,"image/*","image_optimize")));r.addView(tool("Размер и пропорции","Точные W × H px • пропорционально или свободно",v->pick(false,"image/*","image_size_studio")));r.addView(tool("Кадрирование","9:16 по умолчанию • 16 пресетов • свободно • точный размер",v->pick(false,"image/*","image_crop_studio")));r.addView(tool("Умное удаление фона","Автоматически по краям + ручное стереть / вернуть",v->pick(false,"image/*","image_background")));r.addView(section("Редактирование",null));r.addView(tool("Повернуть на 90°","Новая копия",v->pick(false,"image/*","image_rotate")));r.addView(tool("Отразить горизонтально","Зеркальная копия",v->pick(false,"image/*","image_flip")));r.addView(tool("Сделать Ч/Б","Оттенки серого",v->pick(false,"image/*","image_gray")));r.addView(tool("Водяной знак","Текст снизу справа",v->pick(false,"image/*","image_watermark")));r.addView(tool("Удалить EXIF/XMP","Перекодирование без метаданных",v->pick(false,"image/*","image_strip_metadata")));
        r.addView(section("Пакетно",null));r.addView(tool("Сжать несколько","JPEG 78%",v->pick(true,"image/*","image_batch_compress")));r.addView(tool("Водяной знак на несколько","Один текст",v->pick(true,"image/*","image_batch_watermark")));r.addView(tool("Очистить метаданные нескольких","Privacy batch",v->pick(true,"image/*","image_batch_strip_metadata")));}\n\n'''
s = replace_between(s, '    private void addImageTools', '    private void addArchiveTools', new_images)

main_path.write_text(s, encoding='utf-8')

c = conv_path.read_text(encoding='utf-8')
needle = '        root.addView(tool("PDF → JPG","Все страницы в изображения",v->pick(false,"application/pdf","pdf_jpg")));\n'
if needle not in c:
    raise SystemExit('converter PDF JPG marker not found')
c = c.replace(needle, needle + '        root.addView(tool("Несколько PDF → JPG","Пакетный экспорт всех страниц",v->pick(true,"application/pdf","batch_pdf_jpg")));\n', 1)
case_needle = '        case"pdf_jpg"->runTask("Экспортирую JPG…",()->PdfTools.pdfToJpeg(this,first),"JPG сохранены");\n'
if case_needle not in c:
    raise SystemExit('converter switch marker not found')
c = c.replace(case_needle, case_needle + '        case"batch_pdf_jpg"->runTask("Экспортирую PDF в JPG…",()->BatchPdfTools.toJpg(this,uris),"Страницы экспортированы");\n', 1)
conv_path.write_text(c, encoding='utf-8')

print('Converters moved to dedicated tab')
