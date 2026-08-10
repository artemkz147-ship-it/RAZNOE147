#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/.cc0-sources"
rm -rf "$SRC"
mkdir -p "$SRC/mustermenschen" "$SRC/singles"

fetch(){
  local url="$1" out="$2"
  echo "FETCH $url"
  curl -fL --retry 4 --retry-delay 2 --connect-timeout 30 --max-time 300 "$url" -o "$out"
  test -s "$out"
}

# All sources below are CC0/public-domain assets from OpenGameArt.
fetch 'https://opengameart.org/sites/default/files/Mustermenshen%201_0.zip' "$SRC/Mustermenshen1.zip"
fetch 'https://opengameart.org/sites/default/files/Verlaineopen.gif' "$SRC/singles/verlaine.gif"
fetch 'https://opengameart.org/sites/default/files/1_4.gif' "$SRC/singles/female-ninja.gif"
fetch 'https://opengameart.org/sites/default/files/AvaLee-sheet_0.gif' "$SRC/singles/ava-lee.gif"
fetch 'https://opengameart.org/sites/default/files/Spritesheet32bits_0.png' "$SRC/singles/tasen-defender.png"
fetch 'https://opengameart.org/sites/default/files/Knight%202D.zip' "$SRC/Knight2D.zip"

unzip -q "$SRC/Mustermenshen1.zip" -d "$SRC/mustermenschen"
unzip -q "$SRC/Knight2D.zip" -d "$SRC/dark-knight"

echo '--- CC0 SOURCE INVENTORY ---'
find "$SRC" -type f -printf '%s\t%p\n' | sort -nr | head -160
printf 'SOURCE_BYTES='; du -sb "$SRC" | cut -f1
printf 'SOURCE_FILES='; find "$SRC" -type f | wc -l
