#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.asset-src}"
OUT="public/assets"
rm -rf "$OUT"; mkdir -p "$ROOT" "$OUT"

fetch_zip(){
  local url="$1" name="$2"
  echo "==> Downloading $name"
  curl -fL --retry 5 --retry-all-errors --connect-timeout 25 "$url" -o "$ROOT/$name.zip"
  rm -rf "$ROOT/$name"; mkdir -p "$ROOT/$name"
  unzip -q "$ROOT/$name.zip" -d "$ROOT/$name"
}

fetch_zip 'https://opengameart.org/sites/default/files/ultimate_animated_character_pack_by_quaternius.zip' characters
fetch_zip 'https://opengameart.org/sites/default/files/ultimate_gun_pack_by_quaternius.zip' guns
fetch_zip 'https://opengameart.org/sites/default/files/cute_animated_monsters_-_aug_2020.zip' cute_monsters
fetch_zip 'https://opengameart.org/sites/default/files/ultimate_nature_pack_by_quaternius_1.zip' nature
fetch_zip 'https://opengameart.org/sites/default/files/ultimate_rpg_items_pack_by_quaternius_0.zip' rpg
fetch_zip 'https://opengameart.org/sites/default/files/Updated%20Modular%20Dungeon%20-%20May%202019.zip' dungeon
fetch_zip 'https://opengameart.org/sites/default/files/ultimate_food_pack_by_quaternius.zip' food
fetch_zip 'https://opengameart.org/sites/default/files/Medieval%20Weapons%20Pack%20-%20Sept%202018.zip' medieval

all_fbx(){ find "$1" -type f -iname '*.fbx' | grep -Evi '/(animations?|source|unity|unreal|godot)/' | sort; }
pick(){
  local dir="$1"; shift; local kw f
  for kw in "$@"; do
    f="$(all_fbx "$dir" | grep -i "$kw" | head -1 || true)"; [[ -n "$f" ]] && { printf '%s' "$f"; return 0; }
  done
  all_fbx "$dir" | head -1
}
pick_unique(){
  local dir="$1" used_file="$2"; shift 2; local kw f
  for kw in "$@"; do
    while IFS= read -r f; do
      [[ -z "$f" ]] && continue
      if ! grep -Fxq "$f" "$used_file" 2>/dev/null; then echo "$f" >> "$used_file"; printf '%s' "$f"; return 0; fi
    done < <(all_fbx "$dir" | grep -i "$kw" || true)
  done
  while IFS= read -r f; do
    if ! grep -Fxq "$f" "$used_file" 2>/dev/null; then echo "$f" >> "$used_file"; printf '%s' "$f"; return 0; fi
  done < <(all_fbx "$dir")
  return 1
}

convert(){
  local src="$1" out="$2" mode="${3:-animated}"
  echo "==> $out <= $src"
  rm -f "$OUT/$out"
  blender -b --factory-startup --python tools/convert_asset.py -- "$src" "$OUT/$out" "$mode" > "$ROOT/blender-${out}.log" 2>&1 || { cat "$ROOT/blender-${out}.log"; exit 1; }
  [[ -s "$OUT/$out" ]] || { cat "$ROOT/blender-${out}.log"; echo "::error::Missing $OUT/$out"; exit 1; }
}

: > "$ROOT/used-heroes.txt"
hero_keywords=("knight" "elf" "cowboy" "witch" "druid" "wizard" "robot" "chef" "ranger" "princess" "pirate" "viking" "ninja" "medic" "goblin")
for i in $(seq 1 15); do
  kw="${hero_keywords[$((i-1))]}"; src="$(pick_unique "$ROOT/characters" "$ROOT/used-heroes.txt" "$kw")"
  convert "$src" "hero-$(printf '%02d' "$i").glb" animated
done

: > "$ROOT/used-weapons.txt"
for i in $(seq 1 15); do
  src="$(pick_unique "$ROOT/guns" "$ROOT/used-weapons.txt" 'pistol' 'rifle' 'smg' 'revolver' 'shotgun' 'sniper' 'gun')"
  convert "$src" "weapon-$(printf '%02d' "$i").glb" static
done

: > "$ROOT/used-monsters.txt"
monster_keywords=("slime" "mushroom" "bee" "penguin" "crab" "ghost" "demon" "alien" "dragon" "skull" "panda" "cactus")
for i in $(seq 1 12); do
  kw="${monster_keywords[$((i-1))]}"; src="$(pick_unique "$ROOT/cute_monsters" "$ROOT/used-monsters.txt" "$kw")"
  convert "$src" "monster-$(printf '%02d' "$i").glb" animated
done

