package io.github.lars21.cozyhometv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional "Home button fix" (the user turns it on in Accessibility settings).
 *
 * Google TV resolves the HOME intent to its own launcher (a priority-2 system filter) even when another app holds
 * the HOME role, so pressing Home flashes Google TV before any third-party launcher appears. This service:
 *   1. catches the Home key itself (key filter, before the system acts on it) and opens Cozy Home TV directly;
 *      a long press (600 ms+) still opens the system panel;
 *   2. as a fallback, if the stock launcher's home screen still comes up, brings Cozy Home TV back
 *      (only its home activities: the Settings-key dashboard and other overlays are never touched);
 *   3. opens Cozy Home TV once after each boot.
 * It never reads window content and consumes no other keys.
 */
public class HomeButtonService extends AccessibilityService {
    private static final String TAG = "CozyHomeButton";
    private static final String GTV = "com.google.android.apps.tv.launcherx", GTV_HOME = GTV + ".home.";
    private static final String ATV = "com.google.android.tvlauncher", ATV_HOME = ATV + ".MainActivity";
    private final long[] recent = new long[3]; private int n = 0;

    @Override protected void onServiceConnected() {
        AccessibilityServiceInfo i = getServiceInfo();
        if (i != null) { i.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS; setServiceInfo(i); }
        // After boot the stock home is shown before this service binds: open ours once per boot (never on a later rebind).
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        SharedPreferences sp = getSharedPreferences("home_button", MODE_PRIVATE);
        boolean firstThisBoot = boot != sp.getInt("boot", -2);
        sp.edit().putInt("boot", boot).apply();
        if (firstThisBoot && SystemClock.elapsedRealtime() < 4 * 60 * 1000) { Log.d(TAG, "boot " + boot + ": opening Cozy Home TV"); openHome(); }
    }

    private void openHome() {
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_UP) {
            long held = e.getEventTime() - e.getDownTime();
            if (held >= 600) performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); else openHome();
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent ev) {
        if (ev.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || ev.getPackageName() == null || ev.getClassName() == null) return;
        String pkg = ev.getPackageName().toString(), cls = ev.getClassName().toString();
        boolean stockHome = (pkg.equals(GTV) && cls.startsWith(GTV_HOME)) || (pkg.equals(ATV) && cls.equals(ATV_HOME));
        if (!stockHome) return;
        // The stock launcher sometimes re-opens itself right after a redirect: at most 3 redirects per 3 s (no ping-pong).
        long now = SystemClock.elapsedRealtime();
        if (recent[n % 3] != 0 && now - recent[n % 3] < 3000) return;
        recent[n % 3] = now; n++;
        openHome();
    }

    @Override public void onInterrupt() {}
}
