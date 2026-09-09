from pathlib import Path

root=Path('InkFlowReader')
target=root/'app/src/main/java/app/inkflow/reader'

# Extra breathing room below status bar / display cutout on the main app screens.
repls={
    'MainActivity.java':('Ui.safeInsets(root,this,16,8,16,8);','Ui.safeInsets(root,this,16,14,16,8);'),
    'LibraryActivity.java':('Ui.safeInsets(root,this,14,8,14,8);','Ui.safeInsets(root,this,14,14,14,8);'),
    'TorrentActivity.java':('Ui.safeInsets(root,this,0,0,0,0);','Ui.safeInsets(root,this,0,10,0,0);'),
    'BookSettingsActivity.java':('Ui.safeInsets(box,this,0,0,0,0);','Ui.safeInsets(box,this,0,10,0,0);'),
}
for name,(old,new) in repls.items():
    p=target/name
    s=p.read_text()
    if old in s:
        s=s.replace(old,new,1)
        p.write_text(s)

build=root/'app/build.gradle'
b=build.read_text().replace('versionCode 19','versionCode 20').replace("versionName '1.6.4'","versionName '1.6.5'")
if "versionName '1.6.5'" not in b:
    raise SystemExit('1.6.5 version bump failed')
build.write_text(b)
