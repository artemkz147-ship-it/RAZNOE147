from pathlib import Path
import re
import sys

root = Path('dist')
required = [
    root / 'index.html',
    root / 'assets' / 'hero.glb',
    root / 'assets' / 'orc.glb',
    root / 'assets' / 'demon.glb',
    root / 'assets' / 'tree.glb',
    root / 'assets' / 'rock.glb',
    root / 'assets' / 'gem.glb',
    root / 'assets' / 'arena.glb',
]
missing = [str(p) for p in required if not p.is_file()]
if missing:
    raise SystemExit('Missing required build files: ' + ', '.join(missing))

size = sum(p.stat().st_size for p in root.rglob('*') if p.is_file())
limit = 100 * 1024 * 1024
print(f'Unpacked build size: {size / 1024 / 1024:.2f} MiB')
if size > limit:
    raise SystemExit('Yandex Games unpacked build exceeds 100 MiB')

bad = []
for p in root.rglob('*'):
    if not p.is_file():
        continue
    rel = p.relative_to(root).as_posix()
    if ' ' in rel or re.search(r'[А-Яа-яЁё]', rel):
        bad.append(rel)
if bad:
    raise SystemExit('Unsupported filename(s): ' + ', '.join(bad))

html = (root / 'index.html').read_text(encoding='utf-8')
if '/sdk.js' not in html:
    raise SystemExit('Yandex SDK script is not present in index.html')

print('Build validation passed.')
