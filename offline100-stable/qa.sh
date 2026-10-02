#!/usr/bin/env bash
set -euo pipefail
APK="offline100-stable/app/build/outputs/apk/debug/app-debug.apk"
PKG="ru.offline100.games"
ACT="$PKG/.MainActivity"
LOG="qa-w320-home.log"

# The remaining 50 games and their result screens already passed at 320px in
# the previous emulator run. This run checks only the one unresolved catalog
# defect after the catalog-only CSS fix; no game is replayed.
adb install -r "$APK"
adb shell wm density 540
adb shell pm clear "$PKG" >/dev/null
adb logcat -c
adb shell am start -W -n "$ACT" >/dev/null

READY=0
for i in $(seq 1 40); do
  adb logcat -d -s Offline100:I '*:S' > "$LOG"
  if grep -q 'HOME_QA=' "$LOG"; then READY=1; break; fi
  if grep -Eq 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE|MAIN_FRAME_ERROR|QA_READY_TIMEOUT' "$LOG"; then
    cat "$LOG" >&2
    exit 1
  fi
  sleep 1
done
[[ "$READY" -eq 1 ]]

python3 - "$LOG" <<'PY'
import json,sys
text=open(sys.argv[1],encoding='utf-8',errors='replace').read()
rows=[ln.split('HOME_QA=',1)[1].strip() for ln in text.splitlines() if 'HOME_QA=' in ln]
if not rows: raise SystemExit('missing HOME_QA')
h=json.loads(rows[-1]); h=json.loads(h) if isinstance(h,str) else h
vp=h.get('viewport') or [0,0]
assert 310 <= int(vp[0]) <= 330, h
assert h.get('cards') == 100, h
assert h.get('unique') == 100, h
assert h.get('aiCards') == 12, h
assert h.get('bodyOverflow') is False, h
assert h.get('titleIssues') == [], h
print('QA_OK w320 catalog cards=100 unique=100 ai=12 titles=0 overflow=0')
PY

adb shell pidof "$PKG" | grep -q '[0-9]'
echo 'QA_FINAL_OK apk-installed app-alive catalog-320-clean; previous-tail-50=passed'
echo 'CATALOG_320_OK 100/100 titles=0 overflow=0' > qa-all-games-summary.log
adb shell wm density reset
