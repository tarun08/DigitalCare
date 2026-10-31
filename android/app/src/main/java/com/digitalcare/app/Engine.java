package com.digitalcare.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import java.time.ZoneId;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Owns settings and day state (SharedPreferences), arms alarms and posts notifications.
 * Every entry point is synchronized: the plugin thread and the receiver's main thread both call in.
 */
final class Engine {
    private Engine() {}

    /** Recomputes when nothing is armed (first launch) or an alarm was lost, and always re-arms. */
    static synchronized void ensureFresh(Context c) {
        Settings s = settings(c);
        DayState d = day(c);
        long now = System.currentTimeMillis();
        if (Scheduler.stale(d, now)) Scheduler.recompute(s, d, now, ZoneId.systemDefault());
        commit(c, s, d); // a force-stop or a blocked boot broadcast cancels alarms without making the state stale
    }

    static synchronized void recompute(Context c) { run(c, Scheduler::recompute); }

    static synchronized void saveSettings(Context c, Settings s) {
        s.clamp();
        DayState d = day(c);
        Scheduler.recompute(s, d, System.currentTimeMillis(), ZoneId.systemDefault());
        commit(c, s, d);
    }

    static synchronized void logGlass(Context c) {
        run(c, Scheduler::logGlass);
        Notifier.cancel(c, Notifier.ID_WATER);
    }

    static synchronized void pause(Context c, Integer minutes) {
        run(c, (s, d, now, z) -> Scheduler.pause(s, d, now, z, minutes));
    }

    static synchronized void resume(Context c) { run(c, Scheduler::resume); }

    static synchronized void skip(Context c) { run(c, Scheduler::skip); }

    static synchronized void snooze(Context c) { run(c, Scheduler::snooze); }

    static synchronized void onAlarm(Context c, Scheduler.Kind k) {
        Settings s = settings(c);
        DayState d = day(c);
        long now = System.currentTimeMillis();
        ZoneId z = ZoneId.systemDefault();
        if (!Scheduler.due(k, d, now)) { // already handled or rescheduled while this alarm was in flight
            commit(c, s, d);
            return;
        }
        switch (k) {
            case BREAK:
                if (Scheduler.onBreak(s, d, now, z)) Notifier.postBreak(c, d.breakLong, d.breakEndAt, s.wakeScreen);
                break;
            case BREAK_END:
                Scheduler.onBreakEnd(s, d, now, z);
                Notifier.cancel(c, Notifier.ID_BREAK);
                Notifier.postBreakOver(c);
                break;
            case WATER:
                boolean first = d.waterFirst;
                if (Scheduler.onWater(s, d, now, z)) {
                    Notifier.postWater(c, first, d.glasses, Scheduler.goal(s, now, z), Scheduler.season(now, z).tip, s.wakeScreen);
                }
                break;
            case DAY_START:
                Scheduler.onDayStart(s, d, now, z);
                break;
            case RESUME:
                Scheduler.resume(s, d, now, z);
                break;
        }
        commit(c, s, d);
    }

    static synchronized JSONObject state(Context c) {
        Settings s = settings(c);
        DayState d = day(c);
        long now = System.currentTimeMillis();
        ZoneId z = ZoneId.systemDefault();
        Season season = Scheduler.season(now, z);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        try {
            return new JSONObject()
                    .put("settings", Json.write(s))
                    .put("glasses", d.glasses)
                    .put("goal", Scheduler.goal(s, now, z))
                    .put("season", season.id)
                    .put("tip", season.tip)
                    .put("inDay", Scheduler.windowAt(s, now, z) != null)
                    .put("nextDayStartAt", time(d.nextDayStartAt))
                    .put("nextBreakAt", time(d.nextBreakAt))
                    .put("nextBreakType", d.nextBreakAt == 0 ? JSONObject.NULL : Scheduler.nextBreakLong(s, d) ? "long" : "short")
                    .put("breakEndAt", time(d.breakEndAt))
                    .put("nextWaterAt", time(d.nextWaterAt))
                    .put("pausedUntil", d.pausedUntil > now ? d.pausedUntil : JSONObject.NULL)
                    .put("setup", new JSONObject()
                            .put("notifications", nm.areNotificationsEnabled())
                            .put("battery", c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName()))
                            .put("fullScreen", Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()))
                    .put("manufacturer", Build.MANUFACTURER.toLowerCase(Locale.ROOT));
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private interface Step { void apply(Settings s, DayState d, long now, ZoneId z); }

    private static void run(Context c, Step step) {
        Settings s = settings(c);
        DayState d = day(c);
        step.apply(s, d, System.currentTimeMillis(), ZoneId.systemDefault());
        commit(c, s, d);
    }

    private static Object time(long t) { return t == 0 ? JSONObject.NULL : t; }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("digitalcare", Context.MODE_PRIVATE);
    }

    private static Settings settings(Context c) {
        try {
            return Json.read(new JSONObject(prefs(c).getString("settings", "{}")), new Settings());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DayState day(Context c) {
        try {
            return Json.read(new JSONObject(prefs(c).getString("day", "{}")), new DayState());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Saves synchronously (the receiver's process can die right after onReceive), then re-arms every alarm. */
    private static void commit(Context c, Settings s, DayState d) {
        try {
            prefs(c).edit()
                    .putString("settings", Json.write(s).toString())
                    .putString("day", Json.write(d).toString())
                    .commit();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        if (d.breakEndAt == 0) Notifier.cancel(c, Notifier.ID_BREAK);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        for (Scheduler.Kind k : Scheduler.Kind.values()) {
            Intent i = new Intent(c, EventReceiver.class).setAction(EventReceiver.ALARM_PREFIX + k.name());
            PendingIntent pi = PendingIntent.getBroadcast(c, k.ordinal(), i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            long t = Scheduler.alarmAt(k, d);
            if (t > 0) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            else am.cancel(pi);
        }
    }
}
