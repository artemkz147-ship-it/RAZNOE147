#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.asset-src}"
OUT="public/assets"
mkdir -p "$ROOT" "$OUT"

fetch_zip() {
  local url="$1" name="$2"
  echo "Downloading $name"
  curl -fL --retry 4 --retry-all-errors --connect-timeout 20 "$url" -o "$ROOT/$name.zip"
  rm -rf "$ROOT/$name"
  mkdir -p "$ROOT/$name"
  unzip -q "$ROOT/$name.zip" -d "$ROOT/$name"
}

fetch_zip 'https://opengameart.org/sites/default/files/Knight%20Character%20by%20%40Quaternius.zip' knight
fetch_zip 'https://opengameart.org/sites/default/files/Animated%20Monster%20Pack%20by%20%40Quaternius.zip' monsters
fetch_zip 'https://opengameart.org/sites/default/files/ultimate_nature_pack_by_quaternius_1.zip' nature
fetch_zip 'https://opengameart.org/sites/default/files/RPG%20Pack.zip' rpg
fetch_zip 'https://opengameart.org/sites/default/files/Updated%20Modular%20Dungeon%20-%20May%202019.zip' dungeon

echo '--- FBX inventory (first 180) ---'
find "$ROOT" -type f -iname '*.fbx' | sort | head -180

pick() {
  local dir="$1"; shift
  local keyword candidate
  for keyword in "$@"; do
    candidate="$(find "$dir" -type f -iname "*${keyword}*.fbx" | grep -Evi '/(unity|animations?|source)/' | sort | head -1 || true)"
    if [[ -n "$candidate" ]]; then printf '%s' "$candidate"; return 0; fi
    candidate="$(find "$dir" -type f -iname "*${keyword}*.fbx" | sort | head -1 || true)"
    if [[ -n "$candidate" ]]; then printf '%s' "$candidate"; return 0; fi
  done
  candidate="$(find "$dir" -type f -iname '*.fbx' | sort | head -1 || true)"
  [[ -n "$candidate" ]] || return 1
  printf '%s' "$candidate"
}

HERO="$(pick "$ROOT/knight" KnightCharacter Character Knight)"
ORC="$(pick "$ROOT/monsters" Skeleton skeleton Slime slime)"
DEMON="$(pick "$ROOT/monsters" Dragon dragon Bat bat)"
TREE="$(pick "$ROOT/nature" PineTree_1 Pine tree)"
ROCK="$(pick "$ROOT/nature" Rock_1 Rock rock)"
GEM="$(pick "$ROOT/rpg" Gems Gem Crystal)"
ARENA="$(pick "$ROOT/dungeon" Floor_Modular Floor_BricksSeparate Floor)"

printf '\nSELECTED ASSETS\nhero=%s\norc=%s\ndemon=%s\ntree=%s\nrock=%s\ngem=%s\narena=%s\n' "$HERO" "$ORC" "$DEMON" "$TREE" "$ROCK" "$GEM" "$ARENA"

convert() {
  local src="$1" out="$2" mode="${3:-animated}"
  rm -f "$OUT/$out"
  blender -b --factory-startup --python tools/convert_asset.py -- "$src" "$OUT/$out" "$mode"
  if [[ ! -s "$OUT/$out" ]]; then
    echo "::error::Asset conversion failed: $src -> $OUT/$out"
    exit 1
  fi
}

convert "$HERO" hero.glb animated
convert "$ORC" orc.glb animated
convert "$DEMON" demon.glb animated
convert "$TREE" tree.glb static
convert "$ROCK" rock.glb static
convert "$GEM" gem.glb static
convert "$ARENA" arena.glb static

echo '--- Built GLB assets ---'
ls -lh "$OUT"
