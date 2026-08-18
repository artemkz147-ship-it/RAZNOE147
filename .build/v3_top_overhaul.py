from pathlib import Path
import re

main_p=Path('src/main.js')
content_p=Path('src/content.js')
style_p=Path('src/style.css')
build_p=Path('tools/build_assets.sh')
validate_p=Path('tools/validate_build.py')

m=main_p.read_text(encoding='utf-8')
c=content_p.read_text(encoding='utf-8')
s=style_p.read_text(encoding='utf-8')
b=build_p.read_text(encoding='utf-8')
v=validate_p.read_text(encoding='utf-8')


def need(text,needle,label):
    if needle not in text:
        raise SystemExit(f'v3 patch missing marker: {label}')


def replace_once(text,old,new,label):
    need(text,old,label)
    return text.replace(old,new,1)

# ---------------------------------------------------------------------------
# CONTENT: coherent hero roster, bigger maps, brighter enemy silhouettes,
# thematic held weapons only (no modern guns in fantasy hands).
# ---------------------------------------------------------------------------
hero_lines={
'donut_knight':"  {id:'donut_knight',name:'Сэр Крендель',asset:'hero01',weapon:'donut',cost:0,desc:'Тяжёлый рыцарь-пекарь: крепкий, простой и очень живучий.',bonus:{armor:.12,hp:.08}},",
'dew_fairy':"  {id:'dew_fairy',name:'Эльфа Росинка',asset:'hero02',weapon:'firefly',cost:0,desc:'Лесной стрелок: быстрее набирает уровни и притягивает опыт.',bonus:{xp:.16,pickup:.18}},",
'hamster_pirate':"  {id:'hamster_pirate',name:'Капитан Бомба',asset:'hero03',weapon:'pirate_bomb',cost:0,desc:'Пират-бомбардир: крупные взрывы и повышенная удача.',bonus:{luck:.20,splash:.16}},",
'candy_witch':"  {id:'candy_witch',name:'Конфетная Ведьма',asset:'hero04',weapon:'rainbow_fan',cost:900,desc:'Радужная магия: широкий залп и высокий базовый урон.',bonus:{damage:.12,projectiles:1}},",
'mushroom_druid':"  {id:'mushroom_druid',name:'Друид Цветень',asset:'hero05',weapon:'flower_burst',cost:1250,desc:'Контролирует толпу цветочной магией и замедлением.',bonus:{area:.18,slow:.08}},",
'cat_mage':"  {id:'cat_mage',name:'Янтарный Маг',asset:'hero06',weapon:'amber_comet',cost:1600,desc:'Точные пробивающие выстрелы и высокий темп магии.',bonus:{cooldown:.12,pierce:1}},",
'toy_robot':"  {id:'toy_robot',name:'Меха-Малыш',asset:'hero07',weapon:'blast_bot',cost:1950,desc:'Механический герой с самонаводящимися зарядами и защитой.',bonus:{damageReduction:.10,homing:.18}},",
'baker_alchemist':"  {id:'baker_alchemist',name:'Шеф Алхимик',asset:'hero08',weapon:'pancake',cost:2300,desc:'Возвратные атаки и постоянная регенерация в долгом забеге.',bonus:{duration:.20,regen:.18}},",
'fox_archer':"  {id:'fox_archer',name:'Лисий Следопыт',asset:'hero09',weapon:'fox_bow',cost:2650,desc:'Рейнджер с быстрыми критическими стрелами.',bonus:{speed:.12,crit:.08}},",
'cloud_princess':"  {id:'cloud_princess',name:'Принцесса Облаков',asset:'hero10',weapon:'cloud_orb',cost:3000,desc:'Лёгкая магия облаков: быстрые заряды и шанс уклонения.',bonus:{projectileSpeed:.16,dodge:.08}},",
'watermelon_captain':"  {id:'watermelon_captain',name:'Арбузный Корсар',asset:'hero11',weapon:'watermelon',cost:3350,desc:'Тяжёлые арбузные снаряды разбрасывают большие группы.',bonus:{knockback:.22,splash:.18}},",
'music_gnome':"  {id:'music_gnome',name:'Гном-Громовержец',asset:'hero12',weapon:'music_wave',cost:3700,desc:'Громовые волны пробивают плотную толпу вокруг героя.',bonus:{area:.12,cooldown:.08}},",
'lightning_bee':"  {id:'lightning_bee',name:'Ниндзя Искра',asset:'hero13',weapon:'smart_bee',cost:4050,desc:'Самый быстрый герой: рой сам ищет цели.',bonus:{speed:.15,cooldown:.08}},",
'snow_penguin':"  {id:'snow_penguin',name:'Ледяной Медик',asset:'hero14',weapon:'snowball',cost:4400,desc:'Морозит врагов и медленно восстанавливает здоровье.',bonus:{regen:.35,slow:.14}},",
'pumpkin_jester':"  {id:'pumpkin_jester',name:'Тыквенный Гоблин',asset:'hero15',weapon:'pumpkin',cost:4750,desc:'Трикстер с минами и цепными тыквенными взрывами.',bonus:{luck:.12,splash:.12}},",
}
for hid,line in hero_lines.items():
    c,n=re.subn(rf"^\s*\{{id:'{re.escape(hid)}'.*$",line,c,count=1,flags=re.M)
    if n!=1: raise SystemExit(f'hero patch failed: {hid}')

