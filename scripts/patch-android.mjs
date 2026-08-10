import fs from 'node:fs';
const manifest='android/app/src/main/AndroidManifest.xml';
let xml=fs.readFileSync(manifest,'utf8');
xml=xml.replace(/<activity\s+/, '<activity android:screenOrientation="landscape" ');
fs.writeFileSync(manifest,xml);
const styles='android/app/src/main/res/values/styles.xml';
let s=fs.readFileSync(styles,'utf8');
if(!s.includes('windowFullscreen')) s=s.replace(/<\/style>/g,'    <item name="android:windowFullscreen">true</item>\n        <item name="android:navigationBarColor">#050910</item>\n        <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>\n    </style>');
fs.writeFileSync(styles,s);
