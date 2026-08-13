#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/.cc0-sources"
rm -rf "$SRC"
mkdir -p "$SRC/mustermenschen" "$SRC/singles" "$SRC/backgrounds" "$SRC/v5"

fetch(){
  local url="$1" out="$2"
  echo "FETCH $url"
  curl -fL --retry 4 --retry-delay 2 --connect-timeout 30 --max-time 420 "$url" -o "$out"
  test -s "$out"
}

# All sources below are CC0/public-domain assets from OpenGameArt.
# Legacy / specialist fighter animation bases.
fetch 'https://opengameart.org/sites/default/files/Mustermenshen%201_0.zip' "$SRC/Mustermenshen1.zip"
fetch 'https://opengameart.org/sites/default/files/Verlaineopen.gif' "$SRC/singles/verlaine.gif"
fetch 'https://opengameart.org/sites/default/files/1_4.gif' "$SRC/singles/female-ninja.gif"
fetch 'https://opengameart.org/sites/default/files/AvaLee-sheet_0.gif' "$SRC/singles/ava-lee.gif"
fetch 'https://opengameart.org/sites/default/files/Spritesheet32bits_0.png' "$SRC/singles/tasen-defender.png"
fetch 'https://opengameart.org/sites/default/files/Knight%202D.zip' "$SRC/Knight2D.zip"

# High-resolution Universal Prototype 2 masters.
fetch 'https://opengameart.org/sites/default/files/stonewall_realistic_sequence.gif' "$SRC/v5/stonewall-realistic.gif"
fetch 'https://opengameart.org/sites/default/files/erika_sequence.gif' "$SRC/v5/erika-realistic.gif"

unzip -q "$SRC/Mustermenshen1.zip" -d "$SRC/mustermenschen"
unzip -q "$SRC/Knight2D.zip" -d "$SRC/dark-knight"

# Layered / high-resolution stage bases.
fetch 'https://opengameart.org/sites/default/files/Abandon-City-Background-TheGameAssetsMine.com-.zip' "$SRC/AbandonCity.zip"
fetch 'https://opengameart.org/sites/default/files/DarkCityBackground.zip' "$SRC/DarkCity.zip"
fetch 'https://opengameart.org/sites/default/files/ASSETS.zip' "$SRC/StarryNight.zip"
fetch 'https://opengameart.org/sites/default/files/parallax-industrial-pack_0.zip' "$SRC/Industrial.zip"
fetch 'https://opengameart.org/sites/default/files/streets_of_fight_files.zip' "$SRC/StreetsOfFight.zip"

# V6 stage donors: graphic-novel seaview (3840x2160), layered cave,
# full-HD lava and layered space/clouds. These replace the nearly-black
# procedural-looking stages that survived the v5 pass.
fetch 'https://opengameart.org/sites/default/files/seaview_background.zip' "$SRC/SeaView.zip"
fetch 'https://opengameart.org/sites/default/files/Seamless%20Parallax%20Cave%20Background.zip' "$SRC/Cave.zip"
fetch 'https://opengameart.org/sites/default/files/1920x1080_0.png' "$SRC/backgrounds/lava-1920x1080.png"
fetch 'https://opengameart.org/sites/default/files/Space%20Background_0.zip' "$SRC/Space.zip"

for spec in \
  'AbandonCity:AbandonCity.zip' \
  'DarkCity:DarkCity.zip' \
  'StarryNight:StarryNight.zip' \
  'Industrial:Industrial.zip' \
  'StreetsOfFight:StreetsOfFight.zip' \
  'SeaView:SeaView.zip' \
  'Cave:Cave.zip' \
  'Space:Space.zip'; do
  dir="${spec%%:*}"; zip="${spec##*:}"; mkdir -p "$SRC/backgrounds/$dir"; unzip -q "$SRC/$zip" -d "$SRC/backgrounds/$dir" || true
done

echo '--- CC0 SOURCE INVENTORY ---'
find "$SRC" -type f -printf '%s\t%p\n' | sort -nr | head -300
printf 'SOURCE_BYTES='; du -sb "$SRC" | cut -f1
printf 'SOURCE_FILES='; find "$SRC" -type f | wc -l
