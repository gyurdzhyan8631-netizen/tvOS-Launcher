package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * "Живые обои": a transparent layer drawn above the wallpaper photo. It follows the time of day
 * (warm sunrise / sunset glow, a soft dusk at night) and the current weather (drifting clouds,
 * rain, snow, fog, lightning). Animation runs at about 30 fps only while the home is on screen.
 */
final class LiveWallView extends View {
    /** Weather kinds (same meaning as in {@link SkyView}). */
    static final int CLEAR = 0, CLOUDS = 1, OVERCAST = 2, FOG = 3, RAIN = 4, SNOW = 5, STORM = 6;

    private static final int DROPS = 120;
    private static final int FLAKES = 90;
    private static final int CLOUD_N = 5;
    /** Dimming of the picture by weather (alpha 0..255), indexed by kind. */
    private static final int[] DIM = {0, 0x10, 0x30, 0x28, 0x3C, 0x1C, 0x58};
    /** How many cloud blobs are drawn, indexed by kind. */
    private static final int[] CLOUDS_SHOWN = {0, 3, 5, 5, 5, 4, 5};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random(11);
    private final float[] dx = new float[DROPS];
    private final float[] dy = new float[DROPS];
    private final float[] dv = new float[DROPS];
    private final float[] lineBuf = new float[DROPS * 4];
    private final float[] fx = new float[FLAKES];
    private final float[] fy = new float[FLAKES];
    private final float[] fv = new float[FLAKES];
    private final float[] fp = new float[FLAKES];
    private final float[] pointBuf = new float[FLAKES * 2];
    private final float[] cx = new float[CLOUD_N];
    private final float[] cy = new float[CLOUD_N];
    private final float[] cs = new float[CLOUD_N];
    private final float[] cv = new float[CLOUD_N];

    private int kind = CLEAR;
    private boolean running;
    private long lastFrame;
    private long lastGrade;
    private long nextFlash;
    private float flash;

    // time-of-day state, recomputed every 30 s
    private float dayness = 1f;
    private int nightA;
    private int warmA;

    private Shader lightBlob;
    private Shader darkBlob;
    private Shader warmShader;
    private int seededW;
    private int seededH;

