#!/usr/bin/env bash
set -euo pipefail
APK="offline100-stable/app/build/outputs/apk/debug/app-debug.apk"
PKG="ru.offline100.games"
ACT="$PKG/.MainActivity"

# User requested the remaining half only. Original games 1-50 are never launched here.
TAIL50="pyramid13,suithunt,cardmemory,exact21,tenpairs,cipher,proverb,missing,wordbuild,categoryword,takuzu,latin,knight,arrows,flood,gears,balance,numsort,timer,ballsort,shells,mole,lanes,zigzag,precision,solpairs,redblack,cardfour,cardstairs,cardsum,wordfrom,oddletter,alphabet,syllables,wordlength,sequence,parity,colorlinks,tiles3,codebreak,tap30,stopsignal,orbit,coinfall,minigolf,emojimem,changed,battleship,checkers,escape"
# These are the only w412 games that failed run #37. All other w412 tail games are not repeated.
W412_RETEST=(wordfrom oddletter alphabet syllables parity changed battleship checkers escape)

adb install -r "$APK"

cat > /tmp/validate_tail.py <<'PY'
import json,re,sys
path,label,minw,maxw,expected_csv=sys.argv[1],sys.argv[2],int(sys.argv[3]),int(sys.argv[4]),sys.argv[5]
expected=expected_csv.split(',')
text=open(path,encoding='utf-8',errors='replace').read()

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
need(minw <= int(vp[0]) <= maxw,f'wrong viewport {vp}, expected {minw}..{maxw}')

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
    if gid=='checkers':
        if g.get('aiSelector') is not True: errors.append('checkers: no AI/local selector')
        if g.get('aiMode')!='ai': errors.append('checkers: AI not default')
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
if set(games)!=set(expected): errors.append(f'game ID set mismatch got={len(games)} expected={len(expected)}')
if set(results)!=set(expected): errors.append(f'result ID set mismatch got={len(results)} expected={len(expected)}')

with open(f'qa-summary-{label}.log','w',encoding='utf-8') as f:
    f.write(f'VIEWPORT {vp}\nOPEN {len(games)}/{len(expected)}\nFINAL {len(results)}/{len(expected)}\nFAILURES {len(errors)}\n')
    for e in errors: f.write('FAIL '+e+'\n')
if errors:
    print('\n'.join(f'{label}: FAIL {e}' for e in errors))
    raise SystemExit(1)
print(f'QA_OK {label} viewport={vp[0]} open={len(expected)} final={len(expected)} failures=0')
PY

cat > /tmp/validate_one.py <<'PY'
import json,sys
path,gid,minw,maxw=sys.argv[1],sys.argv[2],int(sys.argv[3]),int(sys.argv[4])
text=open(path,encoding='utf-8',errors='replace').read()
def decode(marker):
    rows=[ln.split(marker,1)[1].strip() for ln in text.splitlines() if marker in ln]
    if not rows: raise AssertionError(f'{gid}: missing {marker}')
    obj=json.loads(rows[-1]); return json.loads(obj) if isinstance(obj,str) else obj
def need(c,m):
    if not c: raise AssertionError(f'{gid}: {m}')
h=decode('HOME_QA='); g=decode('QA_SNAPSHOT='); r=decode('RESULT_SNAPSHOT=')
need(h.get('cards')==100 and h.get('unique')==100,'catalog count')
need(h.get('aiCards')==12,'AI card count')
need(h.get('bodyOverflow') is False and h.get('titleIssues')==[],'catalog layout')
vp=h.get('viewport') or [0,0]; need(minw<=int(vp[0])<=maxw,f'viewport {vp}')
need(f'QA_OPEN game={gid} opened=true' in text,'open failed')
need(f'QA_FINISH game={gid} finished=true' in text,'finish hook failed')
need(g.get('game')==gid,'wrong active game')
need(bool(str(g.get('title','')).strip()) and bool(str(g.get('objective','')).strip()),'title/objective')
need(int(g.get('mountChars',0))>20 and int(g.get('mountChildren',0))>0,'empty playfield')
need(g.get('bodyOverflow') is False and g.get('mountOverflow') is False,'playfield overflow')
if gid=='checkers': need(g.get('aiSelector') is True and g.get('aiMode')=='ai','AI not default')
need(r.get('open') is True,'result closed')
need(bool(str(r.get('title','')).strip()) and bool(str(r.get('message','')).strip()) and bool(str(r.get('goal','')).strip()),'result text')
need(len(r.get('buttons') or [])==3 and all(str(x).strip() for x in r.get('buttons')),'result buttons')
need(r.get('bodyOverflow') is False,'result overflow')
print(f'QA_OK w412-retest {gid}')
PY

run_one_w412() {
  local GAME="$1" LOG="qa-w412-$1.log" READY=0
  adb shell wm density 420
  adb shell pm clear "$PKG" >/dev/null
  adb logcat -c
  adb shell am start -W -n "$ACT" --es testGame "$GAME" --ez testFinish true >/dev/null
  for i in $(seq 1 30); do
    adb logcat -d -s Offline100:I '*:S' > "$LOG"
    if grep -q 'RESULT_SNAPSHOT=' "$LOG"; then READY=1; break; fi
    if grep -Eq 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE|MAIN_FRAME_ERROR|QA_READY_TIMEOUT' "$LOG"; then
      echo "w412/$GAME: Android/WebView crash" >&2; cat "$LOG" >&2; return 1
    fi
    sleep 1
  done
  [[ "$READY" -eq 1 ]]
  python3 /tmp/validate_one.py "$LOG" "$GAME" 400 420
}

run_suite() {
  local LABEL="$1" DENSITY="$2" MINW="$3" MAXW="$4"
  local LOG="qa-$LABEL.log" DONE=0
  adb shell wm density "$DENSITY"
  adb shell pm clear "$PKG" >/dev/null
  adb logcat -c
  adb shell am start -W -n "$ACT" --ez testAll true --ei testFrom 50 >/dev/null
  for i in $(seq 1 360); do
    adb logcat -d -s Offline100:I '*:S' > "$LOG"
    if grep -q 'QA_ALL_DONE count=50 start=50' "$LOG"; then DONE=1; break; fi
    if grep -Eq 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE|MAIN_FRAME_ERROR|QA_READY_TIMEOUT' "$LOG"; then
      echo "$LABEL: Android/WebView crash" >&2; cat "$LOG" >&2; return 1
    fi
    sleep 1
  done
  [[ "$DONE" -eq 1 ]]
  adb logcat -d -s Offline100:I '*:S' > "$LOG"
  adb shell pidof "$PKG" | grep -q '[0-9]'
  python3 /tmp/validate_tail.py "$LOG" "$LABEL" "$MINW" "$MAXW" "$TAIL50"
}

# Retest only the nine w412 failures from run #37; no already-passed w412 games are relaunched.
: > qa-summary-w412-retest.log
for GAME in "${W412_RETEST[@]}"; do
  run_one_w412 "$GAME" | tee -a qa-summary-w412-retest.log
done

# The remaining half has never been checked at these narrower widths, so test exactly games 51-100.
run_suite w360 480 350 370
run_suite w320 540 310 330

adb shell wm density reset
cat qa-summary-w412-retest.log qa-summary-w360.log qa-summary-w320.log > qa-all-games-summary.log
echo 'QA_TAIL_OK original-first-half-not-run; w412 failed-only=9/9; w360 tail=50/50; w320 tail=50/50; results matched; failures=0'
