package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** Look of the TIGGO 9 home and settings: theme, accent colour, menu gradient and widget styles. */
final class TiggoTheme {
    static final String K_THEME = "tiggo9_theme";
    static final String K_ACCENT = "tiggo9_accent";
    static final String K_MENU = "tiggo9_menu_grad";
    static final String K_CLOCK = "tiggo9_clock_style";
    static final String K_WEATHER = "tiggo9_weather_style";
    static final String K_WIDGET_OFF = "tiggo9_widget_off_";
    static final String K_TILE_GLASS = "tiggo9_tile_glass";
    static final String K_ICON_PACK = "tiggo9_icon_pack";
    static final String K_WALL = "tiggo9_wall";

    /** Themes 4..6 follow the time of day; 7 ("Auto") switches between them by sunrise and sunset. */
    static final int THEME_DAY = 4, THEME_DAWN = 5, THEME_NIGHT = 6, THEME_AUTO = 7;
    static final String[] THEME_NAMES = {
            "Стильная", "Тёмная", "Яркая", "Природа", "День", "Рассвет", "Ночь", "Авто (по солнцу)"};
    static final String[] CLOCK_NAMES = {"Классический", "Стеклянный", "Крупный", "Компактный"};
    static final String[] WEATHER_NAMES = {"Два блока", "Компактная", "Минимальная"};
    static final String[] MENU_NAMES = {"Синий", "Фиолетовый", "Графитовый", "Зелёный"};
    static final int[] ACCENTS = {0xFF1C6BFF, 0xFF9B4DFF, 0xFFFF4D9D, 0xFFFF9F1A, 0xFF4CD35A};
    static final int[][] MENU_GRADS = {
            {0xFF0B1B4D, 0xFF1D3F9E}, {0xFF2A0F55, 0xFF6A2BC9},
            {0xFF14161C, 0xFF3A3F4A}, {0xFF0B3B2C, 0xFF1E8C62}};
    static final int[][] THEME_PREVIEW = {
            {0xFF0A2A8F, 0xFF2D7BFF, 0xFF0B1648}, {0xFF050608, 0xFF3A3F4B, 0xFF0E0F13},
            {0xFF6A2BFF, 0xFFFF5CA8, 0xFF2A0F7A}, {0xFF1F6B4A, 0xFF6FC3FF, 0xFF0F3322},
            // day: clear sky blue; dawn: warm orange and pink; night: deep navy
            {0xFF4FB3FF, 0xFFBFE6FF, 0xFF2A7BD8}, {0xFFFFB067, 0xFFFF6F7D, 0xFF4A2466},
            {0xFF050B1F, 0xFF1B2A5A, 0xFF000000},
            // auto: dawn -> day -> night
            {0xFFFF9A5A, 0xFF4FB3FF, 0xFF0A1433}};
    private static final int[] TILE_FILLS = {
            0x3D2B3F78, 0x55181A22, 0x4D6B3FD0, 0x4D2A6B4A,
            0x4D2F7FD8, 0x4DB0563A, 0x5C0A1430};
    private static final int[][] TINTS = {
            {0x1A123B8F, 0x66081230}, {0x33000000, 0x99000000},
            {0x33A13CFF, 0x66FF4DA6}, {0x2200472A, 0x66042A1A},
            {0x0A9AD2FF, 0x4D0B3D80}, {0x29FF8A3D, 0x664A1A4F},
            {0x4D000814, 0xB3000510}};

    private TiggoTheme() {
    }

    static SharedPreferences prefs(Context c) {
        return Profiles.homePrefs(c);
    }

    static int get(Context c, String key, int def, int max) {
        return Math.max(0, Math.min(max, prefs(c).getInt(key, def)));
    }

    static void put(Context c, String key, int value) {
        prefs(c).edit().putInt(key, value).apply();
    }

    /** The theme chosen in the settings (may be {@link #THEME_AUTO}). */
    static int chosenTheme(Context c) {
        return get(c, K_THEME, 0, THEME_NAMES.length - 1);
    }

    static boolean isAuto(Context c) {
        return chosenTheme(c) == THEME_AUTO;
    }

    /** The theme actually drawn now: "Auto" resolves to day, dawn or night. */
    static int theme(Context c) {
        int t = chosenTheme(c);
        return t == THEME_AUTO ? sunTheme(c, System.currentTimeMillis()) : t;
    }

    // ------------------------------------------------------------------ sun

