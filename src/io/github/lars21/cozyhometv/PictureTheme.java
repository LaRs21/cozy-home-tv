package io.github.lars21.cozyhometv;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A picture theme: one painted 1920x1080 scene (assets/themes/<id>/bg.jpg) with 4 spots where the chosen apps are drawn
 * (slots.json), its own music (music.mp3, optional) and gentle motion. The home screen for it is PictureHome; this class
 * only describes the theme. See docs/theme-packs.md.
 *
 * theme.json and slots.json are read once, the first time the theme is used (ensure()). Nothing big is kept here: the
 * painting is decoded by the backdrop and freed with it, and the music is streamed by MediaPlayer straight from the APK.
 */
final class PictureTheme implements Theme {
    static final String TAG = "CozyPicture";
    final String id, label, description;
    final Layout layout = new Layout(); final CardStyle cards = new CardStyle(); final Sounds sounds = new Sounds();

    // from theme.json
    String picture = "bg.jpg", music, musicCredit = "", fontName = "serif";
    int musicVolume = 35, textColor = 0xFFFFFFFF, accent = 0xFFFFF0C8, sheetTint = 0xE60E1E3A; float clockSize = 44;
    // from slots.json
    final List<Slot> slots = new ArrayList<>(); float moreX = 960, moreY = 1010; final Motion motion = new Motion();
    private boolean loaded;

    /** One app spot, in the 1920x1080 design space: centre (x, y), size of the drawn face, and how it's drawn. */
    static final class Slot {
        String key = "", name = "spot", style = "glow"; float x, y, w = 100, h = 100, rot, curve, labelDy = 90; int glow = 0xFFFFF2CC; boolean round;
    }
    /** Motion layers from slots.json "motion". */
    static final class Motion {
        float[] twinkleRect; int twinkles; int twinkleColor = 0xFFFFFFFF, mistColor = 0xFFFFFFFF, lightColor = 0xFFFFD08A;
        final List<float[]> mist = new ArrayList<>(), falls = new ArrayList<>(), lights = new ArrayList<>();
    }

    PictureTheme(String id, String label, String description) {
        this.id = id; this.label = label; this.description = description;
        layout.clockRight = true; layout.clockSize = 44; layout.dateSize = 22; layout.bottomShade = false; layout.scrimMax = 0;
        cards.radius = 22; cards.ringWidth = 4;
    }

    String dir() { return "themes/" + id + "/"; }

