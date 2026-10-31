package com.digitalcare.app;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class AlertTest {
    @Test
    public void styleMapsSoundAndVibration() {
        AlertStyle both = new AlertStyle(0, true), sound = new AlertStyle(1, true);
        AlertStyle vibration = new AlertStyle(2, true), silent = new AlertStyle(3, true);
        assertTrue(both.sound()); assertTrue(both.vibrate());
        assertTrue(sound.sound()); assertFalse(sound.vibrate());
        assertFalse(vibration.sound()); assertTrue(vibration.vibrate());
        assertFalse(silent.sound()); assertFalse(silent.vibrate());
    }

    @Test
    public void popUpsAreHighAndQuietAlertsSitLowerButStillSoundOrVibrate() {
        assertEquals(AlertStyle.Level.HIGH, new AlertStyle(0, true).level());
        assertEquals(AlertStyle.Level.HIGH, new AlertStyle(3, true).level());     // a silent pop-up still pops up
        assertEquals(AlertStyle.Level.DEFAULT, new AlertStyle(0, false).level());
        assertEquals(AlertStyle.Level.DEFAULT, new AlertStyle(2, false).level()); // vibration needs more than LOW
        assertEquals(AlertStyle.Level.LOW, new AlertStyle(3, false).level());     // quiet and silent
    }

    @Test
    public void everyChoiceHasItsOwnChannelCode() {                                // Android never lets an app edit a channel
        Set<String> codes = new HashSet<>();
        for (int m = 0; m < 4; m++) for (boolean p : new boolean[]{true, false}) codes.add(new AlertStyle(m, p).code());
        assertEquals(8, codes.size());
        assertEquals(new AlertStyle(3, true).code(), new AlertStyle(9, true).code()); // out of range is clamped
    }

    @Test
    public void defaultsKeepTodaysBehaviour() {
        Settings s = new Settings();
        assertEquals("short_0p", Alert.SHORT.channelId(Alert.SHORT.style(s)));
        assertEquals("long_0p", Alert.LONG.channelId(Alert.LONG.style(s)));
        assertEquals("over_2q", Alert.OVER.channelId(Alert.OVER.style(s)));      // break over: vibration only, no pop-up
        assertEquals("water_0p", Alert.WATER.channelId(Alert.WATER.style(s)));
        assertEquals("phone_0p", Alert.PHONE.channelId(Alert.PHONE.style(s)));
    }

    @Test
    public void aChangedChoiceMakesTheOldChannelObsolete() {
        Settings s = new Settings();
        s.waterSound = 3; s.waterPopup = false;                                    // water is now quiet and silent
        List<String> existing = Arrays.asList("short_0p", "long_0p", "over_2q", "water_0p", "water_3q", "phone_0p",
                "screen_service", "short_break", "water", "some_other_channel");
        assertEquals(Arrays.asList("water_0p", "short_break", "water"), Alert.obsolete(existing, s));
    }

    @Test
    public void alertsAreFoundByTheIdTheScreenSends() {
        assertEquals(Alert.PHONE, Alert.of("phone"));
        assertNull(Alert.of("nope"));
    }
}
