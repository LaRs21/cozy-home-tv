package io.github.lars21.cozyhometv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Clean theme's home screen: a full-screen photo, a thin clock top-right, one frosted dock at the bottom
 * (favourites, then All apps and Settings) with the focused item's name under it, and an All apps sheet that fades in
 * over the photo. The visual design is inspired by the "Alpine Home" TV launcher (see CREDITS.md); the
 * code is our own.
 *
 * Sizes are given in "dp" of a 960x540 dp screen (1080p at xhdpi) and mapped to the 1920x1080 design space, so
 * dp(1) = 2 design px. Only view properties animate on focus (scale, translation, alpha); nothing relayouts.
 */
final class CleanHome {
    // dock: app tiles 120x68 dp, square buttons 68 dp, 14 dp apart; frosted pill r=26 dp
    static final float DOCK_W = 120, DOCK_H = 68, BTN = 68, GAP = 14, RADIUS = 12, FOCUS_SCALE = 1.16f;
    // all apps: 6 per row, 128x72 dp tiles
    static final int COLS = 6; static final float GRID_W = 128, GRID_H = 72;
    static final long FOCUS_MS = 160, LABEL_MS = 200, SHEET_MS = 180;
    static final int TILE_BG = 0xFF20262C, BTN_BG = 0x40FFFFFF, RING = 0xEEFFFFFF;

    final HomeActivity a;
    final FrameLayout root; final View topShade, bottomShade;
    final LinearLayout clockBox; final TextView clock, date, caption, dockLabel;
    final LinearLayout dock; View allBtn, inputsBtn, gearBtn;
    final FrameLayout sheet; final ScrollView sheetScroll; final LinearLayout grid; final TextView sheetTitle, sheetHint;
    boolean sheetOpen; String lastDockFocus;

