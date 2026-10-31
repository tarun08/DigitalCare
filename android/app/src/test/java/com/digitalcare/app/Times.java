package com.digitalcare.app;

import java.time.LocalDateTime;
import java.time.ZoneId;

final class Times {
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final long MIN = 60_000;

    static long at(String isoLocal) {
        return LocalDateTime.parse(isoLocal).atZone(Z).toInstant().toEpochMilli();
    }

    static long mon(String hhmm) { return at("2026-11-02T" + hhmm); } // a Monday in winter
}
