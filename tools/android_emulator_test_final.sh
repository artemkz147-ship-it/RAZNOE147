#!/usr/bin/env bash
set -euo pipefail

SRC="tools/android_emulator_test.sh"
TMP="/tmp/android_emulator_test_final.sh"

python3 - "$SRC" "$TMP" <<'PY'
from pathlib import Path
import sys
src=Path(sys.argv[1]).read_text(encoding='utf-8')
old='adb_t shell input swipe "$PAD_X" "$PAD_Y" "$PAD_RIGHT" "$PAD_Y" 500'
new='adb_t shell input swipe "$PAD_RIGHT" "$PAD_Y" "$PAD_RIGHT" "$PAD_Y" 650'
if old not in src:
    raise SystemExit('expected legacy center-origin stick gesture not found')
src=src.replace(old,new,1)
Path(sys.argv[2]).write_text(src,encoding='utf-8')
PY
chmod +x "$TMP"
exec bash "$TMP" "$@"