    float dp(float v) { return a.df(v * 2); }
    int ip(float v) { return Math.round(dp(v)); }
    TextView text(float sp, String family, int color) {
        TextView t = new TextView(a); t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(sp)); t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color); t.setShadowLayer(dp(4), 0, dp(.5f), 0x66000000); t.setIncludeFontPadding(true); return t;
    }

    CleanHome(HomeActivity act) {
        a = act;
        root = new FrameLayout(a); root.setClipChildren(false);
        topShade = new View(a); topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x80000000, 0x00000000}));
        root.addView(topShade, new FrameLayout.LayoutParams(-1, ip(200), Gravity.TOP));
        bottomShade = new View(a); bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xB0000000, 0x00000000}));
        root.addView(bottomShade, new FrameLayout.LayoutParams(-1, ip(240), Gravity.BOTTOM));

        clockBox = new LinearLayout(a); clockBox.setOrientation(LinearLayout.VERTICAL); clockBox.setGravity(Gravity.END);
        clock = text(64, "sans-serif-thin", 0xFFFFFFFF); clock.setFontFeatureSettings("tnum");
        date = text(16, "sans-serif-light", 0xDDFFFFFF);
        clockBox.addView(clock); clockBox.addView(date);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END); cl.setMargins(0, ip(28), ip(48), 0);
        root.addView(clockBox, cl);

        caption = text(11, "sans-serif", 0x99FFFFFF); caption.setSingleLine(); caption.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams capl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START); capl.setMargins(ip(48), 0, 0, ip(11));
        root.addView(caption, capl);

        dock = new LinearLayout(a); dock.setOrientation(LinearLayout.HORIZONTAL); dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(ip(20), ip(16), ip(20), ip(16)); dock.setClipChildren(false); dock.setClipToPadding(false);
        GradientDrawable pill = new GradientDrawable(); pill.setCornerRadius(dp(26)); pill.setColor(0x33FFFFFF); pill.setStroke(Math.max(1, ip(1)), 0x30FFFFFF);
        dock.setBackground(pill);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); dl.bottomMargin = ip(64);
        root.addView(dock, dl);

        dockLabel = text(14, "sans-serif-medium", 0xFFFFFFFF); dockLabel.setGravity(Gravity.CENTER); dockLabel.setSingleLine(); dockLabel.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(ip(220), -2, Gravity.BOTTOM | Gravity.START); ll.bottomMargin = ip(36);
        root.addView(dockLabel, ll);

        // All apps sheet
        sheet = new FrameLayout(a); sheet.setBackgroundColor(0xE60A0F14); sheet.setVisibility(View.GONE); sheet.setClipChildren(false);
        sheetTitle = text(28, "sans-serif-light", 0xFFFFFFFF); sheetTitle.setText("All Apps");
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START); tl.setMargins(ip(30), ip(28), 0, 0);
        sheet.addView(sheetTitle, tl);
        sheetHint = text(12, "sans-serif", 0x99FFFFFF); sheetHint.setText("Hold OK for options: pin to the dock, move, hide");
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END); hl.setMargins(0, ip(40), ip(56), 0);
        sheet.addView(sheetHint, hl);
        sheetScroll = new ScrollView(a); sheetScroll.setVerticalScrollBarEnabled(false); sheetScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sheetScroll.setClipChildren(false); sheetScroll.setClipToPadding(false); sheetScroll.setSmoothScrollingEnabled(true);
        grid = new LinearLayout(a); grid.setOrientation(LinearLayout.VERTICAL); grid.setClipChildren(false); grid.setClipToPadding(false);
        grid.setPadding(0, 0, 0, ip(40));
        sheetScroll.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(-1, -1); gl.topMargin = ip(80);
        sheet.addView(sheetScroll, gl);
        root.addView(sheet, new FrameLayout.LayoutParams(-1, -1));
    }

    // ---------------- tiles ----------------
    /** A rounded tile: an app banner (or its icon on a dark card) or a white glyph on glass. Focus = 1.16x and a white ring. */
    FrameLayout tile(Drawable art, boolean glyph, float wDp, float hDp, final boolean inDock, final String name) {
        final FrameLayout t = new FrameLayout(a); t.setFocusable(true); t.setClickable(true); t.setContentDescription(name);
        final float r = dp(RADIUS);
        t.setOutlineProvider(new ViewOutlineProvider() { @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); } });
        t.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(r); bg.setColor(glyph ? BTN_BG : TILE_BG); t.setBackground(bg);
        ImageView iv = new ImageView(a); t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        setArt(iv, art, glyph, wDp, hDp);
        final GradientDrawable ring = new GradientDrawable(); ring.setCornerRadius(r); ring.setStroke(Math.max(1, ip(2)), RING); ring.setColor(0);
        t.setOnFocusChangeListener((v, f) -> {
            t.setForeground(f ? ring : null); // no elevation: a shadow would show through the glass buttons
            t.animate().scaleX(f ? FOCUS_SCALE : 1f).scaleY(f ? FOCUS_SCALE : 1f).setDuration(HomeActivity.dur(FOCUS_MS)).setInterpolator(new DecelerateInterpolator()).start();
            if (f && t.getTag() instanceof String) { a.lastFocusPkg = (String) t.getTag(); a.lastFocusInFav = inDock; }
            if (f) { a.click(false); if (inDock) { lastDockFocus = (String) (t.getTag() instanceof String ? t.getTag() : name); moveLabel(t, name); } }
        });
        return t;
    }

    void setArt(ImageView iv, Drawable art, boolean glyph, float wDp, float hDp) {
        if (art instanceof HomeActivity.IconCard) {                // no TV banner: the app icon centred on the dark card
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER); int p = Math.round(Math.min(dp(wDp), dp(hDp)) / 5f); iv.setPadding(p, p, p, p);
            iv.setImageDrawable(((HomeActivity.IconCard) art).icon);
        } else if (glyph) { iv.setScaleType(ImageView.ScaleType.FIT_CENTER); int p = Math.round(dp(hDp) * .22f); iv.setPadding(p, p, p, p); iv.setImageDrawable(art); }
        else { iv.setScaleType(ImageView.ScaleType.FIT_XY); iv.setPadding(0, 0, 0, 0); iv.setImageDrawable(art); }
    }

    /** The name under the dock glides to sit centred under the focused item. */
    void moveLabel(View t, String name) {
        dockLabel.setText(name);
        t.post(() -> {
            if (dock.getWidth() == 0) return;
            float cx = dock.getLeft() + t.getLeft() + t.getWidth() / 2f;
            float tx = cx - dockLabel.getWidth() / 2f;
            if (dockLabel.getAlpha() < .01f || dockLabel.getTranslationX() == 0) { dockLabel.setTranslationX(tx); dockLabel.setAlpha(0f); }
            dockLabel.animate().translationX(tx).alpha(1f).setDuration(HomeActivity.dur(LABEL_MS)).setInterpolator(new DecelerateInterpolator()).start();
        });
    }

    // ---------------- rows ----------------
    void rebuild(List<String> favs, List<AppRepo.App> apps, java.util.Set<String> hidden, Map<String, Drawable> art, boolean hasInputs) {
        View f = a.getCurrentFocus(); String keep = f != null && (f.getParent() == dock) && f.getTag() instanceof String ? (String) f.getTag() : null;
        int keepIdx = f != null && f.getParent() == dock ? dock.indexOfChild(f) : -1;
        dock.removeAllViews();
        for (String pkg : favs) {
            AppRepo.App app = a.byPkg.get(pkg); if (app == null) continue;
            FrameLayout t = tile(art.get(pkg), false, DOCK_W, DOCK_H, true, app.name); t.setTag(pkg);
            final AppRepo.App ap = app;
            t.setOnClickListener(v -> { if (a.movingPkg != null) { a.endMove(); return; } a.launch(ap, v); });
            t.setOnLongClickListener(v -> { a.appMenu(ap, true); return true; });
            if (pkg.equals(a.movingPkg)) { t.setAlpha(.85f); }
            dock.addView(t, lp(DOCK_W, DOCK_H));
        }
        if (dock.getChildCount() > 0) {
            View sep = new View(a); sep.setBackgroundColor(0x40FFFFFF);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Math.max(1, ip(1)), ip(44)); sl.setMargins(ip(4), 0, ip(GAP), 0);
            dock.addView(sep, sl);
        }
        allBtn = tile(new Glyph(Glyph.DOTS), true, BTN, BTN, true, "All Apps"); allBtn.setTag(null);
        allBtn.setOnClickListener(v -> { a.click(true); open(); });
        dock.addView(allBtn, lp(BTN, BTN));
        inputsBtn = null;
        if (hasInputs) {
            inputsBtn = tile(new Glyph(Glyph.INPUT), true, BTN, BTN, true, "Inputs");
            inputsBtn.setOnClickListener(v -> { a.click(true); a.openInputs(); });
            dock.addView(inputsBtn, lp(BTN, BTN));
        }
        gearBtn = tile(new Glyph(Glyph.GEAR), true, BTN, BTN, true, "Settings");
        gearBtn.setOnClickListener(v -> { a.click(true); a.startActivity(new android.content.Intent(a, SettingsActivity.class)); });
        dock.addView(gearBtn, lp(BTN, BTN));

        // all apps, 6 per row, centred; pinned apps get a dot before the name
        grid.removeAllViews(); LinearLayout row = null; int i = 0;
        for (AppRepo.App app : apps) {
            if (hidden.contains(app.pkg)) continue;
            if (i % COLS == 0) { row = new LinearLayout(a); row.setGravity(Gravity.CENTER_HORIZONTAL); row.setClipChildren(false); row.setClipToPadding(false); row.setPadding(0, ip(14), 0, ip(14)); grid.addView(row, new LinearLayout.LayoutParams(-1, -2)); }
            LinearLayout cell = new LinearLayout(a); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER_HORIZONTAL); cell.setClipChildren(false); cell.setClipToPadding(false);
            cell.setPadding(ip(8), ip(8), ip(8), 0);
            FrameLayout t = tile(art.get(app.pkg), false, GRID_W, GRID_H, false, app.name); t.setTag(app.pkg);
            final AppRepo.App ap = app;
            t.setOnClickListener(v -> a.launch(ap, v));
            t.setOnLongClickListener(v -> { a.appMenu(ap, false); return true; });
            cell.addView(t, new LinearLayout.LayoutParams(ip(GRID_W), ip(GRID_H)));
            TextView n = text(12, "sans-serif", 0xCCFFFFFF); n.setText((favs.contains(app.pkg) ? "• " : "") + app.name); n.setGravity(Gravity.CENTER);
            n.setSingleLine(); n.setEllipsize(TextUtils.TruncateAt.END); n.setPadding(0, ip(10), 0, 0);
            cell.addView(n, new LinearLayout.LayoutParams(ip(GRID_W), -2));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2); cp.rightMargin = ip(4);
            row.addView(cell, cp); i++;
        }
        if (keep != null && focusPkg(keep, true)) return;
        if (keepIdx >= 0 && !sheetOpen) { View v = dock.getChildAt(Math.min(keepIdx, dock.getChildCount() - 1)); if (v != null && v.isFocusable()) v.requestFocus(); else allBtn.requestFocus(); }
    }

    LinearLayout.LayoutParams lp(float w, float h) { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ip(w), ip(h)); l.rightMargin = ip(GAP); return l; }

    /** Fills in pictures that finished loading after the tiles were made. */
    void applyArt(Map<String, Drawable> art) {
        for (int i = 0; i < dock.getChildCount(); i++) fill(dock.getChildAt(i), art, DOCK_W, DOCK_H);
        for (int r = 0; r < grid.getChildCount(); r++) { ViewGroup row = (ViewGroup) grid.getChildAt(r); for (int c = 0; c < row.getChildCount(); c++) fill(((ViewGroup) row.getChildAt(c)).getChildAt(0), art, GRID_W, GRID_H); }
    }
    void fill(View t, Map<String, Drawable> art, float w, float h) {
        if (!(t.getTag() instanceof String) || !(t instanceof FrameLayout)) return;
        ImageView iv = (ImageView) ((FrameLayout) t).getChildAt(0);
        if (iv.getDrawable() == null) { Drawable d = art.get((String) t.getTag()); if (d != null) setArt(iv, d, false, w, h); }
    }

    boolean focusPkg(String pkg, boolean inDock) {
        if (inDock && !sheetOpen) for (int i = 0; i < dock.getChildCount(); i++) if (pkg.equals(dock.getChildAt(i).getTag())) { dock.getChildAt(i).requestFocus(); return true; }
        if (sheetOpen) for (int r = 0; r < grid.getChildCount(); r++) { ViewGroup row = (ViewGroup) grid.getChildAt(r); for (int c = 0; c < row.getChildCount(); c++) { View t = ((ViewGroup) row.getChildAt(c)).getChildAt(0); if (pkg.equals(t.getTag())) { t.requestFocus(); return true; } } }
        return false;
    }
    void focusFirst() { if (sheetOpen) { focusFirstGrid(); return; } if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus(); }
    void focusFirstGrid() { if (grid.getChildCount() > 0) ((ViewGroup) ((ViewGroup) grid.getChildAt(0)).getChildAt(0)).getChildAt(0).requestFocus(); }
    boolean isDockTile(View v) { return v != null && v.getParent() == dock; }

    // ---------------- all apps sheet ----------------
    void open() {
        if (sheetOpen) return; sheetOpen = true;
        sheet.setVisibility(View.VISIBLE); sheet.setAlpha(0f); sheet.animate().alpha(1f).setDuration(HomeActivity.dur(SHEET_MS)).start();
        dock.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        dock.setVisibility(View.INVISIBLE); dockLabel.setVisibility(View.INVISIBLE); clockBox.setVisibility(View.INVISIBLE); caption.setVisibility(View.INVISIBLE);
        sheetScroll.scrollTo(0, 0); focusFirstGrid();
    }
    /** Back (or Home) closes the sheet and puts focus back on the All apps button. */
    boolean close(boolean focusButton) {
        if (!sheetOpen) return false; sheetOpen = false;
        sheet.animate().cancel(); sheet.setVisibility(View.GONE);
        dock.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        dock.setVisibility(View.VISIBLE); dockLabel.setVisibility(View.VISIBLE); clockBox.setVisibility(View.VISIBLE); caption.setVisibility(View.VISIBLE);
        if (focusButton && allBtn != null) allBtn.requestFocus();
        return true;
    }

    // ---------------- photo credit ----------------
    static final Map<String, String> CREDIT = new HashMap<>();
    static {
        c("01_matterhorn_riffelsee.jpg", "Riffelsee & Matterhorn, Zermatt", "Robertuswiki", "CC BY-SA 4.0");
        c("02_lej_da_segl.jpg", "Lej da Segl, Engadin", "Hansueli Krapf", "CC BY-SA 3.0");
        c("03_lauterbrunnen.jpg", "Lauterbrunnen Valley, Bernese Oberland", "chensiyuan", "CC BY-SA 4.0");
        c("04_caumasee.jpg", "Caumasee, Flims", "Daniel Kraft", "CC BY-SA 3.0");
        c("05_lake_lucerne_pilatus.jpg", "Lake Lucerne from Pilatus", "Tobi 87", "CC BY-SA 3.0");
        c("06_lavaux.jpg", "Lavaux Vineyards, Lake Geneva", "Lorenz Poffet", "CC BY-SA 4.0");
        c("07_lake_brienz.jpg", "Lake Brienz, Bernese Oberland", "Kabelleger / David Gubler", "CC BY-SA 3.0");
        c("08_alpstein.jpg", "Alpstein, Appenzell", "Daniel Kraft", "CC BY-SA 3.0");
    }
    static void c(String f, String place, String artist, String lic) { CREDIT.put(f, place + "   \u00b7   Photo: " + artist + " / Wikimedia Commons, " + lic); }
    void onWallpaper(String file) { String c = CREDIT.get(file); caption.setText(c != null ? c : ""); }

    // ---------------- glyphs (drawn in code) ----------------
    static final class Glyph extends Drawable {
        static final int DOTS = 0, GEAR = 1, INPUT = 2;
        final int kind; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph(int kind) { this.kind = kind; p.setColor(0xFFFFFFFF); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); float s = Math.min(b.width(), b.height()), cx = b.exactCenterX(), cy = b.exactCenterY();
            if (kind == DOTS) {
                p.setStyle(Paint.Style.FILL); float step = s * .38f, r = s * .11f;
                for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) c.drawCircle(cx + i * step, cy + j * step, r, p);
            } else if (kind == GEAR) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(s * .1f); p.setStrokeCap(Paint.Cap.BUTT);
                c.drawCircle(cx, cy, s * .25f, p);
                p.setStrokeWidth(s * .13f);
                for (int i = 0; i < 8; i++) {
                    double an = Math.PI / 4 * i; float r1 = s * .35f, r2 = s * .48f;
                    c.drawLine(cx + (float) Math.cos(an) * r1, cy + (float) Math.sin(an) * r1, cx + (float) Math.cos(an) * r2, cy + (float) Math.sin(an) * r2, p);
                }
            } else {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(s * .07f); p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeCap(Paint.Cap.ROUND);
                RectF scr = new RectF(cx - s * .44f, cy - s * .32f, cx + s * .44f, cy + s * .2f); c.drawRoundRect(scr, s * .06f, s * .06f, p);
                c.drawLine(cx - s * .18f, cy + s * .36f, cx + s * .18f, cy + s * .36f, p);
                c.drawLine(cx - s * .16f, cy - s * .06f, cx + s * .1f, cy - s * .06f, p);
                c.drawLine(cx + s * .02f, cy - s * .15f, cx + s * .12f, cy - s * .06f, p); c.drawLine(cx + s * .02f, cy + s * .03f, cx + s * .12f, cy - s * .06f, p);
            }
        }
        @Override public int getIntrinsicWidth() { return 96; }
        @Override public int getIntrinsicHeight() { return 96; }
        @Override public void setAlpha(int al) { p.setAlpha(al); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
