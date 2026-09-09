from pathlib import Path
import re

root=Path('InkFlowReader')
target=root/'app/src/main/java/app/inkflow/reader'

for name in ['ReaderActivity.java','BookReaderActivity.java','TorrentActivity.java','OpenFileActivity.java']:
    p=target/name
    if not p.exists():
        continue
    s=p.read_text()
    if 'AppTheme.apply(this);' not in s:
        s2=re.sub(r'(super\.onCreate\([^;]+\);)',r'\1AppTheme.apply(this);',s,count=1)
        if s2==s:
            print('WARN theme injection failed:',name)
        s=s2
    p.write_text(s)

p=target/'BookReaderActivity.java'
s=p.read_text()
old='@Override protected void onPause(){saveNow();super.onPause();}'
new='@Override protected void onPause(){saveNow();new LibraryStore(this).upsert(uri,name==null?"Книга":name);super.onPause();}'
if old in s:
    s=s.replace(old,new,1)
elif 'new LibraryStore(this).upsert(uri' not in s:
    print('WARN BookReaderActivity onPause marker missing')
p.write_text(s)

manifest=root/'app/src/main/AndroidManifest.xml'
ms=manifest.read_text()
activity='''        <activity\n            android:name=".LibraryActivity"\n            android:screenOrientation="unspecified"\n            android:exported="false" />\n'''
if '.LibraryActivity' not in ms:
    marker='        <activity\n            android:name=".MainActivity"'
    if marker not in ms:
        raise SystemExit('MainActivity manifest marker missing')
    ms=ms.replace(marker,activity+marker,1)
manifest.write_text(ms)

build=root/'app/build.gradle'
b=build.read_text().replace('versionCode 14','versionCode 15').replace("versionName '1.5.3'","versionName '1.6.0'")
build.write_text(b)