    LiveWallView(Context c) {
        super(c);
        setWillNotDraw(false);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < CLOUD_N; i++) {
            cx[i] = rnd.nextFloat() * 1.4f - 0.2f;
            cy[i] = rnd.nextFloat();
            cs[i] = 0.7f + rnd.nextFloat() * 0.8f;
            cv[i] = 0.004f + rnd.nextFloat() * 0.008f;
        }
        for (int i = 0; i < FLAKES; i++) {
            fv[i] = 0.5f + rnd.nextFloat();
            fp[i] = rnd.nextFloat() * 6.28f;
        }
        for (int i = 0; i < DROPS; i++) dv[i] = 1f + rnd.nextFloat() * 0.7f;
    }

    // ------------------------------------------------------------------ public

    /** Sets the weather from an Open-Meteo / WMO weather code. */
    void setWeatherCode(int code, boolean known) {
        int k;
        if (!known) k = CLEAR;
        else if (code >= 95) k = STORM;
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) k = SNOW;
        else if (code >= 51) k = RAIN;
        else if (code >= 45) k = FOG;
        else if (code == 3) k = OVERCAST;
        else if (code >= 1) k = CLOUDS;
        else k = CLEAR;
        if (k != kind) {
            kind = k;
            lastGrade = 0;
            invalidate();
        }
    }

    /** Called by the home: true while the wallpaper is visible and the home is in front. */
    void setRunning(boolean on) {
        if (on == running) return;
        running = on;
        lastFrame = 0;
        if (on) postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------------ drawing

    private void computeGrade() {
        Calendar cal = Calendar.getInstance();
        double minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        double rise = 6 * 60;
        double set = 18 * 60;
        try {
            double[] s = TiggoTheme.sunTimes(getContext(), cal);
            if (!Double.isNaN(s[0])) {
                rise = s[0];
                set = s[1];
                if (set <= rise) set += 24 * 60;
            } else if (s[1] > 0) {
                rise = -600;
                set = 2000;
            } else {
                rise = 12 * 60;
                set = 12 * 60 + 1;
            }
        } catch (Throwable ignored) {
            // keep 6..18
        }
        if (minute < rise - 12 * 60) minute += 24 * 60;
        double d;
        if (minute < rise - 30 || minute > set + 30) d = 0;
        else if (minute < rise + 60) d = (minute - (rise - 30)) / 90.0;
        else if (minute > set - 60) d = (set + 30 - minute) / 90.0;
        else d = 1;
        dayness = (float) Math.max(0, Math.min(1, d));
        double nearest = Math.min(Math.abs(minute - rise), Math.abs(minute - set));
        float warmth = (float) Math.max(0, 1 - nearest / 70.0);
        if (kind >= RAIN) warmth *= 0.5f;
        nightA = Math.round((1f - dayness) * 0x38);
        warmA = Math.round(warmth * 0x48);
    }

    private void seed(int w, int h) {
        seededW = w;
        seededH = h;
        for (int i = 0; i < DROPS; i++) {
            dx[i] = rnd.nextFloat() * w;
            dy[i] = rnd.nextFloat() * h;
        }
        for (int i = 0; i < FLAKES; i++) {
            fx[i] = rnd.nextFloat() * w;
            fy[i] = rnd.nextFloat() * h;
        }
        lightBlob = new RadialGradient(0, 0, 100f,
                new int[]{0x50FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        darkBlob = new RadialGradient(0, 0, 100f,
                new int[]{0x7A2B3342, 0x002B3342}, null, Shader.TileMode.CLAMP);
        warmShader = new LinearGradient(0, h * 0.3f, 0, h,
                0x00FF8A3D, 0xFFFF8A3D, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        if (w != seededW || h != seededH) seed(w, h);
        long now = SystemClock.uptimeMillis();
        if (lastGrade == 0 || now - lastGrade > 30000) {
            computeGrade();
            lastGrade = now;
        }
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float t = now / 1000f;

        // time of day: dusk at night, a warm glow at sunrise and sunset
        paint.setShader(null);
        if (nightA > 0) {
            paint.setColor(0xFF06102A);
            paint.setAlpha(nightA);
            c.drawRect(0, 0, w, h, paint);
        }
        if (warmA > 0) {
            paint.setShader(warmShader);
            paint.setAlpha(warmA);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }

        // weather: grey dimming and haze
        if (DIM[kind] > 0) {
            paint.setColor(0xFF2B3342);
            paint.setAlpha(Math.round(DIM[kind] * (0.55f + 0.45f * dayness)));
            c.drawRect(0, 0, w, h, paint);
        }
        if (kind == FOG) {
            paint.setColor(0xFFDDE3EA);
            paint.setAlpha(0x46);
            c.drawRect(0, 0, w, h, paint);
        }

        // drifting clouds (fog hugs the lower half)
        int n = CLOUDS_SHOWN[kind];
        boolean dark = kind == OVERCAST || kind == RAIN || kind == STORM || dayness < 0.35f;
        paint.setShader(dark ? darkBlob : lightBlob);
        paint.setAlpha(255);
        for (int i = 0; i < n; i++) {
            cx[i] += cv[i] * dt;
            if (cx[i] > 1.4f) cx[i] = -0.4f;
            float y = kind == FOG ? h * (0.55f + 0.3f * cy[i]) : h * (0.06f + 0.34f * cy[i]);
            float sx = w * 0.45f * cs[i] / 100f;
            c.save();
            c.translate(cx[i] * w, y);
            c.scale(sx, sx * 0.42f);
            c.drawCircle(0, 0, 100f, paint);
            c.restore();
        }
        paint.setShader(null);

        // rain
        if (kind == RAIN || kind == STORM) {
            float len = h * 0.04f;
            float slant = len * 0.18f;
            for (int i = 0; i < DROPS; i++) {
                dy[i] += dv[i] * h * 0.9f * dt;
                dx[i] -= slant * dv[i] * 6f * dt;
                if (dy[i] > h + len || dx[i] < -len) {
                    dy[i] = -len;
                    dx[i] = rnd.nextFloat() * (w + len * 3f);
                }
                lineBuf[i * 4] = dx[i];
                lineBuf[i * 4 + 1] = dy[i];
                lineBuf[i * 4 + 2] = dx[i] + slant;
                lineBuf[i * 4 + 3] = dy[i] - len;
            }
            line.setColor(0xFFC4D6EE);
            line.setAlpha(kind == STORM ? 0x90 : 0x70);
            line.setStrokeWidth(Math.max(1.5f, h / 520f));
            c.drawLines(lineBuf, 0, DROPS * 4, line);
        }

        // snow
        if (kind == SNOW) {
            for (int i = 0; i < FLAKES; i++) {
                fy[i] += fv[i] * h * 0.07f * dt;
                fx[i] += (float) Math.sin(t * 0.8f + fp[i]) * h * 0.03f * dt;
                if (fy[i] > h + 8f) {
                    fy[i] = -8f;
                    fx[i] = rnd.nextFloat() * w;
                }
                pointBuf[i * 2] = fx[i];
                pointBuf[i * 2 + 1] = fy[i];
            }
            line.setColor(Color.WHITE);
            line.setAlpha(0xD0);
            line.setStrokeWidth(Math.max(3f, h / 220f));
            c.drawPoints(pointBuf, 0, FLAKES * 2, line);
        }

        // lightning
        if (kind == STORM) {
            if (nextFlash == 0) nextFlash = now + 3000 + (long) (rnd.nextFloat() * 6000);
            if (now >= nextFlash) {
                flash = 1f;
                nextFlash = now + 4000 + (long) (rnd.nextFloat() * 9000);
            }
            if (flash > 0f) {
                paint.setColor(Color.WHITE);
                paint.setAlpha(Math.round(flash * 0x8C));
                c.drawRect(0, 0, w, h, paint);
                flash = Math.max(0f, flash - dt * 3.5f);
            }
        }

        if (running) postInvalidateDelayed(33);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        lastFrame = 0;
    }
}
