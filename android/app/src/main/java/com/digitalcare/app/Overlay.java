package com.digitalcare.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.Locale;

/**
 * A reminder drawn straight over whatever app is open. It needs "Display over other apps" and nothing else: no screen
 * is started from the background, which phones like Vivo and Xiaomi refuse unless a separate permission is on.
 * Each try is recorded (Engine.fullScreenTried) so Home and Settings can say whether it showed, and why not.
 */
final class Overlay {
    /** A button on the reminder. `run` happens after the reminder closes; null just closes it. */
    static final class Choice {
        final String label;
        final boolean main;
        final Runnable run;

        Choice(String label, boolean main, Runnable run) {
            this.label = label;
            this.main = main;
            this.run = run;
        }
    }

    private static Handler handler; // made on first use: the unit tests only need blocked() and clock()
    private static View shown;
    private static WindowManager shownIn;

    private Overlay() {}

    /**
     * Why a full-screen reminder cannot show now, or null when it can: "permission" while "Display over other apps" is
     * off, "call" while the phone rings or a phone or app call is on (it would cover the call's buttons).
     */
    static String blocked(boolean canDraw, int audioMode) {
        if (!canDraw) return "permission";
        return audioMode == AudioManager.MODE_NORMAL ? null : "call";
    }

    /** m:ss, rounded up to the next second, never below 0:00. */
    static String clock(long msLeft) {
        long seconds = Math.max(0, (msLeft + 999) / 1000);
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Shows the reminder until `endAt` (counting down when `showClock`), replacing one already up. `scale` makes the
     * text smaller for Hindi. If the phone refuses the window, DigitalCare's own `screen` is opened instead.
     */
    static void show(Context c, String screen, String title, String body, long endAt, boolean showClock, float scale, Choice... choices) {
        Context app = c.getApplicationContext();
        main().post(() -> {
            close();
            try {
                build(app, title, body, endAt, showClock, scale, choices);
                Engine.fullScreenTried(app, "shown");
            } catch (RuntimeException refused) {
                shown = null;
                Engine.fullScreenTried(app, "failed: " + refused.getClass().getSimpleName());
                openScreen(app, screen);
            }
        });
    }

    private static void build(Context app, String title, String body, long endAt, boolean showClock, float scale, Choice[] choices) {
        Context ui = windowContext(app);
        float dp = ui.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(ui);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding((int) (24 * dp), (int) (24 * dp), (int) (24 * dp), (int) (24 * dp));
        root.setBackgroundColor(0xF2101A1F);
        // The background covers the whole screen; the content keeps clear of the status and navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int pad = (int) (24 * dp);
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom);
            return insets;
        });
        root.addView(label(ui, title, 24 * scale, true, 0xFFE8EEF1, dp));
        if (!body.isEmpty()) root.addView(label(ui, body, 16 * scale, false, 0xFFA9B8C0, dp));
        TextView clock = null;
        if (showClock) {
            clock = label(ui, clock(endAt - System.currentTimeMillis()), 56 * scale, true, 0xFF5CCFB8, dp);
            root.addView(clock);
        }
        LinearLayout row = new LinearLayout(ui);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        for (Choice choice : choices) row.addView(button(ui, choice, scale, dp));
        root.addView(row);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        WindowManager wm = ui.getSystemService(WindowManager.class);
        wm.addView(root, params);
        shown = root;
        shownIn = wm;

        TextView ticking = clock;
        main().post(new Runnable() {
            @Override public void run() {
                if (shown != root) return; // closed, or replaced by a newer reminder
                long left = endAt - System.currentTimeMillis();
                if (left <= 0) { close(); return; }
                if (ticking != null) ticking.setText(clock(left));
                main().postDelayed(this, 250);
            }
        });
    }

    /** Android 11+ wants windows added through a context made for that window type. */
    private static Context windowContext(Context app) {
        if (Build.VERSION.SDK_INT < 30) return app;
        Display display = app.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        return app.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
    }

    /** The phone refused the window: try DigitalCare's own screen, which some phones allow instead. */
    private static void openScreen(Context app, String screen) {
        if (screen == null) return; // break over has no screen of its own: the banner stays
        try {
            app.startActivity(new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("screen", screen).putExtra("wake", true));
        } catch (RuntimeException refused) {
            // nothing more to try: the banner is there
        }
    }

    private static void close() {
        View view = shown;
        WindowManager wm = shownIn;
        shown = null;
        shownIn = null;
        if (view == null || wm == null) return;
        try {
            wm.removeView(view);
        } catch (RuntimeException gone) {
            // already removed
        }
    }

    private static TextView label(Context ui, String text, float sp, boolean bold, int color, float dp) {
        TextView view = new TextView(ui);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        view.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
        return view;
    }

    private static Button button(Context ui, Choice choice, float scale, float dp) {
        Button view = new Button(ui);
        view.setText(choice.label);
        view.setAllCaps(false);
        view.setTextSize(16 * scale);
        view.setTextColor(choice.main ? 0xFF062B24 : 0xFFE8EEF1);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(choice.main ? 0xFF5CCFB8 : 0xFF24313A);
        shape.setCornerRadius(12 * dp);
        view.setBackground(shape);
        view.setStateListAnimator(null); // no lift or shadow when pressed: both buttons keep the same flat shape
        // Both buttons share the row equally, with the same space above and below.
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, (int) (56 * dp), 1f);
        size.setMargins((int) (6 * dp), (int) (16 * dp), (int) (6 * dp), (int) (16 * dp));
        view.setLayoutParams(size);
        view.setOnClickListener(v -> {
            close();
            if (choice.run != null) choice.run.run();
        });
        return view;
    }

    private static Handler main() {
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        return handler;
    }
}
