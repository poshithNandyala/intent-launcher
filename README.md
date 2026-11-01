<h1 align="center">Intent</h1>
<p align="center"><b>Open your phone on purpose.</b><br>
An Android home screen that keeps your tools one tap away and puts a short, kind pause in front of the apps that pull you in.</p>

<p align="center">
  <a href="https://github.com/poshithNandyala/intent-launcher/raw/main/download/Intent-1.3.0.apk"><b>⬇ Download the APK</b></a> ·
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
| **Sessions that end** | With Focus Guard on, Intent counts only the minutes the app is actually in front, and when you reach your number it closes the app. No pop-up asking to stay: you're back on your home screen with a line saying so, and nothing keeps playing (no picture-in-picture window). |
| **The pause grows** | Every session after the second one today waits 2 s longer (max 20 s). You can turn this off. |
| **Essentials never wait** | Phone, messages, OTPs, UPI, maps, camera, authenticator, the clock and keyboards are never paused. Wi-Fi, Bluetooth, hotspot and mobile data are untouched. |
| **A real break** | The strictest mode: your home screen goes dark with no timer to watch, and only calls get through. Every other notification is silenced and set aside until the break ends (then they all come back), and the notification shade won't open. Needs Notification access and Focus Guard, one switch each. 15 min to 1 h 30, or type your own. |
| **Focus and basic phone** | Deep work (25/50/90 min) with your essentials, or *basic phone*: only your essentials, with no search at all, until you turn it off. Ending anything early means holding a button for 3 s. |
| **Morning quiet** | Mindful apps rest for the first 15 minutes after your first unlock of the morning, so your first thoughts are your own. |
| **Wind-down** | At night, mindful apps rest (optionally all non-essentials). |
| **Check-in times** | Email or work chat can open only in your check-in windows (12:30 and 18:30 by default). |
| **"What did you pick up your phone for?"** | A quiet card right after you unlock. "A call or message" shows Phone, Messages and WhatsApp; "Just checking" locks the phone; no answer in 15 s locks it too (with Focus Guard). |
| **Shorts and Reels** | The one you open is fine; the endless swipe is the trap. With the optional Shorts & Reels guard, the first swipe through YouTube Shorts or Instagram Reels closes the app (nothing keeps playing) and shows a pause: today's count, a line worth reading, a big "Close YouTube", "Leave Shorts" to go back to the rest of the app, and a small "Watch 5 more" (or none, if you'd rather the swipe always stops you). Each "more" waits a little longer. The count shows on your home screen too. |
| **A line worth stopping for** | A quote under the date, new every 30 seconds, on a soft pane so it reads on any wallpaper: Seneca, Thoreau, Annie Dillard, James Clear, Alex Hormozi and others, plus a few of Intent's own. Another waits on the pause, the break and the Shorts pause. Tap it for the next; turn it off in Settings. |
| **Screen time you can feel** | Today's screen time under the date. Time in the apps that pull you grows heavier as it climbs; calls, maps and work are never singled out. |
| **Rules you set while calm** | Making an app harder to open is instant. Making it easier waits 20 s (adjustable). Nothing ever stops you from changing a rule, switching launchers or uninstalling. |
| **The Mind library** | Swipe right: 56 short ideas from research, each a little puzzle first ("Out of 10 pickups, how many are your own idea?"), then the answer, a thing to try, and its source with an honest evidence label. Covers the science and how people like Warren Buffett, Bill Gates, Pavel Durov and Christopher Nolan use (or don't use) their phones. Shows how many you've read; "Surprise me" picks one; today's question also waits in the Today sheet until you guess it. It ends, on purpose. |
| **Quiet notifications** | A guided checklist to switch off alerts from anything that isn't a person. |
| **Honest numbers** | Unlocks today and minutes in mindful apps (with usage access), and how often you chose "Not now". No streaks to lose. |

**Gestures:** swipe up to search · swipe right for Mind · swipe down for notifications and quick settings · long-press to edit · double-tap to lock (with Focus Guard) · tap the clock for alarms and the date for calendar.

## Install

1. Download **[Intent-1.3.0.apk](https://github.com/poshithNandyala/intent-launcher/raw/main/download/Intent-1.3.0.apk)** on your phone and open it. Allow "install unknown apps" for your browser if Android asks.
   SHA-256 `d565d896709e292216c3c7a2002796bfda374a4ea2a8e9c12be817404fc5d94a`
2. Open Intent and follow the two-minute setup: choose your essentials, the apps that pull you in, and your if-then plan.
3. **Make Intent your home app** when asked, or later in *Settings › Apps › Default apps › Home app*.
4. Optional but recommended: **turn on Focus Guard** (*Settings › Accessibility › Intent · Focus Guard*).
   On Android 13 and newer, apps installed from a file get a **"Restricted setting"** message at this step. Fix it like this: *Settings › Apps › Intent › ⋮ (top right) › Allow restricted settings*, then try again.
   On **Xiaomi, Redmi and POCO** phones, also allow *Display pop-up windows while running in the background* in Intent's permissions (Intent's Focus Guard screen has a button for it), or the pause can't appear over other apps.
5. For the **real break**, allow Intent **Notification access** once (the break sheet takes you there; on Android 13 and newer it may need the same "Allow restricted settings" step). That's what keeps every notification but calls away.
6. Optional: turn on **Intent · Shorts & Reels** in the same Accessibility list to pause the swipe through YouTube Shorts and Instagram Reels (Intent › Settings › Shorts and Reels walks you through it).
7. Optional: allow **usage access** for unlock counts, and notifications for "your minutes are up" reminders when Focus Guard is off.

Requires Android 8.0 or newer. The APK is about 300 KB.

If Intent ever crashes, it shows what went wrong with a **Copy details** button (please send them in an [issue](https://github.com/poshithNandyala/intent-launcher/issues)) and a one-tap way back to your previous home app.

## Privacy

Intent has **no internet permission**, so it cannot send anything anywhere. There are no accounts, ads, analytics or trackers. Focus Guard sees only *which* app is in front (its package name) and **cannot read your screen** (`canRetrieveWindowContent="false"`). The optional Shorts & Reels guard is told only about scrolling inside YouTube and Instagram and looks at view names (like `reel_recycler`), never text. Everything stays in one private file on the phone. Details: [PRIVACY.md](PRIVACY.md).

## How it decides

Every way of opening an app (home screen, search, and with Focus Guard on, notifications, recents and links) goes through one function, [`Policy.decide`](app/src/main/java/com/poshith/intent/core/Policy.kt). It's pure Kotlin with [116 unit tests](app/src/test/java/com/poshith/intent/core/Tests.kt):

```
protected app (phone, settings, keyboard…)  → open
real break                                  → paused until it ends (only calls)
essential                                   → open
focus / basic phone                         → paused until it ends
session you already chose, still running    → open
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
