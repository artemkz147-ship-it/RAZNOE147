#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.asset-src}"
OUT="public/assets"
mkdir -p "$ROOT" "$OUT"

fetch_zip(){
  local url="$1" name="$2"
  echo "Downloading $name"
  curl -fL --retry 4 --retry-all-errors --connect-timeout 20 "$url" -o "$ROOT/$name.zip"
  rm -rf "$ROOT/$name"; mkdir -p "$ROOT/$name"
  unzip -q "$ROOT/$name.zip" -d "$ROOT/$name"
}

fetch_zip 'https://opengameart.org/sites/default/files/Knight%20Character%20by%20%40Quaternius.zip' knight
fetch_zip 'https://opengameart.org/sites/default/files/Animated%20Monster%20Pack%20by%20%40Quaternius.zip' monsters
fetch_zip 'https://opengameart.org/sites/default/files/ultimate_nature_pack_by_quaternius_1.zip' nature
fetch_zip 'https://opengameart.org/sites/default/files/RPG%20Pack.zip' rpg
fetch_zip 'https://opengameart.org/sites/default/files/Updated%20Modular%20Dungeon%20-%20May%202019.zip' dungeon

pick(){
  local dir="$1"; shift
  local keyword candidate
  for keyword in "$@"; do
    candidate="$(find "$dir" -type f -iname "*${keyword}*.fbx" | grep -Evi '/(unity|animations?|source)/' | sort | head -1 || true)"
    [[ -n "$candidate" ]] && { printf '%s' "$candidate"; return 0; }
    candidate="$(find "$dir" -type f -iname "*${keyword}*.fbx" | sort | head -1 || true)"
    [[ -n "$candidate" ]] && { printf '%s' "$candidate"; return 0; }
  done
  return 1
}

HERO="$(pick "$ROOT/knight" KnightCharacter)"
SWORD="$(pick "$ROOT/knight" ShortSword Sword Katana)"
SKELETON="$(pick "$ROOT/monsters" Skeleton)"
SLIME="$(pick "$ROOT/monsters" Slime)"
BAT="$(pick "$ROOT/monsters" Bat)"
DRAGON="$(pick "$ROOT/monsters" Dragon)"
TREE="$(pick "$ROOT/nature" PineTree_1)"
DEAD_TREE="$(pick "$ROOT/nature" CommonTree_Dead_2 CommonTree_Dead BirchTree_Dead_2)"
BUSH="$(pick "$ROOT/nature" Bush_1 BushBerries_1)"
ROCK="$(pick "$ROOT/nature" Rock_1)"
GEM="$(pick "$ROOT/rpg" Gems Gem Crystal)"
FLOOR="$(pick "$ROOT/dungeon" Floor_Modular)"
ARCH="$(pick "$ROOT/dungeon" Arch.fbx Arch)"
COLUMN="$(pick "$ROOT/dungeon" Column.fbx Column)"
CHEST="$(pick "$ROOT/dungeon" Chest.fbx Chest)"
TORCH="$(pick "$ROOT/dungeon" Torch.fbx Torch)"
FIRE="$(pick "$ROOT/dungeon" Woodfire Fire)"

printf '\nSELECTED ASSETS\nhero=%s\nsword=%s\nskeleton=%s\nslime=%s\nbat=%s\ndragon=%s\ntree=%s\ndeadTree=%s\nbush=%s\nrock=%s\ngem=%s\nfloor=%s\narch=%s\ncolumn=%s\nchest=%s\ntorch=%s\nfire=%s\n' \
  "$HERO" "$SWORD" "$SKELETON" "$SLIME" "$BAT" "$DRAGON" "$TREE" "$DEAD_TREE" "$BUSH" "$ROCK" "$GEM" "$FLOOR" "$ARCH" "$COLUMN" "$CHEST" "$TORCH" "$FIRE"

convert(){
  local src="$1" out="$2" mode="${3:-animated}"
  rm -f "$OUT/$out"
  blender -b --factory-startup --python tools/convert_asset.py -- "$src" "$OUT/$out" "$mode"
  [[ -s "$OUT/$out" ]] || { echo "::error::Asset conversion failed: $src -> $OUT/$out"; exit 1; }
}

convert "$HERO" hero.glb animated
convert "$SWORD" sword.glb static
convert "$SKELETON" skeleton.glb animated
convert "$SLIME" slime.glb animated
convert "$BAT" bat.glb animated
convert "$DRAGON" dragon.glb animated
convert "$TREE" tree.glb static
convert "$DEAD_TREE" dead-tree.glb static
convert "$BUSH" bush.glb static
convert "$ROCK" rock.glb static
convert "$GEM" gem.glb static
convert "$FLOOR" floor.glb static
convert "$ARCH" arch.glb static
convert "$COLUMN" column.glb static
convert "$CHEST" chest.glb static
convert "$TORCH" torch.glb static
convert "$FIRE" fire.glb static

echo '--- Built GLB assets ---'
ls -lh "$OUT"
