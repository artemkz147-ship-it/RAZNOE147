#!/usr/bin/env bash
set -euo pipefail
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
[[ ${#ALL_GAMES[@]} -eq 100 ]]

cat > /tmp/validate_offline100.py <<'PY'
import json,re,sys
path,label,minw,maxw=sys.argv[1],sys.argv[2],int(sys.argv[3]),int(sys.argv[4])
text=open(path,encoding='utf-8',errors='replace').read()
expected="""ttt connect4 snake mines fifteen g2048 memory pipes sudoku blocks breakout words parking liquid maze reversi gomoku nim lights hanoi simon numbers target reaction math guess dots chain pong dodge rps higher blackjack war hangman anagram wordchain odd colormatch memgrid route queens magic oneline stack flappy catch space runner compare pyramid13 suithunt cardmemory exact21 tenpairs cipher proverb missing wordbuild categoryword takuzu latin knight arrows flood gears balance numsort timer ballsort shells mole lanes zigzag precision solpairs redblack cardfour cardstairs cardsum wordfrom oddletter alphabet syllables wordlength sequence parity colorlinks tiles3 codebreak tap30 stopsignal orbit coinfall minigolf emojimem changed battleship checkers escape""".split()
duo={'ttt','connect4','reversi','gomoku','nim','dots','checkers'}

def decode(raw):
    obj=json.loads(raw.strip())
    if isinstance(obj,str): obj=json.loads(obj)
    return obj

def last_after(marker):
    rows=[ln.split(marker,1)[1].strip() for ln in text.splitlines() if marker in ln]
    if not rows: raise AssertionError(f'{label}: missing {marker}')
    return rows[-1]

def need(cond,msg):
    if not cond: raise AssertionError(f'{label}: {msg}')

home=decode(last_after('HOME_QA='))
need(home.get('cards')==100,f'catalog cards {home}')
need(home.get('unique')==100,f'catalog unique {home}')
need(home.get('aiCards')==12,f'AI cards {home}')
need(home.get('bodyOverflow') is False,f'catalog overflow {home}')
need(home.get('titleIssues')==[],f'clipped catalog titles {home}')
vp=home.get('viewport') or [0,0]
need(minw <= int(vp[0]) <= maxw,f'wrong viewport {vp}, expected width {minw}..{maxw}')

opens={m.group(1):m.group(2).strip() for m in re.finditer(r'QA_ALL_OPEN ([a-z0-9]+)=([^\r\n]+)',text)}
finishes={m.group(1):m.group(2).strip() for m in re.finditer(r'QA_ALL_FINISH ([a-z0-9]+)=([^\r\n]+)',text)}
games={m.group(1):decode(m.group(2)) for m in re.finditer(r'QA_ALL_GAME ([a-z0-9]+)=(.+)$',text,re.M)}
results={m.group(1):decode(m.group(2)) for m in re.finditer(r'QA_ALL_RESULT ([a-z0-9]+)=(.+)$',text,re.M)}
errors=[]
for gid in expected:
    if opens.get(gid)!='true': errors.append(f'{gid}: open={opens.get(gid)}')
    if finishes.get(gid)!='true': errors.append(f'{gid}: finish={finishes.get(gid)}')
    g=games.get(gid)
    if not g:
        errors.append(f'{gid}: no game snapshot'); continue
    if g.get('game')!=gid: errors.append(f'{gid}: active={g.get("game")}')
    if not str(g.get('title','')).strip(): errors.append(f'{gid}: empty title')
    if not str(g.get('objective','')).strip(): errors.append(f'{gid}: empty objective')
    if int(g.get('mountChars',0))<=20 or int(g.get('mountChildren',0))<=0: errors.append(f'{gid}: empty playfield')
    if g.get('bodyOverflow') is not False: errors.append(f'{gid}: body overflow')
    if g.get('mountOverflow') is not False: errors.append(f'{gid}: playfield overflow')
    if gid in duo:
        if g.get('aiSelector') is not True: errors.append(f'{gid}: no AI/local selector')
        if g.get('aiMode')!='ai': errors.append(f'{gid}: AI not default')
    if gid=='parking' and g.get('parkingExit') is not True: errors.append('parking: exit invisible')
    if gid=='pipes' and int(g.get('pipeTerminals',0))<2: errors.append('pipes: terminals invisible')
    r=results.get(gid)
    if not r:
        errors.append(f'{gid}: no result snapshot'); continue
    if r.get('open') is not True: errors.append(f'{gid}: result closed')
    if not str(r.get('title','')).strip(): errors.append(f'{gid}: empty result title')
    if not str(r.get('message','')).strip(): errors.append(f'{gid}: empty result explanation')
    if not str(r.get('goal','')).strip(): errors.append(f'{gid}: result omits goal')
    buttons=r.get('buttons') or []
    if len(buttons)!=3 or not all(str(x).strip() for x in buttons): errors.append(f'{gid}: result buttons {buttons}')
    if r.get('bodyOverflow') is not False: errors.append(f'{gid}: result overflow')
if set(games)!=set(expected): errors.append('game snapshot ID set mismatch')
if set(results)!=set(expected): errors.append('result snapshot ID set mismatch')

out=f'qa-summary-{label}.log'
with open(out,'w',encoding='utf-8') as f:
    f.write(f'VIEWPORT {vp}\nOPEN {len(games)}/100\nFINAL {len(results)}/100\nFAILURES {len(errors)}\n')
    for e in errors: f.write('FAIL '+e+'\n')
if errors:
    print('\n'.join(f'{label}: FAIL {e}' for e in errors))
    raise SystemExit(1)
print(f'QA_OK {label} viewport={vp[0]} open=100 final=100 failures=0')
PY

adb install -r "$APK"

run_suite() {
  local LABEL="$1" DENSITY="$2" MINW="$3" MAXW="$4" LOG="qa-$1.log"
  adb shell wm density "$DENSITY"
  adb shell pm clear "$PKG" >/dev/null
  adb logcat -c
  adb shell am start -W -n "$ACT" --ez testAll true >/dev/null
  local done=0
  for i in $(seq 1 180); do
    adb logcat -d -s Offline100:I '*:S' > "$LOG"
    if grep -q 'QA_ALL_DONE count=100' "$LOG"; then done=1; break; fi
    if grep -Eq 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE|MAIN_FRAME_ERROR|QA_READY_TIMEOUT' "$LOG"; then
      echo "$LABEL: Android/WebView crash" >&2; cat "$LOG" >&2; return 1
    fi
    sleep 1
  done
  [[ "$done" -eq 1 ]]
  adb logcat -d -s Offline100:I '*:S' > "$LOG"
  ! grep -Eq 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE|MAIN_FRAME_ERROR|QA_READY_TIMEOUT' "$LOG"
  adb shell pidof "$PKG" | grep -q '[0-9]'
  python3 /tmp/validate_offline100.py "$LOG" "$LABEL" "$MINW" "$MAXW"
}

# Same shipped APK, three narrow Android viewport classes.
run_suite w412 420 400 420
run_suite w360 480 350 370
run_suite w320 540 310 330

# Return emulator to normal density and capture the three UX surfaces explicitly
# called out by the user.
adb shell wm density reset
adb shell pm clear "$PKG" >/dev/null
for GAME in pipes parking checkers; do
  adb logcat -c
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$ACT" --es testGame "$GAME" >/dev/null
  for i in $(seq 1 20); do
    adb logcat -d -s Offline100:I '*:S' > "qa-$GAME.log"
    grep -q 'QA_SNAPSHOT=' "qa-$GAME.log" && break
    sleep 1
  done
  grep -q 'QA_SNAPSHOT=' "qa-$GAME.log"
  adb exec-out screencap -p > "qa-$GAME.png"
done

cat qa-summary-w412.log qa-summary-w360.log qa-summary-w320.log > qa-all-games-summary.log
echo 'QA_OK widths=412,360,320 open=300/300 final=300/300 failures=0'
