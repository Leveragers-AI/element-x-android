#!/bin/sh
# Leveragers: install an APK on the emulator, run a sign-in flow, then record what the app shows 60 s later and
# after a relaunch (screenshots + visible texts). Usage: run.sh <apk> <flow.yaml> <package>
set -u
APK="$1"; FLOW="$2"; PKG="$3"
# Chrome's first-run screens get in the way of the sign-in page (same trick as Element's own Maestro workflow).
adb shell am set-debug-app --persistent com.android.chrome
adb shell 'echo "chrome --disable-fre --no-default-browser-check --no-first-run" > /data/local/tmp/chrome-command-line'
adb shell am start -n com.android.chrome/com.google.android.apps.chrome.Main
sleep 5
adb install -r "$APK"
adb shell dumpsys package "$PKG" | grep -E 'versionName|versionCode' | head -2
mkdir -p shots ~/.maestro/tests
~/.maestro/bin/maestro test -e TEST_EMAIL="$TEST_EMAIL" -e TEST_PASSWORD="$TEST_PASSWORD" "$FLOW"
STATUS=$?
echo "maestro status: $STATUS"
capture() {
  adb exec-out screencap -p > "shots/$1.png"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  echo "--- visible texts ($1) ---"
  adb exec-out cat /sdcard/ui.xml | tr '>' '\n' | grep -o 'text="[^"]\+"' | sort -u | head -40
  adb shell dumpsys activity activities | grep -E 'topResumedActivity|ResumedActivity' | head -3
}
sleep 20
capture 09-after-20s
sleep 40
capture 10-after-60s
sleep 60
capture 10b-after-120s
adb shell am force-stop "$PKG"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 45
capture 11-after-relaunch
echo "--- maestro.log (tail) ---"
find ~/.maestro/tests -name maestro.log -exec tail -n 40 {} + | grep -E 'COMPLETED|FAILED|SKIPPED|ERROR' | tail -30
exit $STATUS
