from pathlib import Path
import re

bp=Path('tools/build_assets.sh')
cp=Path('src/content.js')
mp=Path('src/main.js')
b=bp.read_text(encoding='utf-8')
c=cp.read_text(encoding='utf-8')
m=mp.read_text(encoding='utf-8')

# -------------------------------------------------------------------------
# PORTRAITS: Blender Eevee on Ubuntu Actions needs a virtual X/EGL context.
# -------------------------------------------------------------------------
b=b.replace('blender -b --factory-startup --python tools/render_portraits.py -- "$OUT" public/portraits',
            'xvfb-run -a blender -b --factory-startup --python tools/render_portraits.py -- "$OUT" public/portraits')

# -------------------------------------------------------------------------
# WEAPONS: forbid random fallback armour. Add the older CC0 LowPoly RPG pack,
# which contains real staff props, and select explicit recognisable held items.
# All gameplay damage remains ranged; these are the visible hand props.
# -------------------------------------------------------------------------
if "RPG%20Pack.zip' rpg_old" not in b:
    b=b.replace("fetch_zip 'https://opengameart.org/sites/default/files/ultimate_rpg_items_pack_by_quaternius_0.zip' rpg\n",
                "fetch_zip 'https://opengameart.org/sites/default/files/ultimate_rpg_items_pack_by_quaternius_0.zip' rpg\nfetch_zip 'https://opengameart.org/sites/default/files/RPG%20Pack.zip' rpg_old\n")

# A strict picker: if an intended category is absent, CI must fail instead of
# silently substituting armour or an unrelated model.
if 'pick_match(){' not in b:
    marker="pick_unique(){\n"
    idx=b.index(marker)
    # insert helper before pick_unique
    helper="""pick_match(){
  local dir="$1"; shift; local kw f
  for kw in "$@"; do
    f="$(all_fbx "$dir" | grep -i "$kw" | head -1 || true)"
    [[ -n "$f" ]] && { printf '%s' "$f"; return 0; }
  done
  echo "::error::No intended asset in $dir for: $*" >&2
  return 1
}
"""
    b=b[:idx]+helper+b[idx:]

start=b.index(': > "$ROOT/used-weapons.txt"')
end=b.index(': > "$ROOT/used-monsters.txt"',start)
weapon_block=r''': > "$ROOT/used-weapons.txt"
declare -a weapon_src
# 15 deliberately different fantasy/tool silhouettes. No modern gun pack and
# no alphabetic fallback are allowed here.
weapon_src[1]="$(pick_match "$ROOT/rpg_old" 'Staff')"
weapon_src[2]="$(pick_match "$ROOT/rpg" 'Potion10_Filled' 'Potion9_Filled' 'Potion8_Filled')"
weapon_src[3]="$(pick_match "$ROOT/medieval" 'Hammer_Double' 'Mace_Double' 'Hammer')"
weapon_src[4]="$(pick_match "$ROOT/rpg" 'Book1_Closed' 'Book2_Closed' 'Book')"
weapon_src[5]="$(pick_match "$ROOT/rpg_old" 'Sword' 'Staff')"
weapon_src[6]="$(pick_match "$ROOT/rpg" 'Crystal1' 'Crystal2' 'Gem')"
weapon_src[7]="$(pick_match "$ROOT/survival" 'Axe.fbx' 'Pickaxe' 'Hammer')"
weapon_src[8]="$(pick_match "$ROOT/survival" 'Pan.fbx' 'Pot.fbx' 'Shovel')"
weapon_src[9]="$(pick_match "$ROOT/medieval" 'Bow_Evil' 'Bow_Golden' 'Bow')"
weapon_src[10]="$(pick_match "$ROOT/rpg_old" 'Staff' 'Spear')"
weapon_src[11]="$(pick_match "$ROOT/medieval" 'Crossbow' 'Bow_Golden' 'Bow')"
weapon_src[12]="$(pick_match "$ROOT/medieval" 'Hammer_Small' 'Mace' 'Hammer')"
weapon_src[13]="$(pick_match "$ROOT/rpg" 'Dagger' 'Sword')"
weapon_src[14]="$(pick_match "$ROOT/rpg_old" 'Staff' 'Spear')"
weapon_src[15]="$(pick_match "$ROOT/rpg" 'Axe_Double' 'Axe')"
for i in $(seq 1 15); do
  convert "${weapon_src[$i]}" "weapon-$(printf '%02d' "$i").glb" static
done

'''
b=b[:start]+weapon_block+b[end:]

