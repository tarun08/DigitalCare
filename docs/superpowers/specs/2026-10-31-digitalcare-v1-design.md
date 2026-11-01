# DigitalCare v1 — Design Spec

Date: 2026-10-31 · Status: draft for review

## 1. Goal

An Android app (sideloaded APK, user's Vivo phone) that protects eyes and hydration during 10–12 h of daily screen time (office + personal):

- 20-20-20 eye breaks and an hourly long break, with start and end signals.
- Water reminders that start only after wake-up, tuned to North India seasons.
- Reminders keep working with the screen off and the app closed.

**Success criteria**

1. With the phone locked and idle for 1 h or more, reminders arrive on time (within 1 min) on the Vivo phone.
2. Reminders continue after a reboot.
3. Notification buttons (Drank 1 glass / Skip / Snooze) work without opening the app.
4. No reminders outside wake hours.

## 2. Scope

**In v1:** short and long eye breaks · start and break-over signals · Skip / Snooze · wake and sleep times for each of the 7 days (a day can be off) · Pause · water reminders with glass counter, season goal and smart spacing · custom water mode · per-type sound/vibration via Android settings · optional silent screen wake that shows the break screen on the lock screen · built-in animations · night light and Extra Dim shortcuts · setup card with Vivo steps.

**Later phases:**

- v2: Reels/Shorts scroll guard (Accessibility Service), optional own night filter.
- v3: Windows exe (Electron reusing the React UI).

**Not doing:** automatic wake detection · user-picked images · in-app sound/vibration switches · merging water with breaks · meal-time water slots · stats/history · custom sounds · themes · Hindi/i18n · live weather · "behind on water" nudge · weight-based goals.

## 3. Platform and stack

| Item | Choice |
|---|---|
| App shell | Capacitor 8 (same as `D:\BhumiSetu\client`) |
| UI | React 19 + Vite + TypeScript, plain CSS. No router, no state library, no Tailwind. |
| Native engine | Java, in the `android/` app module, exposed as a local Capacitor plugin |
| SDK levels | minSdk 26 (notification channels always available), target/compile 36 |
| App id | `com.digitalcare.app` |
| Toolchain | Reused: Android SDK `C:\android-sdk` (platform 36, build-tools 35), Gradle 9.1 cache, Java 25. No emulator; test on the phone. |
| Disk | About 0.5–1 GB on D: (node_modules + build output) |

## 4. Architecture

```
React UI (Home · Break screen · Settings)
    │  DigitalCarePlugin (Capacitor bridge)
    ▼
Native engine (Java)
    Scheduler      pure calculation: (settings, day state, now) -> next times. No Android imports.
    Engine         loads/saves SharedPreferences, sets/cancels alarms, posts notifications
    EventReceiver  one receiver for alarms, notification buttons, boot/time-change broadcasts
    MainActivity   opens the right screen from a notification; shows over the lock screen for breaks
```

- The engine owns all settings and day state, stored in SharedPreferences. React never stores reminder state.
- At most 3 alarms are set at a time, one per pending event kind: `BREAK`, `BREAK_END` (only during a break) and `WATER` inside wake hours, otherwise `DAY_START` or `RESUME`.
- **Recompute from now** is one shared routine. It runs on settings save, resume from pause, `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and `MY_PACKAGE_REPLACED`. It sets next break = now + short interval and next water = now + water interval. It never fires stale reminders.

## 5. Behavior rules

### 5.1 Day window

- Each of the 7 days has its own wake and sleep time and can be switched off (no reminders that day). At least one day stays on.
- Settings saved by the first version (weekday and weekend times, selectable weekend days) are converted to the 7 days on first read.
- A window that runs past midnight belongs to the day it started on. If it overlaps the next day's window, the next day's window wins from its wake time.
- If sleep ≤ wake (for example sleep 00:30), the window ends on the next calendar day.
- `DAY_START` fires at wake time. It resets glasses and starts the long break timer, clears "pause until tomorrow", sets next break = wake + short interval, and sets next water = wake + 10 min.
- Nothing fires after sleep time. The only alarm armed then is `DAY_START` for the next wake.

### 5.2 Eye breaks

- The **long break timer** starts at wake and restarts each time a long break ends or is skipped. A long break is due `longEveryMin` minutes after that (default 60). A snoozed re-fire keeps its type and does not restart the timer.
- **When the next break falls**, after a break ends at time `t` (and after Save, Resume and day start): normally `t` + the short interval. When the long break falls due within the next interval and a half, the next break is the long one, at the minute it is due, but never closer than half an interval to `t`. A long break that is already overdue (a pause, the phone off) is simply the next regular break. So long breaks come exactly `longEveryMin` minutes apart, and the short breaks fill the time between them.
- A break is **long** when it fires at or after the due time. `longEveryMin` is any whole number of minutes from twice the short interval up to 1440, so a short break always falls between two long ones. Settings saved as "every Nth break" become N × the short interval.
- When a break fires:
  - Post the break notification with a countdown (Android chronometer), **Skip** and **Snooze 5 min** buttons.
  - Set `BREAK_END` at now + duration (20 s short, 5 min long).
- At `BREAK_END`:
  - Cancel the break notification.
  - Post "Break over", which closes itself after 5 s: as a banner, and as a full screen (on by default).
  - Next break = break end + short interval.
- **Skip** (notification or break screen): cancel `BREAK_END`; next break = now + short interval.
- **Snooze 5 min:** cancel `BREAK_END`; the same break type re-fires in 5 min.
- **Done** on the break screen only closes the screen. Timing does not change.
- A break that would start at or after sleep time is not scheduled.

### 5.3 Water

- **Smart mode (default):**
  - Goal comes from the season table below.
  - Spacing: `interval = clamp((cutoff − now) / glassesLeft, 30 min, 120 min)`, where `cutoff = sleep − stopBeforeSleep` (default 90 min).
  - No reminder if `now ≥ cutoff`, if `glassesLeft ≤ 0`, or if the next time would be after the cutoff. When the next time would be after the cutoff, one last reminder is set 10 min before the cutoff instead, if that is still ahead (decided after v1: the 30-min gap should not cost a reminder).
- **Custom mode** (smart switched off): uses your own goal and a fixed "every X min" interval, with the same cutoff.
- The first reminder of the day is wake + 10 min, with the text "Start your day with 1–2 glasses".
- **Drank 1 glass** (notification button, Home or water screen): glasses + 1, then the next reminder is recomputed from now.
- An ignored reminder comes back after one interval, recomputed from the time it fired.

| Months | Season | Goal (glasses of 250 ml) | Tip shown |
|---|---|---|---|
| Apr–Jun | Summer / loo | 12 (3 L) | Nimbu pani, chaas or ORS after sweating. Avoid ice-cold water right after coming in from the heat. |
| Jul–Sep | Monsoon | 10 (2.5 L) | Humidity hides sweat loss. Drink boiled or filtered water. |
| Mar, Oct | Transition | 10 (2.5 L) | Sip regularly, even if you're not thirsty. |
| Nov–Feb | Winter | 8 (2 L) | Less thirst, same need. Warm water helps. |

The general tip "A glass about 30 min before meals" is always shown. The app shows a one-line disclaimer: general guidance, follow your doctor if you have kidney or heart conditions.

### 5.4 Pause

- Options: 30 min, 1 h, until tomorrow.
- Pausing cancels `BREAK`, `BREAK_END` and `WATER`, and sets `RESUME` at the pause end. "Until tomorrow" means the next `DAY_START` instead.
- On resume: recompute from now.

### 5.5 Notifications

Android never lets an app edit a notification channel, so each alert has one channel per choice (sound and vibration mode, and pop-up or quiet), with ids like `water_0p`. A channel is made when it is first used and removed when nothing uses it any more.

| Alert | Text | Buttons |
|---|---|---|
| Short break | "Eye break · look 6 m away" + countdown | Skip, Snooze 5 min |
| Long break | "Long break · stand, walk, stretch" + countdown | Skip, Snooze 5 min |
| Break over | "Break over", closes itself after 5 s | none (Done when full screen) |
| Water | "Drink water · 5/12 glasses" + season tip | Drank 1 glass |
| Phone use | "Time for a break · You've been using your phone for N minutes in a row. Look at something 6 m away." | none |

By default every alert is full screen (drawn over the open app, when "Display over other apps" is on) and also posts a pop-up banner. Break over uses vibration only.

- **Per alert:** sound and vibration is one of sound + vibration, sound only, vibration only, silent. It appears as full screen (the default), a pop-up banner, or quiet. Without the overlay permission the banner is all that shows, at the top of the screen.
- Tapping a notification opens the matching screen (`short`, `long`, `water` or `phone`). The phone-use screen has no scheduled break behind it: it counts down the short-break length from when it opens.
- **Wake screen switch on:** break and water notifications add a full-screen intent to MainActivity. For these launches only, MainActivity calls `setShowWhenLocked(true)` and `setTurnScreenOn(true)`. Android shows a full-screen intent as a pop-up banner while the phone is in use.
- **Full screen** (short, long, break over, water, phone; on by default): the banner is still posted (with the full-screen intent attached when the phone is locked), and when the user has allowed "Display over other apps" (`SYSTEM_ALERT_WINDOW`) the reminder is also drawn as an overlay window (`TYPE_APPLICATION_OVERLAY`, class `Overlay`) over the open app: a title, a countdown to the end of the break (20 s for phone use, 2 min and no clock for water, 5 s for break over) and buttons (Skip and Done; Drank 1 glass and Done; Done for phone use and break over). The overlay starts no screen from the background, which phones such as Vivo refuse unless a separate pop-up permission is on. It is skipped while the phone rings or a call is on (any `AudioManager` mode other than normal) so it cannot cover the call buttons. Views are made with a window context (Android 11+); if adding the window fails, DigitalCare's own screen is opened instead. Every try is recorded ("shown", "permission", "call" or "failed: <exception>") and shown in Settings → Alerts, and on Home when it did not show today. "Send a test alert" draws the overlay too (10 s) when that alert is set to Full screen, with its texts in the page language. Saving with Full screen newly on and the permission off opens the permission page; the Setup card keeps a Fix row until it is on. Without the permission the banner still posts at the top of the screen. Settings saved before full screen became the default are switched to it once: a save writes the `fullScreenDefault` marker, and the saved choices stay after that.
- **Language:** the whole app (every screen, and the notifications) follows the Language setting, English or Hindi; the Settings page changes the moment a language is picked. The notification texts are in `res/values/strings.xml` (English) and `res/values-hi/strings.xml` (Hindi) and are read with a context set to the chosen language, not the phone's. The screens' own text is in `src/text.ts`, typed so that the build fails if a Hindi string is missing. A unit test checks that every Hindi notification string exists and has the same placeholders. The phone's own menu names in the Vivo steps stay in English, as the phone shows them. Hindi text is set at 90% of the English size (`styles.css`), because Devanagari letters stand taller than Latin ones.

### 5.6 Alarms and permissions

- Alarms use `setExactAndAllowWhileIdle(RTC_WAKEUP, …)`, with one PendingIntent per event kind (fixed request codes), so setting an alarm again replaces the old one.
- Manifest permissions: `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `VIBRATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` (the phone-use counter), `SYSTEM_ALERT_WINDOW` (only for the Full screen choice) and `INTERNET` (from the Capacitor template, unused).
- `USE_EXACT_ALARM` is granted at install for a sideloaded app. Code comment: `shortcut: USE_EXACT_ALARM is fine for sideload, Play Store publishing needs policy review`.

## 6. Settings and defaults

| Setting | Default | Range |
|---|---|---|
| Mon–Fri wake / sleep | 07:00 / 23:00 | any time |
| Sat, Sun wake / sleep | 08:30 / 23:30 | any time |
| Day on | all 7 days | any days, at least one |
| Eye breaks on | on | on/off |
| Short break every / length | 20 min / 20 s | 1–60 min / 10–60 s |
| Long break every / length | 60 min / 5 min | 2 × the short interval to 1440 min / 1–15 min |
| Water on | on | on/off |
| Smart water | on | on/off |
| Custom goal / every | 8 glasses / 60 min | 1–20 / 1–180 min |
| Glass size | 250 ml | 100–500 ml (display only) |
| Stop before sleep | 90 min | 0–180 min |
| Wake screen | off | on/off |
| Alert shows as | full screen | full screen, pop-up banner, quiet |
| Language | English | English, Hindi |

Snooze is fixed at 5 min. The 1-minute minimums exist so on-phone testing is quick.

## 7. Plugin API (TypeScript side)

```ts
export type Screen = 'home' | 'short' | 'long' | 'water' | 'phone'
export type SystemPage = 'channel' | 'battery' | 'fullScreen' | 'overlay' | 'nightLight' | 'extraDim' | 'appDetails'
export type AlertId = 'short' | 'long' | 'over' | 'water' | 'phone'

export interface Settings {
  // Hours for each day, Monday first, as minutes after midnight. dayOn: 1 = reminders that day, 0 = none.
  dayWake: number[]; daySleep: number[]; dayOn: number[]
  breaksOn: boolean; shortEveryMin: number; shortSec: number; longEveryMin: number; longMin: number
  waterOn: boolean; smartWater: boolean; customGoal: number; customEveryMin: number; glassMl: number
  stopBeforeSleepMin: number; wakeScreen: boolean
  language: 'en' | 'hi' // the language of the reminders: notifications and the reminder screens
  screenOn: boolean; screenLimitMin: number
  waterCustom: boolean; waterStart: string; waterStop: string
  // Sound: 0 sound + vibration, 1 sound only, 2 vibration only, 3 silent. Popup false = quiet.
  shortSound: number; longSound: number; overSound: number; waterSound: number; phoneSound: number
  shortPopup: boolean; longPopup: boolean; overPopup: boolean; waterPopup: boolean; phonePopup: boolean
  // Full screen: the reminder is drawn over the open app (needs "Display over other apps"). On by default.
  shortFull: boolean; longFull: boolean; overFull: boolean; waterFull: boolean; phoneFull: boolean
}

export interface State {
  settings: Settings
  glasses: number; goal: number; season: 'summer' | 'monsoon' | 'transition' | 'winter'; tip: string
  inDay: boolean; nextDayStartAt: number | null
  nextBreakAt: number | null; nextBreakType: 'short' | 'long' | null; breakEndAt: number | null
  nextWaterAt: number | null; waterCutoffAt: number | null; pausedUntil: number | null
  phoneRunning: boolean; phoneInUseSince: number | null; phoneRemindedAt: number | null
  dayStartAt: number | null; dayEndAt: number | null; waterStartAt: number | null
  setup: { notifications: boolean; battery: boolean; fullScreen: boolean; overlay: boolean; phoneUse: boolean }
  manufacturer: string
}

export interface DigitalCarePlugin {
  getState(): Promise<State>
  saveSettings(settings: Settings): Promise<State>
  logGlass(): Promise<State>
  removeGlass(): Promise<State>
  pause(opts: { minutes: number | null }): Promise<State>
  resume(): Promise<State>
  skipBreak(): Promise<State>
  snoozeBreak(): Promise<State>
  testAlert(opts: { alert: AlertId; sound: number; popup: boolean }): Promise<void>
  openSystemSettings(opts: { page: SystemPage; channel?: string }): Promise<void>
  /** Sends DigitalCare to the back, if a reminder opened it over another app: you land where you were. */
  leave(): Promise<void>
  getLaunchScreen(): Promise<{ screen: Screen }>
  addListener(event: 'launchScreen', cb: (e: { screen: Screen }) => void): Promise<PluginListenerHandle>
  addListener(event: 'appResume', cb: () => void): Promise<PluginListenerHandle>
}
```

React fetches `getState()` again whenever the app comes to the front (the native `appResume` event, which also fires after system dialogs) and once more 1.5 s after launch. Countdowns are calculated in React from the timestamps.

System pages:

- `nightLight`: `Settings.ACTION_NIGHT_DISPLAY_SETTINGS`, falling back to `ACTION_DISPLAY_SETTINGS`. On Vivo, the built-in option is called "Eye protection".
- `extraDim`: `ACTION_ACCESSIBILITY_SETTINGS`, with a hint to find "Extra dim" (it may not exist on Vivo).

## 8. Screens

- **Home:**
  - Setup card (until done).
  - Next break countdown and next water time, or "Reminders start at 07:00" outside wake hours.
  - Water ring with a +1 glass button.
  - Season tip.
  - Pause / Resume.
  - Night light and Extra Dim buttons.
  - Settings link.
- **Break screen** (`short` / `long` / `phone`; the phone-use one has no scheduled break and counts down from when it opens):
  - Animation.
  - Countdown ring plus the remaining time as text.
  - Tip.
  - Done and Skip (no Skip for the phone-use screen). Done also hands the phone back to the app the reminder opened over.
- **Water screen:**
  - Animation.
  - Today's count / goal.
  - Drank 1 glass and Done.
- **Settings:**
  - All settings from section 6, in cards: Language, Eye breaks and hours (with the hours for each day), Phone use, Water, Lock screen, Alerts. Edits not saved yet survive a reminder opening over the screen.
  - A "Sound & vibration" button per channel.
  - The disclaimer.
- **Animations:**
  - Three built-in animated WebP files (eye looking far, stretching, drinking), each 300 KB or less.
  - Self-made or CC0-licensed.
  - Under `prefers-reduced-motion`, a static PNG is shown instead (`<picture>` media query).
- **Accessibility:**
  - Touch targets of 48 dp or more.
  - Text contrast at AA level.
  - Countdown always shown as text as well as the ring.
  - Layout follows the system font size.

## 9. Setup card and Vivo steps

| Item | Check | Action |
|---|---|---|
| Notifications | `areNotificationsEnabled()` | runtime prompt |
| Battery optimization off | `isIgnoringBatteryOptimizations()` | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| Full-screen (only if Wake screen on) | `canUseFullScreenIntent()` (API 34+) | `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` |

When `manufacturer` is vivo, the app also shows these steps. They can't be checked from code, can be dismissed, and their exact names vary by FuntouchOS/OriginOS version:

1. Settings → Battery → Background power consumption management → DigitalCare → allow high background usage / don't restrict.
2. Settings → Apps → Autostart (on some versions: i Manager → App manager → Autostart) → DigitalCare on.
3. App info → Permissions → "Display on lock screen" and "Display pop-up windows while running in background" on (needed for Wake screen).
4. Recent apps → lock the DigitalCare card.

Other brands get a single link to dontkillmyapp.com.

## 10. Testing

**Scheduler unit tests** (JUnit, `gradlew test`, no phone needed):

1. Each day uses its own wake/sleep time, a day that is off has no window, and the next wake skips off days.
2. A sleep time after midnight (00:30) gives the correct window.
3. The long break comes exactly the set number of minutes after the last one ended, even between two regular slots; snooze keeps the type and does not restart the timer; skip moves to the next break.
4. The next break = break end + short interval.
5. Pause until tomorrow resumes at the next wake time.
6. Water spacing: behind schedule hits the 30-min floor, ahead hits the 120-min cap, goal met gives no reminder, and nothing comes after the cutoff.
7. Season goal for each month.
8. No events scheduled outside the window; recompute-from-now never returns a past time.

React has no tests (display and bridge calls only).

**On-phone checklist** (Vivo, with test intervals of 1–2 min, then real intervals):

1. Phone locked on the desk for 1 h or more: breaks and water reminders arrive on time.
2. After a reboot, reminders continue.
3. With the app closed, Drank 1 glass increments the count and moves the next water reminder later.
4. With Wake screen on, the break screen appears over the lock screen.
5. Pause / resume and Skip / Snooze behave as described in section 5.

## 11. Build and install

`build-apk.ps1` is a trimmed copy of BhumiSetu's `create-apk.ps1`. It runs `gradlew test`, then `npm run build`, then `npx cap sync android`, then `gradlew assembleDebug`, and copies the result to `digitalcare.apk`. Install with `C:\android-sdk\platform-tools\adb install -r digitalcare.apk`, or copy the APK to the phone.

## 12. Risks

| Risk | Mitigation |
|---|---|
| Vivo kills or delays background alarms (the biggest risk) | Vivo setup steps; on-phone checklist item 1 runs first |
| Vivo shows a banner instead of the lock-screen break screen | Notification still arrives; accepted |
| The allow-while-idle alarm quota is assumed to be about 72/h on Android 12+, and this app needs about 8/h | Verify with checklist item 1 |
| Vivo's per-channel settings may hide the vibration toggle | Channel defaults (section 5.5) are sensible |
| `USE_EXACT_ALARM` would block Play Store publishing | Fine for sideload; revisit if publishing |