weapon_lines={
'donut':"  {id:'donut',name:'Пекарский Жезл',evolution:'Планетарная Пекарня',model:'weapon01',projectile:'food_donut',type:'shot',damage:17,cooldown:.68,speed:19,count:3,spread:.12,pierce:1,range:29,scale:.34,passive:'area',fx:0xffcf6b},",
'firefly':"  {id:'firefly',name:'Фонарь Светлячков',evolution:'Рой Зарниц',model:'weapon02',projectile:'gem',type:'chain',damage:15,cooldown:.64,speed:23,count:1,chain:4,chainRadius:6.4,range:30,scale:.22,passive:'cooldown',fx:0x8effc8},",
'pirate_bomb':"  {id:'pirate_bomb',name:'Пороховая Мортира',evolution:'Пороховой Шторм',model:'weapon03',projectile:'bomb',type:'bomb',damage:32,cooldown:1.18,speed:14,count:1,splash:4.2,range:28,scale:.38,passive:'area',fx:0xff7b48},",
'rainbow_fan':"  {id:'rainbow_fan',name:'Радужный Жезл',evolution:'Семицветная Буря',model:'weapon04',projectile:'star',type:'fan',damage:12,cooldown:.70,speed:21,count:7,spread:.15,pierce:1,range:27,scale:.20,passive:'projectiles',fx:0xff78dc},",
'flower_burst':"  {id:'flower_burst',name:'Цветочный Посох',evolution:'Цветочная Корона',model:'weapon05',projectile:'flower',type:'fan',damage:14,cooldown:.70,speed:18,count:5,spread:.21,slow:.18,range:25,scale:.30,passive:'area',fx:0xff8fb1},",
'amber_comet':"  {id:'amber_comet',name:'Янтарный Скипетр',evolution:'Янтарная Сверхновая',model:'weapon06',projectile:'gem',type:'shot',damage:28,cooldown:.78,speed:28,count:1,pierce:4,range:34,scale:.30,passive:'power',fx:0xffb54a},",
'blast_bot':"  {id:'blast_bot',name:'Механический Пускатель',evolution:'Механический Карнавал',model:'weapon07',projectile:'mini_robot',type:'homing',damage:23,cooldown:.98,speed:14,count:2,homing:7,splash:2.7,range:31,scale:.30,passive:'cooldown',fx:0x73d9ff},",
'pancake':"  {id:'pancake',name:'Сковорода-Бумеранг',evolution:'Бесконечная Кухня',model:'weapon08',projectile:'food_pancake',type:'boomerang',damage:19,cooldown:.96,speed:19,count:2,pierce:6,range:20,scale:.36,passive:'duration',fx:0xffd78e},",
'fox_bow':"  {id:'fox_bow',name:'Лисий Лук',evolution:'Ливень Лисьих Стрел',model:'weapon09',projectile:'arrow',type:'shot',damage:22,cooldown:.62,speed:32,count:1,pierce:2,crit:.14,range:37,scale:.31,passive:'crit',fx:0xffa24c},",
'cloud_orb':"  {id:'cloud_orb',name:'Облачный Посох',evolution:'Грозовой Спутник',model:'weapon10',projectile:'cloud',type:'homing',damage:17,cooldown:.58,speed:17,count:3,homing:9,pierce:1,range:30,scale:.31,passive:'speed',fx:0xb9e9ff},",
'watermelon':"  {id:'watermelon',name:'Арбузная Рогатка',evolution:'Гигантский Арбуз',model:'weapon11',projectile:'food_watermelon',type:'bomb',damage:38,cooldown:1.24,speed:13,count:1,splash:4.8,knockback:2.8,range:28,scale:.42,passive:'area',fx:0x7dff78},",
'music_wave':"  {id:'music_wave',name:'Камертон Бури',evolution:'Громовой Концерт',model:'weapon12',projectile:'star',type:'radial',damage:13,cooldown:1.02,speed:19,count:10,pierce:2,range:22,scale:.20,passive:'projectiles',fx:0x8e8cff},",
'smart_bee':"  {id:'smart_bee',name:'Жезл Роя',evolution:'Королевский Рой',model:'weapon13',projectile:'bee',type:'homing',damage:15,cooldown:.42,speed:20,count:3,homing:11,range:29,scale:.25,passive:'cooldown',fx:0xffe45d},",
'snowball':"  {id:'snowball',name:'Морозный Посох',evolution:'Полярная Буря',model:'weapon14',projectile:'snow',type:'fan',damage:15,cooldown:.70,speed:18,count:4,spread:.15,slow:.34,range:26,scale:.28,passive:'duration',fx:0x9feaff},",
'pumpkin':"  {id:'pumpkin',name:'Тыквенный Фонарь',evolution:'Тыквенный Лабиринт',model:'weapon15',projectile:'food_pumpkin',type:'mine',damage:31,cooldown:1.05,speed:13,count:2,splash:3.7,range:23,scale:.38,passive:'area',fx:0xff873f},",
}
for wid,line in weapon_lines.items():
    c,n=re.subn(rf"^\s*\{{id:'{re.escape(wid)}'.*$",line,c,count=1,flags=re.M)
    if n!=1: raise SystemExit(f'weapon patch failed: {wid}')

c=replace_once(c,"width:112+(i%3)*8,height:92+(i%4)*7","width:148+(i%3)*10,height:118+(i%4)*9",'map dimensions')

old_enemy=re.search(r"const behaviors=\['chase'.*?\n\}\)\);\n\nexport const BOSSES",c,re.S)
if not old_enemy: raise SystemExit('enemy generator block not found')
new_enemy="""const behaviors=['chase','zigzag','dash','ranged','split','charger','sniper','swarm','shield','healer','bomber','orbit'];
const enemyKinds=['Скелет','Дракончик','Летучая мышь','Слизень','Гриб','Пчела','Краб','Пингвин','Кактус','Призрак','Демон','Пришелец'];
const enemyTitles=['Шустрый','Сияющий','Дикий','Прыгучий','Заводной','Ловкий','Колючий','Хитрый'];
const enemyColors=[0xfff2d0,0xffa17c,0xc9b8ff,0x8ff2a1,0xffd86e,0x79dcff,0xff91c8,0xb9f7ff];
export const ENEMIES = Array.from({length:24},(_,i)=>({
  id:`enemy_${String(i+1).padStart(2,'0')}`,name:`${enemyTitles[i%enemyTitles.length]} ${enemyKinds[i%enemyKinds.length]}`,
  asset:`monster${String((i%12)+1).padStart(2,'0')}`,behavior:behaviors[i%behaviors.length],hp:44+i*8,speed:2.0+(i%6)*.31,damage:7+Math.floor(i/4),xp:1+Math.floor(i/8),scale:.96+(i%4)*.09,
  color:enemyColors[i%enemyColors.length]
}));

export const BOSSES"""
c=c[:old_enemy.start()]+new_enemy+c[old_enemy.end():]

asset_marker="food_cookie:'./assets/food-cookie.glb'"
need(c,asset_marker,'asset url tail')
c=c.replace(asset_marker,asset_marker+",house:'./assets/house.glb',wagon:'./assets/wagon.glb',fence:'./assets/fence.glb',well:'./assets/well.glb',bridge:'./assets/bridge.glb',stall:'./assets/stall.glb',barrel:'./assets/barrel.glb',tent:'./assets/tent.glb',campfire:'./assets/campfire.glb',raft:'./assets/raft.glb',sign:'./assets/sign.glb',bench:'./assets/bench.glb',crate2:'./assets/crate2.glb'",1)

