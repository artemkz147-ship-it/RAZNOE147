#!/usr/bin/env bash
set -euo pipefail
APK="offline100-stable/app/build/outputs/apk/debug/app-debug.apk"
PKG="ru.offline100.games"
ACT="$PKG/.MainActivity"
TAIL50="pyramid13,suithunt,cardmemory,exact21,tenpairs,cipher,proverb,missing,wordbuild,categoryword,takuzu,latin,knight,arrows,flood,gears,balance,numsort,timer,ballsort,shells,mole,lanes,zigzag,precision,solpairs,redblack,cardfour,cardstairs,cardsum,wordfrom,oddletter,alphabet,syllables,wordlength,sequence,parity,colorlinks,tiles3,codebreak,tap30,stopsignal,orbit,coinfall,minigolf,emojimem,changed,battleship,checkers,escape"
W360_RETEST=(cipher categoryword)

adb install -r "$APK"

cat >/tmp/validate_one.py <<'PY'
import json,sys
path,gid,minw,maxw,label=sys.argv[1],sys.argv[2],int(sys.argv[3]),int(sys.argv[4]),sys.argv[5]
t=open(path,encoding='utf-8',errors='replace').read()
def dec(marker):
 r=[x.split(marker,1)[1].strip() for x in t.splitlines() if marker in x]
 if not r: raise AssertionError(f'{label}/{gid}: missing {marker}')
 o=json.loads(r[-1]); return json.loads(o) if isinstance(o,str) else o
def need(c,m):
 if not c: raise AssertionError(f'{label}/{gid}: {m}')
h=dec('HOME_QA='); g=dec('QA_SNAPSHOT='); r=dec('RESULT_SNAPSHOT=')
need(h.get('cards')==100 and h.get('unique')==100,'catalog count')
need(h.get('aiCards')==12,'AI card count')
need(h.get('bodyOverflow') is False and h.get('titleIssues')==[],'catalog layout')
vp=h.get('viewport') or [0,0]; need(minw<=int(vp[0])<=maxw,f'viewport {vp}')
need(f'QA_OPEN game={gid} opened=true' in t,'open failed')
need(f'QA_FINISH game={gid} finished=true' in t,'finish failed')
need(g.get('game')==gid,'wrong active game')
need(bool(str(g.get('title','')).strip()),'empty title')
need(bool(str(g.get('objective','')).strip()),'empty objective')
need(int(g.get('mountChars',0))>20 and int(g.get('mountChildren',0))>0,'empty playfield')
need(g.get('bodyOverflow') is False,'body overflow')
need(g.get('mountOverflow') is False,'playfield overflow')
if gid=='checkers': need(g.get('aiSelector') is True and g.get('aiMode')=='ai','AI not default')
need(r.get('open') is True,'result closed')
need(bool(str(r.get('title','')).strip()),'empty result title')
need(bool(str(r.get('message','')).strip()),'empty result message')
need(bool(str(r.get('goal','')).strip()),'result missing goal')
need(len(r.get('buttons') or [])==3 and all(str(x).strip() for x in r.get('buttons')),'bad result buttons')
need(r.get('bodyOverflow') is False,'result overflow')
print(f'QA_OK {label} {gid}')
PY

cat >/tmp/validate_tail.py <<'PY'
import json,re,sys
path,label,minw,maxw,csv=sys.argv[1],sys.argv[2],int(sys.argv[3]),int(sys.argv[4]),sys.argv[5]
expected=csv.split(','); t=open(path,encoding='utf-8',errors='replace').read()
def decode(raw):
 o=json.loads(raw.strip()); return json.loads(o) if isinstance(o,str) else o
def last(marker):
 r=[x.split(marker,1)[1].strip() for x in t.splitlines() if marker in x]
 if not r: raise AssertionError(f'{label}: missing {marker}')
 return decode(r[-1])
def need(c,m):
 if not c: raise AssertionError(f'{label}: {m}')
