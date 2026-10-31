# EyeCare v1 — Design Spec

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

**In v1:** short and long eye breaks · start and break-over signals · Skip / Snooze · weekday/weekend wake and sleep times · Pause · water reminders with glass counter, season goal and smart spacing · custom water mode · per-type sound/vibration via Android settings · optional silent screen wake that shows the break screen on the lock screen · built-in animations · night light and Extra Dim shortcuts · setup card with Vivo steps.

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
| App id | `com.eyecare.app` |
| Toolchain | Reused: Android SDK `C:\android-sdk` (platform 36, build-tools 35), Gradle 9.1 cache, Java 25. No emulator; test on the phone. |
| Disk | About 0.5–1 GB on D: (node_modules + build output) |

## 4. Architecture

```
React UI (Home · Break screen · Settings)
    │  EyeCarePlugin (Capacitor bridge)
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

- There are two sets of wake and sleep times: weekday and weekend. Weekend days are selectable (default Sat, Sun).
- If sleep ≤ wake (for example sleep 00:30), the window ends on the next calendar day.
- `DAY_START` fires at wake time. It resets glasses and the break counter, clears "pause until tomorrow", sets next break = wake + short interval, and sets next water = wake + 10 min.
- Nothing fires after sleep time. The only alarm armed then is `DAY_START` for the next wake.

### 5.2 Eye breaks

- Break counter `n` increments each time a break fires. A snoozed re-fire does not increment it.
- The break is **long** when `n % N == 0`. N defaults to 3: with a 20-min short interval that's a long break every 60 min. The long-break setting is shown in minutes (N × short interval).
- When a break fires:
  - Post the break notification with a countdown (Android chronometer), **Skip** and **Snooze 5 min** buttons.
  - Set `BREAK_END` at now + duration (20 s short, 5 min long).
- At `BREAK_END`:
  - Cancel the break notification.
  - Post "Break over", which auto-dismisses after 10 s.
  - Next break = break end + short interval.
- **Skip** (notification or break screen): cancel `BREAK_END`; next break = now + short interval.
- **Snooze 5 min:** cancel `BREAK_END`; the same break type re-fires in 5 min.
- **Done** on the break screen only closes the screen. Timing does not change.
- A break that would start at or after sleep time is not scheduled.

### 5.3 Water

- **Smart mode (default):**
  - Goal comes from the season table below.
  - Spacing: `interval = clamp((cutoff − now) / glassesLeft, 30 min, 120 min)`, where `cutoff = sleep − stopBeforeSleep` (default 90 min).
  - No reminder if `now ≥ cutoff`, if `glassesLeft ≤ 0`, or if the next time would be after the cutoff.
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

Channels have fixed ids and are never recreated, so the user's choices in Android settings stick.

| Channel id | Importance | Default sound / vibration | Content |
|---|---|---|---|
| `short_break` | HIGH | sound + vibration | "Eye break · look 6 m away" + countdown, Skip, Snooze 5 min |
| `long_break` | HIGH | sound + vibration | "Long break · stand, walk, stretch" + countdown, Skip, Snooze 5 min |
| `break_over` | DEFAULT | vibration only (no sound) | "Break over", auto-dismiss after 10 s |
| `water` | HIGH | sound + vibration | "Drink water · 5/12 glasses" + season tip, Drank 1 glass |

- Tapping a notification opens the matching screen (`short`, `long` or `water`).
- **Wake screen switch on:** break and water notifications add a full-screen intent to MainActivity. For these launches only, MainActivity calls `setShowWhenLocked(true)` and `setTurnScreenOn(true)`.
- **Sound and vibration per type** are changed in Android's channel settings, opened from the app's Settings screen.

### 5.6 Alarms and permissions

- Alarms use `setExactAndAllowWhileIdle(RTC_WAKEUP, …)`, with one PendingIntent per event kind (fixed request codes), so setting an alarm again replaces the old one.
- Manifest permissions: `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `VIBRATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- `USE_EXACT_ALARM` is granted at install for a sideloaded app. Code comment: `shortcut: USE_EXACT_ALARM is fine for sideload, Play Store publishing needs policy review`.

## 6. Settings and defaults

| Setting | Default | Range |
|---|---|---|
| Weekday wake / sleep | 07:00 / 23:00 | any time |
| Weekend wake / sleep | 08:30 / 23:30 | any time |
| Weekend days | Sat, Sun | any days |
| Eye breaks on | on | on/off |
| Short break every / length | 20 min / 20 s | 1–60 min / 10–60 s |
| Long break every / length | 60 min (N = 3) / 5 min | N = 2–6 / 1–15 min |
| Water on | on | on/off |
| Smart water | on | on/off |
| Custom goal / every | 8 glasses / 60 min | 1–20 / 1–180 min |
| Glass size | 250 ml | 100–500 ml (display only) |
| Stop before sleep | 90 min | 0–180 min |
| Wake screen | off | on/off |

Snooze is fixed at 5 min. The 1-minute minimums exist so on-phone testing is quick.

## 7. Plugin API (TypeScript side)

```ts
type Screen = 'home' | 'short' | 'long' | 'water';
type SystemPage = 'channel' | 'battery' | 'fullScreen' | 'nightLight' | 'extraDim' | 'appDetails';

