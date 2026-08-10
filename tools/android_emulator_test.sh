#!/usr/bin/env bash
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
AVD_NAME="umk3_ci_api31"
PKG="com.raznoe147.umk3hd"
ACTIVITY="$PKG/.MainActivity"
EMU="$ANDROID_HOME/emulator/emulator"

rm -f emulator-*.log emulator-*.txt emulator-*.png emulator.pid

capture_diag() {
  adb logcat -d -v threadtime > emulator-full.log 2>/dev/null || true
  adb logcat -d -s UMK3HD:V '*:S' > emulator-runtime.log 2>/dev/null || true
  adb shell dumpsys meminfo "$PKG" > emulator-meminfo.txt 2>/dev/null || true
  adb shell dumpsys activity activities > emulator-activities.txt 2>/dev/null || true
  adb shell dumpsys window displays > emulator-window.txt 2>/dev/null || true
  adb exec-out screencap -p > emulator-diagnostic.png 2>/dev/null || true
}
cleanup() {
  rc=$?
  capture_diag
  adb emu kill >/dev/null 2>&1 || true
  if [[ -f emulator.pid ]]; then kill "$(cat emulator.pid)" >/dev/null 2>&1 || true; fi
  exit "$rc"
}
trap cleanup EXIT

# Recreate a clean AVD so launch tests are reproducible.
avdmanager delete avd -n "$AVD_NAME" >/dev/null 2>&1 || true
echo no | avdmanager create avd --force -n "$AVD_NAME" -k 'system-images;android-31;google_apis;x86_64' -d pixel_6 > avd-create.log

ACCEL=(-accel off)
if [[ -e /dev/kvm && -r /dev/kvm && -w /dev/kvm ]]; then ACCEL=(-accel on); fi
printf 'ACCEL=%s\n' "${ACCEL[*]}" | tee emulator-accel.txt

nohup "$EMU" -avd "$AVD_NAME" \
  -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim \
  -camera-back none -camera-front none -memory 2048 -cores 2 "${ACCEL[@]}" \
  > emulator-process.log 2>&1 &
echo $! > emulator.pid

# Do not let a broken emulator process hang CI forever.
timeout 180 adb wait-for-device
boot=0
for i in $(seq 1 180); do
  if ! kill -0 "$(cat emulator.pid)" 2>/dev/null; then
    echo 'Emulator process exited before Android booted' >&2
    tail -200 emulator-process.log >&2 || true
    exit 1
  fi
  value="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$value" == "1" ]]; then boot=1; break; fi
  sleep 1
done
test "$boot" -eq 1
adb shell input keyevent 82 || true
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 1 || true
sleep 2

adb shell getprop > emulator-props.txt
adb shell cmd webviewupdate getCurrentWebViewPackage > emulator-webview.txt 2>&1 || true
adb devices -l > emulator-devices.txt

adb install -r "$APK" | tee emulator-install.txt
adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" | tee emulator-start.txt

resumed=0
for i in $(seq 1 20); do
  if adb shell dumpsys activity activities | grep -q "mResumedActivity.*$PKG"; then resumed=1; break; fi
  sleep 1
done
test "$resumed" -eq 1

ready=0
for i in $(seq 1 35); do
  adb logcat -d -s UMK3HD:I '*:S' > emulator-runtime-info.log
  if grep -q 'Runtime check: true' emulator-runtime-info.log; then ready=1; break; fi
  if grep -qE 'Fatal boot error|rendererGone|PAGE_LOAD_TIMEOUT|WEB_RUNTIME_NOT_READY|WEB error' emulator-runtime-info.log; then break; fi
  sleep 1
done
cat emulator-runtime-info.log
adb exec-out screencap -p > emulator-title.png
test "$ready" -eq 1

# Resolve the actual landscape display and the letterboxed 1280x720 game canvas.
read W H < <(python3 - <<'PY'
import re,subprocess
s=subprocess.check_output(['adb','shell','wm','size'],text=True)
m=re.findall(r'(\d+)x(\d+)',s)
w,h=map(int,m[-1])
if w<h:w,h=h,w
print(w,h)
PY
)
read TITLE_X TITLE_Y SCORPION_X SCORPION_Y TOWER_X TOWER_Y < <(python3 - "$W" "$H" <<'PY'
import sys
W,H=map(float,sys.argv[1:])
s=min(W/1280.0,H/720.0);ox=(W-1280*s)/2;oy=(H-720*s)/2
def p(x,y):return round(ox+x*s),round(oy+y*s)
pts=[p(640,396),p(1036,153),p(640,396)]
print(*(v for pt in pts for v in pt))
PY
)
printf 'SCREEN=%sx%s CANVAS_TITLE=%s,%s SCORPION=%s,%s TOWER=%s,%s\n' "$W" "$H" "$TITLE_X" "$TITLE_Y" "$SCORPION_X" "$SCORPION_Y" "$TOWER_X" "$TOWER_Y" | tee emulator-touch.txt

# Title -> Select.
adb shell input tap "$TITLE_X" "$TITLE_Y"
sleep 2
adb exec-out screencap -p > emulator-select.png

# Select Scorpion using logical portrait coordinates transformed through the letterbox.
adb shell input tap "$SCORPION_X" "$SCORPION_Y"
sleep 2
adb exec-out screencap -p > emulator-tower.png

# Tower -> Fight.
adb shell input tap "$TOWER_X" "$TOWER_Y"
sleep 5

# Android controls are CSS overlays positioned against the physical viewport.
# High Punch center: actions right 22, container 306x218, HP right 84/top 0, button 82.
HP_X=$((W-22-84-41)); HP_Y=$((H-18-218+41))
# Analog pad center: left 24 / bottom 22 / size 190.
PAD_X=$((24+95)); PAD_Y=$((H-22-95)); PAD_RIGHT=$((PAD_X+58))
printf 'HP=%s,%s PAD=%s,%s->%s,%s\n' "$HP_X" "$HP_Y" "$PAD_X" "$PAD_Y" "$PAD_RIGHT" "$PAD_Y" | tee -a emulator-touch.txt

adb shell input tap "$HP_X" "$HP_Y"
sleep 1
adb shell input swipe "$PAD_X" "$PAD_Y" "$PAD_RIGHT" "$PAD_Y" 500
sleep 2

adb logcat -d -s UMK3HD:V '*:S' | tee emulator-touch.log
grep -q 'UMK3_STATE=select' emulator-touch.log
grep -q 'UMK3_STATE=tower' emulator-touch.log
grep -q 'UMK3_STATE=fight' emulator-touch.log
if adb logcat -d -v brief | grep -q 'FATAL EXCEPTION'; then exit 1; fi

adb exec-out screencap -p > emulator-fight.png
test -s emulator-fight.png

# Keep final diagnostics, then disable EXIT trap's error semantics and cleanly stop the emulator.
capture_diag
trap - EXIT
adb emu kill >/dev/null 2>&1 || true
exit 0
