from pathlib import Path
import re

root = Path('dist')
asset_names = [
    'hero.glb','sword.glb','skeleton.glb','slime.glb','bat.glb','dragon.glb',
    'tree.glb','dead-tree.glb','bush.glb','rock.glb','gem.glb','floor.glb',
    'arch.glb','column.glb','chest.glb','torch.glb','fire.glb'
]
required = [root / 'index.html', *[root / 'assets' / n for n in asset_names]]
missing = [str(p) for p in required if not p.is_file() or p.stat().st_size == 0]
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
for needle in ['/sdk.js','type="module"']:
    if needle not in html:
        raise SystemExit(f'Required index.html marker missing: {needle}')

source = Path('src/main.js').read_text(encoding='utf-8')
for forbidden in ['BoxGeometry(', 'SphereGeometry(', 'PlaneGeometry(', 'CylinderGeometry(', 'ConeGeometry(']:
    if forbidden in source:
        raise SystemExit(f'Procedural visible geometry is forbidden in gameplay source: {forbidden}')

print(f'Validated {len(asset_names)} GLB art assets.')
print('Build validation passed.')
