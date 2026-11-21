package com.techgenius.tvoslauncher;

import android.app.Notification;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Live turn-by-turn state of Organic Maps (and its fork CoMaps), read from the ongoing navigation notification
 * (title = distance to the next manoeuvre, text = the street). Fed by {@link MediaWatchService}.
 */
final class NavFeed {
    /** Package names of the Organic Maps builds first, then the CoMaps fork. */
    static final String[] MAPS = {"app.organicmaps", "app.organicmaps.web", "app.organicmaps.beta",
            "app.organicmaps.debug", "app.comaps", "app.comaps.fdroid", "app.comaps.google", "app.comaps.debug"};

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    static String dist = "";
    static String street = "";
    static boolean active;
    private static String key;

    private NavFeed() {
    }

    static boolean isCoMaps(String pkg) {
        if (pkg == null) return false;
        for (String p : MAPS) if (p.equals(pkg)) return true;
        return false;
    }

    private static String str(Bundle b, String name) {
        try {
            CharSequence cs = b == null ? null : b.getCharSequence(name);
            return cs == null ? "" : cs.toString().trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** Any thread. */
    static void onPosted(StatusBarNotification sbn) {
        if (sbn == null || !isCoMaps(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || !"navigation".equals(n.category)) return;
        final String d = str(n.extras, Notification.EXTRA_TITLE);
        final String s = str(n.extras, Notification.EXTRA_TEXT);
        final String k = sbn.getKey();
        MAIN.post(() -> {
            key = k;
            dist = d;
            street = s;
            active = true;
            fire();
        });
    }

    /** Any thread. */
    static void onRemoved(StatusBarNotification sbn) {
        if (sbn == null || !isCoMaps(sbn.getPackageName())) return;
        final String k = sbn.getKey();
        MAIN.post(() -> {
            if (k != null && k.equals(key)) {
                key = null;
                dist = "";
                street = "";
                active = false;
                fire();
            }
        });
    }

    /** Picks up a navigation that was already running (main thread). */
    static void seed() {
        try {
            MediaWatchService svc = MediaWatchService.instance;
            if (svc == null) return;
            StatusBarNotification[] list = svc.getActiveNotifications();
            if (list == null) return;
            for (StatusBarNotification s : list) onPosted(s);
        } catch (Throwable ignored) {
            // no notification access
        }
    }

    static void listen(Runnable r) {
        if (!LISTENERS.contains(r)) LISTENERS.add(r);
    }

    static void unlisten(Runnable r) {
        LISTENERS.remove(r);
    }

    private static void fire() {
        for (Runnable r : new ArrayList<>(LISTENERS)) r.run();
    }
}
