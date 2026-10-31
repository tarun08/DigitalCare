package com.eyecare.app;

import static org.junit.Assert.*;

import org.junit.Test;

public class SettingsTest {
    @Test
    public void clampKeepsRangesAndDefaults() {
        Settings s = new Settings();
        s.shortEveryMin = 0; s.shortSec = 500; s.longEveryN = 1; s.longMin = 99;
        s.customGoal = 0; s.customEveryMin = 1000; s.glassMl = 5; s.stopBeforeSleepMin = -5;
        s.weekdayWake = "7am"; s.weekendSleep = null; s.weekendDays = new int[]{0, 6, 9};
        s.clamp();
        assertEquals(1, s.shortEveryMin); assertEquals(60, s.shortSec);
        assertEquals(2, s.longEveryN); assertEquals(15, s.longMin);
        assertEquals(1, s.customGoal); assertEquals(180, s.customEveryMin);
        assertEquals(100, s.glassMl); assertEquals(0, s.stopBeforeSleepMin);
        assertEquals("07:00", s.weekdayWake); assertEquals("23:30", s.weekendSleep);
        assertArrayEquals(new int[]{6}, s.weekendDays);
    }
}
