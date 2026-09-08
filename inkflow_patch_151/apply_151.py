from pathlib import Path
import re

root=Path('InkFlowReader')
target=root/'app/src/main/java/app/inkflow/reader'

# Library removal support.
lib=target/'LibraryStore.java'
s=lib.read_text()
if 'public synchronized void remove(String uri)' not in s:
    needle='    public synchronized int getProgress(String uri){for(Item i:load())if(i.uri.equals(uri))return i.progress;return 0;}\n'
    add=needle+'    public synchronized void remove(String uri){List<Item> list=load();list.removeIf(i->i.uri.equals(uri));save(list);}\n'
    if needle not in s: raise SystemExit('LibraryStore getProgress marker not found')
    s=s.replace(needle,add,1)
lib.write_text(s)

# Main library: long-press menu with remove-from-library and physical delete when provider allows it.
main=target/'MainActivity.java'
s=main.read_text()
if 'setOnItemLongClickListener' not in s:
    pat=re.compile(r'(adapter=new ComicAdapter\(\);grid\.setAdapter\(adapter\);grid\.setOnItemClickListener\(\(a,v,pos,id\)->openItem\(adapter\.items\.get\(pos\)\)\);)')
    if not pat.search(s): raise SystemExit('MainActivity grid marker not found')
    s=pat.sub(r'\1\n        grid.setOnItemLongClickListener((a,v,pos,id)->{showItemMenu(adapter.items.get(pos));return true;});',s,count=1)

if 'private void showItemMenu(LibraryStore.Item item)' not in s:
    methods=r'''    private void showItemMenu(LibraryStore.Item item){
        new AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(new String[]{"Убрать из библиотеки","Удалить файл с устройства"},(d,which)->{
                if(which==0){
                    store.remove(item.uri);
                    adapter.covers.remove(item.uri);
                    adapter.reload();
                    Toast.makeText(this,"Убрано из библиотеки",Toast.LENGTH_SHORT).show();
                }else confirmDeleteFile(item);
            })
            .setNegativeButton("Отмена",null)
            .show();
    }

    private void confirmDeleteFile(LibraryStore.Item item){
        new AlertDialog.Builder(this)
            .setTitle("Удалить файл?")
            .setMessage("Файл будет удалён с устройства, если источник разрешает удаление. Это действие нельзя отменить.\n\n"+item.name)
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Удалить",(d,w)->deleteFileAndRemove(item))
            .show();
    }

    private void deleteFileAndRemove(LibraryStore.Item item){
        pool.submit(()->{
            boolean deleted=false;
            try{
                Uri u=Uri.parse(item.uri);
                if("file".equalsIgnoreCase(u.getScheme()) && u.getPath()!=null){
                    deleted=new java.io.File(u.getPath()).delete();
                }else if(DocumentsContract.isDocumentUri(this,u)){
                    deleted=DocumentsContract.deleteDocument(getContentResolver(),u);
                }else{
                    deleted=getContentResolver().delete(u,null,null)>0;
                }
            }catch(Exception ignored){}
            final boolean ok=deleted;
            runOnUiThread(()->{
                if(ok){
                    store.remove(item.uri);
                    adapter.covers.remove(item.uri);
                    adapter.reload();
                    Toast.makeText(this,"Файл удалён",Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(this,"Источник не разрешил удалить файл. Можно убрать его только из библиотеки.",Toast.LENGTH_LONG).show();
                }
            });
        });
    }

'''
    marker='    private void openUri(Uri uri,String name)'
    if marker not in s: raise SystemExit('MainActivity openUri marker not found')
    s=s.replace(marker,methods+marker,1)
main.write_text(s)

# Centralize all external file opening in one router so Android shows a single "Читай Всё" target.
manifest=root/'app/src/main/AndroidManifest.xml'
ms=manifest.read_text()

def strip_reader_filter(text,name):
    pat=re.compile(r'(<activity\s+[^>]*android:name="\.'+re.escape(name)+r'"[^>]*>)(.*?)(</activity>)',re.S)
    m=pat.search(text)
    if not m: raise SystemExit('Activity not found: '+name)
    head,body,tail=m.group(1),m.group(2),m.group(3)
    body=re.sub(r'\s*<intent-filter>.*?<action\s+android:name="android.intent.action.VIEW"\s*/>.*?</intent-filter>','',body,flags=re.S)
    head=head.replace('android:exported="true"','android:exported="false"')
    return text[:m.start()]+head+body+tail+text[m.end():]

ms=strip_reader_filter(ms,'ReaderActivity')
ms=strip_reader_filter(ms,'BookReaderActivity')

router='''        <activity
            android:name=".OpenFileActivity"
            android:exported="true"
            android:noHistory="true"
            android:excludeFromRecents="true">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/pdf" />
                <data android:mimeType="application/epub+zip" />
                <data android:mimeType="application/x-fictionbook+xml" />
                <data android:mimeType="application/fb2+xml" />
                <data android:mimeType="text/plain" />
                <data android:mimeType="text/html" />
                <data android:mimeType="application/xhtml+xml" />
                <data android:mimeType="application/xml" />
                <data android:mimeType="text/xml" />
                <data android:mimeType="application/zip" />
                <data android:mimeType="application/x-zip-compressed" />
                <data android:mimeType="application/vnd.comicbook+zip" />
                <data android:mimeType="application/x-cbz" />
                <data android:mimeType="application/x-rar-compressed" />
                <data android:mimeType="application/x-rar" />
                <data android:mimeType="application/vnd.rar" />
                <data android:mimeType="application/vnd.comicbook-rar" />
                <data android:mimeType="application/x-cbr" />
                <data android:mimeType="application/x-7z-compressed" />
                <data android:mimeType="application/x-tar" />
                <data android:mimeType="image/*" />
                <data android:mimeType="application/octet-stream" />
            </intent-filter>
        </activity>
'''
if '.OpenFileActivity' not in ms:
    marker='        <activity\n            android:name=".MainActivity"'
    if marker not in ms: raise SystemExit('Manifest MainActivity marker not found')
    ms=ms.replace(marker,router+marker,1)
manifest.write_text(ms)

# Version bump.
build=root/'app/build.gradle'
b=build.read_text().replace('versionCode 11','versionCode 12').replace("versionName '1.5.0'","versionName '1.5.1'")
build.write_text(b)
