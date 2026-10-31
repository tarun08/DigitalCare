package com.digitalcare.app;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.core.content.ContextCompat;

/**
 * Counts continuous phone use while the app is closed. Screen events only reach a process that is alive,
 * so this runs as a foreground service with a visible notification.
 */
public class ScreenService extends Service {
    static volatile boolean running;
    private final Handler handler = new Handler(Looper.getMainLooper());
    static volatile ScreenUse use = new ScreenUse(); // shared so Home can show the count; a new one per service start
    static volatile ScreenService instance;           // the running service, so a changed limit can reach it

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            long now = System.currentTimeMillis();
            if (Intent.ACTION_USER_PRESENT.equals(i.getAction())) use.unlock(now);
            else use.screenOff(now);
            schedule();
        }
    };

    private final Runnable remind = () -> {
        Engine.remindScreen(this, System.currentTimeMillis());
        schedule();
    };

    /**
     * Starts or stops the service to match the setting. Android 12+ can refuse a background start; the next app open retries.
     * A service already running re-times its pending reminder: the limit may have just changed.
     */
    static void sync(Context c, boolean on) {
        if (on == running) {
            ScreenService s = instance;
            if (on && s != null) s.handler.post(s::schedule);
            return;
        }
        Intent i = new Intent(c, ScreenService.class);
        try {
            if (on) c.startForegroundService(i);
            else c.stopService(i);
        } catch (RuntimeException refused) {
            // Left alone on purpose: the next app open calls sync again.
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        use = new ScreenUse();
        Notifier.ensureServiceChannel(this);
        Notification notice = Notifier.screenServiceNotice(this);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID_SCREEN_SERVICE, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(Notifier.ID_SCREEN_SERVICE, notice);
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        // Exported on purpose: the unlock broadcast comes from the lock screen app, which a not-exported receiver never hears.
        // Both actions are protected, so only the system can send them.
        ContextCompat.registerReceiver(this, screen, filter, ContextCompat.RECEIVER_EXPORTED);
        // Started while the phone is in use: the stretch starts now. Started with the screen off: the next unlock starts one.
        long now = System.currentTimeMillis();
        boolean inUse = getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked();
        if (inUse) use.unlock(now);
        else use.screenOff(now - ScreenUse.RESET_MS);
        Engine.screenServiceStarted(this);
        instance = this;
        running = true;
        schedule();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        handler.removeCallbacks(remind);
        unregisterReceiver(screen);
        instance = null;
        running = false;
        super.onDestroy();
    }

    private void schedule() {
        handler.removeCallbacks(remind);
        long now = System.currentTimeMillis();
        long at = use.nextReminder(Engine.screenLimitMin(this) * Scheduler.MIN, now);
        if (at != 0) handler.postDelayed(remind, at - now);
    }
}
