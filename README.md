# DigitalCare

An Android app that reminds you to rest your eyes and drink water through the day. It follows the 20-20-20 rule: every 20 minutes, look at something 20 feet (about 6 m) away for 20 seconds.

It is made to be sideloaded, for an android phone. Everything stays on the phone: no account, no ads, no analytics, and the app's own code makes no network requests.

## What it does

**Eye breaks**
- A short break every 20 minutes (1 to 60) lasting 20 seconds (10 to 60). A long break lasting 5 minutes (1 to 15) comes every 60 minutes, to stand, walk and stretch. Set that to any number of minutes, from twice the short interval up to 1440: the long break comes exactly that long after the last one ended.
- The notification counts down and has **Skip** and **Snooze 5 min** buttons. A short "Break over" signal ends the break. Tapping a notification opens a break screen with an animation and a countdown.

**Water**
- A daily goal that follows the season (North India): 12 glasses of 250 ml in summer, 10 in the monsoon and in March and October, 8 in winter. Or set your own goal and spacing. The glass size can be changed.
- Reminders spread the glasses you have left over the time left in the day, 30 to 120 minutes apart, with one last reminder 10 minutes before they stop. They stop 90 minutes before sleep, or at start and stop times you choose.
- Log a glass from the notification (**Drank 1 glass**) or the app, and take one back with **-1 glass**. A ring shows progress.

**Phone-use reminder**
- After 15 minutes of using the phone in a row (set 1 to 60) it asks you to look away, and asks again every 15 minutes while you carry on. A screen-off of one minute or more starts a new count.
- Tapping the reminder opens a "Look away" screen with a 20-second countdown, like the 20-minute break.
- Home shows how long you have been on the phone, in seconds for the first minute, and when the last reminder went out.
- The counter runs as an Android foreground service, so a small permanent notification is shown while it is on.

**Your hours**
- A wake and a sleep time for each day of the week, and any day can be switched off. Nothing fires outside those hours. A sleep time after midnight works.
- **Pause** reminders for 30 minutes, 1 hour or until tomorrow.

**Alerts**
- For each of the five alerts (short break, long break, break over, water, phone use): sound and vibration, sound only, vibration only or silent, and how it appears: **full screen**, a pop-up banner or quiet. **Send a test alert** tries the sound and banner before you save.
- **Full screen** opens the reminder's own screen by itself, over whatever you are doing, even while you use the phone. It needs "Display over other apps" for DigitalCare; Home shows a Fix button until it is on, and without it you get the pop-up banner. It is on offer for every alert except break over.
- Optional **Wake screen**: show break and water reminders over the lock screen when the phone is locked.

**Language**
- The whole app, notifications included, can be in English or Hindi (हिन्दी). Choose it in Settings; the page changes at once. The phone's own menu names in the Vivo steps stay in English, as the phone shows them.

**Screen comfort**
- Shortcuts to the phone's Night light (some phones call it "Eye protection") and Extra dim settings.

**Keeps running.** Alarms are exact and are set again after a reboot, a time or time-zone change, or an app update. A Setup card on Home checks notifications and battery settings, and lists the extra Vivo steps (battery, autostart, lock-screen permissions, locking the app in recent apps). On other brands it links to [dontkillmyapp.com](https://dontkillmyapp.com/).

## Install

The repo has no APK (they are git-ignored). Build one (below), then:

1. Copy `digitalcare.apk` to the phone.
2. Open it and allow installing from that source when Android asks.
3. Open DigitalCare, allow notifications, and follow the Setup card on Home.

Or, from a computer with USB debugging on: `adb install -r digitalcare.apk`.

To update, install the new APK over the old one and your settings are kept. The APK is signed with the debug key of the computer that built it, so an APK built on another computer cannot update it: uninstall first (settings reset).

## Build it

You need Node.js 22 or newer, JDK 21 or newer, and the Android SDK with platform 36. Point `sdk.dir` in `android/local.properties` at the SDK (Android Studio writes this file for you) or set `ANDROID_HOME`.

```powershell
npm install
.\build-apk.ps1
```

The script runs the scheduler tests, builds the web app, syncs it into the Android project, builds the debug APK and copies it to `digitalcare.apk` here, replacing the last one. It stops at the first failing step, so a failed build never leaves an old APK looking new.

Not on Windows? The same steps by hand:

```sh
cd android && ./gradlew test && cd ..
npm run build
npx cap sync android
cd android && ./gradlew assembleDebug    # android/app/build/outputs/apk/debug/app-debug.apk
```

## Develop

`npm run dev` serves the screens in a browser at the address Vite prints, with a fake Android plugin and made-up data, so you can work on the UI without a phone. The fake plugin is left out of production builds. Preview the other screens with `?screen=short`, `?screen=long`, `?screen=water` or `?screen=phone`, and Hindi with `?lang=hi` (also `?overlay=on`, `?phone=off|stopped|reminded|fresh`, `?water=none|late`). Alarms and notifications only work on a phone.

The scheduling rules are plain Java with JUnit tests (`cd android && ./gradlew test`), so they run on a computer too.

## How it works

The screens are React and TypeScript running in a Capacitor WebView. All timing is native Java, so reminders still fire when the app is closed.

| Class | Job |
|---|---|
| `Scheduler` | The rules: what happens next and when. No Android code, and fully unit-tested. |
| `Engine` | Keeps settings and the day's state, sets the exact alarms, posts the notifications. |
| `Notifier` | Notification channels, text and buttons. |
| `EventReceiver` | Receives alarms, notification buttons, and boot and time-change broadcasts. |
| `ScreenService` | The foreground service that counts continuous phone use. |
| `DigitalCarePlugin` | The small local Capacitor plugin the screens talk to. |

```
src/                    the screens: Home, Settings, break and water screens
src/text.ts             all the app's text in English and Hindi (the build fails if a Hindi string is missing)
src/mock.ts             the fake plugin for the browser preview (dev only)
android/app/src/main/   the native engine (java/com/digitalcare/app) and the manifest
  res/values*/strings.xml   the notification texts: English, and Hindi in values-hi (a test keeps them in step)
android/app/src/test/   JUnit tests for the scheduler and settings
public/anim/            break and water animations, drawn by tools/make_animations.py
docs/superpowers/       the v1 design spec and implementation plan (written for v1, the app has grown since)
build-apk.ps1           test, build and copy the APK
```

## Permissions

| Permission | Why |
|---|---|
| Notifications | Show the reminders. |
| Exact alarms | Fire on time, even when the phone is idle. |
| Start at boot | Set the alarms again after a restart. |
| Full-screen notifications | The optional reminder screen over the lock screen. |
| Display over other apps | Only for the Full screen choice: lets a reminder open its screen by itself while you use the phone. |
| Vibrate | Vibrate for alerts. |
| Ignore battery optimization | So you can switch battery optimization off for the app. |
| Foreground service | The phone-use counter. |
| Internet | Declared by the Capacitor template. The app's own code makes no network requests. |

## Good to know

- Android 8.0 (API 26) or newer. Made for an android phone; other android devices should work, but their battery-saver steps differ.
- Reminders only fire inside your wake hours, and not while paused.
- The phone-use counter needs its service to stay alive. If the phone kills it, Home says "Phone-use counter is not running" and the Setup card offers the fix.
- Debug-signed and for sideloading only. Publishing to Google Play would need a policy review for exact alarms (`USE_EXACT_ALARM`) and a foreground-service declaration; the manifest has comments marking both.
- The phone's own settings, Do Not Disturb and battery saver can still mute or delay a reminder.
- Water guidance is general. If you have a kidney or heart condition, follow your doctor's advice on water.
