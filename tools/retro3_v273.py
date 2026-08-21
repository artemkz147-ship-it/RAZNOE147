from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v273 patch missing: {label}')
    return text.replace(old, new, 1)

# v2.7.3 only restores the complete offline EmulatorJS payload that was accidentally
# omitted from the v2.7.2 build. Functional code from v2.7.2 remains unchanged.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 13', 'versionCode 14', 'version code')
t = must_replace(t, "versionName '2.7.2'", "versionName '2.7.3'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.7.3 complete offline payload fix applied')
