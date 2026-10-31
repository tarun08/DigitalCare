package com.digitalcare.app;

import java.time.LocalTime;
import java.util.Arrays;

/** User settings. Field names are the JSON keys shared with the TypeScript side. */
public final class Settings {
    public String weekdayWake = "07:00", weekdaySleep = "23:00";
    public String weekendWake = "08:30", weekendSleep = "23:30";
    public int[] weekendDays = {6, 7}; // ISO day of week: 1 = Mon ... 7 = Sun
    public boolean breaksOn = true;
    public int shortEveryMin = 20, shortSec = 20, longEveryN = 3, longMin = 5;
    public boolean waterOn = true, smartWater = true;
    public int customGoal = 8, customEveryMin = 60, glassMl = 250, stopBeforeSleepMin = 90;
    public boolean wakeScreen = false;

    /** Clamps every field to the ranges in spec §6, replacing bad times with their defaults. */
    public Settings clamp() {
        Settings d = new Settings();
        weekdayWake = time(weekdayWake, d.weekdayWake);
        weekdaySleep = time(weekdaySleep, d.weekdaySleep);
        weekendWake = time(weekendWake, d.weekendWake);
        weekendSleep = time(weekendSleep, d.weekendSleep);
        weekendDays = weekendDays == null ? d.weekendDays
                : Arrays.stream(weekendDays).filter(v -> v >= 1 && v <= 7).toArray();
        shortEveryMin = clamp(shortEveryMin, 1, 60);
        shortSec = clamp(shortSec, 10, 60);
        longEveryN = clamp(longEveryN, 2, 6);
        longMin = clamp(longMin, 1, 15);
        customGoal = clamp(customGoal, 1, 20);
        customEveryMin = clamp(customEveryMin, 1, 180);
        glassMl = clamp(glassMl, 100, 500);
        stopBeforeSleepMin = clamp(stopBeforeSleepMin, 0, 180);
        return this;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String time(String v, String fallback) {
        try {
            LocalTime.parse(v);
            return v;
        } catch (RuntimeException e) { // null or unparseable
            return fallback;
        }
    }
}
