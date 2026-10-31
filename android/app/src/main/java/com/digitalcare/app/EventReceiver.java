package com.digitalcare.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** One receiver for alarms, notification buttons and boot/time-change broadcasts. */
public class EventReceiver extends BroadcastReceiver {
    static final String ALARM_PREFIX = "com.digitalcare.app.ALARM.";
    static final String SKIP = "com.digitalcare.app.SKIP";
    static final String SNOOZE = "com.digitalcare.app.SNOOZE";
    static final String GLASS = "com.digitalcare.app.GLASS";

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (a == null) return;
        if (a.startsWith(ALARM_PREFIX)) {
            Engine.onAlarm(c, Scheduler.Kind.valueOf(a.substring(ALARM_PREFIX.length())));
            return;
        }
        switch (a) {
            case SKIP: Engine.skip(c); break;
            case SNOOZE: Engine.snooze(c); break;
            case GLASS: Engine.logGlass(c); break;
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                Engine.recompute(c);
                break;
        }
    }
}
