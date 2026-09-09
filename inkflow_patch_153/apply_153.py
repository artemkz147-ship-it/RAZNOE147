from pathlib import Path
import re

root=Path('InkFlowReader')
target=root/'app/src/main/java/app/inkflow/reader'

# Wire office formats into the existing book reader.
bf=target/'BookSourceFactory.java'
s=bf.read_text()
old='return l.endsWith(".epub")||l.endsWith(".fb2")||l.endsWith(".txt")||l.endsWith(".html")||l.endsWith(".htm");'
if 'DocumentSourceFactory.isDocumentFormat(name)' not in s:
    if old not in s: raise SystemExit('BookSourceFactory format marker not found')
    s=s.replace(old,old[:-1]+'||DocumentSourceFactory.isDocumentFormat(name);',1)
if 'DocumentSourceFactory.open(c,uri,name)' not in s:
    marker='        throw new IOException("Неподдерживаемая книга: "+name);\n'
    if marker not in s: raise SystemExit('BookSourceFactory open marker not found')
    s=s.replace(marker,'        if(DocumentSourceFactory.isDocumentFormat(name)) return DocumentSourceFactory.open(c,uri,name);\n'+marker,1)
bf.write_text(s)

# External ACTION_VIEW router: recognise Word/RTF/ODT by extension and MIME.
op=target/'OpenFileActivity.java'
s=op.read_text()
s=s.replace('cbz|cbr|cb7|cbt|zip|rar|7z|tar|pdf|epub|fb2|txt|html|htm|jpg|jpeg|png|webp|gif|bmp',
            'cbz|cbr|cb7|cbt|zip|rar|7z|tar|pdf|epub|fb2|txt|html|htm|docx|docm|doc|rtf|odt|jpg|jpeg|png|webp|gif|bmp')
needle='        if(m.equals("text/plain"))return ".txt";\n'
extra='''        if(m.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))return ".docx";\n        if(m.equals("application/vnd.ms-word.document.macroenabled.12"))return ".docm";\n        if(m.equals("application/msword"))return ".doc";\n        if(m.equals("application/rtf")||m.equals("text/rtf"))return ".rtf";\n        if(m.equals("application/vnd.oasis.opendocument.text"))return ".odt";\n'''
if 'wordprocessingml.document' not in s:
    if needle not in s: raise SystemExit('OpenFileActivity MIME marker not found')
    s=s.replace(needle,extra+needle,1)
op.write_text(s)

# Register Word/RTF/ODT with Android's Open with chooser.
manifest=root/'app/src/main/AndroidManifest.xml'
ms=manifest.read_text()
if 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' not in ms:
    marker='                <data android:mimeType="application/octet-stream" />\n'
    add='''                <data android:mimeType="application/vnd.openxmlformats-officedocument.wordprocessingml.document" />\n                <data android:mimeType="application/vnd.ms-word.document.macroEnabled.12" />\n                <data android:mimeType="application/msword" />\n                <data android:mimeType="application/rtf" />\n                <data android:mimeType="text/rtf" />\n                <data android:mimeType="application/vnd.oasis.opendocument.text" />\n'''
    if marker not in ms: raise SystemExit('Manifest router marker not found')
    ms=ms.replace(marker,add+marker,1)
manifest.write_text(ms)

# Internal file picker and folder scan.
main=target/'MainActivity.java'
m=main.read_text()
if 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' not in m:
    m=m.replace('"application/octet-stream"}', '"application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/msword","application/rtf","text/rtf","application/vnd.oasis.opendocument.text","application/octet-stream"}',1)
m=re.sub(r'fb2\|txt\|html\|htm(?!\|docx)', 'fb2|txt|html|htm|docx|docm|doc|rtf|odt', m)
main.write_text(m)

# Apache POI scratchpad is used only for old binary .doc; DOCX/RTF/ODT use lightweight built-in parsers.
build=root/'app/build.gradle'
b=build.read_text()
if "org.apache.poi:poi-scratchpad:5.2.5" not in b:
    b=b.replace("    implementation 'org.tukaani:xz:1.12'", "    implementation 'org.tukaani:xz:1.12'\n    implementation 'org.apache.poi:poi-scratchpad:5.2.5'",1)
b=b.replace('versionCode 13','versionCode 14').replace("versionName '1.5.2'","versionName '1.5.3'")
build.write_text(b)
