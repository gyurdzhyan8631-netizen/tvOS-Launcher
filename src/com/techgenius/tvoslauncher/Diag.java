package com.techgenius.tvoslauncher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Safety net for the new custom drawing code: when something throws, the screen element is skipped
 * instead of the whole launcher crashing, and the reason is shown on screen for a few seconds and
 * saved to a file (files/diag.txt, Android/data/.../files/diag.txt).
 */
final class Diag {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context app;
    private static String lastKey = "";
    private static int shown;

    private Diag() {
    }

    static void init(Context c) {
        if (c != null && app == null) app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
    }

    static void report(String where, Throwable t) {
        try {
            StackTraceElement[] st = t.getStackTrace();
            String top = "";
            for (StackTraceElement e : st) {
                if (e.getClassName().startsWith("com.techgenius")) {
                    top = e.getClassName().replace("com.techgenius.tvoslauncher.", "") + "." + e.getMethodName()
                            + ":" + e.getLineNumber();
                    break;
                }
            }
            final String msg = where + ": " + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + (top.isEmpty() ? "" : " @ " + top);
            if (msg.equals(lastKey)) return;  // the same failure on every frame: report once
            lastKey = msg;
            Log.e("TiggoDiag", msg, t);
            save(msg + "\n" + Log.getStackTraceString(t));
            if (app != null && shown < 5) {
                shown++;
                MAIN.post(() -> {
                    try {
                        Toast.makeText(app, msg, Toast.LENGTH_LONG).show();
                    } catch (Throwable ignored) {
                        // no screen to tell
                    }
                });
            }
        } catch (Throwable ignored) {
            // never fail while reporting a failure
        }
    }

    private static void save(String text) {
        if (app == null) return;
        try {
            File f = new File(app.getFilesDir(), "diag.txt");
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write((text + "\n\n").getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
            // best effort
        }
        try {
            File dir = app.getExternalFilesDir(null);
            if (dir != null) {
                try (FileOutputStream out = new FileOutputStream(new File(dir, "diag.txt"), true)) {
                    out.write((text + "\n\n").getBytes("UTF-8"));
                }
            }
        } catch (Throwable ignored) {
            // best effort
        }
    }
}
