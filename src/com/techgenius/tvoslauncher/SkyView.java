package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * "Живое небо": the first TIGGO 9 home page. A sky that follows the real sun (or the chosen
 * Day / Dawn / Night theme): gradient, sun with glow, moon with its phase, twinkling stars,
 * drifting clouds, rain, snow and lightning from the current weather, and three mountain ridges.
 * Animation runs at about 30 fps only while the page is on screen.
 */
final class SkyView extends View {
    /** Weather kinds the sky can show. */
    static final int CLEAR = 0, CLOUDS = 1, OVERCAST = 2, FOG = 3, RAIN = 4, SNOW = 5, STORM = 6;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] ridges = new Path[3];
    private final Random rnd = new Random(9);
    private float[] starXY = new float[0];
    private float[] starPhase = new float[0];
    private float[] px = new float[0];
    private float[] py = new float[0];
    private float[] pv = new float[0];
    private float[] cloudX = new float[0];
    private float[] cloudY = new float[0];
    private float[] cloudS = new float[0];
    private float[] cloudV = new float[0];
    private Bitmap cloudSprite;
    private float[] lineBuf = new float[0];
    private float[] pointBuf = new float[0];

    private int kind = CLEAR;
    /** -1 follows the real sun, otherwise {@link TiggoTheme#THEME_DAY}, DAWN or NIGHT. */
    private int forced = -1;
    private boolean animate = true;
    private boolean running;
    private long lastFrame;
    private long lastSky;
    private float flash;
    private long nextFlash;

    // computed sky state
    private float dayness;   // 0 night .. 1 full day
    private float warmth;    // 0 .. 1 sunrise / sunset glow
    private float sunX, sunY, moonX, moonY;
    private boolean sunUp;
    private double moonPhase;  // 0 new .. 0.5 full .. 1 new
    private int top, mid, bottom;
    private LinearGradient skyShader;
    private int shaderFor;
    private Shader bodyGlow, haze, shade;
    private android.graphics.ColorFilter cloudTint;
    private final RectF dst = new RectF();

    SkyView(Context c) {
        super(c);
        drop.setStrokeCap(Paint.Cap.ROUND);
        star.setStrokeCap(Paint.Cap.ROUND);
    }

    // ------------------------------------------------------------------ public

    /** Sets the sky from an Open-Meteo / WMO weather code. */
    void setWeatherCode(int code, boolean known) {
        int k;
        if (!known) k = CLOUDS;
        else if (code >= 95) k = STORM;
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) k = SNOW;
        else if (code >= 51) k = RAIN;
        else if (code >= 45) k = FOG;
        else if (code == 3) k = OVERCAST;
        else if (code >= 1) k = CLOUDS;
        else k = CLEAR;
        if (k != kind) {
            kind = k;
            seedParticles();
            seedClouds();
            bodyGlow = null;
            lastSky = 0;
            invalidate();
        }
    }

    void setForcedTheme(int theme) {
        int f = theme == TiggoTheme.THEME_DAY || theme == TiggoTheme.THEME_DAWN || theme == TiggoTheme.THEME_NIGHT
                ? theme : -1;
        if (f != forced) {
            forced = f;
            lastSky = 0;
            invalidate();
        }
    }

    void setAnimated(boolean on) {
        animate = on;
        if (!on) running = false;
        invalidate();
    }

    /** Called by the home: true while this page is visible and the home is in front. */
    void setRunning(boolean on) {
        boolean r = on && animate;
        if (r == running) return;
        running = r;
        lastFrame = 0;
        if (r) postInvalidateOnAnimation();
    }

    /** 0 night .. 1 day: the home uses it to pick text shadows. */
    float dayness() {
        return dayness;
    }

    // ------------------------------------------------------------------ geometry

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        buildRidges(w, h);
        seedStars(w, h);
        seedParticles();
        seedClouds();
        cloudSprite = makeCloud(Math.max(64, Math.round(h * 0.42f)));
        skyShader = null;
    }

    private void buildRidges(int w, int h) {
        float[][] spec = {{0.70f, 0.10f, 5}, {0.78f, 0.075f, 7}, {0.87f, 0.05f, 9}};
        Random r = new Random(17);
        for (int i = 0; i < 3; i++) {
            Path p = new Path();
            float base = h * spec[i][0];
            float amp = h * spec[i][1];
            int peaks = (int) spec[i][2];
            p.moveTo(0, h);
            p.lineTo(0, base - amp * r.nextFloat());
            float step = w / (float) peaks;
            for (int k = 0; k < peaks; k++) {
                float x0 = k * step;
                float peakX = x0 + step * (0.35f + 0.3f * r.nextFloat());
                float peakY = base - amp * (0.6f + 0.8f * r.nextFloat());
                float endY = base - amp * 0.3f * r.nextFloat();
                p.quadTo(x0 + step * 0.2f, base - amp * 0.2f, peakX, peakY);
                p.quadTo(x0 + step * 0.8f, base - amp * 0.1f, x0 + step, endY);
            }
            p.lineTo(w, h);
            p.close();
            ridges[i] = p;
        }
    }

    private void seedStars(int w, int h) {
        int n = 140;
        starXY = new float[n * 2];
        starPhase = new float[n];
        Random r = new Random(5);
        for (int i = 0; i < n; i++) {
            starXY[i * 2] = r.nextFloat() * w;
            starXY[i * 2 + 1] = (float) Math.pow(r.nextFloat(), 1.4) * h * 0.66f;
            starPhase[i] = r.nextFloat() * 6.283f;
        }
    }

    private void seedParticles() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        int n = kind == RAIN || kind == STORM ? 170 : (kind == SNOW ? 120 : 0);
        px = new float[n];
        py = new float[n];
        pv = new float[n];
        for (int i = 0; i < n; i++) {
            px[i] = rnd.nextFloat() * w * 1.2f;
            py[i] = rnd.nextFloat() * h;
            pv[i] = 0.6f + rnd.nextFloat() * 0.8f;
        }
        lineBuf = new float[n * 4];
        pointBuf = new float[n * 2];
    }

    private void seedClouds() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        int n = kind == CLEAR ? 2 : (kind == CLOUDS ? 5 : (kind == FOG ? 4 : 7));
        cloudX = new float[n];
        cloudY = new float[n];
        cloudS = new float[n];
        cloudV = new float[n];
        Random r = new Random(23 + kind);
        for (int i = 0; i < n; i++) {
            cloudX[i] = r.nextFloat() * w * 1.3f - w * 0.15f;
            cloudY[i] = h * (0.06f + 0.4f * r.nextFloat());
            cloudS[i] = 0.7f + 0.9f * r.nextFloat();
            cloudV[i] = (6f + 10f * r.nextFloat()) * (w / 1280f);
        }
    }

    /** One soft cloud puff drawn from a few radial gradients. */
    private static Bitmap makeCloud(int size) {
        int w = size * 2;
        Bitmap b = Bitmap.createBitmap(w, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[][] blobs = {{0.30f, 0.62f, 0.28f}, {0.50f, 0.48f, 0.34f}, {0.70f, 0.60f, 0.27f},
                {0.42f, 0.70f, 0.24f}, {0.60f, 0.72f, 0.24f}};
        for (float[] bl : blobs) {
            float cx = bl[0] * w;
            float cy = bl[1] * size;
            float r = bl[2] * size;
            p.setShader(new RadialGradient(cx, cy, r, new int[]{0xD0FFFFFF, 0x70FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, p);
        }
        return b;
    }

    // ------------------------------------------------------------------ sky state

    private void computeSky(int w, int h) {
        Calendar cal = Calendar.getInstance();
        double minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60.0;
        double rise = 6 * 60;
        double set = 18 * 60;
        try {
            double[] s = TiggoTheme.sunTimes(getContext(), cal);
            if (!Double.isNaN(s[0])) {
                rise = s[0];
                set = s[1];
                if (set <= rise) set += 24 * 60;
            } else if (s[1] > 0) {
                rise = 0;
                set = 24 * 60;
            } else {
                rise = 12 * 60;
                set = 12 * 60 + 1;
            }
        } catch (Throwable ignored) {
            // keep 6..18
        }
        if (forced == TiggoTheme.THEME_DAY) minute = (rise + set) / 2;
        else if (forced == TiggoTheme.THEME_DAWN) minute = rise + 25;
        else if (forced == TiggoTheme.THEME_NIGHT) minute = (set + 24 * 60 + rise) / 2;
        if (minute < rise - 12 * 60) minute += 24 * 60;

        double dayLen = Math.max(1, set - rise);
        double p = (minute - rise) / dayLen;           // 0..1 while the sun is up
        sunUp = p >= 0 && p <= 1;
        float horizon = h * 0.74f;
        float arc = h * 0.58f;
        double sunAlt = Math.sin(Math.PI * Math.max(-0.2, Math.min(1.2, p)));
        sunX = (float) (w * (0.06 + 0.88 * Math.max(0, Math.min(1, p))));
        sunY = (float) (horizon - arc * sunAlt);
        // the moon runs across the night
        double nightLen = 24 * 60 - dayLen;
        double q = (minute - set) / nightLen;
        if (q < 0) q += 24 * 60 / nightLen;
        q = q % 1.0;
        moonX = (float) (w * (0.08 + 0.84 * q));
        moonY = (float) (horizon - arc * 0.85 * Math.sin(Math.PI * q));

        // twilight: an hour each side of sunrise and sunset
        double toEdge = Math.min(Math.abs(minute - rise), Math.abs(minute - set));
        float tw = (float) Math.max(0, 1 - toEdge / 70.0);
        float d;
        if (sunUp) d = (float) Math.min(1, 0.35 + Math.sin(Math.PI * p) * 1.6);
        else d = (float) Math.max(0, 0.35 - (toEdge / 70.0) * 0.35);
        dayness = d;
        warmth = tw;
        moonPhase = moonPhase(cal);

        int[] night = {0xFF040A1E, 0xFF0B1636, 0xFF17264F};
        int[] dawn = {0xFF2B2D6E, 0xFFC4587C, 0xFFFFB067};
        int[] day = {0xFF1F6FD1, 0xFF4FA9F5, 0xFFBDE3FF};
        int[] c = new int[3];
        for (int i = 0; i < 3; i++) {
            int base = mix(night[i], day[i], d);
            c[i] = mix(base, dawn[i], tw * (sunUp ? 0.85f : 0.7f));
        }
        float grey = kind == OVERCAST || kind == RAIN ? 0.55f : (kind == STORM ? 0.7f
                : (kind == FOG ? 0.6f : (kind == SNOW ? 0.45f : 0f)));
        if (grey > 0) {
            for (int i = 0; i < 3; i++) {
                int lum = luminance(c[i]);
                c[i] = mix(c[i], Color.rgb(lum, lum, Math.min(255, lum + 8)), grey);
            }
        }
        top = c[0];
        mid = c[1];
        bottom = c[2];
    }

    private static double moonPhase(Calendar cal) {
        // days since a known new moon (2000-01-06 18:14 UTC), synodic month 29.53 days
        double days = (cal.getTimeInMillis() - 947182440000L) / 86400000.0;
        double ph = (days / 29.530588853) % 1.0;
        return ph < 0 ? ph + 1 : ph;
    }

    private static int luminance(int c) {
        return Math.round(Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f);
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastSky > 30000 || skyShader == null) {
            computeSky(w, h);
            lastSky = now;
            bodyGlow = null;
        }
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float t = now / 1000f;

        int sig = top ^ (mid * 31) ^ (bottom * 17) ^ h;
        if (skyShader == null || sig != shaderFor) {
            skyShader = new LinearGradient(0, 0, 0, h * 0.8f, new int[]{top, mid, bottom},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            shaderFor = sig;
            bodyGlow = null;
        }
        float bodyR = h * 0.055f;
        if (bodyGlow == null) {
            float a = kind >= OVERCAST ? 0.35f : 1f;
            if (sunUp) {
                int core = mix(0xFFFFF4C8, 0xFFFFC27A, warmth);
                bodyGlow = new RadialGradient(sunX, sunY, bodyR * 5f,
                        new int[]{(Math.round(0x90 * a) << 24) | (core & 0xFFFFFF), 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            } else {
                float r = bodyR * 0.85f;
                bodyGlow = new RadialGradient(moonX, moonY, r * 4f,
                        new int[]{(Math.round(0x50 * (kind >= OVERCAST ? 0.4f : 1f)) << 24) | 0xDDE6FF, 0x00FFFFFF},
                        null, Shader.TileMode.CLAMP);
            }
            int hz = bottom & 0xFFFFFF;
            haze = new LinearGradient(0, h * 0.5f, 0, h * 0.85f,
                    new int[]{hz, (0x40 << 24) | hz, 0x00000000}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
            shade = new LinearGradient(0, h * 0.7f, 0, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP);
            int ct = mix(mix(0xFF3A4566, 0xFFFFFFFF, dayness), 0xFFFFC9A8, warmth * 0.6f);
            if (kind == OVERCAST || kind == RAIN || kind == STORM) ct = mix(ct, 0xFF7B8394, 0.5f);
            cloudTint = new android.graphics.PorterDuffColorFilter(ct, android.graphics.PorterDuff.Mode.MULTIPLY);
        }
        paint.setShader(skyShader);
        paint.setAlpha(255);
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // stars
        float starA = Math.max(0f, 1f - dayness * 2.2f) * (kind >= OVERCAST ? 0.25f : 1f);
        if (starA > 0.02f) {
            star.setColor(Color.WHITE);
            star.setStrokeWidth(Math.max(1.5f, h / 360f));
            int n = starPhase.length;
            for (int g = 0; g < 3; g++) {
                float a = starA * (0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(t * (0.8f + g * 0.5f) + g * 2.1f)));
                star.setAlpha(Math.round(255 * a));
                int cnt = 0;
                for (int i = g; i < n; i += 3) {
                    pointBuf = ensure(pointBuf, (cnt + 1) * 2);
                    pointBuf[cnt * 2] = starXY[i * 2];
                    pointBuf[cnt * 2 + 1] = starXY[i * 2 + 1];
                    cnt++;
                }
                c.drawPoints(pointBuf, 0, cnt * 2, star);
            }
        }

        // sun or moon with a glow
        glow.setShader(bodyGlow);
        if (sunUp) {
            float a = kind >= OVERCAST ? 0.35f : 1f;
            int core = mix(0xFFFFF4C8, 0xFFFFC27A, warmth);
            c.drawCircle(sunX, sunY, bodyR * 5f, glow);
            paint.setColor(core);
            paint.setAlpha(Math.round(255 * a));
            c.drawCircle(sunX, sunY, bodyR, paint);
        } else {
            drawMoon(c, moonX, moonY, bodyR * 0.85f, kind >= OVERCAST ? 0.4f : 1f);
        }

        // clouds
        if (cloudSprite != null) {
            paint.setColorFilter(cloudTint);
            for (int i = 0; i < cloudX.length; i++) {
                cloudX[i] += cloudV[i] * dt;
                float cw = cloudSprite.getWidth() * cloudS[i];
                if (cloudX[i] > w + cw * 0.1f) cloudX[i] = -cw;
                dst.set(cloudX[i], cloudY[i], cloudX[i] + cw, cloudY[i] + cloudSprite.getHeight() * cloudS[i]);
                paint.setAlpha(kind == CLEAR ? 120 : (kind == FOG ? 150 : 220));
                c.drawBitmap(cloudSprite, null, dst, paint);
            }
            paint.setColorFilter(null);
            paint.setAlpha(255);
        }

        // lightning
        if (kind == STORM) {
            if (now > nextFlash) {
                flash = 1f;
                nextFlash = now + 3500 + rnd.nextInt(6000);
            }
            if (flash > 0.01f) {
                paint.setColor(Color.WHITE);
                paint.setAlpha(Math.round(110 * flash));
                c.drawRect(0, 0, w, h, paint);
                flash *= 0.82f;
            }
        }

        // mountains: darker towards the front, tinted by the sky
        int[] dayR = {0xFF5B7FA8, 0xFF3F5F85, 0xFF26405F};
        int[] nightR = {0xFF141E40, 0xFF0D1630, 0xFF070C1C};
        int[] dawnR = {0xFF7A4A7E, 0xFF52305E, 0xFF2C1A3C};
        for (int i = 0; i < 3; i++) {
            if (ridges[i] == null) continue;
            int col = mix(mix(nightR[i], dayR[i], dayness), dawnR[i], warmth * 0.75f);
            if (kind == FOG) col = mix(col, bottom, 0.45f - i * 0.1f);
            paint.setColor(col);
            c.drawPath(ridges[i], paint);
        }
        // haze over the far ridge
        paint.setShader(haze);
        c.drawRect(0, h * 0.5f, w, h * 0.85f, paint);
        paint.setShader(null);

        // rain / snow
        int n = px.length;
        if (n > 0) {
            float wind = w * 0.06f;
            if (kind == SNOW) {
                drop.setColor(Color.WHITE);
                drop.setAlpha(220);
                drop.setStrokeWidth(Math.max(3f, h / 150f));
                for (int i = 0; i < n; i++) {
                    py[i] += h * 0.12f * pv[i] * dt;
                    px[i] += (float) Math.sin(t * 1.3f + i) * 12f * dt - wind * 0.2f * dt;
                    if (py[i] > h) {
                        py[i] = -10;
                        px[i] = rnd.nextFloat() * w * 1.1f;
                    }
                    pointBuf = ensure(pointBuf, (i + 1) * 2);
                    pointBuf[i * 2] = px[i];
                    pointBuf[i * 2 + 1] = py[i];
                }
                c.drawPoints(pointBuf, 0, n * 2, drop);
            } else {
                drop.setColor(0xFFDDE8FF);
                drop.setAlpha(150);
                drop.setStrokeWidth(Math.max(1.5f, h / 480f));
                float len = h * 0.035f;
                for (int i = 0; i < n; i++) {
                    py[i] += h * 1.3f * pv[i] * dt;
                    px[i] -= wind * pv[i] * dt;
                    if (py[i] > h) {
                        py[i] = -len;
                        px[i] = rnd.nextFloat() * w * 1.2f;
                    }
                    lineBuf[i * 4] = px[i];
                    lineBuf[i * 4 + 1] = py[i];
                    lineBuf[i * 4 + 2] = px[i] - len * 0.2f;
                    lineBuf[i * 4 + 3] = py[i] + len * pv[i];
                }
                c.drawLines(lineBuf, 0, n * 4, drop);
            }
        }

        // a soft shade at the bottom so the dock and text read well
        paint.setShader(shade);
        c.drawRect(0, h * 0.7f, w, h, paint);
        paint.setShader(null);

        if (running && isShown()) {
            // rain and snow need a smooth picture; slow clouds and stars look the same at 12 fps
            long frame = px.length > 0 || flash > 0.01f ? 40 : 80;
            long wait = frame - (SystemClock.uptimeMillis() - now);
            if (wait > 4) postInvalidateDelayed(wait);
            else postInvalidateOnAnimation();
        }
    }

    private void drawMoon(Canvas c, float x, float y, float r, float alpha) {
        c.drawCircle(x, y, r * 4f, glow);
        int save = c.saveLayer(x - r * 1.2f, y - r * 1.2f, x + r * 1.2f, y + r * 1.2f, null);
        paint.setColor(0xFFF1F3FF);
        paint.setAlpha(Math.round(255 * alpha));
        c.drawCircle(x, y, r, paint);
        // the shadow: an offset disc cut out of the lit face
        double ph = moonPhase;
        float lit = (float) ((1 - Math.cos(2 * Math.PI * ph)) / 2);  // 0 new moon .. 1 full moon
        if (lit < 0.985f) {
            float off = 2 * r * lit;
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
            paint.setAlpha(225);
            c.drawCircle(ph < 0.5 ? x - off : x + off, y, r * 1.02f, paint);
            paint.setXfermode(null);
        }
        c.restoreToCount(save);
        paint.setAlpha(255);
    }

    private static float[] ensure(float[] a, int n) {
        return a.length >= n ? a : java.util.Arrays.copyOf(a, Math.max(n, a.length * 2));
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
    }
}
