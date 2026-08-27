#!/usr/bin/env bash
set -uo pipefail
APK="offline100-stable/app/build/outputs/apk/debug/app-debug.apk"
PKG="ru.offline100.games"
ACT="$PKG/.MainActivity"

ALL_GAMES=(
  ttt connect4 snake mines fifteen g2048 memory pipes sudoku blocks breakout words parking liquid maze reversi gomoku nim lights hanoi
  simon numbers target reaction math guess dots chain pong dodge rps higher blackjack war hangman anagram wordchain odd colormatch memgrid
  route queens magic oneline stack flappy catch space runner compare pyramid13 suithunt cardmemory exact21 tenpairs cipher proverb missing
  wordbuild categoryword takuzu latin knight arrows flood gears balance numsort timer ballsort shells mole lanes zigzag precision solpairs
  redblack cardfour cardstairs cardsum wordfrom oddletter alphabet syllables wordlength sequence parity colorlinks tiles3 codebreak tap30
  stopsignal orbit coinfall minigolf emojimem changed battleship checkers escape
)

if [[ ${#ALL_GAMES[@]} -ne 100 ]]; then
  echo "Expected 100 games, got ${#ALL_GAMES[@]}" >&2
  exit 2
fi

has_crash() {
  local log="$1"
  grep -Eq 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE|MAIN_FRAME_ERROR|QA_READY_TIMEOUT' "$log"
}

capture_log_until() {
  local file="$1" pattern="$2" tries="${3:-35}" i
  for ((i=1;i<=tries;i++)); do
    adb logcat -d > "$file"
    if grep -q "$pattern" "$file"; then return 0; fi
    sleep 1
  done
  adb logcat -d > "$file"
  echo "Timed out waiting for $pattern in $file" >&2
  return 1
}

validate_marker_json() {
  local file="$1" marker="$2" kind="$3" game="${4:-}"
  python3 - "$file" "$marker" "$kind" "$game" <<'PY'
import json, sys
path, marker, kind, game = sys.argv[1:]
lines=open(path,encoding='utf-8',errors='replace').read().splitlines()
matches=[ln.split(marker,1)[1].strip() for ln in lines if marker in ln]
if not matches:
    raise SystemExit(f'{path}: missing {marker}')
raw=matches[-1]
try:
    obj=json.loads(raw)
    if isinstance(obj,str): obj=json.loads(obj)
except Exception as e:
    raise SystemExit(f'{path}: cannot parse {marker}: {e}: {raw[:300]}')

def need(cond,msg):
    if not cond: raise SystemExit(f'{path}: {msg}; got={obj}')

if kind=='home':
    need(obj.get('cards')==100,'catalog must contain 100 cards')
    need(obj.get('unique')==100,'catalog IDs must be unique')
    need(obj.get('bodyOverflow') is False,'catalog has horizontal overflow')
    need(obj.get('titleIssues')==[],'catalog has clipped/overflowing titles')
elif kind=='game':
    need(obj.get('game')==game,f'wrong game, expected {game}')
    need(bool(str(obj.get('title','')).strip()),'missing game title')
    need(bool(str(obj.get('objective','')).strip()),'missing understandable objective')
    need(int(obj.get('mountChars',0))>20,'game mount is effectively empty')
    need(int(obj.get('mountChildren',0))>0,'game mount has no UI')
    need(obj.get('bodyOverflow') is False,'game page has horizontal overflow')
    need(obj.get('mountOverflow') is False,'playfield overflows its container')
    if game in {'ttt','connect4','reversi','gomoku','nim','dots','checkers'}:
        need(obj.get('aiSelector') is True,'duo game has no AI/local selector')
        need(obj.get('aiMode')=='ai','AI is not selected by default')
    if game=='parking': need(obj.get('parkingExit') is True,'parking exit is not visible')
    if game=='pipes': need(int(obj.get('pipeTerminals',0))>=2,'pipes source/target terminals are not both visible')
elif kind=='result':
    need(obj.get('open') is True,'result modal is not open')
    need(bool(str(obj.get('title','')).strip()),'result title is empty')
    need(bool(str(obj.get('message','')).strip()),'result explanation is empty')
    need(bool(str(obj.get('goal','')).strip()),'result does not repeat the game goal')
    buttons=obj.get('buttons') or []
    need(len(buttons)==3 and all(str(x).strip() for x in buttons),'result must have replay/another/home buttons')
    need(obj.get('bodyOverflow') is False,'result screen has horizontal overflow')
else:
    raise SystemExit('unknown validation kind')
PY
}

adb install -r "$APK" || exit 10

# Home is a hard prerequisite.
adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" || exit 11
capture_log_until qa-home.log 'HOME_QA=' 45 || exit 12
adb exec-out screencap -p > qa-home.png || true
if has_crash qa-home.log; then echo 'HOME_FAIL crash' | tee qa-failures.log; exit 13; fi
validate_marker_json qa-home.log 'HOME_QA=' home || exit 14
adb shell pidof "$PKG" | grep -q '[0-9]' || exit 15
adb shell dumpsys activity activities | grep -q "$PKG/.MainActivity" || exit 16

: > qa-all-games-summary.log
: > qa-failures.log
FAIL_COUNT=0

record_fail() {
  local phase="$1" game="$2" reason="$3"
  echo "${phase}_FAIL $game :: $reason" | tee -a qa-all-games-summary.log qa-failures.log
  FAIL_COUNT=$((FAIL_COUNT+1))
}

# Open and validate every one of the 100 games. Do not abort on the first bad
# layout: collect the whole defect set in a single emulator pass.
for GAME in "${ALL_GAMES[@]}"; do
  LOG="qa-game-$GAME.log"
  adb logcat -c
  adb shell am force-stop "$PKG"
  if ! adb shell am start -W -n "$ACT" --es testGame "$GAME" >/dev/null; then
    record_fail OPEN "$GAME" 'activity start failed'; continue
  fi
  if ! capture_log_until "$LOG" 'QA_SNAPSHOT=' 35; then
    record_fail OPEN "$GAME" 'QA snapshot timeout'; continue
  fi
  adb exec-out screencap -p > "qa-game-$GAME.png" || true
  if has_crash "$LOG"; then
    record_fail OPEN "$GAME" 'crash marker'; continue
  fi
  ERR=$(validate_marker_json "$LOG" 'QA_SNAPSHOT=' game "$GAME" 2>&1) || {
    record_fail OPEN "$GAME" "$ERR"; continue;
  }
  if ! adb shell pidof "$PKG" | grep -q '[0-9]'; then
    record_fail OPEN "$GAME" 'process not alive'; continue
  fi
  echo "OPEN_OK $GAME" >> qa-all-games-summary.log
done

# Validate the unified final state for all 100 games independently too.
for GAME in "${ALL_GAMES[@]}"; do
  LOG="qa-final-$GAME.log"
  adb logcat -c
  adb shell am force-stop "$PKG"
  if ! adb shell am start -W -n "$ACT" --es testGame "$GAME" --ez testFinish true >/dev/null; then
    record_fail FINAL "$GAME" 'activity start failed'; continue
  fi
  if ! capture_log_until "$LOG" 'RESULT_SNAPSHOT=' 35; then
    record_fail FINAL "$GAME" 'result snapshot timeout'; continue
  fi
  adb exec-out screencap -p > "qa-final-$GAME.png" || true
  if has_crash "$LOG"; then
    record_fail FINAL "$GAME" 'crash marker'; continue
  fi
  ERR=$(validate_marker_json "$LOG" 'QA_SNAPSHOT=' game "$GAME" 2>&1) || {
    record_fail FINAL "$GAME" "$ERR"; continue;
  }
  ERR=$(validate_marker_json "$LOG" 'RESULT_SNAPSHOT=' result "$GAME" 2>&1) || {
    record_fail FINAL "$GAME" "$ERR"; continue;
  }
  if ! adb shell pidof "$PKG" | grep -q '[0-9]'; then
    record_fail FINAL "$GAME" 'process not alive'; continue
  fi
  echo "FINAL_OK $GAME" >> qa-all-games-summary.log
done

OPEN_COUNT=$(grep -c '^OPEN_OK ' qa-all-games-summary.log || true)
FINAL_COUNT=$(grep -c '^FINAL_OK ' qa-all-games-summary.log || true)
echo "QA_TOTAL open=$OPEN_COUNT/100 final=$FINAL_COUNT/100 failures=$FAIL_COUNT" | tee -a qa-all-games-summary.log

if [[ "$FAIL_COUNT" -ne 0 || "$OPEN_COUNT" -ne 100 || "$FINAL_COUNT" -ne 100 ]]; then
  echo 'FULL_QA_FAILED'
  cat qa-failures.log
  exit 1
fi

echo 'QA_OK open=100 final=100 failures=0'
