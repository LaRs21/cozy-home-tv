package io.github.lars21.cozyhometv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Settings panel (translucent, slides over the home screen). D-pad: Up/Down to move, OK to change, Left/Right for volume. */
public class SettingsActivity extends Activity {
    static final int TEXT = 0xFFF4F1EA, DIM = 0xA6F4F1EA;
    float S = 1; int d(float v) { return Math.round(v * S); }
    Prefs prefs; LinearLayout list; TextView hint;
    final List<Row> rows = new ArrayList<>();

    final class Row {
        final String key; final LinearLayout view; final TextView label, value;
        Row(String key, String label) {
            this.key = key; view = new LinearLayout(SettingsActivity.this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(d(28), d(20), d(28), d(20)); view.setFocusable(true); view.setClickable(true);
            final Ui.Box bg = new Ui.Box(0, d(14)); view.setBackground(bg);
            this.label = text(label, 26, TEXT); value = text("", 24, DIM); value.setGravity(Gravity.END);
            view.addView(this.label, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2); vl.leftMargin = d(20); view.addView(value, vl);
            view.setOnFocusChangeListener((v, f) -> { bg.set(f ? 0xF2F4F1EA : 0, 0, 0); this.label.setTextColor(f ? 0xFF15181F : TEXT); value.setTextColor(f ? 0xFF3A3F4A : DIM); if (f) hint.setText(help(key)); });
            view.setOnClickListener(v -> activate(key));
            view.setOnKeyListener((v, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) { step(key, code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1); return true; }
                return false;
            });
        }
    }

