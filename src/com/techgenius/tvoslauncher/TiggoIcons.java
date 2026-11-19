package com.techgenius.tvoslauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Simple vector-like icons drawn with Canvas, used by the TIGGO 9 home (dock and status capsule). */
final class TiggoIcons {
    private TiggoIcons() {
    }

    private static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    private static Paint stroke(int color, float w) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        return p;
    }

    private static Paint clear() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        return p;
    }

    private static Bitmap bmp(int s) {
        return Bitmap.createBitmap(Math.max(8, s), Math.max(8, s), Bitmap.Config.ARGB_8888);
    }

    /** Navigation arrow (paper-plane style). */
    static Bitmap navigation(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(s * 0.07f);
        Path path = new Path();
        path.moveTo(s * 0.84f, s * 0.16f);
        path.lineTo(s * 0.18f, s * 0.46f);
        path.lineTo(s * 0.50f, s * 0.54f);
        path.lineTo(s * 0.58f, s * 0.84f);
        path.close();
        c.drawPath(path, p);
        return b;
    }

    /** TV: rounded screen, antenna and the letters TV. */
    static Bitmap tv(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint st = stroke(color, s * 0.07f);
        c.drawRoundRect(new RectF(s * 0.10f, s * 0.26f, s * 0.90f, s * 0.86f), s * 0.12f, s * 0.12f, st);
        Path ears = new Path();
        ears.moveTo(s * 0.34f, s * 0.10f);
        ears.lineTo(s * 0.50f, s * 0.26f);
        ears.lineTo(s * 0.66f, s * 0.10f);
        c.drawPath(ears, st);
        Paint t = fill(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(s * 0.27f);
        c.drawText("TV", s * 0.5f, s * 0.67f, t);
        return b;
    }

    /** Car, front view. */
    static Bitmap car(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        Path cabin = new Path();
        cabin.moveTo(s * 0.27f, s * 0.20f);
        cabin.lineTo(s * 0.73f, s * 0.20f);
        cabin.lineTo(s * 0.86f, s * 0.48f);
        cabin.lineTo(s * 0.14f, s * 0.48f);
        cabin.close();
        c.drawPath(cabin, p);
        c.drawRoundRect(new RectF(s * 0.06f, s * 0.44f, s * 0.94f, s * 0.76f), s * 0.10f, s * 0.10f, p);
        c.drawRoundRect(new RectF(s * 0.14f, s * 0.70f, s * 0.31f, s * 0.88f), s * 0.04f, s * 0.04f, p);
        c.drawRoundRect(new RectF(s * 0.69f, s * 0.70f, s * 0.86f, s * 0.88f), s * 0.04f, s * 0.04f, p);
        Paint cut = clear();
        Path glass = new Path();
        glass.moveTo(s * 0.33f, s * 0.28f);
        glass.lineTo(s * 0.67f, s * 0.28f);
        glass.lineTo(s * 0.76f, s * 0.43f);
        glass.lineTo(s * 0.24f, s * 0.43f);
        glass.close();
        c.drawPath(glass, cut);
        c.drawCircle(s * 0.21f, s * 0.58f, s * 0.055f, cut);
        c.drawCircle(s * 0.79f, s * 0.58f, s * 0.055f, cut);
        c.drawRoundRect(new RectF(s * 0.36f, s * 0.57f, s * 0.64f, s * 0.63f), s * 0.03f, s * 0.03f, cut);
        return b;
    }

    /** Music note. */
    static Bitmap note(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        c.save();
        c.rotate(-20f, s * 0.34f, s * 0.74f);
        c.drawOval(new RectF(s * 0.18f, s * 0.64f, s * 0.50f, s * 0.84f), p);
        c.restore();
        c.drawRect(s * 0.45f, s * 0.16f, s * 0.52f, s * 0.76f, p);
        Path flag = new Path();
        flag.moveTo(s * 0.52f, s * 0.16f);
        flag.cubicTo(s * 0.62f, s * 0.32f, s * 0.80f, s * 0.34f, s * 0.80f, s * 0.56f);
        flag.cubicTo(s * 0.74f, s * 0.44f, s * 0.62f, s * 0.42f, s * 0.52f, s * 0.36f);
        flag.close();
        c.drawPath(flag, p);
        return b;
    }

    /** Folder. */
    static Bitmap folder(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint back = fill(color);
        back.setAlpha(190);
        c.drawRoundRect(new RectF(s * 0.08f, s * 0.20f, s * 0.44f, s * 0.42f), s * 0.05f, s * 0.05f, back);
        c.drawRoundRect(new RectF(s * 0.08f, s * 0.26f, s * 0.92f, s * 0.72f), s * 0.07f, s * 0.07f, back);
        Paint front = fill(color);
        c.drawRoundRect(new RectF(s * 0.08f, s * 0.36f, s * 0.92f, s * 0.80f), s * 0.07f, s * 0.07f, front);
        return b;
    }

    /** Solid play triangle (rounded corners). */
    static Bitmap play(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(s * 0.08f);
        Path path = new Path();
        path.moveTo(s * 0.32f, s * 0.20f);
        path.lineTo(s * 0.32f, s * 0.80f);
        path.lineTo(s * 0.82f, s * 0.50f);
        path.close();
        c.drawPath(path, p);
        return b;
    }

    /** Blue shield with VPN; dimmed when no VPN is connected. */
    static Bitmap vpn(int s, boolean on) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Path shield = new Path();
        shield.moveTo(s * 0.50f, s * 0.04f);
        shield.lineTo(s * 0.90f, s * 0.18f);
        shield.lineTo(s * 0.90f, s * 0.50f);
        shield.cubicTo(s * 0.90f, s * 0.75f, s * 0.70f, s * 0.88f, s * 0.50f, s * 0.97f);
        shield.cubicTo(s * 0.30f, s * 0.88f, s * 0.10f, s * 0.75f, s * 0.10f, s * 0.50f);
        shield.lineTo(s * 0.10f, s * 0.18f);
        shield.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, s, 0xFF55B6FF, 0xFF1E6FE0, Shader.TileMode.CLAMP));
        p.setAlpha(on ? 255 : 120);
        c.drawPath(shield, p);
        Paint edge = stroke(Color.argb(on ? 230 : 120, 255, 255, 255), s * 0.045f);
        c.drawPath(shield, edge);
        Paint t = fill(Color.argb(on ? 255 : 150, 255, 255, 255));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(s * 0.25f);
        c.drawText("VPN", s * 0.5f, s * 0.58f, t);
        return b;
    }

    /** Four mobile-signal bars; the first {@code level} are bright. */
    static Bitmap bars(int s, int level, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        float bw = s * 0.16f;
        float gap = s * 0.07f;
        float[] heights = {0.30f, 0.50f, 0.70f, 0.90f};
        for (int i = 0; i < 4; i++) {
            Paint p = fill(color);
            p.setAlpha(i < level ? 255 : 70);
            float left = s * 0.08f + i * (bw + gap);
            float top = s * (0.94f - heights[i]);
            c.drawRoundRect(new RectF(left, top, left + bw, s * 0.94f), bw * 0.3f, bw * 0.3f, p);
        }
        return b;
    }

    /** Bluetooth rune. */
    static Bitmap bluetooth(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint st = stroke(color, s * 0.10f);
        Path p = new Path();
        p.moveTo(s * 0.28f, s * 0.30f);
        p.lineTo(s * 0.72f, s * 0.68f);
        p.lineTo(s * 0.50f, s * 0.88f);
        p.lineTo(s * 0.50f, s * 0.12f);
        p.lineTo(s * 0.72f, s * 0.32f);
        p.lineTo(s * 0.28f, s * 0.70f);
        c.drawPath(p, st);
        return b;
    }

    /** Video: rounded screen with a play triangle cut out. */
    static Bitmap video(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        c.drawRoundRect(new RectF(s * 0.08f, s * 0.20f, s * 0.92f, s * 0.80f), s * 0.14f, s * 0.14f, p);
        Path tri = new Path();
        tri.moveTo(s * 0.42f, s * 0.36f);
        tri.lineTo(s * 0.42f, s * 0.64f);
        tri.lineTo(s * 0.66f, s * 0.50f);
        tri.close();
        c.drawPath(tri, clear());
        return b;
    }

    /** 3x3 grid of rounded squares (the "all apps" tile in the mockup). */
    static Bitmap apps(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        float cell = s * 0.24f;
        float gap = s * 0.07f;
        float start = (s - (3 * cell + 2 * gap)) / 2f;
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 3; col++) {
                Paint p = fill(color);
                if (r == 2 && col >= 1) p.setAlpha(140);
                float x = start + col * (cell + gap);
                float y = start + r * (cell + gap);
                c.drawRoundRect(new RectF(x, y, x + cell, y + cell), cell * 0.28f, cell * 0.28f, p);
            }
        }
        return b;
    }

    /** Shield with VPN: green and filled while a VPN is connected, outlined and dim otherwise. */
    static Bitmap vpnShield(int s, boolean on) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Path shield = new Path();
        shield.moveTo(s * 0.50f, s * 0.04f);
        shield.lineTo(s * 0.90f, s * 0.18f);
        shield.lineTo(s * 0.90f, s * 0.50f);
        shield.cubicTo(s * 0.90f, s * 0.75f, s * 0.70f, s * 0.88f, s * 0.50f, s * 0.97f);
        shield.cubicTo(s * 0.30f, s * 0.88f, s * 0.10f, s * 0.75f, s * 0.10f, s * 0.50f);
        shield.lineTo(s * 0.10f, s * 0.18f);
        shield.close();
        if (on) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, 0, 0, s, 0xFF5BEA9C, 0xFF1C9E55, Shader.TileMode.CLAMP));
            c.drawPath(shield, p);
        }
        c.drawPath(shield, stroke(Color.argb(on ? 255 : 210, 255, 255, 255), s * 0.07f));
        Paint t = fill(Color.argb(on ? 255 : 210, 255, 255, 255));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(s * 0.27f);
        c.drawText("VPN", s * 0.5f, s * 0.58f, t);
        return b;
    }

    private static void rounded(Canvas c, int s, int top, int bottom) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, s, top, bottom, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, s, s), s * 0.24f, s * 0.24f, p);
    }

    private static void inside(Canvas c, Bitmap icon, int s, float scale) {
        float size = s * scale;
        float off = (s - size) / 2f;
        c.drawBitmap(icon, null, new RectF(off, off, off + size, off + size), new Paint(Paint.FILTER_BITMAP_FLAG));
    }

    /** Blue navigation arrow. */
    static Bitmap navTile(int s) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, s, s, 0xFF63D2FF, 0xFF0A5CFF, Shader.TileMode.CLAMP));
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeWidth(s * 0.05f);
        Path path = new Path();
        path.moveTo(s * 0.50f, s * 0.10f);
        path.lineTo(s * 0.88f, s * 0.88f);
        path.lineTo(s * 0.50f, s * 0.68f);
        path.lineTo(s * 0.12f, s * 0.88f);
        path.close();
        c.drawPath(path, p);
        return b;
    }

    /** Purple tile with a white note. */
    static Bitmap musicTile(int s) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        rounded(c, s, 0xFF9A6BFF, 0xFF4A2BD8);
        inside(c, note(s, Color.WHITE), s, 0.66f);
        return b;
    }

    /** Purple tile with a white car. */
    static Bitmap carTile(int s) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        rounded(c, s, 0xFF7C7CFF, 0xFF3D3FC9);
        inside(c, car(s, Color.WHITE), s, 0.66f);
        return b;
    }

    /** Empty dock slot. */
    static Bitmap plusTile(int s) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint bg = fill(0x33FFFFFF);
        c.drawRoundRect(new RectF(0, 0, s, s), s * 0.24f, s * 0.24f, bg);
        Paint st = stroke(0xCCFFFFFF, s * 0.07f);
        c.drawLine(s * 0.5f, s * 0.3f, s * 0.5f, s * 0.7f, st);
        c.drawLine(s * 0.3f, s * 0.5f, s * 0.7f, s * 0.5f, st);
        return b;
    }

    /** 3x3 round dots (all apps). */
    static Bitmap dots9(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        float step = s * 0.28f;
        float start = s * 0.16f;
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 3; col++) {
                c.drawCircle(start + col * step + s * 0.07f, start + r * step + s * 0.07f, s * 0.075f, p);
            }
        }
        return b;
    }

    static Bitmap chevron(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Path p = new Path();
        p.moveTo(s * 0.35f, s * 0.2f);
        p.lineTo(s * 0.65f, s * 0.5f);
        p.lineTo(s * 0.35f, s * 0.8f);
        c.drawPath(p, stroke(color, s * 0.09f));
        return b;
    }

    static Bitmap monitor(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint st = stroke(color, s * 0.07f);
        c.drawRoundRect(new RectF(s * 0.10f, s * 0.20f, s * 0.90f, s * 0.68f), s * 0.07f, s * 0.07f, st);
        c.drawLine(s * 0.5f, s * 0.68f, s * 0.5f, s * 0.82f, st);
        c.drawLine(s * 0.30f, s * 0.84f, s * 0.70f, s * 0.84f, st);
        return b;
    }

    static Bitmap volume(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        Path sp = new Path();
        sp.moveTo(s * 0.12f, s * 0.40f);
        sp.lineTo(s * 0.30f, s * 0.40f);
        sp.lineTo(s * 0.50f, s * 0.22f);
        sp.lineTo(s * 0.50f, s * 0.78f);
        sp.lineTo(s * 0.30f, s * 0.60f);
        sp.lineTo(s * 0.12f, s * 0.60f);
        sp.close();
        c.drawPath(sp, p);
        Paint st = stroke(color, s * 0.07f);
        c.drawArc(new RectF(s * 0.40f, s * 0.34f, s * 0.70f, s * 0.66f), -50, 100, false, st);
        c.drawArc(new RectF(s * 0.40f, s * 0.20f, s * 0.90f, s * 0.80f), -50, 100, false, st);
        return b;
    }

    static Bitmap squareOutline(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        c.drawRoundRect(new RectF(s * 0.24f, s * 0.24f, s * 0.76f, s * 0.76f), s * 0.08f, s * 0.08f,
                stroke(color, s * 0.08f));
        return b;
    }

    static Bitmap search(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint st = stroke(color, s * 0.10f);
        c.drawCircle(s * 0.44f, s * 0.44f, s * 0.26f, st);
        c.drawLine(s * 0.64f, s * 0.64f, s * 0.86f, s * 0.86f, st);
        return b;
    }

    /** Sun with rays (brightness). */
    static Bitmap sun(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint st = stroke(color, s * 0.07f);
        c.drawCircle(s * 0.5f, s * 0.5f, s * 0.19f, st);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4.0;
            float x0 = s * 0.5f + (float) Math.cos(a) * s * 0.30f;
            float y0 = s * 0.5f + (float) Math.sin(a) * s * 0.30f;
            float x1 = s * 0.5f + (float) Math.cos(a) * s * 0.42f;
            float y1 = s * 0.5f + (float) Math.sin(a) * s * 0.42f;
            c.drawLine(x0, y0, x1, y1, st);
        }
        return b;
    }

    /** Speaker with coloured waves. */
    static Bitmap volumeColored(int s, int body, int waves) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(body);
        Path sp = new Path();
        sp.moveTo(s * 0.10f, s * 0.40f);
        sp.lineTo(s * 0.28f, s * 0.40f);
        sp.lineTo(s * 0.48f, s * 0.20f);
        sp.lineTo(s * 0.48f, s * 0.80f);
        sp.lineTo(s * 0.28f, s * 0.60f);
        sp.lineTo(s * 0.10f, s * 0.60f);
        sp.close();
        c.drawPath(sp, p);
        Paint st = stroke(waves, s * 0.08f);
        c.drawArc(new RectF(s * 0.38f, s * 0.34f, s * 0.68f, s * 0.66f), -50, 100, false, st);
        c.drawArc(new RectF(s * 0.38f, s * 0.20f, s * 0.90f, s * 0.80f), -50, 100, false, st);
        return b;
    }

    /** Painter's palette. */
    static Bitmap palette(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        c.drawOval(new RectF(s * 0.06f, s * 0.10f, s * 0.94f, s * 0.90f), fill(color));
        Paint cut = clear();
        c.drawCircle(s * 0.32f, s * 0.40f, s * 0.07f, cut);
        c.drawCircle(s * 0.50f, s * 0.28f, s * 0.07f, cut);
        c.drawCircle(s * 0.70f, s * 0.36f, s * 0.07f, cut);
        c.drawCircle(s * 0.30f, s * 0.60f, s * 0.07f, cut);
        c.drawCircle(s * 0.66f, s * 0.74f, s * 0.12f, cut);
        return b;
    }

    /** Circle with an "i". */
    static Bitmap infoCircle(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        c.drawCircle(s * 0.5f, s * 0.5f, s * 0.44f, fill(color));
        Paint cut = clear();
        c.drawCircle(s * 0.5f, s * 0.30f, s * 0.06f, cut);
        c.drawRoundRect(new RectF(s * 0.45f, s * 0.42f, s * 0.55f, s * 0.74f), s * 0.03f, s * 0.03f, cut);
        return b;
    }

    static Bitmap star(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Path p = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float r = (i % 2 == 0) ? s * 0.44f : s * 0.19f;
            float x = s * 0.5f + (float) Math.cos(a) * r;
            float y = s * 0.54f + (float) Math.sin(a) * r;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        Paint f = fill(color);
        f.setStyle(Paint.Style.FILL_AND_STROKE);
        f.setStrokeJoin(Paint.Join.ROUND);
        f.setStrokeWidth(s * 0.05f);
        c.drawPath(p, f);
        return b;
    }

    /** 2x2 rounded squares. */
    static Bitmap apps4(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        float a = s * 0.14f;
        float m = s * 0.54f;
        float w = s * 0.32f;
        c.drawRoundRect(new RectF(a, a, a + w, a + w), s * 0.08f, s * 0.08f, p);
        c.drawRoundRect(new RectF(m, a, m + w, a + w), s * 0.08f, s * 0.08f, p);
        c.drawRoundRect(new RectF(a, m, a + w, m + w), s * 0.08f, s * 0.08f, p);
        c.drawRoundRect(new RectF(m, m, m + w, m + w), s * 0.08f, s * 0.08f, p);
        return b;
    }

    /** Chevron pointing left (back). */
    static Bitmap back(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Path p = new Path();
        p.moveTo(s * 0.65f, s * 0.2f);
        p.lineTo(s * 0.35f, s * 0.5f);
        p.lineTo(s * 0.65f, s * 0.8f);
        c.drawPath(p, stroke(color, s * 0.09f));
        return b;
    }

    /** Round clock face. */
    static Bitmap clockFace(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint st = stroke(color, s * 0.07f);
        c.drawCircle(s * 0.5f, s * 0.5f, s * 0.38f, st);
        c.drawLine(s * 0.5f, s * 0.5f, s * 0.5f, s * 0.28f, st);
        c.drawLine(s * 0.5f, s * 0.5f, s * 0.66f, s * 0.58f, st);
        return b;
    }

    /** 3x3 grid of rounded squares (widgets). */
    static Bitmap widgets(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        c.drawRoundRect(new RectF(s * 0.12f, s * 0.14f, s * 0.88f, s * 0.54f), s * 0.1f, s * 0.1f, p);
        c.drawRoundRect(new RectF(s * 0.12f, s * 0.60f, s * 0.46f, s * 0.86f), s * 0.08f, s * 0.08f, p);
        c.drawRoundRect(new RectF(s * 0.54f, s * 0.60f, s * 0.88f, s * 0.86f), s * 0.08f, s * 0.08f, p);
        return b;
    }

    /** Restart the track (circular arrow). */
    static Bitmap replay(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint st = stroke(color, s * 0.085f);
        c.drawArc(new RectF(s * 0.20f, s * 0.22f, s * 0.80f, s * 0.82f), -70, -290, false, st);
        Path head = new Path();
        head.moveTo(s * 0.50f, s * 0.04f);
        head.lineTo(s * 0.50f, s * 0.40f);
        head.lineTo(s * 0.24f, s * 0.22f);
        head.close();
        c.drawPath(head, fill(color));
        return b;
    }

    static Bitmap skipPrev(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        c.drawRoundRect(new RectF(s * 0.16f, s * 0.22f, s * 0.28f, s * 0.78f), s * 0.03f, s * 0.03f, p);
        Path t = new Path();
        t.moveTo(s * 0.84f, s * 0.20f);
        t.lineTo(s * 0.84f, s * 0.80f);
        t.lineTo(s * 0.34f, s * 0.50f);
        t.close();
        Paint tp = fill(color);
        tp.setStyle(Paint.Style.FILL_AND_STROKE);
        tp.setStrokeJoin(Paint.Join.ROUND);
        tp.setStrokeWidth(s * 0.05f);
        c.drawPath(t, tp);
        return b;
    }

    static Bitmap skipNext(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        c.drawRoundRect(new RectF(s * 0.72f, s * 0.22f, s * 0.84f, s * 0.78f), s * 0.03f, s * 0.03f, p);
        Path t = new Path();
        t.moveTo(s * 0.16f, s * 0.20f);
        t.lineTo(s * 0.16f, s * 0.80f);
        t.lineTo(s * 0.66f, s * 0.50f);
        t.close();
        Paint tp = fill(color);
        tp.setStyle(Paint.Style.FILL_AND_STROKE);
        tp.setStrokeJoin(Paint.Join.ROUND);
        tp.setStrokeWidth(s * 0.05f);
        c.drawPath(t, tp);
        return b;
    }

    static Bitmap pause(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint p = fill(color);
        c.drawRoundRect(new RectF(s * 0.26f, s * 0.20f, s * 0.43f, s * 0.80f), s * 0.05f, s * 0.05f, p);
        c.drawRoundRect(new RectF(s * 0.57f, s * 0.20f, s * 0.74f, s * 0.80f), s * 0.05f, s * 0.05f, p);
        return b;
    }

    /** Box with an arrow pointing up (open the app). */
    static Bitmap shareUp(int s, int color) {
        Bitmap b = bmp(s);
        Canvas c = new Canvas(b);
        Paint st = stroke(color, s * 0.08f);
        Path box = new Path();
        box.moveTo(s * 0.34f, s * 0.42f);
        box.lineTo(s * 0.20f, s * 0.42f);
        box.lineTo(s * 0.20f, s * 0.86f);
        box.lineTo(s * 0.80f, s * 0.86f);
        box.lineTo(s * 0.80f, s * 0.42f);
        box.lineTo(s * 0.66f, s * 0.42f);
        c.drawPath(box, st);
        c.drawLine(s * 0.5f, s * 0.12f, s * 0.5f, s * 0.60f, st);
        Path ah = new Path();
        ah.moveTo(s * 0.34f, s * 0.28f);
        ah.lineTo(s * 0.5f, s * 0.12f);
        ah.lineTo(s * 0.66f, s * 0.28f);
        c.drawPath(ah, st);
        return b;
    }
}
