from pathlib import Path

root=Path('InkFlowReader')
main=root/'app/src/main/java/app/inkflow/reader/MainActivity.java'
s=main.read_text()
repls={
'TextView brand=Ui.text(this,"INKFLOW",28,Ui.GOLD);':'TextView brand=Ui.text(this,"ЧИТАЙ ВСЁ",28,Ui.GOLD);',
'TextView sub=Ui.text(this,"Премиальная читалка комиксов, манги и webtoon",12,Ui.TEXT);':'TextView sub=Ui.text(this,"Книги, манга, комиксы и webtoon в одной сочной читалке",12,Ui.TEXT);',
'TextView heroTitle=Ui.text(this,"Читай красиво",22,Ui.TEXT);':'TextView heroTitle=Ui.text(this,"Одна читалка для всего",22,Ui.TEXT);',
'TextView heroSub=Ui.text(this,"Авто‑двойные страницы в альбомной ориентации, манга RTL, webtoon, 3D‑перелистывание и локальная библиотека без аккаунта.",13,0xFFF0EDE7);':'TextView heroSub=Ui.text(this,"Комиксы и манга с сочным перелистыванием, плюс полноценная книжная читалка EPUB/FB2/TXT/HTML с запоминанием места чтения.",13,0xFFF0EDE7);',
'infoPill("CBZ • CBR • PDF",true)':'infoPill("CBZ • CBR • PDF • EPUB",true)',
'infoPill("RTL • 2× • WEBTOON",false)':'infoPill("FB2 • TXT • 2× • WEBTOON",false)',
'new String[]{"application/pdf","application/epub+zip","application/zip","application/x-rar-compressed","image/*","application/octet-stream"}':'new String[]{"application/pdf","application/epub+zip","application/zip","application/x-rar-compressed","image/*","text/plain","text/html","application/xml","application/octet-stream"}',
'cbz|cbr|cb7|cbt|zip|rar|7z|tar|pdf|epub|jpg|jpeg|png|webp|gif|bmp':'cbz|cbr|cb7|cbt|zip|rar|7z|tar|pdf|epub|fb2|txt|html|htm|jpg|jpeg|png|webp|gif|bmp',
'Intent i=new Intent(this,ReaderActivity.class);':'Intent i=new Intent(this,SourceFactory.isBookFormat(name)?BookReaderActivity.class:ReaderActivity.class);',
'new AlertDialog.Builder(this).setTitle("InkFlow Reader").setMessage("Сочный локальный ридер без аккаунта и без загрузки в облако.\\n\\n• Авто‑двойные страницы в альбомной ориентации\\n• Манга RTL и webtoon\\n• Премиальный интерфейс и 3D‑перелистывание\\n• CBZ, CBR, PDF, EPUB и изображения")':'new AlertDialog.Builder(this).setTitle("Читай Всё").setMessage("Сочная локальная читалка без аккаунта и без загрузки в облако.\\n\\n• Комиксы, манга и книги в одном приложении\\n• Авто‑двойные страницы в альбомной ориентации\\n• Манга RTL и webtoon\\n• Премиальный интерфейс и 3D‑перелистывание\\n• Книги: EPUB, FB2, TXT, HTML\\n• Комиксы: CBZ, CBR, PDF, EPUB и изображения")'
}
for a,b in repls.items():
    if a not in s: print('WARN main pattern missing:',a[:80])
    s=s.replace(a,b)
main.write_text(s)

sf=root/'app/src/main/java/app/inkflow/reader/SourceFactory.java'
s=sf.read_text()
needle='public final class SourceFactory {\n'
if 'isBookFormat(String name)' not in s:
    s=s.replace(needle,needle+'    public static boolean isBookFormat(String name){ return BookSourceFactory.isBookFormat(name); }\n\n')
sf.write_text(s)

manifest=root/'app/src/main/AndroidManifest.xml'
s=manifest.read_text().replace('android:label="InkFlow"','android:label="Читай Всё"')
activity='''        <activity\n            android:name=".BookReaderActivity"\n            android:configChanges="orientation|screenSize|keyboardHidden"\n            android:screenOrientation="unspecified"\n            android:exported="true" />\n'''
if '.BookReaderActivity' not in s:
    s=s.replace('        <activity\n            android:name=".MainActivity"',activity+'        <activity\n            android:name=".MainActivity"')
manifest.write_text(s)

build=root/'app/build.gradle'
s=build.read_text().replace('versionCode 4','versionCode 5').replace("versionName '1.1.1'","versionName '1.2.0'")
build.write_text(s)
