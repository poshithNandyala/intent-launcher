#!/usr/bin/env bash
# Emulator smoke test: installs the APK, walks through setup and the main screens, and records
# a screenshot, the visible text and any crash at every step (in smoke/).
set -u
# No adb call may hang the run: a lost emulator connection makes adb wait forever ("waiting for
# device"), and the job then dies at its time limit with nothing to show. Every call gets a limit.
ADB_BIN=$(command -v adb)
adb() { timeout 120 "$ADB_BIN" "$@"; }
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
# A system "isn't responding" dialog over the screen: answer it, and keep the evidence (anr.txt) so a
# failure caused by another app's ANR is told apart from one of Intent's own.
answer_anr() { # step
    local said
    said=$(python3 scripts/ui.py dismiss 2>/dev/null) || return 0
    echo "$1: $said" >> "$OUT/anr.txt"
    adb logcat -d 2>/dev/null | grep -E "ANR in |Application Not Responding" | tail -3 >> "$OUT/anr.txt"
    sleep 1
}
record() { # name
    n=$((n + 1))
    local name
    name=$(printf "%02d-%s" "$n" "$1")
    sleep 2
    python3 scripts/ui.py texts > "$OUT/$name.txt" 2>&1
    # Another app's "isn't responding" dialog in the way: answer it (and keep the evidence), then look again.
    if grep -qE "isn't responding|keeps stopping" "$OUT/$name.txt"; then
        answer_anr "$1"
        python3 scripts/ui.py texts > "$OUT/$name.txt" 2>&1
    fi
    adb exec-out screencap -p > "$OUT/$name.png" 2>/dev/null
    intent_crashes
    echo "== $name (pid: $(adb shell pidof $PKG || echo none))"
}
# Checks: a step that doesn't end where it should is a failure, listed in failures.txt; the run exits 1.
: > "$OUT/failures.txt"
fail() { echo "   FAIL: $*"; echo "$*" >> "$OUT/failures.txt"; }
expect_top() { # activity step: that activity is in front now
    top | grep -q "$1" || fail "$2: expected $1 in front, found: $(top | sed 's/.*{//; s/}.*//')"
}
expect_text() { # text step: the step's screen text (from record) contains it
    local f; f=$(ls "$OUT"/*-"$2".txt 2>/dev/null | tail -1)
    grep -qF "$1" "$f" 2>/dev/null || { fail "$2: expected \"$1\" on screen"; return 1; }
}
# Setup that goes wrong makes every later step fail for the same reason: stop there, with the evidence.
stop_early() { # why
    fail "stopped early: $1"
    adb logcat -d -v time > "$OUT/logcat.txt" 2>&1
    adb logcat -d 2>/dev/null | grep -E "ANR in |Application Not Responding" >> "$OUT/anr.txt"
    intent_crashes
    grep -q "com.poshith.intent" "$OUT/anr.txt" 2>/dev/null && fail "Intent itself stopped responding (anr.txt)"
    echo "== $(wc -l < "$OUT/failures.txt") check(s) failed:"; cat "$OUT/failures.txt"
    exit 1
}
shade_closed() { # step: the notification shade isn't open
    local focus; focus=$(adb shell dumpsys window | grep -m1 mCurrentFocus)
    echo "$focus" | grep -qiE "NotificationShade|StatusBar\b" && fail "$1: the notification shade is open ($focus)"
    return 0
}
# A slow emulator sometimes puts "Pixel Launcher isn't responding" over everything; answer it first.
tap() { python3 scripts/ui.py dismiss && sleep 1; python3 scripts/ui.py tap "$1" || echo "   (couldn't find \"$1\")"; }
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
# The emulator's own Pixel Launcher (API 34+) stops responding under the software GPU and puts its
# dialog over everything; Intent doesn't need it (it becomes the only home app). Test setup only.
if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 34 ] && adb shell pm list packages | grep -q "package:com.google.android.apps.nexuslauncher"; then
    adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher > "$OUT/launcher-off.txt" 2>&1
fi
adb install -r -g "$APK" > "$OUT/install.txt" 2>&1
# The real break silences notifications (Do Not Disturb access) and sets aside the ones already
# showing (Notification access); grant both as a person would.
adb shell cmd notification allow_dnd $PKG > /dev/null 2>&1
adb shell cmd notification allow_listener $PKG/$PKG.guard.BreakQuietService > /dev/null 2>&1
# YouTube asks for notifications on first launch; answer it up front so the dialog doesn't get in the way.
adb shell pm grant com.google.android.youtube android.permission.POST_NOTIFICATIONS > /dev/null 2>&1
cat "$OUT/install.txt"
# A stand-in for YouTube Shorts (the emulator's YouTube is too old to play them): test fixture only.
{ bash scripts/build-fake-feed.sh && adb install -r out/fake-feed.apk; } > "$OUT/fake-feed.txt" 2>&1 || echo "   (couldn't build the Shorts fixture)"
adb logcat -c
adb logcat -b crash -c

timeout 30 adb shell am start -n $PKG/.HomeActivity > "$OUT/start.txt" 2>&1; sleep 4
record launch

# Setup
tap "Begin";              record essentials
tap "Next";               record pull
tap "YouTube"             # only Instagram comes ticked; this test uses YouTube as the pull app
tap "Next";               record plan
# The home screen coming forward mid-setup (choosing Intent as home does that), and Intent restarting:
# setup must come back on the same page, not from the start.
timeout 30 adb shell am start -n $PKG/.HomeActivity > /dev/null 2>&1; sleep 4
record setup-after-home
expect_text "When you feel the pull" setup-after-home || stop_early "setup didn't come back on its page after the home screen came forward"
adb shell am force-stop $PKG; sleep 1
timeout 30 adb shell am start -n $PKG/.HomeActivity > /dev/null 2>&1; sleep 4
record setup-after-restart
expect_text "When you feel the pull" setup-after-restart || stop_early "setup didn't come back on its page after a restart"
tap "Next";               record strict-choice
tap "Gentle"              # the Shorts test below uses the gentle pause; Strict gets its own test
tap "Next";               record protect
expect_text "Turn on what you need" protect
tap "Skip for now";       record quiet
tap "Next";               record done
tap "Start using Intent"; record home-role
adb shell input keyevent KEYCODE_BACK
timeout 30 adb shell am start -n $PKG/.HomeActivity > /dev/null 2>&1; sleep 4
record home
top | grep -q HomeActivity || stop_early "setup never reached the home screen ($(top | sed 's/.*{//; s/}.*//'))"
expect_text "What matters today?" home    # the day's plan, asked once, on the home screen

# Main gestures
swipe 50 80 50 25;  record library
type_text "set"; record search
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
swipe 15 50 85 50;  record practice
expect_text "A one-minute reset" practice
adb shell input keyevent KEYCODE_BACK
adb shell input swipe "$((W / 2))" "$((H / 2))" "$((W / 2))" "$((H / 2))" 900
record long-press
tap "Settings";           record settings
swipe 50 75 50 45;        record settings-scrolled
tap "Shorts and Reels";   record reels-settings
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK
timeout 30 adb shell am start -n $PKG/.HomeActivity > /dev/null 2>&1; sleep 4
tap "Focus";              record focus-sheet
adb shell input keyevent KEYCODE_BACK

# The pause, opened from Intent's search (YouTube was suggested as a pull app during setup)
if adb shell pm list packages | grep -q "package:com.google.android.youtube"; then
    swipe 50 80 50 25
    type_text "YouTube"; sleep 1
    tap "YouTube";        record pause
    # Fresh install = first unlock of the day: morning quiet holds mindful apps. Skip it to see the pause.
    read -r qx qy < <(python3 scripts/ui.py coords "Hold to skip this morning" | head -1)
    if [ "$qx" -gt 0 ]; then adb shell input swipe "$qx" "$qy" "$qx" "$qy" 3800; record pause-real; fi
    sleep 7;              record pause-after-breath   # expected: minute chips + "Open for 5 min", no questions
    tap "Not now";        record after-not-now

    # Focus Guard: the same app opened from outside Intent (like a notification) still gets the pause
    adb shell settings put secure enabled_accessibility_services $PKG/$PKG.guard.FocusGuardService
    adb shell settings put secure accessibility_enabled 1
    sleep 3
    adb shell monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
    wait_top GateActivity 15
    snap guard-pause
    expect_top GateActivity guard-pause
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
if [ "$mx" -gt 0 ]; then
    adb shell input swipe "$mx" "$my" "$mx" "$my" 900; record app-menu
    # Replace: the search opens as a picker, and the chosen app takes Messages' place on home
    tap "Replace";        record replace-picker
    type_text "Clock"; sleep 1
    tap "Clock";          record replaced    # expected: Clock where Messages was
fi

if adb shell pm list packages | grep -q "package:com.google.android.youtube"; then
    # Is a 2-minute session really over after 2 minutes? Choose 2 min on the pause, then wait.
    adb shell input keyevent KEYCODE_HOME
    swipe 50 80 50 25
    type_text "YouTube"; sleep 1
    tap "YouTube"
    sleep 9 # the breath
    mapfile -t pts < <(python3 scripts/ui.py coords "Look something up" "2 min")
    for p in "${pts[@]}"; do read -r x y <<< "$p"; [ "$x" -gt 0 ] && adb shell input tap "$x" "$y"; sleep 1; done
    sleep 2
    read -r ox oy < <(python3 scripts/ui.py coords "Open for 2 min" | head -1)
    sleep 3 # let Focus Guard reconnect after the screen read
    [ "$ox" -gt 0 ] && adb shell input tap "$ox" "$oy"
    wait_top youtube 20
    snap session-started
    sleep 135
    snap session-2-min-later    # expected: YouTube closed, Intent's home in front (no time's-up screen)
    expect_top HomeActivity session-2-min-later
    adb shell input keyevent KEYCODE_HOME
fi

# The check-in after unlocking, and "Just checking" locking the phone
adb shell input keyevent KEYCODE_SLEEP; sleep 2
adb shell input keyevent KEYCODE_WAKEUP; sleep 1
adb shell wm dismiss-keyguard; sleep 1
record unlock-check-in
read -r jx jy < <(python3 scripts/ui.py coords "Just checking" | head -1)
sleep 3
[ "$jx" -gt 0 ] && adb shell input tap "$jx" "$jy"
sleep 3
snap just-checking-locks
{ adb shell dumpsys window | grep -m1 -iE "mShowingLockscreen|isKeyguardShowing|KeyguardShowing"
  adb shell dumpsys power | grep -m1 -E "mWakefulness="; } > "$OUT/lock-state.txt" 2>&1
adb shell input keyevent KEYCODE_WAKEUP; sleep 1; adb shell wm dismiss-keyguard; sleep 2

# A real break: no timer, only calls
adb shell input keyevent KEYCODE_HOME
tap "Focus"; sleep 1
record quiet-sheet
tap "15 min"
tap "Start the break"
tap "Start anyway"          # only if a switch is missing (it shouldn't be)
record break-screen
expect_text "Be here." break-screen
expect_text "min left" break-screen
adb shell settings get global zen_mode > "$OUT/break-zen.txt" 2>&1   # 1 = Do Not Disturb, priority only
# A notification from another app during the break: expected nowhere (not on screen, not in the list).
adb shell cmd notification post -S bigtext -t "Sale ends tonight" smoke "Tap to see the deals" > /dev/null 2>&1
sleep 3
adb shell cmd notification list > "$OUT/break-notifications.txt" 2>&1
grep -q "|smoke|" "$OUT/break-notifications.txt" && fail "break: another app's notification still showing during the break"
[ "$(tr -d '\r\n ' < "$OUT/break-zen.txt")" = "1" ] || fail "break: Do Not Disturb isn't on (zen_mode $(cat "$OUT/break-zen.txt"))"
sleep 4 # Focus Guard comes back after the screen read
adb shell cmd statusbar expand-notifications > /dev/null 2>&1
sleep 2
snap break-shade            # expected: the break screen, the shade closed again
shade_closed break-shade
expect_top HomeActivity break-shade
adb shell monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
wait_top GateActivity 15
snap break-blocks-youtube
expect_top GateActivity break-blocks-youtube
adb shell input keyevent KEYCODE_BACK
sleep 2
read -r hx hy < <(python3 scripts/ui.py coords "Hold to end the break" | head -1)
[ "$hx" -gt 0 ] && adb shell input swipe "$hx" "$hy" "$hx" "$hy" 6800
record break-ended

# Shorts and Reels: the Short you open plays; the first swipe to another one gets the pause, and
# after "Watch 5 more", the 6th swipe gets it again. Both guards on, as most people would have them.
# The stand-in is shaped like current YouTube: the scroller has no telling name, only the per-Short
# page views (reel_player_page_container) say it's Shorts.
flick() { adb shell input swipe "$((W / 2))" "$((H * 70 / 100))" "$((W / 2))" "$((H * 40 / 100))" 300; }
if adb shell pm list packages | grep -q "package:app.revanced.android.youtube"; then
    adb shell settings put secure enabled_accessibility_services $PKG/$PKG.guard.FocusGuardService:$PKG/$PKG.guard.ReelsGuardService
    adb shell settings put secure accessibility_enabled 1
    sleep 3
    timeout 30 adb shell am start -W -n app.revanced.android.youtube/.Feed > /dev/null 2>&1
    sleep 3
    flick
    snap shorts-first-swipe        # expected: the feed closed, ReelsActivity over Intent's home
    expect_top ReelsActivity shorts-first-swipe
    record shorts-pause
    tap "Watch 5 more"
    sleep 4 # the guard comes back after the screen read
    for _ in $(seq 1 6); do flick; sleep 2.5; done
    snap shorts-after-five         # expected: ReelsActivity again, "That's about 7 in a row"
    record shorts-pause-again
    expect_text "That's about 7 in a row" shorts-pause-again
    tap "Close YouTube"
    snap shorts-closed             # expected: Intent's home
    expect_top HomeActivity shorts-closed
    record home-after-shorts

    # Strict mode: no Shorts a day, so stepping into Shorts is a stop (no "more" button); going back
    # locks Shorts (only Shorts); a third try is sent straight back out of the feed.
    adb shell input keyevent KEYCODE_HOME; sleep 1
    adb shell input swipe "$((W / 2))" "$((H / 2))" "$((W / 2))" "$((H / 2))" 900; sleep 1
    tap "Settings"; sleep 1
    tap "Strict mode"
    record strict-settings
    tap "None"
    tap "Turn on Strict mode";     record strict-code
    tap "I wrote it down. Turn on Strict mode"
    record strict-on
    expect_text "Shorts and Reels are off today" strict-on
    adb shell input keyevent KEYCODE_HOME; sleep 2
    timeout 30 adb shell am start -W -n app.revanced.android.youtube/.Feed > /dev/null 2>&1
    sleep 4
    snap strict-first-stop          # expected: closed, ReelsActivity "Shorts are off"
    expect_top ReelsActivity strict-first-stop
    record strict-stop
    expect_text "Shorts are off" strict-stop
    grep -q "Watch" "$OUT"/*-strict-stop.txt && fail "strict-stop: a \"Watch more\" button in Strict mode"
    tap "Close YouTube"
    sleep 2
    timeout 30 adb shell am start -W -n app.revanced.android.youtube/.Feed > /dev/null 2>&1
    sleep 4
    snap strict-second-stop         # expected: closed, and honest about when: tomorrow, not "in 30 minutes"
    expect_top ReelsActivity strict-second-stop
    record strict-locked
    expect_text "Available again tomorrow" strict-locked
    tap "Close YouTube"
    sleep 2
    timeout 30 adb shell am start -W -n app.revanced.android.youtube/.Feed > /dev/null 2>&1
    sleep 5
    snap strict-locked-bounce       # expected: out of the feed (Back), no pause screen, no gate
    top | grep -q "ReelsActivity\|GateActivity\|revanced" && fail "strict-locked-bounce: still in Shorts or on a stop screen: $(top)"
fi

# Basic phone: only essentials on the home screen, and swiping up must not open search
adb shell input keyevent KEYCODE_HOME
tap "Focus";              sleep 1
tap "Basic phone";        sleep 1
tap "Turn on basic phone"
record basic-on
swipe 50 80 50 25
record basic-swipe-up

adb logcat -d -v time > "$OUT/logcat.txt" 2>&1
grep -E "ANR in |Application Not Responding" "$OUT/logcat.txt" >> "$OUT/anr.txt"
grep -q "com.poshith.intent" "$OUT/anr.txt" 2>/dev/null && fail "Intent stopped responding at some point (anr.txt)"
if crashed; then echo "CRASH:"; cat "$OUT/crash.txt"; fail "Intent crashed (crash.txt)"; fi

# The safety net: a forced crash must show Intent's own error screen, not just close.
adb shell am crash $PKG > /dev/null 2>&1
sleep 4
adb exec-out screencap -p > "$OUT/99-crash-screen.png" 2>/dev/null
python3 scripts/ui.py texts > "$OUT/99-crash-screen.txt" 2>&1
grep -qF "Copy details" "$OUT/99-crash-screen.txt" || fail "crash screen: Intent's own error screen didn't show"

if [ -s "$OUT/failures.txt" ]; then
    echo "== $(wc -l < "$OUT/failures.txt") check(s) failed:"; cat "$OUT/failures.txt"
    exit 1
fi
echo "== all checks passed"
exit 0
