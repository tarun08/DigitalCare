package com.digitalcare.app;

import static com.digitalcare.app.Times.*;
import static org.junit.Assert.*;

import java.time.LocalDate;
import org.junit.Test;

public class DayWindowTest {
    Settings s = new Settings();

    @Test
    public void weekdayAndWeekendWindows() {
        Scheduler.Window mon = Scheduler.windowAt(s, mon("12:00"), Z);
        assertEquals(mon("07:00"), mon.start); assertEquals(mon("23:00"), mon.end);
        Scheduler.Window sat = Scheduler.windowAt(s, at("2026-11-07T12:00"), Z);
        assertEquals(at("2026-11-07T08:30"), sat.start); assertEquals(at("2026-11-07T23:30"), sat.end);
        assertNull(Scheduler.windowAt(s, mon("06:59"), Z));
        assertNull(Scheduler.windowAt(s, mon("23:00"), Z)); // end is exclusive
    }

    @Test
    public void eachDayHasItsOwnHours() {
        day(s, 5, "10:00", "02:00");                                   // Friday: late start, sleeps after midnight
        Scheduler.Window fri = Scheduler.windowAt(s, at("2026-11-06T12:00"), Z);
        assertEquals(at("2026-11-06T10:00"), fri.start); assertEquals(at("2026-11-07T02:00"), fri.end);
        assertEquals(at("2026-11-05T07:00"), Scheduler.windowAt(s, at("2026-11-05T12:00"), Z).start);   // Thursday unchanged
        assertEquals(at("2026-11-07T08:30"), Scheduler.windowAt(s, at("2026-11-07T12:00"), Z).start);   // Saturday unchanged
    }

    @Test
    public void aDayOffHasNoWindowAndNextWakeSkipsIt() {
        s.dayOn[1] = 0;                                                // Tuesday off
        assertNull(Scheduler.windowAt(s, at("2026-11-03T12:00"), Z));
        assertEquals(at("2026-11-04T07:00"), Scheduler.nextWake(s, mon("23:30"), Z));   // Monday night -> Wednesday
    }

    @Test
    public void aLateSleepIntoAnOffDayStillCounts() {
        day(s, 1, "07:00", "01:00");                                   // Monday sleeps at 01:00 on Tuesday
        s.dayOn[1] = 0;
        assertNotNull(Scheduler.windowAt(s, at("2026-11-03T00:30"), Z));
        assertNull(Scheduler.windowAt(s, at("2026-11-03T01:00"), Z));
    }

    @Test
    public void everyDayOffMeansNothingToWakeFor() {
        s.dayOn = new int[7];
        assertNull(Scheduler.windowAt(s, mon("12:00"), Z));
        assertEquals(0, Scheduler.nextWake(s, mon("12:00"), Z));
    }

    @Test
    public void sleepAfterMidnight() {
        weekdays(s, "07:00", "00:30");
        Scheduler.Window w = Scheduler.windowAt(s, at("2026-11-03T00:10"), Z);
        assertEquals(LocalDate.parse("2026-11-02"), w.date);
        assertEquals(at("2026-11-03T00:30"), w.end);
        assertNull(Scheduler.windowAt(s, at("2026-11-03T00:30"), Z));
    }

    @Test
    public void nextWakeIsStrictlyAfterNow() {
        assertEquals(mon("07:00"), Scheduler.nextWake(s, mon("06:00"), Z));
        assertEquals(at("2026-11-03T07:00"), Scheduler.nextWake(s, mon("07:00"), Z));
        assertEquals(at("2026-11-07T08:30"), Scheduler.nextWake(s, at("2026-11-06T23:30"), Z)); // Fri night -> Sat
    }

    @Test
    public void seasonGoalPerMonth() {
        int[] expected = {8, 8, 10, 12, 12, 12, 10, 10, 10, 10, 8, 8};
        for (int m = 1; m <= 12; m++) assertEquals("month " + m, expected[m - 1], Season.of(m).goal);
        assertEquals("summer", Season.of(5).id);
        assertEquals(8, Scheduler.goal(s, mon("12:00"), Z));
        s.smartWater = false; s.customGoal = 15;
        assertEquals(15, Scheduler.goal(s, mon("12:00"), Z));
    }
}
