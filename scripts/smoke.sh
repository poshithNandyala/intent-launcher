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
# Only Intent's own crashes (the crash log also holds other apps', e.g. the uiautomator test tool).
intent_crashes() {
    adb logcat -d -b crash 2>/dev/null | awk '/FATAL EXCEPTION/{keep=0} /Process: com.poshith.intent/{keep=1} {buf[NR]=$0} keep{print}' > "$OUT/crash.txt"
}
record() { # name
    n=$((n + 1))
    local name
    name=$(printf "%02d-%s" "$n" "$1")
    sleep 2
    adb exec-out screencap -p > "$OUT/$name.png" 2>/dev/null
    python3 scripts/ui.py texts > "$OUT/$name.txt" 2>&1
    intent_crashes
    echo "== $name (pid: $(adb shell pidof $PKG || echo none))"
}
tap() { python3 scripts/ui.py tap "$1" || echo "   (couldn't find \"$1\")"; }
top() { adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity"; }
wait_top() { # activity-name seconds: wait until it's the resumed activity (slow emulators)
    for _ in $(seq 1 "$2"); do top | grep -q "$1" && return 0; sleep 1; done
    echo "   ($1 never came to the front)"
}
type_text() { # one key at a time: fast adb typing scrambles letters on some emulators
    local i
    for ((i = 0; i < ${#1}; i++)); do adb shell input text "${1:$i:1}"; done
}
# Screenshot only. uiautomator suspends other accessibility services while it runs, so Focus Guard
# steps are recorded without it.
snap() {
    n=$((n + 1))
    local name
    name=$(printf "%02d-%s" "$n" "$1")
    sleep 3
    adb exec-out screencap -p > "$OUT/$name.png" 2>/dev/null
    adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" > "$OUT/$name.txt" 2>&1
    intent_crashes
    echo "== $name: $(cat "$OUT/$name.txt")"
}
size=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1)
W=${size%x*}; H=${size#*x}
swipe() { # x1 y1 x2 y2 in percent of the screen
    adb shell input swipe $(($1 * W / 100)) $(($2 * H / 100)) $(($3 * W / 100)) $(($4 * H / 100)) 180
}

adb shell getprop ro.build.version.release > "$OUT/android.txt"
# Older emulators report "booted" well before they finish starting up; let them settle.
for _ in $(seq 1 60); do adb shell dumpsys activity | grep -q "mBooted=true\|BOOT_COMPLETED\|mBootAnimationComplete=true" && break; sleep 2; done
[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -lt 29 ] && sleep 30
[ "${NIGHT:-no}" = "yes" ] && adb shell cmd uimode night yes > /dev/null 2>&1
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
type_text "set"; record search
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK
swipe 15 50 85 50;  record mind
adb shell input keyevent KEYCODE_BACK
adb shell input swipe "$((W / 2))" "$((H / 2))" "$((W / 2))" "$((H / 2))" 900
record long-press
tap "Settings";           record settings
adb shell input keyevent KEYCODE_BACK
adb shell am start -W -n $PKG/.HomeActivity > /dev/null 2>&1
tap "Focus";              record focus-sheet
adb shell input keyevent KEYCODE_BACK

# The pause, opened from Intent's search (YouTube was suggested as a pull app during setup)
if adb shell pm list packages | grep -q "package:com.google.android.youtube"; then
    swipe 50 80 50 25
    type_text "YouTube"; sleep 1
    tap "YouTube";        record pause
    sleep 6;              record pause-after-breath
    tap "Not now";        record after-not-now

    # Focus Guard: the same app opened from outside Intent (like a notification) still gets the pause
    adb shell settings put secure enabled_accessibility_services $PKG/$PKG.guard.FocusGuardService
    adb shell settings put secure accessibility_enabled 1
    sleep 3
    adb shell monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
    wait_top GateActivity 15
    snap guard-pause
    adb shell input keyevent KEYCODE_BACK       # back = "Not now" on the pause screen
    snap guard-not-now
    sleep 3
    snap guard-stays-home
fi

# Intent as the real home app, and the check-in after unlocking
adb shell cmd role add-role-holder --user 0 android.app.role.HOME $PKG > /dev/null 2>&1 ||
    adb shell cmd package set-home-activity $PKG/.HomeActivity > /dev/null 2>&1
adb shell input keyevent KEYCODE_HOME
record default-home

# The long-press menu: everything visible, no keyboard
read -r mx my < <(python3 scripts/ui.py coords "Messages" | head -1)
if [ "$mx" -gt 0 ]; then adb shell input swipe "$mx" "$my" "$mx" "$my" 900; record app-menu; adb shell input keyevent KEYCODE_BACK; fi

if adb shell pm list packages | grep -q "package:com.google.android.youtube"; then
    # Is a 2-minute session really over after 2 minutes? Choose 2 min on the pause, then wait.
    adb shell input keyevent KEYCODE_HOME
    swipe 50 80 50 25
    type_text "YouTube"; sleep 1
    tap "YouTube"
    sleep 9 # the breath
    mapfile -t pts < <(python3 scripts/ui.py coords "Look something up" "2 min" "Choose what it")
    for p in "${pts[@]}"; do read -r x y <<< "$p"; [ "$x" -gt 0 ] && adb shell input tap "$x" "$y"; sleep 1; done
    sleep 2
    read -r ox oy < <(python3 scripts/ui.py coords "Open for 2 min" | head -1)
    sleep 3 # let Focus Guard reconnect after the screen read
    [ "$ox" -gt 0 ] && adb shell input tap "$ox" "$oy"
    wait_top youtube 20
    snap session-started
    sleep 135
    snap session-2-min-later    # expected: TimeUpActivity in front
    adb shell input keyevent KEYCODE_HOME
fi

# The check-in after unlocking, and "Just checking" locking the phone
adb shell input keyevent KEYCODE_SLEEP; sleep 2
adb shell input keyevent KEYCODE_WAKEUP; sleep 1
adb shell wm dismiss-keyguard; sleep 1
record unlock-check-in
tap "Just checking"
sleep 3
snap just-checking-locks
adb shell dumpsys window | grep -m1 -iE "mShowingLockscreen|isKeyguardShowing|KeyguardShowing" > "$OUT/lock-state.txt" 2>&1
adb shell input keyevent KEYCODE_WAKEUP; sleep 1; adb shell wm dismiss-keyguard; sleep 2

# A real break: just a timer, only calls
adb shell input keyevent KEYCODE_HOME
tap "Focus"; sleep 1
record quiet-sheet
tap "15 min"
tap "Start the break"
record break-screen
adb shell monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
wait_top GateActivity 15
snap break-blocks-youtube
adb shell input keyevent KEYCODE_BACK
sleep 2
read -r hx hy < <(python3 scripts/ui.py coords "Hold to end the break" | head -1)
[ "$hx" -gt 0 ] && adb shell input swipe "$hx" "$hy" "$hx" "$hy" 3800
record break-ended

# Basic phone: only essentials on the home screen, and swiping up must not open search
adb shell input keyevent KEYCODE_HOME
tap "Focus";              sleep 1
tap "Basic phone";        sleep 1
tap "Turn on basic phone"
record basic-on
swipe 50 80 50 25
record basic-swipe-up

adb logcat -d -v time > "$OUT/logcat.txt" 2>&1
if crashed; then echo "CRASH:"; cat "$OUT/crash.txt"; fi

# The safety net: a forced crash must show Intent's own error screen, not just close.
adb shell am crash $PKG > /dev/null 2>&1
sleep 4
adb exec-out screencap -p > "$OUT/99-crash-screen.png" 2>/dev/null
python3 scripts/ui.py texts > "$OUT/99-crash-screen.txt" 2>&1
exit 0
