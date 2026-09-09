from pathlib import Path

root=Path('InkFlowReader')
target=root/'app/src/main/java/app/inkflow/reader'

# Reader settings must be a real full-screen Activity so it participates in the
# same banner/safe-area layout as the rest of the app. The old AlertDialog
# could cover the ad slot and system navigation area.
p=target/'BookReaderActivity.java'
s=p.read_text()
old='private boolean loaded=false,bars=true,animating=false;'
new='private boolean loaded=false,bars=true,animating=false,settingsLaunched=false;'
if old not in s:
    raise SystemExit('BookReader boolean marker missing')
s=s.replace(old,new,1)

old='settings.setOnClickListener(v->showSettings());'
new='settings.setOnClickListener(v->{ saveNow(); settingsLaunched=true; startActivity(new android.content.Intent(this,BookSettingsActivity.class)); });'
if old not in s:
    raise SystemExit('BookReader settings click marker missing')
s=s.replace(old,new,1)

marker='    @Override protected void onPause(){saveNow();super.onPause();}'
resume='''    @Override protected void onResume(){
        super.onResume();
        if(settingsLaunched && store!=null){
            settingsLaunched=false;
            int r=loaded?currentRatio():store.getRatio(uri);
            readSettings();
            applyWindowSettings(true);
            pendingRatio=r;
            if(book!=null) loadCurrentHtml();
        }
    }
'''
if marker not in s:
    raise SystemExit('BookReader onPause marker missing')
s=s.replace(marker,resume+marker,1)
p.write_text(s)

# Release bump.
build=root/'app/build.gradle'
b=build.read_text().replace('versionCode 20','versionCode 21').replace("versionName '1.6.5'","versionName '1.6.6'")
if "versionName '1.6.6'" not in b:
    raise SystemExit('1.6.6 version bump failed')
build.write_text(b)