held="""export const HELD_PROFILES={
 donut:['weapon01',.54,'spell',[.06,-.12,-.03],[0,.12,.08],'low'],
 firefly:['weapon02',.34,'spell',[.06,-.02,-.03],[0,.18,.05],'center'],
 pirate_bomb:['weapon03',.42,'throw',[.07,-.10,-.03],[0,.16,.16],'low'],
 rainbow_fan:['weapon04',.42,'spell',[.06,-.09,-.03],[0,.12,.08],'low'],
 flower_burst:['weapon05',.58,'spell',[.07,-.16,-.04],[0,.10,.10],'low'],
 amber_comet:['weapon06',.50,'spell',[.07,-.13,-.04],[0,.10,.08],'low'],
 blast_bot:['weapon07',.43,'shoot',[.04,-.09,-.04],[Math.PI/2,0,.06],'low'],
 pancake:['weapon08',.42,'throw',[.06,-.10,-.02],[0,.12,.18],'low'],
 fox_bow:['weapon09',.62,'shoot',[.03,0,-.02],[Math.PI/2,0,0],'center'],
 cloud_orb:['weapon10',.50,'spell',[.07,-.12,-.04],[0,.08,.08],'low'],
 watermelon:['weapon11',.43,'shoot',[.05,-.08,-.03],[Math.PI/2,0,.08],'low'],
 music_wave:['weapon12',.48,'spell',[.06,-.11,-.03],[0,.10,.12],'low'],
 smart_bee:['weapon13',.38,'spell',[.06,-.09,-.03],[0,.12,.12],'low'],
 snowball:['weapon14',.56,'spell',[.07,-.15,-.03],[0,.10,.08],'low'],
 pumpkin:['weapon15',.40,'spell',[.06,-.08,-.03],[0,.14,.10],'center']
};"""
c,n=re.subn(r"export const HELD_PROFILES=\{.*?\};",held,c,count=1,flags=re.S)
if n!=1: raise SystemExit('held profiles block not found')

# ---------------------------------------------------------------------------
# ASSET PIPELINE: remove modern gun pack, add classic monsters, village and
# survival environment. Use RPG/medieval/survival props as visible weapons.
# ---------------------------------------------------------------------------
b=b.replace("fetch_zip 'https://opengameart.org/sites/default/files/ultimate_gun_pack_by_quaternius.zip' guns\n","")
if "classic_monsters" not in b:
    anchor="fetch_zip 'https://opengameart.org/sites/default/files/cute_animated_monsters_-_aug_2020.zip' cute_monsters\n"
    need(b,anchor,'cute monsters fetch')
    b=b.replace(anchor,"fetch_zip 'https://opengameart.org/sites/default/files/Animated%20Monster%20Pack%20by%20%40Quaternius.zip' classic_monsters\n"+anchor+"fetch_zip 'https://opengameart.org/sites/default/files/medieval_village_pack_-_dec_2020.zip' village\nfetch_zip 'https://opengameart.org/sites/default/files/survival_pack_-_sept_2020.zip' survival\n",1)

b=b.replace('hero_keywords=("knight" "elf" "cowboy" "witch" "druid" "wizard" "robot" "chef" "ranger" "princess" "pirate" "viking" "ninja" "medic" "goblin")','hero_keywords=("knight" "elf" "pirate" "witch" "druid" "wizard" "robot" "chef" "ranger" "princess" "pirate" "viking" "ninja" "medic" "goblin")')

weapon_block=re.search(r': > "\$ROOT/used-weapons\.txt".*?(?=: > "\$ROOT/used-monsters\.txt")',b,re.S)
if not weapon_block: raise SystemExit('weapon build block not found')
new_weapon_block=r''': > "$ROOT/used-weapons.txt"
declare -a weapon_src
weapon_src[1]="$(pick_unique "$ROOT/rpg" "$ROOT/used-weapons.txt" 'Staff' 'Scepter' 'Wand')"
weapon_src[2]="$(pick_unique "$ROOT/rpg" "$ROOT/used-weapons.txt" 'Lantern' 'Torch' 'Potion')"
weapon_src[3]="$(pick_unique "$ROOT/rpg" "$ROOT/used-weapons.txt" 'Bomb' 'Mace' 'Potion')"
weapon_src[4]="$(pick_unique "$ROOT/rpg" "$ROOT/used-weapons.txt" 'Book' 'Scroll' 'Wand')"
weapon_src[5]="$(pick_unique "$ROOT/rpg" "$ROOT/used-weapons.txt" 'Staff' 'Wand' 'Spear')"
weapon_src[6]="$(pick_unique "$ROOT/rpg" "$ROOT/used-weapons.txt" 'Crystal' 'Gem' 'Scepter')"
weapon_src[7]="$(pick_unique "$ROOT/survival" "$ROOT/used-weapons.txt" 'Wrench' 'Hammer' 'Pickaxe' 'Axe')"
weapon_src[8]="$(pick_unique "$ROOT/survival" "$ROOT/used-weapons.txt" 'Pan' 'Pot' 'Shovel' 'Axe')"
weapon_src[9]="$(pick_unique "$ROOT/medieval" "$ROOT/used-weapons.txt" 'Bow' 'Crossbow' 'Longbow')"
weapon_src[10]="$(pick_unique "$ROOT/rpg" "$ROOT/used-weapons.txt" 'Wand' 'Staff' 'Scepter')"
weapon_src[11]="$(pick_unique "$ROOT/medieval" "$ROOT/used-weapons.txt" 'Crossbow' 'Mace' 'Hammer')"
weapon_src[12]="$(pick_unique "$ROOT/medieval" "$ROOT/used-weapons.txt" 'Mace' 'Hammer' 'Spear')"
weapon_src[13]="$(pick_unique "$ROOT/rpg" "$ROOT/used-weapons.txt" 'Dagger' 'Wand' 'Scepter')"
weapon_src[14]="$(pick_unique "$ROOT/rpg" "$ROOT/used-weapons.txt" 'Staff' 'Spear' 'Wand')"
weapon_src[15]="$(pick_unique "$ROOT/rpg" "$ROOT/used-weapons.txt" 'Lantern' 'Torch' 'Axe')"
for i in $(seq 1 15); do
  convert "${weapon_src[$i]}" "weapon-$(printf '%02d' "$i").glb" static
done

'''
b=b[:weapon_block.start()]+new_weapon_block+b[weapon_block.end():]