getState(): Promise<State>
saveSettings(settings: Settings): Promise<State>
logGlass(): Promise<State>
pause(opts: { minutes: number | null }): Promise<State>   // null = until tomorrow
resume(): Promise<State>
skipBreak(): Promise<State>
snoozeBreak(): Promise<State>
openSystemSettings(opts: { page: SystemPage; channel?: string }): Promise<void>
getLaunchScreen(): Promise<{ screen: Screen }>             // plus 'launchScreen' event on new intents

interface State {
  settings: Settings;
  glasses: number; goal: number; season: 'summer' | 'monsoon' | 'transition' | 'winter';
  inDay: boolean; nextDayStartAt: number | null;
  nextBreakAt: number | null; nextBreakType: 'short' | 'long' | null; breakEndAt: number | null;
  nextWaterAt: number | null; pausedUntil: number | null;
  setup: { notifications: boolean; battery: boolean; fullScreen: boolean };
  manufacturer: string;
}
```

React fetches `getState()` again whenever the app comes to the front (`visibilitychange`). Countdowns are calculated in React from the timestamps.

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
- **Break screen** (`short` / `long`):
  - Animation.
  - Countdown ring plus the remaining time as text.
  - Tip.
  - Done and Skip.
- **Water screen:**
  - Animation.
  - Today's count / goal.
  - Drank 1 glass and Done.
- **Settings:**
  - All settings from section 6.
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

1. Settings → Battery → Background power consumption management → EyeCare → allow high background usage / don't restrict.
2. Settings → Apps → Autostart (on some versions: i Manager → App manager → Autostart) → EyeCare on.
3. App info → Permissions → "Display on lock screen" and "Display pop-up windows while running in background" on (needed for Wake screen).
4. Recent apps → lock the EyeCare card.

Other brands get a single link to dontkillmyapp.com.

## 10. Testing

**Scheduler unit tests** (JUnit, `gradlew test`, no phone needed):

1. Weekday vs weekend wake/sleep selection, including custom weekend days.
2. A sleep time after midnight (00:30) gives the correct window.
3. Every Nth break is long; snooze keeps the type and doesn't increment the counter; skip moves to the next break.
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

`build-apk.ps1` is a trimmed copy of BhumiSetu's `create-apk.ps1`. It runs `gradlew test`, then `npm run build`, then `npx cap sync android`, then `gradlew assembleDebug`, and copies the result to `eyecare.apk`. Install with `C:\android-sdk\platform-tools\adb install -r eyecare.apk`, or copy the APK to the phone.

## 12. Risks

| Risk | Mitigation |
|---|---|
| Vivo kills or delays background alarms (the biggest risk) | Vivo setup steps; on-phone checklist item 1 runs first |
| Vivo shows a banner instead of the lock-screen break screen | Notification still arrives; accepted |
| The allow-while-idle alarm quota is assumed to be about 72/h on Android 12+, and this app needs about 8/h | Verify with checklist item 1 |
| Vivo's per-channel settings may hide the vibration toggle | Channel defaults (section 5.5) are sensible |
| `USE_EXACT_ALARM` would block Play Store publishing | Fine for sideload; revisit if publishing |
