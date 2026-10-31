package com.digitalcare.app;

import static com.digitalcare.app.Scheduler.Kind.*;
import static com.digitalcare.app.Times.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class PauseRecomputeTest {
    Settings s = new Settings();
    DayState d = new DayState();

    @Before
    public void startMonday() { Scheduler.onDayStart(s, d, mon("07:00"), Z); }

    @Test
    public void pauseUntilTomorrowResumesAtNextWake() {
        Scheduler.pause(s, d, mon("15:00"), Z, null);
        assertEquals(at("2026-11-03T07:00"), d.pausedUntil);
        assertEquals(0, d.nextBreakAt); assertEquals(0, d.breakEndAt); assertEquals(0, d.nextWaterAt);
        assertEquals(0, Scheduler.alarmAt(RESUME, d));
        assertEquals(at("2026-11-03T07:00"), Scheduler.alarmAt(DAY_START, d));
        Scheduler.onDayStart(s, d, at("2026-11-03T07:00"), Z);
        assertEquals(0, d.pausedUntil); assertEquals(at("2026-11-03T07:20"), d.nextBreakAt);
    }

    @Test
    public void pauseUntilTomorrowAfterMidnightMeansNextWake() {
        s.weekdaySleep = "00:30";
        Scheduler.pause(s, d, at("2026-11-03T00:10"), Z, null);
        assertEquals(at("2026-11-03T07:00"), d.pausedUntil);
    }

    @Test
    public void pauseMinutesArmsResumeOnly() {
        Scheduler.pause(s, d, mon("10:00"), Z, 30);
        assertEquals(mon("10:30"), Scheduler.alarmAt(RESUME, d));
        assertEquals(0, Scheduler.alarmAt(DAY_START, d)); assertEquals(0, Scheduler.alarmAt(BREAK, d));
        Scheduler.resume(s, d, mon("10:30"), Z);
        assertEquals(0, d.pausedUntil); assertEquals(mon("10:50"), d.nextBreakAt);
    }

    @Test
    public void recomputeFromNowNeverReturnsPastTimes() {
        long now = mon("12:00");
        Scheduler.recompute(s, d, now, Z);   // stale 07:20 / 07:10 times from before a reboot
        assertEquals(now + 20 * MIN, d.nextBreakAt); assertTrue(d.nextWaterAt > now);
        for (Scheduler.Kind k : Scheduler.Kind.values()) {
            long t = Scheduler.alarmAt(k, d);
            assertTrue(k + " in the past", t == 0 || t > now);
        }
    }

    @Test
    public void outsideWindowOnlyDayStartIsArmed() {
        Scheduler.recompute(s, d, mon("23:30"), Z);
        for (Scheduler.Kind k : new Scheduler.Kind[]{BREAK, BREAK_END, WATER, RESUME}) assertEquals(0, Scheduler.alarmAt(k, d));
        assertEquals(at("2026-11-03T07:00"), Scheduler.alarmAt(DAY_START, d));
    }

    @Test
    public void dayStartNotArmedDuringTheDay() {
        assertEquals(0, Scheduler.alarmAt(DAY_START, d));
    }

    @Test
    public void staleDayStartRecomputes() {
        s.weekdayWake = "09:00";                       // DAY_START was armed for 07:00, window now starts 09:00
        Scheduler.onDayStart(s, d, at("2026-11-03T07:00"), Z);
        assertEquals(at("2026-11-03T09:00"), Scheduler.alarmAt(DAY_START, d));
        assertEquals(0, d.nextBreakAt);
    }

    @Test
    public void settingsChangeMidDayKeepsGlasses() {           // Review Focus 2
        d.glasses = 4; s.weekdayWake = "06:30";
        Scheduler.recompute(s, d, mon("12:00"), Z);
        assertEquals(4, d.glasses);
    }

    @Test
    public void missedDayStartResetsOnRecompute() {            // Review Focus 3
        d.glasses = 6; d.breakCount = 9;
        Scheduler.recompute(s, d, at("2026-11-03T09:00"), Z);         // phone was off at 07:00
        assertEquals(0, d.glasses); assertEquals(0, d.breakCount);
    }

    @Test
    public void settingsSaveWhilePausedStaysPaused() {         // Review Focus 5
        Scheduler.pause(s, d, mon("10:00"), Z, 60);
        Scheduler.recompute(s, d, mon("10:15"), Z);
        assertEquals(mon("11:00"), d.pausedUntil);
        assertEquals(0, d.nextBreakAt); assertEquals(0, d.nextWaterAt);
        assertEquals(mon("11:00"), Scheduler.alarmAt(RESUME, d));
    }

    @Test
    public void wakeMovedLaterKeepsGlasses() {                   // Review Focus 2, final review
        Scheduler.logGlass(s, d, mon("07:20"), Z);
        Scheduler.logGlass(s, d, mon("07:40"), Z);
        s.weekdayWake = "08:00";
        Scheduler.recompute(s, d, mon("07:45"), Z);               // settings saved before the new wake time
        assertEquals(mon("08:00"), Scheduler.alarmAt(DAY_START, d));
        Scheduler.onDayStart(s, d, mon("08:00"), Z);
        assertEquals(2, d.glasses); assertFalse(d.waterFirst);
        assertEquals(mon("08:20"), d.nextBreakAt);
    }

    @Test
    public void wakeMovedLaterKeepsPauseUntilTomorrow() {        // Review Focus 5, final review
        Scheduler.pause(s, d, mon("07:30"), Z, null);
        s.weekdayWake = "08:00";
        Scheduler.recompute(s, d, mon("07:45"), Z);
        Scheduler.onDayStart(s, d, mon("08:00"), Z);
        assertEquals(at("2026-11-03T07:00"), d.pausedUntil);
        assertEquals(0, d.nextBreakAt); assertEquals(0, d.nextWaterAt);
    }

    @Test
    public void alarmIsDueOnlyWhileArmedAndReached() {           // final review: re-arming on app open must not double-fire
        assertFalse(Scheduler.due(BREAK, d, mon("07:19")));
        assertTrue(Scheduler.due(BREAK, d, mon("07:20")));
        Scheduler.skip(s, d, mon("07:20"), Z);                    // the Skip button handled it first
        assertFalse(Scheduler.due(BREAK, d, mon("07:20:01")));    // so the in-flight 07:20 alarm is ignored
        Scheduler.pause(s, d, mon("07:21"), Z, 30);
        assertFalse(Scheduler.due(WATER, d, mon("07:25")));       // nothing fires during a pause
    }

    @Test
    public void staleDetection() {
        assertTrue(Scheduler.stale(new DayState(), mon("12:00")));    // never started
        assertFalse(Scheduler.stale(d, mon("07:05")));
        assertTrue(Scheduler.stale(d, mon("07:30")));                 // the 07:10 water alarm never fired
    }
}