monster_block=re.search(r': > "\$ROOT/used-monsters\.txt".*?done\n\n# Environment pieces',b,re.S)
if not monster_block: raise SystemExit('monster build block not found')
new_monster=r''': > "$ROOT/used-monsters.txt"
classic_keywords=("skeleton" "dragon" "bat" "slime")
for i in $(seq 1 4); do
  kw="${classic_keywords[$((i-1))]}"; src="$(pick_unique "$ROOT/classic_monsters" "$ROOT/used-monsters.txt" "$kw")"
  convert "$src" "monster-$(printf '%02d' "$i").glb" animated
done
cute_keywords=("mushroom" "bee" "crab" "penguin" "cactus" "ghost" "demon" "alien")
for j in $(seq 1 8); do
  i=$((j+4)); kw="${cute_keywords[$((j-1))]}"; src="$(pick_unique "$ROOT/cute_monsters" "$ROOT/used-monsters.txt" "$kw")"
  convert "$src" "monster-$(printf '%02d' "$i").glb" animated
done

# Environment pieces'''
b=b[:monster_block.start()]+new_monster+b[monster_block.end():]

env_anchor='MINIROBOT="$(pick "$ROOT/characters" \'Robot\' \'Mech\' \'Knight\')"\n'
need(b,env_anchor,'environment variable anchor')
if 'HOUSE="$(pick "$ROOT/village"' not in b:
    b=b.replace(env_anchor,env_anchor+'''HOUSE="$(pick "$ROOT/village" 'House' 'Cabin' 'Building')"\nWAGON="$(pick "$ROOT/village" 'Wagon' 'Cart')"\nFENCE="$(pick "$ROOT/village" 'Fence' 'Wall')"\nWELL="$(pick "$ROOT/village" 'Well' 'Fountain')"\nBRIDGE="$(pick "$ROOT/village" 'Bridge' 'Stairs')"\nSTALL="$(pick "$ROOT/village" 'Market' 'Stall' 'Shop')"\nBARREL="$(pick "$ROOT/village" 'Barrel' 'Crate' 'Box')"\nSIGN="$(pick "$ROOT/village" 'Sign' 'Board' 'Fence')"\nBENCH="$(pick "$ROOT/village" 'Bench' 'Table' 'Chair')"\nCRATE2="$(pick "$ROOT/village" 'Crate' 'Box' 'Barrel')"\nTENT="$(pick "$ROOT/survival" 'Tent' 'Shelter' 'Camp')"\nCAMPFIRE="$(pick "$ROOT/survival" 'Campfire' 'Fire' 'Torch')"\nRAFT="$(pick "$ROOT/survival" 'Raft' 'Boat' 'Canoe')"\n''',1)

convert_anchor='convert "$MINIROBOT" mini-robot.glb static\n'
need(b,convert_anchor,'convert anchor')
if 'convert "$HOUSE" house.glb static' not in b:
    b=b.replace(convert_anchor,convert_anchor+'''convert "$HOUSE" house.glb static\nconvert "$WAGON" wagon.glb static\nconvert "$FENCE" fence.glb static\nconvert "$WELL" well.glb static\nconvert "$BRIDGE" bridge.glb static\nconvert "$STALL" stall.glb static\nconvert "$BARREL" barrel.glb static\nconvert "$TENT" tent.glb static\nconvert "$CAMPFIRE" campfire.glb static\nconvert "$RAFT" raft.glb static\nconvert "$SIGN" sign.glb static\nconvert "$BENCH" bench.glb static\nconvert "$CRATE2" crate2.glb static\n''',1)

if 'tools/render_portraits.py' not in b:
    b=b.replace("python3 tools/check_glb_animations.py","python3 tools/check_glb_animations.py\nrm -rf public/portraits && mkdir -p public/portraits\nblender -b --factory-startup --python tools/render_portraits.py -- \"$OUT\" public/portraits")

# ---------------------------------------------------------------------------
# RUNTIME VISUALS / UI / COMBAT FEEDBACK
# ---------------------------------------------------------------------------
m=m.replace("this.scene=new THREE.Scene(); this.camera=new THREE.PerspectiveCamera(48,1,.1,240); this.camera.position.set(0,9.4,11.2);","this.scene=new THREE.Scene(); this.camera=new THREE.PerspectiveCamera(44,1,.1,320); this.camera.position.set(0,9.4,11.2);")
m=m.replace("this.renderer.outputColorSpace=THREE.SRGBColorSpace; this.renderer.toneMapping=THREE.ACESFilmicToneMapping; this.renderer.toneMappingExposure=1.08;","this.renderer.outputColorSpace=THREE.SRGBColorSpace; this.renderer.toneMapping=THREE.ACESFilmicToneMapping; this.renderer.toneMappingExposure=1.16;")

lights_old="""  setupLights(){
    this.hemi=new THREE.HemisphereLight(0xcfefff,0x344227,1.45); this.scene.add(this.hemi);
    this.sun=new THREE.DirectionalLight(0xffe9bd,2.4); this.sun.position.set(-14,22,10); this.sun.castShadow=true;
    this.sun.shadow.mapSize.set(1024,1024); this.sun.shadow.camera.left=-32;this.sun.shadow.camera.right=32;this.sun.shadow.camera.top=32;this.sun.shadow.camera.bottom=-32; this.scene.add(this.sun);
    this.rim=new THREE.DirectionalLight(0x7fc7ff,.75); this.rim.position.set(12,8,-10);this.scene.add(this.rim);
  }"""
lights_new="""  setupLights(){
    this.hemi=new THREE.HemisphereLight(0xdaf4ff,0x31472e,1.12); this.scene.add(this.hemi);
    this.sun=new THREE.DirectionalLight(0xffe1ad,3.05); this.sun.position.set(-18,25,12); this.sun.castShadow=true;
    this.sun.shadow.mapSize.set(2048,2048); this.sun.shadow.camera.left=-38;this.sun.shadow.camera.right=38;this.sun.shadow.camera.top=38;this.sun.shadow.camera.bottom=-38;this.sun.shadow.bias=-.00025; this.scene.add(this.sun);
    this.rim=new THREE.DirectionalLight(0x79bdff,1.08); this.rim.position.set(15,10,-12);this.scene.add(this.rim);
  }"""
m=replace_once(m,lights_old,lights_new,'lighting')

mat_old="if(tint&&m.color)m.color.lerp(new THREE.Color(tint),.35);if('roughness'in m)m.roughness=Math.max(.45,m.roughness??.6);"
mat_new="if(m.color){if(tint)m.color.lerp(new THREE.Color(tint),.18);m.color.offsetHSL(0,0,.035);}if('roughness'in m)m.roughness=Math.max(.48,m.roughness??.62);if('metalness'in m)m.metalness=Math.min(.28,m.metalness??0);if(tint&&m.emissive){m.emissive.copy(new THREE.Color(tint));m.emissive.multiplyScalar(.055);m.emissiveIntensity=.22;}"
m=replace_once(m,mat_old,mat_new,'material polish')

