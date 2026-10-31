package com.digitalcare.app;

import static com.digitalcare.app.Times.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class ScreenUseTest {
    private static final long LIMIT = 15 * MIN;
    ScreenUse use = new ScreenUse();

    @Test
    public void noStretchUntilTheUserUnlocks() {          // a lock-screen glance is not phone use
        assertEquals(0, use.nextReminder(LIMIT, mon("10:00")));
    }

    @Test
    public void remindsEveryLimitWhileInUse() {
        use.unlock(mon("10:00"));
        assertEquals(mon("10:15"), use.nextReminder(LIMIT, mon("10:00")));
        assertEquals(mon("10:30"), use.nextReminder(LIMIT, mon("10:15")));
        assertEquals(mon("10:45"), use.nextReminder(LIMIT, mon("10:30")));
    }

    @Test
    public void screenOffStopsTheReminders() {
        use.unlock(mon("10:00"));
        use.screenOff(mon("10:05"));
        assertEquals(0, use.nextReminder(LIMIT, mon("10:06")));
    }

    @Test
    public void briefScreenOffKeepsTheStretch() {        // under a minute: a glance away, not a break
        use.unlock(mon("10:00"));
        use.screenOff(mon("10:05"));
        use.unlock(mon("10:05") + 30_000);
        assertEquals(mon("10:15"), use.nextReminder(LIMIT, mon("10:05") + 30_000));
    }

    @Test
    public void oneMinuteOffStartsANewStretch() {
        use.unlock(mon("10:00"));
        use.screenOff(mon("10:05"));
        use.unlock(mon("10:06"));
        assertEquals(mon("10:21"), use.nextReminder(LIMIT, mon("10:06")));
    }

    @Test
    public void limitFromSettingsChangesTheInterval() {
        use.unlock(mon("10:00"));
        assertEquals(mon("10:05"), use.nextReminder(5 * MIN, mon("10:00")));
    }

    @Test
    public void remindsOnlyInsideWakeHours() {
        Settings s = new Settings();
        DayState d = new DayState();
        assertTrue(Scheduler.screenReminderAllowed(s, d, mon("10:00"), Z));
        assertFalse(Scheduler.screenReminderAllowed(s, d, mon("06:59"), Z));
        assertFalse(Scheduler.screenReminderAllowed(s, d, mon("23:30"), Z));
    }

    @Test
    public void pauseSilencesTheReminderUntilItEnds() {
        Settings s = new Settings();
        DayState d = new DayState();
        d.pausedUntil = mon("11:00");
        assertFalse(Scheduler.screenReminderAllowed(s, d, mon("10:30"), Z));
        assertTrue(Scheduler.screenReminderAllowed(s, d, mon("11:00"), Z));
    }

    @Test
    public void theSettingTurnsRemindersOff() {
        Settings s = new Settings();
        s.screenOn = false;
        assertFalse(Scheduler.screenReminderAllowed(s, new DayState(), mon("10:00"), Z));
    }

    @Test
    public void stoppedOnlyWhenItRanBeforeAndIsDownNow() {   // a first install is not a stop
        Settings s = new Settings();
        assertFalse(Scheduler.screenStopped(s, false, false));
        assertTrue(Scheduler.screenStopped(s, true, false));
        assertFalse(Scheduler.screenStopped(s, true, true));
        s.screenOn = false;
        assertFalse(Scheduler.screenStopped(s, true, false));
    }

    @Test
    public void minutesIsHowLongTheStretchHasLasted() {   // the reminder says 30 at the second mark, not the limit again
        assertEquals(0, use.minutes(mon("10:00")));
        use.unlock(mon("10:00"));
        assertEquals(15, use.minutes(mon("10:15") + 40));  // a reminder fires a moment after the mark
        assertEquals(30, use.minutes(mon("10:30")));
    }

    @Test
    public void inUseSinceIsTheStretchStartOnlyWhileTheScreenIsOn() {   // the Home line counts from here
        assertEquals(0, use.inUseSince());
        use.unlock(mon("10:00"));
        assertEquals(mon("10:00"), use.inUseSince());
        use.screenOff(mon("10:05"));
        assertEquals(0, use.inUseSince());
        use.unlock(mon("10:06"));                             // a minute off starts a new stretch
        assertEquals(mon("10:06"), use.inUseSince());
    }
}