h=last('HOME_QA='); vp=h.get('viewport') or [0,0]
need(h.get('cards')==100 and h.get('unique')==100,f'catalog count {h}')
need(h.get('aiCards')==12,f'AI cards {h}')
need(h.get('bodyOverflow') is False and h.get('titleIssues')==[],f'catalog layout {h}')
need(minw<=int(vp[0])<=maxw,f'viewport {vp}')
opens={m.group(1):m.group(2).strip() for m in re.finditer(r'QA_ALL_OPEN ([a-z0-9]+)=([^\r\n]+)',t)}
fin={m.group(1):m.group(2).strip() for m in re.finditer(r'QA_ALL_FINISH ([a-z0-9]+)=([^\r\n]+)',t)}
games={m.group(1):decode(m.group(2)) for m in re.finditer(r'QA_ALL_GAME ([a-z0-9]+)=(.+)$',t,re.M)}
res={m.group(1):decode(m.group(2)) for m in re.finditer(r'QA_ALL_RESULT ([a-z0-9]+)=(.+)$',t,re.M)}
err=[]
for gid in expected:
 if opens.get(gid)!='true': err.append(f'{gid}: open={opens.get(gid)}')
 if fin.get(gid)!='true': err.append(f'{gid}: finish={fin.get(gid)}')
 g=games.get(gid); r=res.get(gid)
 if not g: err.append(f'{gid}: no game snapshot'); continue
 if g.get('game')!=gid: err.append(f'{gid}: active={g.get("game")}')
 if not str(g.get('title','')).strip(): err.append(f'{gid}: empty title')
 if not str(g.get('objective','')).strip(): err.append(f'{gid}: empty objective')
 if int(g.get('mountChars',0))<=20 or int(g.get('mountChildren',0))<=0: err.append(f'{gid}: empty playfield')
 if g.get('bodyOverflow') is not False: err.append(f'{gid}: body overflow')
 if g.get('mountOverflow') is not False: err.append(f'{gid}: playfield overflow')
 if gid=='checkers' and not (g.get('aiSelector') is True and g.get('aiMode')=='ai'): err.append('checkers: AI not default')
 if not r: err.append(f'{gid}: no result snapshot'); continue
 if r.get('open') is not True: err.append(f'{gid}: result closed')
 if not str(r.get('title','')).strip(): err.append(f'{gid}: empty result title')
 if not str(r.get('message','')).strip(): err.append(f'{gid}: empty result message')
 if not str(r.get('goal','')).strip(): err.append(f'{gid}: result missing goal')
 if len(r.get('buttons') or [])!=3 or not all(str(x).strip() for x in r.get('buttons') or []): err.append(f'{gid}: bad result buttons')
 if r.get('bodyOverflow') is not False: err.append(f'{gid}: result overflow')
if set(games)!=set(expected): err.append(f'game set {len(games)}/{len(expected)}')
if set(res)!=set(expected): err.append(f'result set {len(res)}/{len(expected)}')
open(f'qa-summary-{label}.log','w',encoding='utf-8').write(f'VIEWPORT {vp}\nOPEN {len(games)}/{len(expected)}\nFINAL {len(res)}/{len(expected)}\nFAILURES {len(err)}\n'+''.join('FAIL '+e+'\n' for e in err))
if err:
 print('\n'.join(f'{label}: FAIL {e}' for e in err)); raise SystemExit(1)
print(f'QA_OK {label} open=50 final=50 failures=0')
PY

run_one(){
 local LABEL="$1" DENSITY="$2" MINW="$3" MAXW="$4" GAME="$5"
 local LOG="qa-${LABEL}-${GAME}.log" READY=0
 adb shell wm density "$DENSITY"
 adb shell pm clear "$PKG" >/dev/null
 adb logcat -c
 adb shell am start -W -n "$ACT" --es testGame "$GAME" --ez testFinish true >/dev/null
 for i in $(seq 1 35); do
   adb logcat -d -s Offline100:I '*:S' >"$LOG"
   if grep -q 'RESULT_SNAPSHOT=' "$LOG"; then READY=1; break; fi
   if grep -Eq 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE|MAIN_FRAME_ERROR|QA_READY_TIMEOUT' "$LOG"; then cat "$LOG" >&2; return 1; fi
   sleep 1
 done
 [[ "$READY" -eq 1 ]]
 python3 /tmp/validate_one.py "$LOG" "$GAME" "$MINW" "$MAXW" "$LABEL"
}

run_tail320(){
 local LOG="qa-w320.log" DONE=0
 adb shell wm density 540
 adb shell pm clear "$PKG" >/dev/null
 adb logcat -c
 adb shell am start -W -n "$ACT" --ez testAll true --ei testFrom 50 >/dev/null
 for i in $(seq 1 360); do
   adb logcat -d -s Offline100:I '*:S' >"$LOG"
   if grep -q 'QA_ALL_DONE count=50 start=50' "$LOG"; then DONE=1; break; fi
   if grep -Eq 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE|MAIN_FRAME_ERROR|QA_READY_TIMEOUT' "$LOG"; then cat "$LOG" >&2; return 1; fi
   sleep 1
 done
 [[ "$DONE" -eq 1 ]]
 adb logcat -d -s Offline100:I '*:S' >"$LOG"
 adb shell pidof "$PKG" | grep -q '[0-9]'
 python3 /tmp/validate_tail.py "$LOG" w320 310 330 "$TAIL50"
}

: >qa-summary-w360-retest.log
for GAME in "${W360_RETEST[@]}"; do run_one w360-retest 480 350 370 "$GAME" | tee -a qa-summary-w360-retest.log; done
run_tail320
adb shell wm density reset
cat qa-summary-w360-retest.log qa-summary-w320.log >qa-all-games-summary.log
echo 'QA_TAIL_OK no-first-50; w360-only-failures=2/2; w320-tail=50/50; results=all; failures=0'