held_old=re.search(r"  heldProfile\(w\).*?  projectileOrigin\(a\).*?\n\n",m,re.S)
if not held_old: raise SystemExit('held weapon runtime block not found')
held_new="""  heldProfile(w){const p=HELD_PROFILES[w?.id]||[w?.model||w?.projectile||'gem',.40,'spell'];return{asset:p[0],height:p[1],attack:p[2],pos:p[3]||[.06,-.08,-.03],rot:p[4]||[0,.1,.08],grip:p[5]||'center'};}
  playWeaponAttack(a,w){if(!a?.mixer||!a.gltf)return;const k=this.heldProfile(w).attack,t=k==='throw'?['throw','attack','punch','shoot']:k==='shoot'?['shoot','rifle','attack','throw','punch']:['spell','cast','attack','punch','shoot'];const clip=this.findClip(a.gltf,t);if(!clip)return;const n=a.mixer.clipAction(clip);n.reset();n.enabled=true;n.setLoop(THREE.LoopOnce,1);n.clampWhenFinished=true;n.fadeIn(.03).play();a.action?.fadeOut(.03);a.action=n;a.kind='attack';}
  prepareHeldWeapon(gltf,targetHeight,grip='center'){const root=this.cloneVisual(gltf,targetHeight,{shadow:true});const model=root.userData.model;model.updateMatrixWorld(true);const b=new THREE.Box3().setFromObject(model),c=new THREE.Vector3();b.getCenter(c);if(grip==='low')c.y=b.min.y+(b.max.y-b.min.y)*.18;else if(grip==='high')c.y=b.min.y+(b.max.y-b.min.y)*.78;model.position.sub(c);return root;}
  attachHeldWeapon(actor,w){actor.root.userData.weapon?.removeFromParent();const p=this.heldProfile(w),src=this.assets[p.asset]||this.assets[w.model]||this.assets[w.projectile]||this.assets.gem,weapon=this.prepareHeldWeapon(src,p.height,p.grip),hand=this.findRightHand(actor.root);if(hand){hand.add(weapon);weapon.position.fromArray(p.pos);weapon.rotation.set(p.rot[0],p.rot[1],p.rot[2]);}else{actor.root.add(weapon);weapon.position.set(.25,1,.10);}actor.root.userData.weapon=weapon;actor.root.userData.weaponHand=hand||null;return weapon;}
  projectileOrigin(a){const v=new THREE.Vector3(),w=a?.root?.userData?.weapon,h=a?.root?.userData?.weaponHand;if(w){w.updateWorldMatrix(true,false);w.getWorldPosition(v);v.y=Math.max(.72,v.y);return v;}if(h){h.updateWorldMatrix(true,false);h.getWorldPosition(v);v.y=Math.max(.72,v.y);return v;}v.copy(a.root.position);v.y=.95;return v;}

"""
m=m[:held_old.start()]+held_new+m[held_old.end():]

# Portrait helpers and real art cards.
helper_anchor="  currentHero(){return HEROES.find(h=>h.id===this.selectedHero)||HEROES[0];}\n"
need(m,helper_anchor,'current hero helper')
if 'heroPortrait(h)' not in m:
    m=m.replace(helper_anchor,"  heroPortrait(h){const n=String(Number(h.asset.replace(/\\D/g,''))).padStart(2,'0');return `./portraits/hero-${n}.png`;}\n  weaponPortrait(w){const i=Math.max(1,WEAPONS.findIndex(x=>x.id===w.id)+1);return `./portraits/weapon-${String(i).padStart(2,'0')}.png`;}\n"+helper_anchor,1)

hero_render_old=re.search(r"  renderHeroes\(\)\{.*?\n  \}\n  renderMaps",m,re.S)
if not hero_render_old: raise SystemExit('renderHeroes not found')
hero_render_new="""  renderHeroes(){
    $('#hero-coins').textContent=Math.floor(this.progress.coins);$('#hero-grid').innerHTML=HEROES.map(h=>{const unlocked=this.progress.unlockedHeroes.includes(h.id),sel=h.id===this.selectedHero,w=weaponById(h.weapon);return `<button class="hero-card ${sel?'selected':''} ${unlocked?'':'locked'}" data-hero="${h.id}"><div class="hero-art"><img src="${this.heroPortrait(h)}" alt=""><span>${esc(w.name)}</span></div><div class="hero-copy"><strong>${esc(h.name)}</strong><p>${esc(h.desc)}</p><b>${unlocked?(sel?'ВЫБРАН':'ВЫБРАТЬ'):`★ ${h.cost}`}</b></div></button>`;}).join('');
    $$('#hero-grid [data-hero]').forEach(b=>b.onclick=()=>this.selectHero(b.dataset.hero));
  }
  renderMaps"""
m=m[:hero_render_old.start()]+hero_render_new+m[hero_render_old.end():]

collection_old=re.search(r"  renderCollection\(tab\)\{.*?\n  \}\n\n  openOverlay",m,re.S)
if not collection_old: raise SystemExit('renderCollection not found')
collection_new="""  renderCollection(tab){
    let items=[];
    if(tab==='heroes')items=HEROES.map(h=>({name:h.name,sub:weaponById(h.weapon).name,open:this.progress.unlockedHeroes.includes(h.id),art:this.heroPortrait(h)}));
    if(tab==='weapons')items=WEAPONS.map(w=>({name:w.name,sub:`Эволюция: ${w.evolution}`,open:true,art:this.weaponPortrait(w)}));
    if(tab==='maps')items=MAPS.map(m=>({name:m.name,sub:m.desc,open:this.progress.wins>=m.unlockWins}));
    if(tab==='enemies')items=ENEMIES.map(e=>({name:e.name,sub:`Поведение: ${e.behavior}`,open:true}));
    if(tab==='bosses')items=BOSSES.map((b,i)=>({name:b.name,sub:`Стиль: ${b.pattern}`,open:this.progress.wins>=Math.max(0,i-1)}));
    $('#collection-grid').innerHTML=items.map(x=>`<div class="collection-card ${x.open?'':'locked'}">${x.art&&x.open?`<img class="collection-art" src="${x.art}" alt="">`:`<div class="collection-icon">${x.open?'◆':'?'}</div>`}<strong>${x.open?esc(x.name):'???'}</strong><small>${x.open?esc(x.sub):'Ещё не открыто'}</small></div>`).join('');
  }

  openOverlay"""
m=m[:collection_old.start()]+collection_new+m[collection_old.end():]