# Environment pieces. Every gameplay-visible object is an imported art asset.
TREE="$(pick "$ROOT/nature" 'PineTree_1' 'PineTree' 'Tree')"
DEAD_TREE="$(pick "$ROOT/nature" 'Dead' 'DryTree' 'Tree')"
BUSH="$(pick "$ROOT/nature" 'Bush_1' 'Bush')"
ROCK="$(pick "$ROOT/nature" 'Rock_1' 'Rock')"
FLOWER="$(pick "$ROOT/nature" 'Flowers.fbx' 'Flower' 'Plant')"
GRASS="$(pick "$ROOT/nature" 'Grass.fbx' 'Grass_2' 'Plant')"
GRASS_SHORT="$(pick "$ROOT/nature" 'Grass_Short' 'Grass_2' 'Grass')"
SNOW_ROCK="$(pick "$ROOT/nature" 'Rock_Snow_1' 'Rock_Snow' 'Snow')"
PINE_SNOW="$(pick "$ROOT/nature" 'PineTree_Snow_1' 'PineTree_Snow' 'PineTree')"
BIRCH="$(pick "$ROOT/nature" 'BirchTree_1' 'BirchTree')"
BIRCH_AUTUMN="$(pick "$ROOT/nature" 'BirchTree_Autumn_1' 'Autumn')"
WILLOW="$(pick "$ROOT/nature" 'Willow_1' 'Willow')"
CACTUS="$(pick "$ROOT/nature" 'Cactus_1' 'Cactus')"
PALM="$(pick "$ROOT/nature" 'PalmTree_1' 'PalmTree')"
MOSS_ROCK="$(pick "$ROOT/nature" 'Rock_Moss_1' 'Rock_Moss' 'Rock')"
SNOW="$SNOW_ROCK"
GEM="$(pick "$ROOT/rpg" 'Gems' 'Gem' 'Crystal')"
STAR="$(pick "$ROOT/rpg" 'Star' 'Gem' 'Crystal')"
BOMB="$(pick "$ROOT/rpg" 'Bomb' 'Potion' 'Gem')"
FLOOR="$(pick "$ROOT/dungeon" 'Floor_Modular' 'Floor')"
ARCH="$(pick "$ROOT/dungeon" 'Arch' 'Door')"
COLUMN="$(pick "$ROOT/dungeon" 'Column' 'Pillar' 'Wall')"
CHEST="$(pick "$ROOT/dungeon" 'Chest' 'Crate')"
TORCH="$(pick "$ROOT/dungeon" 'Torch' 'Lamp' 'Woodfire')"
FIRE="$(pick "$ROOT/dungeon" 'Woodfire' 'Fire' 'Torch')"
DONUT="$(pick "$ROOT/food" 'Donut' 'Doughnut' 'Bagel')"
WATERMELON="$(pick "$ROOT/food" 'Watermelon' 'Melon' 'Apple')"
PANCAKE="$(pick "$ROOT/food" 'Pancake' 'Waffle' 'Bread')"
PUMPKIN="$(pick "$ROOT/food" 'Pumpkin' 'Orange' 'Apple')"
COOKIE="$(pick "$ROOT/food" 'Cookie' 'Biscuit' 'Donut')"
ARROW="$(pick "$ROOT/medieval" 'Arrow' 'Bolt' 'Bow')"
CLOUD="$GEM"
BEE="$(pick "$ROOT/cute_monsters" 'Bee' 'Fly' 'Monster')"
MINIROBOT="$(pick "$ROOT/characters" 'Robot' 'Mech' 'Knight')"

convert "$TREE" tree.glb static
convert "$DEAD_TREE" dead-tree.glb static
convert "$BUSH" bush.glb static
convert "$ROCK" rock.glb static
convert "$FLOWER" flower.glb static
convert "$GRASS" grass.glb static
convert "$GRASS_SHORT" grass-short.glb static
convert "$SNOW_ROCK" snow-rock.glb static
convert "$PINE_SNOW" pine-snow.glb static
convert "$BIRCH" birch.glb static
convert "$BIRCH_AUTUMN" birch-autumn.glb static
convert "$WILLOW" willow.glb static
convert "$CACTUS" cactus.glb static
convert "$PALM" palm.glb static
convert "$MOSS_ROCK" moss-rock.glb static
convert "$SNOW" snow.glb static
convert "$GEM" gem.glb static
convert "$STAR" star.glb static
convert "$BOMB" bomb.glb static
convert "$FLOOR" floor.glb static
convert "$ARCH" arch.glb static
convert "$COLUMN" column.glb static
convert "$CHEST" chest.glb static
convert "$TORCH" torch.glb static
convert "$FIRE" fire.glb static
convert "$DONUT" food-donut.glb static
convert "$WATERMELON" food-watermelon.glb static
convert "$PANCAKE" food-pancake.glb static
convert "$PUMPKIN" food-pumpkin.glb static
convert "$COOKIE" food-cookie.glb static
convert "$ARROW" arrow.glb static
convert "$CLOUD" cloud.glb static
convert "$BEE" bee.glb static
convert "$MINIROBOT" mini-robot.glb static

echo '--- Built art assets ---'
ls -lh "$OUT"
python3 tools/check_glb_animations.py
