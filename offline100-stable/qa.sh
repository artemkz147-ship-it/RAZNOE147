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

adb install -r "$APK"

adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT"
sleep 5
adb logcat -d > qa-home.log
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
  sleep 5
  adb logcat -d > "qa-$GAME.log"
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
  sleep 4
  adb logcat -d > "qa-$GAME.log"
  grep -q "QA_SNAPSHOT=.*$GAME" "qa-$GAME.log"
  grep -q 'QA_SNAPSHOT=.*aiSelector.*true' "qa-$GAME.log"
  fail_on_crash "qa-$GAME.log"
  adb shell pidof "$PKG" | grep -q '[0-9]'
done
adb exec-out screencap -p > qa-checkers-ai.png

adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" --es testGame ttt --ez testFinish true
sleep 5
adb logcat -d > qa-result.log
adb exec-out screencap -p > qa-result.png
grep -q 'RESULT_SNAPSHOT=.*open.*true' qa-result.log
grep -q 'RESULT_SNAPSHOT=.*Цель выполнена' qa-result.log
fail_on_crash qa-result.log
adb shell pidof "$PKG" | grep -q '[0-9]'

echo "QA_OK"