# V3 set-pieces inserted after macro landmark population.
build_env_old="this.buildMacroClusters(map);this.buildBoundary(map);\n    if(map.hazard)this.buildHazard(map,map.hazard);\n    this.populateMapDecor(map);"
build_env_new="this.buildMacroClusters(map);this.buildV3SetPieces(map);this.buildBoundary(map);\n    if(map.hazard)this.buildHazard(map,map.hazard);\n    this.populateMapDecor(map);"
m=replace_once(m,build_env_old,build_env_new,'environment sequence')

macro_anchor="  buildMacroClusters(map){"
need(m,macro_anchor,'macro method')
setpiece_method="""  buildV3SetPieces(map){
    const sets={
      forest:[['bridge',-.18,.29,2.2,.1],['tent',.24,.31,2.0,-.5],['campfire',.18,.25,1.0,0],['wagon',-.34,-.24,2.3,.6],['fence',.34,-.24,1.4,1.5],['well',-.28,.05,1.8,0]],
      park:[['stall',-.30,-.24,2.3,.3],['bench',.27,-.20,1.2,1.3],['well',.29,.24,1.8,0],['sign',-.32,.20,1.4,.2],['fence',0,.34,1.4,0],['wagon',.06,-.33,2.1,-.2]],
      village:[['house',-.34,-.27,5.0,.25],['house',.34,-.23,4.7,-.4],['wagon',-.18,.31,2.4,.3],['well',.10,.28,2.0,0],['fence',.31,.17,1.5,1.4],['stall',-.31,.12,2.4,.2],['barrel',.20,-.03,1.0,0]],
      snow:[['house',-.32,-.27,4.7,.2],['tent',.29,.25,2.0,-.4],['campfire',.20,.18,1.0,0],['fence',-.30,.22,1.4,.2],['wagon',.31,-.12,2.3,1.2]],
      castle:[['house',-.35,-.25,4.6,.15],['house',.35,-.25,4.6,-.15],['fence',-.34,.18,1.7,1.5],['fence',.34,.18,1.7,1.5],['bench',-.18,.30,1.3,.2],['bench',.18,.30,1.3,-.2],['barrel',.28,.02,1.0,0]],
      beach:[['raft',-.28,.30,2.5,.2],['tent',.25,.27,2.0,-.5],['campfire',.17,.20,1.0,0],['barrel',-.30,-.18,1.0,0],['crate2',-.24,-.23,1.1,.3],['sign',.32,-.12,1.4,.2]],
      moon:[['crate2',-.28,.27,1.2,.2],['sign',.29,-.23,1.5,-.4],['campfire',0,.31,1.0,0]],
      clock:[['bench',-.30,-.24,1.3,.3],['bench',.30,-.24,1.3,-.3],['well',0,.31,2.0,0],['sign',-.31,.17,1.4,.4],['barrel',.31,.17,1.0,0]],
      canyon:[['tent',-.31,-.22,2.0,.2],['campfire',-.22,-.15,1.0,0],['wagon',.31,.22,2.3,-.6],['sign',.26,.29,1.4,0],['barrel',-.33,.23,1.0,0]],
      cave:[['bridge',0,.31,2.2,0],['chest',-.28,-.22,1.3,.2],['barrel',.30,-.22,1.0,0],['campfire',-.22,.25,1.0,0]],
      sky:[['bench',-.28,-.25,1.3,.2],['bench',.28,-.25,1.3,-.2],['well',0,.31,2.0,0],['sign',-.30,.20,1.4,.2]],
      desert:[['tent',-.31,-.24,2.1,.2],['campfire',-.22,-.18,1.0,0],['wagon',.30,.25,2.3,-.5],['well',.22,-.28,1.9,0],['sign',-.32,.19,1.4,.2]],
      swamp:[['bridge',0,.30,2.4,0],['tent',-.29,-.23,2.0,.3],['campfire',-.21,-.16,1.0,0],['barrel',.31,.21,1.0,0],['sign',.29,-.20,1.4,-.2]],
      fair:[['stall',-.32,-.22,2.4,.2],['stall',.32,-.22,2.4,-.2],['wagon',-.28,.25,2.2,.5],['bench',.28,.25,1.3,-.2],['sign',0,.32,1.5,0],['barrel',.20,.02,1.0,0]],
      crystal:[['bridge',-.12,.31,2.2,0],['chest',.28,-.22,1.3,.3],['tent',-.30,-.23,2.0,.2],['campfire',-.22,-.16,1.0,0]],
      rooftop:[['bench',-.30,-.22,1.3,.2],['crate2',.30,-.22,1.2,.2],['barrel',-.28,.24,1.0,0],['sign',.28,.24,1.4,0],['stall',0,.32,2.3,0]],
      festival:[['stall',-.33,-.23,2.4,.2],['stall',.33,-.23,2.4,-.2],['wagon',-.29,.24,2.3,.4],['bench',.29,.24,1.3,-.3],['sign',0,.32,1.5,0],['well',0,-.31,2.0,0],['barrel',.23,.04,1.0,0]]
    };
    const list=sets[map.layout]||sets.forest,hx=map.width*.5,hz=map.height*.5;
    for(const [asset,nx,nz,height,rot] of list){const x=nx*map.width,z=nz*map.height;if(map.hazard){const coord=map.hazard.axis==='x'?x:z;if(Math.abs(coord-map.hazard.at)<map.hazard.width*.85)continue;}const root=this.cloneVisual(this.assets[asset]||this.assets.rock,height,{tint:null});root.position.set(x,0,z);root.rotation.y=rot||0;this.environment.add(root);const solid=!['campfire','sign','barrel','bench'].includes(asset);if(solid)this.obstacles.push({x,z,radius:asset==='house'?1.8:asset==='bridge'?1.25:asset==='wagon'?1.1:.8});}
    // Scenic chains create authored-looking zones instead of isolated props.
    const edgeAsset=['village','castle','fair','festival','rooftop','clock','sky'].includes(map.layout)?'fence':['forest','snow','swamp','beach'].includes(map.layout)?'bush':'rock';
    for(let i=0;i<(this.lowPower?10:18);i++){const side=i%2?-1:1,x=side*(hx*.62+(i%4)*1.45),z=-hz*.08+(i-8)*2.2;if(Math.abs(z)>hz*.63)continue;const root=this.cloneVisual(this.assets[edgeAsset]||this.assets.rock,edgeAsset==='fence'?1.15:edgeAsset==='bush'?.85:1.05,{shadow:true});root.position.set(x,0,z);root.rotation.y=side>0?1.45:-1.45;this.environment.add(root);}
  }

"""
m=m.replace(macro_anchor,setpiece_method+macro_anchor,1)

