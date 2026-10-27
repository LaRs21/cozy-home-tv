package io.github.lars21.cozyhometv;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The home screen of a picture theme: the painting, 4 spots where the chosen apps are drawn in the style of the
 * object they sit on (printed on cloth, glowing in a crystal, lit in a lantern or window, set in a ring), the app's
 * name under each spot in the theme's font, a live clock and a gear top-right, and "More apps" at the bottom, which
 * opens all apps over a tint of the theme. Any app works in any spot: only its icon is used and the spot draws it.
 *
 * Spots are shared by all picture themes (Prefs.spots(); empty = "+ Add app"). Hold OK on a spot to choose, move or
 * clear it. The D-pad moves to the nearest item in that direction, judged by the spot centres.
 * Motion (Settings > Little movements) is one cheap layer drawn at about 30 fps, only while home is visible.
 */
final class PictureHome {
    static final float FOCUS_SCALE = 1.08f; static final long FOCUS_MS = 180, SHEET_MS = 180;
    static final int COLS = 6; static final float GRID_W = 256, GRID_H = 144; // design px

    final HomeActivity a; final FrameLayout root, spotLayer; final MotionLayer motion;
    final LinearLayout clockBox, more; final TextView clock, moreText; final FrameLayout gear;
    final FrameLayout sheet; final ScrollView sheetScroll; final LinearLayout grid; final TextView sheetTitle, sheetHint;
    final List<Spot> spots = new ArrayList<>(); final Map<String, Drawable> icons = new HashMap<>(); final Set<String> banners = new java.util.HashSet<>(); // pkg -> its banner, or its icon if it has none
    PictureTheme theme; Typeface font = Typeface.SERIF; boolean sheetOpen, running; int lastSpot = 0;
    /** -1: More apps (OK opens the app). 0..3: choosing the app for that spot (OK puts it there). */
    int pickIndex = -1; boolean fromChooser;
    final FrameLayout chooser; final LinearLayout chooserRows; final TextView chooserTitle, chooserHint; boolean chooserOpen;

