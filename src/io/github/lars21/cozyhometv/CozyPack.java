package io.github.lars21.cozyhometv;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A "Cozy pack": the user's own pictures, music and card art, read from a folder on the TV.
 *
 * Folder (first one that exists and is readable wins):
 *   /sdcard/CozyHome/                                         (needs the all-files permission, granted by scripts/push-cozy-pack.sh)
 *   /sdcard/Android/data/io.github.lars21.cozyhometv/files/CozyHome/   (no permission needed)
 *
 * config.json is optional. Without it, files named morning/day/evening/night.(jpg|png|webp) or room.(jpg|png|webp)
 * and music.(mp3|ogg|m4a) are picked up automatically. See docs/cozy-pack.md for the full format.
 */
final class CozyPack {
    static final String TAG = "CozyPack";
    static final String DIR_NAME = "CozyHome";
    static final String[] PERIODS = {"morning", "day", "evening", "night"};
    private static final String[] IMG_EXT = {".jpg", ".jpeg", ".png", ".webp"};
    private static final String[] AUDIO_EXT = {".mp3", ".ogg", ".m4a"};

    File dir;
    String title = "Cozy Home";
    final Map<String, File> scenes = new HashMap<>();   // period -> image
    /** Start hour of each period (24 h clock). */
    int morningAt = 6, dayAt = 11, eveningAt = 17, nightAt = 20;
    File music; int defaultVolume = 40; boolean musicEnabled = true;
    final Map<String, File> cards = new HashMap<>();    // package -> 16:9 card image
    String[] favorites;                                 // optional seed for the favourites row
    boolean fireflies = true;
    /** Optional live background (experimental, see docs/live-background.md). */
    final Map<String, File> liveBase = new HashMap<>();             // period -> looping clip
    final Map<String, File[]> liveMoments = new HashMap<>();        // period -> one-shot clips
    int liveGapMin = 25, liveGapMax = 45; boolean liveEnabled = false;
    long stamp;                                         // newest mtime seen, for reload checks
    String problem;                                     // human-readable reason when nothing usable was found

    boolean hasScene() { return !scenes.isEmpty(); }

    static File[] candidateDirs(Context c) {
        File ext = c.getExternalFilesDir(null);
        return new File[]{ new File(Environment.getExternalStorageDirectory(), DIR_NAME), ext == null ? null : new File(ext, DIR_NAME) };
    }

    static File findDir(Context c) {
        // A folder only counts if we can actually read a file in it (without permission Android may show it empty).
        for (File d : candidateDirs(c)) {
            if (d == null || !d.isDirectory()) continue;
            File[] fs = d.listFiles();
            if (fs != null) for (File f : fs) if (f.isFile() && f.canRead()) return d;
        }
        return null;
    }

