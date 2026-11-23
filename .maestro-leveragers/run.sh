#!/bin/sh
# Leveragers: install the APK on the emulator and run the sign-in flow (called by .github/workflows/leveragers-android.yml).
set -u
APK="$1"
# Chrome's first-run screens get in the way of the sign-in page (same trick as Element's own Maestro workflow).
adb shell am set-debug-app --persistent com.android.chrome
adb shell 'echo "chrome --disable-fre --no-default-browser-check --no-first-run" > /data/local/tmp/chrome-command-line'
adb shell am start -n com.android.chrome/com.google.android.apps.chrome.Main
sleep 5
adb install -r "$APK"
adb shell dumpsys package com.leveragers.agents | grep -E 'versionName|versionCode' | head -2
mkdir -p shots ~/.maestro/tests
adb logcat '*:I' > ~/.maestro/tests/logcat.txt &
LOGCAT_PID=$!
~/.maestro/bin/maestro test --format junit --output ~/.maestro/tests/report.xml \
  -e TEST_EMAIL="$TEST_EMAIL" -e TEST_PASSWORD="$TEST_PASSWORD" .maestro-leveragers/signin.yaml
STATUS=$?
adb exec-out screencap -p > shots/99-last.png
kill "$LOGCAT_PID" 2>/dev/null
echo "--- maestro.log (tail) ---"
find ~/.maestro/tests -name maestro.log -exec tail -n 60 {} +
echo "--- logcat, OAuth and errors (tail) ---"
grep -iE 'oauth|oidc|login|AndroidRuntime|FATAL' ~/.maestro/tests/logcat.txt | grep -viE 'password|code=' | tail -n 60
exit $STATUS
