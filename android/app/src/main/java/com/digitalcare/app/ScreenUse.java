package com.digitalcare.app;

/**
 * One continuous stretch of phone use, kept in memory by ScreenService. The stretch starts on unlock,
 * and reminders fall at every multiple of the limit from its start while the screen stays on.
 */
final class ScreenUse {
    // shortcut: a fixed 1-minute reset, make it a setting if people ask for it
    static final long RESET_MS = 60_000;

    private volatile long streakStart; // 0 = no stretch yet; read by the UI thread through inUseSince()
    private volatile long offSince;    // 0 = screen in use

    void unlock(long now) {
        if (streakStart == 0 || (offSince != 0 && now - offSince >= RESET_MS)) streakStart = now;
        offSince = 0;
    }

    void screenOff(long now) {
        if (offSince == 0) offSince = now;
    }

    /** When the stretch in use began, or 0 while the screen is off or no stretch has started. */
    long inUseSince() {
        return offSince == 0 ? streakStart : 0;
    }

    /** How long the stretch has lasted, to the nearest minute; a reminder fires a moment after its mark. */
    int minutes(long now) {
        return streakStart == 0 ? 0 : (int) Math.round((now - streakStart) / 60_000.0);
    }

    /** The next reminder while in use, or 0 when the screen is off or no stretch has started. */
    long nextReminder(long limitMs, long now) {
        if (streakStart == 0 || offSince != 0) return 0;
        return streakStart + ((now - streakStart) / limitMs + 1) * limitMs;
    }
}
