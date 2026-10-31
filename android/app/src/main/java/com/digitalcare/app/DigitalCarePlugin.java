package com.digitalcare.app;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Bridge between the React UI and Engine. Every method resolves with the fresh State. */
@CapacitorPlugin(name = "DigitalCare")
public class DigitalCarePlugin extends Plugin {
    private interface Action { void run(Context c) throws Exception; }

    @Override
    public void load() {
        Notifier.ensureChannels(getContext());
    }

    @PluginMethod
    public void getState(PluginCall call) { respond(call, Engine::ensureFresh); }

    @PluginMethod
    public void saveSettings(PluginCall call) {
        respond(call, c -> Engine.saveSettings(c, Json.read(call.getData(), new com.digitalcare.app.Settings())));
    }

    @PluginMethod
    public void logGlass(PluginCall call) { respond(call, Engine::logGlass); }

    @PluginMethod
    public void pause(PluginCall call) { respond(call, c -> Engine.pause(c, call.getInt("minutes"))); }

    @PluginMethod
    public void resume(PluginCall call) { respond(call, Engine::resume); }

    @PluginMethod
    public void skipBreak(PluginCall call) { respond(call, Engine::skip); }

    @PluginMethod
    public void snoozeBreak(PluginCall call) { respond(call, Engine::snooze); }

    @PluginMethod
    public void getLaunchScreen(PluginCall call) {
        String screen = getActivity().getIntent().getStringExtra("screen");
        call.resolve(new JSObject().put("screen", screen == null ? "home" : screen));
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        String screen = intent.getStringExtra("screen");
        if (screen != null) notifyListeners("launchScreen", new JSObject().put("screen", screen));
    }

    /** Also fires after system dialogs (permission, battery), which don't trigger the WebView's visibilitychange. */
    @Override
    protected void handleOnResume() {
        notifyListeners("appResume", new JSObject());
    }

    @PluginMethod
    public void openSystemSettings(PluginCall call) {
        String page = call.getString("page", "");
        String channel = call.getString("channel");
        String pkg = getContext().getPackageName();
        Uri pkgUri = Uri.parse("package:" + pkg);
        Intent appNotifications = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
        Intent intent;
        switch (page) {
            case "channel":
                intent = channel == null ? appNotifications
                        : new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, channel);
                break;
            case "battery":
                intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri);
                break;
            case "fullScreen":
                intent = Build.VERSION.SDK_INT >= 34
                        ? new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri)
                        : appNotifications;
                break;
            case "nightLight":
                intent = new Intent("android.settings.NIGHT_DISPLAY_SETTINGS");
                break;
            case "extraDim":
                intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                break;
            case "appDetails":
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri);
                break;
            default:
                call.reject("Unknown settings page: " + page);
                return;
        }
        try {
            getActivity().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            getActivity().startActivity(page.equals("nightLight")
                    ? new Intent(Settings.ACTION_DISPLAY_SETTINGS)
                    : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri));
        }
        call.resolve();
    }

    private void respond(PluginCall call, Action action) {
        try {
            action.run(getContext());
            call.resolve(JSObject.fromJSONObject(Engine.state(getContext())));
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }
}
