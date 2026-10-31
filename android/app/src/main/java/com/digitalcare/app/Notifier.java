package com.digitalcare.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.ArrayList;
import java.util.List;

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
        Alert a = isLong ? Alert.LONG : Alert.SHORT;
        Notification.Builder b = builder(c, channel(c, a, a.style(s)), ID_BREAK, isLong ? "long" : "short", wakeScreen)
                .setContentTitle(isLong ? "Long break · stand, walk, stretch" : "Eye break · look 6 m away")
                .setWhen(endAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(action(c, EventReceiver.SKIP, 30, "Skip"))
                .addAction(action(c, EventReceiver.SNOOZE, 31, "Snooze 5 min"));
        post(c, ID_BREAK, b);
    }

    static void postBreakOver(Context c, Settings s) {
        post(c, ID_BREAK_OVER, builder(c, channel(c, Alert.OVER, Alert.OVER.style(s)), ID_BREAK_OVER, null, false)
                .setContentTitle("Break over")
                .setTimeoutAfter(10_000));
    }

    static void postWater(Context c, Settings s, boolean first, int glasses, int goal, String tip, boolean wakeScreen) {
        post(c, ID_WATER, builder(c, channel(c, Alert.WATER, Alert.WATER.style(s)), ID_WATER, "water", wakeScreen)
                .setContentTitle("Drink water · " + glasses + "/" + goal + " glasses")
                .setContentText(first ? "Start your day with 1–2 glasses" : tip)
                .setStyle(new Notification.BigTextStyle().bigText(first ? "Start your day with 1–2 glasses" : tip))
                .addAction(action(c, EventReceiver.GLASS, 32, "Drank 1 glass")));
    }

    static void postScreenBreak(Context c, Settings s, int minutes) {
        post(c, ID_SCREEN, builder(c, channel(c, Alert.PHONE, Alert.PHONE.style(s)), ID_SCREEN, null, false)
                .setContentTitle("Time for a break")
                .setContentText("You've been using your phone for " + minutes + " minutes in a row. Look at something 6 m away."));
    }

    /** A sample of an alert delivered this way, for the Settings screen's "Send a test alert". */
    static void postTest(Context c, Alert a, AlertStyle style) {
        post(c, ID_TEST, builder(c, channel(c, a, style), ID_TEST, null, false)
                .setContentTitle("Test: " + a.label)
                .setContentText("This is how it looks and sounds (" + style.describe() + ")."));
    }

    static Notification screenServiceNotice(Context c) {
        return builder(c, "screen_service", ID_SCREEN_SERVICE, null, false)
                .setContentTitle("Phone-use reminder is on")
                .setContentText("Change it in DigitalCare Settings.")
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

    private static void post(Context c, int id, Notification.Builder b) {
        c.getSystemService(NotificationManager.class).notify(id, b.build()); // dropped silently if permission is off
    }
}
