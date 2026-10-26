package io.github.lars21.cozyhometv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** All user settings, in one SharedPreferences file shared by the home screen, settings screen and screensaver. */
final class Prefs {
    static final String FILE = "cozy_prefs";
    static final String MODE_DEFAULT = "default", MODE_COZY = "cozy";
    static final String[] PERIODS = {"auto", "morning", "day", "evening", "night"};

    private final SharedPreferences sp;

    Prefs(Context c) { sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    String mode() { return sp.getString("mode", MODE_DEFAULT); }
    boolean cozy() { return MODE_COZY.equals(mode()); }
    void setMode(String m) { sp.edit().putString("mode", m).apply(); }

    /** "auto" follows the clock; otherwise one of morning/day/evening/night. */
    String period() { return sp.getString("period", "auto"); }
    void setPeriod(String p) { sp.edit().putString("period", p).apply(); }

    boolean music() { return sp.getBoolean("music", true); }
    void setMusic(boolean on) { sp.edit().putBoolean("music", on).apply(); }

    /** 0..100. -1 means "not set yet": use the pack's default volume. */
    int volume() { return sp.getInt("volume", -1); }
    void setVolume(int v) { sp.edit().putInt("volume", Math.max(0, Math.min(100, v))).apply(); }

    boolean motion() { return sp.getBoolean("motion", true); }
    void setMotion(boolean on) { sp.edit().putBoolean("motion", on).apply(); }

    /** Theme id (see Themes). A fresh install starts with the first one (Clean); setup can pick another. */
    String theme() { return sp.getString("theme", Themes.ALL[0].id()); }
    void setTheme(String id) { sp.edit().putString("theme", id).apply(); }

    static final int[] WALLPAPER_SECONDS = {30, 60, 120, 300, 900, 0};
    /** How often the bundled wallpaper changes, in seconds. 0 = keep one picture. */
    int wallpaperSeconds() { return sp.getInt("wallpaperSeconds", 60); }
    void setWallpaperSeconds(int s) { sp.edit().putInt("wallpaperSeconds", Math.max(0, s)).apply(); }

    boolean sounds() { return sp.getBoolean("sounds", true); }
    void setSounds(boolean on) { sp.edit().putBoolean("sounds", on).apply(); }

    /** null = follow the system 12/24-hour setting. */
    Boolean clock24() { return sp.contains("clock24") ? sp.getBoolean("clock24", false) : null; }
    void setClock24(Boolean v) { if (v == null) sp.edit().remove("clock24").apply(); else sp.edit().putBoolean("clock24", v).apply(); }

    boolean favoritesSeeded() { return sp.getBoolean("favSeeded", false); }
    List<String> favorites() { return split(sp.getString("favorites", "")); }
    void setFavorites(List<String> pkgs) { sp.edit().putString("favorites", TextUtils.join(",", pkgs)).putBoolean("favSeeded", true).apply(); }

    Set<String> hidden() { return new LinkedHashSet<>(split(sp.getString("hidden", ""))); }
    void setHidden(Set<String> pkgs) { sp.edit().putString("hidden", TextUtils.join(",", pkgs)).apply(); }

    SharedPreferences raw() { return sp; }

    static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String x : Arrays.asList(s.split(","))) { x = x.trim(); if (!x.isEmpty() && !out.contains(x)) out.add(x); }
        return out;
    }
}
