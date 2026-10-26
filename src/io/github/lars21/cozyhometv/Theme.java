package io.github.lars21.cozyhometv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.view.SoundEffectConstants;
import android.widget.TextView;

/**
 * A pluggable look (Settings > Theme). A Cozy pack, when the Cozy look is on, replaces the theme's background but keeps
 * the rest of the theme.
 *
 * To add a theme: implement this interface (see Themes.java for six examples), put its art in assets/themes/<id>/
 * (including a 320x180 thumb.jpg for the picker), credit the art and fonts in CREDITS.md, then add it to Themes.ALL.
 * A theme only describes things; HomeActivity and the screensaver do the work, so focus, memory handling and app logic
 * are the same for every theme.
 *
 * Rules for themes: at most two full-screen pictures decoded at a time (at screen size), motion only through
 * Ui.Ambient (it pauses when an app opens and follows Settings > Little movements), fonts must be OFL or similar with
 * their licence file, no audio files (use Synth). All sizes are in the 1920x1080 design space.
 */
interface Theme {
    /** Stable id stored in the settings (never change it once released). */
    String id();
    /** Name shown in Settings. */
    String label();
    /** One line for the theme picker. */
    String description();
    /** Asset path of the picker thumbnail. */
    default String previewAsset() { return "themes/" + id() + "/thumb.jpg"; }

    /**
     * The full-screen background layer, or null when the theme only uses baseDrawable(). It should include its own
     * motion layer (if any), started in start() and stopped in stop(). Must free everything in release().
     * @param frost gets a small blurred copy of the current picture for the frosted panels (may be null).
     */
    Scene.Backdrop background(Context c, int screenW, int screenH, Prefs prefs, Wallpapers.FrostSink frost);

    /** Painted behind the background layer (and alone when there is none). Must be cheap: no bitmaps. */
    Drawable baseDrawable();

    Layout layout();
    CardStyle cards();
    /** Font for the clock (and the screensaver clock). */
    Typeface clockFont(Context c);
    /** Font for app names and section labels. */
    default Typeface labelFont(Context c) { return Typeface.SANS_SERIF; }
    Sounds sounds();

    /** Colours, glow and spacing of the clock and date. s = design-to-device scale. */
    default void styleClock(TextView clock, TextView date, float s) {
        clock.setTextColor(layout().textColor); clock.setShadowLayer(8 * s, 0, 2 * s, 0x80000000); clock.setLetterSpacing(0);
        date.setTextColor(layout().dimColor); date.setShadowLayer(6 * s, 0, 2 * s, 0x80000000); date.setLetterSpacing(0); date.setAllCaps(false);
    }
    /** Called when the clock shows a new minute (not on the first draw). motion = Little movements is on and home is visible. */
    default void onClockChanged(TextView clock, TextView date, float s, boolean motion) {}
    /**
     * Optional animated decoration behind the dock row (e.g. a string of fairy lights). Sized to the dock row; the
     * dock tiles start layout().dockDecorHeight below its top.
     */
    default Ui.Ambient dockDecor(Context c, float s) { return null; }

    /** Where things go on the home screen. */
    final class Layout {
        /** Clock in the top-right corner (true) or a big clock top-left (false). */
        boolean clockRight;
        float clockSize = 92, dateSize = 28;
        int textColor = 0xFFF4F1EA, dimColor = 0xB3F4F1EA;
        /** Empty space between the header and the dock row; pushes the dock towards the bottom of the screen. */
        float dockSpacer = 56;
        /** Dock tile width (height follows from the 16:9 picture plus the card's frame insets) and gap. */
        float dockW = 300, dockGap = 24;
        /** Extra room above the dock tiles for dockDecor(). */
        float dockDecorHeight = 0;
        int gridCols = 6; float gridGap = 24;
        /** Space between the dock and the all-apps grid (a big gap keeps the grid just below the screen edge). */
        float gridTopGap = 20;
        boolean dockLabel = true, gridLabel = true; float labelSize = 26, nameSize = 22; boolean upperCase;
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
        /** Colour of the shadow under a focused tile; a bright colour reads as a glow (Android 9+). */
        int focusShadowColor = 0xFF000000;
        int cardColor = 0xFF262B36;
        float ringWidth = 4; int ringColor = 0xFFFFFFFF;
        float unfocusedAlpha = .86f;
        /** App name under the tile: always, only under the focused tile, or never (labelInside). */
        boolean showName = true, namesOnFocusOnly;
        int nameColor = 0, nameFocusColor = 0; // 0 = layout text colours
        /** Degrees the focused tile tilts towards the direction you came from, then settles (0 = off). */
        float tiltDeg;
        /** A light sweep and gloss over the focused tile. */
        boolean shine; int shineColor = 0x30FFFFFF;
        long focusMs = 150;
        // ---- frames (polaroid, cartridge, HUD panel)
        /** A frame around the picture (0 = none): the picture sits inside the card with these insets. */
        int frameColor = 0; float insetX, insetTop, insetBottom;
        /** App name printed in the frame's bottom strip. */
        boolean labelInside; int labelInsideColor = 0xFF333333; float labelInsideSize = 22;
        /** Each app hangs at its own slight angle (degrees) and straightens when focused. */
        float restTilt;
        /** Outline (unfocused / focused), HUD corner brackets, cartridge ridges, polaroid peg (dock only). */
        float strokeWidth; int strokeColor, strokeFocusColor;
        boolean brackets; int bracketColor = 0xFF7FF3FF; float bracketLen = 22;
        boolean ridges, peg;
    }

    /** UI sounds. pcm == null: the TV's own sound effects. Otherwise a tiny tone made in code (Synth). */
    final class Sounds {
        int focus = AudioManager.FX_FOCUS_NAVIGATION_DOWN, select = SoundEffectConstants.CLICK; float volume = .5f;
        short[] focusPcm, selectPcm;
    }
}
