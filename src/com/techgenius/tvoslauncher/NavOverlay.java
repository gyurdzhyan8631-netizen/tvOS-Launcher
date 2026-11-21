package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Always-visible Back / Home / Recents pill over every other app, so the home screen is one tap away
 * from anywhere. It is see-through (it does not get in the way of the app below), becomes solid
 * while it is touched and fades again. Shown only while the launcher itself is not in front (the home
 * has its own navigation island) and only while the accessibility service is running.
 */
final class NavOverlay {
    private static final float IDLE_ALPHA = 0.42f;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean homeVisible;
    private static WindowManager wm;
    private static FrameLayout root;

    private NavOverlay() {
    }

    /** The home reports whether it is on screen. */
    static void setHomeVisible(boolean visible) {
        homeVisible = visible;
        if (visible) MAIN.post(NavOverlay::hideNow);
        else MAIN.post(NavOverlay::showNow);
    }

    /** The accessibility service has connected. */
    static void onServiceReady() {
        MAIN.post(NavOverlay::showNow);
    }

    /** The accessibility service has stopped: its windows are gone with it. */
    static void onServiceGone() {
        MAIN.post(NavOverlay::hideNow);
    }

    private static void showNow() {
        if (root != null || homeVisible) return;
        final PowerService svc = PowerService.instance;
        if (svc == null) return;
        try {
            WindowManager w = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
            if (w == null) return;
            DisplayMetrics dm = svc.getResources().getDisplayMetrics();
            float k = Math.max(0.2f, Math.min(dm.widthPixels / 1672f, dm.heightPixels / 941f));
            float u = k * 0.66f;  // smaller than the island on the home
            final Context app = svc.getApplicationContext();

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xD9202228);
            bg.setCornerRadius(44f * u);
            bg.setStroke(Math.max(1, Math.round(1.4f * u)), 0x55FFFFFF);
            NavIsland pill = new NavIsland(svc, u, bg,
                    () -> ButtonMapper.sendKeyCode(app, KeyEvent.KEYCODE_BACK),
                    () -> ButtonMapper.sendKeyCode(app, KeyEvent.KEYCODE_HOME),
                    () -> ButtonMapper.sendKeyCode(app, KeyEvent.KEYCODE_APP_SWITCH));

            Holder holder = new Holder(svc);
            holder.setAlpha(IDLE_ALPHA);
            holder.addView(pill, new FrameLayout.LayoutParams(
                    Math.round((3 * NavIsland.ITEM_W + 2 * NavIsland.SIDE_PAD) * u), Math.round(132f * u)));

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.y = Math.round(20f * k);
            w.addView(holder, lp);
            wm = w;
            root = holder;
        } catch (Throwable ignored) {
            root = null;
            wm = null;
        }
    }

    private static void hideNow() {
        if (root == null) return;
        try {
            if (wm != null) wm.removeView(root);
        } catch (Throwable ignored) {
            // already gone
        }
        root = null;
        wm = null;
    }

    /** Container that turns solid while touched and fades back afterwards. */
    private static final class Holder extends FrameLayout {
        private final Runnable fade = () -> animate().alpha(IDLE_ALPHA).setDuration(400).start();

        Holder(Context c) {
            super(c);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                removeCallbacks(fade);
                animate().alpha(1f).setDuration(120).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                removeCallbacks(fade);
                postDelayed(fade, 2500);
            }
            return super.dispatchTouchEvent(e);
        }
    }
}
