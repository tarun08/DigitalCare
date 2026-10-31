package com.digitalcare.app;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

final class Times {
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final long MIN = 60_000;

    static long at(String isoLocal) {
        return LocalDateTime.parse(isoLocal).atZone(Z).toInstant().toEpochMilli();
    }

    static long mon(String hhmm) { return at("2026-11-02T" + hhmm); } // a Monday in winter

    /** Hours for one day: 1 = Monday ... 7 = Sunday. */
    static void day(Settings s, int dow, String wake, String sleep) {
        s.dayWake[dow - 1] = minutes(wake);
        s.daySleep[dow - 1] = minutes(sleep);
    }

    /** The same hours for Monday to Friday. */
    static void weekdays(Settings s, String wake, String sleep) {
        for (int dow = 1; dow <= 5; dow++) day(s, dow, wake, sleep);
    }

    private static int minutes(String hhmm) { return LocalTime.parse(hhmm).toSecondOfDay() / 60; }
}
