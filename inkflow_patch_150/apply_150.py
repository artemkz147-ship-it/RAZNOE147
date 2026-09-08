from pathlib import Path
import re

root=Path('InkFlowReader')
target=root/'app/src/main/java/app/inkflow/reader'

# Reserve one permanent banner slot on every Activity screen.
for p in target.glob('*Activity.java'):
    s=p.read_text()
    if 'BannerAdSlot.wrap' in s:
        continue
    s2=re.sub(r'setContentView\(([^;\n]+)\);', r'setContentView(BannerAdSlot.wrap(this,\1));', s)
    if s2 != s:
        p.write_text(s2)
        print('ad slot:', p.name)

build=root/'app/build.gradle'
s=build.read_text()
if "com.yandex.android:mobileads:8.2.0" not in s:
    marker="    implementation 'org.tukaani:xz:1.12'"
    if marker in s:
        s=s.replace(marker, marker+"\n    implementation 'com.yandex.android:mobileads:8.2.0'")
    else:
        s=s.replace('dependencies {', "dependencies {\n    implementation 'com.yandex.android:mobileads:8.2.0'", 1)
s=s.replace('versionCode 10','versionCode 11')
s=s.replace("versionName '1.4.3'","versionName '1.5.0'")
build.write_text(s)

# We intentionally do not add or configure interstitial ads in this version.
manifest=root/'app/src/main/AndroidManifest.xml'
ms=manifest.read_text()
if 'android.permission.INTERNET' not in ms:
    ms=ms.replace('<application','    <uses-permission android:name="android.permission.INTERNET" />\n\n    <application',1)
manifest.write_text(ms)
