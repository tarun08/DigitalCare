package com.digitalcare.app;

import static org.junit.Assert.*;

import android.media.AudioManager;
import org.junit.Test;

public class OverlayTest {
    @Test
    public void saysWhyAFullScreenReminderCannotShow() {
        assertEquals("permission", Overlay.blocked(false, AudioManager.MODE_NORMAL));     // "Display over other apps" is off
        assertNull(Overlay.blocked(true, AudioManager.MODE_NORMAL));                     // free to show
        assertEquals("call", Overlay.blocked(true, AudioManager.MODE_RINGTONE));          // it would cover the incoming call
        assertEquals("call", Overlay.blocked(true, AudioManager.MODE_IN_CALL));
        assertEquals("call", Overlay.blocked(true, AudioManager.MODE_IN_COMMUNICATION));  // WhatsApp and other app calls
    }

    @Test
    public void theCountdownShowsWholeSecondsRoundedUp() {
        assertEquals("0:20", Overlay.clock(20_000));
        assertEquals("0:20", Overlay.clock(19_001));      // a second is not over until it is over
        assertEquals("5:00", Overlay.clock(300_000));
        assertEquals("1:05", Overlay.clock(65_000));
        assertEquals("0:01", Overlay.clock(1));
        assertEquals("0:00", Overlay.clock(0));
        assertEquals("0:00", Overlay.clock(-5_000));      // a tick that comes a little late
    }
}
