from pathlib import Path
import re

root=Path('InkFlowReader')
main=root/'app/src/main/java/app/inkflow/reader/MainActivity.java'
s=main.read_text()
# The top-right gear used to call showInfo(), which only displayed a product description.
# Keep the existing click wiring and turn showInfo() into the real settings screen.
pat=re.compile(r'private\s+void\s+showInfo\s*\(\s*\)\s*\{.*?\.show\s*\(\s*\)\s*;\s*\}',re.S)
replacement='private void showInfo(){ startActivity(new Intent(this,BookSettingsActivity.class)); }'
if pat.search(s):
    s=pat.sub(replacement,s,count=1)
else:
    print('WARN: showInfo method not found, trying broad replacement')
    s=re.sub(r'private\s+void\s+showInfo\s*\(\s*\)\s*\{.*?\n\s*\}',replacement,s,count=1,flags=re.S)
main.write_text(s)

manifest=root/'app/src/main/AndroidManifest.xml'
s=manifest.read_text()
settings_activity='''        <activity\n            android:name=".BookSettingsActivity"\n            android:screenOrientation="unspecified"\n            android:exported="false" />\n'''
if '.BookSettingsActivity' not in s:
    s=s.replace('        <activity\n            android:name=".BookReaderActivity"',settings_activity+'        <activity\n            android:name=".BookReaderActivity"')
manifest.write_text(s)

build=root/'app/build.gradle'
s=build.read_text().replace('versionCode 5','versionCode 6').replace("versionName '1.2.0'","versionName '1.3.0'")
build.write_text(s)
