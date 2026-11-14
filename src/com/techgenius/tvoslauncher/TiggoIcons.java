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
}
