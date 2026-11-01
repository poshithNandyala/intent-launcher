# Privacy

**Short version:** Intent collects nothing, sends nothing and has no way to. Everything it knows stays in one private file on your phone, and uninstalling the app deletes it.

## What Intent can't do

- **No internet.** The app does not request the `INTERNET` permission, so Android won't let it open a network connection. You can confirm this in *Settings › Apps › Intent › Permissions* or in [the manifest](app/src/main/AndroidManifest.xml).
- **No accounts, ads, analytics, crash reporters or trackers.** The app uses no third-party libraries at all.
- **No backups.** Intent opts out of Android's cloud backup and phone-to-phone transfer ([rules](app/src/main/res/xml/data_extraction_rules.xml)), so its data doesn't leave the phone that way either. The trade-off: on a new phone, setup starts fresh.
- **It can't read your screen.** Focus Guard (the optional accessibility service) is set to `canRetrieveWindowContent="false"` ([config](app/src/main/res/xml/focus_guard.xml)), so Android doesn't give it your messages, text or anything else you see. It receives one kind of event, "a new window came to the front", and keeps only that app's package name (for example `com.instagram.android`).
- **The Shorts & Reels guard sees view names, never text.** It's a second, separate accessibility service, also optional and off until you turn it on ([config](app/src/main/res/xml/reels_guard.xml)). Android tells it only about scrolling inside YouTube and Instagram (`packageNames`), nothing from any other app. To tell a swipe through Shorts or Reels from scrolling comments, it looks at the resource names of the view that scrolled and the views around it (like `reel_recycler`) and its size on screen. It never reads text, captions, comments or messages, and keeps only a count.

## What stays on your phone

All of it lives in the app's private storage (one SharedPreferences file). Other apps can't read it.

- Your choices: home apps, custom names, hidden apps, each app's rule (essential, mindful, check-in, blocked) and your settings.
- What you typed: today's one thing and your if-then plan.
- Small counters per day for the last 35 days: pauses shown, "Not now" taps, sessions started, "just checking" taps, and (with the Shorts & Reels guard) how many Shorts and Reels you swiped to. Older days are deleted automatically.
- Current sessions: which mindful app, the minutes and the purpose you picked ("Reply to someone"), minutes used. A session is deleted when it ends, and all of them after a reboot.
- If you allow **usage access**: Intent reads Android's own usage statistics on demand to count unlocks and minutes in mindful apps. It doesn't copy that history anywhere.

Your answer to "How are you feeling?" is never saved. It only changes the suggestion on the pause screen you're looking at.

## Permissions, and why

| Permission | Why |
|---|---|
| Home app role | To be your home screen. |
| Query installed apps (`<queries>`) | To list the apps you can choose from and open them. |
| Accessibility (Focus Guard, optional) | To notice when a mindful app opens from a notification, recents or a link, to count session minutes, and to show "time's up". Also used for double-tap to lock and swipe down for notifications. |
| Accessibility (Shorts & Reels guard, optional) | To notice when you start swiping through YouTube Shorts or Instagram Reels, count those swipes and show the pause. Told only about scrolling in those two apps. |
| Usage access (optional) | Unlock count and minutes in mindful apps on the home screen and in Your week. |
| Notifications (optional) | A "your minutes are up" reminder when Focus Guard is off. Intent sends nothing else. |
| Notification access (optional) | During a real break only: sets every notification that isn't a call (or an alarm) aside, and they come back by themselves after the break. Intent doesn't keep, copy or send them, and outside a break it does nothing. |
| Do Not Disturb access (optional) | During a real break, silence everything except calls and alarms, then put Do Not Disturb back exactly as it was. It can't read your notifications. |
| Expand status bar | Swipe down on the home screen to open notifications. |
| Request uninstall | "Uninstall" in an app's long-press menu. Android always asks you to confirm. |
| Set alarm | Tapping the clock opens your alarms. |

## Your control

- Turn Focus Guard or the Shorts & Reels guard off any time in *Settings › Accessibility* (or pause the Shorts & Reels guard in Intent's settings). Intent never blocks Settings, the uninstaller or choosing another home app.
- *Settings › Privacy › Erase everything* in Intent deletes all of its data and starts setup again.
- Uninstalling Intent removes everything it stored.

Questions: [open an issue](https://github.com/poshithNandyala/intent-launcher/issues).