m=m.replace('const coverCount=this.lowPower?70:128;','const coverCount=this.lowPower?118:238;')
m=m.replace('const propCount=this.lowPower?30:54;','const propCount=this.lowPower?46:86;')
m=m.replace('for(let ring=0;ring<3;ring++){const out=2+ring*5.2;','for(let ring=0;ring<4;ring++){const out=2+ring*4.8;')

# Shared obstacle resolution for player and enemies.
player_obstacles="for(const o of this.obstacles){const dx=p.root.position.x-o.x,dz=p.root.position.z-o.z,d=Math.hypot(dx,dz),min=o.radius+.58;if(d<min&&d>.001){p.root.position.x=o.x+dx/d*min;p.root.position.z=o.z+dz/d*min;}}"
need(m,player_obstacles,'player obstacle loop')
resolve_method="""  resolveObstacles(root,radius=.55){for(const o of this.obstacles){const dx=root.position.x-o.x,dz=root.position.z-o.z,d=Math.hypot(dx,dz),min=o.radius+radius;if(d<min&&d>.001){root.position.x=o.x+dx/d*min;root.position.z=o.z+dz/d*min;}}}

"""
m=m.replace('  updatePlayer(dt){',resolve_method+'  updatePlayer(dt){',1)
m=m.replace(player_obstacles,'this.resolveObstacles(p.root,.58);',1)
enemy_move="e.root.position.addScaledVector(move,speed*dt);if(move.lengthSq()>.01)e.root.rotation.y=Math.atan2(move.x,move.z);"
need(m,enemy_move,'enemy move')
m=m.replace(enemy_move,"e.root.position.addScaledVector(move,speed*dt);this.resolveObstacles(e.root,Math.max(.35,e.radius*.55));if(move.lengthSq()>.01)e.root.rotation.y=Math.atan2(move.x,move.z);",1)

# Projectile colour and hit feedback.
proj_old="const root=this.cloneVisual(this.assets[w.projectile]||this.assets.gem,w.scale||.25,{shadow:false});"
proj_new="const root=this.cloneVisual(this.assets[w.projectile]||this.assets.gem,w.scale||.25,{shadow:false,tint:w.fx||null});"
m=replace_once(m,proj_old,proj_new,'projectile tint')

damage_old="damageEnemy(e,amount,proj){if(e.dead)return;document.body.dataset.qaHits=String((Number(document.body.dataset.qaHits)||0)+1);if(e.shield>0)"
damage_new="damageEnemy(e,amount,proj){if(e.dead)return;document.body.dataset.qaHits=String((Number(document.body.dataset.qaHits)||0)+1);this.spawnImpact(e.root.position,proj?.w?.fx||e.def?.color||0xffffff,amount>35);if(e.shield>0)"
m=replace_once(m,damage_old,damage_new,'damage impact')

impact_anchor="  chainFrom(first,p){"
need(m,impact_anchor,'impact insertion')
impact_methods="""  spawnImpact(pos,color=0xffffff,strong=false){if(this.lowPower&&this.effects.length>28)return;const root=this.cloneVisual(this.assets.star||this.assets.gem,strong?.26:.15,{shadow:false,tint:color});root.position.copy(pos);root.position.y=.72;root.scale.setScalar(.35);this.scene.add(root);this.effects.push({root,life:strong?.34:.20,max:strong?.34:.20,spin:5+Math.random()*4});}
  updateEffects(dt){for(let i=this.effects.length-1;i>=0;i--){const e=this.effects[i];e.life-=dt;e.root.rotation.y+=dt*e.spin;e.root.rotation.z+=dt*e.spin*.55;const t=1-e.life/e.max;e.root.scale.setScalar(.35+t*(t<.55?1.35:.65));e.root.position.y+=dt*.9;if(e.life<=0){e.root.removeFromParent();this.effects.splice(i,1);}}}

"""
m=m.replace(impact_anchor,impact_methods+impact_anchor,1)

animate_seq='this.updateBoss(dt);this.updateProjectiles(dt);this.updateEnemyProjectiles(dt);this.updatePickups(dt);this.updateCamera(dt);this.updateHUD();'
need(m,animate_seq,'animate update sequence')
m=m.replace(animate_seq,'this.updateBoss(dt);this.updateProjectiles(dt);this.updateEnemyProjectiles(dt);this.updatePickups(dt);this.updateEffects(dt);this.updateCamera(dt);this.updateHUD();',1)

# Slightly more readable camera and larger hero presentation.
m=m.replace("const hero=this.currentHero(),root=this.cloneVisual(this.assets[hero.asset],2.25);","const hero=this.currentHero(),root=this.cloneVisual(this.assets[hero.asset],2.48);")
m=m.replace("const pos=this.player.root.position,target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?9.4:7),pos.z+(this.lowPower?11.8:9.3)),look=new THREE.Vector3(pos.x,pos.y+1,pos.z-2.7);","const pos=this.player.root.position,target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?9.7:7.4),pos.z+(this.lowPower?12.2:9.8)),look=new THREE.Vector3(pos.x,pos.y+1.15,pos.z-2.9);")

# Weapon art in upgrade/loadout cards.
new_weapon_card="if(c.kind==='newWeapon'){const w=weaponById(c.id);return `<button class=\"upgrade-card rare\" data-upgrade=\"${encodeURIComponent(JSON.stringify(c))}\"><img class=\"upgrade-weapon-art\" src=\"${this.weaponPortrait(w)}\" alt=\"\"><span>НОВОЕ ОРУЖИЕ</span><strong>${esc(w.name)}</strong><p>Добавляет новый дальнобойный стиль атаки.</p><em>${esc(w.evolution)}</em></button>`;}"
old_weapon_card="if(c.kind==='newWeapon'){const w=weaponById(c.id);return `<button class=\"upgrade-card rare\" data-upgrade=\"${encodeURIComponent(JSON.stringify(c))}\"><span>НОВОЕ ОРУЖИЕ</span><strong>${esc(w.name)}</strong><p>Добавляет новый дальнобойный стиль атаки.</p><em>${esc(w.evolution)}</em></button>`;}"
m=replace_once(m,old_weapon_card,new_weapon_card,'new weapon upgrade art')
old_upgraded="if(c.kind==='weapon'){const w=weaponById(c.id),r=this.player.weaponRanks[c.id]||1;return `<button class=\"upgrade-card ${r>=7?'legendary':''}\" data-upgrade=\"${encodeURIComponent(JSON.stringify(c))}\"><span>${r>=7?'ЭВОЛЮЦИЯ':`ОРУЖИЕ · ${r+1}/8`}</span><strong>${esc(r>=7?w.evolution:w.name)}</strong><p>${esc(w.levels[Math.min(7,r)])}</p><em>${esc(passiveById(w.passive)?.name||'')}</em></button>`;}"
new_upgraded="if(c.kind==='weapon'){const w=weaponById(c.id),r=this.player.weaponRanks[c.id]||1;return `<button class=\"upgrade-card ${r>=7?'legendary':''}\" data-upgrade=\"${encodeURIComponent(JSON.stringify(c))}\"><img class=\"upgrade-weapon-art\" src=\"${this.weaponPortrait(w)}\" alt=\"\"><span>${r>=7?'ЭВОЛЮЦИЯ':`ОРУЖИЕ · ${r+1}/8`}</span><strong>${esc(r>=7?w.evolution:w.name)}</strong><p>${esc(w.levels[Math.min(7,r)])}</p><em>${esc(passiveById(w.passive)?.name||'')}</em></button>`;}"
m=replace_once(m,old_upgraded,new_upgraded,'weapon upgrade art')

