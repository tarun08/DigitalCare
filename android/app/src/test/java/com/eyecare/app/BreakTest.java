package com.eyecare.app;

import static com.eyecare.app.Times.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class BreakTest {
    Settings s = new Settings();
    DayState d = new DayState();

    @Test
    public void dayStartResetsAndSchedulesFirstBreakAndWater() {
        d.glasses = 5; d.breakCount = 7; d.pausedUntil = at("2026-11-03T07:00");
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        assertEquals(0, d.glasses); assertEquals(0, d.breakCount); assertEquals(0, d.pausedUntil);
        assertEquals(mon("07:20"), d.nextBreakAt);
        assertEquals(mon("07:10"), d.nextWaterAt); assertTrue(d.waterFirst);
        assertEquals(at("2026-11-03T07:00"), d.nextDayStartAt);
    }

    @Test
    public void everyThirdBreakIsLong() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        boolean[] types = new boolean[6];
        for (int i = 0; i < 6; i++) {
            assertTrue(Scheduler.onBreak(s, d, d.nextBreakAt, Z));
            types[i] = d.breakLong;
            Scheduler.onBreakEnd(s, d, d.breakEndAt, Z);
        }
        assertArrayEquals(new boolean[]{false, false, true, false, false, true}, types);
    }

    @Test
    public void breakLengthAndNextBreakAfterEnd() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        Scheduler.onBreak(s, d, mon("07:20"), Z);
        assertEquals(mon("07:20") + 20_000, d.breakEndAt); assertEquals(0, d.nextBreakAt);
        Scheduler.onBreakEnd(s, d, d.breakEndAt, Z);
        assertEquals(0, d.breakEndAt); assertEquals(mon("07:40") + 20_000, d.nextBreakAt);
        d.breakCount = 2;
        Scheduler.onBreak(s, d, mon("08:00"), Z);
        assertTrue(d.breakLong); assertEquals(mon("08:05"), d.breakEndAt);
    }

    @Test
    public void snoozeKeepsTypeAndCount() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        d.breakCount = 2;
        Scheduler.onBreak(s, d, mon("08:00"), Z); // 3rd = long
        Scheduler.snooze(s, d, mon("08:01"), Z);
        assertEquals(0, d.breakEndAt); assertEquals(mon("08:06"), d.nextBreakAt);
        assertTrue(Scheduler.nextBreakLong(s, d));
        Scheduler.onBreak(s, d, mon("08:06"), Z);
        assertEquals(3, d.breakCount); assertTrue(d.breakLong);
    }

    @Test
    public void skipMovesToNextBreak() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        Scheduler.onBreak(s, d, mon("07:20"), Z);
        Scheduler.skip(s, d, mon("07:20:05"), Z);
        assertEquals(0, d.breakEndAt); assertEquals(mon("07:40:05"), d.nextBreakAt);
    }

    @Test
    public void noBreakAtOrAfterSleep() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        Scheduler.onBreak(s, d, mon("22:40"), Z);
        Scheduler.onBreakEnd(s, d, mon("22:40:20"), Z);
        assertEquals(0, d.nextBreakAt);            // 23:00:20 is after sleep
        Scheduler.snooze(s, d, mon("22:56"), Z);
        assertEquals(0, d.nextBreakAt); assertFalse(d.snoozed);
    }

    @Test
    public void lateAlarmAfterSleepShowsNothing() {       // Review Focus 1
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        d.nextBreakAt = mon("22:59");
        assertFalse(Scheduler.onBreak(s, d, mon("23:04"), Z));
        assertEquals(0, d.breakEndAt); assertEquals(0, d.nextBreakAt);
    }

    @Test
    public void lateOvernightBreakStartsTheNewDayInstead() {     // e.g. Vivo held the 22:40 alarm until morning
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        for (int i = 0; i < 8; i++) Scheduler.logGlass(s, d, mon("08:00"), Z);   // Monday's goal met
        assertFalse(Scheduler.onBreak(s, d, at("2026-11-03T07:30"), Z));
        assertEquals(0, d.glasses);
        assertEquals(at("2026-11-03T07:50"), d.nextBreakAt);
        assertTrue(d.nextWaterAt > 0);                                         // water reminders resume
    }

    @Test
    public void breakEndingInTheNextWindowStartsTheNewDay() {
        s.weekdaySleep = "07:00";                                  // sleep == wake: back-to-back 24-hour windows
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        Scheduler.logGlass(s, d, mon("08:00"), Z);
        d.breakCount = 2;
        Scheduler.onBreak(s, d, at("2026-11-03T06:59:40"), Z);     // long break runs past Tuesday's wake
        Scheduler.onBreakEnd(s, d, d.breakEndAt, Z);
        assertEquals(0, d.glasses);
    }

    @Test
    public void breaksOffSchedulesNone() {
        s.breaksOn = false;
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        assertEquals(0, d.nextBreakAt);
    }
}
