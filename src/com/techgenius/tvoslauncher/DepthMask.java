package com.techgenius.tvoslauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

/**
 * Finds what stands in front of the sky in a wallpaper (mountains, trees, buildings, the car) and
 * cuts it out, so the large clock can sit behind those objects.
 */
final class DepthMask {
    private static final int SMALL_W = 192;
    private static final int SKY_DIFF = 30;

    private DepthMask() {
    }

    /** The picture with only the foreground opaque, or null when nothing reaches the clock area. */
    static Bitmap cutout(Bitmap wall) {
        try {
            int ww = wall.getWidth();
            int wh = wall.getHeight();
            int w = SMALL_W;
            int h = Math.max(8, Math.round(w * (float) wh / ww));
            Bitmap small = Bitmap.createScaledBitmap(wall, w, h, true);
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            if (small != wall) small.recycle();

            // sky: grows down from the top edge while neighbouring colours stay smooth
            boolean[] sky = new boolean[w * h];
            int[] queue = new int[w * h];
            int head = 0;
            int tail = 0;
            for (int x = 0; x < w; x++) {
                sky[x] = true;
                queue[tail++] = x;
            }
            while (head < tail) {
                int i = queue[head++];
                int x = i % w;
                int y = i / w;
                int c = px[i];
                if (y + 1 < h) tail = growSky(px, sky, queue, tail, i + w, c);
                if (y > 0) tail = growSky(px, sky, queue, tail, i - w, c);
                if (x + 1 < w) tail = growSky(px, sky, queue, tail, i + 1, c);
                if (x > 0) tail = growSky(px, sky, queue, tail, i - 1, c);
            }

            // foreground: everything that is not sky and is connected to the bottom edge
            boolean[] fg = new boolean[w * h];
            head = 0;
            tail = 0;
            for (int x = 0; x < w; x++) {
                int i = (h - 1) * w + x;
                if (!sky[i]) {
                    fg[i] = true;
                    queue[tail++] = i;
                }
            }
            while (head < tail) {
                int i = queue[head++];
                int x = i % w;
                int y = i / w;
                if (y + 1 < h) tail = growFg(sky, fg, queue, tail, i + w);
                if (y > 0) tail = growFg(sky, fg, queue, tail, i - w);
                if (x + 1 < w) tail = growFg(sky, fg, queue, tail, i + 1);
                if (x > 0) tail = growFg(sky, fg, queue, tail, i - 1);
            }

            // how much of the clock area do the objects cover?
            int x0 = (int) (w * 0.22f);
            int x1 = (int) (w * 0.78f);
            int y0 = (int) (h * 0.06f);
            int y1 = (int) (h * 0.50f);
            int total = 0;
            int covered = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    total++;
                    if (fg[y * w + x]) covered++;
                }
            }
            if (covered == 0 || covered > total * 0.62f) return null;  // nothing to overlap, or too busy

            float[] a = new float[w * h];
            for (int i = 0; i < a.length; i++) a[i] = fg[i] ? 1f : 0f;
            a = blur(a, w, h);
            a = blur(a, w, h);
            int[] argb = new int[w * h];
            for (int i = 0; i < argb.length; i++) {
                int al = Math.max(0, Math.min(255, Math.round(a[i] * 255f)));
                argb[i] = (al << 24) | 0x00FFFFFF;
            }
            Bitmap mask = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
            Bitmap big = Bitmap.createScaledBitmap(mask, ww, wh, true);
            if (big != mask) mask.recycle();
            Bitmap cut = wall.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(cut);
            Paint p = new Paint();
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            canvas.drawBitmap(big, 0, 0, p);
            big.recycle();
            return cut;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int growSky(int[] px, boolean[] sky, int[] q, int tail, int ni, int c) {
        if (sky[ni]) return tail;
        int d = px[ni];
        int diff = Math.abs(((d >> 16) & 255) - ((c >> 16) & 255))
                + Math.abs(((d >> 8) & 255) - ((c >> 8) & 255))
                + Math.abs((d & 255) - (c & 255));
        if (diff < SKY_DIFF) {
            sky[ni] = true;
            q[tail++] = ni;
        }
        return tail;
    }

    private static int growFg(boolean[] sky, boolean[] fg, int[] q, int tail, int ni) {
        if (!sky[ni] && !fg[ni]) {
            fg[ni] = true;
            q[tail++] = ni;
        }
        return tail;
    }

    private static float[] blur(float[] src, int w, int h) {
        float[] out = new float[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0f;
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int yy = y + dy;
                        int xx = x + dx;
                        if (yy >= 0 && yy < h && xx >= 0 && xx < w) {
                            sum += src[yy * w + xx];
                            n++;
                        }
                    }
                }
                out[y * w + x] = sum / n;
            }
        }
        return out;
    }
}
