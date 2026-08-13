#!/usr/bin/env bash
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
AVD_NAME="umk3_ci_api31"
PKG="com.raznoe147.umk3hd"
ACTIVITY="$PKG/.MainActivity"
EMU="$ANDROID_HOME/emulator/emulator"
ADB_TIMEOUT=12
ADB_LONG_TIMEOUT=120

export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$PWD/.android-user}"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$PWD/.android-avd}"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"

rm -f emulator-*.log emulator-*.txt emulator-*.png emulator.pid

adb_t() { timeout --signal=KILL "${ADB_TIMEOUT}s" adb "$@"; }
adb_long() { timeout --signal=KILL "${ADB_LONG_TIMEOUT}s" adb "$@"; }
adb_quiet() { timeout --signal=KILL "${ADB_TIMEOUT}s" adb "$@" >/dev/null 2>&1 || true; }

capture_diag() {
  adb_t logcat -d -v threadtime > emulator-full.log 2>/dev/null || true
  adb_t logcat -d -s UMK3HD:V '*:S' > emulator-runtime.log 2>/dev/null || true
  adb_t shell dumpsys meminfo "$PKG" > emulator-meminfo.txt 2>/dev/null || true
  adb_t shell dumpsys activity activities > emulator-activities.txt 2>/dev/null || true
  adb_t shell dumpsys window displays > emulator-window.txt 2>/dev/null || true
  adb_t exec-out screencap -p > emulator-diagnostic.png 2>/dev/null || true
  tail -300 emulator-process.log > emulator-process-tail.log 2>/dev/null || true
}
cleanup() {
  rc=$?
  set +e
  capture_diag
  adb_quiet emu kill
  if [[ -f emulator.pid ]]; then
    pid="$(cat emulator.pid 2>/dev/null || true)"
    [[ -n "$pid" ]] && kill "$pid" >/dev/null 2>&1 || true
    sleep 1
    [[ -n "$pid" ]] && kill -9 "$pid" >/dev/null 2>&1 || true
  fi
  exit "$rc"
}
trap cleanup EXIT

rm -rf "$ANDROID_AVD_HOME/$AVD_NAME.avd" "$ANDROID_AVD_HOME/$AVD_NAME.ini"
echo no | avdmanager create avd --force -n "$AVD_NAME" -k 'system-images;android-31;google_apis;x86_64' -d pixel_6 -p "$ANDROID_AVD_HOME/$AVD_NAME.avd" > avd-create.log
"$EMU" -list-avds | tee emulator-avds.txt
grep -qx "$AVD_NAME" emulator-avds.txt

if [[ ! -e /dev/kvm || ! -r /dev/kvm || ! -w /dev/kvm ]]; then
  echo 'KVM is unavailable; refusing an unbounded software-emulation run.' | tee emulator-accel.txt
  ls -l /dev/kvm >> emulator-accel.txt 2>&1 || true
  exit 2
fi
echo 'ACCEL=-accel on' | tee emulator-accel.txt

nohup "$EMU" -avd "$AVD_NAME" \
  -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim \
  -camera-back none -camera-front none -memory 2048 -cores 2 -accel on \
  > emulator-process.log 2>&1 &
echo $! > emulator.pid

transport=0
for i in $(seq 1 90); do
  if ! kill -0 "$(cat emulator.pid)" 2>/dev/null; then
    echo 'Emulator process exited before adb transport appeared' >&2
    tail -200 emulator-process.log >&2 || true
    exit 1
  fi
  if timeout 3s adb get-state 2>/dev/null | grep -q '^device$'; then transport=1; break; fi
  sleep 1
done
if [[ "$transport" -ne 1 ]]; then
  echo 'ADB transport did not become ready within 90 seconds' >&2
  tail -200 emulator-process.log >&2 || true
  exit 1
fi

boot=0
for i in $(seq 1 120); do
  if ! kill -0 "$(cat emulator.pid)" 2>/dev/null; then
    echo 'Emulator process exited before Android booted' >&2
    tail -200 emulator-process.log >&2 || true
    exit 1
  fi
  value="$(timeout 5s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$value" == "1" ]]; then boot=1; break; fi
  if (( i % 15 == 0 )); then echo "boot wait ${i}s"; tail -20 emulator-process.log || true; fi
  sleep 1
