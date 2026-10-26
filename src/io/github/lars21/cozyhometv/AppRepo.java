package io.github.lars21.cozyhometv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lists launchable apps (TV + phone launchers) and loads their banners/icons. Call scan() off the main thread. */
final class AppRepo {
    static final class App {
        String pkg, name; Intent launch; ActivityInfo ai; boolean tv;
    }

    /** Popular TV apps used to seed the favourites row the first time (only the ones that are installed). */
    static final String[] SEED = {
        "com.netflix.ninja", "com.google.android.youtube.tv", "com.disney.disneyplus", "com.amazon.amazonvideo.livingroom",
        "com.spotify.tv.android", "com.plexapp.android", "org.xbmc.kodi", "com.google.android.youtube.tvkids",
        "com.apple.atve.androidtv.appletv", "com.hbo.hbonow", "com.wbd.stream", "tv.twitch.android.app"
    };

    /** Apps that switch HDMI inputs / show live TV, in order of preference. Never hidden, never debloated. */
    static final String[] TV_INPUT_APPS = {"com.google.android.tv", "com.android.tv", "com.realtek.tv", "com.mediatek.wwtv.tvcenter", "com.sony.dtv.tvx", "com.tcl.tv", "com.hisense.tv"};

    static List<App> scan(Context c) {
        PackageManager pm = c.getPackageManager();
        LinkedHashMap<String, App> found = new LinkedHashMap<>();
        String self = c.getPackageName();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(self) || found.containsKey(pkg)) continue;
                App a = new App(); a.pkg = pkg; a.ai = r.activityInfo; a.tv = cat.equals(Intent.CATEGORY_LEANBACK_LAUNCHER);
                a.name = String.valueOf(r.loadLabel(pm));
                Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (li == null) li = pm.getLaunchIntentForPackage(pkg);
                if (li == null) li = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(pkg, r.activityInfo.name));
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                a.launch = li; found.put(pkg, a);
            }
        }
        List<App> list = new ArrayList<>(found.values());
        Collections.sort(list, (x, y) -> x.name.compareToIgnoreCase(y.name));
        return list;
    }

    /** Banner (16:9 TV art) if the app has one, otherwise null. */
    static Drawable banner(PackageManager pm, App a) {
        try { Drawable d = a.ai.loadBanner(pm); if (d != null) return d; } catch (Exception ignored) {}
        try { Drawable d = pm.getApplicationBanner(a.pkg); if (d != null) return d; } catch (Exception ignored) {}
        return null;
    }
    static Drawable icon(PackageManager pm, App a) {
        try { return a.ai.loadIcon(pm); } catch (Exception e) { return pm.getDefaultActivityIcon(); }
    }

    static String tvInputApp(PackageManager pm) {
        for (String p : TV_INPUT_APPS) { try { pm.getPackageInfo(p, 0); if (pm.getLeanbackLaunchIntentForPackage(p) != null || pm.getLaunchIntentForPackage(p) != null) return p; } catch (Exception ignored) {} }
        return null;
    }

    static Map<String, App> index(List<App> apps) { Map<String, App> m = new LinkedHashMap<>(); for (App a : apps) m.put(a.pkg, a); return m; }
}
