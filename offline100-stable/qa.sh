#!/usr/bin/env bash
set -euo pipefail
APK="offline100-stable/app/build/outputs/apk/debug/app-debug.apk"
PKG="ru.offline100.games"
ACT="$PKG/.MainActivity"

fail_on_crash() {
  local log="$1"
  if grep -E 'FATAL EXCEPTION|STARTUP_FAILED|WEBVIEW_RENDERER_GONE' "$log"; then
    echo "Crash marker in $log" >&2
    exit 42
  fi
}

capture_log_until() {
  local file="$1"
  local pattern="$2"
  local tries="${3:-10}"
  local i
  for ((i=1;i<=tries;i++)); do
    adb logcat -d > "$file"
    if grep -q "$pattern" "$file"; then return 0; fi
    sleep 1
  done
  adb logcat -d > "$file"
  echo "Timed out waiting for $pattern in $file" >&2
  return 1
}

adb install -r "$APK"

adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT"
capture_log_until qa-home.log 'Offline100.*GAME_CARD_COUNT="100"' 10
adb exec-out screencap -p > qa-home.png
grep -q 'Offline100.*PAGE_FINISHED' qa-home.log
grep -q 'Offline100.*GAME_CARD_COUNT="100"' qa-home.log
fail_on_crash qa-home.log
adb shell pidof "$PKG" | grep -q '[0-9]'
adb shell dumpsys activity activities | grep -q "$PKG/.MainActivity"

for GAME in pipes parking; do
  adb logcat -c
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$ACT" --es testGame "$GAME"
  capture_log_until "qa-$GAME.log" "QA_SNAPSHOT=.*$GAME" 10
  adb exec-out screencap -p > "qa-$GAME.png"
  grep -q "QA_SNAPSHOT=.*$GAME" "qa-$GAME.log"
  grep -q 'QA_SNAPSHOT=.*objective' "qa-$GAME.log"
  fail_on_crash "qa-$GAME.log"
  adb shell pidof "$PKG" | grep -q '[0-9]'
done

for GAME in ttt connect4 reversi gomoku nim dots checkers; do
  adb logcat -c
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$ACT" --es testGame "$GAME"
  capture_log_until "qa-$GAME.log" "QA_SNAPSHOT=.*$GAME" 12
  grep -q "QA_SNAPSHOT=.*$GAME" "qa-$GAME.log"
  grep -q 'QA_SNAPSHOT=.*aiSelector.*true' "qa-$GAME.log"
  fail_on_crash "qa-$GAME.log"
  adb shell pidof "$PKG" | grep -q '[0-9]'
done
adb exec-out screencap -p > qa-checkers-ai.png

adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" --es testGame ttt --ez testFinish true
capture_log_until qa-result.log 'RESULT_SNAPSHOT=' 12
adb exec-out screencap -p > qa-result.png
grep -q 'RESULT_SNAPSHOT=.*open.*true' qa-result.log
grep -q 'RESULT_SNAPSHOT=.*Цель выполнена' qa-result.log
fail_on_crash qa-result.log
adb shell pidof "$PKG" | grep -q '[0-9]'

echo "QA_OK"
