package com.digitalcare.app;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

public class SettingsTest {
    @Test
    public void clampKeepsRangesAndDefaults() {
        Settings s = new Settings();
        s.shortEveryMin = 0; s.shortSec = 500; s.longEveryMin = 1; s.longMin = 99;
        s.customGoal = 0; s.customEveryMin = 1000; s.glassMl = 5; s.stopBeforeSleepMin = -5;
        s.dayWake = new int[]{1, 2}; s.daySleep = new int[]{-5, 3000, 0, 0, 0, 0, 0}; s.dayOn = new int[7];
        s.clamp();
        assertEquals(1, s.shortEveryMin); assertEquals(60, s.shortSec);
        assertEquals(2, s.longEveryMin); assertEquals(15, s.longMin);          // two short intervals of 1 minute
        assertEquals(1, s.customGoal); assertEquals(180, s.customEveryMin);
        assertEquals(100, s.glassMl); assertEquals(0, s.stopBeforeSleepMin);
        assertArrayEquals(new Settings().dayWake, s.dayWake);                        // wrong length: back to the defaults
        assertEquals(0, s.daySleep[0]); assertEquals(1439, s.daySleep[1]);           // minutes stay inside the day
        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 1, 1}, s.dayOn);                  // at least one day stays on
    }

    @Test
    public void languageIsEnglishUnlessHindiIsChosen() {
        Settings s = new Settings();
        assertEquals("en", s.language);
        s.language = "hi"; s.clamp(); assertEquals("hi", s.language);
        s.language = "fr"; s.clamp(); assertEquals("en", s.language);
        s.language = null; s.clamp(); assertEquals("en", s.language);
    }

    @Test
    public void fullScreenIsOffByDefaultAndSurvivesSaving() throws Exception {
        Settings s = new Settings();
        assertFalse(s.shortFull); assertFalse(s.longFull); assertFalse(s.waterFull); assertFalse(s.phoneFull);
        s.phoneFull = true;
        Settings back = Json.read(Json.write(s), new Settings());
        assertTrue(back.phoneFull); assertFalse(back.waterFull);
    }

    @Test
    public void longBreakEveryTakesAnyMinutesFromTwoShortIntervalsToADay() {
        Settings s = new Settings();
        assertEquals(60, s.longEveryMin);
        s.longEveryMin = 45; s.clamp(); assertEquals(45, s.longEveryMin);       // not a multiple of the 20 minute short break
        s.longEveryMin = 30; s.clamp(); assertEquals(40, s.longEveryMin);       // there must be a short break in between
        s.longEveryMin = 9999; s.clamp(); assertEquals(1440, s.longEveryMin);
        s.shortEveryMin = 30; s.longEveryMin = 45; s.clamp(); assertEquals(60, s.longEveryMin);
    }

    @Test
    public void aSavedEveryNthBreakBecomesMinutes() throws Exception {
        JSONObject saved = new JSONObject("{\"shortEveryMin\":15,\"longEveryN\":5}");
        Settings s = Json.read(saved, new Settings());
        Settings.migrate(saved, s);
        assertEquals(75, s.longEveryMin);                                       // every 5th break of 15 minutes

        JSONObject already = new JSONObject("{\"shortEveryMin\":15,\"longEveryN\":4,\"longEveryMin\":50}");
        Settings t = Json.read(already, new Settings());
        Settings.migrate(already, t);
        assertEquals(50, t.longEveryMin);                                       // minutes saved: left alone

        JSONObject fresh = new JSONObject();
        Settings f = Json.read(fresh, new Settings());
        Settings.migrate(fresh, f);
        assertEquals(60, f.longEveryMin);
    }

    @Test
    public void screenLimitDefaultsAndClampsToOneToSixty() {
        Settings s = new Settings();
        assertTrue(s.screenOn); assertEquals(15, s.screenLimitMin);
        s.screenLimitMin = 1; s.clamp(); assertEquals(1, s.screenLimitMin);
        s.screenLimitMin = 0; s.clamp(); assertEquals(1, s.screenLimitMin);
        s.screenLimitMin = 500; s.clamp(); assertEquals(60, s.screenLimitMin);
    }

    @Test
    public void waterTimesDefaultOffAndFallBackWhenInvalid() {
        Settings s = new Settings();
        assertFalse(s.waterCustom); assertEquals("09:00", s.waterStart); assertEquals("21:00", s.waterStop);
        s.waterStart = "9am"; s.waterStop = null; s.clamp();
        assertEquals("09:00", s.waterStart); assertEquals("21:00", s.waterStop);
    }

    @Test
    public void alertStylesDefaultToTodaysAndClamp() {
        Settings s = new Settings();
        assertEquals(0, s.shortSound); assertTrue(s.shortPopup);
        assertEquals(2, s.overSound); assertFalse(s.overPopup);
        s.waterSound = 9; s.phoneSound = -3; s.clamp();
        assertEquals(3, s.waterSound); assertEquals(0, s.phoneSound);
    }

    @Test
    public void savedWeekdayAndWeekendTimesBecomeTheSevenDays() throws Exception {
        JSONObject saved = new JSONObject("{\"weekdayWake\":\"06:30\",\"weekdaySleep\":\"22:00\","
                + "\"weekendWake\":\"09:00\",\"weekendSleep\":\"00:30\",\"weekendDays\":[5,6]}");
        Settings s = Json.read(saved, new Settings());
        Settings.migrate(saved, s);
        assertArrayEquals(new int[]{390, 390, 390, 390, 540, 540, 390}, s.dayWake);       // Fri and Sat were the weekend
        assertArrayEquals(new int[]{1320, 1320, 1320, 1320, 30, 30, 1320}, s.daySleep);
        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 1, 1}, s.dayOn);
    }

    @Test
    public void settingsSavedWithDaysAndFreshInstallsAreLeftAlone() throws Exception {
        Settings custom = new Settings(); custom.dayWake[2] = 480; custom.dayOn[6] = 0;
        JSONObject saved = Json.write(custom);
        Settings s = Json.read(saved, new Settings());
        Settings.migrate(saved, s);
        assertEquals(480, s.dayWake[2]); assertEquals(0, s.dayOn[6]);

        JSONObject fresh = new JSONObject();                                              // nothing saved yet
        Settings f = Json.read(fresh, new Settings());
        Settings.migrate(fresh, f);
        assertArrayEquals(new Settings().dayWake, f.dayWake); assertArrayEquals(new Settings().daySleep, f.daySleep);
    }
}
