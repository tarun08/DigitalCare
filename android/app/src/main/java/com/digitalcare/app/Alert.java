package com.digitalcare.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** The alerts whose sound, vibration and pop-up the user can choose. No Android imports, so the channel logic can be tested. */
enum Alert {
    SHORT("short", "Short eye break"), LONG("long", "Long break"), OVER("over", "Break over"),
    WATER("water", "Water"), PHONE("phone", "Phone-use reminder");

    // The fixed channels from earlier versions; no alert uses them now.
    private static final Set<String> LEGACY = new HashSet<>(Arrays.asList("short_break", "long_break", "break_over", "water"));
    private static final Pattern OURS = Pattern.compile("(short|long|over|water|phone)_[0-3][pq]");

    final String id, label;

    Alert(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** A full screen alert still posts the banner (it is what shows when Android refuses to open the screen), so it pops up. */
    AlertStyle style(Settings s) {
        switch (this) {
            case SHORT: return new AlertStyle(s.shortSound, s.shortPopup || s.shortFull);
            case LONG: return new AlertStyle(s.longSound, s.longPopup || s.longFull);
            case OVER: return new AlertStyle(s.overSound, s.overPopup || s.overFull);
            case WATER: return new AlertStyle(s.waterSound, s.waterPopup || s.waterFull);
            default: return new AlertStyle(s.phoneSound, s.phonePopup || s.phoneFull);
        }
    }

    /** Whether the reminder is drawn over the open app. */
    boolean full(Settings s) {
        switch (this) {
            case SHORT: return s.shortFull;
            case LONG: return s.longFull;
            case OVER: return s.overFull;
            case WATER: return s.waterFull;
            default: return s.phoneFull;
        }
    }

    String channelId(AlertStyle style) { return id + "_" + style.code(); }

    /** The alert the screen means by this id, or null. */
    static Alert of(String id) {
        for (Alert a : values()) if (a.id.equals(id)) return a;
        return null;
    }

    /** Channels of ours that no alert uses now: an earlier choice, or the fixed ones from earlier versions. */
    static List<String> obsolete(Collection<String> existing, Settings s) {
        Set<String> current = new HashSet<>();
        for (Alert a : values()) current.add(a.channelId(a.style(s)));
        List<String> gone = new ArrayList<>();
        for (String id : existing) {
            boolean ours = LEGACY.contains(id) || OURS.matcher(id).matches();
            if (ours && !current.contains(id)) gone.add(id);
        }
        return gone;
    }
}
