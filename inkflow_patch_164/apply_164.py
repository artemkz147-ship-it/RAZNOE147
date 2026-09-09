from pathlib import Path

root=Path('InkFlowReader')
build=root/'app/build.gradle'
b=build.read_text().replace('versionCode 18','versionCode 19').replace("versionName '1.6.3'","versionName '1.6.4'")
if "versionName '1.6.4'" not in b:
    raise SystemExit('1.6.4 version bump failed')
build.write_text(b)
