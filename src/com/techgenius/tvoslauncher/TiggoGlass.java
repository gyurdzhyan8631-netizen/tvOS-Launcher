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
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

/**
 * iOS-style "Liquid Glass" surface for the TIGGO 9 screens.
 *
 * The home screen renders its background (wallpaper, clock, foreground cut-out, tint) into a small,
 * heavily blurred and saturated bitmap ({@link #capture}). Every glass surface then draws the part
 * of that bitmap that lies behind it, adds a light tint, a soft sheen at the top and a rim that
 * catches the light, exactly like frosted glass in iOS. Everything is a few rounded-rect draws, so
 * the GPU renders it cheaply; no live blur runs per frame.
 */
final class TiggoGlass extends Drawable {
    /** Blurred picture of the home background, mapped onto the whole window. */
    static Bitmap backdrop;
    /** Even softer and darker copy for full-screen layers (menus, all apps, control center). */
    static Bitmap heavy;
    static int winW = 1;
    static int winH = 1;
    static int originX;
    static int originY;

    private final float r;
    private final int tint;
    private final boolean useHeavy;
    private float rimWidth;
    private float rimStrength = 1f;
    private boolean sheen = true;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Matrix mtx = new Matrix();
    private final int[] loc = new int[2];
    private Bitmap shaderFor;
    private BitmapShader shader;

    TiggoGlass(float radius, int tint, float density) {
        this(radius, tint, density, false);
    }

    TiggoGlass(float radius, int tint, float density, boolean heavyBackdrop) {
        this.r = radius;
        this.tint = tint;
        this.useHeavy = heavyBackdrop;
        this.rimWidth = Math.max(1f, 1.2f * density);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(rimWidth);
    }

    /** 0..1: how strongly the rim catches the light. */
    TiggoGlass rim(float strength) {
        rimStrength = strength;
        onBoundsChange(getBounds());
        return this;
    }

    TiggoGlass noSheen() {
        sheen = false;
        return this;
    }

    private int rimA(int a) {
        return (Math.round(a * rimStrength) << 24) | 0xFFFFFF;
    }

    @Override protected void onBoundsChange(Rect b) {
        box.set(b);
        float h = Math.max(1f, box.height());
        glow.setShader(new LinearGradient(0, box.top, 0, box.top + Math.min(h, r * 3f + h * 0.25f),
                0x24FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(box.left, box.top, box.right * 0.35f + box.left * 0.65f, box.bottom,
                new int[]{rimA(0xB0), rimA(0x38), rimA(0x12), rimA(0x30)},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
    }

    private View hostView() {
        Callback cb = getCallback();
        for (int i = 0; i < 4 && cb instanceof Drawable; i++) cb = ((Drawable) cb).getCallback();
        return cb instanceof View ? (View) cb : null;
    }

    @Override public void draw(Canvas c) {
        if (box.isEmpty()) return;
        Bitmap b = useHeavy && heavy != null ? heavy : backdrop;
        View v = hostView();
        boolean glassy = b != null && !b.isRecycled() && v != null;
        if (glassy) {
            if (b != shaderFor) {
                shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                body.setShader(shader);
                shaderFor = b;
            }
            v.getLocationInWindow(loc);
            mtx.setScale(winW / (float) b.getWidth(), winH / (float) b.getHeight());
            mtx.postTranslate(originX - loc[0], originY - loc[1]);
            shader.setLocalMatrix(mtx);
            c.drawRoundRect(box, r, r, body);
            fill.setColor(tint);
        } else {
            // no picture to blur yet: an opaque-enough frosted fill
            int a = Math.max(Color.alpha(tint), 0xB4);
            fill.setColor((a << 24) | (tint & 0xFFFFFF));
        }
        c.drawRoundRect(box, r, r, fill);
        if (sheen) c.drawRoundRect(box, r, r, glow);
        float in = rimWidth / 2f;
        c.drawRoundRect(box.left + in, box.top + in, box.right - in, box.bottom - in, r - in, r - in, rim);
    }

    @Override public void getOutline(Outline outline) {
        Rect b = getBounds();
        outline.setRoundRect(b, r);
        outline.setAlpha(0.35f);
    }

    @Override public void setAlpha(int a) {
    }

    @Override public void setColorFilter(ColorFilter cf) {
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    // ------------------------------------------------------------------ backdrop

    /**
     * Renders the first {@code count} children of {@code home} (its background layers) into the
     * shared blurred backdrop. Cheap: about 1/7 of the screen, box-blurred in a few passes.
     */
    static void capture(ViewGroup home, int count) {
        try {
            int w = home.getWidth();
            int h = home.getHeight();
            if (w < 16 || h < 16) return;
            int sw = Math.max(16, w / 4);
            int sh = Math.max(9, h / 4);
            Bitmap small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            c.drawColor(0xFF0B1226);
            c.scale(sw / (float) w, sh / (float) h);
            for (int i = 0; i < Math.min(count, home.getChildCount()); i++) {
                View child = home.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE || child.getWidth() == 0) continue;
                int save = child.getAlpha() < 1f
                        ? c.saveLayerAlpha(0, 0, w, h, Math.round(child.getAlpha() * 255))
                        : c.save();
                c.translate(child.getLeft(), child.getTop());
                c.concat(child.getMatrix());
                child.draw(c);
                c.restoreToCount(save);
            }
            int bw = Math.max(16, w / 7);
            int bh = Math.max(9, h / 7);
            Bitmap blurred = Ui.glassBackdrop(small, bw, bh);
            Bitmap soft = Ui.blur(blurred, bw / 2, bh / 2, 3, 3);
            small.recycle();
            if (blurred == null) return;
            if (soft != null) {
                // darken a little: menus and big panels need contrast for white text
                Canvas sc = new Canvas(soft);
                sc.drawColor(0x55000000);
            }
            int[] at = new int[2];
            home.getLocationInWindow(at);
            originX = at[0];
            originY = at[1];
            winW = w;
            winH = h;
            backdrop = blurred;  // old bitmaps are left to the GC: something may still draw them
            heavy = soft;
        } catch (Throwable ignored) {
            // keep the previous backdrop
        }
    }

    /** Redraws every view under {@code v} so glass surfaces pick up a new backdrop. */
    static void invalidateTree(View v) {
        v.invalidate();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) invalidateTree(g.getChildAt(i));
        }
    }

    /** Full-screen blurred copy of the backdrop for overlays (Control Center, menus). */
    static Drawable fullScreenBlur(android.content.res.Resources res, int dim) {
        Bitmap b = heavy != null ? heavy : backdrop;
        if (b == null) return new android.graphics.drawable.ColorDrawable(dim | 0xE0000000);
        android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(res, b);
        bd.setFilterBitmap(true);
        return new android.graphics.drawable.LayerDrawable(new Drawable[]{
                bd, new android.graphics.drawable.ColorDrawable(dim)});
    }
}