    /** Day phase of the moment: {@link #THEME_DAWN} around sunrise and sunset, else day or night. */
    static int sunTheme(Context c, long now) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(now);
        double minute = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
        double[] sun = sunTimes(c, cal);
        if (Double.isNaN(sun[0])) return sun[1] > 0 ? THEME_DAY : THEME_NIGHT;  // polar day / night
        double rise = sun[0];
        double set = sun[1];
        if (minute >= rise - 45 && minute < rise + 75) return THEME_DAWN;
        if (minute >= set - 60 && minute < set + 30) return THEME_DAWN;  // sunset glow uses the warm look too
        if (minute >= rise + 75 && minute < set - 60) return THEME_DAY;
        return THEME_NIGHT;
    }

    /** True between sunset and sunrise at the saved position. */
    static boolean isNight(Context c, long now) {
        return sunTheme(c, now) == THEME_NIGHT;
    }

    /**
     * Sunrise and sunset in local minutes after midnight for the day of {@code cal}. Uses the weather
     * location; without one the time zone gives an approximate longitude (latitude 50°).
     * Polar night returns {NaN, 0}, polar day {NaN, 1}.
     */
    static double[] sunTimes(Context c, java.util.Calendar cal) {
        double lat = Weather.latitude(c);
        double lon = Weather.longitude(c);
        java.util.TimeZone tz = cal.getTimeZone();
        double tzHours = tz.getOffset(cal.getTimeInMillis()) / 3600000.0;
        if (Double.isNaN(lat) || Double.isNaN(lon) || (lat == 0 && lon == 0)) {
            lat = 50;
            lon = tzHours * 15;
        }
        int n = cal.get(java.util.Calendar.DAY_OF_YEAR);
        double rise = sunEvent(n, lat, lon, true);
        double set = sunEvent(n, lat, lon, false);
        if (Double.isNaN(rise) || Double.isNaN(set) || Double.isInfinite(rise) || Double.isInfinite(set)) {
            return new double[]{Double.NaN, Double.isInfinite(rise) ? 1 : 0};
        }
        rise = ((rise + tzHours) % 24 + 24) % 24 * 60;
        set = ((set + tzHours) % 24 + 24) % 24 * 60;
        return new double[]{rise, set};
    }

    /** NOAA sunrise equation: UTC hour of sunrise / sunset; NaN in polar night, +Infinity in polar day. */
    private static double sunEvent(int dayOfYear, double lat, double lon, boolean rise) {
        double lngHour = lon / 15;
        double t = dayOfYear + ((rise ? 6 : 18) - lngHour) / 24;
        double mAnom = 0.9856 * t - 3.289;
        double l = mAnom + 1.916 * Math.sin(Math.toRadians(mAnom)) + 0.020 * Math.sin(Math.toRadians(2 * mAnom)) + 282.634;
        l = (l % 360 + 360) % 360;
        double ra = Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(l))));
        ra = (ra % 360 + 360) % 360;
        ra += Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90;
        ra /= 15;
        double sinDec = 0.39782 * Math.sin(Math.toRadians(l));
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (Math.cos(Math.toRadians(90.833)) - sinDec * Math.sin(Math.toRadians(lat)))
                / (cosDec * Math.cos(Math.toRadians(lat)));
        if (cosH > 1) return Double.NaN;                     // the sun stays below the horizon
        if (cosH < -1) return Double.POSITIVE_INFINITY;      // the sun stays above the horizon
        double h = rise ? 360 - Math.toDegrees(Math.acos(cosH)) : Math.toDegrees(Math.acos(cosH));
        h /= 15;
        double localT = h + ra - 0.06571 * t - 6.622;
        return ((localT - lngHour) % 24 + 24) % 24;
    }

    static int accent(Context c) {
        return ACCENTS[get(c, K_ACCENT, 0, ACCENTS.length - 1)];
    }

    static int[] menuGrad(Context c) {
        return MENU_GRADS[get(c, K_MENU, 0, MENU_GRADS.length - 1)];
    }

    static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(Color.red(a) * (1 - t) + Color.red(b) * t);
        int g = Math.round(Color.green(a) * (1 - t) + Color.green(b) * t);
        int bl = Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t);
        return Color.rgb(r, g, bl);
    }

    /** Glass fill of tiles and cards on the home screen. */
    static int tileFill(Context c) {
        int base = TILE_FILLS[theme(c)];
        int alpha = Color.alpha(base);
        return withAlpha(mix(base & 0x00FFFFFF | 0xFF000000, accent(c), 0.12f), alpha);
    }

    /** Glass fill of the status capsule, dock and settings panels (menu gradient). */
    static int barFill(Context c) {
        int[] g = menuGrad(c);
        return withAlpha(mix(g[0], g[1], 0.5f), 0x66);
    }

    static int[] tint(Context c) {
        return TINTS[theme(c)];
    }

    static boolean widgetVisible(Context c, String id) {
        return !prefs(c).getBoolean(K_WIDGET_OFF + id, false);
    }

    /** Changes whenever something the home draws depends on changes. */
    static String signature(Context c) {
        SharedPreferences p = prefs(c);
        return theme(c) + "|" + get(c, K_ACCENT, 0, 4) + "|" + get(c, K_MENU, 0, 3) + "|"
                + get(c, K_CLOCK, 1, 3) + "|" + get(c, K_WEATHER, 0, 2) + "|"
                + p.getBoolean(K_WIDGET_OFF + "weather", false) + p.getBoolean(K_WIDGET_OFF + "nav", false)
                + p.getBoolean(K_WIDGET_OFF + "music", false) + "|"
                + p.getBoolean(K_TILE_GLASS, true) + "|" + p.getString(K_ICON_PACK, "auto") + "|"
                + Scenes.getDockScalePercent(c) + "|" + Scenes.getIconScalePercent(c) + "|"
                + p.getString("tiggo9_wapp_pkg_music", "") + p.getString("tiggo9_wapp_pkg_nav", "") + "|"
                + p.getBoolean("tiggo9_sky_on", true) + p.getBoolean("tiggo9_sky_anim", true)
                + p.getInt("tiggo9_start_page", 0) + "|" + p.getString("tiggo9_board", "");
    }
}
