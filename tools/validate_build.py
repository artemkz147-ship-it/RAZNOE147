from pathlib import Path
import re
root=Path('dist')
assets=[
 'floor.glb','tree.glb','dead-tree.glb','bush.glb','rock.glb','grass.glb','grass-short.glb','snow-rock.glb','pine-snow.glb','birch.glb','birch-autumn.glb','willow.glb','cactus.glb','palm.glb','moss-rock.glb','gem.glb','arch.glb','column.glb','chest.glb','torch.glb','fire.glb','flower.glb','bomb.glb','arrow.glb','bow.glb','star.glb','cloud.glb','snow.glb','bee.glb','mini-robot.glb','food-donut.glb','food-watermelon.glb','food-pancake.glb','food-pumpkin.glb','food-cookie.glb','house.glb','wagon.glb','fence.glb','well.glb','bridge.glb','stall.glb','barrel.glb','tent.glb','campfire.glb','raft.glb','sign.glb','bench.glb','crate2.glb',
 *[f'hero-{i:02}.glb' for i in range(1,16)],*[f'weapon-{i:02}.glb' for i in range(1,16)],*[f'monster-{i:02}.glb' for i in range(1,13)]
]
portraits=[root/'portraits'/f'hero-{i:02}.png' for i in range(1,16)]+[root/'portraits'/f'weapon-{i:02}.png' for i in range(1,16)]
required=[root/'index.html',*[root/'assets'/n for n in assets],*portraits]
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
map_match=re.search(r"const maps\s*=\s*\[(.*?)\n\];",content,re.S)
if not map_match:
    raise SystemExit('Map definition table not found')
map_ids=re.findall(r"^\s*\['([^']+)'",map_match.group(1),re.M)
boss_match=re.search(r"export const BOSSES\s*=\s*\[(.*?)\n\]\.map",content,re.S)
boss_names=re.findall(r"'([^']+)'",boss_match.group(1)) if boss_match else []
checks=[
    ('Expected 15 heroes', content.count("asset:'hero")>=15),
    ('Expected 15 weapon models', content.count("model:'weapon")>=15),
    ('Expected 18 unique maps', len(map_ids)==18 and len(set(map_ids))==18),
    ('Expected 24 enemy definitions', 'Array.from({length:24}' in content),
    ('Expected 18 bosses', len(boss_names)==18 and len(set(boss_names))==18),
    ('Expected expanded daily quests', content.count("id:'daily_")>=9),
    ('Expected expanded career quests', content.count("id:'career_")>=24),
]
for message,ok in checks:
    if not ok: raise SystemExit(message)
print(f'Expanded content counts validated ({len(map_ids)} maps, {len(boss_names)} bosses).')

build_script=Path('tools/build_assets.sh').read_text(encoding='utf-8')
if 'ultimate_gun_pack' in build_script or '$ROOT/guns' in build_script: raise SystemExit('Modern gun asset pack must not ship in v3')
if len(list((root/'portraits').glob('hero-*.png')))!=15 or len(list((root/'portraits').glob('weapon-*.png')))!=15: raise SystemExit('Expected 15 hero and 15 weapon portraits')
print('V3.1 art validation passed: thematic weapons, portraits, expanded environment.')
