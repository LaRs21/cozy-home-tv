package io.github.lars21.cozyhometv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** The built-in themes. Add new ones to ALL; the first one is the default for a fresh install. */
final class Themes {
    private Themes() {}

    static final Theme CLEAN = new Clean();
    /** The picture themes (assets/themes/<id>/): a painting with 4 app spots, music and gentle motion. */
    static final PictureTheme SKY = new PictureTheme("sky_kingdom", "Sky Kingdom", "A sky ship, floating islands and waterfalls above the clouds."),
        WHALE = new PictureTheme("moonlit_whale", "Moonlit Whale", "A sky whale, a moonlit crystal and a lighthouse over the clouds."),
        DESERT = new PictureTheme("desert_kingdom", "Desert Kingdom", "A golden desert city with fountains, palms and banners."),
        TREE = new PictureTheme("world_tree", "World Tree", "A sunlit forest valley, a glowing tree, a bell and a crystal spring.");
    static final Theme[] ALL = {CLEAN, SKY, WHALE, DESERT, TREE};

    static Theme get(String id) {
        if ("classic".equals(id)) return CLEAN; // 1.1 preview name; removed themes (glass, cozy, neon, starship, arcade) fall back to Clean
        for (Theme t : ALL) if (t.id().equals(id)) return t;
        return ALL[0];
    }

    // ---- fonts (bundled, OFL; see assets/fonts/*-OFL.txt)
    static final Map<String, Typeface> FONTS = new HashMap<>();
    static Typeface font(Context c, String asset, int weight) {
        String k = asset + "@" + weight; Typeface t = FONTS.get(k); if (t != null) return t;
        try {
            Typeface.Builder b = new Typeface.Builder(c.getAssets(), asset);
            if (weight > 0) b.setFontVariationSettings("'wght' " + weight);
            t = b.build();
        } catch (Exception e) { t = null; }
        if (t == null) t = Typeface.DEFAULT;
        FONTS.put(k, t); return t;
    }

    static Drawable vertical(int top, int bottom) { return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom}); }

    /**
     * Clean (default): the look of a calm, modern TV home. A big landscape photo that changes every 45 s with a slow
     * crossfade, a thin clock top-right, and one frosted dock at the bottom (favourites, All apps, Settings) with the
     * focused name under it. All apps opens as a sheet over the photo. The screen itself is built by CleanHome.
     */
    static final class Clean implements Theme {
        static final String WALLS = "themes/clean/walls";
        final Layout layout = new Layout(); final CardStyle cards = new CardStyle(); final Sounds sounds = new Sounds();
        Clean() { layout.textColor = 0xFFFFFFFF; layout.dimColor = 0xDDFFFFFF; }
        public String id() { return "clean"; }
        public String label() { return "Clean"; }
        public String description() { return "Swiss landscapes, a frosted dock and a thin clock. Simple and fast."; }
        public Scene.Backdrop background(Context c, int w, int h, Prefs p, Wallpapers.FrostSink frost) {
            long every = p.wallpaperSeconds() * 1000L; // Settings > Change picture (Alpine used 45 s)
            Wallpapers wp = new Wallpapers(c, WALLS, w, h, every, false, null); wp.fadeMs = 1600;
            if (wp.isEmpty()) { wp.release(); return null; }
            return wp;
        }
        public Drawable baseDrawable() { return new android.graphics.drawable.ColorDrawable(0xFF000000); }
        public Layout layout() { return layout; }
        public CardStyle cards() { return cards; }
        public Typeface clockFont(Context c) { return Typeface.create("sans-serif-thin", Typeface.NORMAL); }
        public Sounds sounds() { return sounds; }
    }
}
