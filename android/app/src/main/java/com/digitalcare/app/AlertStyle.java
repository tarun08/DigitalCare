package com.digitalcare.app;

/** How one alert is delivered: sound and vibration (mode 0 to 3) and whether it pops up. No Android imports, so it can be tested. */
final class AlertStyle {
    enum Level { LOW, DEFAULT, HIGH }

    final int mode; // 0 sound + vibration, 1 sound only, 2 vibration only, 3 silent
    final boolean popup;

    AlertStyle(int mode, boolean popup) {
        this.mode = Math.max(0, Math.min(3, mode));
        this.popup = popup;
    }

    boolean sound() { return mode <= 1; }

    boolean vibrate() { return mode == 0 || mode == 2; }

    /** A pop-up needs high importance. A quiet alert that still sounds or vibrates needs default, and a silent one sits low. */
    Level level() { return popup ? Level.HIGH : sound() || vibrate() ? Level.DEFAULT : Level.LOW; }

    /** Part of the channel id: Android never lets an app edit a channel, so each choice gets its own. */
    String code() { return mode + (popup ? "p" : "q"); }

    String describe() {
        String how = mode == 0 ? "sound + vibration" : mode == 1 ? "sound only" : mode == 2 ? "vibration only" : "silent";
        return (popup ? "pop-up, " : "quiet, ") + how;
    }
}
