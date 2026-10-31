package com.digitalcare.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Notification channels and the three notifications. Channel ids are fixed so the user's choices stick. */
final class Notifier {
    static final int ID_BREAK = 1, ID_BREAK_OVER = 2, ID_WATER = 3;
    private static final int FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    private Notifier() {}

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel("short_break", "Short eye break", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(channel("long_break", "Long break", NotificationManager.IMPORTANCE_HIGH));
        NotificationChannel over = channel("break_over", "Break over", NotificationManager.IMPORTANCE_DEFAULT);
        over.setSound(null, null);
        nm.createNotificationChannel(over);
        nm.createNotificationChannel(channel("water", "Water", NotificationManager.IMPORTANCE_HIGH));
    }

    static void postBreak(Context c, boolean isLong, long endAt, boolean wakeScreen) {
        String screen = isLong ? "long" : "short";
        Notification.Builder b = builder(c, isLong ? "long_break" : "short_break", ID_BREAK, screen, wakeScreen)
                .setContentTitle(isLong ? "Long break · stand, walk, stretch" : "Eye break · look 6 m away")
                .setWhen(endAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(action(c, EventReceiver.SKIP, 30, "Skip"))
                .addAction(action(c, EventReceiver.SNOOZE, 31, "Snooze 5 min"));
        post(c, ID_BREAK, b);
    }

    static void postBreakOver(Context c) {
        post(c, ID_BREAK_OVER, builder(c, "break_over", ID_BREAK_OVER, null, false)
                .setContentTitle("Break over")
                .setTimeoutAfter(10_000));
    }

    static void postWater(Context c, boolean first, int glasses, int goal, String tip, boolean wakeScreen) {
        post(c, ID_WATER, builder(c, "water", ID_WATER, "water", wakeScreen)
                .setContentTitle("Drink water · " + glasses + "/" + goal + " glasses")
                .setContentText(first ? "Start your day with 1–2 glasses" : tip)
                .setStyle(new Notification.BigTextStyle().bigText(first ? "Start your day with 1–2 glasses" : tip))
                .addAction(action(c, EventReceiver.GLASS, 32, "Drank 1 glass")));
    }

    static void cancel(Context c, int id) {
        c.getSystemService(NotificationManager.class).cancel(id);
    }

    private static NotificationChannel channel(String id, String name, int importance) {
        NotificationChannel ch = new NotificationChannel(id, name, importance);
        ch.enableVibration(true);
        return ch;
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
        ensureChannels(c);
        c.getSystemService(NotificationManager.class).notify(id, b.build()); // dropped silently if permission is off
    }
}
