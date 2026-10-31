package com.eyecare.app;

import static com.eyecare.app.Times.*;
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
    public void customWeekendDays() {
        s.weekendDays = new int[]{5, 6}; // Fri, Sat: Sunday becomes a weekday
        assertEquals(at("2026-11-08T07:00"), Scheduler.windowAt(s, at("2026-11-08T12:00"), Z).start);
    }

    @Test
    public void sleepAfterMidnight() {
        s.weekdaySleep = "00:30";
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