# -------------------------------------------------------------------------
# HERO NAMES: match the actual 15 character models selected by the source pack
# so cards/portraits never promise a robot/princess while showing a soldier.
# -------------------------------------------------------------------------
hero_names={
    'donut_knight':('Золотая Рыцарка','Бронированный герой: высокий запас здоровья и защита.'),
    'dew_fairy':('Лесная Эльфийка','Быстрый герой: быстрее набирает уровни и притягивает опыт.'),
    'hamster_pirate':('Капитан Бомба','Пират-бомбардир: крупные взрывы и повышенная удача.'),
    'candy_witch':('Конфетная Ведьма','Радужная магия: широкий залп и высокий базовый урон.'),
    'mushroom_druid':('Зелёная Авантюристка','Контроль толпы, увеличенная площадь и замедление.'),
    'cat_mage':('Янтарный Маг','Точные пробивающие выстрелы и высокий темп магии.'),
    'toy_robot':('Лазурная Стражница','Защитный класс с самонаводящимися механическими зарядами.'),
    'baker_alchemist':('Шеф Алхимик','Возвратные атаки и постоянная регенерация в долгом забеге.'),
    'fox_archer':('Лазурный Стрелок','Скоростной стрелок с повышенным шансом критического попадания.'),
    'cloud_princess':('Небесная Путница','Быстрые воздушные заряды и шанс уклонения.'),
    'watermelon_captain':('Арбузный Корсар','Тяжёлые снаряды разбрасывают большие группы.'),
    'music_gnome':('Северный Громовержец','Громовые волны пробивают плотную толпу вокруг героя.'),
    'lightning_bee':('Ниндзя Искра','Самый быстрый герой: рой сам ищет цели.'),
    'snow_penguin':('Полярный Разведчик','Морозит врагов и постепенно восстанавливает здоровье.'),
    'pumpkin_jester':('Тыквенная Гоблинка','Трикстер с минами и цепными тыквенными взрывами.'),
}
for hid,(name,desc) in hero_names.items():
    pat=rf"(\{{id:'{re.escape(hid)}',name:)'[^']+'(,asset:'[^']+',weapon:'[^']+',cost:[^,]+,desc:)'[^']+'"
    c,n=re.subn(pat,lambda x:x.group(1)+repr(name)+x.group(2)+repr(desc),c,count=1)
    if n!=1: raise SystemExit(f'hero rename failed: {hid}')

weapon_names={
 'donut':('Пекарский Посох','Планетарная Пекарня'),
 'firefly':('Эликсир Светлячков','Рой Зарниц'),
 'pirate_bomb':('Пороховой Молот','Пороховой Шторм'),
 'rainbow_fan':('Радужный Гримуар','Семицветная Буря'),
 'flower_burst':('Клинок Цветения','Цветочная Корона'),
 'amber_comet':('Янтарный Кристалл','Янтарная Сверхновая'),
 'blast_bot':('Механический Топор','Механический Карнавал'),
 'pancake':('Сковорода-Бумеранг','Бесконечная Кухня'),
 'fox_bow':('Лисий Лук','Ливень Лисьих Стрел'),
 'cloud_orb':('Облачный Посох','Грозовой Спутник'),
 'watermelon':('Арбузный Арбалет','Гигантский Арбуз'),
 'music_wave':('Молот Грома','Громовой Концерт'),
 'smart_bee':('Клинок Роя','Королевский Рой'),
 'snowball':('Морозный Посох','Полярная Буря'),
 'pumpkin':('Тыквенный Топор','Тыквенный Лабиринт'),
}
for wid,(name,evo) in weapon_names.items():
    pat=rf"(\{{id:'{re.escape(wid)}',name:)'[^']+'(,evolution:)'[^']+'"
    c,n=re.subn(pat,lambda x:x.group(1)+repr(name)+x.group(2)+repr(evo),c,count=1)
    if n!=1: raise SystemExit(f'weapon rename failed: {wid}')

# The generic level text was already baked when module data is constructed; it
# references current weapon names at runtime, so no duplicate text patch needed.

# Slight additional polish: enemies are no longer heavily colour-washed; keep
# their authored palette and only add a subtle class accent.
m=m.replace("const root=this.cloneVisual(this.assets[def.asset],def.scale*(elite?1.45:1),{tint:def.color});",
            "const root=this.cloneVisual(this.assets[def.asset],def.scale*(elite?1.45:1),{tint:elite?0xffd76a:null});")

bp.write_text(b,encoding='utf-8')
cp.write_text(c,encoding='utf-8')
mp.write_text(m,encoding='utf-8')
print('v3 release art fixes applied: EGL portraits, strict thematic weapons, coherent hero cards')
