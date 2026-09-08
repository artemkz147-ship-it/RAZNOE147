from pathlib import Path

root=Path('InkFlowReader')
build=root/'app/build.gradle'
s=build.read_text()
s=s.replace('versionCode 12','versionCode 13').replace("versionName '1.5.1'","versionName '1.5.2'")
build.write_text(s)
