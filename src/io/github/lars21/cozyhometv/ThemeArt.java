package io.github.lars21.cozyhometv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Backgrounds and code-drawn motion for the illustrated themes (Cozy Home, Neon City, Starship, Retro Arcade). */
final class ThemeArt {
    private ThemeArt() {}

    /** morning / day / evening / night from the clock (same split as Cozy packs). */
    static String period() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 5 && h < 10 ? "morning" : h >= 10 && h < 17 ? "day" : h >= 17 && h < 20 ? "evening" : "night";
    }

    /**
     * One bundled still picture (decoded once at screen size into GPU memory) with an optional code-drawn motion layer
     * on top. The motion runs only between start() and stop() and only when Little movements is on.
     */
    static final class ArtBackdrop implements Scene.Backdrop {
        interface PathFor { String get(); }
        final Context c; final int w, h; final PathFor path; final Ui.Ambient ambient; final boolean motion; final Wallpapers.FrostSink sink;
        final FrameLayout root; final ImageView iv; Bitmap bmp; String shown; boolean released, loading, running;
        final Handler ui = new Handler(Looper.getMainLooper()); final ExecutorService io = Executors.newSingleThreadExecutor();

        ArtBackdrop(Context c, int w, int h, PathFor path, Ui.Ambient ambient, boolean motion, Wallpapers.FrostSink sink) {
            this.c = c.getApplicationContext() != null ? c.getApplicationContext() : c; this.w = w; this.h = h; this.path = path;
            this.ambient = ambient; this.motion = motion; this.sink = sink;
            root = new FrameLayout(c); iv = new ImageView(c); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setAlpha(0f);
            root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            if (ambient != null) root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
        }
        public View view() { return root; }
        public void start() {
            if (released) return; running = true;
            String p = path.get();
            if (!p.equals(shown) && !loading) load(p);
            if (ambient != null) ambient.setRunning(motion);
        }
        public void stop() { running = false; if (ambient != null) ambient.setRunning(false); }
        public void release() {
            released = true; stop(); ui.removeCallbacksAndMessages(null);
            iv.setImageDrawable(null); if (bmp != null) bmp.recycle(); bmp = null; io.shutdownNow();
        }
        void load(final String p) {
            loading = true;
            try {
                io.execute(() -> {
                    final Bitmap b = Scene.decodeCoverAsset(c.getAssets(), p, w, h, true);
                    final Bitmap fr = sink == null ? null : Scene.decodeFrostAsset(c.getAssets(), p, w, h);
                    ui.post(() -> {
                        loading = false;
                        if (released || b == null) { if (b != null) b.recycle(); if (fr != null) fr.recycle(); return; }
                        Bitmap old = bmp; bmp = b; shown = p; iv.setImageBitmap(b);
                        iv.animate().alpha(1f).setDuration(HomeActivity.dur(old == null ? 300 : 900)).start();
                        if (old != null) old.recycle();
                        if (sink != null) sink.onFrost(fr);
                    });
                });
            } catch (Exception e) { loading = false; }
        }
    }

    // ---------------------------------------------------------------- Cozy Home
    /** Warm specks of dust drifting up through the lamplight. */
    static final class Motes extends Ui.Ambient {
        final Bitmap g = Ui.glow(48); final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG); final float[][] m = new float[14][5]; final RectF r = new RectF();
        Motes(Context c) {
            super(c, 24); Random rnd = new Random(7); p.setColorFilter(new PorterDuffColorFilter(0xFFFFD79A, PorterDuff.Mode.MULTIPLY));
            for (float[] a : m) { a[0] = rnd.nextFloat(); a[1] = rnd.nextFloat(); a[2] = .006f + rnd.nextFloat() * .01f; a[3] = rnd.nextFloat() * 6.28f; a[4] = .4f + rnd.nextFloat() * .8f; }
        }
        @Override void paint(Canvas c, float t, int w, int h) {
            for (float[] a : m) {
                float y = (a[1] - t * a[2]) % 1f; if (y < 0) y += 1f;
                float x = a[0] + .012f * (float) Math.sin(t * .4f + a[3]);
                float s = h * .012f * a[4]; float al = (float) Math.sin(Math.PI * y); // fade in at the bottom, out at the top
                p.setAlpha((int) (110 * al * a[4]));
                r.set(x * w - s, y * h - s, x * w + s, y * h + s); c.drawBitmap(g, null, r, p);
            }
        }
    }

    /** A string of warm fairy lights across the dock row; bulbs twinkle slowly, each at its own pace. */
    static final class FairyLights extends Ui.Ambient {
        final Bitmap g = Ui.glow(64); final Paint wire = new Paint(Paint.ANTI_ALIAS_FLAG), core = new Paint(Paint.ANTI_ALIAS_FLAG), halo = new Paint(Paint.FILTER_BITMAP_FLAG);
        final float wireY, sag; final int n = 26; final float[] phase = new float[n], speed = new float[n]; final RectF r = new RectF(); final android.graphics.Path path = new android.graphics.Path();
        FairyLights(Context c, float wireY, float sag) {
            super(c, 20); this.wireY = wireY; this.sag = sag; Random rnd = new Random(11);
            for (int i = 0; i < n; i++) { phase[i] = rnd.nextFloat() * 6.28f; speed[i] = .6f + rnd.nextFloat() * 1.4f; }
            wire.setStyle(Paint.Style.STROKE); wire.setColor(0xCC3B2A1C); core.setColor(0xFFFFE6B0);
            halo.setColorFilter(new PorterDuffColorFilter(0xFFFFC36B, PorterDuff.Mode.MULTIPLY));
        }
        float yAt(float x, int w) { float u = x / w * 2f - 1f; return wireY + sag * (1 - u * u); }
        @Override void paint(Canvas c, float t, int w, int h) {
            wire.setStrokeWidth(Math.max(1.5f, h * .02f)); path.reset();
            for (int i = 0; i <= 40; i++) { float x = w * i / 40f; if (i == 0) path.moveTo(x, yAt(x, w)); else path.lineTo(x, yAt(x, w)); }
            c.drawPath(path, wire);
            float br = Math.max(3, wireY * .16f);
            for (int i = 0; i < n; i++) {
                float x = w * (i + .5f) / n, y = yAt(x, w) + br * 1.2f;
                float tw = .55f + .45f * (float) Math.sin(t * speed[i] + phase[i]);
                halo.setAlpha((int) (200 * tw)); float hs = br * 5f; r.set(x - hs, y - hs, x + hs, y + hs); c.drawBitmap(g, null, r, halo);
                core.setAlpha((int) (150 + 105 * tw)); c.drawCircle(x, y, br, core);
            }
        }
    }

    // ---------------------------------------------------------------- Neon City
    /** Thin rain streaks plus neon signs that flicker now and then (drawn over the still). */
    static final class NeonRain extends Ui.Ambient {
        final int n = 90; final float[] x = new float[n], y = new float[n], v = new float[n], len = new float[n]; final float[] pts = new float[n * 4];
        final Paint rain = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(); final Random rnd = new Random(3);
        /** Sign rectangles in fractions of the screen (matched to the bundled picture). */
        final float[][] signs; final long[] nextFlick; final float[] flickUntil; final RectF r = new RectF();
        float last = -1;
        NeonRain(Context c, float[][] signs) {
            super(c, 30); this.signs = signs; nextFlick = new long[signs.length]; flickUntil = new float[signs.length];
            for (int i = 0; i < n; i++) { x[i] = rnd.nextFloat(); y[i] = rnd.nextFloat(); v[i] = .9f + rnd.nextFloat() * .6f; len[i] = .025f + rnd.nextFloat() * .035f; }
            for (int i = 0; i < signs.length; i++) nextFlick[i] = 2 + rnd.nextInt(9);
            rain.setColor(0x55C8D4FF); rain.setStrokeCap(Paint.Cap.ROUND); off.setColor(0xC8070512);
        }
        @Override void paint(Canvas c, float t, int w, int h) {
            float dt = last < 0 ? 0 : Math.min(.1f, t - last); last = t;
            rain.setStrokeWidth(Math.max(1, w / 1400f));
            for (int i = 0; i < n; i++) {
                y[i] += v[i] * dt; if (y[i] > 1.05f) { y[i] = -.05f; x[i] = rnd.nextFloat() * 1.1f; }
                float x0 = x[i] * w, y0 = y[i] * h, dy = len[i] * h;
                pts[i * 4] = x0; pts[i * 4 + 1] = y0; pts[i * 4 + 2] = x0 - dy * .12f; pts[i * 4 + 3] = y0 + dy;
            }
            c.drawLines(pts, rain);
            for (int i = 0; i < signs.length; i++) {
                if (t >= nextFlick[i]) { flickUntil[i] = t + .06f + rnd.nextFloat() * .16f; nextFlick[i] = (long) t + 4 + rnd.nextInt(10); }
                boolean dark = t < flickUntil[i] && ((int) (t * 40) % 3 != 0);
                if (dark) { float[] s = signs[i]; r.set(s[0] * w, s[1] * h, s[2] * w, s[3] * h); c.drawRect(r, off); }
            }
        }
    }

    // ---------------------------------------------------------------- Starship
    /** Slow starfield drift, only inside the cockpit window. */
    static final class Starfield extends Ui.Ambient {
        final int n = 160; final float[] sx = new float[n], sy = new float[n], sz = new float[n];
        final Paint[] p = new Paint[3]; final float[][] pts = new float[3][n * 2]; final int[] cnt = new int[3]; final float[] window; final Random rnd = new Random(5);
        float last = -1;
        Starfield(Context c, float[] window) {
            super(c, 30); this.window = window;
            for (int i = 0; i < n; i++) reset(i, true);
            for (int k = 0; k < 3; k++) { p[k] = new Paint(Paint.ANTI_ALIAS_FLAG); p[k].setStrokeCap(Paint.Cap.ROUND); p[k].setColor(k == 0 ? 0x66CFE8FF : k == 1 ? 0xAADDEEFF : 0xEEFFFFFF); }
        }
        void reset(int i, boolean any) { sx[i] = rnd.nextFloat() * 2 - 1; sy[i] = rnd.nextFloat() * 2 - 1; sz[i] = any ? .05f + rnd.nextFloat() * .95f : 1f; }
        @Override void paint(Canvas c, float t, int w, int h) {
            float dt = last < 0 ? 0 : Math.min(.1f, t - last); last = t;
            float l = window[0] * w, tp = window[1] * h, rr = window[2] * w, b = window[3] * h, cx = (l + rr) / 2, cy = (tp + b) / 2, ext = (rr - l) / 2;
            cnt[0] = cnt[1] = cnt[2] = 0;
            for (int i = 0; i < n; i++) {
                sz[i] -= dt * .022f; if (sz[i] <= .04f) reset(i, false);
                float px = cx + sx[i] / sz[i] * ext * .35f, py = cy + sy[i] / sz[i] * ext * .35f;
                if (px < l || px > rr || py < tp || py > b) { if (sz[i] < .5f) reset(i, false); continue; }
                int k = sz[i] > .66f ? 0 : sz[i] > .33f ? 1 : 2; pts[k][cnt[k] * 2] = px; pts[k][cnt[k] * 2 + 1] = py; cnt[k]++;
            }
            for (int k = 0; k < 3; k++) { p[k].setStrokeWidth(Math.max(1, w / 1920f * (1.4f + k * 1.1f))); c.drawPoints(pts[k], 0, cnt[k] * 2, p[k]); }
        }
    }

    // ---------------------------------------------------------------- Retro Arcade
    /** CRT feel: fixed scanlines, a faint bright band rolling down, and a few blinking pixel stars in the window. */
    static final class Crt extends Ui.Ambient {
        final Paint lines = new Paint(), band = new Paint(), star = new Paint(); final float[] sky; final float[][] stars = new float[12][3]; float bandH = -1;
        Crt(Context c, float[] sky) {
            super(c, 15); this.sky = sky; Random rnd = new Random(9);
            for (float[] s : stars) { s[0] = rnd.nextFloat(); s[1] = rnd.nextFloat(); s[2] = rnd.nextFloat() * 6.28f; }
            Bitmap tile = Bitmap.createBitmap(1, 4, Bitmap.Config.ARGB_8888); tile.setPixel(0, 0, 0x00000000); tile.setPixel(0, 1, 0x00000000); tile.setPixel(0, 2, 0x26000000); tile.setPixel(0, 3, 0x26000000);
            lines.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)); star.setColor(0xFFFFFFFF);
        }
        @Override void paint(Canvas c, float t, int w, int h) {
            c.drawRect(0, 0, w, h, lines);
            float cyc = (t % 9f) / 9f, bh = h * .14f, y = -bh + cyc * (h + bh * 2);
            if (bandH != bh) { bandH = bh; band.setShader(new LinearGradient(0, 0, 0, bh, new int[]{0x00FFFFFF, 0x10FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP)); }
            c.save(); c.translate(0, y); c.drawRect(0, 0, w, bh, band); c.restore();
            float px = Math.max(2, w / 480f);
            for (float[] s : stars) {
                float a = .5f + .5f * (float) Math.sin(t * 1.7f + s[2]); if (a < .35f) continue;
                star.setAlpha((int) (255 * a)); float x = (sky[0] + s[0] * (sky[2] - sky[0])) * w, yy = (sky[1] + s[1] * (sky[3] - sky[1])) * h;
                x -= x % px; yy -= yy % px; c.drawRect(x, yy, x + px, yy + px, star);
            }
        }
    }
}
