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

    static final String[] THEME_NAMES = {"Стильная", "Тёмная", "Яркая", "Природа"};
    static final String[] CLOCK_NAMES = {"Классический", "Стеклянный", "Крупный", "Компактный"};
    static final String[] WEATHER_NAMES = {"Карточка", "Компактная", "Минимальная"};
    static final String[] MENU_NAMES = {"Синий", "Фиолетовый", "Графитовый", "Зелёный"};
    static final int[] ACCENTS = {0xFF1C6BFF, 0xFF9B4DFF, 0xFFFF4D9D, 0xFFFF9F1A, 0xFF4CD35A};
    static final int[][] MENU_GRADS = {
            {0xFF0B1B4D, 0xFF1D3F9E}, {0xFF2A0F55, 0xFF6A2BC9},
            {0xFF14161C, 0xFF3A3F4A}, {0xFF0B3B2C, 0xFF1E8C62}};
    static final int[][] THEME_PREVIEW = {
            {0xFF0A2A8F, 0xFF2D7BFF, 0xFF0B1648}, {0xFF050608, 0xFF3A3F4B, 0xFF0E0F13},
            {0xFF6A2BFF, 0xFFFF5CA8, 0xFF2A0F7A}, {0xFF1F6B4A, 0xFF6FC3FF, 0xFF0F3322}};
    private static final int[] TILE_FILLS = {0x3D2B3F78, 0x55181A22, 0x4D6B3FD0, 0x4D2A6B4A};
    private static final int[][] TINTS = {
            {0x1A123B8F, 0x66081230}, {0x33000000, 0x99000000},
            {0x33A13CFF, 0x66FF4DA6}, {0x2200472A, 0x66042A1A}};

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

    static int theme(Context c) {
        return get(c, K_THEME, 0, THEME_NAMES.length - 1);
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
                + get(c, K_CLOCK, 0, 3) + "|" + get(c, K_WEATHER, 0, 2) + "|"
                + p.getBoolean(K_WIDGET_OFF + "weather", false) + p.getBoolean(K_WIDGET_OFF + "nav", false)
                + p.getBoolean(K_WIDGET_OFF + "music", false) + "|"
                + p.getBoolean(K_TILE_GLASS, true) + "|" + p.getString(K_ICON_PACK, "auto");
    }
}
