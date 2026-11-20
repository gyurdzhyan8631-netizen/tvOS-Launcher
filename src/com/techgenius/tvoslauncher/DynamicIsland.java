package com.techgenius.tvoslauncher;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamic Island: a black pill at the top of the second home screen.
 * <ul>
 *   <li>Compact: live activities that are running now (navigation turn + distance, music time left,
 *   a call, an alarm), like the iPhone.</li>
 *   <li>Expanded for a few seconds: a new track, an SMS / message, an incoming call, a ringing alarm,
 *   a new navigation manoeuvre.</li>
 * </ul>
 * Notifications come from {@link MediaWatchService} (notification-listener access is required);
 * the playing track is read from the active media sessions.
 */
final class DynamicIsland extends View {

    private static final int T_NAV = 0, T_MUSIC = 1, T_SMS = 2, T_CALL = 3, T_ALARM = 4;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Attached islands; touched on the main thread only. */
    private static final List<DynamicIsland> LIVE = new ArrayList<>();

    private static final String[] NAV_PKGS = {
            "ru.yandex.yandexnavi", "ru.yandex.yandexmaps", "ru.yandex.maps", "com.google.android.apps.maps",
            "com.waze", "ru.dublgis.dgismobile", "com.sygic.aura", "net.osmand", "net.osmand.plus",
            "app.organicmaps", "com.here.app.maps", "ru.mail.mailnews", "com.mapfactor.navigator"
    };

    /** "300 м", "1,2 км", "500 m", "0.3 mi" ... */
    private static final Pattern DIST = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(километр\\p{L}*|kilomet\\p{L}*|км|km|метр\\p{L}*|meter\\p{L}*|м|m|mi|ft)(?!\\p{L})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ------------------------------------------------------------------ bridge from the service