    /** Reads theme.json and slots.json (once). Safe to call often. */
    PictureTheme ensure(Context c) {
        if (loaded) return this;
        loaded = true;
        try {
            JSONObject t = new JSONObject(read(c, dir() + "theme.json"));
            picture = t.optString("picture", picture);
            music = t.has("music") ? t.optString("music", null) : null; if (music != null && music.isEmpty()) music = null;
            musicVolume = t.optInt("musicVolume", musicVolume); musicCredit = t.optString("musicCredit", "");
            fontName = t.optString("font", fontName);
            textColor = color(t, "textColor", textColor); accent = color(t, "accent", accent); sheetTint = color(t, "sheetTint", sheetTint);
            clockSize = (float) t.optDouble("clockSize", clockSize); layout.clockSize = clockSize;
            layout.textColor = textColor; layout.dimColor = (textColor & 0x00FFFFFF) | 0xB3000000; cards.ringColor = accent;
        } catch (Exception e) { Log.e(TAG, id + "/theme.json", e); }
        try {
            JSONObject s = new JSONObject(read(c, dir() + "slots.json"));
            JSONArray a = s.getJSONArray("slots");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i); Slot sl = new Slot();
                sl.key = o.optString("key", "spot" + i); sl.name = o.optString("name", sl.key); sl.style = o.optString("style", "glow");
                sl.x = (float) o.getDouble("x"); sl.y = (float) o.getDouble("y"); sl.w = (float) o.optDouble("w", 100); sl.h = (float) o.optDouble("h", 100);
                sl.rot = (float) o.optDouble("rotate", 0); sl.curve = (float) o.optDouble("curve", 0); sl.labelDy = (float) o.optDouble("labelDy", sl.h / 2 + 40);
                sl.glow = color(o, "glow", sl.glow); sl.round = o.optBoolean("round", false);
                slots.add(sl);
            }
            JSONObject m = s.optJSONObject("more"); if (m != null) { moreX = (float) m.optDouble("x", moreX); moreY = (float) m.optDouble("y", moreY); }
            JSONObject mo = s.optJSONObject("motion");
            if (mo != null) {
                JSONObject tw = mo.optJSONObject("twinkles");
                if (tw != null) { motion.twinkleRect = floats(tw.optJSONArray("rect")); motion.twinkles = tw.optInt("count", 0); motion.twinkleColor = color(tw, "color", motion.twinkleColor); }
                list(mo.optJSONArray("mist"), motion.mist); list(mo.optJSONArray("falls"), motion.falls); list(mo.optJSONArray("lights"), motion.lights);
                motion.mistColor = color(mo, "mistColor", motion.mistColor); motion.lightColor = color(mo, "lightColor", motion.lightColor);
            }
        } catch (Exception e) { Log.e(TAG, id + "/slots.json", e); }
        return this;
    }

    static String read(Context c, String path) throws Exception {
        try (InputStream in = c.getAssets().open(path)) { ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) bo.write(b, 0, n); return bo.toString("UTF-8"); }
    }
    static int color(JSONObject o, String k, int def) { try { return o.has(k) ? Color.parseColor(o.getString(k)) : def; } catch (Exception e) { return def; } }
    static float[] floats(JSONArray a) { if (a == null) return null; float[] f = new float[a.length()]; for (int i = 0; i < f.length; i++) f[i] = (float) a.optDouble(i, 0); return f; }
    static void list(JSONArray a, List<float[]> out) { if (a == null) return; for (int i = 0; i < a.length(); i++) { float[] f = floats(a.optJSONArray(i)); if (f != null) out.add(f); } }

    // ---- music
    boolean hasMusic(Context c) {
        ensure(c); if (music == null) return false;
        try (AssetFileDescriptor fd = c.getAssets().openFd(dir() + music)) { return fd.getLength() > 0; } catch (Exception e) { return false; }
    }
    String musicKey() { return "theme:" + id; }
    /** music.mp3 is stored uncompressed in the APK (build.sh: -0 mp3), so it can be streamed from the APK file itself. */
    void setMusicSource(Context c, MediaPlayer mp) throws Exception {
        try (AssetFileDescriptor fd = c.getAssets().openFd(dir() + music)) { mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength()); }
    }

    // ---- Theme
    public String id() { return id; }
    public String label() { return label; }
    public String description() { return description; }

    /** The painting, decoded off the main thread at screen size. Used by the home screen and the screensaver. */
    public Scene.Backdrop background(Context c, int w, int h, Prefs p, Wallpapers.FrostSink frost) {
        ensure(c);
        final Scene.StillBackdrop still = new Scene.StillBackdrop(c);
        final String path = dir() + picture; final android.content.res.AssetManager am = c.getAssets();
        final Handler ui = new Handler(Looper.getMainLooper()); final boolean[] released = {false};
        new Thread(() -> {
            final Bitmap b = Scene.decodeCoverAsset(am, path, w, h, true);
            ui.post(() -> { if (released[0]) { if (b != null) b.recycle(); return; } if (b != null) { still.set(b); still.iv.setAlpha(0f); still.iv.animate().alpha(1f).setDuration(HomeActivity.dur(200)).start(); } });
        }, "picture-" + id).start();
        return new Scene.Backdrop() {
            public View view() { return still.view(); }
            public void start() {}
            public void stop() {}
            public void release() { released[0] = true; still.release(); }
        };
    }
    public Drawable baseDrawable() { return new ColorDrawable(0xFF0B1020); }
    public Layout layout() { return layout; }
    public CardStyle cards() { return cards; }
    public Typeface clockFont(Context c) { ensure(c); return Typeface.create(fontName, Typeface.NORMAL); }
    public Typeface labelFont(Context c) { ensure(c); return Typeface.create(fontName, Typeface.NORMAL); }
    public Sounds sounds() { return sounds; }
}
