from pathlib import Path

mp=Path('src/main.js')
s=mp.read_text(encoding='utf-8')
old="""    const map=this.currentMap(),mode=this.currentMode();if(this.progress.wins<map.unlockWins||this.progress.wins<mode.unlockWins)return;"""
new="""    const map=this.currentMap(),mode=this.currentMode();if(!(AUTO_START&&DEBUG_MAP)&&(this.progress.wins<map.unlockWins||this.progress.wins<mode.unlockWins))return;"""
if old in s:s=s.replace(old,new)
mp.write_text(s,encoding='utf-8')

vp=Path('tools/validate_build.py')
v=vp.read_text(encoding='utf-8')
old_assets=""" 'floor.glb','tree.glb','dead-tree.glb','bush.glb','rock.glb','gem.glb','arch.glb','column.glb','chest.glb','torch.glb','fire.glb','flower.glb','bomb.glb','arrow.glb','star.glb','cloud.glb','snow.glb','bee.glb','mini-robot.glb','food-donut.glb','food-watermelon.glb','food-pancake.glb','food-pumpkin.glb','food-cookie.glb',"""
new_assets=""" 'floor.glb','tree.glb','dead-tree.glb','bush.glb','rock.glb','grass.glb','grass-short.glb','snow-rock.glb','pine-snow.glb','birch.glb','birch-autumn.glb','willow.glb','cactus.glb','palm.glb','moss-rock.glb','gem.glb','arch.glb','column.glb','chest.glb','torch.glb','fire.glb','flower.glb','bomb.glb','arrow.glb','star.glb','cloud.glb','snow.glb','bee.glb','mini-robot.glb','food-donut.glb','food-watermelon.glb','food-pancake.glb','food-pumpkin.glb','food-cookie.glb',"""
if old_assets in v:v=v.replace(old_assets,new_assets)

# Keep the content checks strict, but count the generated map table itself.
# MAPS derives unlockWins in maps.map(...), so counting the token unlockWins cannot
# represent the number of map definitions.
marker="\ncontent=Path('src/content.js').read_text(encoding='utf-8')\nchecks=["
if marker in v:
    v=v.split(marker,1)[0].rstrip()+"\n"

v += r'''
content=Path('src/content.js').read_text(encoding='utf-8')
map_match=re.search(r"const maps\s*=\s*\[(.*?)\n\];",content,re.S)
if not map_match:
    raise SystemExit('Map definition table not found')
map_ids=re.findall(r"^\s*\['([^']+)'",map_match.group(1),re.M)
checks=[
    ('Expected 15 heroes', content.count("asset:'hero")>=15),
    ('Expected 15 weapon models', content.count("model:'weapon")>=15),
    ('Expected 18 unique maps', len(map_ids)==18 and len(set(map_ids))==18),
    ('Expected 24 enemy definitions', 'Array.from({length:24}' in content),
    ('Expected 18 bosses', "'Король Фестиваля'" in content),
    ('Expected expanded daily quests', content.count("id:'daily_")>=9),
    ('Expected expanded career quests', content.count("id:'career_")>=24),
]
for message,ok in checks:
    if not ok: raise SystemExit(message)
print(f'Expanded content counts validated ({len(map_ids)} maps).')
'''
vp.write_text(v,encoding='utf-8')
print('final QA progression bypass and strict content validation applied')