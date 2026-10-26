package io.github.lars21.cozyhometv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.view.SoundEffectConstants;

/**
 * A pluggable look for the Default mode (Cozy mode with a user pack always overrides it).
 *
 * To add a theme: implement this interface (see {@link Themes.Glass} for a full example), then add it to
 * {@link Themes#ALL}. It shows up in Settings > Theme automatically. A theme only describes things; HomeActivity
 * and the screensaver do the work, so focus, memory handling and app logic stay the same for every theme.
 *
 * All sizes are in the 1920x1080 design space (scaled to the real screen).
 */
interface Theme {
    /** Stable id stored in the settings (never change it once released). */
    String id();
    /** Name shown in Settings. */
    String label();

    /**
     * The full-screen background layer, or null when the theme only uses {@link #baseDrawable()}.
     * Must decode at most two pictures at a time, at screen size, and free everything in release().
     * @param frost gets a small blurred copy of the current picture for the frosted panels (may be null).
     */
    Scene.Backdrop background(Context c, int screenW, int screenH, Prefs prefs, Wallpapers.FrostSink frost);

    /** Painted behind the background layer (and alone when there is none). Must be cheap: no bitmaps. */
    Drawable baseDrawable();

    Layout layout();
    CardStyle cards();
    /** Font for the clock (and the screensaver clock). */
    Typeface clockFont(Context c);
    Sounds sounds();

    /** Where things go on the home screen. */
    final class Layout {
        /** Clock in the top-right corner (true) or a big clock top-left (false). */
        boolean clockRight;
        float clockSize = 92, dateSize = 28;
        /** Empty space between the header and the dock row; pushes the dock towards the bottom of the screen. */
        float dockSpacer = 56;
        /** Favourites ("dock") tiles. */
        float dockW = 300, dockH = 169, dockGap = 24;
        int gridCols = 6; float gridGap = 24;
        /** Space between the dock and the all-apps grid (a big gap keeps the grid just below the screen edge). */
        float gridTopGap = 20;
        boolean dockLabel = true, gridLabel = true;
        /** A frosted panel (blurred background + tint) behind the dock and the all-apps grid. */
        boolean frostPanel; int panelTint = 0x47000000; float panelRadius = 28;
        /** A soft dark gradient over the bottom of the background. */
        boolean bottomShade;
        /** How much the background dims when you scroll down to all apps (0..1). */
        float scrimMax;
    }

    /** How app tiles look and react to focus. */
    final class CardStyle {
        float radius = 14, focusScale = 1.08f, lift = 4, restElevation = 4, focusElevation = 16;
        float ringWidth = 4; int ringColor = 0xFFFFFFFF;
        float unfocusedAlpha = .86f;
        /** App name only under the focused tile (keeps the screen calm). */
        boolean namesOnFocusOnly;
        /** Degrees the focused tile tilts towards the direction you came from, then settles (0 = off). */
        float tiltDeg;
        /** A light sweep and gloss over the focused tile. */
        boolean shine;
        /** Focus animation length in ms. */
        long focusMs = 150;
    }

    /** UI sounds (played through the TV's own sound effects, respecting its setting and Settings > Navigation sounds). */
    final class Sounds {
        int focus = AudioManager.FX_FOCUS_NAVIGATION_DOWN, select = SoundEffectConstants.CLICK; float volume = .5f;
    }
}
