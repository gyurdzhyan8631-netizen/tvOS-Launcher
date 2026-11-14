package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Icon pack support (ADW / Nova style appfilter.xml), used for the app icons on the TIGGO 9 home. */
final class IconPacks {
    static final String AUTO = "auto";
    static final String NONE = "none";
    static final String FLUID = "com.fluidpremium.icon";

    private static final String[] ACTIONS = {
            "org.adw.launcher.THEMES", "com.novalauncher.THEME",
            "com.anddoes.launcher.THEME", "com.gau.go.launcherex.theme"};
    private static final int SIZE = 256;

    private static String loadedPack = "";
    private static Resources packRes;
    private static Map<String, String> byComponent = new HashMap<>();
    private static Map<String, String> byPackage = new HashMap<>();
    private static final Map<String, Bitmap> cache = new HashMap<>();

    private IconPacks() {
    }

    static final class Pack {
        String pkg;
        String label;
    }

    static boolean isInstalled(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Saved choice -> package of the pack to use ("" = system icons). */
    static String resolve(Context c, String saved) {
        if (saved == null || AUTO.equals(saved)) return isInstalled(c, FLUID) ? FLUID : "";
        if (NONE.equals(saved)) return "";
        return isInstalled(c, saved) ? saved : "";
    }

    static List<Pack> installed(Context c) {
        List<Pack> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        PackageManager pm = c.getPackageManager();
        for (String action : ACTIONS) {
            try {
                for (ResolveInfo ri : pm.queryIntentActivities(new Intent(action), 0)) {
                    String pkg = ri.activityInfo.packageName;
                    if (seen.add(pkg)) {
                        Pack p = new Pack();
                        p.pkg = pkg;
                        CharSequence label = ri.loadLabel(pm);
                        p.label = label != null ? label.toString() : pkg;
                        out.add(p);
                    }
                }
            } catch (Throwable ignored) {
                // this action is not supported by the system
            }
        }
        return out;
    }

    /** Reads the pack's appfilter. Call off the UI thread; the maps are swapped in at the end. */
    static void load(Context c, String pack) {
        if (pack == null) pack = "";
        synchronized (IconPacks.class) {
            if (pack.equals(loadedPack)) return;
        }
        Map<String, String> comps = new HashMap<>();
        Map<String, String> pkgs = new HashMap<>();
        Resources res = null;
        if (!pack.isEmpty()) {
            try {
                res = c.getPackageManager().getResourcesForApplication(pack);
                XmlPullParser p;
                InputStream in = null;
                int id = res.getIdentifier("appfilter", "xml", pack);
                if (id != 0) {
                    p = res.getXml(id);
                } else {
                    in = res.getAssets().open("appfilter.xml");
                    p = android.util.Xml.newPullParser();
                    p.setInput(in, "utf-8");
                }
                int ev;
                while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                    if (ev != XmlPullParser.START_TAG || !"item".equals(p.getName())) continue;
                    String comp = p.getAttributeValue(null, "component");
                    String dr = p.getAttributeValue(null, "drawable");
                    if (comp == null || dr == null || !comp.startsWith("ComponentInfo{") || !comp.endsWith("}")) continue;
                    String body = comp.substring(14, comp.length() - 1);
                    if (!comps.containsKey(body)) comps.put(body, dr);
                    int slash = body.indexOf('/');
                    String pkg = slash > 0 ? body.substring(0, slash) : body;
                    if (!pkgs.containsKey(pkg)) pkgs.put(pkg, dr);
                }
                if (in != null) in.close();
            } catch (Throwable t) {
                res = null;
                comps.clear();
                pkgs.clear();
            }
        }
        synchronized (IconPacks.class) {
            loadedPack = pack;
            packRes = res;
            byComponent = comps;
            byPackage = pkgs;
            cache.clear();
        }
    }

    /** The app's icon from the loaded pack, or null (no pack / app not covered). */
    static synchronized Drawable get(Context c, MainActivity.AppEntry e) {
        if (packRes == null || e == null) return null;
        String comp = null;
        if (e.launch != null && e.launch.getComponent() != null) {
            comp = e.launch.getComponent().getPackageName() + "/" + e.launch.getComponent().getClassName();
        }
        String name = comp != null ? byComponent.get(comp) : null;
        if (name == null && e.pkg != null) name = byPackage.get(e.pkg);
        if (name == null) return null;
        Bitmap b = cache.get(name);
        if (b == null) {
            try {
                int id = packRes.getIdentifier(name, "drawable", loadedPack);
                if (id == 0) id = packRes.getIdentifier(name, "mipmap", loadedPack);
                if (id == 0) return null;
                Drawable d = packRes.getDrawable(id, null);
                if (d == null) return null;
                b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(b);
                d.setBounds(0, 0, SIZE, SIZE);
                d.draw(cv);
                cache.put(name, b);
            } catch (Throwable t) {
                return null;
            }
        }
        return new BitmapDrawable(c.getResources(), b);
    }
}
