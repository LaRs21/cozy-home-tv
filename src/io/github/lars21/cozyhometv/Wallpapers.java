package io.github.lars21.cozyhometv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background layer that shows the bundled pictures in assets/wallpapers/ one after another with a slow crossfade
 * and an optional gentle "Ken Burns" drift (Settings > Little movements).
 *
 * Memory: only the picture on screen is kept; the next one is decoded (at screen size, into GPU memory) just before
 * it fades in, and the old one is freed as soon as the fade ends. So at most two pictures exist, and only for the
 * few seconds of a crossfade. File names: NN_period_name.jpg, period = day | dusk | night; the rotation starts at
 * the first picture that matches the time of day.
 */
final class Wallpapers implements Scene.Backdrop {
    static final String TAG = "CozyWallpapers", DIR = "wallpapers";
    static final long FADE_MS = 2500;

    /** Receives a small blurred copy of the current picture. The receiver owns the bitmap (and recycles it). */
    interface FrostSink { void onFrost(Bitmap small); }

    static String[] list(Context c) { return list(c, DIR); }
    static String[] list(Context c, String dir) {
        List<String> out = new ArrayList<>();
        try { String[] a = c.getAssets().list(dir); if (a != null) for (String s : a) { String l = s.toLowerCase(); if (l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".webp") || l.endsWith(".png")) out.add(s); } }
        catch (Exception e) { Log.w(TAG, "list", e); }
        Collections.sort(out);
        return out.toArray(new String[0]);
    }

    static String periodNow() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 8 && h < 17) return "day";
        if ((h >= 17 && h < 19) || (h >= 6 && h < 8)) return "dusk";
        return "night";
    }

    /** Told which file is on screen (for a photo credit line). */
    interface OnShow { void onShow(String file); }
    OnShow onShow; long fadeMs = FADE_MS;
    final Context c; final String dir; final String[] names; final int w, h; final long intervalMs; final boolean drift; final FrostSink sink;
    final FrameLayout root; final ImageView[] views = new ImageView[2]; final Bitmap[] bmps = new Bitmap[2];
    int front = -1, index; boolean running, released, loading; float driftDir = 1;
    ValueAnimator kb;
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    final Runnable next = this::advance;

    Wallpapers(Context c, int w, int h, long intervalMs, boolean drift, FrostSink sink) { this(c, DIR, w, h, intervalMs, drift, sink); }
    Wallpapers(Context c, String dir, int w, int h, long intervalMs, boolean drift, FrostSink sink) {
        this.dir = dir;
        this.c = c.getApplicationContext() != null ? c.getApplicationContext() : c; this.w = w; this.h = h;
        this.intervalMs = intervalMs; this.drift = drift; this.sink = sink;
        names = list(c, dir);
        root = new FrameLayout(c);
        for (int i = 0; i < 2; i++) { ImageView iv = new ImageView(c); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setAlpha(0f); views[i] = iv; root.addView(iv, new FrameLayout.LayoutParams(-1, -1)); }
        String p = "_" + periodNow() + "_";
        for (int i = 0; i < names.length; i++) if (names[i].contains(p)) { index = i; break; }
    }

    boolean isEmpty() { return names.length == 0; }
    public View view() { return root; }

    public void start() {
        if (released || running || names.length == 0) return;
        running = true;
        if (front < 0) { if (!loading) load(index, true); return; }
        if (kb != null) { if (kb.isPaused()) kb.resume(); else if (!kb.isStarted()) kb.start(); }
        schedule();
    }

    public void stop() { running = false; ui.removeCallbacks(next); if (kb != null && kb.isRunning()) kb.pause(); }

    public void release() {
        released = true; running = false; ui.removeCallbacksAndMessages(null);
        if (kb != null) kb.cancel(); kb = null;
        for (int i = 0; i < 2; i++) { views[i].animate().cancel(); views[i].setImageDrawable(null); if (bmps[i] != null) bmps[i].recycle(); bmps[i] = null; }
        io.shutdownNow();
    }

    void schedule() { ui.removeCallbacks(next); if (running && names.length > 1 && intervalMs > 0) ui.postDelayed(next, intervalMs); }

    void advance() { if (!running || loading || released) return; index = (index + 1) % names.length; load(index, false); }

    void load(final int i, final boolean first) {
        loading = true; final String path = dir + "/" + names[i]; final String file = names[i];
        try {
            io.execute(() -> {
                long t = android.os.SystemClock.uptimeMillis();
                final Bitmap b = Scene.decodeCoverAsset(c.getAssets(), path, w, h, true);
                final Bitmap fr = sink == null ? null : Scene.decodeFrostAsset(c.getAssets(), path, w, h);
                Log.d(TAG, names[i] + " decoded in " + (android.os.SystemClock.uptimeMillis() - t) + "ms");
                ui.post(() -> {
                    loading = false;
                    if (released || b == null) { if (b != null) b.recycle(); if (fr != null) fr.recycle(); if (!released) schedule(); return; }
                    show(b, fr, first); if (onShow != null) onShow.onShow(file);
                });
            });
        } catch (Exception e) { loading = false; } // executor shut down
    }

    void show(Bitmap b, final Bitmap fr, boolean first) {
        final int nf = front < 0 ? 0 : 1 - front, of = front;
        final ImageView iv = views[nf];
        iv.animate().cancel(); iv.setImageDrawable(null); if (bmps[nf] != null) bmps[nf].recycle(); // a fade that was cut short
        iv.setImageBitmap(b); bmps[nf] = b;
        iv.setScaleX(1f); iv.setScaleY(1f); iv.setTranslationX(0); iv.setAlpha(0f); iv.bringToFront();
        front = nf;
        startDrift(iv);
        long fd = HomeActivity.dur(first ? 400 : fadeMs);
        iv.animate().alpha(1f).setDuration(fd).setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(() -> {
            if (of >= 0 && of != front) { views[of].setImageDrawable(null); views[of].setAlpha(0f); if (bmps[of] != null) bmps[of].recycle(); bmps[of] = null; }
        }).start();
        if (sink != null) {
            if (first) sink.onFrost(fr);
            else ui.postDelayed(() -> { if (!released) sink.onFrost(fr); else if (fr != null) fr.recycle(); }, fd / 2);
        }
        schedule();
    }

    /** Slow zoom from 102% to 107% with a small sideways pan, alternating direction. Never shows an edge. */
    void startDrift(final ImageView iv) {
        if (kb != null) kb.cancel(); kb = null;
        if (!drift) return;
        driftDir = -driftDir; final float dir = driftDir, pan = w * .02f;
        iv.setScaleX(1.02f); iv.setScaleY(1.02f); iv.setTranslationX(-dir * pan * .5f);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        if (intervalMs > 0) a.setDuration(HomeActivity.dur(intervalMs + FADE_MS * 2));
        else { a.setDuration(HomeActivity.dur(90_000)); a.setRepeatMode(ValueAnimator.REVERSE); a.setRepeatCount(ValueAnimator.INFINITE); }
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(an -> { float t = (float) an.getAnimatedValue(); float s = 1.02f + .05f * t; iv.setScaleX(s); iv.setScaleY(s); iv.setTranslationX(dir * pan * (t - .5f)); });
        kb = a; if (running) a.start();
    }
}
