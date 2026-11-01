package com.digitalcare.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Notification channels and the notifications. Android never lets an app edit a channel, so each sound and
 * pop-up choice has a channel of its own, created when first used and removed when nothing uses it.
 */
final class Notifier {
    static final int ID_BREAK = 1, ID_BREAK_OVER = 2, ID_WATER = 3, ID_SCREEN = 4, ID_SCREEN_SERVICE = 5, ID_TEST = 6;
    private static final int FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    private Notifier() {}

    /** Makes each alert's channel match its current choice and removes the channels nothing uses any more. */
    static void ensureChannels(Context c, Settings s) {
        ensureServiceChannel(c);
        for (Alert a : Alert.values()) channel(c, a, a.style(s));
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        List<String> ids = new ArrayList<>();
        for (NotificationChannel ch : nm.getNotificationChannels()) ids.add(ch.getId());
        for (String id : Alert.obsolete(ids, s)) nm.deleteNotificationChannel(id);
    }

    static void ensureServiceChannel(Context c) {
        c.getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel("screen_service", "Phone-use reminder (always on)", NotificationManager.IMPORTANCE_LOW));
    }

    static void postBreak(Context c, Settings s, boolean isLong, long endAt, boolean wakeScreen) {
        Context t = in(c, s.language);
        Alert a = isLong ? Alert.LONG : Alert.SHORT;
        Notification.Builder b = builder(c, channel(c, a, a.style(s)), ID_BREAK, isLong ? "long" : "short", wakeScreen || a.full(s))
                .setContentTitle(t.getString(isLong ? R.string.n_long_break : R.string.n_eye_break))
                .setWhen(endAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(action(c, EventReceiver.SKIP, 30, t.getString(R.string.n_skip)))
                .addAction(action(c, EventReceiver.SNOOZE, 31, t.getString(R.string.n_snooze)));
        post(c, ID_BREAK, b);
        if (a.full(s)) launch(c, isLong ? "long" : "short");
    }

    static void postBreakOver(Context c, Settings s) {
        post(c, ID_BREAK_OVER, builder(c, channel(c, Alert.OVER, Alert.OVER.style(s)), ID_BREAK_OVER, null, false)
                .setContentTitle(in(c, s.language).getString(R.string.n_break_over))
                .setTimeoutAfter(10_000));
    }

    static void postWater(Context c, Settings s, boolean first, int glasses, int goal, Season season, boolean wakeScreen) {
        Context t = in(c, s.language);
        String text = first ? t.getString(R.string.n_water_first) : tip(c, s.language, season);
        post(c, ID_WATER, builder(c, channel(c, Alert.WATER, Alert.WATER.style(s)), ID_WATER, "water", wakeScreen || Alert.WATER.full(s))
                .setContentTitle(t.getString(R.string.n_water, glasses, goal))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .addAction(action(c, EventReceiver.GLASS, 32, t.getString(R.string.n_glass))));
        if (Alert.WATER.full(s)) launch(c, "water");
    }

    static void postScreenBreak(Context c, Settings s, int minutes) {
        Context t = in(c, s.language);
        post(c, ID_SCREEN, builder(c, channel(c, Alert.PHONE, Alert.PHONE.style(s)), ID_SCREEN, "phone", Alert.PHONE.full(s))
                .setContentTitle(t.getString(R.string.n_phone_title))
                .setContentText(t.getResources().getQuantityString(R.plurals.n_phone_text, minutes, minutes)));
        if (Alert.PHONE.full(s)) launch(c, "phone");
    }

    /** The season's water tip in the reminder language. */
    static String tip(Context c, String language, Season season) {
        switch (season) {
            case SUMMER: return in(c, language).getString(R.string.tip_summer);
            case MONSOON: return in(c, language).getString(R.string.tip_monsoon);
            case TRANSITION: return in(c, language).getString(R.string.tip_transition);
            default: return in(c, language).getString(R.string.tip_winter);
        }
    }

    /** Reminder texts come from this context: the chosen language, whatever language the phone itself is set to. */
    private static Context in(Context c, String language) {
        Configuration config = new Configuration(c.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(language));
        return c.createConfigurationContext(config);
    }

    /** A sample of an alert delivered this way, for the Settings screen's "Send a test alert". */
    static void postTest(Context c, Alert a, AlertStyle style) {
        post(c, ID_TEST, builder(c, channel(c, a, style), ID_TEST, null, false)
                .setContentTitle("Test: " + a.label)
                .setContentText("This is how it looks and sounds (" + style.describe() + ")."));
    }

    static Notification screenServiceNotice(Context c, String language) {
        Context t = in(c, language);
        return builder(c, "screen_service", ID_SCREEN_SERVICE, null, false)
                .setContentTitle(t.getString(R.string.n_service_title))
                .setContentText(t.getString(R.string.n_service_text))
                .build();
    }

    static void cancel(Context c, int id) {
        c.getSystemService(NotificationManager.class).cancel(id);
    }

    /** The channel for an alert delivered this way. Asking again is harmless, and it brings back a channel deleted earlier. */
    private static String channel(Context c, Alert a, AlertStyle style) {
        String id = a.channelId(style);
        NotificationChannel ch = new NotificationChannel(id, a.label + " (" + style.describe() + ")", importance(style.level()));
        if (!style.sound()) ch.setSound(null, null);
        ch.enableVibration(style.vibrate());
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        return id;
    }

    private static int importance(AlertStyle.Level level) {
        switch (level) {
            case HIGH: return NotificationManager.IMPORTANCE_HIGH;
            case DEFAULT: return NotificationManager.IMPORTANCE_DEFAULT;
            default: return NotificationManager.IMPORTANCE_LOW;
        }
    }

    /** screen == null: tapping just opens Home. wakeScreen adds a full-screen intent that shows over the lock screen. */
    private static Notification.Builder builder(Context c, String channel, int id, String screen, boolean wakeScreen) {
        Notification.Builder b = new Notification.Builder(c, channel)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(open(c, 10 + id, screen, false));
        if (wakeScreen) b.setFullScreenIntent(open(c, 20 + id, screen, true), true);
        return b;
    }

    private static PendingIntent open(Context c, int requestCode, String screen, boolean wake) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (screen != null) i.putExtra("screen", screen);
        if (wake) i.putExtra("wake", true);
        return PendingIntent.getActivity(c, requestCode, i, FLAGS);
    }

    private static Notification.Action action(Context c, String act, int requestCode, String title) {
        Intent i = new Intent(c, EventReceiver.class).setAction(act);
        PendingIntent pi = PendingIntent.getBroadcast(c, requestCode, i, FLAGS);
        return new Notification.Action.Builder(null, title, pi).build();
    }

    /**
     * Opens a reminder's screen by itself, over whatever is on screen. Android allows that from the background only
     * with "Display over other apps"; without it the banner already posted is all there is. Not during a call: the
     * screen would cover its buttons.
     */
    private static void launch(Context c, String screen) {
        if (!android.provider.Settings.canDrawOverlays(c)) return;
        int mode = c.getSystemService(AudioManager.class).getMode();
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) return;
        try {
            c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("screen", screen).putExtra("wake", true));
        } catch (RuntimeException refused) {
            // The phone said no: the banner is still there.
        }
    }

    private static void post(Context c, int id, Notification.Builder b) {
        c.getSystemService(NotificationManager.class).notify(id, b.build()); // dropped silently if permission is off
    }
}
