package io.github.lars21.cozyhometv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ChangedPackages;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cozy Home TV: the home screen.
 *  - Default look (no setup): drawn by the selected Theme (Themes.java). The default "Glass" theme shows bundled
 *    photos that slowly crossfade, a clock top-right, a dock of rounded tiles over a frosted strip and the all-apps
 *    grid below. "Classic" is the 1.0 look without pictures. No music in either.
 *  - Cozy look (opt-in in Settings): the user's own time-of-day picture(s), optional music and card art from a
 *    folder on the TV (see CozyPack).
 * Layout works in a 1920x1080 design space scaled to the real screen with d().
 */
public class HomeActivity extends Activity {
    static final String TAG = "CozyHome";
    static final int W = 1920, H = 1080, COLS = 6;
    static final int TEXT = 0xFFF4F1EA, DIM = 0xB3F4F1EA;

    float S = 1; int screenW, screenH;
    int d(float v) { return Math.round(v * S); }
    float df(float v) { return v * S; }

    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    Prefs prefs; CozyPack pack; String lastProblem; boolean cozyShown; String scenePeriod = ""; long packStamp;
    Typeface caveat, light;
    AudioManager audio;

    // views
    FrameLayout root, sceneLayer; View scrim, bottomShade; FrameLayout ambient; ScrollView scroller; LinearLayout column;
    TextView title, clock, date, favLabel, allLabel, notice; LinearLayout header, favRow, allPanel; GridLayout grid; View spacer;
    TextView inputsBtn; FrameLayout gearBtn; Ui.Frost panelFrost;
    Scene.Backdrop backdrop; Bitmap frostBmp;
    // theme (Default mode)
    Theme theme; String themeKey = ""; Scene.Backdrop themeBg; Bitmap themeFrost; Ui.Frost shelfFrost;
    LinearLayout shelf, clockCol; View headerGap; int keyDx, keyDy;
    final List<ValueAnimator> loops = new ArrayList<>();

    // apps
    List<AppRepo.App> apps = new ArrayList<>(); Map<String, AppRepo.App> byPkg = new HashMap<>();
    final Map<String, Drawable> art = new HashMap<>();          // pkg -> banner or icon card (cached)
    final Map<String, Bitmap> cardArt = new HashMap<>();        // pkg -> user card image (Cozy mode)
    List<String> favorites = new ArrayList<>(); Set<String> hidden;
    String movingPkg; String lastFocusPkg; boolean lastFocusInFav;
    int changeSeq = 0; String tvInputPkg;
    boolean started, resumed, initialFocusDone;

    // music
    MediaPlayer music; int musicPos;
    final AudioManager.OnAudioFocusChangeListener focusL = c -> {
        if (music == null) return;
        if (c == AudioManager.AUDIOFOCUS_GAIN) { music.setVolume(vol(), vol()); if (resumed) music.start(); }
        else if (c == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) music.setVolume(vol() * .2f, vol() * .2f);
        else if (c == AudioManager.AUDIOFOCUS_LOSS) releaseMusic();
        else if (music.isPlaying()) music.pause();
    };

