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
# The test front door of the emulator test APK (http://10.0.2.2:8080 in the app = port 8080 of this runner): an empty
# folder, so every lookup answers 404 and the app goes on to the pro server (chat.leveragers.com), as the real front
# door does for an address it has no file for.
FRONT_DOOR_DIR="$(mktemp -d)"
python3 -m http.server 8080 --bind 127.0.0.1 --directory "$FRONT_DOOR_DIR" >/dev/null 2>&1 &
FRONT_DOOR_PID=$!
sleep 1
~/.maestro/bin/maestro test -e APP_EMAIL="${APP_EMAIL:-}" -e TEST_EMAIL="$TEST_EMAIL" -e TEST_PASSWORD="$TEST_PASSWORD" "$FLOW"
STATUS=$?
echo "maestro status: $STATUS"
# Is the app signed in? Polled right away (POLL_NOW=1, as a user staring at the screen would not) or after 20 s.
[ "${POLL_NOW:-0}" = "1" ] || sleep 20
~/.maestro/bin/maestro test -e APP_ID="$PKG" .maestro-leveragers/assert-signed-in.yaml
SIGNED_IN=$?
echo "signed-in check (POLL_NOW=${POLL_NOW:-0}): $SIGNED_IN"
[ "$STATUS" = "0" ] && STATUS=$SIGNED_IN
# Google Play screenshots (STORE_SCREENSHOTS=1): the app in French (per-app language, Android 13) and a clean status
# bar (System UI demo mode: fixed time, full battery and signal, no notification icons).
if [ "${STORE_SCREENSHOTS:-0}" = "1" ]; then
  # The app may stay on the spinning sign-in button after the hand-back although the session exists (seen with
  # Element's official app too): reopen it, as a user would, before switching it to French.
  adb shell am force-stop "$PKG"
  adb shell cmd locale set-app-locales "$PKG" --locales fr-FR
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 30
  adb shell settings put global sysui_demo_allowed 1
  adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype none -e level 4 >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
  sleep 5
  ~/.maestro/bin/maestro test .maestro-leveragers/store-screenshots.yaml
  echo "store screenshots: $?"
fi
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
kill "$FRONT_DOOR_PID" 2>/dev/null
echo "--- maestro.log (tail) ---"
find ~/.maestro/tests -name maestro.log -exec tail -n 40 {} + | grep -E 'COMPLETED|FAILED|SKIPPED|ERROR' | tail -30
exit $STATUS
