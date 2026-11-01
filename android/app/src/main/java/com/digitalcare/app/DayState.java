package com.digitalcare.app;

/** Today's counters and pending event times. Times are epoch ms, 0 = none. */
public final class DayState {
    public long dayDate;          // epochDay of the wake date the counters belong to
    public int glasses;
    public long nextBreakAt, breakEndAt, nextWaterAt, pausedUntil, nextDayStartAt;
    public long lastLongAt;       // when the last long break ended, or the day began: the next one comes longEveryMin after it
    public boolean breakLong;     // type of the running or snoozed break
    public boolean snoozed;       // the next BREAK is a snooze re-fire
    public boolean waterFirst;    // the next WATER is the day's first
}