loadout_old="$('#weapon-slots').innerHTML=this.player.weapons.map(id=>{const w=weaponById(id);return `<div title=\"${esc(w.name)}\"><b>${this.player.weaponRanks[id]||1}</b><span>${esc(w.name.slice(0,2))}</span></div>`;}).join('');"
loadout_new="$('#weapon-slots').innerHTML=this.player.weapons.map(id=>{const w=weaponById(id);return `<div title=\"${esc(w.name)}\"><b>${this.player.weaponRanks[id]||1}</b><img src=\"${this.weaponPortrait(w)}\" alt=\"\"></div>`;}).join('');"
m=replace_once(m,loadout_old,loadout_new,'loadout weapon art')

# ---------------------------------------------------------------------------
# CSS: portrait-led hero selection and cleaner premium cards.
# ---------------------------------------------------------------------------
if '/* V3 TOP ART */' not in s:
    s += r'''

/* V3 TOP ART */
.hero-grid{grid-template-columns:repeat(5,minmax(0,1fr));gap:14px}
.hero-card{padding:0;min-height:286px;overflow:hidden;background:linear-gradient(160deg,rgba(255,255,255,.095),rgba(255,255,255,.035));box-shadow:0 16px 34px #0003;transition:transform .16s,border-color .16s,box-shadow .16s}
.hero-card:hover{transform:translateY(-4px);box-shadow:0 22px 44px #0005;border-color:#ffffff42}
.hero-art{height:150px;position:relative;display:grid;place-items:end center;background:radial-gradient(circle at 50% 38%,#ffffff28 0 26%,transparent 60%),linear-gradient(150deg,#254a58,#17323c);overflow:hidden}
.hero-art:after{content:"";position:absolute;inset:auto 0 0;height:42%;background:linear-gradient(transparent,#0d2029c9)}
.hero-art img{width:100%;height:100%;object-fit:contain;object-position:center bottom;filter:drop-shadow(0 12px 12px #0006);transform:scale(1.08);z-index:1}
.hero-art span{position:absolute;left:10px;right:10px;bottom:8px;z-index:2;font-size:9px;font-weight:900;letter-spacing:.07em;color:#fff1c9;text-shadow:0 2px 5px #000}
.hero-copy{padding:12px 13px 13px;display:flex;flex-direction:column;min-height:134px}
.hero-copy strong{font-size:14px;line-height:1.2}.hero-copy p{font-size:10px;line-height:1.38;opacity:.72;margin:7px 0;flex:1}.hero-copy b{font-size:10px;color:var(--gold);letter-spacing:.06em}
.collection-card{overflow:hidden}.collection-art{display:block;width:100%;height:78px;object-fit:contain;margin:-5px auto 8px;background:radial-gradient(circle,#ffffff14,transparent 66%);filter:drop-shadow(0 7px 7px #0005)}
.upgrade-weapon-art{height:68px;width:92px;object-fit:contain;align-self:flex-end;margin:-4px -2px -48px 0;filter:drop-shadow(0 8px 9px #0007);opacity:.98}
.slots>div img{width:32px;height:32px;object-fit:contain;filter:drop-shadow(0 3px 4px #0007)}
.map-card{background:linear-gradient(160deg,#ffffff0c,#ffffff05);box-shadow:0 10px 28px #0002}
.map-thumb{box-shadow:inset 0 0 0 1px #ffffff12,0 8px 18px #0002}
@media(max-width:860px){.hero-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.hero-card{min-height:248px}.hero-art{height:128px}.collection-grid{grid-template-columns:repeat(2,1fr)}}
'''

# ---------------------------------------------------------------------------
# BUILD VALIDATION: new props + 30 real PNG portraits + no gun pack.
# ---------------------------------------------------------------------------
asset_list_marker=" 'floor.glb','tree.glb'"
need(v,asset_list_marker,'validator assets')
v=v.replace("'food-cookie.glb',\n *[f'hero-", "'food-cookie.glb','house.glb','wagon.glb','fence.glb','well.glb','bridge.glb','stall.glb','barrel.glb','tent.glb','campfire.glb','raft.glb','sign.glb','bench.glb','crate2.glb',\n *[f'hero-",1)
required_line="required=[root/'index.html',*[root/'assets'/n for n in assets]]"
need(v,required_line,'validator required')
v=v.replace(required_line,"portraits=[root/'portraits'/f'hero-{i:02}.png' for i in range(1,16)]+[root/'portraits'/f'weapon-{i:02}.png' for i in range(1,16)]\nrequired=[root/'index.html',*[root/'assets'/n for n in assets],*portraits]",1)
if 'Modern gun asset pack must not ship' not in v:
    v += "\nbuild_script=Path('tools/build_assets.sh').read_text(encoding='utf-8')\nif 'ultimate_gun_pack' in build_script or '$ROOT/guns' in build_script: raise SystemExit('Modern gun asset pack must not ship in v3')\nif len(list((root/'portraits').glob('hero-*.png')))!=15 or len(list((root/'portraits').glob('weapon-*.png')))!=15: raise SystemExit('Expected 15 hero and 15 weapon portraits')\nprint('V3 art validation passed: thematic weapons, portraits, expanded environment.')\n"

main_p.write_text(m,encoding='utf-8')
content_p.write_text(c,encoding='utf-8')
style_p.write_text(s,encoding='utf-8')
build_p.write_text(b,encoding='utf-8')
validate_p.write_text(v,encoding='utf-8')
print('Merry Mayhem 3D v3 top-art overhaul applied')
