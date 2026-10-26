package io.github.lars21.cozyhometv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.File;
import java.util.Random;

/** Image decoding and the background layer ("backdrop") of Cozy mode. */
final class Scene {
    static final String TAG = "CozyScene";
    private Scene() {}

    /**
     * Decodes an image so it just covers targetW x targetH (the real screen size). Bigger pictures are scaled down
     * once with filtering (no visible loss: the screen cannot show more pixels); smaller ones are never upscaled in
     * memory (the GPU scales them while drawing). Result is a HARDWARE bitmap when possible: pixels live in GPU memory.
     */
    static Bitmap decodeCover(File f, int targetW, int targetH, boolean hardware) {
        if (f == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                final int tw = targetW, th = targetH; final boolean hw = hardware;
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(f), (dec, info, src) -> {
                    int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                    float k = Math.max(tw / (float) w, th / (float) h);
                    if (k < 1f) dec.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)));
                    dec.setAllocator(hw ? ImageDecoder.ALLOCATOR_HARDWARE : ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            }
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= targetW && o.outHeight / (sample * 2) >= targetH) sample *= 2;
            o = new BitmapFactory.Options(); o.inSampleSize = sample; o.inScaled = false;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b == null) return null;
            float k = Math.max(targetW / (float) b.getWidth(), targetH / (float) b.getHeight());
            if (k < 1f) { Bitmap s = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true); if (s != b) b.recycle(); b = s; }
            if (hardware) { Bitmap h = b.copy(Bitmap.Config.HARDWARE, false); if (h != null) { b.recycle(); b = h; } }
            return b;
        } catch (Throwable e) {
            Log.e(TAG, "decode " + f, e);
            return null;
        }
    }

    /** Small, blurred copy of the scene for the frosted panels (about 1/8 of the screen, blurred once, ~100 KB). */
    static Bitmap decodeFrost(File f, int screenW, int screenH) {
        Bitmap small = decodeCover(f, Math.max(1, screenW / 8), Math.max(1, screenH / 8), false);
        if (small == null) return null;
        Bitmap b = Ui.blur(small, 4); small.recycle();
        return b;
    }

    /** The background layer. Still image now; a live video can be dropped in without touching the rest of the screen. */
    interface Backdrop {
        View view();
        /** Called when the scene becomes visible (onStart / dream started). */
        void start();
        /** Called when hidden: stop animation, keep pixels. */
        void stop();
        /** Free everything (bitmaps, decoders, surfaces). */
        void release();
    }

    /** Full-screen still picture. */
    static final class StillBackdrop implements Backdrop {
        final ImageView iv; Bitmap bmp;
        StillBackdrop(Context c) { iv = new ImageView(c); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); }
        void set(Bitmap b) { Bitmap old = bmp; bmp = b; iv.setImageBitmap(b); if (old != null && old != b) old.recycle(); }
        public View view() { return iv; }
        public void start() {}
        public void stop() {}
        public void release() { iv.setImageDrawable(null); if (bmp != null) bmp.recycle(); bmp = null; }
    }

    /**
     * EXPERIMENTAL live background ("lofi" style): a still picture underneath, a muted looping base clip on top, and
     * now and then one short one-shot clip ("moment") that plays once over the base and fades away. All clips must be
     * rendered from the same still so the cuts are invisible. No clips ship with the app; see docs/live-background.md.
     */
    static final class LiveBackdrop implements Backdrop, TextureView.SurfaceTextureListener {
        final FrameLayout root; final StillBackdrop still; final TextureView baseView, momentView;
        final File base; final File[] moments; final int gapMin, gapMax;
        final Handler ui = new Handler(Looper.getMainLooper()); final Random rnd = new Random();
        MediaPlayer basePlayer, momentPlayer; Surface baseSurface, momentSurface; boolean running;
        final Runnable nextMoment = this::playMoment;

        LiveBackdrop(Context c, File base, File[] moments, int gapMin, int gapMax) {
            this.base = base; this.moments = moments == null ? new File[0] : moments; this.gapMin = gapMin; this.gapMax = gapMax;
            root = new FrameLayout(c); still = new StillBackdrop(c);
            root.addView(still.view(), new FrameLayout.LayoutParams(-1, -1));
            baseView = new TextureView(c); baseView.setOpaque(false); baseView.setAlpha(0f); baseView.setSurfaceTextureListener(this);
            momentView = new TextureView(c); momentView.setOpaque(false); momentView.setAlpha(0f); momentView.setSurfaceTextureListener(this);
            root.addView(baseView, new FrameLayout.LayoutParams(-1, -1));
            root.addView(momentView, new FrameLayout.LayoutParams(-1, -1));
        }
        public View view() { return root; }
        public void start() { running = true; startBase(); scheduleMoment(); }
        public void stop() { running = false; ui.removeCallbacksAndMessages(null); releasePlayers(); baseView.setAlpha(0f); momentView.setAlpha(0f); }
        public void release() { stop(); still.release(); }

        void startBase() {
            if (!running || basePlayer != null || baseSurface == null || base == null) return;
            try {
                MediaPlayer mp = new MediaPlayer(); basePlayer = mp;
                mp.setDataSource(base.getPath()); mp.setSurface(baseSurface); mp.setLooping(true); mp.setVolume(0f, 0f);
                mp.setOnPreparedListener(p -> { if (basePlayer != p) return; p.start(); baseView.animate().alpha(1f).setDuration(400).start(); });
                mp.setOnErrorListener((p, w, x) -> { Log.w(TAG, "base clip error " + w + "/" + x); releasePlayers(); return true; });
                mp.prepareAsync();
            } catch (Exception e) { Log.e(TAG, "base clip", e); releasePlayers(); }
        }
        void scheduleMoment() { if (!running || moments.length == 0) return; int s = gapMin + rnd.nextInt(Math.max(1, gapMax - gapMin + 1)); ui.postDelayed(nextMoment, s * 1000L); }
        void playMoment() {
            if (!running || momentSurface == null || momentPlayer != null) { scheduleMoment(); return; }
            File clip = moments[rnd.nextInt(moments.length)];
            try {
                MediaPlayer mp = new MediaPlayer(); momentPlayer = mp;
                mp.setDataSource(clip.getPath()); mp.setSurface(momentSurface); mp.setLooping(false); mp.setVolume(0f, 0f);
                mp.setOnPreparedListener(p -> { if (momentPlayer != p) return; p.start(); momentView.animate().alpha(1f).setDuration(250).start(); });
                mp.setOnCompletionListener(p -> momentView.animate().alpha(0f).setDuration(250).withEndAction(() -> { if (momentPlayer == p) { p.release(); momentPlayer = null; } scheduleMoment(); }).start());
                mp.setOnErrorListener((p, w, x) -> { p.release(); if (momentPlayer == p) momentPlayer = null; momentView.setAlpha(0f); scheduleMoment(); return true; });
                mp.prepareAsync();
            } catch (Exception e) { Log.e(TAG, "moment", e); if (momentPlayer != null) momentPlayer.release(); momentPlayer = null; scheduleMoment(); }
        }
        void releasePlayers() {
            if (basePlayer != null) { try { basePlayer.release(); } catch (Exception ignored) {} basePlayer = null; }
            if (momentPlayer != null) { try { momentPlayer.release(); } catch (Exception ignored) {} momentPlayer = null; }
        }
        @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
            if (baseView.getSurfaceTexture() == st) { baseSurface = new Surface(st); startBase(); } else { momentSurface = new Surface(st); }
        }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
            if (baseView.getSurfaceTexture() == st) { releasePlayers(); if (baseSurface != null) baseSurface.release(); baseSurface = null; }
            else { if (momentPlayer != null) { momentPlayer.release(); momentPlayer = null; } if (momentSurface != null) momentSurface.release(); momentSurface = null; }
            return true;
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
        @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
    }
}
