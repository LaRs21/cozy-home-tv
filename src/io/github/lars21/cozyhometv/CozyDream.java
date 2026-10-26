package io.github.lars21.cozyhometv;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * Screensaver. Cozy look: the current time-of-day picture (or live background) with a small clock.
 * Default look: the theme's background (Glass: the bundled wallpapers, slowly rotating) with a small clock, or for
 * a theme without pictures (Classic) a calm dark gradient with a large clock and the date. The clock fades to a new spot every
 * 75 s against burn-in. Any key wakes the TV. Everything is freed in onDetachedFromWindow.
 */
public class CozyDream extends DreamService {
    static final int TEXT = 0xFFF4F1EA;
    float S = 1; int sw, sh; int d(float v) { return Math.round(v * S); }
    final Handler ui = new Handler(Looper.getMainLooper()); final Random rnd = new Random();
    FrameLayout root; Scene.Backdrop backdrop; LinearLayout clockBox; TextView clock, date; boolean cozy, pictures, running;
    final List<ValueAnimator> loops = new ArrayList<>(); Prefs prefs;
    final BroadcastReceiver tick = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { updateClock(); } };
    final Runnable drift = new Runnable() { @Override public void run() { if (!running) return; moveClock(true); ui.postDelayed(this, 75_000); } };

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); setFullscreen(true); setScreenBright(true);
        Point sz = new Point(); getWindowManager().getDefaultDisplay().getRealSize(sz); sw = Math.max(sz.x, sz.y); sh = Math.min(sz.x, sz.y);
        S = Math.min(sw / 1920f, sh / 1080f);
        prefs = new Prefs(this);
        root = new FrameLayout(this); root.setBackground(new Ui.Night());
        CozyPack pack = prefs.cozy() ? CozyPack.load(this) : null;
        cozy = pack != null && pack.hasScene();
        if (cozy) {
            String p = pack.periodNow(prefs.period()); File img = pack.scene(p);
            boolean live = pack.liveEnabled && prefs.motion() && pack.liveBase.get(p) != null;
            Scene.StillBackdrop still;
            if (live) { Scene.LiveBackdrop lb = new Scene.LiveBackdrop(this, pack.liveBase.get(p), pack.liveMoments.get(p), pack.liveGapMin, pack.liveGapMax); backdrop = lb; still = lb.still; }
            else { still = new Scene.StillBackdrop(this); backdrop = still; }
            Bitmap b = Scene.decodeCover(img, sw, sh, true);
            if (b != null) still.set(b);
            root.addView(backdrop.view(), new FrameLayout.LayoutParams(-1, -1));
            if (pack.fireflies && prefs.motion()) addFireflies(p.equals("morning") || p.equals("day") ? .18f : 1f);
        }
        Theme theme = Themes.get(prefs.theme());
        if (!cozy) {
            backdrop = theme.background(this, sw, sh, prefs, null);
            if (backdrop != null) {
                root.addView(backdrop.view(), new FrameLayout.LayoutParams(-1, -1));
                View shade = new View(this); // keeps the small clock readable on bright pictures
                shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x55000000, 0x00000000, 0x00000000, 0x55000000}));
                root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
            }
        }
        pictures = cozy || backdrop != null;
        Typeface tf; try { tf = cozy ? Typeface.createFromAsset(getAssets(), "caveat.ttf") : theme.clockFont(this); } catch (Exception e) { tf = Typeface.DEFAULT; }
        clockBox = new LinearLayout(this); clockBox.setOrientation(LinearLayout.VERTICAL); clockBox.setGravity(Gravity.CENTER_HORIZONTAL);
        clock = new TextView(this); clock.setTypeface(tf); clock.setTextColor(TEXT); clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, d(cozy ? 72 : pictures ? 64 : 150));
        clock.setShadowLayer(d(8), 0, d(2), 0xA0000000); clock.setFontFeatureSettings("tnum");
        date = new TextView(this); date.setTextColor(0xB3F4F1EA); date.setTextSize(TypedValue.COMPLEX_UNIT_PX, d(pictures ? 26 : 34)); date.setShadowLayer(d(6), 0, d(2), 0xA0000000);
        clockBox.addView(clock); clockBox.addView(date); clockBox.setAlpha(.85f);
        root.addView(clockBox, new FrameLayout.LayoutParams(-2, -2));
        updateClock(); moveClock(false);
        setContentView(root);
    }

    void addFireflies(float alpha) {
        FrameLayout amb = new FrameLayout(this); amb.setAlpha(alpha); root.addView(amb, new FrameLayout.LayoutParams(-1, -1));
        float[][] fl = {{44, 30, 0}, {52, 26, 6000}, {60, 33, 10000}, {56, 38, 3000}, {30, 22, 8000}, {72, 24, 4500}};
        for (float[] f : fl) {
            final Ui.Dot dot = new Ui.Dot(this, d(2.4f), d(11));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(d(30), d(30)); lp.leftMargin = Math.round(f[0] / 100 * sw); lp.topMargin = Math.round(f[1] / 100 * sh); amb.addView(dot, lp);
            ValueAnimator a = ValueAnimator.ofFloat(0, 1); a.setDuration(HomeActivity.dur(14000)); a.setRepeatCount(ValueAnimator.INFINITE); a.setInterpolator(new LinearInterpolator());
            a.addUpdateListener(an -> { float t = (float) an.getAnimatedValue(); float u = t < .5f ? t * 2 : (1 - t) * 2; float e = (float) (1 - Math.cos(Math.PI * u)) / 2; dot.setTranslationX(d(40) * e); dot.setTranslationY(-d(24) * e); dot.setAlpha(.2f + .6f * e); });
            a.setCurrentPlayTime((long) f[2]); loops.add(a);
        }
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted(); running = true;
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK); f.addAction(Intent.ACTION_TIME_CHANGED); f.addAction(Intent.ACTION_TIMEZONE_CHANGED); registerReceiver(tick, f);
        updateClock(); for (ValueAnimator a : loops) a.start(); if (backdrop != null) backdrop.start(); ui.postDelayed(drift, 75_000);
    }

    @Override public void onDreamingStopped() {
        running = false; ui.removeCallbacks(drift); try { unregisterReceiver(tick); } catch (Exception ignored) {}
        for (ValueAnimator a : loops) a.cancel(); if (backdrop != null) backdrop.stop(); super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        running = false; ui.removeCallbacksAndMessages(null); for (ValueAnimator a : loops) a.cancel(); loops.clear();
        if (backdrop != null) backdrop.release(); backdrop = null;
        if (root != null) root.removeAllViews(); root = null;
        super.onDetachedFromWindow();
    }

    void updateClock() {
        if (clock == null) return;
        Boolean c24 = prefs.clock24(); boolean h24 = c24 != null ? c24 : DateFormat.is24HourFormat(this);
        Calendar now = Calendar.getInstance();
        clock.setText(DateFormat.format(h24 ? "H:mm" : "h:mm", now)); date.setText(DateFormat.format("EEEE, d MMMM", now));
    }

    /** With pictures: a small clock in one of the corners. Without: a big clock somewhere near the middle. */
    void moveClock(boolean fade) {
        final float x, y;
        if (pictures) { int c = rnd.nextInt(4); x = (c % 2 == 0 ? .06f : .80f) * sw + rnd.nextInt(Math.max(1, d(40))); y = (c < 2 ? .06f : .80f) * sh + rnd.nextInt(Math.max(1, d(30))); }
        else { x = (.18f + rnd.nextFloat() * .40f) * sw; y = (.22f + rnd.nextFloat() * .36f) * sh; }
        Runnable place = () -> { if (clockBox == null) return; clockBox.setTranslationX(x); clockBox.setTranslationY(y); };
        if (!fade) { place.run(); return; }
        clockBox.animate().alpha(0).setDuration(HomeActivity.dur(900)).withEndAction(() -> { place.run(); if (clockBox != null) clockBox.animate().alpha(.85f).setDuration(HomeActivity.dur(900)).start(); }).start();
    }
}
