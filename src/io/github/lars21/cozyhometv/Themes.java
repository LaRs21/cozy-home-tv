package io.github.lars21.cozyhometv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** The built-in themes. Add new ones to ALL (the first one is the default). */
final class Themes {
    private Themes() {}

    static final Theme GLASS = new Glass(), CLASSIC = new Classic();
    static final Theme[] ALL = {GLASS, CLASSIC};

    static Theme get(String id) { for (Theme t : ALL) if (t.id().equals(id)) return t; return ALL[0]; }

    /**
     * Glass (default): full-screen photos that slowly crossfade, a clock in the top-right corner, a dock of large
     * rounded tiles over a frosted strip, and the all-apps grid below it. Focus: scale up, soft shadow, a small tilt
     * that settles, and a light sweep.
     */
    static final class Glass implements Theme {
        final Layout layout = new Layout(); final CardStyle cards = new CardStyle(); final Sounds sounds = new Sounds();
        Glass() {
            layout.clockRight = true; layout.clockSize = 46; layout.dateSize = 22;
            layout.dockSpacer = 590; layout.gridTopGap = 80; layout.dockW = 306; layout.dockH = 172; layout.dockGap = 28;
            layout.gridCols = 6; layout.gridGap = 28; layout.dockLabel = false; layout.gridLabel = true;
            layout.frostPanel = true; layout.panelTint = 0x52101318; layout.panelRadius = 36;
            layout.bottomShade = true; layout.scrimMax = .45f;
            cards.radius = 22; cards.focusScale = 1.10f; cards.lift = 6; cards.restElevation = 2; cards.focusElevation = 30;
            cards.ringWidth = 0; cards.unfocusedAlpha = .92f; cards.namesOnFocusOnly = true; cards.tiltDeg = 5; cards.shine = true;
            cards.focusMs = 240;
        }
        public String id() { return "glass"; }
        public String label() { return "Glass"; }
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

    /** Classic: the 1.0 look. Dark gradient, big clock, no pictures (lightest on memory). */
    static final class Classic implements Theme {
        final Layout layout = new Layout(); final CardStyle cards = new CardStyle(); final Sounds sounds = new Sounds();
        public String id() { return "classic"; }
        public String label() { return "Classic (no pictures)"; }
        public Scene.Backdrop background(Context c, int w, int h, Prefs p, Wallpapers.FrostSink frost) { return null; }
        public Drawable baseDrawable() { return new Ui.Night(); }
        public Layout layout() { return layout; }
        public CardStyle cards() { return cards; }
        public Typeface clockFont(Context c) { return Typeface.create("sans-serif-light", Typeface.NORMAL); }
        public Sounds sounds() { return sounds; }
    }
}
