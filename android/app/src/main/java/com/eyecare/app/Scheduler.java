package com.eyecare.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Pure reminder timing: (settings, day state, now) -> next times. No Android imports. */
final class Scheduler {
    static final long MIN = 60_000;

    private Scheduler() {}

    /** One day's wake window; end is exclusive and falls on the next day when sleep <= wake. */
    static final class Window {
        final LocalDate date;
        final long start, end;

        Window(LocalDate date, long start, long end) {
            this.date = date;
            this.start = start;
            this.end = end;
        }
    }

    static Window windowAt(Settings s, long now, ZoneId z) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(z).toLocalDate();
        for (LocalDate date : new LocalDate[]{today, today.minusDays(1)}) {
            Window w = window(s, date, z);
            if (w.start <= now && now < w.end) return w;
        }
        return null;
    }

    static long nextWake(Settings s, long now, ZoneId z) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(z).toLocalDate();
        long wake = window(s, today, z).start;
        return wake > now ? wake : window(s, today.plusDays(1), z).start;
    }

    static Season season(long now, ZoneId z) {
        return Season.of(Instant.ofEpochMilli(now).atZone(z).getMonthValue());
    }

    static int goal(Settings s, long now, ZoneId z) {
        return s.smartWater ? season(now, z).goal : s.customGoal;
    }

    private static Window window(Settings s, LocalDate date, ZoneId z) {
        boolean weekend = false;
        for (int d : s.weekendDays) weekend |= d == date.getDayOfWeek().getValue();
        LocalTime wake = LocalTime.parse(weekend ? s.weekendWake : s.weekdayWake);
        LocalTime sleep = LocalTime.parse(weekend ? s.weekendSleep : s.weekdaySleep);
        ZonedDateTime start = date.atTime(wake).atZone(z);
        ZonedDateTime end = (sleep.isAfter(wake) ? date : date.plusDays(1)).atTime(sleep).atZone(z);
        return new Window(date, start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }
}
