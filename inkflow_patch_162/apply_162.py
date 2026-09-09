from pathlib import Path

root=Path('InkFlowReader')
build=root/'app/build.gradle'
b=build.read_text().replace('versionCode 16','versionCode 17').replace("versionName '1.6.1'","versionName '1.6.2'")
build.write_text(b)
