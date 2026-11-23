package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Extra clock faces for the TIGGO home: 4 = Duo (big hours, small minutes, date at the right),
 * 5 = iOS 26 (fat rounded digits that overlap, glass colon), 6 = Outline (stroked hours, filled minutes).
 * Size is a percentage of the mockup size and applies to the four older styles as well.
 */
final class TiggoClock extends View {
    static final String K_SIZE = "tiggo9_clock_size";
    static final int DUO = 4, LIQUID = 5, OUTLINE = 6;

    private final int style;
    private final float k;
    private final int oy;
    private final float scale;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            invalidate();
            if (isShown()) postDelayed(this, 60000 - (System.currentTimeMillis() % 60000) + 20);
        }
    };

    static float scale(Context c) {
        return Math.max(40, Math.min(200, TiggoTheme.prefs(c).getInt(K_SIZE, 100))) / 100f;
    }

    static void setScale(Context c, int percent) {
        TiggoTheme.prefs(c).edit().putInt(K_SIZE, Math.max(40, Math.min(200, percent))).apply();
    }

    /** Mockup size of the digits: new styles know their own base, older ones pass theirs in. */
    static float mockSize(Context c, float olderBase) {
        int s = TiggoTheme.get(c, TiggoTheme.K_CLOCK, 1, TiggoTheme.CLOCK_NAMES.length - 1);
        float base = s == DUO ? 300f : (s == LIQUID ? 340f : (s == OUTLINE ? 310f : olderBase));
        float size = base * scale(c);
        // keep the digits above the widgets: their bottom stays at about 430 of 941
        if (s >= DUO) size = Math.min(size, (430f - 118f) / digitRatio(s));
        return size;
    }

    /** Height of the digits as a share of the font size, for each face. */
    static float digitRatio(int style) {
        return style == DUO ? 0.74f : (style == LIQUID ? 0.76f : 0.87f);
    }

    /**
     * The size the board uses to find its top edge below this face: the digits start at 118 and end
     * at 118 + ratio * size, the board follows 26 lower.
     */
    static float boardSize(Context c, float olderBase) {
        int s = TiggoTheme.get(c, TiggoTheme.K_CLOCK, 1, TiggoTheme.CLOCK_NAMES.length - 1);
        if (s < DUO) return mockSize(c, olderBase);
        return (digitRatio(s) * mockSize(c, olderBase) + 9f) / 0.7233f;
    }

    TiggoClock(Context c, int style, float unit, int offsetY) {
        super(c);
        this.style = style;
        this.k = unit;
        this.oy = offsetY;
        this.scale = scale(c);
        setWillNotDraw(false);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(tick);
    }

    @Override protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        removeCallbacks(tick);
        if (v == VISIBLE) post(tick);
    }

    private float size() {
        return (style == DUO ? 300f : (style == LIQUID ? 420f : 400f)) * scale * k;
    }

    private String[] time() {
        Calendar cal = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(getContext());
        int h = cal.get(h24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        if (!h24 && h == 0) h = 12;
        String hh = h < 10 && style != DUO ? "0" + h : String.valueOf(h);
        int mi = cal.get(Calendar.MINUTE);
        return new String[]{hh, mi < 10 ? "0" + mi : String.valueOf(mi)};
    }

    private void date(Paint paint, Canvas c, String pattern, float x, float y, float size, boolean center) {
        paint.setTextSize(size);
        paint.setTextAlign(center ? Paint.Align.CENTER : Paint.Align.LEFT);
        Locale loc = Locale.getDefault();
        String s = new SimpleDateFormat(pattern, loc).format(Calendar.getInstance().getTime());
        c.drawText(s.isEmpty() ? s : s.substring(0, 1).toUpperCase(loc) + s.substring(1), x, y, paint);
    }

    @Override protected void onDraw(Canvas c) {
        int saved = c.getSaveCount();
        try {
            paint(c);
        } catch (Throwable e) {
            c.restoreToCount(saved);
            Diag.report("TiggoClock", e);
        }
    }

    private void paint(Canvas c) {
        float cx = getWidth() / 2f;
        String[] t = time();
        float s = fit(size(), t);
        float y0 = oy + 118f * k;  // the digits start here, whatever the face
        float top = style == OUTLINE ? y0 + 0.066f * s : y0 - 0.04f * s;
        if (style == DUO) drawDuo(c, t, s, top, cx);
        else if (style == LIQUID) drawLiquid(c, t, s, top, cx);
        else drawOutline(c, t, s, top, cx);
    }

    /** Shrinks a size that would make the digits wider than the screen. */
    private float fit(float s, String[] t) {
        if (style == DUO || getWidth() <= 0) return s;
        float w;
        if (style == OUTLINE) {
            p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            p.setTextSize(s);
            p.setTextScaleX(0.78f);
            w = p.measureText(t[0]) + p.measureText(t[1]);
            p.setTextScaleX(1f);
        } else {
            p.setTypeface(Ui.font(getContext(), "display"));
            p.setTextSize(s);
            w = p.measureText(t[0] + t[1]) * 0.92f + s * 0.25f;
        }
        float limit = getWidth() * 0.90f;
        return w > limit ? s * limit / Math.max(1f, w) : s;
    }

    /** Big thin hours, small raised minutes, two-line date at the right. */
    private void drawDuo(Canvas c, String[] t, float s, float top, float cx) {
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.font(getContext(), "regular"));
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(s);
        float wh = p.measureText(t[0]);
        float ms = s * 0.46f;
        p.setTextSize(ms);
        float wm = p.measureText(t[1]);
        float gap = s * 0.04f;
        float ds = s * 0.15f;
        float wd = Math.max(wm, ds * 3.4f);
        float x = cx - (wh + gap + wd) / 2f;
        float base = top + s * 0.78f;
        p.setShader(new LinearGradient(0, base - s * 0.8f, 0, base,
                new int[]{0xFFF2EEF7, 0xD8DCD3E6}, null, Shader.TileMode.CLAMP));
        p.setTextSize(s);
        c.drawText(t[0], x, base, p);
        p.setTextSize(ms);
        c.drawText(t[1], x + wh + gap, top + ms * 0.80f, p);
        p.setShader(null);
        p.setColor(0xE6DCD3E6);
        p.setTypeface(Ui.font(getContext(), "semibold"));
        float dx = x + wh + gap;
        date(p, c, "EEE,", dx, top + ms * 0.80f + ds * 1.25f, ds, false);
        date(p, c, "MMM d", dx, top + ms * 0.80f + ds * 2.45f, ds, false);
    }

    /** iOS 26: fat rounded digits, each drawn with alpha so the overlaps read lighter. */
    private void drawLiquid(Canvas c, String[] t, float s, float top, float cx) {
        String digits = t[0] + t[1];
        p.setTypeface(Ui.font(getContext(), "display"));
        p.setTextSize(s);
        p.setTextAlign(Paint.Align.LEFT);
        p.setStyle(Paint.Style.FILL_AND_STROKE);   // rounds and fattens the bundled Inter
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.055f);
        float colon = s * 0.20f;
        float overlap = 0.90f;
        float total = colon;
        for (int i = 0; i < digits.length(); i++) {
            total += p.measureText(String.valueOf(digits.charAt(i))) * (i == 0 ? 1f : overlap);
            if (i == t[0].length() - 1) total += colon * 0.2f;
        }
        float x = cx - total / 2f;
        float base = top + s * 0.80f;
        p.setColor(0xD9FF7A6B);
        for (int i = 0; i < digits.length(); i++) {
            String g = String.valueOf(digits.charAt(i));
            c.drawText(g, x, base, p);
            x += p.measureText(g) * overlap;
            if (i == t[0].length() - 1) {
                float r = s * 0.055f;
                float dx = x + colon * 0.35f;
                for (int j = 0; j < 2; j++) {
                    float dy = base - s * (j == 0 ? 0.50f : 0.20f);
                    dot.setShader(new RadialGradient(dx - r * 0.3f, dy - r * 0.3f, r * 1.6f,
                            new int[]{0xF2FFFFFF, 0x99E8E8EE, 0x33FFFFFF}, null, Shader.TileMode.CLAMP));
                    c.drawCircle(dx, dy, r, dot);
                }
                x += colon;
            }
        }
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(0);
        p.setColor(0xCCFFFFFF);
        p.setTypeface(Ui.font(getContext(), "semibold"));
        date(p, c, "EEEE, d MMMM", cx, top - s * 0.06f, s * 0.075f, true);
    }

    /** Condensed tall digits: hours stroked, minutes filled. */
    private void drawOutline(Canvas c, String[] t, float s, float top, float cx) {
        Typeface cond = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        p.setTypeface(cond);
        p.setTextSize(s);
        p.setTextScaleX(0.78f);
        p.setTextAlign(Paint.Align.LEFT);
        float wh = p.measureText(t[0]);
        float wm = p.measureText(t[1]);
        float x = cx - (wh + wm) / 2f;
        float base = top + s * 0.80f;
        c.save();
        c.scale(1f, 1.22f, cx, base);
        p.setColor(0xFFCFCFCF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, s * 0.018f));
        c.drawText(t[0], x, base, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFEDEDED);
        c.drawText(t[1], x + wh, base, p);
        c.restore();
        p.setTextScaleX(1f);
        p.setColor(0xB3FFFFFF);
        p.setTypeface(Ui.font(getContext(), "semibold"));
        date(p, c, "EEEE, d MMMM", cx, top - s * 0.10f, s * 0.07f, true);
    }
}
