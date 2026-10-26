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

    static final Theme CLEAN = new Clean(), GLASS = new Glass(), COZY = new CozyHome(), NEON = new NeonCity(), STARSHIP = new Starship(), ARCADE = new Arcade();
    static final Theme[] ALL = {CLEAN, GLASS, COZY, NEON, STARSHIP, ARCADE};

    static Theme get(String id) {
        if ("classic".equals(id)) return CLEAN; // 1.1 preview name
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

    /**
     * Glass (Aerial): full-screen photos that slowly crossfade, a clock in the top-right corner, a dock of large
     * rounded tiles over a frosted strip, and the all-apps grid below it. Focus: scale up, soft shadow, a small tilt
     * that settles, and a light sweep.
     */
    static final class Glass implements Theme {
        final Layout layout = new Layout(); final CardStyle cards = new CardStyle(); final Sounds sounds = new Sounds();
        Glass() {
            layout.clockRight = true; layout.clockSize = 46; layout.dateSize = 22;
            layout.dockSpacer = 590; layout.gridTopGap = 80; layout.dockW = 306; layout.dockGap = 28;
            layout.gridCols = 6; layout.gridGap = 28; layout.dockLabel = false; layout.gridLabel = true;
            layout.frostPanel = true; layout.panelTint = 0x52101318; layout.panelRadius = 36;
            layout.bottomShade = true; layout.scrimMax = .45f;
            cards.radius = 22; cards.focusScale = 1.10f; cards.lift = 6; cards.restElevation = 2; cards.focusElevation = 30;
            cards.ringWidth = 0; cards.unfocusedAlpha = .92f; cards.namesOnFocusOnly = true; cards.tiltDeg = 5; cards.shine = true;
            cards.focusMs = 240;
        }
        public String id() { return "glass"; }
        public String label() { return "Glass (Aerial)"; }
        public String description() { return "Cozy photos that slowly change, rounded tiles over frosted glass."; }
        public Scene.Backdrop background(Context c, int w, int h, Prefs p, Wallpapers.FrostSink frost) {
            Wallpapers wp = new Wallpapers(c, w, h, p.wallpaperSeconds() * 1000L, p.motion(), frost);
            if (wp.isEmpty()) { wp.release(); return null; }
            return wp;
        }
        public Drawable baseDrawable() { return new Ui.Night(); }
        public Layout layout() { return layout; }
        public CardStyle cards() { return cards; }
        public Typeface clockFont(Context c) { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }
        public Sounds sounds() { return sounds; }
    }

    /** Cozy Home: an illustrated living room for the time of day, polaroid app cards pegged to twinkling fairy lights. */
    static final class CozyHome implements Theme {
        final Layout layout = new Layout(); final CardStyle cards = new CardStyle(); final Sounds sounds = new Sounds();
        CozyHome() {
            layout.clockRight = false; layout.clockSize = 96; layout.dateSize = 34; layout.textColor = 0xFFFFF4E2; layout.dimColor = 0xE6FFF0DA;
            layout.dockSpacer = 470; layout.dockDecorHeight = 46; layout.dockW = 262; layout.dockGap = 44; layout.gridTopGap = 70;
            layout.gridCols = 6; layout.gridGap = 30; layout.dockLabel = false; layout.labelSize = 36; layout.nameSize = 28;
            layout.frostPanel = true; layout.panelTint = 0x66301A0C; layout.panelRadius = 30; layout.bottomShade = true; layout.scrimMax = .45f;
            cards.radius = 4; cards.cardColor = 0xFFF8F2E6; cards.frameColor = 0xFFF8F2E6; cards.insetX = 12; cards.insetTop = 12; cards.insetBottom = 50;
            cards.labelInside = true; cards.labelInsideColor = 0xFF4A3A2C; cards.labelInsideSize = 30; cards.showName = false;
            cards.restTilt = 3; cards.peg = true; cards.ringWidth = 0; cards.unfocusedAlpha = 1f;
            cards.focusScale = 1.08f; cards.lift = 10; cards.restElevation = 6; cards.focusElevation = 26; cards.focusShadowColor = 0xFF2A1406; cards.focusMs = 220;
        }
        public String id() { return "cozy"; }
        public String label() { return "Cozy Home"; }
        public String description() { return "An illustrated living room that follows the time of day, polaroids on fairy lights."; }
        public Scene.Backdrop background(Context c, int w, int h, Prefs p, Wallpapers.FrostSink frost) {
            return new ThemeArt.ArtBackdrop(c, w, h, () -> "themes/cozy/" + ThemeArt.period() + ".jpg", new ThemeArt.Motes(c), p.motion(), frost);
        }
        public Drawable baseDrawable() { return vertical(0xFF2A1C14, 0xFF140C08); }
        public Layout layout() { return layout; }
        public CardStyle cards() { return cards; }
        public Typeface clockFont(Context c) { return font(c, "caveat.ttf", 0); }
        public Typeface labelFont(Context c) { return font(c, "caveat.ttf", 0); }
        public Sounds sounds() { return sounds; }
        public void styleClock(TextView clock, TextView date, float s) {
            Theme.super.styleClock(clock, date, s);
            clock.setShadowLayer(12 * s, 0, 3 * s, 0xB0301808); date.setShadowLayer(8 * s, 0, 2 * s, 0xB0301808);
        }
        public Ui.Ambient dockDecor(Context c, float s) { return new ThemeArt.FairyLights(c, 22 * s, 14 * s); }
    }

    /** Neon City: rainy neon skyline, glowing outlined tiles, a mono clock that glitches when the minute changes. */
    static final class NeonCity implements Theme {
        final Layout layout = new Layout(); final CardStyle cards = new CardStyle(); final Sounds sounds = new Sounds();
        final Handler ui = new Handler(Looper.getMainLooper()); final Random rnd = new Random();
        NeonCity() {
            layout.clockRight = true; layout.clockSize = 64; layout.dateSize = 22; layout.textColor = 0xFFE9FDFF; layout.dimColor = 0xFFFF6AD5;
            layout.dockSpacer = 570; layout.dockW = 300; layout.dockGap = 32; layout.gridTopGap = 70; layout.gridGap = 28;
            layout.dockLabel = false; layout.labelSize = 24; layout.nameSize = 20; layout.upperCase = true;
            layout.frostPanel = true; layout.panelTint = 0x80080512; layout.panelRadius = 10; layout.bottomShade = true; layout.scrimMax = .5f;
            cards.radius = 8; cards.cardColor = 0xE00B0A16; cards.ringWidth = 0; cards.strokeWidth = 2; cards.strokeColor = 0x8000E5FF; cards.strokeFocusColor = 0xFFFF2BD6;
            cards.focusShadowColor = 0xFFFF2BD6; cards.restElevation = 0; cards.focusElevation = 40; cards.focusScale = 1.07f; cards.lift = 4;
            cards.unfocusedAlpha = .82f; cards.nameColor = 0x9900E5FF; cards.nameFocusColor = 0xFFFF8BE8; cards.focusMs = 160;
            sounds.focusPcm = Synth.tone(1900, 1700, 22, 's', .18f); sounds.selectPcm = Synth.tone(1200, 500, 70, 't', .25f);
        }
        public String id() { return "neon"; }
        public String label() { return "Neon City"; }
        public String description() { return "Cyberpunk: a rainy neon skyline, glowing tiles, a glitchy clock."; }
        public Scene.Backdrop background(Context c, int w, int h, Prefs p, Wallpapers.FrostSink frost) {
            float[][] signs = {{.146f, .318f, .200f, .516f}, {.151f, .083f, .186f, .226f}, {.571f, .363f, .589f, .455f}, {.742f, .316f, .771f, .455f}};
            return new ThemeArt.ArtBackdrop(c, w, h, () -> "themes/neon/bg.jpg", new ThemeArt.NeonRain(c, signs), p.motion(), frost);
        }
        public Drawable baseDrawable() { return vertical(0xFF0A0818, 0xFF04030A); }
        public Layout layout() { return layout; }
        public CardStyle cards() { return cards; }
        public Typeface clockFont(Context c) { return font(c, "fonts/sharetechmono.ttf", 0); }
        public Typeface labelFont(Context c) { return font(c, "fonts/sharetechmono.ttf", 0); }
        public Sounds sounds() { return sounds; }
        public void styleClock(TextView clock, TextView date, float s) {
            clock.setTextColor(layout.textColor); clock.setShadowLayer(14 * s, 0, 0, 0xDD00E5FF); clock.setTranslationX(0); clock.setLetterSpacing(.05f);
            date.setTextColor(layout.dimColor); date.setShadowLayer(10 * s, 0, 0, 0xCCFF2BD6); date.setLetterSpacing(.18f); date.setAllCaps(true);
        }
        public void onClockChanged(final TextView clock, final TextView date, final float s, boolean motion) {
            if (!motion) return;
            for (int i = 0; i < 7; i++) {
                final int k = i;
                ui.postDelayed(() -> {
                    if (k == 6) { styleClock(clock, date, s); return; }
                    float dx = (rnd.nextFloat() - .5f) * 14 * s; boolean m = k % 2 == 0;
                    clock.setTranslationX(dx); clock.setShadowLayer(1, (m ? 5 : -5) * s, 0, m ? 0xFFFF2BD6 : 0xFF00E5FF);
                }, i * 55L);
            }
        }
    }

    /** Starship: a cockpit window with a slow starfield, HUD tiles with thin cyan lines and corner brackets. */
    static final class Starship implements Theme {
        final Layout layout = new Layout(); final CardStyle cards = new CardStyle(); final Sounds sounds = new Sounds();
        Starship() {
            layout.clockRight = true; layout.clockSize = 52; layout.dateSize = 18; layout.textColor = 0xFFCFF8FF; layout.dimColor = 0xB37FF3FF;
            layout.dockSpacer = 590; layout.dockW = 300; layout.dockGap = 32; layout.gridTopGap = 70; layout.gridGap = 28;
            layout.dockLabel = false; layout.labelSize = 20; layout.nameSize = 16; layout.upperCase = true;
            layout.frostPanel = true; layout.panelTint = 0x7005101A; layout.panelRadius = 6; layout.bottomShade = true; layout.scrimMax = .5f;
            cards.radius = 3; cards.cardColor = 0xAA06141E; cards.ringWidth = 0; cards.strokeWidth = 1.5f; cards.strokeColor = 0x5533D6FF; cards.strokeFocusColor = 0xFF7FF3FF;
            cards.brackets = true; cards.bracketColor = 0xFF7FF3FF; cards.bracketLen = 22;
            cards.focusShadowColor = 0xFF00B4FF; cards.restElevation = 0; cards.focusElevation = 26; cards.focusScale = 1.06f; cards.lift = 4;
            cards.unfocusedAlpha = .8f; cards.shine = true; cards.shineColor = 0x2A7FF3FF; cards.nameColor = 0x997FF3FF; cards.nameFocusColor = 0xFFCFF8FF; cards.focusMs = 200;
            sounds.focusPcm = Synth.tone(760, 1300, 38, 's', .16f); sounds.selectPcm = Synth.tone(1300, 2300, 80, 's', .2f);
        }
        public String id() { return "starship"; }
        public String label() { return "Starship"; }
        public String description() { return "Sci-fi: a cockpit window on drifting stars, HUD panels."; }
        public Scene.Backdrop background(Context c, int w, int h, Prefs p, Wallpapers.FrostSink frost) {
            return new ThemeArt.ArtBackdrop(c, w, h, () -> "themes/starship/bg.jpg", new ThemeArt.Starfield(c, new float[]{.07f, .17f, .93f, .62f}), p.motion(), frost);
        }
        public Drawable baseDrawable() { return vertical(0xFF050A12, 0xFF020407); }
        public Layout layout() { return layout; }
        public CardStyle cards() { return cards; }
        public Typeface clockFont(Context c) { return font(c, "fonts/orbitron.ttf", 600); }
        public Typeface labelFont(Context c) { return font(c, "fonts/orbitron.ttf", 500); }
        public Sounds sounds() { return sounds; }
        public void styleClock(TextView clock, TextView date, float s) {
            clock.setTextColor(layout.textColor); clock.setShadowLayer(12 * s, 0, 0, 0x9900C8FF); clock.setLetterSpacing(.08f);
            date.setTextColor(layout.dimColor); date.setShadowLayer(8 * s, 0, 0, 0x8800C8FF); date.setLetterSpacing(.3f); date.setAllCaps(true);
        }
    }

    /** Retro Arcade: pixel-art arcade room with scanlines, apps as game cartridges, a pixel font. */
    static final class Arcade implements Theme {
        final Layout layout = new Layout(); final CardStyle cards = new CardStyle(); final Sounds sounds = new Sounds();
        Arcade() {
            layout.clockRight = true; layout.clockSize = 40; layout.dateSize = 14; layout.textColor = 0xFFFFF1A8; layout.dimColor = 0xFFE9E3FF;
            layout.dockSpacer = 520; layout.dockW = 290; layout.dockGap = 36; layout.gridTopGap = 70; layout.gridGap = 30;
            layout.dockLabel = false; layout.labelSize = 16; layout.nameSize = 12; layout.upperCase = true;
            layout.frostPanel = true; layout.panelTint = 0x9A140A26; layout.panelRadius = 4; layout.bottomShade = true; layout.scrimMax = .5f;
            cards.radius = 8; cards.cardColor = 0xFF4B4A5A; cards.frameColor = 0xFF4B4A5A; cards.insetX = 18; cards.insetTop = 34; cards.insetBottom = 42;
            cards.labelInside = true; cards.labelInsideColor = 0xFFF2F2F2; cards.labelInsideSize = 13; cards.showName = false; cards.ridges = true;
            cards.ringWidth = 0; cards.strokeWidth = 3; cards.strokeColor = 0; cards.strokeFocusColor = 0xFFFFE14A;
            cards.focusScale = 1.08f; cards.lift = 10; cards.restElevation = 4; cards.focusElevation = 18; cards.unfocusedAlpha = .9f; cards.focusMs = 120;
            sounds.focusPcm = Synth.tone(880, 880, 34, 'q', .12f); sounds.selectPcm = Synth.tone(660, 1320, 90, 'q', .14f);
        }
        public String id() { return "arcade"; }
        public String label() { return "Retro Arcade"; }
        public String description() { return "Pixel art with scanlines, apps as game cartridges."; }
        public Scene.Backdrop background(Context c, int w, int h, Prefs p, Wallpapers.FrostSink frost) {
            return new ThemeArt.ArtBackdrop(c, w, h, () -> "themes/arcade/bg.jpg", new ThemeArt.Crt(c, new float[]{.78f, .09f, .97f, .40f}), p.motion(), frost);
        }
        public Drawable baseDrawable() { return vertical(0xFF1A0E2E, 0xFF0A0614); }
        public Layout layout() { return layout; }
        public CardStyle cards() { return cards; }
        public Typeface clockFont(Context c) { return font(c, "fonts/pressstart2p.ttf", 0); }
        public Typeface labelFont(Context c) { return font(c, "fonts/pressstart2p.ttf", 0); }
        public Sounds sounds() { return sounds; }
        public void styleClock(TextView clock, TextView date, float s) {
            clock.setTextColor(layout.textColor); clock.setShadowLayer(0.01f, 4 * s, 4 * s, 0xFF7A2A9A); clock.setLetterSpacing(0);
            date.setTextColor(layout.dimColor); date.setShadowLayer(0.01f, 2 * s, 2 * s, 0xFF3A1A5A); date.setLetterSpacing(.05f); date.setAllCaps(true);
        }
    }
}