    final BroadcastReceiver pkgReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { refreshApps(); } };
    final BroadcastReceiver timeReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { updateClock(); if (cozyShown) checkPeriod(); } };

    /** Keeps animation lengths right even when the TV's animator scale is changed in developer options. */
    static long dur(long ms) { float sc = ValueAnimator.getDurationScale(); return sc > 0 ? (long) (ms / sc) : ms; }

    // ================= lifecycle =================
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); long t0 = SystemClock.uptimeMillis();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        Point sz = new Point(); getWindowManager().getDefaultDisplay().getRealSize(sz);
        screenW = Math.max(sz.x, sz.y); screenH = Math.min(sz.x, sz.y); S = Math.min(screenW / (float) W, screenH / (float) H);
        try { caveat = Typeface.createFromAsset(getAssets(), "caveat.ttf"); } catch (Exception e) { caveat = Typeface.create("cursive", Typeface.NORMAL); }
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        prefs = new Prefs(this); hidden = prefs.hidden(); audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        theme = Themes.get(prefs.theme()); themeKey = themeKey();
        handleIntent(getIntent());
        buildUi();
        IntentFilter pf = new IntentFilter(); pf.addAction(Intent.ACTION_PACKAGE_ADDED); pf.addAction(Intent.ACTION_PACKAGE_REMOVED); pf.addAction(Intent.ACTION_PACKAGE_CHANGED); pf.addAction(Intent.ACTION_PACKAGE_REPLACED); pf.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, pf, Context.RECEIVER_EXPORTED); else registerReceiver(pkgReceiver, pf);
        ChangedPackages cp0 = getPackageManager().getChangedPackages(0); if (cp0 != null) changeSeq = cp0.getSequenceNumber();
        refreshApps();
        Log.d(TAG, "onCreate " + (SystemClock.uptimeMillis() - t0) + "ms scale=" + S + " screen=" + screenW + "x" + screenH);
    }

    @Override protected void onStart() {
        super.onStart(); started = true;
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK); tf.addAction(Intent.ACTION_TIME_CHANGED); tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, tf);
        updateClock();
        // Apps installed while we were hidden (a missed broadcast) are picked up here.
        ChangedPackages cp = getPackageManager().getChangedPackages(changeSeq);
        if (cp != null) { changeSeq = cp.getSequenceNumber(); refreshApps(); }
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true;
        hidden = prefs.hidden();
        String tk = themeKey();
        if (!tk.equals(themeKey)) { themeKey = tk; theme = Themes.get(prefs.theme()); releaseThemeBg(); applyLook(true); }
        else applyLook(false);
        startMotion();
        setMusic();
        restoreFocus();
    }

    @Override protected void onPause() { resumed = false; releaseMusic(); stopMotion(); super.onPause(); }

    @Override protected void onStop() {
        started = false;
        try { unregisterReceiver(timeReceiver); } catch (Exception ignored) {}
        if (backdrop != null) backdrop.stop();
        if (themeBg != null) themeBg.stop();
        super.onStop();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (!started && (level >= TRIM_MEMORY_BACKGROUND || level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL)) releaseHeavy();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) {}
        releaseMusic(); releaseHeavy(); io.shutdownNow(); super.onDestroy();
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handleIntent(i); if (resumed) { applyLook(false); setMusic(); } closeOverlays(); }

    /** Script hooks: am start -n <pkg>/.HomeActivity --es mode cozy|default --es hide a,b --es unhide a,b --ez reload true */
    void handleIntent(Intent i) {
        if (i == null || prefs == null) return;
        String mode = i.getStringExtra("mode");
        if (Prefs.MODE_COZY.equals(mode) || Prefs.MODE_DEFAULT.equals(mode)) prefs.setMode(mode);
        String hide = i.getStringExtra("hide"), unhide = i.getStringExtra("unhide");
        if (hide != null || unhide != null) {
            Set<String> h = prefs.hidden();
            if (hide != null) h.addAll(Prefs.split(hide));
            if (unhide != null) h.removeAll(Prefs.split(unhide));
            prefs.setHidden(h); hidden = h; if (column != null) refreshApps();
        }
        if (i.getBooleanExtra("reload", false)) packStamp = -1;
    }

    /** Pressing Home (or Back) while on the home screen just returns to the top. */
    void closeOverlays() { movingPkg = null; if (scroller != null) scroller.smoothScrollTo(0, 0); }

    // ================= UI =================
    TextView text(String s, float size, Typeface tf, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(TypedValue.COMPLEX_UNIT_PX, df(size)); t.setTypeface(tf); t.setTextColor(color); return t;
    }

    void buildUi() {
        root = new FrameLayout(this); root.setBackground(theme.baseDrawable());
        sceneLayer = new FrameLayout(this); root.addView(sceneLayer, new FrameLayout.LayoutParams(-1, -1));
        ambient = new FrameLayout(this); root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
        bottomShade = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x66000000, 0xC0000000});
        bottomShade.setBackground(g); root.addView(bottomShade, new FrameLayout.LayoutParams(-1, d(560), Gravity.BOTTOM));
        scrim = new View(this); scrim.setBackgroundColor(0xFF0B0D12); scrim.setAlpha(0f); root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        scroller = new ScrollView(this); scroller.setVerticalScrollBarEnabled(false); scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(false); scroller.setFillViewport(true); scroller.setSmoothScrollingEnabled(true);
        column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setClipChildren(false); column.setClipToPadding(false);
        column.setPadding(d(96), d(48), d(96), d(72));
        scroller.addView(column, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroller, new FrameLayout.LayoutParams(-1, -1));
        scroller.setOnScrollChangeListener((v, x, y, ox, oy) -> onScrolled(y));

        // header: title/clock on the left, Inputs + Settings on the right
        header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.TOP); header.setClipChildren(false);
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); clockCol = left;
        title = text("", 46, caveat, TEXT); title.setShadowLayer(df(6), 0, df(2), 0xA0000000); title.setVisibility(View.GONE);
        clock = text("", 92, light, TEXT); clock.setIncludeFontPadding(false); clock.setShadowLayer(df(8), 0, df(2), 0x80000000); clock.setFontFeatureSettings("tnum");
        date = text("", 28, Typeface.SANS_SERIF, DIM); date.setShadowLayer(df(6), 0, df(2), 0x80000000);
        left.addView(title); left.addView(clock); left.addView(date);
        headerGap = new View(this);
        inputsBtn = pill("Inputs");
        inputsBtn.setOnClickListener(v -> openInputs());
        gearBtn = new FrameLayout(this); gearBtn.setFocusable(true); gearBtn.setClickable(true);
        Ui.Box gb = new Ui.Box(0x33FFFFFF, df(32)).stroke(0x40FFFFFF, Math.max(1, df(1.5f))); gearBtn.setBackground(gb);
        gearBtn.addView(new Ui.Gear(this, TEXT), new FrameLayout.LayoutParams(d(34), d(34), Gravity.CENTER));
        gearBtn.setContentDescription("Settings");
        gearBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        gearBtn.setOnFocusChangeListener((v, f) -> pillFocus(v, f));
        arrangeHeader(false);
        column.addView(header, new LinearLayout.LayoutParams(-1, -2));

        spacer = new View(this); column.addView(spacer, new LinearLayout.LayoutParams(-1, d(56)));
        // shelf = dock row + all apps; the theme may put a frosted panel behind it
        shelf = new LinearLayout(this); shelf.setOrientation(LinearLayout.VERTICAL); shelf.setClipChildren(false); shelf.setClipToPadding(false);
        column.addView(shelf, new LinearLayout.LayoutParams(-1, -2));
        shelfFrost = new Ui.Frost(shelf, theme.layout().panelTint, df(theme.layout().panelRadius));
        favLabel = sectionLabel("Favourites"); shelf.addView(favLabel);
        favRow = new LinearLayout(this); favRow.setOrientation(LinearLayout.HORIZONTAL); favRow.setClipChildren(false); favRow.setClipToPadding(false);
        favRow.setPadding(0, d(14), 0, d(14));
        shelf.addView(favRow, new LinearLayout.LayoutParams(-1, -2));

        allPanel = new LinearLayout(this); allPanel.setOrientation(LinearLayout.VERTICAL); allPanel.setClipChildren(false); allPanel.setClipToPadding(false);
        panelFrost = new Ui.Frost(allPanel, 0x47000000, df(28));
        allLabel = sectionLabel("All apps"); allPanel.addView(allLabel);
        grid = new GridLayout(this); grid.setColumnCount(COLS); grid.setClipChildren(false); grid.setClipToPadding(false); grid.setPadding(0, d(14), 0, d(8));
        allPanel.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams apl = new LinearLayout.LayoutParams(-1, -2); apl.topMargin = d(20);
        shelf.addView(allPanel, apl);

        notice = text("", 24, Typeface.SANS_SERIF, TEXT); notice.setPadding(d(30), d(18), d(30), d(18)); notice.setBackground(new Ui.Box(0xE0202530, df(18))); notice.setVisibility(View.GONE);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); nl.bottomMargin = d(60);
        root.addView(notice, nl);
        setContentView(root);
    }

    /** Classic: big clock top-left, buttons top-right. Glass: buttons top-left, clock top-right. */
    void arrangeHeader(boolean clockRight) {
        header.removeAllViews();
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-2, d(64)); il.rightMargin = d(16); il.topMargin = d(14);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(d(64), d(64)); gl.topMargin = d(14);
        int g = clockRight ? Gravity.END : Gravity.START;
        clockCol.setGravity(g); clock.setGravity(g); date.setGravity(g); title.setGravity(g);
        if (clockRight) {
            header.addView(inputsBtn, il); header.addView(gearBtn, gl);
            header.addView(headerGap, new LinearLayout.LayoutParams(0, 1, 1));
            header.addView(clockCol, new LinearLayout.LayoutParams(-2, -2));
        } else {
            header.addView(clockCol, new LinearLayout.LayoutParams(0, -2, 1));
            header.addView(inputsBtn, il); header.addView(gearBtn, gl);
        }
    }

    /** Layout/card style in use: Cozy mode keeps the classic arrangement over the user's own picture. */
    Theme.Layout L() { return cozyShown ? Themes.CLASSIC.layout() : theme.layout(); }
    Theme.CardStyle C() { return cozyShown ? Themes.CLASSIC.cards() : theme.cards(); }
    String themeKey() { return prefs.theme() + "/" + prefs.wallpaperSeconds() + "/" + prefs.motion(); }

    /** Starts the theme's background (bundled wallpapers) in Default mode. Created lazily, freed in releaseHeavy. */
    void ensureThemeBg() {
        if (cozyShown || themeBg != null || !started || isDestroyed()) return;
        final Scene.Backdrop[] self = new Scene.Backdrop[1];
        themeBg = theme.background(this, screenW, screenH, prefs, fr -> {
            if (themeBg == null || themeBg != self[0] || cozyShown) { if (fr != null) fr.recycle(); return; }
            Bitmap old = themeFrost; themeFrost = fr; shelfFrost.blurred = fr; shelfFrost.screenW = screenW; shelfFrost.screenH = screenH;
            shelf.invalidate(); if (old != null) old.recycle();
        });
        self[0] = themeBg;
        if (themeBg == null) return;
        sceneLayer.addView(themeBg.view(), 0, new FrameLayout.LayoutParams(-1, -1));
        themeBg.start();
    }
    void releaseThemeBg() {
        if (themeBg != null) { sceneLayer.removeView(themeBg.view()); themeBg.release(); themeBg = null; }
        if (shelfFrost != null) shelfFrost.blurred = null;
        if (themeFrost != null) { themeFrost.recycle(); themeFrost = null; }
        if (shelf != null) shelf.invalidate();
    }

    TextView sectionLabel(String s) { TextView t = text(s, 26, Typeface.create("sans-serif-medium", Typeface.NORMAL), DIM); t.setShadowLayer(df(6), 0, df(2), 0x80000000); return t; }

    TextView pill(String s) {
        TextView t = text(s, 24, Typeface.create("sans-serif-medium", Typeface.NORMAL), TEXT); t.setGravity(Gravity.CENTER); t.setPadding(d(28), 0, d(28), 0);
        t.setFocusable(true); t.setClickable(true); t.setBackground(new Ui.Box(0x33FFFFFF, df(32)).stroke(0x40FFFFFF, Math.max(1, df(1.5f))));
        t.setOnFocusChangeListener((v, f) -> pillFocus(v, f)); return t;
    }
    void pillFocus(View v, boolean f) {
        Ui.Box b = (Ui.Box) v.getBackground(); b.set(f ? 0xF2F4F1EA : 0x33FFFFFF, f ? 0xFFFFFFFF : 0x40FFFFFF, Math.max(1, df(1.5f)));
        if (v instanceof TextView) ((TextView) v).setTextColor(f ? 0xFF15181F : TEXT);
        if (v == gearBtn) ((Ui.Gear) gearBtn.getChildAt(0)).setColor(f ? 0xFF15181F : TEXT);
        v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(dur(140)).setInterpolator(new DecelerateInterpolator()).start();
        if (f) click(false);
    }

    void onScrolled(int y) {
        float k = Math.min(1f, y / (float) d(cozyShown ? 420 : 300));
        scrim.setAlpha((cozyShown ? .62f : L().scrimMax) * k);
        if (cozyShown) allPanel.invalidate(); else if (L().frostPanel) shelf.invalidate();
    }

    // ================= look: default vs cozy =================
    /** (Re)applies the look when the mode, the pack folder or the time of day changed. */
    void applyLook(boolean force) {
        boolean wantCozy = prefs.cozy();
        if (wantCozy) {
            File dir = CozyPack.findDir(this); long st = CozyPack.stampOf(dir);
            if (force || pack == null || st != packStamp || !cozyShown) { pack = CozyPack.load(this); packStamp = st; scenePeriod = ""; }
            if (!pack.hasScene()) {
                if (cozyShown || force || backdrop != null) showDefault(); else showDefaultChrome();
                if (!pack.problem.equals(lastProblem)) { lastProblem = pack.problem; notice(pack.problem + " Showing the default look."); }
                return;
            }
            lastProblem = null;
            if (!cozyShown) showCozyChrome();
            checkPeriod();
        } else if (cozyShown || force || backdrop != null) showDefault();
        else showDefaultChrome();
    }

    void showDefault() {
        cozyShown = false; scenePeriod = "";
        releaseHeavy();
        showDefaultChrome();
        rebuildRows();
    }

    void showDefaultChrome() {
        Theme.Layout l = theme.layout();
        root.setBackground(theme.baseDrawable());
        title.setVisibility(View.GONE); clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, df(l.clockSize)); clock.setTypeface(theme.clockFont(this));
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, df(l.dateSize));
        arrangeHeader(l.clockRight);
        bottomShade.setVisibility(l.bottomShade ? View.VISIBLE : View.GONE); onScrolled(scroller.getScrollY());
        spacer.getLayoutParams().height = d(l.dockSpacer); spacer.requestLayout();
        favLabel.setVisibility(l.dockLabel ? View.VISIBLE : View.GONE); allLabel.setVisibility(l.gridLabel ? View.VISIBLE : View.GONE);
        allPanel.setBackground(null); allPanel.setPadding(0, 0, 0, 0);
        ((LinearLayout.LayoutParams) allPanel.getLayoutParams()).topMargin = d(l.gridTopGap); allPanel.requestLayout();
        shelfFrost.tint = l.panelTint; shelfFrost.t.setColor(l.panelTint); shelfFrost.radius = df(l.panelRadius);
        if (l.frostPanel) { shelf.setBackground(shelfFrost); shelf.setPadding(d(28), d(22), d(28), d(40)); }
        else { shelf.setBackground(null); shelf.setPadding(0, 0, 0, 0); }
        ensureThemeBg();
    }

    void showCozyChrome() {
        releaseThemeBg(); // the user's own pack replaces the bundled wallpapers
        cozyShown = true;
        root.setBackground(new Ui.Night()); arrangeHeader(false);
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, df(28));
        shelf.setBackground(null); shelf.setPadding(0, 0, 0, 0);
        favLabel.setVisibility(View.VISIBLE); allLabel.setVisibility(View.VISIBLE);
        ((LinearLayout.LayoutParams) allPanel.getLayoutParams()).topMargin = d(20); allPanel.requestLayout();
        title.setText(pack.title); title.setVisibility(TextUtils.isEmpty(pack.title) ? View.GONE : View.VISIBLE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, df(64)); clock.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        bottomShade.setVisibility(View.VISIBLE);
        // leave most of the picture visible: the favourites row sits in the lower part of the screen
        spacer.getLayoutParams().height = d(360); spacer.requestLayout();
        allPanel.setBackground(panelFrost); allPanel.setPadding(d(28), d(24), d(28), d(28));
        onScrolled(scroller.getScrollY());
        rebuildRows(); // grid tiles are a little narrower inside the glass panel
    }

    /** Loads the picture for the current time of day if it changed. Only one period is ever decoded. */
    void checkPeriod() {
        if (pack == null || !pack.hasScene()) return;
        final String p = pack.periodNow(prefs.period());
        final File img = pack.scene(p);
        if (p.equals(scenePeriod) && backdrop != null) { if (started) backdrop.start(); return; }
        boolean sameFile = backdrop != null && img != null && img.equals(pack.scene(scenePeriod)) && !(backdrop instanceof Scene.LiveBackdrop) && pack.liveBase.get(p) == null;
        scenePeriod = p;
        if (sameFile) { setAmbientForPeriod(p); return; }
        final boolean live = pack.liveEnabled && pack.liveBase.get(p) != null;
        final Scene.Backdrop nb = live ? new Scene.LiveBackdrop(this, pack.liveBase.get(p), pack.liveMoments.get(p), pack.liveGapMin, pack.liveGapMax) : new Scene.StillBackdrop(this);
        final Scene.StillBackdrop still = live ? ((Scene.LiveBackdrop) nb).still : (Scene.StillBackdrop) nb;
        final int w = screenW, h = screenH; final CozyPack pk = pack;
        io.execute(() -> {
            long t = SystemClock.uptimeMillis();
            Bitmap b = Scene.decodeCover(img, w, h, true);
            Bitmap fr = Scene.decodeFrost(img, w, h);
            final Map<String, Bitmap> cards = new HashMap<>();
            int cw = d(TILE_W_FAV), ch = d(TILE_H_FAV);
            for (Map.Entry<String, File> e : pk.cards.entrySet()) { Bitmap cb = Scene.decodeCover(e.getValue(), cw, ch, true); if (cb != null) cards.put(e.getKey(), cb); }
            Log.d(TAG, "scene " + p + " decoded in " + (SystemClock.uptimeMillis() - t) + "ms " + (b == null ? "FAILED" : b.getWidth() + "x" + b.getHeight()));
            ui.post(() -> {
                if (!p.equals(scenePeriod) || !cozyShown || isDestroyed()) { if (b != null) b.recycle(); if (fr != null) fr.recycle(); for (Bitmap cb : cards.values()) cb.recycle(); return; }
                if (b == null) { notice("Could not open " + img.getName() + ". Showing the default look."); prefs.setMode(Prefs.MODE_DEFAULT); showDefault(); return; }
                Scene.Backdrop old = backdrop; backdrop = nb; still.set(b);
                View nv = nb.view(); nv.setAlpha(0f); sceneLayer.addView(nv, new FrameLayout.LayoutParams(-1, -1));
                nv.animate().alpha(1f).setDuration(dur(old == null ? 220 : 600)).withEndAction(() -> { if (old != null) { sceneLayer.removeView(old.view()); old.release(); } }).start();
                Bitmap oldFrost = frostBmp; frostBmp = fr; panelFrost.blurred = fr; panelFrost.screenW = screenW; panelFrost.screenH = screenH; allPanel.invalidate(); if (oldFrost != null) oldFrost.recycle();
                for (Bitmap cb : cardArt.values()) cb.recycle(); cardArt.clear(); cardArt.putAll(cards);
                setAmbientForPeriod(p);
                rebuildRows();
                if (started) nb.start();
            });
        });
    }

    /** Frees pictures, video and fireflies. The default look needs none of them. */
    void releaseHeavy() {
        releaseThemeBg();
        if (backdrop != null) { sceneLayer.removeView(backdrop.view()); backdrop.release(); backdrop = null; }
        sceneLayer.removeAllViews();
        panelFrost.blurred = null; if (frostBmp != null) frostBmp.recycle(); frostBmp = null;
        boolean hadCards = !cardArt.isEmpty();
        for (Bitmap b : cardArt.values()) b.recycle(); cardArt.clear();
        clearAmbient(); scenePeriod = "";
        if (hadCards && favRow != null) rebuildRows();
    }

    // ================= fireflies (Cozy, optional) =================
    void setAmbientForPeriod(String p) {
        clearAmbient();
        if (!cozyShown || pack == null || !pack.fireflies || !prefs.motion()) return;
        ambient.setAlpha(p.equals("morning") || p.equals("day") ? .18f : 1f);
        float[][] fl = {{44, 30, 0}, {52, 26, 6000}, {60, 33, 10000}, {56, 38, 3000}, {30, 22, 8000}, {72, 24, 4500}};
        for (float[] f : fl) {
            final Ui.Dot dot = new Ui.Dot(this, df(2.4f), df(11));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(d(30), d(30)); lp.leftMargin = d(f[0] / 100 * W); lp.topMargin = d(f[1] / 100 * H);
            ambient.addView(dot, lp);
            ValueAnimator a = ValueAnimator.ofFloat(0, 1); a.setDuration(dur(14000)); a.setRepeatCount(ValueAnimator.INFINITE); a.setInterpolator(new LinearInterpolator());
            a.addUpdateListener(an -> { float t = (float) an.getAnimatedValue(); float u = t < .5f ? t * 2 : (1 - t) * 2; float e = (float) (1 - Math.cos(Math.PI * u)) / 2; dot.setTranslationX(df(40) * e); dot.setTranslationY(-df(24) * e); dot.setAlpha(.2f + .6f * e); });
            a.setCurrentPlayTime((long) f[2]); loops.add(a);
        }
        if (resumed) startMotion();
    }
    void clearAmbient() { for (ValueAnimator a : loops) a.cancel(); loops.clear(); ambient.removeAllViews(); }
    void startMotion() { if (!prefs.motion()) { for (ValueAnimator a : loops) a.pause(); return; } for (ValueAnimator a : loops) { if (!a.isStarted()) a.start(); else if (a.isPaused()) a.resume(); } }
    void stopMotion() { for (ValueAnimator a : loops) a.pause(); }

    // ================= music (Cozy, optional) =================
    float vol() { int v = prefs.volume(); if (v < 0) v = pack != null ? pack.defaultVolume : 40; float k = v / 100f; return k * k; } // perceptual curve
    void setMusic() {
        boolean want = resumed && cozyShown && pack != null && pack.music != null && pack.musicEnabled && prefs.music();
        if (!want) { releaseMusic(); audio.abandonAudioFocus(focusL); return; }
        try {
            if (music == null) {
                music = new MediaPlayer();
                music.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                music.setDataSource(pack.music.getPath()); music.setLooping(true); music.prepare();
                if (musicPos > 0) music.seekTo(musicPos);
            }
            music.setVolume(vol(), vol());
            if (audio.requestAudioFocus(focusL, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED && !music.isPlaying()) music.start();
        } catch (Exception e) { Log.e(TAG, "music", e); releaseMusic(); notice("Music could not start (" + pack.music.getName() + ")"); }
    }
    void releaseMusic() {
        if (music == null) return;
        try { musicPos = music.getCurrentPosition(); } catch (Exception ignored) {}
        try { music.stop(); } catch (Exception ignored) {}
        music.release(); music = null;
    }

    // ================= apps =================
    void refreshApps() {
        io.execute(() -> {
            List<AppRepo.App> list = AppRepo.scan(this);
            String tvIn = AppRepo.tvInputApp(getPackageManager());
            ui.post(() -> { if (isDestroyed()) return; apps = list; byPkg = AppRepo.index(list); tvInputPkg = tvIn; seedFavorites(); rebuildRows(); loadArt(); });
        });
    }

    void seedFavorites() {
        favorites = prefs.favorites();
        if (!prefs.favoritesSeeded()) {
            List<String> seed = new ArrayList<>();
            CozyPack p = pack != null ? pack : (prefs.cozy() ? CozyPack.load(this) : null);
            if (p != null && p.favorites != null) seed.addAll(Arrays.asList(p.favorites));
            if (seed.isEmpty()) seed.addAll(Arrays.asList(AppRepo.SEED));
            List<String> fav = new ArrayList<>();
            for (String s : seed) if (byPkg.containsKey(s) && fav.size() < 6) fav.add(s);
            for (AppRepo.App a : apps) { if (fav.size() >= 4) break; if (a.tv && !fav.contains(a.pkg)) fav.add(a.pkg); }
            favorites = fav; prefs.setFavorites(fav);
        }
    }

    static final int MAX_FAV = 5; // 5 dock tiles + gaps fit the 1920 design width
    /** Favourites that are installed, in order, at most MAX_FAV. */
    List<String> visibleFavorites() { List<String> v = new ArrayList<>(); for (String p : favorites) { if (byPkg.containsKey(p)) v.add(p); if (v.size() >= MAX_FAV) break; } return v; }

    static final float TILE_W_FAV = 300, TILE_H_FAV = 169, GAP = 24;
    float gridTileW() { Theme.Layout l = L(); int cols = l.gridCols; return (W - 2 * 96 - (cols - 1) * l.gridGap - (cozyShown || l.frostPanel ? 56 : 0)) / (float) cols; }

    void rebuildRows() {
        if (favRow == null) return;
        View f = getCurrentFocus(); String keepPkg = null; boolean keepFav = false;
        if (f != null && f.getTag() instanceof String) { keepPkg = (String) f.getTag(); keepFav = f.getParent() == favRow; }
        favRow.removeAllViews(); grid.removeAllViews();
        Theme.Layout l = L(); int cols = l.gridCols; grid.setColumnCount(cols);
        int shown = 0;
        for (String pkg : visibleFavorites()) {
            View t = tile(byPkg.get(pkg), l.dockW, l.dockH, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); if (shown > 0) lp.leftMargin = d(l.dockGap);
            favRow.addView(t, lp); shown++;
        }
        favLabel.setVisibility(shown == 0 || !l.dockLabel ? View.GONE : View.VISIBLE); favRow.setVisibility(shown == 0 ? View.GONE : View.VISIBLE);
        float tw = gridTileW(), th = tw * 9 / 16; int i = 0;
        for (AppRepo.App a : apps) {
            if (hidden.contains(a.pkg)) continue;
            View t = tile(a, tw, th, false);
            GridLayout.LayoutParams gl = new GridLayout.LayoutParams(); gl.width = -2; gl.height = -2;
            gl.setMargins(i % cols == 0 ? 0 : d(l.gridGap), i < cols ? 0 : d(l.gridGap), 0, 0);
            grid.addView(t, gl); i++;
        }
        inputsBtn.setVisibility(tvInputPkg == null ? View.GONE : View.VISIBLE);
        if (keepPkg != null) focusPkg(keepPkg, keepFav);
        else if (getCurrentFocus() == null || (!initialFocusDone && (favRow.getChildCount() > 0 || grid.getChildCount() > 0))) restoreFocus();
        if (favRow.getChildCount() > 0 || grid.getChildCount() > 0) initialFocusDone = true;
    }

    /**
     * One app tile: rounded 16:9 card + name, styled by the theme's CardStyle. Focus = scale up with a soft shadow,
     * optionally a small tilt towards where you came from that settles, a parallax nudge of the picture and a light sweep.
     * Everything animates view properties only (no relayout), so D-pad focus stays instant.
     */
    View tile(final AppRepo.App a, float w, float h, final boolean fav) {
        final Theme.CardStyle cs = C();
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setClipChildren(false); box.setClipToPadding(false);
        box.setFocusable(true); box.setClickable(true); box.setTag(a.pkg);
        final FrameLayout card = new FrameLayout(this); final float r = df(cs.radius);
        card.setOutlineProvider(new ViewOutlineProvider() { @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); } });
        card.setClipToOutline(true); card.setElevation(df(cs.restElevation));
        if (Build.VERSION.SDK_INT >= 28) { card.setOutlineSpotShadowColor(0xFF000000); card.setOutlineAmbientShadowColor(0xFF000000); }
        card.setCameraDistance(12000 * getResources().getDisplayMetrics().density);
        card.setBackground(new Ui.Box(0xFF262B36, r));
        final ImageView iv = new ImageView(this); iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap user = cardArt.get(a.pkg);
        if (user != null) iv.setImageBitmap(user); else { Drawable dr = art.get(a.pkg); if (dr != null) iv.setImageDrawable(dr); }
        card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        final View gloss, sweep;
        if (cs.shine) {
            gloss = new View(this); gloss.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x2EFFFFFF, 0x08FFFFFF, 0x00FFFFFF})); gloss.setAlpha(0f);
            card.addView(gloss, new FrameLayout.LayoutParams(-1, -1));
            sweep = new View(this); sweep.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF})); sweep.setAlpha(0f);
            sweep.setRotation(14f);
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(d(w * .32f), d(h * 1.6f), Gravity.CENTER_VERTICAL); card.addView(sweep, sl);
        } else { gloss = null; sweep = null; }
        final View ring;
        if (cs.ringWidth > 0) { ring = new View(this); ring.setBackground(new Ui.Box(0, r).stroke(cs.ringColor, df(cs.ringWidth))); ring.setAlpha(0f); card.addView(ring, new FrameLayout.LayoutParams(-1, -1)); }
        else ring = null;
        card.setAlpha(cs.unfocusedAlpha); // unfocused tiles sit back a little so the focused one stands out
        box.addView(card, new LinearLayout.LayoutParams(d(w), d(h)));
        final TextView name = text(a.name, 22, Typeface.SANS_SERIF, DIM); name.setSingleLine(); name.setEllipsize(TextUtils.TruncateAt.END); name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setShadowLayer(df(5), 0, df(2), 0xA0000000);
        if (cs.namesOnFocusOnly) name.setAlpha(0f);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(d(w), -2); nl.topMargin = d(cs.namesOnFocusOnly ? 18 : 12); box.addView(name, nl);
        box.setOnFocusChangeListener((v, f) -> {
            boolean moving = a.pkg.equals(movingPkg);
            float sc = f ? (moving ? cs.focusScale + .04f : cs.focusScale) : 1f;
            android.animation.TimeInterpolator ease = new DecelerateInterpolator(1.8f);
            long ms = dur(f ? cs.focusMs : 150);
            if (f && cs.tiltDeg > 0 && (keyDx != 0 || keyDy != 0)) {
                card.setRotationY(-keyDx * cs.tiltDeg); card.setRotationX(keyDy * cs.tiltDeg * .7f);
                iv.setTranslationX(-keyDx * df(10)); iv.setTranslationY(-keyDy * df(6));
            }
            card.animate().scaleX(sc).scaleY(sc).alpha(f ? 1f : cs.unfocusedAlpha).translationY(f ? -df(cs.lift) : 0).rotationX(0f).rotationY(0f)
                .setDuration(ms).setInterpolator(ease).start();
            iv.animate().translationX(0).translationY(0).scaleX(f && cs.shine ? 1.04f : 1f).scaleY(f && cs.shine ? 1.04f : 1f).setDuration(dur(f ? cs.focusMs + 120 : 150)).setInterpolator(ease).start();
            if (ring != null) ring.animate().alpha(f ? 1f : 0f).setDuration(dur(120)).start();
            if (gloss != null) gloss.animate().alpha(f ? 1f : 0f).setDuration(dur(200)).start();
            if (sweep != null) {
                sweep.animate().cancel();
                if (f) { sweep.setTranslationX(-d(w * .45f)); sweep.setAlpha(1f); sweep.animate().translationX(d(w * 1.1f)).setDuration(dur(650)).setStartDelay(dur(60)).setInterpolator(new DecelerateInterpolator()).withEndAction(() -> sweep.setAlpha(0f)).start(); }
                else sweep.setAlpha(0f);
            }
            card.setElevation(f ? df(cs.focusElevation) : df(cs.restElevation)); box.setZ(f ? 1 : 0);
            name.setTextColor(f ? TEXT : DIM);
            if (cs.namesOnFocusOnly) name.animate().alpha(f ? 1f : 0f).setDuration(dur(f ? 200 : 100)).start();
            if (f) { lastFocusPkg = a.pkg; lastFocusInFav = fav; click(false); }
        });
        box.setOnClickListener(v -> { if (movingPkg != null) { endMove(); return; } launch(a, card); });
        box.setOnLongClickListener(v -> { appMenu(a, fav); return true; });
        return box;
    }

    /** Banners (or an icon on a soft card when the app has no banner), loaded off the main thread once and cached. */
    void loadArt() {
        final List<AppRepo.App> need = new ArrayList<>(); for (AppRepo.App a : apps) if (!art.containsKey(a.pkg)) need.add(a);
        if (need.isEmpty()) return;
        final PackageManager pm = getPackageManager();
        io.execute(() -> {
            final Map<String, Drawable> got = new HashMap<>();
            for (AppRepo.App a : need) {
                Drawable b = AppRepo.banner(pm, a);
                got.put(a.pkg, b != null ? b : new IconCard(AppRepo.icon(pm, a), a.pkg));
            }
            ui.post(() -> { if (isDestroyed()) return; art.putAll(got); applyArt(favRow); applyArt(grid); });
        });
    }
    void applyArt(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View t = g.getChildAt(i); if (!(t.getTag() instanceof String)) continue; String pkg = (String) t.getTag();
            ImageView iv = (ImageView) ((ViewGroup) ((ViewGroup) t).getChildAt(0)).getChildAt(0);
            if (iv.getDrawable() == null) { Drawable dr = art.get(pkg); if (dr != null) iv.setImageDrawable(dr); }
        }
    }

    /** App icon centred on a calm colour picked from the package name (for apps without a TV banner). */
    static final class IconCard extends Drawable {
        final Drawable icon; final android.graphics.Paint bg = new android.graphics.Paint();
        static final int[] PALETTE = {0xFF2E3A4F, 0xFF3B2F4A, 0xFF27443F, 0xFF4A3A2A, 0xFF2F3F52, 0xFF452E36};
        IconCard(Drawable icon, String pkg) { this.icon = icon; bg.setColor(PALETTE[Math.abs(pkg.hashCode()) % PALETTE.length]); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); c.drawRect(b, bg);
            if (icon == null) return; int s = Math.round(b.height() * .52f); int l = b.centerX() - s / 2, t = b.centerY() - s / 2;
            icon.setBounds(l, t, l + s, t + s); icon.draw(c);
        }
        @Override public int getIntrinsicWidth() { return 320; }
        @Override public int getIntrinsicHeight() { return 180; }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    void launch(AppRepo.App a, View from) {
        click(true);
        try {
            Bundle opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
            startActivity(a.launch, opts);
        } catch (Exception e) { notice("Could not open " + a.name); }
    }

    void openInputs() {
        if (tvInputPkg == null) { notice("No TV input app found"); return; }
        PackageManager pm = getPackageManager(); Intent i = pm.getLeanbackLaunchIntentForPackage(tvInputPkg); if (i == null) i = pm.getLaunchIntentForPackage(tvInputPkg);
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { notice("Could not open the TV inputs"); }
    }

    // ================= menu: favourites, reorder, hide =================
    void appMenu(final AppRepo.App a, final boolean inFav) {
        final boolean isFav = favorites.contains(a.pkg);
        final List<String> items = new ArrayList<>();
        if (inFav) { items.add("Move"); items.add("Remove from favourites"); }
        else items.add(isFav ? "Remove from favourites" : "Add to favourites");
        items.add("Hide app"); items.add("App info"); items.add("Uninstall");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(a.name)
            .setItems(items.toArray(new String[0]), (dlg, which) -> {
                String it = items.get(which);
                switch (it) {
                    case "Move": startMove(a.pkg); break;
                    case "Add to favourites": favorites.remove(a.pkg); favorites.add(a.pkg); if (visibleFavorites().size() >= MAX_FAV && !visibleFavorites().contains(a.pkg)) notice("Only the first 5 favourites are shown. Use Move to reorder."); prefs.setFavorites(favorites); rebuildRows(); break;
                    case "Remove from favourites": favorites.remove(a.pkg); prefs.setFavorites(favorites); rebuildRows(); break;
                    case "Hide app": hidden.add(a.pkg); favorites.remove(a.pkg); prefs.setHidden(hidden); prefs.setFavorites(favorites); rebuildRows(); notice(a.name + " hidden. Unhide it in Settings > Hidden apps."); break;
                    case "App info": try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { notice("App info is not available"); } break;
                    case "Uninstall": try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { notice("Uninstall is not available"); } break;
                }
            }).show();
    }

    void startMove(String pkg) { movingPkg = pkg; rebuildRows(); focusPkg(pkg, true); notice("Use ◀ ▶ to move, OK to finish"); }
    void endMove() { movingPkg = null; prefs.setFavorites(favorites); View f = getCurrentFocus(); if (f != null) { f.clearFocus(); } rebuildRows(); if (lastFocusPkg != null) focusPkg(lastFocusPkg, true); notice.setVisibility(View.GONE); }
    void moveBy(int delta) {
        List<String> vis = visibleFavorites(); int i = vis.indexOf(movingPkg); int j = i + delta; if (i < 0 || j < 0 || j >= vis.size()) return;
        vis.remove(i); vis.add(j, movingPkg);
        List<String> rest = new ArrayList<>(favorites); rest.removeAll(vis); vis.addAll(rest); favorites = vis;
        rebuildRows(); focusPkg(movingPkg, true); click(false);
    }

    void focusPkg(String pkg, boolean inFav) {
        ViewGroup g = inFav ? favRow : grid;
        for (int i = 0; i < g.getChildCount(); i++) if (pkg.equals(g.getChildAt(i).getTag())) { g.getChildAt(i).requestFocus(); return; }
        if (inFav) focusPkg(pkg, false);
    }
    void restoreFocus() {
        if (favRow == null) return;
        if (lastFocusPkg != null) { focusPkg(lastFocusPkg, lastFocusInFav); if (getCurrentFocus() != null && getCurrentFocus().getTag() != null) return; }
        if (favRow.getChildCount() > 0) favRow.getChildAt(0).requestFocus(); else if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus(); else gearBtn.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            keyDx = k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
            keyDy = k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : k == KeyEvent.KEYCODE_DPAD_UP ? -1 : 0;
        }
        if (movingPkg != null && e.getAction() == KeyEvent.ACTION_DOWN) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) { moveBy(-1); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { moveBy(1); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (k == KeyEvent.KEYCODE_BACK) { endMove(); return true; }
        }
        if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_DOWN) return true;
        if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
            View f = getCurrentFocus();
            if (f != null && f.getTag() instanceof String) { AppRepo.App a = byPkg.get(f.getTag()); if (a != null) appMenu(a, f.getParent() == favRow); }
            else startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) { if (e.getAction() == KeyEvent.ACTION_UP) { closeOverlays(); restoreTop(); } return true; } // a launcher never closes on Back
        return super.dispatchKeyEvent(e);
    }
    void restoreTop() { if (favRow.getChildCount() > 0) favRow.getChildAt(0).requestFocus(); }

    // ================= small things =================
    void updateClock() {
        if (clock == null) return;
        Boolean c24 = prefs.clock24(); boolean h24 = c24 != null ? c24 : DateFormat.is24HourFormat(this);
        Calendar now = Calendar.getInstance();
        clock.setText(DateFormat.format(h24 ? "H:mm" : "h:mm", now));
        date.setText(DateFormat.format("EEEE, d MMMM", now));
    }

    void click(boolean select) {
        if (!prefs.sounds() || !resumed) return;
        Theme.Sounds snd = theme.sounds();
        audio.playSoundEffect(select ? snd.select : snd.focus, snd.volume);
    }

    Runnable noticeHide;
    void notice(String m) {
        if (notice == null) return;
        notice.setText(m); notice.setVisibility(View.VISIBLE); ui.removeCallbacks(noticeHide);
        noticeHide = () -> notice.setVisibility(View.GONE); ui.postDelayed(noticeHide, 4500);
    }
}
