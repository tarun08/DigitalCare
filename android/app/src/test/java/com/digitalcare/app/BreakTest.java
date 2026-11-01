package com.digitalcare.app;

import static com.digitalcare.app.Times.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class BreakTest {
    Settings s = new Settings();
    DayState d = new DayState();

    /** Takes the next `n` breaks to their end. Returns when each started; types[] gets whether it was a long one. */
    private long[] takeBreaks(int n, boolean[] types) {
        long[] starts = new long[n];
        for (int i = 0; i < n; i++) {
            starts[i] = d.nextBreakAt;
            assertTrue(Scheduler.onBreak(s, d, d.nextBreakAt, Z));
            types[i] = d.breakLong;
            Scheduler.onBreakEnd(s, d, d.breakEndAt, Z);
        }
        return starts;
    }

    @Test
    public void dayStartResetsAndSchedulesFirstBreakAndWater() {
        d.glasses = 5; d.lastLongAt = mon("03:00"); d.pausedUntil = at("2026-11-03T07:00");
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        assertEquals(0, d.glasses); assertEquals(0, d.pausedUntil);
        assertEquals(mon("07:00"), d.lastLongAt);                       // the long break timer starts at wake
        assertEquals(mon("07:20"), d.nextBreakAt);
        assertEquals(mon("07:10"), d.nextWaterAt); assertTrue(d.waterFirst);
        assertEquals(at("2026-11-03T07:00"), d.nextDayStartAt);
    }

    @Test
    public void theThirdBreakIsTheLongOneByDefault() {                  // 60 minutes, with a break every 20
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        boolean[] types = new boolean[6];
        long[] starts = takeBreaks(6, types);
        assertArrayEquals(new boolean[]{false, false, true, false, false, true}, types);
        assertArrayEquals(new long[]{mon("07:20"), mon("07:40:20"), mon("08:00"), mon("08:25"), mon("08:45:20"), mon("09:05")}, starts);
    }

    @Test
    public void longBreakComesExactlyOnTheMinutesYouSet() {             // 45 is not a multiple of the 20 minute break
        s.longEveryMin = 45; s.clamp();
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        boolean[] types = new boolean[4];
        long[] starts = takeBreaks(4, types);
        assertArrayEquals(new boolean[]{false, true, false, true}, types);
        assertArrayEquals(new long[]{mon("07:20"), mon("07:45"), mon("08:10"), mon("08:35")}, starts);   // 45 after each long one ends
    }

    @Test
    public void aLongBreakDueRightAfterABreakWaitsHalfAnIntervalNotAFullOne() {   // 50 minutes
        s.longEveryMin = 50; s.clamp();
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        d.lastLongAt = mon("07:00:30");                                 // so the long break falls due at 07:50:30
        takeBreaks(2, new boolean[2]);                                  // 07:20 and 07:40:20, which ended at 07:40:40
        assertEquals(mon("07:50:40"), d.nextBreakAt);                   // due 9:50 after that, but never closer than half an interval
        assertTrue(Scheduler.nextBreakLong(s, d));
    }

    @Test
    public void breakLengthAndNextBreakAfterEnd() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        Scheduler.onBreak(s, d, mon("07:20"), Z);
        assertEquals(mon("07:20") + 20_000, d.breakEndAt); assertEquals(0, d.nextBreakAt);
        Scheduler.onBreakEnd(s, d, d.breakEndAt, Z);
        assertEquals(0, d.breakEndAt); assertEquals(mon("07:40") + 20_000, d.nextBreakAt);
        Scheduler.onBreak(s, d, mon("08:00"), Z);                       // an hour after wake
        assertTrue(d.breakLong); assertEquals(mon("08:05"), d.breakEndAt);
    }

    @Test
    public void snoozeKeepsTheTypeAndDoesNotRestartTheLongTimer() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        Scheduler.onBreak(s, d, mon("08:00"), Z); // long
        Scheduler.snooze(s, d, mon("08:01"), Z);
        assertEquals(0, d.breakEndAt); assertEquals(mon("08:06"), d.nextBreakAt);
        assertTrue(Scheduler.nextBreakLong(s, d));
        Scheduler.onBreak(s, d, mon("08:06"), Z);
        assertTrue(d.breakLong); assertEquals(mon("07:00"), d.lastLongAt);   // only a long break that ends restarts it
    }

    @Test
    public void aSkippedLongBreakCountsButASkipOnAnOldNotificationDoesNot() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        Scheduler.onBreak(s, d, mon("08:00"), Z);                       // long
        Scheduler.skip(s, d, mon("08:01"), Z);
        assertEquals(mon("08:01"), d.lastLongAt);                       // skipping it still counts as the long break
        Scheduler.skip(s, d, mon("08:30"), Z);                          // nothing is running now
        assertEquals(mon("08:01"), d.lastLongAt);
    }

    @Test
    public void aLongBreakThatWasDueDuringAPauseIsTheNextRegularBreak() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        Scheduler.pause(s, d, mon("07:30"), Z, 60);
        Scheduler.resume(s, d, mon("08:30"), Z);                        // the long break was due at 08:00
        assertEquals(mon("08:50"), d.nextBreakAt);                      // as always: 20 minutes after resuming
        assertTrue(Scheduler.nextBreakLong(s, d));
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
        weekdays(s, "07:00", "07:00");                                // sleep == wake: back-to-back 24-hour windows
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        Scheduler.logGlass(s, d, mon("08:00"), Z);
        Scheduler.onBreak(s, d, at("2026-11-03T06:59:40"), Z);     // a long break (a day since the last), running past Tuesday's wake
        assertTrue(d.breakLong);
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
