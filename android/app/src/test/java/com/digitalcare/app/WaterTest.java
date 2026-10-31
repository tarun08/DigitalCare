package com.digitalcare.app;

import static com.digitalcare.app.Times.*;
import static org.junit.Assert.*;

import org.junit.Test;

/** Monday window 07:00–23:00, cutoff 21:30, winter goal 8. */
public class WaterTest {
    Settings s = new Settings();
    DayState d = new DayState();

    @Test
    public void smartSpacing() {
        d.glasses = 0; assertEquals(mon("14:30"), Scheduler.waterNext(s, d, mon("13:30"), Z)); // 480/8 = 60 min
        d.glasses = 7; assertEquals(mon("14:00"), Scheduler.waterNext(s, d, mon("12:00"), Z)); // ahead: 120-min cap
        d.glasses = 0; assertEquals(mon("19:30"), Scheduler.waterNext(s, d, mon("19:00"), Z)); // behind: 30-min floor
        d.glasses = 8; assertEquals(0, Scheduler.waterNext(s, d, mon("12:00"), Z));            // goal met
        d.glasses = 0;
        assertEquals(0, Scheduler.waterNext(s, d, mon("21:30"), Z));                            // at cutoff
        assertEquals(mon("21:20"), Scheduler.waterNext(s, d, mon("21:10"), Z));                            // 21:40 is past the cutoff: one last reminder 10 min before it
    }

    @Test
    public void customModeUsesFixedInterval() {
        s.smartWater = false; s.customGoal = 10; s.customEveryMin = 45; d.glasses = 3;
        assertEquals(mon("10:45"), Scheduler.waterNext(s, d, mon("10:00"), Z));
    }

