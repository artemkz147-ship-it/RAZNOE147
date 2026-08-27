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

capture_offline_log() {
  adb logcat -d -s Offline100:I '*:S' > qa-full.log
}

# Install and cold-start one production WebView process. The test-all intent
# drives all 100 games and all 100 unified endings inside that exact process,
# avoiding 200 artificial app reloads while still testing the shipped APK.
adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" --ez testAll true

DONE=0
for i in $(seq 1 180); do
  capture_offline_log
  if grep -q 'QA_ALL_DONE count=100' qa-full.log; then DONE=1; break; fi
  if grep -Eq 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE|MAIN_FRAME_ERROR|QA_READY_TIMEOUT' qa-full.log; then
    echo 'Android/WebView crash marker detected' >&2
    cat qa-full.log >&2
    exit 20
  fi
  sleep 1
done
[[ "$DONE" -eq 1 ]]
capture_offline_log
adb exec-out screencap -p > qa-all-final.png || true
adb shell pidof "$PKG" | grep -q '[0-9]'
adb shell dumpsys activity activities | grep -q "$PKG/.MainActivity"
! grep -Eq 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE|MAIN_FRAME_ERROR|QA_READY_TIMEOUT' qa-full.log

python3 - qa-full.log <<'PY'
import json,re,sys
path=sys.argv[1]
text=open(path,encoding='utf-8',errors='replace').read()
expected="""ttt connect4 snake mines fifteen g2048 memory pipes sudoku blocks breakout words parking liquid maze reversi gomoku nim lights hanoi simon numbers target reaction math guess dots chain pong dodge rps higher blackjack war hangman anagram wordchain odd colormatch memgrid route queens magic oneline stack flappy catch space runner compare pyramid13 suithunt cardmemory exact21 tenpairs cipher proverb missing wordbuild categoryword takuzu latin knight arrows flood gears balance numsort timer ballsort shells mole lanes zigzag precision solpairs redblack cardfour cardstairs cardsum wordfrom oddletter alphabet syllables wordlength sequence parity colorlinks tiles3 codebreak tap30 stopsignal orbit coinfall minigolf emojimem changed battleship checkers escape""".split()
duo={'ttt','connect4','reversi','gomoku','nim','dots','checkers'}

def decode(raw):
    raw=raw.strip()
    obj=json.loads(raw)
    if isinstance(obj,str): obj=json.loads(obj)
    return obj

def last_after(marker):
    rows=[ln.split(marker,1)[1].strip() for ln in text.splitlines() if marker in ln]
    if not rows: raise AssertionError(f'missing {marker}')
    return rows[-1]

def need(cond,msg):
    if not cond: raise AssertionError(msg)

home=decode(last_after('HOME_QA='))
need(home.get('cards')==100,f"catalog cards={home}")
need(home.get('unique')==100,f"catalog unique={home}")
need(home.get('aiCards')==12,f"AI-labelled catalog games must be 12: {home}")
need(home.get('bodyOverflow') is False,f"catalog overflow: {home}")
need(home.get('titleIssues')==[],f"clipped catalog titles: {home}")

opens={m.group(1):m.group(2).strip() for m in re.finditer(r'QA_ALL_OPEN ([a-z0-9]+)=([^\r\n]+)',text)}
finishes={m.group(1):m.group(2).strip() for m in re.finditer(r'QA_ALL_FINISH ([a-z0-9]+)=([^\r\n]+)',text)}
games={m.group(1):decode(m.group(2)) for m in re.finditer(r'QA_ALL_GAME ([a-z0-9]+)=(.+)$',text,re.M)}
results={m.group(1):decode(m.group(2)) for m in re.finditer(r'QA_ALL_RESULT ([a-z0-9]+)=(.+)$',text,re.M)}

errors=[]
for gid in expected:
    if opens.get(gid)!='true': errors.append(f'{gid}: open={opens.get(gid)}')
    if finishes.get(gid)!='true': errors.append(f'{gid}: forced finish={finishes.get(gid)}')
    g=games.get(gid)
    if not g:
        errors.append(f'{gid}: no game snapshot'); continue
    if g.get('game')!=gid: errors.append(f'{gid}: wrong active game {g.get("game")}')
    if not str(g.get('title','')).strip(): errors.append(f'{gid}: empty title')
    if not str(g.get('objective','')).strip(): errors.append(f'{gid}: empty objective')
    if int(g.get('mountChars',0))<=20 or int(g.get('mountChildren',0))<=0: errors.append(f'{gid}: empty playfield')
    if g.get('bodyOverflow') is not False: errors.append(f'{gid}: body overflow')
    if g.get('mountOverflow') is not False: errors.append(f'{gid}: playfield overflow')
    if gid in duo:
        if g.get('aiSelector') is not True: errors.append(f'{gid}: missing AI/local selector')
        if g.get('aiMode')!='ai': errors.append(f'{gid}: AI not default ({g.get("aiMode")})')
    if gid=='parking' and g.get('parkingExit') is not True: errors.append('parking: exit not visible')
    if gid=='pipes' and int(g.get('pipeTerminals',0))<2: errors.append('pipes: source/target not visible')
    r=results.get(gid)
    if not r:
        errors.append(f'{gid}: no result snapshot'); continue
    if r.get('open') is not True: errors.append(f'{gid}: result not open')
    if not str(r.get('title','')).strip(): errors.append(f'{gid}: empty result title')
    if not str(r.get('message','')).strip(): errors.append(f'{gid}: empty result explanation')
    if not str(r.get('goal','')).strip(): errors.append(f'{gid}: result omits goal')
    buttons=r.get('buttons') or []
    if len(buttons)!=3 or not all(str(x).strip() for x in buttons): errors.append(f'{gid}: bad result buttons {buttons}')
    if r.get('bodyOverflow') is not False: errors.append(f'{gid}: result overflow')

if set(games)!=set(expected): errors.append(f'game snapshot id set mismatch: {sorted(set(expected)-set(games))}')
if set(results)!=set(expected): errors.append(f'result snapshot id set mismatch: {sorted(set(expected)-set(results))}')

with open('qa-all-games-summary.log','w',encoding='utf-8') as f:
    for gid in expected:
        f.write(f"OPEN_OK {gid}\n" if gid in games and opens.get(gid)=='true' else f"OPEN_FAIL {gid}\n")
    for gid in expected:
        f.write(f"FINAL_OK {gid}\n" if gid in results and finishes.get(gid)=='true' else f"FINAL_FAIL {gid}\n")
    f.write(f"QA_TOTAL open={len(games)}/100 final={len(results)}/100 failures={len(errors)}\n")
    for e in errors: f.write('FAIL '+e+'\n')

if errors:
    print('\n'.join('FAIL '+e for e in errors))
    raise SystemExit(1)
print('QA_OK open=100 final=100 failures=0')
PY

# Extra human-readable proof frames for the exact UX the user called out.
for GAME in pipes parking checkers; do
  adb logcat -c
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$ACT" --es testGame "$GAME" >/dev/null
  for i in $(seq 1 15); do
    adb logcat -d -s Offline100:I '*:S' > "qa-$GAME.log"
    grep -q 'QA_SNAPSHOT=' "qa-$GAME.log" && break
    sleep 1
  done
  grep -q 'QA_SNAPSHOT=' "qa-$GAME.log"
  adb exec-out screencap -p > "qa-$GAME.png"
done

echo 'QA_OK open=100 final=100 failures=0'
