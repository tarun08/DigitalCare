package com.digitalcare.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.lifecycle.Lifecycle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    /** A reminder opened this over another app, or over the lock screen: Done should hand the phone back. */
    private volatile boolean openedByReminder;

    /** True once per such launch, for the plugin's leave(). */
    boolean takeOpenedByReminder() {
        boolean was = openedByReminder;
        openedByReminder = false;
        return was;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DigitalCarePlugin.class);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        // The WebView ignores the system font size unless told; this makes the layout follow it.
        bridge.getWebView().getSettings().setTextZoom(Math.round(getResources().getConfiguration().fontScale * 100));
    }

    /** Also runs for the launch intent: BridgeActivity.load() calls onNewIntent(getIntent()). */
    @Override
    protected void onNewIntent(Intent intent) {
        // getLaunchScreen reads getIntent(): keep it current when a notification tap relaunches a killed process.
        setIntent(intent);
        super.onNewIntent(intent);
        // Show over the lock screen only for full-screen reminder launches; normal launches clear it.
        boolean wake = intent.getBooleanExtra("wake", false);
        // Only when the app was not already on screen: someone using DigitalCare itself should stay in it.
        openedByReminder = wake && !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(wake);
            setTurnScreenOn(wake);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // Once the reminder is out of sight, the power button shows the normal lock screen again.
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(false);
            setTurnScreenOn(false);
        }
    }
}
