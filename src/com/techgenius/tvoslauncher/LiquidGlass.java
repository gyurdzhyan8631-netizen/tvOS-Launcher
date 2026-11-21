package com.techgenius.tvoslauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * iOS 26 "Liquid Glass" surface: clear glass that bends what is behind it.
 * The picture behind is magnified a little like a lens, and much more along a wide edge band
 * (the refraction), a light tint keeps the white icons readable, a bright specular rim catches the
 * light at the top left and the bottom right, and a thin second line inside gives the glass its
 * thickness. It reuses the blurred backdrop of {@link TiggoGlass}.
 */
final class LiquidGlass extends Drawable {
    private final float r;
    private final float density;
    private final int tint;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final RectF inner = new RectF();
    private final Path outerPath = new Path();
    private final Path innerPath = new Path();
    private final Path ring = new Path();
    private final Matrix mtx = new Matrix();
    private final int[] loc = new int[2];
    private float innerR;
    private float rimW;
    private Bitmap shaderFor;
    private BitmapShader shader;

    LiquidGlass(float radius, float density) {
        this(radius, density, 0x2A0E1424);
    }

    LiquidGlass(float radius, float density, int tint) {
        this.r = radius;
        this.density = Math.max(1f, density);
        this.tint = tint;
        rimW = Math.max(1.4f, this.density * 1.1f);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(rimW);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, this.density * 0.8f));
    }

    @Override protected void onBoundsChange(Rect b) {
        box.set(b);
        if (box.isEmpty()) return;
        float t = Math.max(density * 6f, Math.min(box.width(), box.height()) * 0.15f);  // refraction band
        inner.set(box.left + t, box.top + t, box.right - t, box.bottom - t);
        innerR = Math.max(0f, r - t);
        outerPath.reset();
        outerPath.addRoundRect(box, r, r, Path.Direction.CW);
        innerPath.reset();
        innerPath.addRoundRect(inner, innerR, innerR, Path.Direction.CW);
        ring.reset();
        ring.op(outerPath, innerPath, Path.Op.DIFFERENCE);
        // light inside the refraction band: brighter at the top, a little again at the bottom
        band.setShader(new LinearGradient(0, box.top, 0, box.bottom,
                new int[]{0x34FFFFFF, 0x06FFFFFF, 0x0CFFFFFF, 0x22FFFFFF},
                new float[]{0f, 0.45f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        // specular rim: bright top-left and bottom-right, faint in between
        rim.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom,
                new int[]{0xF2FFFFFF, 0x55FFFFFF, 0x12FFFFFF, 0x55FFFFFF, 0xD8FFFFFF},
                new float[]{0f, 0.26f, 0.5f, 0.74f, 1f}, Shader.TileMode.CLAMP));
        edge.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom,
                new int[]{0x70FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x40FFFFFF},
                new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
    }

    private View hostView() {
        Callback cb = getCallback();
        for (int i = 0; i < 4 && cb instanceof Drawable; i++) cb = ((Drawable) cb).getCallback();
        return cb instanceof View ? (View) cb : null;
    }

    @Override public void draw(Canvas c) {
        if (box.isEmpty()) return;
        Bitmap b = TiggoGlass.backdrop;
        View v = hostView();
        boolean glassy = b != null && !b.isRecycled() && v != null;
        float cx = box.centerX();
        float cy = box.centerY();
        if (glassy) {
            if (b != shaderFor) {
                shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                body.setShader(shader);
                shaderFor = b;
            }
            v.getLocationInWindow(loc);
            mtx.setScale(TiggoGlass.winW / (float) b.getWidth(), TiggoGlass.winH / (float) b.getHeight());
            mtx.postTranslate(TiggoGlass.originX - loc[0], TiggoGlass.originY - loc[1]);
            mtx.postScale(1.05f, 1.05f, cx, cy);          // the whole pane magnifies a little
            shader.setLocalMatrix(mtx);
            c.drawRoundRect(box, r, r, body);
            mtx.postScale(1.18f, 1.18f, cx, cy);          // the edge band bends the picture much more
            shader.setLocalMatrix(mtx);
            c.drawPath(ring, body);
            fill.setColor(tint);
        } else {
            int a = Math.max(Color.alpha(tint), 0xB0);
            fill.setColor((a << 24) | (tint & 0xFFFFFF));
        }
        c.drawRoundRect(box, r, r, fill);
        c.drawPath(ring, band);

        float in = rimW / 2f;
        c.drawRoundRect(box.left + in, box.top + in, box.right - in, box.bottom - in,
                Math.max(0f, r - in), Math.max(0f, r - in), rim);
        // thin second line along the inner edge of the band: the thickness of the glass
        c.drawRoundRect(inner, innerR, innerR, edge);
    }

    @Override public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), r);
        outline.setAlpha(0.3f);
    }

    @Override public void setAlpha(int a) {
    }

    @Override public void setColorFilter(ColorFilter cf) {
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