    /** Cheap check: newest modification time inside the pack folder (top level + config). */
    static long stampOf(File d) {
        if (d == null) return 0;
        long s = d.lastModified();
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) s = Math.max(s, f.lastModified());
        return s;
    }

    static CozyPack load(Context c) {
        CozyPack p = new CozyPack();
        p.dir = findDir(c);
        if (p.dir == null) { p.problem = "No CozyHome folder found (or no permission to read it)."; return p; }
        p.stamp = stampOf(p.dir);
        try {
            File cfg = new File(p.dir, "config.json");
            JSONObject j = cfg.isFile() ? new JSONObject(readText(cfg)) : new JSONObject();
            p.title = j.optString("title", p.title);
            JSONObject sc = j.optJSONObject("scenes");
            String single = j.optString("image", null);
            if (sc != null) for (String per : PERIODS) { File f = p.file(sc.optString(per, null)); if (f != null) p.scenes.put(per, f); }
            if (p.scenes.isEmpty() && single != null) { File f = p.file(single); if (f != null) for (String per : PERIODS) p.scenes.put(per, f); }
            if (p.scenes.isEmpty()) { // auto-detect
                for (String per : PERIODS) { File f = p.find(per); if (f == null && per.equals("day")) f = p.find("afternoon"); if (f != null) p.scenes.put(per, f); }
                if (p.scenes.isEmpty()) { File f = p.find("room"); if (f == null) f = p.find("background"); if (f != null) for (String per : PERIODS) p.scenes.put(per, f); }
            }
            // Fill gaps: a missing period borrows the nearest earlier one (so 2 or 3 images also work).
            if (!p.scenes.isEmpty()) for (int pass = 0; pass < 2; pass++) for (int i = 0; i < 4; i++) {
                String per = PERIODS[i]; if (p.scenes.containsKey(per)) continue;
                File prev = p.scenes.get(PERIODS[(i + 3) % 4]); if (prev != null) p.scenes.put(per, prev);
            }
            JSONObject t = j.optJSONObject("times");
            if (t != null) { p.morningAt = t.optInt("morning", p.morningAt); p.dayAt = t.optInt("day", p.dayAt); p.eveningAt = t.optInt("evening", p.eveningAt); p.nightAt = t.optInt("night", p.nightAt); }
            Object m = j.opt("music");
            if (m instanceof String) p.music = p.file((String) m);
            else if (m instanceof JSONObject) {
                JSONObject mj = (JSONObject) m;
                p.music = p.file(mj.optString("file", null));
                p.defaultVolume = Math.max(0, Math.min(100, mj.optInt("volume", p.defaultVolume)));
                p.musicEnabled = mj.optBoolean("enabled", true);
            }
            if (p.music == null && !(m instanceof JSONObject && ((JSONObject) m).has("file"))) {
                for (String n : new String[]{"music", "song", "bgm"}) { p.music = p.findAudio(n); if (p.music != null) break; }
            }
            JSONObject cd = j.optJSONObject("cards");
            if (cd != null) for (Iterator<String> it = cd.keys(); it.hasNext(); ) { String pkg = it.next(); File f = p.file(cd.optString(pkg, null)); if (f != null) p.cards.put(pkg, f); }
            File cardsDir = new File(p.dir, "cards"); File[] cfs = cardsDir.listFiles();
            if (cfs != null) for (File f : cfs) { String n = f.getName(); int dot = n.lastIndexOf('.'); if (dot > 0 && isImage(n) && !p.cards.containsKey(n.substring(0, dot))) p.cards.put(n.substring(0, dot), f); }
            JSONArray fav = j.optJSONArray("favorites");
            if (fav != null) { p.favorites = new String[fav.length()]; for (int i = 0; i < fav.length(); i++) p.favorites[i] = fav.optString(i); }
            JSONObject amb = j.optJSONObject("ambient");
            if (amb != null) p.fireflies = amb.optBoolean("fireflies", p.fireflies);
            JSONObject live = j.optJSONObject("live");
            if (live != null) {
                p.liveEnabled = live.optBoolean("enabled", true);
                p.liveGapMin = Math.max(5, live.optInt("gapMinSeconds", p.liveGapMin));
                p.liveGapMax = Math.max(p.liveGapMin, live.optInt("gapMaxSeconds", p.liveGapMax));
                for (String per : PERIODS) {
                    JSONObject lp = live.optJSONObject(per); if (lp == null) continue;
                    File base = p.file(lp.optString("base", null)); if (base != null) p.liveBase.put(per, base);
                    JSONArray mo = lp.optJSONArray("moments");
                    if (mo != null) { java.util.List<File> l = new java.util.ArrayList<>(); for (int i = 0; i < mo.length(); i++) { File f = p.file(mo.optString(i)); if (f != null) l.add(f); } p.liveMoments.put(per, l.toArray(new File[0])); }
                }
            }
            if (p.scenes.isEmpty()) p.problem = "The CozyHome folder has no pictures (morning/day/evening/night or room .jpg/.png/.webp).";
        } catch (Exception e) {
            Log.e(TAG, "config", e);
            p.problem = "config.json could not be read: " + e.getMessage();
        }
        Log.d(TAG, "pack " + p.dir + " scenes=" + p.scenes.keySet() + " music=" + p.music + " cards=" + p.cards.size());
        return p;
    }

    /** Which period is it now (given the user's override)? */
    String periodNow(String override) {
        if (override != null && !"auto".equals(override)) return override;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= nightAt || h < morningAt) return "night";
        if (h >= eveningAt) return "evening";
        if (h >= dayAt) return "day";
        return "morning";
    }

    File scene(String period) { return scenes.get(period); }

    private File file(String rel) {
        if (rel == null || rel.isEmpty() || rel.contains("..")) return null;
        File f = new File(dir, rel);
        return f.isFile() && f.canRead() ? f : null;
    }
    private File find(String base) { for (String e : IMG_EXT) { File f = file(base + e); if (f != null) return f; } return null; }
    private File findAudio(String base) { for (String e : AUDIO_EXT) { File f = file(base + e); if (f != null) return f; } return null; }
    static boolean isImage(String n) { n = n.toLowerCase(); for (String e : IMG_EXT) if (n.endsWith(e)) return true; return false; }

    static String readText(File f) throws java.io.IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) { bo.write(b, 0, n); if (bo.size() > 256 * 1024) throw new java.io.IOException("config.json is too big"); }
            return bo.toString("UTF-8");
        }
    }

    static boolean needsAllFilesAccess() { return Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager(); }
}
