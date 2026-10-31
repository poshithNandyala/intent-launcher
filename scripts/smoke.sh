#!/usr/bin/env bash
# Emulator smoke test: installs the APK, walks through setup and the main screens, and records
# a screenshot, the visible text and any crash at every step (in smoke/).
set -u
PKG=com.poshith.intent
OUT=smoke
mkdir -p "$OUT"
APK=$(ls out/Intent-*.apk | head -1)
n=0

crashed() { [ -s "$OUT/crash.txt" ]; }
record() { # name
    n=$((n + 1))
    local name
    name=$(printf "%02d-%s" "$n" "$1")
    sleep 2
    adb exec-out screencap -p > "$OUT/$name.png" 2>/dev/null
    python3 scripts/ui.py texts > "$OUT/$name.txt" 2>&1
    adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
    echo "== $name (pid: $(adb shell pidof $PKG || echo none))"
}
tap() { python3 scripts/ui.py tap "$1" || echo "   (couldn't find \"$1\")"; }
size=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1)
W=${size%x*}; H=${size#*x}
swipe() { # x1 y1 x2 y2 in percent of the screen
    adb shell input swipe $(($1 * W / 100)) $(($2 * H / 100)) $(($3 * W / 100)) $(($4 * H / 100)) 180
}

adb shell getprop ro.build.version.release > "$OUT/android.txt"
adb install -r -g "$APK" > "$OUT/install.txt" 2>&1
cat "$OUT/install.txt"
adb logcat -c
adb logcat -b crash -c

adb shell am start -W -n $PKG/.HomeActivity > "$OUT/start.txt" 2>&1
record launch

# Setup
tap "Begin";              record essentials
tap "Next";               record pull
tap "Next";               record plan
tap "Next";               record protect
tap "Next";               record quiet
tap "Next";               record done
tap "Start using Intent"; record home-role
adb shell input keyevent KEYCODE_BACK
adb shell am start -W -n $PKG/.HomeActivity > /dev/null 2>&1
record home

# Main gestures
swipe 50 80 50 25;  record library
adb shell input text "set"; record search
adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK
swipe 15 50 85 50;  record mind
adb shell input keyevent KEYCODE_BACK
adb shell input swipe "$((W / 2))" "$((H / 2))" "$((W / 2))" "$((H / 2))" 900
record long-press
tap "Settings";           record settings
adb shell input keyevent KEYCODE_BACK
adb shell am start -W -n $PKG/.HomeActivity > /dev/null 2>&1
tap "Focus";              record focus-sheet

adb logcat -d -v time > "$OUT/logcat.txt" 2>&1
if crashed; then echo "CRASH:"; cat "$OUT/crash.txt"; fi

# The safety net: a forced crash must show Intent's own error screen, not just close.
adb shell am crash $PKG > /dev/null 2>&1
sleep 4
adb exec-out screencap -p > "$OUT/99-crash-screen.png" 2>/dev/null
python3 scripts/ui.py texts > "$OUT/99-crash-screen.txt" 2>&1
exit 0