    /** Called from the notification listener (any thread). */
    static void onPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        final StatusBarNotification s = sbn;
        MAIN.post(() -> {
            for (DynamicIsland v : new ArrayList<>(LIVE)) v.handlePosted(s, false);
        });
    }

    /** Called from the notification listener (any thread). */
    static void onRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        final StatusBarNotification s = sbn;
        MAIN.post(() -> {
            for (DynamicIsland v : new ArrayList<>(LIVE)) v.handleRemoved(s);
        });
    }

    // ------------------------------------------------------------------ model

    private static final class Item {
        String key;
        String chip;   // text in the compact pill
        int arrow;     // navigation only
    }

    private static final class Alert {
        int type;
        String title;
        String sub;
        Bitmap art;
        int arrow;
        long ms;
        long at;
    }

    private final float u;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF box = new RectF();
    private final Path clip = new Path();
    private final Path clip2 = new Path();
    private final Path shape = new Path();
    private final ArrayDeque<Alert> queue = new ArrayDeque<>();
    private final Interpolator decel = new DecelerateInterpolator();

    private Item nav;
    private Item call;
    private Item alarm;
    private String lastMsgSig = "";

    private String musicKey;
    private boolean musicPlaying;
    private long musicDur;
    private long musicPos;
    private long musicPosAt;
    private float musicSpeed = 1f;

    private Alert shown;
    private float showP;
    private float expandP;
    private ValueAnimator showAnim;
    private ValueAnimator expandAnim;
    private boolean suppressed;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            pollMedia();
            h.postDelayed(this, 2000);
        }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (showP > 0.01f && musicPlaying && shown == null) invalidate();
            h.postDelayed(this, 1000);
        }
    };
    private final Runnable endAlert = this::endAlertNow;
    private final Runnable afterCollapse = this::afterCollapseNow;

    DynamicIsland(Context c, float unit) {
        super(c);
        u = unit;
        setWillNotDraw(false);
        tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        tp.setColor(Color.WHITE);
        tp2.setTypeface(Typeface.DEFAULT);
        tp2.setColor(0xFFA0A0A8);
    }

    // ------------------------------------------------------------------ public

    /** The home hides the island while the widget board is being edited. */
    void setSuppressed(boolean on) {
        if (suppressed == on) return;
        suppressed = on;
        invalidate();
        if (!on) advance();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!LIVE.contains(this)) LIVE.add(this);
        seed();
        syncCompact();
        if (getWindowVisibility() == VISIBLE) startPolling();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        LIVE.remove(this);
        stopPolling();
        h.removeCallbacks(endAlert);
        h.removeCallbacks(afterCollapse);
        if (showAnim != null) showAnim.cancel();
        if (expandAnim != null) expandAnim.cancel();
        shown = null;
        queue.clear();
        showP = 0f;
        expandP = 0f;
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) startPolling();
        else stopPolling();
    }

    private void startPolling() {
        h.removeCallbacks(poll);
        h.removeCallbacks(tick);
        h.post(poll);
        h.postDelayed(tick, 1000);
    }

    private void stopPolling() {
        h.removeCallbacks(poll);
        h.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------ notifications

    private void seed() {
        try {
            MediaWatchService svc = MediaWatchService.instance;
            if (svc == null) return;
            StatusBarNotification[] list = svc.getActiveNotifications();
            if (list == null) return;
            for (StatusBarNotification s : list) handlePosted(s, true);
        } catch (Throwable ignored) {
            // no notification access yet
        }
    }

    private static String text(Bundle b, String key) {
        try {
            CharSequence cs = b == null ? null : b.getCharSequence(key);
            return cs == null ? "" : cs.toString().trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean isNavPkg(String pkg) {
        if (pkg == null) return false;
        for (String p : NAV_PKGS) if (p.equals(pkg)) return true;
        return false;
    }

    private static String unit(String raw) {
        String l = raw.toLowerCase(Locale.ROOT);
        if (l.startsWith("километ") || l.startsWith("kilomet") || l.equals("км") || l.equals("km")) return "км";
        if (l.startsWith("метр") || l.startsWith("meter") || l.equals("м") || l.equals("m")) return "м";
        return l;
    }

    /** 0 straight, 1 right, 2 left, 3 U-turn. */
    private static int arrowOf(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        if (l.contains("разворот") || l.contains("u-turn") || l.contains("uturn")) return 3;
        if (l.contains("направо") || l.contains("right")) return 1;
        if (l.contains("налево") || l.contains("left")) return 2;
        return 0;
    }

    private void handlePosted(StatusBarNotification sbn, boolean quiet) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getContext().getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null) return;
        String cat = n.category;
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        boolean summary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if ("transport".equals(cat)) return;  // music comes from the media session
        Bundle ex = n.extras;
        String title = text(ex, Notification.EXTRA_TITLE);
        String body = text(ex, Notification.EXTRA_TEXT);
        String big = text(ex, Notification.EXTRA_BIG_TEXT);
        String key = sbn.getKey();

        if ("navigation".equals(cat) || (isNavPkg(pkg) && ongoing)) {
            String all = title + " " + body + " " + big;
            Matcher mm = DIST.matcher(all);
            String dist = mm.find() ? mm.group(1) + " " + unit(mm.group(2)) : null;
            int arrow = arrowOf(all);
            String instr = !body.isEmpty() ? body : (!big.isEmpty() ? big : "");
            if (instr.isEmpty() || instr.equals(title)) instr = dist != null ? title : "";
            boolean fresh = nav == null || nav.arrow != arrow;
            Item it = new Item();
            it.key = key;
            it.arrow = arrow;
            it.chip = dist != null ? dist : title;
            if (it.chip.isEmpty()) return;
            nav = it;
            if (fresh && !quiet) {
                Alert a = new Alert();
                a.type = T_NAV;
                a.arrow = arrow;
                a.title = dist != null ? dist : title;
                a.sub = instr;
                a.ms = 4500;
                enqueue(a);
            }
            syncCompact();
        } else if ("call".equals(cat)) {
            String name = !title.isEmpty() ? title : body;
            boolean fresh = call == null || !key.equals(call.key);
            Item it = new Item();
            it.key = key;
            it.chip = name;
            call = it;
            if (fresh && !quiet) {
                Alert a = new Alert();
                a.type = T_CALL;
                a.title = name;
                a.sub = TiggoText.t("Входящий вызов");
                a.ms = 8000;
                enqueue(a);
            }
            syncCompact();
        } else if ("alarm".equals(cat)) {
            String label = !body.isEmpty() ? body : title;
            boolean fresh = alarm == null || !key.equals(alarm.key);
            Item it = new Item();
            it.key = key;
            it.chip = label;
            alarm = it;
            if (fresh && !quiet) {
                Alert a = new Alert();
                a.type = T_ALARM;
                a.title = label;
                a.sub = TiggoText.t("Будильник");
                a.ms = 10000;
                enqueue(a);
            }
            syncCompact();
        } else if ("msg".equals(cat) && !summary && !quiet) {
            String preview = !body.isEmpty() ? body : big;
            if (title.isEmpty() && preview.isEmpty()) return;
            String sig = key + "|" + preview;
            if (sig.equals(lastMsgSig)) return;
            lastMsgSig = sig;
            Alert a = new Alert();
            a.type = T_SMS;
            a.title = title;
            a.sub = preview;
            a.ms = 5000;
            enqueue(a);
        }
    }

    private void handleRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        String key = sbn.getKey();
        if (key == null) return;
        boolean changed = false;
        if (nav != null && key.equals(nav.key)) {
            nav = null;
            changed = true;
        }
        if (call != null && key.equals(call.key)) {
            call = null;
            changed = true;
        }
        if (alarm != null && key.equals(alarm.key)) {
            alarm = null;
            changed = true;
        }
        if (changed) syncCompact();
    }

    // ------------------------------------------------------------------ music

    private void pollMedia() {
        try {
            MediaSessionManager msm = (MediaSessionManager) getContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return;
            List<MediaController> list = msm.getActiveSessions(new ComponentName(getContext(), MediaWatchService.class));
            MediaController best = null;
            PlaybackState bestState = null;
            if (list != null) {
                for (MediaController c : list) {
                    PlaybackState s = c.getPlaybackState();
                    if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                        best = c;
                        bestState = s;
                        break;
                    }
                }
            }
            boolean was = musicPlaying;
            if (best == null) {
                musicPlaying = false;
                if (was) syncCompact();
                return;
            }
            MediaMetadata md = best.getMetadata();
            String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null && md != null) artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            Bitmap art = null;
            if (md != null) {
                art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            }
            musicDur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            float speed = bestState.getPlaybackSpeed();
            musicSpeed = speed <= 0f ? 1f : speed;
            long now = SystemClock.elapsedRealtime();
            musicPos = bestState.getPosition()
                    + (long) ((now - bestState.getLastPositionUpdateTime()) * musicSpeed);
            musicPosAt = now;
            musicPlaying = true;

            String key = best.getPackageName() + "|" + title + "|" + artist;
            boolean changedTrack = musicKey != null && !key.equals(musicKey);
            musicKey = key;
            if (changedTrack && title != null && !title.isEmpty()) {
                Alert a = new Alert();
                a.type = T_MUSIC;
                a.title = title;
                a.sub = artist == null ? "" : artist;
                a.art = art;
                a.ms = 4500;
                enqueue(a);
            }
            if (!was) syncCompact();
        } catch (Throwable ignored) {
            // no notification access: no music on the island
        }
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long sec = s % 60;
        return (s / 60) + ":" + (sec < 10 ? "0" : "") + sec;
    }

    // ------------------------------------------------------------------ state machine

    private boolean hasCompact() {
        return nav != null || call != null || alarm != null || musicPlaying;
    }

    private void enqueue(Alert a) {
        a.at = SystemClock.uptimeMillis();
        queue.add(a);
        while (queue.size() > 4) queue.poll();
        advance();
    }

    private void advance() {
        if (shown != null || suppressed || !isAttachedToWindow()) return;
        long now = SystemClock.uptimeMillis();
        Alert a;
        do {
            a = queue.poll();
        } while (a != null && now - a.at > 20000);
        if (a == null) return;
        shown = a;
        animateShow(1f, 360, decel);
        animateExpand(1f, 480, new OvershootInterpolator(1.15f));
        h.removeCallbacks(endAlert);
        h.postDelayed(endAlert, a.ms);
    }

    private void endAlertNow() {
        if (shown == null) return;
        animateExpand(0f, 320, decel);
        h.removeCallbacks(afterCollapse);
        h.postDelayed(afterCollapse, 340);
    }

    private void afterCollapseNow() {
        shown = null;
        expandP = 0f;
        syncCompact();
        advance();
    }

    private void syncCompact() {
        if (shown != null) return;
        animateShow(hasCompact() ? 1f : 0f, 300, decel);
    }

    private void animateShow(float to, long ms, Interpolator ip) {
        if (showAnim != null) showAnim.cancel();
        showAnim = ValueAnimator.ofFloat(showP, to);
        showAnim.setDuration(ms);
        showAnim.setInterpolator(ip);
        showAnim.addUpdateListener(a -> {
            showP = (Float) a.getAnimatedValue();
            invalidate();
        });
        showAnim.start();
    }

    private void animateExpand(float to, long ms, Interpolator ip) {
        if (expandAnim != null) expandAnim.cancel();
        expandAnim = ValueAnimator.ofFloat(expandP, to);
        expandAnim.setDuration(ms);
        expandAnim.setInterpolator(ip);
        expandAnim.addUpdateListener(a -> {
            expandP = (Float) a.getAnimatedValue();
            invalidate();
        });
        expandAnim.start();
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        if (suppressed || showP <= 0.01f) return;
        float w0 = getWidth();
        float cx = w0 / 2f;
        float top = 4f * u;

        // compact chips: call, alarm, navigation, music (two at most)
        int n = 0;
        int[] kind = new int[2];
        String[] txt = new String[2];
        if (call != null && n < 2) {
            kind[n] = T_CALL;
            txt[n++] = cut(call.chip, 12);
        }
        if (alarm != null && n < 2) {
            kind[n] = T_ALARM;
            txt[n++] = cut(alarm.chip, 10);
        }
        if (nav != null && n < 2) {
            kind[n] = T_NAV;
            txt[n++] = cut(nav.chip, 12);
        }
        if (musicPlaying && n < 2) {
            long pos = musicPos + (long) ((SystemClock.elapsedRealtime() - musicPosAt) * musicSpeed);
            kind[n] = T_MUSIC;
            txt[n++] = fmt(musicDur > 0 ? musicDur - pos : pos);
        }

        float ch = 58f * u;
        float iconS = 30f * u;
        float gapIT = 10f * u;
        float gapC = 28f * u;
        float padH = 26f * u;
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTextSize(28f * u);
        float[] tw = new float[2];
        float inner = 0f;
        for (int i = 0; i < n; i++) {
            tw[i] = tp.measureText(txt[i]);
            inner += iconS + gapIT + tw[i];
            if (i > 0) inner += gapC;
        }
        float cw = n == 0 ? 110f * u : inner + padH * 2f;
        float ew = Math.min(w0 - 8f * u, 640f * u);
        float eh = 118f * u;
        float e = Math.max(0f, expandP);
        float w = cw + (ew - cw) * e;
        float hh = ch + (eh - ch) * e;

        float sp = Math.min(1f, showP);
        float s = 0.7f + 0.3f * sp;
        c.save();
        c.scale(s, s, cx, top);
        float l = cx - w / 2f;
        float rad = Math.min(hh / 2f, 46f * u);
        rect.set(l, top, l + w, top + hh);
        bg.setStyle(Paint.Style.FILL);
        bg.setColor(Color.BLACK);
        bg.setAlpha(Math.round(255 * sp));
        c.drawRoundRect(rect, rad, rad, bg);

        float ce = Math.min(1f, e);
        float aCompact = Math.max(0f, 1f - ce * 2f) * sp;
        float aExp = Math.max(0f, ce * 2f - 1f) * sp;
        clip.reset();
        clip.addRoundRect(rect, rad, rad, Path.Direction.CW);
        c.save();
        c.clipPath(clip);

        if (aCompact > 0.01f && n > 0) {
            float x = l + (w - inner) / 2f;
            float cy = top + hh / 2f;
            int a255 = Math.round(255 * aCompact);
            tp.setAlpha(a255);
            float base = cy - (tp.ascent() + tp.descent()) / 2f;
            for (int i = 0; i < n; i++) {
                drawIcon(c, kind[i], 0, x, cy - iconS / 2f, iconS, a255);
                x += iconS + gapIT;
                tp.setTextAlign(Paint.Align.LEFT);
                tp.setTextSize(28f * u);
                tp.setAlpha(a255);
                c.drawText(txt[i], x, base, tp);
                x += tw[i] + gapC;
            }
        }
        if (aExp > 0.01f && shown != null) drawExpanded(c, l, top, w, hh, ew, aExp);

        c.restore();
        c.restore();
        tp.setAlpha(255);
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void drawExpanded(Canvas c, float l, float top, float w, float hh, float ew, float a) {
        Alert al = shown;
        int a255 = Math.round(255 * a);
        float pad = 22f * u;
        float side = 118f * u - 2f * pad;
        float bx = l + (w - ew) / 2f + pad;
        float by = top + (hh - side) / 2f;
        box.set(bx, by, bx + side, by + side);

        if (al.type == T_MUSIC && al.art != null) {
            clip2.reset();
            clip2.addRoundRect(box, 16f * u, 16f * u, Path.Direction.CW);
            c.save();
            c.clipPath(clip2);
            bmp.setAlpha(a255);
            c.drawBitmap(al.art, null, box, bmp);
            c.restore();
        } else if (al.type == T_NAV || al.type == T_MUSIC) {
            drawIcon(c, al.type, al.arrow, bx + side * 0.1f, by + side * 0.1f, side * 0.8f, a255);
        } else {
            drawIcon(c, al.type, 0, bx, by, side, a255);
        }

        float tx = bx + side + 18f * u;
        float avail = l + (w + ew) / 2f - pad - tx;
        if (avail < 40f * u) return;
        boolean two = al.sub != null && !al.sub.isEmpty();
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTextSize(30f * u);
        tp.setAlpha(a255);
        String t1 = al.title == null ? "" : al.title;
        CharSequence c1 = TextUtils.ellipsize(t1, tp, avail, TextUtils.TruncateAt.END);
        float y1 = two ? by + side * 0.42f : by + side * 0.62f;
        c.drawText(c1, 0, c1.length(), tx, y1, tp);
        if (two) {
            tp2.setTextSize(24f * u);
            tp2.setAlpha(a255);
            CharSequence c2 = TextUtils.ellipsize(al.sub, tp2, avail, TextUtils.TruncateAt.END);
            c.drawText(c2, 0, c2.length(), tx, by + side * 0.86f, tp2);
        }
    }

    /** Draws the glyph of an activity into a square at (x, y) with side s. */
    private void drawIcon(Canvas c, int type, int arrow, float x, float y, float s, int a255) {
        if (type == T_NAV) {
            drawArrow(c, arrow, x, y, s, a255);
        } else if (type == T_MUSIC) {
            drawNote(c, x, y, s, a255);
        } else {
            String e = type == T_CALL ? "\uD83D\uDCDE" : type == T_ALARM ? "\u23F0" : "\uD83D\uDCAC";
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(s * 0.78f);
            tp.setAlpha(a255);
            float base = y + s / 2f - (tp.ascent() + tp.descent()) / 2f;
            c.drawText(e, x + s / 2f, base, tp);
            tp.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawArrow(Canvas c, int kind, float x, float y, float s, int a255) {
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeWidth(s * 0.13f);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setColor(Color.WHITE);
        icon.setAlpha(a255);
        shape.reset();
        switch (kind) {
            case 1:  // turn right
                shape.moveTo(x + s * 0.28f, y + s * 0.92f);
                shape.lineTo(x + s * 0.28f, y + s * 0.38f);
                shape.lineTo(x + s * 0.86f, y + s * 0.38f);
                shape.moveTo(x + s * 0.64f, y + s * 0.16f);
                shape.lineTo(x + s * 0.88f, y + s * 0.38f);
                shape.lineTo(x + s * 0.64f, y + s * 0.60f);
                break;
            case 2:  // turn left
                shape.moveTo(x + s * 0.72f, y + s * 0.92f);
                shape.lineTo(x + s * 0.72f, y + s * 0.38f);
                shape.lineTo(x + s * 0.14f, y + s * 0.38f);
                shape.moveTo(x + s * 0.36f, y + s * 0.16f);
                shape.lineTo(x + s * 0.12f, y + s * 0.38f);
                shape.lineTo(x + s * 0.36f, y + s * 0.60f);
                break;
            case 3:  // U-turn
                shape.moveTo(x + s * 0.72f, y + s * 0.95f);
                shape.lineTo(x + s * 0.72f, y + s * 0.30f);
                shape.lineTo(x + s * 0.30f, y + s * 0.30f);
                shape.lineTo(x + s * 0.30f, y + s * 0.72f);
                shape.moveTo(x + s * 0.12f, y + s * 0.55f);
                shape.lineTo(x + s * 0.30f, y + s * 0.76f);
                shape.lineTo(x + s * 0.48f, y + s * 0.55f);
                break;
            default:  // straight
                shape.moveTo(x + s * 0.50f, y + s * 0.95f);
                shape.lineTo(x + s * 0.50f, y + s * 0.10f);
                shape.moveTo(x + s * 0.26f, y + s * 0.34f);
                shape.lineTo(x + s * 0.50f, y + s * 0.10f);
                shape.lineTo(x + s * 0.74f, y + s * 0.34f);
                break;
        }
        c.drawPath(shape, icon);
    }

    private void drawNote(Canvas c, float x, float y, float s, int a255) {
        icon.setColor(Color.WHITE);
        icon.setAlpha(a255);
        icon.setStyle(Paint.Style.FILL);
        c.drawCircle(x + s * 0.34f, y + s * 0.78f, s * 0.2f, icon);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeWidth(s * 0.12f);
        icon.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(x + s * 0.52f, y + s * 0.74f, x + s * 0.52f, y + s * 0.12f, icon);
        c.drawLine(x + s * 0.52f, y + s * 0.12f, x + s * 0.84f, y + s * 0.34f, icon);
    }
}
