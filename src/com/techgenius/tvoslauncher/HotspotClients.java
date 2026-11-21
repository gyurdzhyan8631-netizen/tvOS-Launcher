package com.techgenius.tvoslauncher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Counts the devices connected to this device's Wi-Fi hotspot. First tries the ARP table; when the
 * system hides it from apps, asks the local ADB shell (only if it is set up) for the neighbour table.
 * Returns -1 when the count is unknown.
 */
final class HotspotClients {
    interface Callback {
        void onCount(int count);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean busy;
    private static long lastAdbTry;

    private HotspotClients() {
    }

    /** Runs in the background; the callback is delivered on the main thread (count or -1). */
    static void query(final Context c, final Callback cb) {
        if (busy || c == null || cb == null) return;
        busy = true;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            int n = -1;
            try {
                n = count(app);
            } catch (Throwable ignored) {
                // unknown
            } finally {
                busy = false;
            }
            final int result = n;
            MAIN.post(() -> cb.onCount(result));
        }).start();
    }

    private static int count(Context c) {
        int n = fromArp();
        if (n > 0) return n;
        long now = System.currentTimeMillis();
        if (now - lastAdbTry >= 15000L) {
            lastAdbTry = now;
            try {
                String out = LocalAdb.execShellOutput(c, "ip neigh show");
                int m = fromNeigh(out);
                if (m >= 0) return m;
            } catch (Throwable ignored) {
                // ADB is not set up
            }
        }
        return n;
    }

    private static boolean hotspotDev(String d) {
        if (d == null) return false;
        String l = d.toLowerCase(Locale.ROOT);
        return l.startsWith("ap") || l.startsWith("softap") || l.startsWith("swlan") || l.startsWith("wlan1");
    }

    private static int fromArp() {
        File f = new File("/proc/net/arp");
        if (!f.canRead()) return -1;
        Set<String> macs = new HashSet<>();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line = r.readLine();  // header
            while ((line = r.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 6) continue;
                if (!"0x2".equals(p[2])) continue;
                if ("00:00:00:00:00:00".equals(p[3])) continue;
                if (hotspotDev(p[5])) macs.add(p[3].toLowerCase(Locale.ROOT));
            }
        } catch (Throwable t) {
            return -1;
        }
        return macs.size();
    }

    private static int fromNeigh(String out) {
        if (out == null) return -1;
        Set<String> macs = new HashSet<>();
        for (String line : out.split("\n")) {
            String[] t = line.trim().split("\\s+");
            String dev = null;
            String mac = null;
            for (int i = 0; i + 1 < t.length; i++) {
                if ("dev".equals(t[i])) dev = t[i + 1];
                if ("lladdr".equals(t[i])) mac = t[i + 1];
            }
            if (dev == null || mac == null || !hotspotDev(dev)) continue;
            String state = t[t.length - 1];
            if ("REACHABLE".equals(state) || "STALE".equals(state) || "DELAY".equals(state)
                    || "PROBE".equals(state) || "PERMANENT".equals(state)) {
                macs.add(mac.toLowerCase(Locale.ROOT));
            }
        }
        return macs.size();
    }
}