    TextView text(String s, float size, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(TypedValue.COMPLEX_UNIT_PX, d(size)); t.setTextColor(color); t.setTypeface(Typeface.SANS_SERIF); return t; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Point sz = new Point(); getWindowManager().getDefaultDisplay().getRealSize(sz); S = Math.min(Math.max(sz.x, sz.y) / 1920f, Math.min(sz.x, sz.y) / 1080f);
        prefs = new Prefs(this);
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(0x99000000);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(d(40), d(48), d(40), d(40));
        panel.setBackground(new Ui.Box(0xF2181C24, 0));
        TextView h = text("Settings", 44, TEXT); h.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)); panel.addView(h);
        TextView sub = text("Cozy Home TV " + version(), 22, DIM); LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2); sl.bottomMargin = d(24); panel.addView(sub, sl);
        ScrollView sc = new ScrollView(this); sc.setVerticalScrollBarEnabled(false); sc.setClipToPadding(false);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); sc.addView(list);
        panel.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        hint = text("", 21, DIM); hint.setLineSpacing(0, 1.15f); LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2); hl.topMargin = d(20); panel.addView(hint, hl);
        root.addView(panel, new FrameLayout.LayoutParams(d(760), -1, Gravity.END));
        setContentView(root);
        build();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    void build() {
        list.removeAllViews(); rows.clear();
        add("mode", "Look");
        add("theme", "Theme");
        add("wallpaper", "Change picture");
        add("folder", "Cozy pack");
        add("period", "Time of day");
        add("music", "Music");
        add("volume", "Music volume");
        add("motion", "Little movements");
        add("sounds", "Navigation sounds");
        add("clock", "Clock");
        add("hidden", "Hidden apps");
        add("homefix", "Home button fix");
        add("dream", "Screensaver");
        if (CozyPack.needsAllFilesAccess()) add("access", "Allow reading /sdcard/CozyHome");
        add("tv", "TV settings");
        add("about", "About");
        refresh();
        if (!rows.isEmpty()) rows.get(0).view.requestFocus();
    }
    void add(String key, String label) { Row r = new Row(key, label); rows.add(r); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = d(4); list.addView(r.view, lp); }

    void refresh() {
        CozyPack pack = prefs.cozy() || CozyPack.findDir(this) != null ? CozyPack.load(this) : null;
        for (Row r : rows) {
            String v = "";
            switch (r.key) {
                case "mode": v = prefs.cozy() ? "Cozy" : "Default"; break;
                case "theme": v = Themes.get(prefs.theme()).label(); break;
                case "wallpaper": { int sec = prefs.wallpaperSeconds(); v = sec == 0 ? "Never" : sec < 60 ? "Every " + sec + " s" : "Every " + (sec / 60) + " min"; break; }
                case "folder": v = pack == null || pack.dir == null ? "Not found" : pack.hasScene() ? pack.scenes.size() + " pictures" + (pack.music != null ? " + music" : "") : "No pictures"; break;
                case "period": { String p = prefs.period(); v = p.equals("auto") ? "Follow the clock" : cap(p); break; }
                case "music": v = prefs.music() ? "On" : "Off"; break;
                case "volume": { int vol = prefs.volume(); if (vol < 0) vol = pack != null ? pack.defaultVolume : 40; v = "◀ " + vol + "% ▶"; break; }
                case "motion": v = prefs.motion() ? "On" : "Off"; break;
                case "sounds": v = prefs.sounds() ? "On" : "Off"; break;
                case "clock": { Boolean c = prefs.clock24(); v = c == null ? "Follow system" : c ? "24-hour" : "12-hour"; break; }
                case "hidden": v = String.valueOf(prefs.hidden().size()); break;
                case "homefix": v = homeFixOn() ? "On" : "Off"; break;
                case "dream": v = dreamOn() ? "Cozy Home TV" : "Other"; break;
                case "access": v = "Open"; break;
                case "tv": case "about": v = "›"; break;
            }
            r.value.setText(v);
            boolean cozyOnly = r.key.equals("period") || r.key.equals("music") || r.key.equals("volume");
            boolean defaultOnly = r.key.equals("wallpaper");
            r.view.setAlpha((cozyOnly && !prefs.cozy()) || (defaultOnly && prefs.cozy()) ? .55f : 1f);
        }
    }

    void activate(String key) {
        switch (key) {
            case "mode": {
                if (!prefs.cozy()) {
                    CozyPack p = CozyPack.load(this);
                    if (!p.hasScene()) { info("Cozy look needs pictures", "Put your pictures in /sdcard/CozyHome/ on the TV (morning.jpg, day.jpg, evening.jpg, night.jpg, or just room.jpg) and optional music.mp3.\n\nFrom a computer: scripts/push-cozy-pack.sh <TV-IP> <your-folder>\n\n" + (p.problem == null ? "" : p.problem)); return; }
                    prefs.setMode(Prefs.MODE_COZY);
                } else prefs.setMode(Prefs.MODE_DEFAULT);
                break;
            }
            case "folder": {
                CozyPack p = CozyPack.load(this); File[] dirs = CozyPack.candidateDirs(this);
                StringBuilder sb = new StringBuilder("The app looks in:\n");
                for (File f : dirs) if (f != null) sb.append("  ").append(f.getPath()).append(f.isDirectory() ? (f.canRead() ? "  (found)" : "  (no permission)") : "").append('\n');
                sb.append('\n');
                if (p.dir != null) sb.append("Using ").append(p.dir.getPath()).append("\nPictures: ").append(TextUtils.join(", ", p.scenes.keySet())).append("\nMusic: ").append(p.music == null ? "none" : p.music.getName()).append("\nCard pictures: ").append(p.cards.size()).append(p.liveEnabled && !p.liveBase.isEmpty() ? "\nLive background: on (experimental)" : "");
                if (p.problem != null) sb.append("\n\n").append(p.problem);
                sb.append("\n\nChanges are picked up automatically when you go back home.");
                info("Cozy pack", sb.toString()); return;
            }
            case "theme": themePicker(); return;
            case "wallpaper": { int[] o = Prefs.WALLPAPER_SECONDS; int i = 0; for (int j = 0; j < o.length; j++) if (o[j] == prefs.wallpaperSeconds()) i = j; prefs.setWallpaperSeconds(o[(i + 1) % o.length]); break; }
            case "period": { String[] ps = Prefs.PERIODS; int i = indexOf(ps, prefs.period()); prefs.setPeriod(ps[(i + 1) % ps.length]); break; }
            case "music": prefs.setMusic(!prefs.music()); break;
            case "volume": { int v = prefs.volume(); if (v < 0) v = CozyPack.load(this).defaultVolume; prefs.setVolume(v >= 100 ? 0 : v + 10); refresh(); return; }
            case "motion": prefs.setMotion(!prefs.motion()); break;
            case "sounds": prefs.setSounds(!prefs.sounds()); break;
            case "clock": { Boolean c = prefs.clock24(); prefs.setClock24(c == null ? Boolean.FALSE : !c ? Boolean.TRUE : null); break; }
            case "hidden": hiddenDialog(); return;
            case "homefix": homeFixDialog(); return;
            case "dream": openSettings(Settings.ACTION_DREAM_SETTINGS, "Screensaver settings are not available here. Use scripts/set-home.sh --screensaver."); return;
            case "access": {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { info("Allow access from a computer", "This TV has no screen for it. Run:\n\nscripts/push-cozy-pack.sh <TV-IP> <your-folder>\n\nor\n\nadb shell appops set --uid " + getPackageName() + " MANAGE_EXTERNAL_STORAGE allow"); }
                return;
            }
            case "tv": openSettings(Settings.ACTION_SETTINGS, "TV settings are not available"); return;
            case "about": info("Cozy Home TV " + version(), "A fast, clean home screen for Android TV and Google TV, with an optional cozy look made from your own pictures and music.\n\nOpen source (MIT licence)\ngithub.com/LaRs21/cozy-home-tv\n\nWallpapers: public-domain (CC0) photos, see CREDITS.md.\nCaveat font: SIL Open Font License 1.1."); return;
        }
        refresh();
    }

    void step(String key, int dir) {
        if (!key.equals("volume")) return;
        int v = prefs.volume(); if (v < 0) { CozyPack p = CozyPack.load(this); v = p.defaultVolume; }
        int nv = Math.max(0, Math.min(100, v + dir * 5));
        prefs.setVolume(nv); refresh();
    }

    /** Theme picker: every theme with a small preview picture. Thumbnails are decoded small and freed on close. */
    void themePicker() {
        final Theme[] all = Themes.ALL; final Bitmap[] thumbs = new Bitmap[all.length]; final String cur = Themes.get(prefs.theme()).id();
        for (int i = 0; i < all.length; i++) {
            try (java.io.InputStream in = getAssets().open(all[i].previewAsset())) {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options(); o.inPreferredConfig = Bitmap.Config.RGB_565;
                thumbs[i] = android.graphics.BitmapFactory.decodeStream(in, null, o);
            } catch (Exception ignored) {}
        }
        android.widget.BaseAdapter ad = new android.widget.BaseAdapter() {
            public int getCount() { return all.length; }
            public Object getItem(int i) { return all[i]; }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, android.view.ViewGroup parent) {
                LinearLayout row = new LinearLayout(SettingsActivity.this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(d(24), d(14), d(24), d(14));
                android.widget.ImageView iv = new android.widget.ImageView(SettingsActivity.this); iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                if (thumbs[i] != null) iv.setImageBitmap(thumbs[i]); else iv.setBackgroundColor(0xFF20242C);
                iv.setClipToOutline(true); iv.setOutlineProvider(new android.view.ViewOutlineProvider() { @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), d(10)); } });
                row.addView(iv, new LinearLayout.LayoutParams(d(192), d(108)));
                LinearLayout col = new LinearLayout(SettingsActivity.this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(d(24), 0, 0, 0);
                TextView t = text((all[i].id().equals(cur) ? "✓  " : "") + all[i].label(), 28, TEXT); col.addView(t);
                TextView sd = text(all[i].description(), 20, DIM); sd.setMaxLines(2); col.addView(sd);
                row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
                return row;
            }
        };
        int sel = 0; for (int i = 0; i < all.length; i++) if (all[i].id().equals(cur)) sel = i;
        AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Theme")
            .setAdapter(ad, (dd, which) -> { prefs.setTheme(all[which].id()); refresh(); })
            .setNegativeButton("Cancel", null).create();
        dlg.setOnDismissListener(dd -> { for (int i = 0; i < thumbs.length; i++) { if (thumbs[i] != null) thumbs[i].recycle(); thumbs[i] = null; } });
        dlg.show();
        final int s0 = sel; dlg.getListView().post(() -> { dlg.getListView().setSelection(s0); dlg.getListView().requestFocus(); });
    }

    void hiddenDialog() {
        final Set<String> h = prefs.hidden();
        if (h.isEmpty()) { info("Hidden apps", "No hidden apps. On the home screen, hold OK on an app (or press Menu) and choose Hide app."); return; }
        final String[] pkgs = h.toArray(new String[0]); final String[] names = new String[pkgs.length]; final boolean[] keep = new boolean[pkgs.length];
        PackageManager pm = getPackageManager();
        for (int i = 0; i < pkgs.length; i++) { keep[i] = true; try { names[i] = pm.getApplicationLabel(pm.getApplicationInfo(pkgs[i], 0)) + "  (" + pkgs[i] + ")"; } catch (Exception e) { names[i] = pkgs[i] + "  (not installed)"; } }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Untick to show again")
            .setMultiChoiceItems(names, keep, (dlg, which, checked) -> keep[which] = checked)
            .setPositiveButton("Save", (dlg, w) -> { Set<String> n = new LinkedHashSet<>(); for (int i = 0; i < pkgs.length; i++) if (keep[i]) n.add(pkgs[i]); prefs.setHidden(n); refresh(); })
            .setNegativeButton("Cancel", null).show();
    }

    void homeFixDialog() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Home button fix")
            .setMessage("On Google TV the Home button always opens Google TV first, even when Cozy Home TV is your home app, so you see a short flash.\n\n"
                + "This optional helper (an accessibility service) catches only the Home button and opens Cozy Home TV directly. Hold Home for the system panel. "
                + "It also opens Cozy Home TV once after the TV starts. It does not read anything on screen, and no other buttons are touched.\n\n"
                + "On the next screen, choose \"Cozy Home TV: Home button\" and turn it " + (homeFixOn() ? "off" : "on") + ".")
            .setPositiveButton("Open accessibility settings", (dlg, w) -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, "Not available here. Use scripts/set-home.sh --home-fix from a computer."))
            .setNegativeButton("Not now", null).show();
    }

    boolean homeFixOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }
    boolean dreamOn() {
        String s = Settings.Secure.getString(getContentResolver(), "screensaver_components");
        return s != null && s.contains(getPackageName());
    }
    void openSettings(String action, String fallback) {
        try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { info("Not available", fallback); }
    }
    void info(String title, String msg) { new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); }

    String help(String key) {
        switch (key) {
            case "mode": return "Default: the selected theme, with no setup. Cozy: your own pictures and music from the CozyHome folder (replaces the theme's pictures).";
            case "theme": return "The whole look: background, tiles, fonts, motion and screensaver. Clean is the fastest. A Cozy pack (Look > Cozy) replaces the theme's background.";
            case "wallpaper": return "Glass (Aerial) theme and screensaver: how often the photo crossfades to the next one.";
            case "folder": return "Where your pictures and music are read from, and what was found.";
            case "period": return "Cozy look: which picture to show. Follow the clock switches morning, day, evening and night by itself.";
            case "music": return "Cozy look: plays your music.mp3 on loop while the home screen is showing. Stops as soon as you open an app.";
            case "volume": return "Use ◀ ▶ to change.";
            case "motion": return "Theme motion: photo drift, fairy lights, rain, stars, scanlines; fireflies and the live background in the Cozy look. Always paused while an app is open.";
            case "sounds": return "Small clicks when you move around (uses the TV's own sound setting).";
            case "hidden": return "Apps you hid from the home screen. Choose to show them again.";
            case "homefix": return "Optional: stops the Google TV flash when you press Home.";
            case "dream": return "Pick \"Cozy Home TV\" as the screensaver to see your picture and a clock when the TV is idle.";
            case "access": return "Lets the app read /sdcard/CozyHome. Not needed if you use the app's own folder (see README).";
            default: return "";
        }
    }

    static int indexOf(String[] a, String s) { for (int i = 0; i < a.length; i++) if (a[i].equals(s)) return i; return 0; }
    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    String version() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; } }
}
