from pathlib import Path
root=Path('InkFlowReader')
main=root/'app/src/main/java/app/inkflow/reader/MainActivity.java'
s=main.read_text()
needle='        root.addView(actions);\n'
add='''        root.addView(actions);\n\n        TextView torrents=action("⇩  Торрент-загрузки • magnet • .torrent",false);\n        torrents.setOnClickListener(v->startActivity(new Intent(this,TorrentActivity.class)));\n        LinearLayout.LayoutParams tcp=new LinearLayout.LayoutParams(-1,Ui.dp(this,52));tcp.setMargins(0,0,0,Ui.dp(this,14));root.addView(torrents,tcp);\n'''
if 'Торрент-загрузки • magnet • .torrent' not in s:
    s=s.replace(needle,add,1)
main.write_text(s)

manifest=root/'app/src/main/AndroidManifest.xml'
s=manifest.read_text()
perms='''    <uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />\n    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <uses-permission android:name="android.permission.WAKE_LOCK" />\n'''
if 'android.permission.INTERNET' not in s:
    s=s.replace('<application',perms+'\n    <application',1)
components='''        <service\n            android:name=".TorrentService"\n            android:exported="false"\n            android:foregroundServiceType="dataSync" />\n        <activity\n            android:name=".TorrentActivity"\n            android:screenOrientation="unspecified"\n            android:exported="true">\n            <intent-filter>\n                <action android:name="android.intent.action.VIEW" />\n                <category android:name="android.intent.category.DEFAULT" />\n                <category android:name="android.intent.category.BROWSABLE" />\n                <data android:scheme="magnet" />\n            </intent-filter>\n            <intent-filter>\n                <action android:name="android.intent.action.VIEW" />\n                <category android:name="android.intent.category.DEFAULT" />\n                <data android:mimeType="application/x-bittorrent" />\n            </intent-filter>\n        </activity>\n'''
if '.TorrentService' not in s:
    s=s.replace('        <activity\n            android:name=".BookSettingsActivity"',components+'        <activity\n            android:name=".BookSettingsActivity"',1)
manifest.write_text(s)

build=root/'app/build.gradle'
s=build.read_text()
if 'libtorrent4j' not in s:
    s=s.replace("    implementation 'org.tukaani:xz:1.12'","    implementation 'org.tukaani:xz:1.12'\n    implementation 'org.libtorrent4j:libtorrent4j:2.1.0-39'\n    implementation 'org.libtorrent4j:libtorrent4j-android-arm:2.1.0-39'\n    implementation 'org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-39'\n    implementation 'org.libtorrent4j:libtorrent4j-android-x86:2.1.0-39'\n    implementation 'org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-39'")
s=s.replace('versionCode 6','versionCode 10').replace("versionName '1.3.0'","versionName '1.4.3'")
build.write_text(s)
