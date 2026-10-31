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
        assertEquals(0, Scheduler.waterNext(s, d, mon("21:10"), Z));                            // 21:40 > cutoff
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
}
