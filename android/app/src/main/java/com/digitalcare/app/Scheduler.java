package com.digitalcare.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Pure reminder timing: (settings, day state, now) -> next times. No Android imports. */
final class Scheduler {
    static final long MIN = 60_000;
    static final long SNOOZE_MS = 5 * MIN;
    private static final long FIRST_WATER_MS = 10 * MIN;
    private static final long WATER_MIN_MS = 30 * MIN, WATER_MAX_MS = 120 * MIN, LAST_CALL_MS = 10 * MIN;

    private Scheduler() {}

    enum Kind { BREAK, BREAK_END, WATER, DAY_START, RESUME }

    /** One day's wake window; end is exclusive and falls on the next day when sleep <= wake. */
    static final class Window {
        final LocalDate date;
        final long start, end;
        final ZoneId zone;

        Window(LocalDate date, long start, long end, ZoneId zone) {
            this.date = date;
            this.start = start;
            this.end = end;
            this.zone = zone;
        }
    }

    static Window windowAt(Settings s, long now, ZoneId z) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(z).toLocalDate();
        for (LocalDate date : new LocalDate[]{today, today.minusDays(1)}) {
            Window w = window(s, date, z);
            if (w != null && w.start <= now && now < w.end) return w;
        }
        return null;
    }

    /** The next wake time after now, skipping days that are off; 0 when every day is off. */
    static long nextWake(Settings s, long now, ZoneId z) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(z).toLocalDate();
        for (int i = 0; i <= 7; i++) {
            Window w = window(s, today.plusDays(i), z);
            if (w != null && w.start > now) return w.start;
        }
        return 0;
    }

    static Season season(long now, ZoneId z) {
        return Season.of(Instant.ofEpochMilli(now).atZone(z).getMonthValue());
    }

    static int goal(Settings s, long now, ZoneId z) {
        return s.smartWater ? season(now, z).goal : s.customGoal;
    }

    static boolean nextBreakLong(Settings s, DayState d) {
        return d.snoozed ? d.breakLong : (d.breakCount + 1) % s.longEveryN == 0;
    }

    static void recompute(Settings s, DayState d, long now, ZoneId z) {
        Window w = windowAt(s, now, z);
        if (w != null && w.date.toEpochDay() != d.dayDate) { // matched by date so a wake-time change keeps today
            d.glasses = 0;
            d.breakCount = 0;
            d.dayDate = w.date.toEpochDay();
            d.waterFirst = false;
        }
        d.breakEndAt = 0;
        d.snoozed = false;
        if (d.pausedUntil > now) {
            d.nextBreakAt = 0;
            d.nextWaterAt = 0;
        } else {
            d.pausedUntil = 0;
            d.nextBreakAt = breakAt(s, w, now + s.shortEveryMin * MIN);
            d.nextWaterAt = waterNext(s, d, now, z);
        }
        finish(s, d, now, z);
    }

    /** minutes == null pauses until the next wake. */
    static void pause(Settings s, DayState d, long now, ZoneId z, Integer minutes) {
        d.pausedUntil = minutes == null ? nextWake(s, now, z) : now + minutes * MIN;
        d.nextBreakAt = 0;
        d.breakEndAt = 0;
        d.nextWaterAt = 0;
        d.snoozed = false;
        finish(s, d, now, z);
    }

    static void resume(Settings s, DayState d, long now, ZoneId z) {
        d.pausedUntil = 0;
        recompute(s, d, now, z);
    }

    /** Alarm time for each kind, 0 = cancel. DAY_START is armed only when nothing else is. */
    static long alarmAt(Kind k, DayState d) {
        switch (k) {
            case BREAK: return d.nextBreakAt;
            case BREAK_END: return d.breakEndAt;
            case WATER: return d.nextWaterAt;
            case RESUME: return d.pausedUntil > 0 && d.pausedUntil < d.nextDayStartAt ? d.pausedUntil : 0;
            default:
                boolean idle = d.nextBreakAt == 0 && d.breakEndAt == 0 && d.nextWaterAt == 0 && alarmAt(Kind.RESUME, d) == 0;
                return idle ? d.nextDayStartAt : 0;
        }
    }

    /** True when nothing is armed (first launch) or an armed alarm is over a minute overdue (lost). */
    static boolean stale(DayState d, long now) {
        boolean armed = false;
        for (Kind k : Kind.values()) {
            long t = alarmAt(k, d);
            if (t > 0 && t < now - MIN) return true;
            armed |= t > 0;
        }
        return !armed;
    }

    /** False for an alarm no longer armed at or before now: it was in flight when Skip/Pause/Save re-armed it. */
    static boolean due(Kind k, DayState d, long now) {
        long t = alarmAt(k, d);
        return t != 0 && t <= now;
    }

    static void onDayStart(Settings s, DayState d, long now, ZoneId z) {
        Window w = windowAt(s, now, z);
        // Stale alarm (wake changed after it was set), or today already started (wake moved later, clock set back).
        if (w == null || w.date.toEpochDay() == d.dayDate) {
            recompute(s, d, now, z);
            return;
        }
        d.glasses = 0;
        d.breakCount = 0;
        d.dayDate = w.date.toEpochDay();
        d.pausedUntil = 0;
        d.breakEndAt = 0;
        d.snoozed = false;
        d.nextBreakAt = breakAt(s, w, now + s.shortEveryMin * MIN);
        d.waterFirst = true;
        long first = s.waterCustom ? Math.max(now, waterStart(s, w)) : now + FIRST_WATER_MS;
        d.nextWaterAt = s.waterOn && first <= cutoff(s, w) ? first : 0;
        finish(s, d, now, z);
    }

    /** Returns true when the break should be shown; a late alarm outside wake hours shows nothing. */
    static boolean onBreak(Settings s, DayState d, long now, ZoneId z) {
        Window w = windowAt(s, now, z);
        if (newDay(s, d, w, now, z)) return false;
        boolean show = w != null && s.breaksOn;
        if (show) {
            if (!d.snoozed) {
                d.breakCount++;
                d.breakLong = d.breakCount % s.longEveryN == 0;
            }
            d.breakEndAt = now + (d.breakLong ? s.longMin * MIN : s.shortSec * 1000L);
        }
        d.snoozed = false;
        d.nextBreakAt = 0;
        finish(s, d, now, z);
        return show;
    }

    static void onBreakEnd(Settings s, DayState d, long now, ZoneId z) {
        Window w = windowAt(s, now, z);
        if (newDay(s, d, w, now, z)) return;
        d.breakEndAt = 0;
        d.nextBreakAt = breakAt(s, w, now + s.shortEveryMin * MIN);
        finish(s, d, now, z);
    }

    static void skip(Settings s, DayState d, long now, ZoneId z) {
        d.snoozed = false;
        onBreakEnd(s, d, now, z);
    }

    static void snooze(Settings s, DayState d, long now, ZoneId z) {
        d.breakEndAt = 0;
        d.nextBreakAt = breakAt(s, windowAt(s, now, z), now + SNOOZE_MS);
        d.snoozed = d.nextBreakAt != 0;
        finish(s, d, now, z);
    }

    /** Returns true when the reminder should be shown; an ignored reminder comes back one interval later. */
    static boolean onWater(Settings s, DayState d, long now, ZoneId z) {
        Window w = windowAt(s, now, z);
        if (newDay(s, d, w, now, z)) return false;
        boolean show = w != null && s.waterOn && now < cutoff(s, w);
        if (show) d.waterFirst = false;
        d.nextWaterAt = show ? waterNext(s, d, now, z) : 0;
        finish(s, d, now, z);
        return show;
    }

    static void logGlass(Settings s, DayState d, long now, ZoneId z) {
        d.glasses++;
        d.waterFirst = false;
        d.nextWaterAt = d.pausedUntil > now ? 0 : waterNext(s, d, now, z);
        finish(s, d, now, z);
    }

    /** Takes back a mis-tapped glass. A reminder already scheduled stays; reminders that stopped at the goal restart. */
    static void removeGlass(Settings s, DayState d, long now, ZoneId z) {
        if (d.glasses > 0) d.glasses--;
        if (d.nextWaterAt == 0 && d.pausedUntil <= now) d.nextWaterAt = waterNext(s, d, now, z);
        finish(s, d, now, z);
    }

    /** Phone-use reminders only inside the wake window, and never while paused or switched off. */
    static boolean screenReminderAllowed(Settings s, DayState d, long now, ZoneId z) {
        return s.screenOn && d.pausedUntil <= now && windowAt(s, now, z) != null;
    }

    /** The service ran before, is switched on, and is down now: the user should hear that it was stopped. */
    static boolean screenStopped(Settings s, boolean ranBefore, boolean runningNow) {
        return s.screenOn && ranBefore && !runningNow;
    }

    static long waterNext(Settings s, DayState d, long now, ZoneId z) {
        Window w = windowAt(s, now, z);
        if (!s.waterOn || w == null) return 0;
        long cutoff = cutoff(s, w);
        int left = goal(s, now, z) - d.glasses;
        if (now >= cutoff || left <= 0) return 0;
        long interval = s.smartWater
                ? Math.max(WATER_MIN_MS, Math.min(WATER_MAX_MS, (cutoff - now) / left))
                : s.customEveryMin * MIN;
        long start = s.waterCustom ? waterStart(s, w) : w.start;
        long next = Math.max(now + interval, start);
        if (next < cutoff) return next;
        long last = cutoff - LAST_CALL_MS; // the gap does not fit: one last reminder shortly before the stop
        return last > now && last >= start ? last : 0;
    }

    /**
     * An alarm firing in a window whose day never started (held overnight, back-to-back windows)
     * starts that day from now instead of continuing yesterday's counts.
     */
    private static boolean newDay(Settings s, DayState d, Window w, long now, ZoneId z) {
        if (w == null || w.date.toEpochDay() == d.dayDate) return false;
        recompute(s, d, now, z);
        return true;
    }

    private static long breakAt(Settings s, Window w, long t) {
        return s.breaksOn && w != null && t < w.end ? t : 0;
    }

    /** When water reminders end: the chosen stop time (after midnight if it is before wake) or sleep minus the margin; never after sleep. */
    static long cutoff(Settings s, Window w) {
        if (!s.waterCustom) return w.end - s.stopBeforeSleepMin * MIN;
        long stop = onDate(w, s.waterStop, 0);
        return Math.min(w.end, stop <= w.start ? onDate(w, s.waterStop, 1) : stop);
    }

    /** The first moment water reminders may fire when a start time is chosen; never before wake. */
    private static long waterStart(Settings s, Window w) {
        return Math.max(w.start, onDate(w, s.waterStart, 0));
    }

    /** When the window's first water reminder falls: the chosen start time, or 10 min after wake. */
    static long waterStartAt(Settings s, Window w) {
        return s.waterCustom ? waterStart(s, w) : w.start + FIRST_WATER_MS;
    }

    private static long onDate(Window w, String hhmm, int plusDays) {
        return w.date.plusDays(plusDays).atTime(LocalTime.parse(hhmm)).atZone(w.zone).toInstant().toEpochMilli();
    }

    private static void finish(Settings s, DayState d, long now, ZoneId z) {
        d.nextDayStartAt = nextWake(s, now, z);
    }

    /** One day's hours, or null when reminders are off that day. */
    private static Window window(Settings s, LocalDate date, ZoneId z) {
        int i = date.getDayOfWeek().getValue() - 1; // Monday first
        if (s.dayOn[i] == 0) return null;
        LocalTime wake = LocalTime.ofSecondOfDay(s.dayWake[i] * 60L);
        LocalTime sleep = LocalTime.ofSecondOfDay(s.daySleep[i] * 60L);
        ZonedDateTime start = date.atTime(wake).atZone(z);
        ZonedDateTime end = (sleep.isAfter(wake) ? date : date.plusDays(1)).atTime(sleep).atZone(z);
        return new Window(date, start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), z);
    }
}
