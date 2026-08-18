from pathlib import Path
import re
root=Path('dist')
assets=[
 'floor.glb','tree.glb','dead-tree.glb','bush.glb','rock.glb','grass.glb','grass-short.glb','snow-rock.glb','pine-snow.glb','birch.glb','birch-autumn.glb','willow.glb','cactus.glb','palm.glb','moss-rock.glb','gem.glb','arch.glb','column.glb','chest.glb','torch.glb','fire.glb','flower.glb','bomb.glb','arrow.glb','star.glb','cloud.glb','snow.glb','bee.glb','mini-robot.glb','food-donut.glb','food-watermelon.glb','food-pancake.glb','food-pumpkin.glb','food-cookie.glb',
 *[f'hero-{i:02}.glb' for i in range(1,16)],*[f'weapon-{i:02}.glb' for i in range(1,16)],*[f'monster-{i:02}.glb' for i in range(1,13)]
]
required=[root/'index.html',*[root/'assets'/n for n in assets]]
missing=[str(p) for p in required if not p.is_file() or p.stat().st_size==0]
if missing: raise SystemExit('Missing build files: '+', '.join(missing))
size=sum(p.stat().st_size for p in root.rglob('*') if p.is_file())
print(f'Unpacked build size: {size/1024/1024:.2f} MiB')
if size>100*1024*1024: raise SystemExit('Yandex unpacked build exceeds 100 MiB')
bad=[]
for p in root.rglob('*'):
    if not p.is_file(): continue
    rel=p.relative_to(root).as_posix()
    if ' ' in rel or re.search(r'[А-Яа-яЁё]',rel): bad.append(rel)
if bad: raise SystemExit('Unsupported filename(s): '+', '.join(bad))
html=(root/'index.html').read_text(encoding='utf-8')
for needle in ['/sdk.js','type="module"']:
    if needle not in html: raise SystemExit('index marker missing: '+needle)
source=(Path('src/main.js').read_text(encoding='utf-8')+Path('src/content.js').read_text(encoding='utf-8'))
for forbidden in ['BoxGeometry(', 'SphereGeometry(', 'PlaneGeometry(', 'CylinderGeometry(', 'ConeGeometry(', 'TorusGeometry(']:
    if forbidden in source: raise SystemExit('Procedural visible geometry forbidden: '+forbidden)
for marker in ['HEROES','MAPS','WEAPONS','ENEMIES','BOSSES']:
    if marker not in source: raise SystemExit('Content marker missing: '+marker)
print(f'Validated {len(assets)} imported GLB assets; no procedural gameplay geometry.')

content=Path('src/content.js').read_text(encoding='utf-8')
checks=[
    ('Expected 15 heroes', content.count("asset:'hero")>=15),
    ('Expected 15 weapon models', content.count("model:'weapon")>=15),
    ('Expected 18 maps', content.count('unlockWins:')>=18),
    ('Expected 24 enemy definitions', 'Array.from({length:24}' in content),
    ('Expected 18 bosses', "'Король Фестиваля'" in content),
    ('Expected expanded daily quests', content.count("id:'daily_")>=9),
    ('Expected expanded career quests', content.count("id:'career_")>=24),
]
for message,ok in checks:
    if not ok: raise SystemExit(message)
print('Expanded content counts validated.')