done
if [[ "$boot" -ne 1 ]]; then
  echo 'Android did not report sys.boot_completed=1 within 120 seconds' >&2
  exit 1
fi

pm_ready=0
for i in $(seq 1 60); do
  if timeout 8s adb shell pm path android 2>/dev/null | grep -q '^package:'; then
    pm_ready=1; echo "PACKAGE_MANAGER_READY_AT=${i}s" | tee emulator-pm-ready.txt; break
  fi
  sleep 1
done
if [[ "$pm_ready" -ne 1 ]]; then
  echo 'Package Manager did not become ready within 60 seconds after boot' >&2
  exit 1
fi

adb_t shell input keyevent 82 || true
adb_t shell settings put secure immersive_mode_confirmations confirmed || true
adb_t shell settings put system accelerometer_rotation 0 || true
adb_t shell settings put system user_rotation 1 || true
sleep 2

adb_t shell getprop > emulator-props.txt
adb_t shell cmd webviewupdate getCurrentWebViewPackage > emulator-webview.txt 2>&1 || true
adb_t devices -l > emulator-devices.txt

adb_long install -r -t "$APK" | tee emulator-install.txt
grep -q 'Success' emulator-install.txt
adb_t shell pm path "$PKG" | tee emulator-package.txt
grep -q 'package:' emulator-package.txt
adb_t logcat -c
adb_t shell am force-stop "$PKG"
adb_long shell am start -W -n "$ACTIVITY" | tee emulator-start.txt

resumed=0
for i in $(seq 1 20); do
  if timeout 6s adb shell dumpsys activity activities 2>/dev/null | grep -q "mResumedActivity.*$PKG"; then resumed=1; break; fi
  sleep 1
done
if [[ "$resumed" -ne 1 ]]; then echo 'MainActivity never became resumed' >&2; exit 1; fi

ready=0
for i in $(seq 1 35); do
  timeout 6s adb logcat -d -s UMK3HD:I '*:S' > emulator-runtime-info.log 2>/dev/null || true
  if grep -q 'Runtime check: true' emulator-runtime-info.log; then ready=1; break; fi
  if grep -qE 'Fatal boot error|rendererGone|PAGE_LOAD_TIMEOUT|WEB_RUNTIME_NOT_READY|WEB error' emulator-runtime-info.log; then break; fi
  sleep 1
done
cat emulator-runtime-info.log || true
adb_t exec-out screencap -p > emulator-title.png
if [[ "$ready" -ne 1 ]]; then echo 'Web runtime did not become ready' >&2; exit 1; fi

read W H < <(python3 - <<'PY'
import re,subprocess
s=subprocess.check_output(['timeout','8s','adb','shell','wm','size'],text=True)
m=re.findall(r'(\d+)x(\d+)',s); assert m,s
w,h=map(int,m[-1])
if w<h:w,h=h,w
print(w,h)
PY
)
read TITLE_X TITLE_Y SCORPION_X SCORPION_Y TOWER_X TOWER_Y < <(python3 - "$W" "$H" <<'PY'
import sys
W,H=map(float,sys.argv[1:]);s=min(W/1280.0,H/720.0);ox=(W-1280*s)/2;oy=(H-720*s)/2
def p(x,y):return round(ox+x*s),round(oy+y*s)
pts=[p(640,396),p(1036,153),p(640,396)]
print(*(v for pt in pts for v in pt))
PY
)
printf 'SCREEN=%sx%s CANVAS_TITLE=%s,%s SCORPION=%s,%s TOWER=%s,%s\n' "$W" "$H" "$TITLE_X" "$TITLE_Y" "$SCORPION_X" "$SCORPION_Y" "$TOWER_X" "$TOWER_Y" | tee emulator-touch.txt

wait_marker() {
  local marker="$1" seconds="${2:-12}" out="${3:-emulator-js-live.log}"
  for _ in $(seq 1 "$seconds"); do
    timeout 6s adb logcat -d -s UMK3HD:V '*:S' > "$out" 2>/dev/null || true
    if grep -q "$marker" "$out"; then return 0; fi
    sleep 1
  done
  echo "Timed out waiting for JS marker: $marker" >&2
  cat "$out" >&2 || true
  return 1
}