    float px(float v) { return a.df(v); }
    int ip(float v) { return a.d(v); }
    TextView text(float size, int color) {
        TextView t = new TextView(a); t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size)); t.setTextColor(color);
        t.setShadowLayer(px(6), 0, px(2), 0xB0000000); return t;
    }

    PictureHome(HomeActivity act) {
        a = act;
        root = new FrameLayout(a); root.setClipChildren(false);
        motion = new MotionLayer(a, this); root.addView(motion, new FrameLayout.LayoutParams(-1, -1));
        spotLayer = new FrameLayout(a); spotLayer.setClipChildren(false); root.addView(spotLayer, new FrameLayout.LayoutParams(-1, -1));

        clockBox = new LinearLayout(a); clockBox.setOrientation(LinearLayout.HORIZONTAL); clockBox.setGravity(Gravity.CENTER_VERTICAL);
        clock = text(44, 0xFFFFFFFF); clock.setFontFeatureSettings("tnum");
        clockBox.addView(clock);
        gear = new FrameLayout(a); gear.setFocusable(true); gear.setClickable(true); gear.setContentDescription("Settings");
        ImageView gi = new ImageView(a); gi.setImageDrawable(new Glyph(Glyph.GEAR)); int gp = ip(12); gi.setPadding(gp, gp, gp, gp);
        gear.addView(gi, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ip(64), ip(64)); gl.leftMargin = ip(12); clockBox.addView(gear, gl);
        gear.setOnClickListener(v -> { a.click(true); a.startActivity(new android.content.Intent(a, SettingsActivity.class)); });
        gear.setOnFocusChangeListener((v, f) -> { pill(v, f); if (f) a.click(false); });
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END); cl.setMargins(0, ip(14), ip(28), 0);
        root.addView(clockBox, cl);

        // "More apps": nine dots over the words, at the bottom centre (where the painting left room for it)
        more = new LinearLayout(a); more.setOrientation(LinearLayout.VERTICAL); more.setGravity(Gravity.CENTER_HORIZONTAL);
        more.setFocusable(true); more.setClickable(true); more.setContentDescription("More apps"); more.setPadding(ip(26), ip(12), ip(26), ip(10));
        ImageView dots = new ImageView(a); dots.setImageDrawable(new Glyph(Glyph.DOTS));
        more.addView(dots, new LinearLayout.LayoutParams(ip(28), ip(28)));
        moreText = text(26, 0xFFFFFFFF); moreText.setText("More apps"); LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2); ml.topMargin = ip(6);
        more.addView(moreText, ml);
        more.setOnClickListener(v -> { a.click(true); open(); });
        more.setOnFocusChangeListener((v, f) -> { pill(v, f); v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(HomeActivity.dur(FOCUS_MS)).start(); if (f) a.click(false); });
        root.addView(more, new FrameLayout.LayoutParams(-2, -2));
        View.OnKeyListener nav = (v, k, e) -> e.getAction() == KeyEvent.ACTION_DOWN && navigate(v, k);
        gear.setOnKeyListener(nav); more.setOnKeyListener(nav);

        sheet = new FrameLayout(a); sheet.setVisibility(View.GONE); sheet.setClipChildren(false);
        sheetTitle = text(56, 0xFFFFFFFF); sheetTitle.setText("More apps");
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START); tl.setMargins(ip(72), ip(44), 0, 0);
        sheet.addView(sheetTitle, tl);
        sheetHint = text(24, 0xB3FFFFFF); sheetHint.setText("Hold OK for options: put in a spot, hide");
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END); hl.setMargins(0, ip(72), ip(96), 0);
        sheet.addView(sheetHint, hl);
        sheetScroll = new ScrollView(a); sheetScroll.setVerticalScrollBarEnabled(false); sheetScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sheetScroll.setClipChildren(false); sheetScroll.setClipToPadding(false);
        grid = new LinearLayout(a); grid.setOrientation(LinearLayout.VERTICAL); grid.setClipChildren(false); grid.setClipToPadding(false); grid.setPadding(0, 0, 0, ip(80));
        sheetScroll.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, -1); sl.topMargin = ip(150);
        sheet.addView(sheetScroll, sl);
        root.addView(sheet, new FrameLayout.LayoutParams(-1, -1));

        // "Choose apps": the 4 spots of this picture, each with its app; OK on one opens the app list to pick from
        chooser = new FrameLayout(a); chooser.setVisibility(View.GONE); chooser.setClipChildren(false);
        chooserTitle = text(56, 0xFFFFFFFF); chooserTitle.setText("Choose apps");
        FrameLayout.LayoutParams ctl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START); ctl.setMargins(ip(120), ip(80), 0, 0);
        chooser.addView(chooserTitle, ctl);
        chooserHint = text(26, 0xC8FFFFFF); chooserHint.setText("Any app fits any spot. Pick a spot, then the app for it.");
        FrameLayout.LayoutParams chl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START); chl.setMargins(ip(122), ip(160), 0, 0);
        chooser.addView(chooserHint, chl);
        chooserRows = new LinearLayout(a); chooserRows.setOrientation(LinearLayout.VERTICAL); chooserRows.setClipChildren(false);
        FrameLayout.LayoutParams crl = new FrameLayout.LayoutParams(ip(1100), -2, Gravity.TOP | Gravity.START); crl.setMargins(ip(120), ip(250), 0, 0);
        chooser.addView(chooserRows, crl);
        root.addView(chooser, new FrameLayout.LayoutParams(-1, -1));
    }

    void pill(View v, boolean f) {
        if (!f) { v.setBackground(null); return; }
        int acc = theme != null ? theme.accent : 0xFFFFFFFF;
        GradientDrawable g = new GradientDrawable(); g.setCornerRadius(px(36)); g.setColor(acc & 0x00FFFFFF | 0x38000000);
        g.setStroke(Math.max(2, ip(3)), acc); v.setBackground(g);
    }

    /** Switches to another picture theme: new spots, font, colours and motion. */
    void setTheme(PictureTheme t) {
        if (t == theme) return;
        theme = t; font = t.labelFont(a);
        clock.setTypeface(font); clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(t.clockSize)); clock.setTextColor(t.textColor);
        moreText.setTypeface(font); moreText.setTextColor(t.textColor);
        sheetTitle.setTypeface(font); sheetHint.setTypeface(font); sheet.setBackground(sheetBg(t.sheetTint));
        chooserTitle.setTypeface(font); chooserHint.setTypeface(font); chooser.setBackground(sheetBg(t.sheetTint));
        placeMore(); more.post(this::placeMore);
        spotLayer.removeAllViews(); spots.clear();
        for (int i = 0; i < t.slots.size() && i < Prefs.SPOTS; i++) { Spot s = new Spot(this, t.slots.get(i), i); spots.add(s); spotLayer.addView(s, s.params()); }
        motion.setTheme(t, a.S); setRunning(running);
    }
    /** The theme's tint, a little lighter at the top, so the painting's mood carries into the sheets. */
    static Drawable sheetBg(int tint) {
        int top = (tint & 0x00FFFFFF) | (Math.max(0, ((tint >>> 24) & 0xFF) - 0x30) << 24);
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, tint, tint});
    }
    void placeMore() { if (theme == null) return; more.setX(px(theme.moreX) - more.getWidth() / 2f); more.setY(px(theme.moreY) - more.getHeight() / 2f); }

    // ---------------- spots ----------------
    void rebuild(List<AppRepo.App> apps, Set<String> hidden, Map<String, Drawable> art) {
        List<String> pk = a.prefs.spots();
        final List<AppRepo.App> need = new ArrayList<>();
        for (Spot s : spots) {
            String p = pk.get(s.index); AppRepo.App app = p.isEmpty() ? null : a.byPkg.get(p);
            s.setApp(app, app != null ? icons.get(app.pkg) : null);
            if (app != null && !icons.containsKey(app.pkg)) need.add(app);
        }
        if (!need.isEmpty()) {
            final PackageManager pm = a.getPackageManager();
            a.io.execute(() -> {
                final Map<String, Drawable> got = new HashMap<>(); final Set<String> gotB = new java.util.HashSet<>();
                for (AppRepo.App ap : need) { Drawable bn = AppRepo.banner(pm, ap); if (bn != null) { got.put(ap.pkg, bn); gotB.add(ap.pkg); } else got.put(ap.pkg, AppRepo.icon(pm, ap)); }
                a.ui.post(() -> { icons.putAll(got); banners.addAll(gotB); for (Spot s : spots) if (s.app != null && s.icon == null) s.setApp(s.app, icons.get(s.app.pkg)); });
            });
        }
        buildGrid(apps, hidden, art, pk);
    }
    void focusFirst() { if (chooserOpen) { if (chooserRows.getChildCount() > 0) chooserRows.getChildAt(Math.min(lastSpot, chooserRows.getChildCount() - 1)).requestFocus(); return; } if (sheetOpen) { focusFirstGrid(); return; } if (!spots.isEmpty()) spots.get(Math.min(lastSpot, spots.size() - 1)).requestFocus(); else more.requestFocus(); }
    boolean focusPkg(String pkg) {
        if (sheetOpen) {
            for (int r = 0; r < grid.getChildCount(); r++) { ViewGroup row = (ViewGroup) grid.getChildAt(r); for (int c = 0; c < row.getChildCount(); c++) { View t = ((ViewGroup) row.getChildAt(c)).getChildAt(0); if (pkg.equals(t.getTag())) { t.requestFocus(); return true; } } }
            return false;
        }
        for (Spot s : spots) if (s.app != null && s.app.pkg.equals(pkg)) { s.requestFocus(); return true; }
        return false;
    }
    Spot spotOf(View v) { return v instanceof Spot && spots.contains(v) ? (Spot) v : null; }

    /** Centre of a focusable item in screen px (a spot's is its slot centre, not its glow-sized view box). */
    float[] centre(View v) {
        if (v instanceof Spot) { Spot s = (Spot) v; return new float[]{px(s.slot.x), px(s.slot.y)}; }
        int[] loc = new int[2]; v.getLocationOnScreen(loc); return new float[]{loc[0] + v.getWidth() / 2f, loc[1] + v.getHeight() / 2f};
    }
    /** The spots in rows (top to bottom), each row left to right: reading order, whatever order slots.json lists them in. */
    List<List<Spot>> rows() {
        List<Spot> byY = new ArrayList<>(spots); java.util.Collections.sort(byY, (p, q) -> Float.compare(p.slot.y, q.slot.y));
        List<List<Spot>> rows = new ArrayList<>();
        for (Spot s : byY) {
            List<Spot> last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (last != null && Math.abs(s.slot.y - last.get(0).slot.y) < 220) last.add(s); else { last = new ArrayList<>(); last.add(s); rows.add(last); }
        }
        for (List<Spot> r : rows) java.util.Collections.sort(r, (p, q) -> Float.compare(p.slot.x, q.slot.x));
        return rows;
    }
    static Spot nearestX(List<Spot> row, float x) { Spot b = null; for (Spot s : row) if (b == null || Math.abs(s.slot.x - x) < Math.abs(b.slot.x - x)) b = s; return b; }
    /**
     * D-pad, app to app: left/right walk the spots in reading order; up/down move between the rows; down from the
     * bottom row is More apps; up from the top-right spot is the gear. Focus never falls off the scene.
     */
    boolean navigate(View from, int key) {
        boolean L = key == KeyEvent.KEYCODE_DPAD_LEFT, R = key == KeyEvent.KEYCODE_DPAD_RIGHT, U = key == KeyEvent.KEYCODE_DPAD_UP, D = key == KeyEvent.KEYCODE_DPAD_DOWN;
        if (!(L || R || U || D) || sheetOpen || chooserOpen) return false;
        List<List<Spot>> rows = rows(); List<Spot> order = new ArrayList<>(); for (List<Spot> r : rows) order.addAll(r);
        View to = null;
        if (order.isEmpty()) to = from == more && U ? gear : from == gear && D ? more : null;
        else if (from == gear) { if (D || L) to = rows.get(0).get(rows.get(0).size() - 1); }
        else if (from == more) {
            List<Spot> bottom = rows.get(rows.size() - 1);
            if (U) { Spot last = lastSpot < spots.size() ? spots.get(lastSpot) : null; to = last != null && bottom.contains(last) ? last : nearestX(bottom, 960); }
            else if (L) to = bottom.get(0); else if (R) to = bottom.get(bottom.size() - 1);
        } else {
            Spot s = spotOf(from); if (s == null) return false;
            int i = order.indexOf(s), ri = 0; for (int r = 0; r < rows.size(); r++) if (rows.get(r).contains(s)) ri = r;
            List<Spot> row = rows.get(ri);
            if (R) to = i + 1 < order.size() ? order.get(i + 1) : null;
            else if (L) to = i > 0 ? order.get(i - 1) : null;
            else if (D) to = ri + 1 < rows.size() ? nearestX(rows.get(ri + 1), s.slot.x) : more;
            else if (U) to = ri > 0 ? nearestX(rows.get(ri - 1), s.slot.x) : (s == row.get(row.size() - 1) ? gear : null);
        }
        if (to != null && to.getVisibility() == View.VISIBLE) to.requestFocus();
        return true; // never let focus fall off the scene
    }

    // ---------------- editing the spots ----------------
    AlertDialog.Builder dialog() { return new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert); }
    /** Hold OK on a spot: choose an app, move it to another spot, or clear it. */
    void spotMenu(final Spot s) {
        final List<String> items = new ArrayList<>();
        items.add(s.app == null ? "Choose an app" : "Change app");
        if (s.app != null) { items.add("Move to another spot"); items.add("Clear this spot"); items.add("App info"); }
        items.add("Choose all apps");
        dialog().setTitle(s.app != null ? s.app.name + "  ·  " + s.slot.name : "The " + s.slot.name)
            .setItems(items.toArray(new String[0]), (d, w) -> {
                String it = items.get(w);
                if (it.equals("Choose an app") || it.equals("Change app")) chooseApp(s.index);
                else if (it.equals("Move to another spot")) moveSpot(s.index);
                else if (it.equals("Clear this spot")) setSpot(s.index, "");
                else if (it.equals("Choose all apps")) openChooser();
                else a.appInfo(s.app.pkg);
            }).show();
    }
    /** Opens the app list, in the theme's style, to pick the app for one spot. */
    void chooseApp(final int index) { open(index); }
    void moveSpot(final int from) {
        final List<String> names = new ArrayList<>(); final List<Integer> idx = new ArrayList<>(); List<String> pk = a.prefs.spots();
        for (Spot s : spots) if (s.index != from) { AppRepo.App ap = a.byPkg.get(pk.get(s.index)); names.add("The " + s.slot.name + (ap != null ? "  (swap with " + ap.name + ")" : "")); idx.add(s.index); }
        dialog().setTitle("Move to").setItems(names.toArray(new String[0]), (d, w) -> {
            List<String> l = a.prefs.spots(); int to = idx.get(w); String t = l.get(to); l.set(to, l.get(from)); l.set(from, t);
            a.prefs.setSpots(l); lastSpot = to; a.rebuildRows(); if (to < spots.size()) spots.get(to).requestFocus();
        }).show();
    }
    /** From the More apps sheet: put this app in one of the spots. */
    void putInSpot(final AppRepo.App app) {
        final List<String> names = new ArrayList<>(); List<String> pk = a.prefs.spots();
        for (Spot s : spots) { AppRepo.App ap = a.byPkg.get(pk.get(s.index)); names.add("The " + s.slot.name + (ap != null ? "  (now " + ap.name + ")" : "  (empty)")); }
        dialog().setTitle("Put " + app.name + " in").setItems(names.toArray(new String[0]), (d, w) -> { close(false); setSpot(w, app.pkg); }).show();
    }
    void setSpot(int index, String pkg) {
        List<String> l = a.prefs.spots();
        if (!pkg.isEmpty()) for (int i = 0; i < l.size(); i++) if (i != index && pkg.equals(l.get(i))) l.set(i, ""); // one spot per app
        l.set(index, pkg); a.prefs.setSpots(l); lastSpot = index; a.rebuildRows();
        if (chooserOpen) { fillChooser(); chooserRows.getChildAt(Math.min(index, chooserRows.getChildCount() - 1)).requestFocus(); }
        else if (index < spots.size()) spots.get(index).requestFocus();
    }

    void setRunning(boolean on) { running = on; boolean m = on && a.prefs.motion(); motion.setRunning(m); for (Spot s : spots) { s.live = m; if (!m && s.flick != 1f) { s.flick = 1f; s.invalidate(); } } }

    // ---------------- More apps sheet ----------------
    void open() { open(-1); }
    /** pick = -1: More apps. 0..3: choose the app for that spot (the apps already in spots are marked with a dot). */
    void open(int pick) {
        pickIndex = pick; fromChooser = chooserOpen;
        String spotName = pick >= 0 && pick < spots.size() ? spots.get(pick).slot.name : "spot";
        sheetTitle.setText(pick < 0 ? "More apps" : "App for the " + spotName);
        sheetHint.setText(pick < 0 ? "Hold OK for options: put in a spot, hide" : "OK puts the app in the " + spotName + "  ·  Back to cancel");
        if (chooserOpen) { chooser.setVisibility(View.GONE); chooserOpen = false; }
        if (sheetOpen) { focusFirstGrid(); return; } sheetOpen = true;
        sheet.setVisibility(View.VISIBLE); sheet.setAlpha(0f); sheet.animate().alpha(1f).setDuration(HomeActivity.dur(SHEET_MS)).start();
        spotLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); spotLayer.setVisibility(View.INVISIBLE);
        more.setVisibility(View.INVISIBLE); clockBox.setVisibility(View.INVISIBLE);
        sheetScroll.scrollTo(0, 0); focusFirstGrid();
    }
    /** Back (or Home) closes the sheet (or the chooser); focus goes back to where it came from. */
    boolean close(boolean focusButton) {
        if (sheetOpen && fromChooser && focusButton) { hideSheet(); pickIndex = -1; openChooser(); return true; }
        boolean was = sheetOpen || chooserOpen;
        if (!was) return false;
        int pick = pickIndex; hideSheet(); pickIndex = -1; fromChooser = false;
        if (chooserOpen) { chooser.setVisibility(View.GONE); chooserOpen = false; }
        spotLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS); spotLayer.setVisibility(View.VISIBLE);
        more.setVisibility(View.VISIBLE); clockBox.setVisibility(View.VISIBLE);
        if (focusButton) { if (pick >= 0 && pick < spots.size()) spots.get(pick).requestFocus(); else more.requestFocus(); }
        return true;
    }
    void hideSheet() { sheetOpen = false; sheet.animate().cancel(); sheet.setVisibility(View.GONE); }

    /** The Choose apps screen: one row per spot of this picture with its app, OK = pick an app for it. */
    void openChooser() {
        if (sheetOpen) hideSheet();
        chooserOpen = true; fillChooser();
        chooser.setVisibility(View.VISIBLE); chooser.setAlpha(0f); chooser.animate().alpha(1f).setDuration(HomeActivity.dur(SHEET_MS)).start();
        spotLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); spotLayer.setVisibility(View.INVISIBLE);
        more.setVisibility(View.INVISIBLE); clockBox.setVisibility(View.INVISIBLE);
        focusFirst();
    }
    void fillChooser() {
        chooserRows.removeAllViews(); List<String> pk = a.prefs.spots();
        for (final Spot s : spots) {
            final AppRepo.App ap = a.byPkg.get(pk.get(s.index));
            LinearLayout row = new LinearLayout(a); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true); row.setClickable(true); row.setPadding(ip(28), ip(16), ip(36), ip(16));
            ImageView iv = new ImageView(a); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (s.face != null) iv.setImageBitmap(s.face);
            row.addView(iv, new LinearLayout.LayoutParams(ip(96), ip(96)));
            LinearLayout col = new LinearLayout(a); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(ip(28), 0, 0, 0);
            TextView t1 = text(34, theme.textColor); t1.setTypeface(font); t1.setText((s.index + 1) + ".  The " + s.slot.name); col.addView(t1);
            TextView t2 = text(26, (theme.textColor & 0x00FFFFFF) | 0xC0000000); t2.setTypeface(font); t2.setText(ap != null ? ap.name : "Empty: + Add app"); col.addView(t2);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            TextView act = text(24, theme.textColor); act.setTypeface(font); act.setText(ap != null ? "Change" : "Choose"); row.addView(act);
            row.setOnFocusChangeListener((v, f) -> { pill(v, f); if (f) { lastSpot = s.index; a.click(false); } });
            row.setOnClickListener(v -> { a.click(true); open(s.index); });
            row.setOnLongClickListener(v -> { if (ap != null) setSpot(s.index, ""); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = ip(14); chooserRows.addView(row, lp);
        }
        chooserHint.setText("Any app fits any spot. OK to choose, hold OK to clear. Back when you're done.");
    }
    void focusFirstGrid() { if (grid.getChildCount() > 0) ((ViewGroup) ((ViewGroup) grid.getChildAt(0)).getChildAt(0)).getChildAt(0).requestFocus(); }

    void buildGrid(List<AppRepo.App> apps, Set<String> hidden, Map<String, Drawable> art, List<String> inSpots) {
        grid.removeAllViews(); LinearLayout row = null; int i = 0;
        for (AppRepo.App app : apps) {
            if (hidden.contains(app.pkg)) continue;
            if (i % COLS == 0) { row = new LinearLayout(a); row.setGravity(Gravity.CENTER_HORIZONTAL); row.setClipChildren(false); row.setClipToPadding(false); row.setPadding(0, ip(20), 0, ip(20)); grid.addView(row, new LinearLayout.LayoutParams(-1, -2)); }
            LinearLayout cell = new LinearLayout(a); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER_HORIZONTAL); cell.setClipChildren(false); cell.setClipToPadding(false);
            cell.setPadding(ip(16), ip(12), ip(16), 0);
            final FrameLayout t = tile(art.get(app.pkg)); t.setTag(app.pkg); t.setContentDescription(app.name);
            final AppRepo.App ap = app;
            t.setOnClickListener(v -> { if (pickIndex >= 0) { int ix = pickIndex; boolean back = fromChooser; a.click(true); if (back) { hideSheet(); pickIndex = -1; fromChooser = false; chooserOpen = true; chooser.setVisibility(View.VISIBLE); setSpot(ix, ap.pkg); } else { close(false); setSpot(ix, ap.pkg); } } else a.launch(ap, v); });
            t.setOnLongClickListener(v -> { if (pickIndex < 0) a.appMenu(ap, false); return true; });
            cell.addView(t, new LinearLayout.LayoutParams(ip(GRID_W), ip(GRID_H)));
            TextView n = text(24, theme != null ? theme.textColor : 0xFFFFFFFF); n.setTypeface(font); n.setText((inSpots.contains(app.pkg) ? "• " : "") + app.name); n.setGravity(Gravity.CENTER);
            n.setSingleLine(); n.setEllipsize(TextUtils.TruncateAt.END); n.setPadding(0, ip(16), 0, 0);
            cell.addView(n, new LinearLayout.LayoutParams(ip(GRID_W), -2));
            row.addView(cell, new LinearLayout.LayoutParams(-2, -2)); i++;
        }
    }
    FrameLayout tile(Drawable art) {
        final FrameLayout t = new FrameLayout(a); t.setFocusable(true); t.setClickable(true);
        final float r = px(22);
        t.setOutlineProvider(new ViewOutlineProvider() { @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); } });
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(r); bg.setColor(0xFF20262C); // the app's art is not clipped t.setBackground(bg);
        ImageView iv = new ImageView(a); t.addView(iv, new FrameLayout.LayoutParams(-1, -1)); setArt(iv, art);
        final GradientDrawable ring = new GradientDrawable(); ring.setCornerRadius(r); ring.setStroke(Math.max(2, ip(4)), theme != null ? theme.accent : 0xFFFFFFFF); ring.setColor(0);
        t.setOnFocusChangeListener((v, f) -> {
            t.setForeground(f ? ring : null);
            t.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).setDuration(HomeActivity.dur(160)).setInterpolator(new DecelerateInterpolator()).start();
            if (f) { a.click(false); a.lastFocusPkg = (String) t.getTag(); a.lastFocusInFav = false; }
        });
        return t;
    }
    void setArt(ImageView iv, Drawable art) {
        if (art instanceof HomeActivity.IconCard) { iv.setScaleType(ImageView.ScaleType.FIT_CENTER); int p = ip(GRID_H / 5f); iv.setPadding(p, p, p, p); iv.setImageDrawable(((HomeActivity.IconCard) art).icon); }
        else { iv.setScaleType(ImageView.ScaleType.FIT_CENTER); iv.setPadding(0, 0, 0, 0); iv.setImageDrawable(art); } // banner at its own aspect
    }
    void applyArt(Map<String, Drawable> art) {
        for (int r = 0; r < grid.getChildCount(); r++) {
            ViewGroup row = (ViewGroup) grid.getChildAt(r);
            for (int c = 0; c < row.getChildCount(); c++) {
                View t = ((ViewGroup) row.getChildAt(c)).getChildAt(0); if (!(t.getTag() instanceof String)) continue;
                ImageView iv = (ImageView) ((FrameLayout) t).getChildAt(0); if (iv.getDrawable() == null) { Drawable d = art.get((String) t.getTag()); if (d != null) setArt(iv, d); }
            }
        }
    }

    // =====================================================================================================
    /** Small code-drawn icons: the settings gear and the nine dots of More apps. */
    static final class Glyph extends Drawable {
        static final int GEAR = 0, DOTS = 1;
        final int kind; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); int color = 0xFFFFFFFF;
        Glyph(int kind) { this.kind = kind; p.setStyle(Paint.Style.FILL); p.setShadowLayer(3, 0, 1, 0x90000000); }
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds(); float w = b.width(), h = b.height(), cx = b.exactCenterX(), cy = b.exactCenterY(); p.setColor(color);
            if (kind == DOTS) { float st = Math.min(w, h) / 3f, r = st * .2f; for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) c.drawCircle(b.left + st * (i + .5f), b.top + st * (j + .5f), r, p); return; }
            Path path = new Path(); float ro = Math.min(w, h) * .46f, ri = ro * .74f; int teeth = 8;
            for (int i = 0; i < teeth * 2; i++) { double a0 = Math.PI * 2 * i / (teeth * 2), a1 = Math.PI * 2 * (i + 1) / (teeth * 2); float r = i % 2 == 0 ? ro : ri;
                float x0 = cx + (float) Math.cos(a0) * r, y0 = cy + (float) Math.sin(a0) * r, x1 = cx + (float) Math.cos(a1) * r, y1 = cy + (float) Math.sin(a1) * r;
                if (i == 0) path.moveTo(x0, y0); else path.lineTo(x0, y0); path.lineTo(x1, y1); }
            path.close(); path.addCircle(cx, cy, ro * .38f, Path.Direction.CCW); path.setFillType(Path.FillType.EVEN_ODD); c.drawPath(path, p);
        }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    // =====================================================================================================
    /**
     * One spot. The app's icon is drawn once into a small "face" bitmap in the spot's style (only when the app
     * changes); each frame is then just that bitmap, a soft glow behind it and the name under it.
     */
    static final class Spot extends View {
        final PictureHome h; final PictureTheme.Slot slot; final int index; final float seed;
        AppRepo.App app; Drawable icon; boolean banner; int artW, artH; Bitmap face; float glow, flick = 1f; boolean live;
        final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG), haloP = new Paint(Paint.ANTI_ALIAS_FLAG), bmpP = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        final TextPaint labelP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final Paint ringP = new Paint(Paint.ANTI_ALIAS_FLAG), pillP = new Paint(Paint.ANTI_ALIAS_FLAG), darkP = new Paint(Paint.ANTI_ALIAS_FLAG); final RectF rect = new RectF();
        final float left, top, vw, vh; ValueAnimator glowAnim;

        Spot(PictureHome h, PictureTheme.Slot s, int index) {
            super(h.a); this.h = h; slot = s; this.index = index; seed = index * 1.7f + .3f;
            setFocusable(true); setClickable(true);
            float r = Math.max(s.w, s.h) * .95f; // glow reach
            float halfW = Math.max(r, 200), above = r, below = Math.max(r, s.labelDy + 50);
            left = s.x - halfW; top = s.y - above; vw = 2 * halfW; vh = above + below;
            setPivotX(h.px(s.x - left)); setPivotY(h.px(s.y - top));
            labelP.setTypeface(h.font); labelP.setTextSize(h.px(30)); labelP.setTextAlign(Paint.Align.CENTER); labelP.setColor(h.theme.textColor);
            labelP.setShadowLayer(h.px(6), 0, h.px(2), 0xE0000000);
            setOnClickListener(v -> { if (app == null) h.chooseApp(index); else h.a.launch(app, v); });
            setOnLongClickListener(v -> { h.spotMenu(this); return true; });
            setOnKeyListener((v, k, e) -> e.getAction() == KeyEvent.ACTION_DOWN && h.navigate(v, k));
        }
        FrameLayout.LayoutParams params() {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(h.px(vw)), Math.round(h.px(vh)));
            lp.leftMargin = Math.round(h.px(left)); lp.topMargin = Math.round(h.px(top)); return lp;
        }

        void setApp(AppRepo.App a, Drawable ic) {
            if (a == app && ic == icon && face != null) return;
            app = a; icon = ic; banner = a != null && h.banners.contains(a.pkg); setContentDescription(a != null ? a.name : "Add an app to the " + slot.name);
            Bitmap old = face; face = render(); if (old != null) old.recycle(); invalidate();
        }
        /** Lit objects (crystals, lanterns, windows, rings) glow a little even when not focused; cloth does not. */
        boolean lit() { return !slot.style.equals("cloth"); }

        @Override protected void onFocusChanged(boolean f, int dir, android.graphics.Rect prev) {
            super.onFocusChanged(f, dir, prev);
            if (glowAnim != null) glowAnim.cancel();
            glowAnim = ValueAnimator.ofFloat(glow, f ? 1f : 0f); glowAnim.setDuration(HomeActivity.dur(FOCUS_MS));
            glowAnim.addUpdateListener(an -> { glow = (float) an.getAnimatedValue(); invalidate(); }); glowAnim.start();
            animate().scaleX(f ? FOCUS_SCALE : 1f).scaleY(f ? FOCUS_SCALE : 1f).setDuration(HomeActivity.dur(FOCUS_MS)).setInterpolator(new DecelerateInterpolator()).start();
            if (f) { h.lastSpot = index; h.a.click(false); if (app != null) { h.a.lastFocusPkg = app.pkg; h.a.lastFocusInFav = true; } }
        }

        /** From the motion layer: a slow, uneven flicker for lit spots. */
        void tick(float t) {
            if (!live || !lit() || app == null) return;
            float f = 1f - .12f * (float) (Math.sin(t * 2.1 + seed) * .5 + .5) - .06f * (float) (Math.sin(t * 5.3 + seed * 3) * .5 + .5);
            if (Math.abs(f - flick) > .01f) { flick = f; invalidate(); }
        }

        @Override protected void onDraw(Canvas c) {
            float cx = h.px(slot.x - left), cy = h.px(slot.y - top), R = h.px(Math.max(slot.w, slot.h) * .85f);
            float base = app != null && lit() ? .4f : 0f, k = (base + (1 - base) * glow) * (lit() ? flick : 1f);
            if (k > .01f) {
                if (glowP.getShader() == null) { int g = slot.glow & 0x00FFFFFF; glowP.setShader(new RadialGradient(cx, cy, R, new int[]{g | 0xB0000000, g | 0x48000000, g}, new float[]{0f, .45f, 1f}, Shader.TileMode.CLAMP)); }
                glowP.setAlpha(Math.round(255 * Math.min(1f, k))); c.drawCircle(cx, cy, R, glowP);
            }
            if (glow > .01f) { // focus halo: a bright, soft light in the theme's accent behind the object
                if (haloP.getShader() == null) { int ac = h.theme.accent & 0x00FFFFFF; haloP.setShader(new RadialGradient(cx, cy, R * .9f, new int[]{ac | 0xD0000000, ac | 0x70000000, ac}, new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP)); }
                haloP.setAlpha(Math.round(255 * glow)); c.drawCircle(cx, cy, R * .9f, haloP);
            }
            if (face != null) c.drawBitmap(face, cx - face.getWidth() / 2f, cy - face.getHeight() / 2f, bmpP); // upright, as the app made it
            // focus: a ring in the theme's accent around the object (it must read on bright paintings too), and the name on a pill
            int acc = h.theme.accent;
            String label = app != null ? app.name : "+ Add app";
            CharSequence l = TextUtils.ellipsize(label, labelP, h.px(vw) - h.px(40), TextUtils.TruncateAt.END);
            float ly = cy + Math.max(h.px(slot.labelDy), artH / 2f + h.px(56)) + h.px(10); // the name clears the art
            if (glow > .01f) {
                float m = h.px(Math.min(slot.w, slot.h)) * (slot.round || slot.style.equals("ring") ? .5f : .46f) + h.px(10);
                ringP.setStyle(Paint.Style.STROKE); ringP.setStrokeWidth(h.px(6)); ringP.setColor(acc); ringP.setAlpha(Math.round(255 * glow));
                ringP.setShadowLayer(h.px(22), 0, 0, (acc & 0x00FFFFFF) | 0xE0000000);
                // a dark edge under the ring keeps it visible on white sails and bright skies
                darkP.setStyle(Paint.Style.STROKE); darkP.setStrokeWidth(h.px(9)); darkP.setColor(0x8C000000); darkP.setAlpha(Math.round(0x8C * glow));
                if (artW > 0) { float hw = artW / 2f + h.px(10), hh = artH / 2f + h.px(10), rr = Math.min(hw, hh) * .3f; c.drawRoundRect(cx - hw, cy - hh, cx + hw, cy + hh, rr, rr, darkP); c.drawRoundRect(cx - hw, cy - hh, cx + hw, cy + hh, rr, rr, ringP); }
                else if (slot.round || slot.style.equals("ring")) { c.drawCircle(cx, cy, m, darkP); c.drawCircle(cx, cy, m, ringP); }
                else { float s0 = Math.min(h.px(slot.w), h.px(slot.h)) * .40f + h.px(12); c.drawRoundRect(cx - s0, cy - s0, cx + s0, cy + s0, s0 * .36f, s0 * .36f, darkP); c.drawRoundRect(cx - s0, cy - s0, cx + s0, cy + s0, s0 * .36f, s0 * .36f, ringP); }
                float tw = labelP.measureText(l, 0, l.length()), ph = h.px(46);
                pillP.setColor(0x99000000); pillP.setAlpha(Math.round(0x99 * glow));
                rect.set(cx - tw / 2 - h.px(22), ly - ph * .72f, cx + tw / 2 + h.px(22), ly + ph * .28f); c.drawRoundRect(rect, ph / 2, ph / 2, pillP);
                ringP.setStrokeWidth(h.px(2.5f)); ringP.setShadowLayer(0, 0, 0, 0); c.drawRoundRect(rect, ph / 2, ph / 2, ringP);
            }
            labelP.setAlpha(app == null && glow < .5f ? 210 : 255);
            c.drawText(l, 0, l.length(), cx, ly, labelP);
        }

        /**
         * The spot's picture. An app is shown with its own artwork, never redrawn: its TV banner at the banner's own
         * aspect ratio if it has one, else its launcher icon as the system draws it (adaptive icons in the system's
         * shape). Only things around it are added: a gentle glow of the object's light behind and a soft drop shadow.
         * An empty spot is a dashed "+".
         */
        Bitmap render() {
            int fw = Math.max(8, Math.round(h.px(slot.w))), fh = Math.max(8, Math.round(h.px(slot.h)));
            float m = Math.min(fw, fh);
            if (app == null) {
                Bitmap b = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2, m * .035f)); p.setColor(0xE0FFFFFF);
                p.setPathEffect(new DashPathEffect(new float[]{m * .08f, m * .05f}, 0)); p.setShadowLayer(m * .04f, 0, 0, 0xC0000000);
                float r = m * .32f; c.drawCircle(fw / 2f, fh / 2f, r, p);
                p.setPathEffect(null); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(Math.max(3, m * .06f));
                c.drawLine(fw / 2f - r * .45f, fh / 2f, fw / 2f + r * .45f, fh / 2f, p); c.drawLine(fw / 2f, fh / 2f - r * .45f, fw / 2f, fh / 2f + r * .45f, p);
                artW = 0; artH = 0; return b;
            }
            if (icon == null) { artW = 0; artH = 0; return Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888); }
            int iw = icon.getIntrinsicWidth(), ih = icon.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = banner ? 320 : 1; ih = banner ? 180 : 1; }
            int tw, th;
            if (banner) { tw = Math.round(h.px(Math.max(Math.max(slot.w, slot.h) * 1.35f, 150))); th = Math.max(1, Math.round(tw * ih / (float) iw)); }
            else { int sz = Math.round(Math.max(h.px(Math.min(slot.w, slot.h) * .85f), h.px(76))); if (iw >= ih) { tw = sz; th = Math.max(1, Math.round(sz * ih / (float) iw)); } else { th = sz; tw = Math.max(1, Math.round(sz * iw / (float) ih)); } }
            artW = tw; artH = th;
            Bitmap art = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, tw, th); icon.draw(new Canvas(art)); // the app's own drawing, unchanged
            int pad = Math.round(Math.max(tw, th) * .3f);
            Bitmap b = Bitmap.createBitmap(tw + 2 * pad, th + 2 * pad, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b);
            float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f, R = Math.max(tw, th) * .78f;
            if (lit()) { // a gentle glow of the object's light behind the art
                int g = slot.glow & 0x00FFFFFF; Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
                gp.setShader(new RadialGradient(cx, cy, R, new int[]{g | 0x70000000, g | 0x28000000, g}, new float[]{0f, .6f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, R, gp);
            }
            Paint blur = new Paint(); blur.setMaskFilter(new android.graphics.BlurMaskFilter(Math.max(1, m * .07f), android.graphics.BlurMaskFilter.Blur.NORMAL));
            int[] off = new int[2]; Bitmap shadow = art.extractAlpha(blur, off);
            Paint sp = new Paint(Paint.FILTER_BITMAP_FLAG); sp.setColor(0x99000000);
            c.drawBitmap(shadow, pad + off[0], pad + off[1] + m * .05f, sp); shadow.recycle();
            c.drawBitmap(art, pad, pad, null); art.recycle();
            return b;
        }
    }

    // =====================================================================================================
    /** Twinkles, drifting mist, waterfall shimmer and flickering lights from slots.json "motion"; about 30 fps. */
    static final class MotionLayer extends View {
        final PictureHome home; PictureTheme t; float S = 1; boolean running; final long t0 = SystemClock.uptimeMillis();
        float[][] tw = new float[0][]; // x, y, r, phase, speed
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), fall = new Paint(Paint.ANTI_ALIAS_FLAG); final Matrix mx = new Matrix();
        final List<Paint> mistP = new ArrayList<>(), lightP = new ArrayList<>();
        final Runnable frame = new Runnable() { @Override public void run() { if (!running) return; invalidate(); postDelayed(this, 33); } };

        MotionLayer(HomeActivity a, PictureHome home) { super(a); this.home = home; }

        void setTheme(PictureTheme th, float s) {
            t = th; S = s; Random r = new Random(th.id.hashCode()); PictureTheme.Motion m = th.motion;
            tw = new float[m.twinkleRect == null ? 0 : m.twinkles][];
            for (int i = 0; i < tw.length; i++) { float[] q = m.twinkleRect; tw[i] = new float[]{q[0] + r.nextFloat() * q[2], q[1] + r.nextFloat() * q[3], 1.2f + r.nextFloat() * 2f, r.nextFloat() * 6.28f, .5f + r.nextFloat() * 1.2f}; }
            int mc = m.mistColor & 0x00FFFFFF, lc = m.lightColor & 0x00FFFFFF;
            mistP.clear(); for (int i = 0; i < m.mist.size(); i++) { Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG); mp.setShader(new RadialGradient(0, 0, 1, new int[]{mc | 0x30000000, mc | 0x14000000, mc}, null, Shader.TileMode.CLAMP)); mistP.add(mp); }
            lightP.clear(); for (float[] q : m.lights) { Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG); lp.setShader(new RadialGradient(q[0] * S, q[1] * S, Math.max(1, q[2] * S), new int[]{lc | 0x80000000, lc}, null, Shader.TileMode.CLAMP)); lightP.add(lp); }
            fall.setShader(new LinearGradient(0, 0, 0, 1, new int[]{0x00FFFFFF, 0x50FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            invalidate();
        }
        void setRunning(boolean on) { if (on == running) return; running = on; removeCallbacks(frame); if (on) post(frame); else invalidate(); }

        @Override protected void onDraw(Canvas c) {
            if (t == null || !running) return; // still = just the painting
            float time = (SystemClock.uptimeMillis() - t0) / 1000f;
            for (Spot s : home.spots) s.tick(time);
            PictureTheme.Motion m = t.motion;
            // mist and clouds: soft bands that sway slowly sideways (x, y, w, h, amplitude px, period s)
            for (int i = 0; i < m.mist.size(); i++) {
                float[] q = m.mist.get(i); float amp = q.length > 4 ? q[4] : 40, per = q.length > 5 ? q[5] : 40;
                float dx = amp * (float) Math.sin(time * 6.2832f / per + i);
                c.save(); c.translate((q[0] + q[2] / 2 + dx) * S, (q[1] + q[3] / 2) * S); c.scale(q[2] / 2 * S, q[3] / 2 * S); c.drawCircle(0, 0, 1, mistP.get(i)); c.restore();
            }
            // waterfalls: light sliding down the water (x, y, w, h)
            for (float[] q : m.falls) {
                float len = q[3] * .35f;
                c.save(); c.clipRect(q[0] * S, q[1] * S, (q[0] + q[2]) * S, (q[1] + q[3]) * S);
                for (int k = 0; k < 2; k++) {
                    float y = ((time * 80 + k * (q[3] + len) / 2) % (q[3] + len)) - len;
                    mx.setScale(1, len * S); mx.postTranslate(0, (q[1] + y) * S); fall.getShader().setLocalMatrix(mx);
                    c.drawRect(q[0] * S, (q[1] + y) * S, (q[0] + q[2]) * S, (q[1] + y + len) * S, fall);
                }
                c.restore();
            }
            // warm lights (lanterns, windows): uneven flicker
            for (int i = 0; i < m.lights.size(); i++) {
                float[] q = m.lights.get(i);
                float f = .55f + .25f * (float) Math.sin(time * 2.3 + i * 1.9) + .2f * (float) Math.sin(time * 6.1 + i * 4.1);
                Paint lp = lightP.get(i); lp.setAlpha(Math.round(255 * Math.max(0, Math.min(1, f)))); c.drawCircle(q[0] * S, q[1] * S, q[2] * S, lp);
            }
            // twinkles: tiny stars or sparkles that brighten and fade
            p.setColor(m.twinkleColor);
            for (float[] q : tw) {
                float s = (float) Math.sin(time * q[4] + q[3]); if (s <= 0) continue; float al = s * s * s;
                p.setAlpha(Math.round(220 * al)); c.drawCircle(q[0] * S, q[1] * S, q[2] * S * (.6f + .4f * al), p);
                if (al > .6f) { p.setStrokeWidth(Math.max(1, S)); float L = q[2] * 3 * S * al; c.drawLine(q[0] * S - L, q[1] * S, q[0] * S + L, q[1] * S, p); c.drawLine(q[0] * S, q[1] * S - L, q[0] * S, q[1] * S + L, p); }
            }
        }
    }
}
