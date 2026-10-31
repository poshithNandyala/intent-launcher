<h1 align="center">Intent</h1>
<p align="center"><b>Open your phone on purpose.</b><br>
An Android home screen that keeps your tools one tap away and puts a short, kind pause in front of the apps that pull you in.</p>

<p align="center">
  <a href="https://github.com/poshithNandyala/intent-launcher/raw/main/download/Intent-1.0.1.apk"><b>⬇ Download the APK</b></a> ·
  <a href="docs/RESEARCH.md">The research</a> ·
  <a href="PRIVACY.md">Privacy</a>
</p>

---

Most of the time we don't pick up our phones because something needs us. In a study that filmed people's real days, **89% of phone interactions were started by the person, not by a notification** ([Heitmayer & Lahlou, 2021](docs/RESEARCH.md#habits)). We check about **85 times a day, twice as often as we think** ([Andrews et al., 2015](docs/RESEARCH.md#habits)). And a pause of just a few seconds, with an easy way out, cut app openings by **57%** ([Grüning et al., PNAS 2023](docs/RESEARCH.md#what-works)).

Intent is built around that moment of reaching. It doesn't fight you or shame you. It makes what you meant to do easy, and what you didn't mean to do a little slower.

## What it does

| | |
|---|---|
| **Text home screen** | Time, date, today's one thing, and up to 7 apps you chose, written as words on your own wallpaper (the words turn light or dark to stay readable; dim it or go plain if you like). No icons, no badges, no red dots. |
| **Find anything by typing** | Swipe up and type. Nothing to browse, so you open what you came for. |
| **The pause** | Apps you mark *mindful* (Instagram, YouTube…) open after one slow breath, a question about how you feel and what it's for, and a session length you pick. **"Not now" is always the biggest button.** |
| **Sessions that end** | With Focus Guard on, Intent counts only the minutes the app is actually in front, and shows "time's up" when you reach your number. One "2 more minutes", then you stop. |
| **The pause grows** | Every session after the second one today waits 2 s longer (max 20 s). You can turn this off. |
| **Essentials never wait** | Phone, messages, OTPs, UPI, maps, camera, authenticator, the clock and keyboards are never paused. Wi-Fi, Bluetooth, hotspot and mobile data are untouched. |
| **Focus, breaks, basic phone** | Deep work (25/50/90 min), a screen-free break (10–20 min), or *basic phone*: only your essentials, with no search at all, until you turn it off. Ending early means holding a button for 3 s. |
| **Morning quiet** | Mindful apps rest for the first 15 minutes after your first unlock of the morning, so your first thoughts are your own. |
| **Wind-down** | At night, mindful apps rest (optionally all non-essentials). |
| **Check-in times** | Email or work chat can open only in your check-in windows (12:30 and 18:30 by default). |
| **"What did you pick up your phone for?"** | A quiet card right after you unlock. "Just checking" offers to lock the phone. It never blocks anything. |
| **Rules you set while calm** | Making an app harder to open is instant. Making it easier waits 20 s (adjustable). Nothing ever stops you from changing a rule, switching launchers or uninstalling. |
| **The Mind library** | Swipe right: 50+ short lessons, each with its source and an honest evidence label, covering the science and how people like Warren Buffett, Bill Gates, Pavel Durov and Christopher Nolan use (or don't use) their phones. It ends, on purpose. |
| **Quiet notifications** | A guided checklist to switch off alerts from anything that isn't a person. |
| **Honest numbers** | Unlocks today and minutes in mindful apps (with usage access), and how often you chose "Not now". No streaks to lose. |

**Gestures:** swipe up to search · swipe right for Mind · swipe down for notifications and quick settings · long-press to edit · double-tap to lock (with Focus Guard) · tap the clock for alarms and the date for calendar.

## Install

1. Download **[Intent-1.0.1.apk](https://github.com/poshithNandyala/intent-launcher/raw/main/download/Intent-1.0.1.apk)** on your phone and open it. Allow "install unknown apps" for your browser if Android asks.
   SHA-256 `fb2fa744a80d1c49ca3e2f59b2bf99fad667821181c6adb4f12299831f7fb8fe`
2. Open Intent and follow the two-minute setup: choose your essentials, the apps that pull you in, and your if-then plan.
3. **Make Intent your home app** when asked, or later in *Settings › Apps › Default apps › Home app*.
4. Optional but recommended: **turn on Focus Guard** (*Settings › Accessibility › Intent · Focus Guard*).
   On Android 13 and newer, apps installed from a file get a **"Restricted setting"** message at this step. Fix it like this: *Settings › Apps › Intent › ⋮ (top right) › Allow restricted settings*, then try again.
   On **Xiaomi, Redmi and POCO** phones, also allow *Display pop-up windows while running in the background* in Intent's permissions (Intent's Focus Guard screen has a button for it), or the pause can't appear over other apps.
5. Optional: allow **usage access** for unlock counts, and notifications for "time's up" reminders when Focus Guard is off.

Requires Android 8.0 or newer. The APK is about 300 KB.

If Intent ever crashes, it shows what went wrong with a **Copy details** button (please send them in an [issue](https://github.com/poshithNandyala/intent-launcher/issues)) and a one-tap way back to your previous home app.

## Privacy

Intent has **no internet permission**, so it cannot send anything anywhere. There are no accounts, ads, analytics or trackers. Focus Guard sees only *which* app is in front (its package name) and **cannot read your screen** (`canRetrieveWindowContent="false"`). Everything stays in one private file on the phone. Details: [PRIVACY.md](PRIVACY.md).

## How it decides

Every way of opening an app (home screen, search, and with Focus Guard on, notifications, recents and links) goes through one function, [`Policy.decide`](app/src/main/java/com/poshith/intent/core/Policy.kt). It's pure Kotlin with [83 unit tests](app/src/test/java/com/poshith/intent/core/Tests.kt):

```
protected app (phone, settings, keyboard…)  → open
session you already chose, still running    → open
essential                                   → open
focus / break / basic phone                 → paused until it ends
blocked                                     → paused (type a phrase for 5 minutes)
mindful during morning quiet                → paused for a few minutes
check-in app outside its window             → paused until the next window
mindful (or anything, if strict) at night   → paused until morning
mindful                                     → the pause: breath, feeling, purpose, minutes
everything else                             → open
```

## Build from source

No Gradle or Android Studio needed: just JDK 17+, `curl`, `unzip` and `zip`.

```bash
./build.sh test   # unit tests for the rules engine
./build.sh        # → out/Intent-<version>.apk, signed
```

The first run downloads the tools from GitHub into `.tools/`: the Kotlin compiler, [bundletool](https://github.com/google/bundletool) (for aapt2, R8 and apksig) and the public Android SDK jar. A signing key is created in `keystore/` on the first build. **Back it up and never commit it**: Android only installs an update over an existing app when both are signed with the same key.

Prefer Android Studio? Open the folder. The Gradle files build the same sources (`./gradlew assembleDebug`).

**Releasing:** raise `VERSION_CODE` and `VERSION_NAME` in `build.sh` and `app/build.gradle.kts`, then push a tag such as `v1.0.1`. CI publishes the APK to Releases when the repository has two secrets: `INTENT_KEYSTORE_B64` (the keystore file, base64) and `INTENT_KEY_PASS`. Without them it builds but never publishes, because an APK signed with another key can't update the installed app.

The app uses only the Android framework (no AndroidX, no libraries), which keeps it tiny and fast.

| Path | What's there |
|---|---|
| `app/src/main/java/.../core` | The rules engine, sessions, lessons and feelings. Pure Kotlin, unit tested. |
| `app/src/main/java/.../data` | Local storage, the app list (LauncherApps), usage stats, counters. |
| `app/src/main/java/.../guard` | Launching, Focus Guard (accessibility service) and reminders. |
| `app/src/main/java/.../ui` | The small design system: palette, type, sheets, the breathing view, the Mind library. |
| `docs/RESEARCH.md` | Every study the app relies on, with what it does and doesn't show. |

## Credits

Ideas from [Olauncher](https://github.com/tanujnotes/Olauncher) (text home screen), [one sec](https://one-sec.app) (the pause), ScreenZen (escalating waits), the Center for Humane Technology's *Take Control* guide, and Dr. Sahar Yousef's work on focus and recovery. No code was copied. Font: [Inter](https://rsms.me/inter/) by Rasmus Andersson (SIL Open Font License 1.1).

Made by [Poshith Nandyala](https://github.com/poshithNandyala). MIT license.
