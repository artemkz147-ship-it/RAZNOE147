from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v272 patch missing: {label}')
    return text.replace(old, new, 1)


# Android/WebView file pickers often hide raw ROM extensions (.nes/.md/.smd/etc.)
# when they are supplied through input.accept. Show all files and validate the
# extension ourselves after selection; archives and raw ROMs then behave identically.
p = Path('app/src/main/assets/launcher.js')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    "chooseFiles({accept:allowed.map(x=>`.${x}`).join(','),onFiles:async files=>",
    "chooseFiles({accept:'*/*',onFiles:async files=>",
    'ROM picker MIME/extension filter',
)
p.write_text(s, encoding='utf-8')

# Update in place using the same package/signing certificate.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 12', 'versionCode 13', 'version code')
t = must_replace(t, "versionName '2.7.1'", "versionName '2.7.2'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.7.2 raw ROM picker fix applied')
