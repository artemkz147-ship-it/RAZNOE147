import fs from 'node:fs';
const manifest='android/app/src/main/AndroidManifest.xml';
let xml=fs.readFileSync(manifest,'utf8');
xml=xml.replace(/<activity([^>]*?)android:name="\.MainActivity"([^>]*?)>/, (m,a,b)=>{
  let attrs=`<activity${a}android:name=".MainActivity"${b}>`;
  if(!/screenOrientation=/.test(attrs)) attrs=attrs.replace('>',' android:screenOrientation="landscape">');
  return attrs;
});
fs.writeFileSync(manifest,xml);
const main='android/app/src/main/java/com/artemkz/shadowronin/MainActivity.java';
let java=fs.readFileSync(main,'utf8');
java=java.replace('import com.getcapacitor.BridgeActivity;','import com.getcapacitor.BridgeActivity;\nimport android.os.Bundle;\nimport android.view.View;');
java=java.replace('public class MainActivity extends BridgeActivity {}',`public class MainActivity extends BridgeActivity {\n  @Override protected void onCreate(Bundle savedInstanceState){\n    super.onCreate(savedInstanceState);\n    getWindow().getDecorView().setSystemUiVisibility(\n      View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN\n    );\n  }\n}`);
fs.writeFileSync(main,java);
