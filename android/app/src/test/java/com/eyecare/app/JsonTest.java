package com.eyecare.app;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

public class JsonTest {
    @Test
    public void roundTripsAndKeepsDefaultsForMissingOrNull() throws Exception {
        Settings s = new Settings(); s.weekendDays = new int[]{5}; s.smartWater = false; s.weekdayWake = "06:15";
        Settings back = Json.read(Json.write(s), new Settings());
        assertArrayEquals(new int[]{5}, back.weekendDays); assertFalse(back.smartWater); assertEquals("06:15", back.weekdayWake);

        JSONObject fromForm = new JSONObject().put("shortEveryMin", 25.0).put("longMin", JSONObject.NULL); // Review Focus 4
        Settings p = Json.read(fromForm, new Settings());
        assertEquals(25, p.shortEveryMin); assertEquals(5, p.longMin);

        DayState d = new DayState(); d.nextBreakAt = 1_800_000_000_000L; d.snoozed = true;
        DayState dBack = Json.read(Json.write(d), new DayState());
        assertEquals(1_800_000_000_000L, dBack.nextBreakAt); assertTrue(dBack.snoozed);
    }
}
