#!/usr/bin/env bash
set -euo pipefail
APK="offline100-stable/app/build/outputs/apk/debug/app-debug.apk"
PKG="ru.offline100.games"
ACT="$PKG/.MainActivity"

# First 50 were already covered in the previous run. This pass intentionally
# starts at game 51 and never launches games 1-50 again.
TAIL_GAMES=(
  pyramid13 suithunt cardmemory exact21 tenpairs cipher proverb missing wordbuild categoryword
  takuzu latin knight arrows flood gears balance numsort timer ballsort shells mole lanes zigzag precision solpairs
  redblack cardfour cardstairs cardsum wordfrom oddletter alphabet syllables wordlength sequence parity colorlinks tiles3 codebreak tap30
  stopsignal orbit coinfall minigolf emojimem changed battleship checkers escape
)
[[ ${#TAIL_GAMES[@]} -eq 50 ]]

adb install -r "$APK"

validate_probe() {
  local GAME="$1" LOG="$2" LABEL="$3"
  python3 - "$GAME" "$LOG" "$LABEL" <<'PY'
import json,re,sys

game,path,label=sys.argv[1:4]
text=open(path,encoding='utf-8',errors='replace').read()

def last(marker):
    rows=[ln.split(marker,1)[1].strip() for ln in text.splitlines() if marker in ln]
    if not rows:
        raise AssertionError(f'{label}/{game}: missing {marker}')
    value=json.loads(rows[-1])
    if isinstance(value,str):
        value=json.loads(value)
    return value

def need(cond,msg):
    if not cond:
        raise AssertionError(f'{label}/{game}: {msg}')

need('QA_OPEN game='+game+' opened=true' in text,'game did not open')
need('QA_FINISH game='+game+' finished=true' in text,'finish hook failed')
g=last('QA_SNAPSHOT=')
r=last('RESULT_SNAPSHOT=')
need(g.get('game')==game,f'active game is {g.get("game")}')
need(bool(str(g.get('title','')).strip()),'empty title')
need(bool(str(g.get('objective','')).strip()),'empty objective')
need(int(g.get('mountChars',0))>20 and int(g.get('mountChildren',0))>0,'empty playfield')
need(g.get('bodyOverflow') is False,'body overflow')
need(g.get('mountOverflow') is False,'playfield overflow')
if game=='checkers':
    need(g.get('aiSelector') is True,'no AI/local selector')
    need(g.get('aiMode')=='ai','AI is not default')
need(r.get('open') is True,'result modal closed')
need(bool(str(r.get('title','')).strip()),'empty result title')
need(bool(str(r.get('message','')).strip()),'empty result explanation')
need(bool(str(r.get('goal','')).strip()),'result omits goal')
buttons=r.get('buttons') or []
need(len(buttons)==3 and all(str(x).strip() for x in buttons),f'bad result buttons {buttons}')
need(r.get('bodyOverflow') is False,'result overflow')
print(f'OK {label} {game}')
PY
}

run_tail_suite() {
  local LABEL="$1" DENSITY="$2" MINW="$3" MAXW="$4"
  adb shell wm density "$DENSITY"
  local OK=0
  : > "qa-tail-$LABEL.log"

  for GAME in "${TAIL_GAMES[@]}"; do
    adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
    adb logcat -c
    adb shell am start -W -n "$ACT" --es testGame "$GAME" --ez testFinish true >/dev/null

    local READY=0 LOG="qa-tail-${LABEL}-${GAME}.log"
    for i in $(seq 1 20); do
      adb logcat -d -s Offline100:I '*:S' > "$LOG"
      if grep -q 'RESULT_SNAPSHOT=' "$LOG"; then READY=1; break; fi
      if grep -Eq 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE|MAIN_FRAME_ERROR|QA_READY_TIMEOUT' "$LOG"; then
        echo "$LABEL/$GAME: Android/WebView crash" >&2
        cat "$LOG" >&2
        return 1
      fi
      sleep 1
    done
    [[ "$READY" -eq 1 ]]

    # Also verify the actual viewport class used for this probe.
    python3 - "$LOG" "$MINW" "$MAXW" "$LABEL" "$GAME" <<'PY'
import json,sys
path,minw,maxw,label,game=sys.argv[1],int(sys.argv[2]),int(sys.argv[3]),sys.argv[4],sys.argv[5]
text=open(path,encoding='utf-8',errors='replace').read()
rows=[ln.split('HOME_QA=',1)[1].strip() for ln in text.splitlines() if 'HOME_QA=' in ln]
if not rows: raise SystemExit(f'{label}/{game}: missing HOME_QA')
h=json.loads(rows[-1]); h=json.loads(h) if isinstance(h,str) else h
vp=h.get('viewport') or [0,0]
if not (minw <= int(vp[0]) <= maxw): raise SystemExit(f'{label}/{game}: wrong viewport {vp}')
if h.get('cards')!=100 or h.get('unique')!=100: raise SystemExit(f'{label}/{game}: catalog count broken {h}')
if h.get('bodyOverflow') is not False or h.get('titleIssues')!=[]: raise SystemExit(f'{label}/{game}: catalog layout broken {h}')
PY

    validate_probe "$GAME" "$LOG" "$LABEL"
    cat "$LOG" >> "qa-tail-$LABEL.log"
    OK=$((OK+1))
  done

  [[ "$OK" -eq 50 ]]
  echo "QA_TAIL_OK $LABEL 50/50" | tee "qa-summary-$LABEL.log"
}

# Check only the untested half at the same three narrow Android widths.
run_tail_suite w412 420 400 420
run_tail_suite w360 480 350 370
run_tail_suite w320 540 310 330

adb shell wm density reset
cat qa-summary-w412.log qa-summary-w360.log qa-summary-w320.log > qa-all-games-summary.log
echo 'QA_TAIL_OK widths=412,360,320 remaining=150/150 results=150/150 failures=0'
