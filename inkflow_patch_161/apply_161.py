from pathlib import Path

root=Path('InkFlowReader')
target=root/'app/src/main/java/app/inkflow/reader'

# Safer MainActivity startup: if any decorative 1.6 component throws at runtime,
# show a minimal usable home instead of terminating the process.
p=target/'MainActivity.java'
s=p.read_text()
s=s.replace('store=new LibraryStore(this); build();','store=new LibraryStore(this); try{build();}catch(Throwable crash){buildEmergency();}',1)
s=s.replace('if(day!=lastDay){lastDay=day;AppTheme.apply(this);build();return;}','if(day!=lastDay){lastDay=day;AppTheme.apply(this);try{build();}catch(Throwable crash){buildEmergency();}return;}',1)
old='CoverPlaceholderView placeholder=new CoverPlaceholderView(MainActivity.this,it.name);coverWrap.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));'
new='TextView placeholder=Ui.text(MainActivity.this,"ЧИТАЙ ВСЁ\\n\\n"+it.name,13,Ui.TEXT);placeholder.setGravity(Gravity.CENTER);placeholder.setPadding(Ui.dp(MainActivity.this,14),Ui.dp(MainActivity.this,16),Ui.dp(MainActivity.this,14),Ui.dp(MainActivity.this,16));placeholder.setBackground(Ui.gradient(new int[]{Ui.SURFACE3,Ui.SURFACE2,Ui.SURFACE},16,MainActivity.this));coverWrap.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));'
if old in s:s=s.replace(old,new,1)
marker='    private boolean isLandscape()'
if 'private void buildEmergency()' not in s:
    emergency='''    private void buildEmergency(){\n        try{AppTheme.apply(this);}catch(Throwable ignored){}\n        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(Ui.dp(this,20),Ui.dp(this,30),Ui.dp(this,20),Ui.dp(this,30));root.setBackgroundColor(Ui.BG);\n        TextView h=Ui.text(this,"ЧИТАЙ ВСЁ",28,Ui.GOLD);h.setGravity(Gravity.CENTER);h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);root.addView(h,new LinearLayout.LayoutParams(-1,Ui.dp(this,64)));\n        TextView msg=Ui.text(this,"Безопасный режим запуска",13,Ui.MUTED);msg.setGravity(Gravity.CENTER);root.addView(msg,new LinearLayout.LayoutParams(-1,Ui.dp(this,42)));\n        TextView open=action("Открыть файл",true);open.setOnClickListener(v->pickFile());root.addView(open,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));\n        TextView lib=action("Вся библиотека",false);lib.setOnClickListener(v->startActivity(new Intent(this,LibraryActivity.class)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,54));lp.topMargin=Ui.dp(this,12);root.addView(lib,lp);\n        TextView settings=action("Настройки",false);settings.setOnClickListener(v->showInfo());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,Ui.dp(this,54));sp.topMargin=Ui.dp(this,12);root.addView(settings,sp);\n        setContentView(root);\n    }\n\n'''
    s=s.replace(marker,emergency+marker,1)
p.write_text(s)

# Full-screen library gets the same simple placeholder to avoid startup/render crashes.
p=target/'LibraryActivity.java'
s=p.read_text()
old='CoverPlaceholderView ph=new CoverPlaceholderView(LibraryActivity.this,it.name);coverWrap.addView(ph,new FrameLayout.LayoutParams(-1,-1));'
new='TextView ph=Ui.text(LibraryActivity.this,"ЧИТАЙ ВСЁ\\n\\n"+it.name,13,Ui.TEXT);ph.setGravity(Gravity.CENTER);ph.setPadding(Ui.dp(LibraryActivity.this,14),Ui.dp(LibraryActivity.this,16),Ui.dp(LibraryActivity.this,14),Ui.dp(LibraryActivity.this,16));ph.setBackground(Ui.gradient(new int[]{Ui.SURFACE3,Ui.SURFACE2,Ui.SURFACE},16,LibraryActivity.this));coverWrap.addView(ph,new FrameLayout.LayoutParams(-1,-1));'
if old in s:s=s.replace(old,new,1)
p.write_text(s)

build=root/'app/build.gradle'
b=build.read_text().replace('versionCode 15','versionCode 16').replace("versionName '1.6.0'","versionName '1.6.1'")
build.write_text(b)