# Title -> Select, and wait for the actual JS state before taking screenshots.
adb_t shell input tap "$TITLE_X" "$TITLE_Y"
wait_marker 'UMK3_STATE=select' 15
adb_t exec-out screencap -p > emulator-select.png

# Select Scorpion -> Tower.
adb_t shell input tap "$SCORPION_X" "$SCORPION_Y"
wait_marker 'UMK3_STATE=tower' 15
adb_t exec-out screencap -p > emulator-tower.png

# Tower -> Fight.
adb_t shell input tap "$TOWER_X" "$TOWER_Y"
wait_marker 'UMK3_STATE=fight' 20
sleep 2

# Read the actual CSS viewport and control rectangles from WebView. This avoids
# guessing Android density, immersive insets, CSS media-query scale or letterbox.
wait_marker 'UMK3_TOUCH_RECT_HP=' 12 emulator-geometry.log
wait_marker 'UMK3_TOUCH_RECT_PAD=' 12 emulator-geometry.log
VIEW_LINE="$(grep 'UMK3_VIEWPORT=' emulator-geometry.log | tail -1)"
HP_LINE="$(grep 'UMK3_TOUCH_RECT_HP=' emulator-geometry.log | tail -1)"
PAD_LINE="$(grep 'UMK3_TOUCH_RECT_PAD=' emulator-geometry.log | tail -1)"
printf '%s\n%s\n%s\n' "$VIEW_LINE" "$HP_LINE" "$PAD_LINE" | tee emulator-dom-geometry.txt

read HP_X HP_Y PAD_X PAD_Y PAD_RIGHT < <(python3 - "$W" "$H" "$VIEW_LINE" "$HP_LINE" "$PAD_LINE" <<'PY'
import re,sys
W,H=map(float,sys.argv[1:3]);v,hp,pad=sys.argv[3:6]
m=re.search(r'UMK3_VIEWPORT=(\d+),(\d+),([\d.]+)',v);assert m,v
vw,vh=map(float,m.group(1,2))
def rect(line,key):
    m=re.search(rf'UMK3_TOUCH_RECT_{key}=(-?[\d.]+),(-?[\d.]+),([\d.]+),([\d.]+)',line);assert m,line
    return tuple(map(float,m.groups()))
hx,hy,hw,hh=rect(hp,'HP');px,py,pw,ph=rect(pad,'PAD')
sx=W/vw;sy=H/vh
# Tap exact HP center. Start the stick in its exact center and move 28% of its
# DOM width to the right, staying safely inside the circular pointer target.
hpc=((hx+hw*.5)*sx,(hy+hh*.5)*sy)
pc=((px+pw*.5)*sx,(py+ph*.5)*sy)
pr=((px+pw*.78)*sx,(py+ph*.5)*sy)
print(*(round(x) for x in (*hpc,*pc,pr[0])))
PY
)
printf 'DOM_HP=%s,%s DOM_PAD=%s,%s->%s,%s\n' "$HP_X" "$HP_Y" "$PAD_X" "$PAD_Y" "$PAD_RIGHT" "$PAD_Y" | tee -a emulator-touch.txt

adb_t shell input tap "$HP_X" "$HP_Y"
wait_marker 'UMK3_TOUCH=HP' 8
adb_t shell input swipe "$PAD_X" "$PAD_Y" "$PAD_RIGHT" "$PAD_Y" 500
wait_marker 'UMK3_TOUCH_DIR=right' 8
sleep 1

adb_t logcat -d -s UMK3HD:V '*:S' | tee emulator-touch.log
grep -q 'UMK3_STATE=select' emulator-touch.log
grep -q 'UMK3_STATE=tower' emulator-touch.log
grep -q 'UMK3_STATE=fight' emulator-touch.log
grep -q 'UMK3_TOUCH=HP' emulator-touch.log
grep -q 'UMK3_TOUCH_DIR=right' emulator-touch.log
if timeout 8s adb logcat -d -v brief 2>/dev/null | grep -q 'FATAL EXCEPTION'; then exit 1; fi

adb_t exec-out screencap -p > emulator-fight.png
test -s emulator-fight.png

capture_diag
trap - EXIT
adb_quiet emu kill
exit 0