    @Test
    public void firstReminderThenIgnoredThenGlass() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        assertTrue(Scheduler.onWater(s, d, mon("07:10"), Z));
        assertFalse(d.waterFirst);
        assertEquals(mon("07:10") + 860 * MIN / 8, d.nextWaterAt);   // ignored: back after one interval
        Scheduler.logGlass(s, d, mon("07:30"), Z);
        assertEquals(1, d.glasses);
        assertEquals(mon("09:30"), d.nextWaterAt);                   // 840 min / 7 = 120
    }

    @Test
    public void lateWaterAfterCutoffShowsNothing() {        // spec §5.3: no reminder once now >= cutoff
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        d.nextWaterAt = mon("21:20");
        assertFalse(Scheduler.onWater(s, d, mon("22:40"), Z));
        assertEquals(0, d.nextWaterAt);
    }

    @Test
    public void lateOvernightWaterStartsTheNewDayInstead() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        Scheduler.logGlass(s, d, mon("08:00"), Z);
        assertFalse(Scheduler.onWater(s, d, at("2026-11-03T07:30"), Z));   // held overnight, delivered Tuesday
        assertEquals(0, d.glasses);
        assertTrue(d.nextWaterAt > at("2026-11-03T07:30"));
    }

    @Test
    public void lateWaterAfterSleepShowsNothing() {        // Review Focus 1
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        assertFalse(Scheduler.onWater(s, d, mon("23:10"), Z));
        assertEquals(0, d.nextWaterAt);
    }

    @Test
    public void removeGlassTakesOneBackAndStopsAtZero() {
        Scheduler.logGlass(s, d, mon("08:00"), Z);
        Scheduler.logGlass(s, d, mon("09:00"), Z);
        Scheduler.removeGlass(s, d, mon("09:05"), Z);
        assertEquals(1, d.glasses);
        Scheduler.removeGlass(s, d, mon("09:06"), Z);
        Scheduler.removeGlass(s, d, mon("09:07"), Z);               // nothing left to take back
        assertEquals(0, d.glasses);
    }

    @Test
    public void removeGlassKeepsAPendingReminder() {               // fixing a mis-tap must not push the next reminder back
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        Scheduler.logGlass(s, d, mon("07:30"), Z);                 // 840 min / 7 = 120 -> 09:30
        Scheduler.removeGlass(s, d, mon("09:00"), Z);
        assertEquals(mon("09:30"), d.nextWaterAt);
    }

    @Test
    public void removeGlassRestartsRemindersStoppedAtTheGoal() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        for (int i = 0; i < 8; i++) Scheduler.logGlass(s, d, mon("12:00"), Z);   // goal met, reminders stop
        assertEquals(0, d.nextWaterAt);
        Scheduler.removeGlass(s, d, mon("12:05"), Z);
        assertEquals(7, d.glasses);
        assertEquals(mon("14:05"), d.nextWaterAt);                 // one glass left: the 120-min cap
    }

    @Test
    public void removeGlassWhilePausedSchedulesNothing() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        for (int i = 0; i < 8; i++) Scheduler.logGlass(s, d, mon("12:00"), Z);
        Scheduler.pause(s, d, mon("12:01"), Z, 60);
        Scheduler.removeGlass(s, d, mon("12:05"), Z);
        assertEquals(7, d.glasses);
        assertEquals(0, d.nextWaterAt);                            // the resume at 13:01 brings reminders back
    }

    // water start and stop chosen in Settings (waterCustom on)

    @Test
    public void customStartHoldsTheFirstReminderUntilThen() {
        s.waterCustom = true; s.waterStart = "09:00";
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        assertEquals(mon("09:00"), d.nextWaterAt);
    }

    @Test
    public void customStartBeforeWakeMeansFromWake() {
        s.waterCustom = true; s.waterStart = "06:00";
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        assertEquals(mon("07:00"), d.nextWaterAt);
    }

    @Test
    public void customStartAlsoHoldsBackTheNextReminder() {      // a glass at 07:10 alone would give 09:08
        s.waterCustom = true; s.waterStart = "11:00";
        Scheduler.logGlass(s, d, mon("07:10"), Z);
        assertEquals(mon("11:00"), d.nextWaterAt);
    }

    @Test
    public void customStopReplacesTheSleepMargin() {             // the margin would put the cutoff at 21:30
        s.waterCustom = true; s.waterStop = "20:00";
        assertEquals(mon("19:30"), Scheduler.waterNext(s, d, mon("19:00"), Z));
        assertEquals(mon("19:50"), Scheduler.waterNext(s, d, mon("19:40"), Z));         // 20:10 is after the stop: the last reminder comes 10 min before it
    }

    @Test
    public void customStopCanBeLaterThanTheMarginButNotAfterSleep() {
        s.waterCustom = true;
        s.waterStop = "22:00";
        assertEquals(mon("21:40"), Scheduler.waterNext(s, d, mon("21:10"), Z));
        s.waterStop = "23:45";                                                // sleep is 23:00
        assertEquals(mon("22:50"), Scheduler.waterNext(s, d, mon("22:40"), Z));         // 23:10 is after sleep: the last reminder comes at 22:50
    }

    @Test
    public void customStopAfterMidnightIsOnTheNextDay() {
        s.waterCustom = true; weekdays(s, "07:00", "01:00"); s.waterStop = "00:30";
        assertEquals(at("2026-11-03T00:20"), Scheduler.waterNext(s, d, mon("23:50"), Z));
    }

    @Test
    public void customTimesAreIgnoredWhileOff() {
        s.waterStart = "11:00"; s.waterStop = "12:00";
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        assertEquals(mon("07:10"), d.nextWaterAt);
        assertEquals(mon("21:20"), Scheduler.waterNext(s, d, mon("21:10"), Z));         // the 21:30 margin still applies: last reminder at 21:20
    }

    @Test
    public void waterHoursShownOnHome() {                        // first reminder and cutoff, automatic then chosen
        Scheduler.Window w = Scheduler.windowAt(s, mon("10:00"), Z);
        assertEquals(mon("07:10"), Scheduler.waterStartAt(s, w));
        assertEquals(mon("21:30"), Scheduler.cutoff(s, w));
        s.waterCustom = true; s.waterStart = "09:00"; s.waterStop = "20:00";
        assertEquals(mon("09:00"), Scheduler.waterStartAt(s, w));
        assertEquals(mon("20:00"), Scheduler.cutoff(s, w));
    }

    @Test
    public void nothingIsScheduledOrShownPastTheStopTime() {     // Home must never say "next water" after the stop time
        for (boolean custom : new boolean[]{false, true}) {
            Settings cs = new Settings();
            cs.waterCustom = custom; cs.waterStart = "09:00"; cs.waterStop = "20:00";
            DayState ds = new DayState();
            Scheduler.onDayStart(cs, ds, mon("07:00"), Z);
            Scheduler.Window w = Scheduler.windowAt(cs, mon("12:00"), Z);
            for (long t = mon("07:00"); t < mon("23:00"); t += MIN) {
                if (ds.nextWaterAt != 0 && ds.nextWaterAt <= t && Scheduler.onWater(cs, ds, t, Z)) {
                    assertTrue("shown past the stop at " + t, t < Scheduler.cutoff(cs, w));
                    if ((t / MIN) % 3 == 0) Scheduler.logGlass(cs, ds, t, Z);     // some reminders get answered
                }
                assertTrue("scheduled outside the water hours at " + t,
                        ds.nextWaterAt == 0 || (ds.nextWaterAt >= w.start && ds.nextWaterAt <= Scheduler.cutoff(cs, w)));
            }
        }
    }

    @Test
    public void oneLastReminderTenMinutesBeforeTheStopWhenTheGapDoesNotFit() {
        d.glasses = 0;
        assertEquals(mon("21:20"), Scheduler.waterNext(s, d, mon("21:10"), Z));   // 21:40 would be past the 21:30 stop
        assertEquals(0, Scheduler.waterNext(s, d, mon("21:20"), Z));              // that last reminder is not repeated
        assertEquals(0, Scheduler.waterNext(s, d, mon("21:25"), Z));              // too close to the stop
    }

    @Test
    public void takingAGlassBackLateOffersTheLastReminder() {
        Scheduler.onDayStart(s, d, mon("07:00"), Z);
        for (int i = 0; i < 8; i++) Scheduler.logGlass(s, d, mon("21:16"), Z);    // goal met, reminders stop
        assertEquals(0, d.nextWaterAt);
        Scheduler.removeGlass(s, d, mon("21:16"), Z);
        assertEquals(mon("21:20"), d.nextWaterAt);
    }

    @Test
    public void lastReminderNeverComesBeforeTheChosenStart() {
        s.waterCustom = true; s.waterStart = "21:25"; s.waterStop = "21:30";
        assertEquals(0, Scheduler.waterNext(s, d, mon("21:00"), Z));              // 21:20 is before the start
        s.waterStart = "21:00";
        assertEquals(mon("21:20"), Scheduler.waterNext(s, d, mon("21:05"), Z));
    }
}
