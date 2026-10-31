package com.digitalcare.app;

import java.time.LocalTime;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/** User settings. Field names are the JSON keys shared with the TypeScript side. */
public final class Settings {
    // Hours for each day, Monday first, as minutes after midnight. dayOn: 1 = reminders that day, 0 = none.
    public int[] dayWake = {420, 420, 420, 420, 420, 510, 510};
    public int[] daySleep = {1380, 1380, 1380, 1380, 1380, 1410, 1410};
    public int[] dayOn = {1, 1, 1, 1, 1, 1, 1};
    public boolean breaksOn = true;
    public int shortEveryMin = 20, shortSec = 20, longEveryN = 3, longMin = 5;
    public boolean waterOn = true, smartWater = true;
    public int customGoal = 8, customEveryMin = 60, glassMl = 250, stopBeforeSleepMin = 90;
    public boolean waterCustom = false; // on: water runs from waterStart to waterStop; off: 10 min after wake until stopBeforeSleepMin before sleep
    public String waterStart = "09:00", waterStop = "21:00";
    // How each alert is delivered. Sound: 0 sound + vibration, 1 sound only, 2 vibration only, 3 silent. Popup false = quiet.
    public int shortSound = 0, longSound = 0, overSound = 2, waterSound = 0, phoneSound = 0;
    public boolean shortPopup = true, longPopup = true, overPopup = false, waterPopup = true, phonePopup = true;
    public boolean wakeScreen = false;
    public boolean screenOn = true;
    public int screenLimitMin = 15;

    /** Clamps every field to the ranges in spec §6, replacing bad times with their defaults. */
    public Settings clamp() {
        Settings d = new Settings();
        dayWake = days(dayWake, d.dayWake, 1439);
        daySleep = days(daySleep, d.daySleep, 1439);
        dayOn = days(dayOn, d.dayOn, 1);
        if (Arrays.stream(dayOn).sum() == 0) dayOn = d.dayOn; // at least one day stays on
        waterStart = time(waterStart, d.waterStart);
        waterStop = time(waterStop, d.waterStop);
        shortSound = clamp(shortSound, 0, 3);
        longSound = clamp(longSound, 0, 3);
        overSound = clamp(overSound, 0, 3);
        waterSound = clamp(waterSound, 0, 3);
        phoneSound = clamp(phoneSound, 0, 3);
        shortEveryMin = clamp(shortEveryMin, 1, 60);
        shortSec = clamp(shortSec, 10, 60);
        longEveryN = clamp(longEveryN, 2, 99);
        longMin = clamp(longMin, 1, 15);
        customGoal = clamp(customGoal, 1, 20);
        customEveryMin = clamp(customEveryMin, 1, 180);
        glassMl = clamp(glassMl, 100, 500);
        stopBeforeSleepMin = clamp(stopBeforeSleepMin, 0, 180);
        screenLimitMin = clamp(screenLimitMin, 1, 60);
        return this;
    }

    /**
     * Settings saved before each day had its own hours hold a weekday pair, a weekend pair and the weekend days;
     * they become the seven days. Settings already saved with the seven days, or nothing saved yet, are left alone.
     */
    static void migrate(JSONObject saved, Settings s) {
        if (saved.has("dayWake")) return;
        int weekdayWake = minutes(saved.optString("weekdayWake"), 420), weekdaySleep = minutes(saved.optString("weekdaySleep"), 1380);
        int weekendWake = minutes(saved.optString("weekendWake"), 510), weekendSleep = minutes(saved.optString("weekendSleep"), 1410);
        JSONArray weekend = saved.optJSONArray("weekendDays");
        for (int i = 0; i < 7; i++) {
            boolean isWeekend = weekend == null ? i >= 5 : contains(weekend, i + 1); // ISO day of week: 1 = Monday
            s.dayWake[i] = isWeekend ? weekendWake : weekdayWake;
            s.daySleep[i] = isWeekend ? weekendSleep : weekdaySleep;
        }
    }

    private static boolean contains(JSONArray a, int v) {
        for (int i = 0; i < a.length(); i++) if (a.optInt(i) == v) return true;
        return false;
    }

    private static int minutes(String v, int fallback) {
        try {
            return LocalTime.parse(v).toSecondOfDay() / 60;
        } catch (RuntimeException e) { // missing or unparseable
            return fallback;
        }
    }

    /** Seven values between 0 and max; anything else falls back to the defaults. */
    private static int[] days(int[] v, int[] fallback, int max) {
        if (v == null || v.length != 7) return fallback.clone();
        int[] out = new int[7];
        for (int i = 0; i < 7; i++) out[i] = clamp(v[i], 0, max);
        return out;
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
