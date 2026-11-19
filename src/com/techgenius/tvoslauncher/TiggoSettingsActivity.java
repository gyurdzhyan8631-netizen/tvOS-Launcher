package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Touch-first settings of the TIGGO 9 launcher: a glass sidebar whose blue highlight slides between
 * sections, and a "Personalization" section (wallpaper, themes, clock / weather / widget styles).
 */
public class TiggoSettingsActivity extends Activity {
    private static final int REQ_WALL = 9601;
    private static final int REQ_VIDEO = 9602;
    private static final String[] SECTIONS = {
            "Главная", "Wi-Fi", "Bluetooth", "Экран и яркость", "Звук",
            "Персонализация", "Приложения", "Система", "О лаунчере"};
    private static final String[] WALL_PRESETS = {
            "auto", "photo:tiggo9_autumn.jpg", "photo:tiggo9_winter.jpg", "photo:tiggo9_spring.jpg",
            "photo:tiggo9_summer.jpg", "photo:tiggo9_night.jpg", "photo:tiggo9-car.jpg"};
    private static final String[] WALL_CAPTIONS = {
            "Авто: сезон и время суток", "Осень", "Зима", "Весна", "Лето", "Ночь", "TIGGO 9",
            "Фото 1", "Фото 2", "Фото 3", "Фото 4", "Фото 5",
            "Аврора", "Полночь", "Закат", "Океан", "Графит"};

    private interface IntPick {
        boolean on(int index);
    }

    private SharedPreferences prefs;
    private Root rootView;
    private FrameLayout contentHost;
    private TextView titleView;
    private View highlight;
    private final List<TextView> sideLabels = new ArrayList<>();
    private final List<Runnable> backStack = new ArrayList<>();
    private Runnable currentPage;
    private int section = 5;
    private int W;
    private int H;
    private float k = 1f;
    private int rowH;
    private int padTop;

    private final class Root extends FrameLayout {
        Root(Context c) {
            super(c);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w > 0 && h > 0 && (w != W || h != H)) {
                final int nw = w;
                final int nh = h;
                post(() -> {
                    W = nw;
                    H = nh;
                    build();
                });
            }
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        TiggoText.init(this);
        Ui.font(this, "regular");
        prefs = TiggoTheme.prefs(this);
        rootView = new Root(this);
        rootView.setBackgroundColor(0xFF0A1022);
        setContentView(rootView);
    }

    // ------------------------------------------------------------------ helpers

    private int m(float v) {
        return Math.round(v * k);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private FrameLayout.LayoutParams pos(int w, int h, int left, int top) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.START);
        p.leftMargin = left;
        p.topMargin = top;
        return p;
    }

    private FrameLayout.LayoutParams posEnd(int w, int h, int right, int top) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.END);
        p.rightMargin = right;
        p.topMargin = top;
        return p;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private GradientDrawable glass(int radiusPx, int fill) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33FFFFFF | (fill & 0x00FFFFFF), fill});
        d.setCornerRadius(radiusPx);
        d.setStroke(Math.max(1, m(2)), 0x44FFFFFF);
        return d;
    }

    private GradientDrawable mask(int radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(radiusPx);
        return g;
    }

    private RippleDrawable pressable(int radiusPx, int fill) {
        return new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), glass(radiusPx, fill), mask(radiusPx));
    }

    private RippleDrawable rippleOnly(int radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), null, mask(radiusPx));
    }

    private int cardFill() {
        return TiggoTheme.withAlpha(TiggoTheme.mix(TiggoTheme.menuGrad(this)[0], 0xFFFFFFFF, 0.16f), 0x66);
    }

    private TextView text(String value, float size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k);
        t.setTypeface(Ui.font(this, bold ? "semibold" : "regular"));
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private ImageView icon(Bitmap b) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setImageBitmap(b);
        return iv;
    }

    private Bitmap assetBitmap(String path) {
        try (InputStream in = getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (Throwable t) {
            return null;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int accent() {
        return TiggoTheme.accent(this);
    }

    // ------------------------------------------------------------------ frame

    private void build() {
        rootView.removeAllViews();
        sideLabels.clear();
        k = Math.max(0.2f, Math.min(W / 1672f, H / 941f));
        rowH = m(78);
        padTop = m(18);
        int[] grad = TiggoTheme.menuGrad(this);

        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setBackgroundColor(0xFF0A1022);
        rootView.addView(bg, match());
        loadBackdrop(bg);
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{TiggoTheme.withAlpha(grad[0], 0xC8), TiggoTheme.withAlpha(grad[1], 0xEE)}));
        rootView.addView(shade, match());

        buildHeader();
        buildSidebar();
        contentHost = new FrameLayout(this);
        int cw = Math.max(m(600), W - m(400) - m(58));
        int ch = Math.max(m(400), H - m(108) - m(36));
        rootView.addView(contentHost, pos(cw, ch, m(400), m(108)));
        if (currentPage != null) currentPage.run();
        else showSection(section);
    }

    private void loadBackdrop(final ImageView target) {
        final String sel = prefs.getString(TiggoTheme.K_WALL, "auto");
        new Thread(() -> {
            Bitmap small = null;
            try {
                Bitmap src = thumbFor(sel, 480, 270);
                if (src != null) small = Bitmap.createScaledBitmap(src, 64, 36, true);
            } catch (Throwable ignored) {
                // keep the plain background
            }
            final Bitmap result = small;
            runOnUiThread(() -> {
                if (result != null) target.setImageBitmap(result);
            });
        }).start();
    }

    private Bitmap thumbFor(String key, int w, int h) {
        try {
            String style = "auto".equals(key) ? TiggoHome.autoWallpaperStyle(this) : key;
            if (style.startsWith("photo:")) {
                return Scenes.loadAssetBitmap(this, "backgrounds/" + style.substring(6), w, h);
            }
            if (style.startsWith("tiggo:")) {
                Bitmap own = TiggoHome.loadOwnWallpaper(this, style.substring(6));
                return own == null ? null : Bitmap.createScaledBitmap(own, w, h, true);
            }
            return MainActivity.makeWallpaperBase(style);
        } catch (Throwable t) {
            return null;
        }
    }

    private void buildHeader() {
        Bitmap logoBmp = assetBitmap("backgrounds/tiggo9_logo.png");
        if (logoBmp != null) {
            ImageView logo = new ImageView(this);
            logo.setScaleType(ImageView.ScaleType.FIT_START);
            logo.setImageBitmap(logoBmp);
            rootView.addView(logo, pos(m(210), m(38), m(54), m(36)));
        }
        FrameLayout back = new FrameLayout(this);
        back.setBackground(pressable(m(22), 0x33FFFFFF));
        back.addView(icon(TiggoIcons.back(m(40), Color.WHITE)), match());
        back.setOnClickListener(v -> goBack());
        rootView.addView(back, pos(m(84), m(66), m(392), m(24)));

        titleView = text("", 46, true);
        rootView.addView(titleView, pos(ViewGroup.LayoutParams.WRAP_CONTENT, m(66), m(504), m(24)));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30 * k);
        time.setTypeface(Ui.font(this, "regular"));
        time.setIncludeFontPadding(false);
        right.addView(time);
        View div = new View(this);
        div.setBackgroundColor(0x88FFFFFF);
        LinearLayout.LayoutParams dl = lp(Math.max(1, m(2)), m(30));
        dl.leftMargin = m(18);
        dl.rightMargin = m(18);
        right.addView(div, dl);
        right.addView(icon(Glyphs.get("wifi", m(38), Color.WHITE)), lp(m(46), m(46)));
        right.addView(icon(TiggoIcons.bluetooth(m(36), Color.WHITE)), lp(m(44), m(46)));
        rootView.addView(right, posEnd(ViewGroup.LayoutParams.WRAP_CONTENT, m(64), m(58), m(24)));
    }

    private Bitmap sectionIcon(int i, int px) {
        int light = 0xFFE8EEFF;
        switch (i) {
            case 0: return Glyphs.get("house", px, light);
            case 1: return Glyphs.get("wifi", px, light);
            case 2: return TiggoIcons.bluetooth(px, light);
            case 3: return TiggoIcons.sun(px, 0xFFFFB020);
            case 4: return TiggoIcons.volumeColored(px, light, 0xFF3C9BFF);
            case 5: return TiggoIcons.palette(px, Color.WHITE);
            case 6: return TiggoIcons.apps4(px, light);
            case 7: return TiggoIcons.infoCircle(px, light);
            default: return TiggoIcons.star(px, light);
        }
    }

    private void buildSidebar() {
        FrameLayout side = new FrameLayout(this);
        side.setBackground(glass(m(46), TiggoTheme.barFill(this)));

        highlight = new View(this);
        int ac = accent();
        GradientDrawable hl = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{TiggoTheme.mix(ac, 0xFF000000, 0.25f), ac});
        hl.setCornerRadius(m(33));
        highlight.setBackground(hl);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(m(346), m(66));
        hlp.leftMargin = m(13);
        side.addView(highlight, hlp);
        highlight.setTranslationY(padTop + section * rowH + (rowH - m(66)) / 2f);

        for (int i = 0; i < SECTIONS.length; i++) {
            final int idx = i;
            FrameLayout row = new FrameLayout(this);
            row.setBackground(rippleOnly(m(33)));
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(m(50), m(50), Gravity.START | Gravity.CENTER_VERTICAL);
            il.leftMargin = m(26);
            row.addView(icon(sectionIcon(i, m(46))), il);
            TextView label = text(TiggoText.t(SECTIONS[i]), 27, i == section);
            label.setMaxLines(2);
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.START | Gravity.CENTER_VERTICAL);
            ll.leftMargin = m(104);
            ll.rightMargin = m(14);
            row.addView(label, ll);
            sideLabels.add(label);
            row.setOnClickListener(v -> select(idx));
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH);
            rl.topMargin = padTop + i * rowH;
            rl.leftMargin = m(13);
            rl.rightMargin = m(13);
            side.addView(row, rl);
        }
        rootView.addView(side, pos(m(372), padTop * 2 + SECTIONS.length * rowH, m(22), m(108)));
    }

    private void select(int idx) {
        if (idx == 0) {
            finish();
            return;
        }
        section = idx;
        backStack.clear();
        currentPage = null;
        for (int i = 0; i < sideLabels.size(); i++) {
            sideLabels.get(i).setTypeface(Ui.font(this, i == idx ? "semibold" : "regular"));
        }
        highlight.animate().translationY(padTop + idx * rowH + (rowH - m(66)) / 2f)
                .setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        showSection(idx);
    }

    private void goBack() {
        if (!backStack.isEmpty()) {
            Runnable prev = backStack.remove(backStack.size() - 1);
            currentPage = prev;
            prev.run();
        } else {
            finish();
        }
    }

    @Override public void onBackPressed() {
        goBack();
    }

    private void openSub(Runnable sub) {
        backStack.add(currentPage);
        currentPage = sub;
        sub.run();
    }

    private void rerun() {
        if (currentPage != null) currentPage.run();
    }

    private void showPage(String title, View content) {
        titleView.setText(title);
        contentHost.removeAllViews();
        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentHost.addView(sv, match());
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(0, 0, 0, m(40));
        return c;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(glass(m(34), cardFill()));
        return c;
    }

    private void addCard(LinearLayout col, View card, float topMargin) {
        LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = m(topMargin);
        col.addView(card, l);
    }

    private View settingRow(Bitmap ic, String title, String value, View.OnClickListener click, boolean chevron) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(m(30), 0, m(26), 0);
        row.setMinimumHeight(m(90));
        row.setBackground(rippleOnly(m(30)));
        if (ic != null) row.addView(icon(ic), lp(m(52), m(52)));
        TextView t = text(title, 28, false);
        LinearLayout.LayoutParams tl = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.weight = 1f;
        tl.leftMargin = ic != null ? m(26) : 0;
        row.addView(t, tl);
        if (value != null) {
            TextView v = text(value, 26, false);
            v.setAlpha(0.8f);
            row.addView(v, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (chevron) {
            LinearLayout.LayoutParams cl = lp(m(36), m(36));
            cl.leftMargin = m(16);
            row.addView(icon(TiggoIcons.chevron(m(34), 0xCCFFFFFF)), cl);
        }
        if (click != null) row.setOnClickListener(click);
        return row;
    }

    private View rowDivider() {
        View d = new View(this);
        d.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        l.leftMargin = m(108);
        l.rightMargin = m(24);
        d.setLayoutParams(l);
        return d;
    }

    private void openSystem(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t2) {
                toast(TiggoText.t("Настройки недоступны"));
            }
        }
    }

    // ------------------------------------------------------------------ sections

    private void showSection(final int idx) {
        currentPage = () -> showSection(idx);
        switch (idx) {
            case 1: pageSimple("Wi-Fi", wifiState(), TiggoText.t("Открыть настройки Wi-Fi"), Settings.ACTION_WIFI_SETTINGS,
                    Glyphs.get("wifi", m(52), Color.WHITE)); break;
            case 2: pageSimple("Bluetooth", btState(), TiggoText.t("Открыть настройки Bluetooth"),
                    Settings.ACTION_BLUETOOTH_SETTINGS, TiggoIcons.bluetooth(m(52), Color.WHITE)); break;
            case 3: pageDisplay(); break;
            case 4: pageSound(); break;
            case 6: pageApps(); break;
            case 7: pageSystem(); break;
            case 8: pageAbout(); break;
            default: pagePersonalization(); break;
        }
    }

    @SuppressWarnings("deprecation")
    private String wifiState() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                    if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return TiggoText.t("Подключено");
                }
            }
        } catch (Throwable ignored) {
            // unknown
        }
        return TiggoText.t("Нет подключения");
    }

    private String btState() {
        try {
            android.bluetooth.BluetoothAdapter a = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (a != null) return a.isEnabled() ? TiggoText.t("Включён") : TiggoText.t("Выключен");
        } catch (Throwable ignored) {
            // permission missing
        }
        return TiggoText.t("Состояние неизвестно");
    }

    private void pageSimple(String title, String state, String button, final String action, Bitmap ic) {
        LinearLayout col = column();
        LinearLayout c = card();
        c.addView(settingRow(ic, title, state, null, false));
        c.addView(rowDivider());
        c.addView(settingRow(null, button, null, v -> openSystem(action), true));
        addCard(col, c, 0);
        showPage(title, col);
    }

    private View sliderRow(String title, int max, int current, final IntPick onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(m(30), m(18), m(30), m(18));
        row.addView(text(title, 28, false));
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(current);
        sb.setProgressTintList(ColorStateList.valueOf(accent()));
        sb.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) onChange.on(p);
            }

            @Override public void onStartTrackingTouch(SeekBar s) {
            }

            @Override public void onStopTrackingTouch(SeekBar s) {
            }
        });
        LinearLayout.LayoutParams sl = lp(ViewGroup.LayoutParams.MATCH_PARENT, m(64));
        sl.topMargin = m(10);
        row.addView(sb, sl);
        return row;
    }

    private void pageDisplay() {
        LinearLayout col = column();
        LinearLayout c = card();
        if (Settings.System.canWrite(this)) {
            int cur = 128;
            try {
                cur = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            } catch (Throwable ignored) {
                // default
            }
            c.addView(sliderRow(TiggoText.t("Яркость экрана"), 255, cur, p -> {
                try {
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(5, p));
                } catch (Throwable ignored) {
                    // not allowed
                }
                return false;
            }));
        } else {
            c.addView(settingRow(TiggoIcons.sun(m(52), 0xFFFFB020), TiggoText.t("Разрешить изменение яркости"),
                    TiggoText.t("Нужно разрешение"), v -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Throwable t) {
                            openSystem(Settings.ACTION_DISPLAY_SETTINGS);
                        }
                    }, true));
        }
        c.addView(rowDivider());
        c.addView(settingRow(null, TiggoText.t("Настройки экрана Android"), null,
                v -> openSystem(Settings.ACTION_DISPLAY_SETTINGS), true));
        addCard(col, c, 0);
        showPage(TiggoText.t("Экран и яркость"), col);
    }

    private void pageSound() {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        LinearLayout col = column();
        LinearLayout c = card();
        if (am != null) {
            c.addView(sliderRow(TiggoText.t("Музыка, видео и навигация"), am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                    am.getStreamVolume(AudioManager.STREAM_MUSIC), p -> {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0);
                        return false;
                    }));
            c.addView(rowDivider());
            c.addView(sliderRow(TiggoText.t("Уведомления"), am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION),
                    am.getStreamVolume(AudioManager.STREAM_NOTIFICATION), p -> {
                        try {
                            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, p, 0);
                        } catch (Throwable ignored) {
                            // do-not-disturb policy
                        }
                        return false;
                    }));
            c.addView(rowDivider());
        }
        c.addView(settingRow(null, TiggoText.t("Настройки звука Android"), null,
                v -> openSystem(Settings.ACTION_SOUND_SETTINGS), true));
        addCard(col, c, 0);
        showPage(TiggoText.t("Звук"), col);
    }

    private void pageApps() {
        LinearLayout col = column();
        LinearLayout c = card();
        c.addView(settingRow(TiggoIcons.apps4(m(52), Color.WHITE), TiggoText.t("Показать или скрыть приложения"), null,
                v -> startActivity(new Intent(this, SettingsActivity.class)), true));
        c.addView(rowDivider());
        c.addView(settingRow(TiggoIcons.apps4(m(52), Color.WHITE), TiggoText.t("Управление приложениями Android"), null,
                v -> openSystem(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), true));
        addCard(col, c, 0);
        showPage(TiggoText.t("Приложения"), col);
    }

    private void pageSystem() {
        LinearLayout col = column();
        LinearLayout c = card();
        c.addView(settingRow(TiggoIcons.infoCircle(m(52), Color.WHITE), TiggoText.t("Язык и погода лаунчера"), null,
                v -> startActivity(new Intent(this, SettingsActivity.class)), true));
        c.addView(rowDivider());
        c.addView(settingRow(TiggoIcons.clockFace(m(52), Color.WHITE), TiggoText.t("Дата и время"), null,
                v -> openSystem(Settings.ACTION_DATE_SETTINGS), true));
        c.addView(rowDivider());
        c.addView(settingRow(TiggoIcons.monitor(m(52), Color.WHITE), TiggoText.t("Открывать приложения в окне"),
                prefs.getBoolean("tiggo9_window_mode", false) ? TiggoText.t("Вкл") : TiggoText.t("Выкл"), v -> {
                    prefs.edit().putBoolean("tiggo9_window_mode", !prefs.getBoolean("tiggo9_window_mode", false)).apply();
                    rerun();
                }, false));
        c.addView(rowDivider());
        c.addView(settingRow(null, TiggoText.t("Размер окна"), prefs.getInt("tiggo9_window_pct", 58) + "%",
                v -> openSub(this::pageWindowSize), true));
        c.addView(rowDivider());
        c.addView(settingRow(null, TiggoText.t("Включить окна свободной формы (Android)"), null,
                v -> openSub(this::pageFreeformHelp), true));
        c.addView(rowDivider());
        c.addView(settingRow(null, TiggoText.t("Все настройки Android"), null,
                v -> openSystem(Settings.ACTION_SETTINGS), true));
        addCard(col, c, 0);
        showPage(TiggoText.t("Система"), col);
    }

    private void pageAbout() {
        LinearLayout col = column();
        LinearLayout c = card();
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            // unknown
        }
        c.addView(settingRow(TiggoIcons.star(m(52), Color.WHITE), "TIGGO 9 Launcher", "v" + version, null, false));
        c.addView(rowDivider());
        c.addView(settingRow(TiggoIcons.dots9(m(52), Color.WHITE), TiggoText.t("Воспроизвести промо-ролик"), null, v -> {
            prefs.edit().putBoolean("tiggo9_play_promo", true).apply();
            finish();
        }, true));
        c.addView(rowDivider());
        c.addView(settingRow(null, TiggoText.t("Выбрать видео для ролика (MP4)"), null, v -> {
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.setType("video/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(pick, REQ_VIDEO);
            } catch (Throwable t) {
                toast(TiggoText.t("Выбор видео недоступен на этом устройстве"));
            }
        }, true));
        c.addView(rowDivider());
        c.addView(settingRow(null, TiggoText.t("Сбросить оформление"), null, v -> {
            SharedPreferences.Editor ed = prefs.edit();
            ed.remove(TiggoTheme.K_THEME).remove(TiggoTheme.K_ACCENT).remove(TiggoTheme.K_MENU)
                    .remove(TiggoTheme.K_CLOCK).remove(TiggoTheme.K_WEATHER);
            for (String id : new String[]{"weather", "nav", "music"}) ed.remove(TiggoTheme.K_WIDGET_OFF + id);
            ed.apply();
            toast(TiggoText.t("Оформление сброшено"));
            build();
        }, false));
        addCard(col, c, 0);
        TextView hint = text(TiggoText.t("Долгое нажатие на логотип TIGGO 9 на главном экране запускает ролик."), 22, false);
        hint.setAlpha(0.7f);
        hint.setPadding(m(8), m(24), m(8), 0);
        col.addView(hint);
        showPage(TiggoText.t("О лаунчере"), col);
    }

    // ------------------------------------------------------------------ personalization

    private void pagePersonalization() {
        LinearLayout col = column();

        LinearLayout wall = new LinearLayout(this);
        wall.setOrientation(LinearLayout.HORIZONTAL);
        wall.setGravity(Gravity.CENTER_VERTICAL);
        wall.setPadding(m(18), m(16), m(26), m(16));
        wall.setBackground(pressable(m(34), cardFill()));
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable tb = new GradientDrawable();
        tb.setColor(0x33FFFFFF);
        tb.setCornerRadius(m(22));
        thumb.setBackground(tb);
        thumb.setClipToOutline(true);
        wall.addView(thumb, lp(m(450), m(148)));
        loadThumb(thumb, prefs.getString(TiggoTheme.K_WALL, "auto"));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.setGravity(Gravity.CENTER_VERTICAL);
        tcol.addView(text(TiggoText.t("Обои"), 44, true));
        TextView sub = text(TiggoText.t("Выберите изображение для главного экрана"), 24, false);
        sub.setAlpha(0.85f);
        tcol.addView(sub);
        LinearLayout.LayoutParams tl = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.weight = 1f;
        tl.leftMargin = m(34);
        wall.addView(tcol, tl);
        FrameLayout chev = new FrameLayout(this);
        GradientDrawable cc = new GradientDrawable();
        cc.setShape(GradientDrawable.OVAL);
        cc.setColor(0x33FFFFFF);
        chev.setBackground(cc);
        chev.addView(icon(TiggoIcons.chevron(m(34), Color.WHITE)), match());
        wall.addView(chev, lp(m(56), m(56)));
        wall.setOnClickListener(v -> openSub(this::pageWallpapers));
        addCard(col, wall, 0);

        TextView lab = text(TiggoText.t("Темы оформления"), 34, false);
        LinearLayout.LayoutParams ll = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.topMargin = m(30);
        ll.bottomMargin = m(14);
        ll.leftMargin = m(8);
        col.addView(lab, ll);
        int cur = TiggoTheme.chosenTheme(this);
        // four theme cards per row: classic looks, then day / dawn / night / auto
        for (int row = 0; row * 4 < TiggoTheme.THEME_NAMES.length; row++) {
            LinearLayout themes = new LinearLayout(this);
            themes.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = row * 4; i < Math.min(row * 4 + 4, TiggoTheme.THEME_NAMES.length); i++) {
                LinearLayout.LayoutParams tp = lp(0, m(150));
                tp.weight = 1f;
                if (i > row * 4) tp.leftMargin = m(18);
                themes.addView(themeCard(i, i == cur), tp);
            }
            LinearLayout.LayoutParams rl = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (row > 0) rl.topMargin = m(18);
            col.addView(themes, rl);
        }
        if (cur == TiggoTheme.THEME_AUTO) {
            TextView now = text(TiggoText.t("Сейчас: ") + TiggoText.t(TiggoTheme.THEME_NAMES[TiggoTheme.theme(this)]) + " · " + sunLabel(), 22, false);
            now.setAlpha(0.8f);
            LinearLayout.LayoutParams nl = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nl.topMargin = m(12);
            nl.leftMargin = m(8);
            col.addView(now, nl);
        }

        LinearLayout list = card();
        boolean glassOn = prefs.getBoolean(TiggoTheme.K_TILE_GLASS, true);
        list.addView(settingRow(TiggoIcons.apps4(m(52), 0xFF7FB2FF), TiggoText.t("Стиль иконок"),
                glassOn ? TiggoText.t("Стеклянный") : TiggoText.t("Оригинальные"), v -> {
                    prefs.edit().putBoolean(TiggoTheme.K_TILE_GLASS, !prefs.getBoolean(TiggoTheme.K_TILE_GLASS, true)).apply();
                    rerun();
                }, true));
        list.addView(rowDivider());
        list.addView(settingRow(TiggoIcons.apps4(m(52), Color.WHITE), TiggoText.t("Размер иконок"), Scenes.iconScaleLabel(this),
                v -> openSub(this::pageIconSize), true));
        list.addView(rowDivider());
        list.addView(settingRow(TiggoIcons.apps4(m(52), Color.WHITE), TiggoText.t("Размер дока"), Scenes.dockScaleLabel(this),
                v -> openSub(this::pageDockSize), true));
        list.addView(rowDivider());
        list.addView(settingRow(TiggoIcons.note(m(52), Color.WHITE), TiggoText.t("Музыкальное приложение"), widgetAppLabel("music"),
                v -> openSub(() -> pageWidgetApp("music", TiggoText.t("Музыкальное приложение"))), true));
        list.addView(rowDivider());
        list.addView(settingRow(TiggoIcons.navigation(m(52), Color.WHITE), TiggoText.t("Навигатор"), widgetAppLabel("nav"),
                v -> openSub(() -> pageWidgetApp("nav", TiggoText.t("Навигатор"))), true));
        list.addView(rowDivider());
        list.addView(settingRow(TiggoIcons.apps4(m(52), Color.WHITE), TiggoText.t("Пак иконок"), iconPackName(),
                v -> openSub(this::pageIconPack), true));
        list.addView(rowDivider());
        list.addView(settingRow(TiggoIcons.clockFace(m(52), Color.WHITE), TiggoText.t("Стиль часов"),
                TiggoText.t(TiggoTheme.CLOCK_NAMES[TiggoTheme.get(this, TiggoTheme.K_CLOCK, 1, 3)]),
                v -> openSub(() -> pageChoice(TiggoText.t("Стиль часов"), TiggoTheme.CLOCK_NAMES,
                        TiggoTheme.get(this, TiggoTheme.K_CLOCK, 1, 3), i -> {
                            TiggoTheme.put(this, TiggoTheme.K_CLOCK, i);
                            return false;
                        })), true));
        list.addView(rowDivider());
        list.addView(settingRow(Glyphs.get("cloud", m(52), Color.WHITE), TiggoText.t("Стиль погоды"),
                TiggoText.t(TiggoTheme.WEATHER_NAMES[TiggoTheme.get(this, TiggoTheme.K_WEATHER, 0, 2)]),
                v -> openSub(() -> pageChoice(TiggoText.t("Стиль погоды"), TiggoTheme.WEATHER_NAMES,
                        TiggoTheme.get(this, TiggoTheme.K_WEATHER, 0, 2), i -> {
                            TiggoTheme.put(this, TiggoTheme.K_WEATHER, i);
                            return false;
                        })), true));
        list.addView(rowDivider());
        list.addView(settingRow(TiggoIcons.widgets(m(52), Color.WHITE), TiggoText.t("Виджеты"), visibleWidgetsLabel(),
                v -> openSub(this::pageWidgets), true));
        list.addView(rowDivider());
        list.addView(settingRow(TiggoIcons.palette(m(52), Color.WHITE), TiggoText.t("Градиент меню"),
                TiggoText.t(TiggoTheme.MENU_NAMES[TiggoTheme.get(this, TiggoTheme.K_MENU, 0, 3)]),
                v -> openSub(() -> pageChoice(TiggoText.t("Градиент меню"), TiggoTheme.MENU_NAMES,
                        TiggoTheme.get(this, TiggoTheme.K_MENU, 0, 3), i -> {
                            TiggoTheme.put(this, TiggoTheme.K_MENU, i);
                            return true;
                        })), true));
        list.addView(rowDivider());
        list.addView(accentRow());
        addCard(col, list, 22);
        showPage(TiggoText.t("Персонализация"), col);
    }

    private View accentRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(m(30), 0, m(30), 0);
        row.setMinimumHeight(m(90));
        row.addView(icon(TiggoIcons.palette(m(52), Color.WHITE)), lp(m(52), m(52)));
        TextView t = text(TiggoText.t("Цветовая схема"), 28, false);
        LinearLayout.LayoutParams tl = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.weight = 1f;
        tl.leftMargin = m(26);
        row.addView(t, tl);
        int cur = TiggoTheme.get(this, TiggoTheme.K_ACCENT, 0, TiggoTheme.ACCENTS.length - 1);
        for (int i = 0; i < TiggoTheme.ACCENTS.length; i++) {
            final int idx = i;
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(TiggoTheme.ACCENTS[i]);
            if (i == cur) g.setStroke(Math.max(2, m(4)), Color.WHITE);
            dot.setBackground(g);
            dot.setOnClickListener(v -> {
                TiggoTheme.put(this, TiggoTheme.K_ACCENT, idx);
                build();
            });
            LinearLayout.LayoutParams dl = lp(m(46), m(46));
            dl.leftMargin = m(14);
            row.addView(dot, dl);
        }
        return row;
    }

    private View themeCard(final int i, boolean selected) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, TiggoTheme.THEME_PREVIEW[i]);
        g.setCornerRadius(m(26));
        g.setStroke(selected ? Math.max(2, m(4)) : Math.max(1, m(2)), selected ? accent() : 0x66FFFFFF);
        f.setBackground(g);
        TextView t = text(TiggoText.t(TiggoTheme.THEME_NAMES[i]), 26, true);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        tl.leftMargin = m(20);
        tl.bottomMargin = m(18);
        f.addView(t, tl);
        if (selected) {
            TextView check = text("✓", 28, true);
            check.setGravity(Gravity.CENTER);
            GradientDrawable cg = new GradientDrawable();
            cg.setShape(GradientDrawable.OVAL);
            cg.setColor(accent());
            check.setBackground(cg);
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(m(44), m(44), Gravity.BOTTOM | Gravity.END);
            cl.rightMargin = m(14);
            cl.bottomMargin = m(14);
            f.addView(check, cl);
        }
        f.setOnClickListener(v -> {
            TiggoTheme.put(this, TiggoTheme.K_THEME, i);
            build();
        });
        return f;
    }

    /** "Восход 07:48 · Закат 16:21" for the saved weather location. */
    private String sunLabel() {
        double[] sun = TiggoTheme.sunTimes(this, java.util.Calendar.getInstance());
        if (Double.isNaN(sun[0])) return sun[1] > 0 ? TiggoText.t("Полярный день") : TiggoText.t("Полярная ночь");
        return TiggoText.t("Восход ") + hm(sun[0]) + TiggoText.t(" · Закат ") + hm(sun[1]);
    }

    private static String hm(double minutes) {
        int m = (int) Math.round(minutes) % (24 * 60);
        return String.format(java.util.Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    private String visibleWidgetsLabel() {
        int n = 0;
        for (String id : new String[]{"weather", "nav", "music"}) if (TiggoTheme.widgetVisible(this, id)) n++;
        return n == 3 ? TiggoText.t("Показать все") : TiggoText.t("Показано: ") + n;
    }

    private String iconPackName() {
        String saved = prefs.getString(TiggoTheme.K_ICON_PACK, IconPacks.AUTO);
        if (IconPacks.AUTO.equals(saved)) return TiggoText.t("Авто");
        if (IconPacks.NONE.equals(saved)) return TiggoText.t("Системные");
        for (IconPacks.Pack p : IconPacks.installed(this)) if (p.pkg.equals(saved)) return p.label;
        return TiggoText.t("Авто");
    }

    private void pageChoice(String title, String[] options, int current, IntPick pick) {
        LinearLayout col = column();
        LinearLayout c = card();
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            if (i > 0) c.addView(rowDivider());
            c.addView(settingRow(null, TiggoText.t(options[i]), i == current ? "✓" : null, v -> {
                boolean rebuild = pick.on(idx);
                goBack();
                if (rebuild) build();
            }, false));
        }
        addCard(col, c, 0);
        showPage(title, col);
    }

    private void pagePercent(String title, final int[] steps, int current, final boolean dock) {
        String[] labels = new String[steps.length];
        int cur = 0;
        for (int i = 0; i < steps.length; i++) {
            int diff = steps[i] - 100;
            labels[i] = diff == 0 ? TiggoText.t("100% (По умолчанию)") : (diff > 0 ? steps[i] + "% (+" + diff + "%)"
                    : steps[i] + "% (" + diff + "%)");
            if (steps[i] == current) cur = i;
        }
        pageChoice(title, labels, cur, i -> {
            if (dock) Scenes.setDockScalePercent(this, steps[i]);
            else Scenes.setIconScalePercent(this, steps[i]);
            return false;
        });
    }

    private void pageIconSize() {
        pagePercent(TiggoText.t("Размер иконок"), new int[]{60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160},
                Scenes.getIconScalePercent(this), false);
    }

    private void pageDockSize() {
        pagePercent(TiggoText.t("Размер дока"), new int[]{50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150},
                Scenes.getDockScalePercent(this), true);
    }

    private String widgetAppLabel(String id) {
        String pkg = prefs.getString("tiggo9_wapp_pkg_" + id, null);
        if (pkg == null) return TiggoText.t("Авто");
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return TiggoText.t("Авто");
        }
    }

    private Bitmap bitmapOf(Drawable d, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, px, px);
        d.draw(c);
        return b;
    }

    /** Choose which app the music widget controls (or the navigation capsule opens). */
    private void pageWidgetApp(final String id, String title) {
        LinearLayout col = column();
        LinearLayout c = card();
        final String current = prefs.getString("tiggo9_wapp_pkg_" + id, null);
        c.addView(settingRow(null, TiggoText.t("Автоматически"), current == null ? "✓" : null, v -> {
            prefs.edit().remove("tiggo9_wapp_pkg_" + id).remove("tiggo9_wapp_" + id).apply();
            goBack();
        }, false));
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        java.util.Collections.sort(apps, (a, b) -> a.loadLabel(pm).toString()
                .compareToIgnoreCase(b.loadLabel(pm).toString()));
        for (ResolveInfo ri : apps) {
            final String pkg = ri.activityInfo.packageName;
            c.addView(rowDivider());
            Bitmap ic;
            try {
                ic = bitmapOf(ri.loadIcon(pm), m(52));
            } catch (Throwable t) {
                ic = null;
            }
            c.addView(settingRow(ic, ri.loadLabel(pm).toString(), pkg.equals(current) ? "✓" : null, v -> {
                prefs.edit().putString("tiggo9_wapp_pkg_" + id, pkg).remove("tiggo9_wapp_" + id).apply();
                toast(TiggoText.t("Источник: ") + widgetAppLabel(id));
                goBack();
            }, false));
        }
        addCard(col, c, 0);
        showPage(title, col);
    }

    private void pageWindowSize() {
        final int[] steps = {45, 50, 58, 65, 72, 80};
        String[] labels = new String[steps.length];
        int cur = 2;
        int saved = prefs.getInt("tiggo9_window_pct", 58);
        for (int i = 0; i < steps.length; i++) {
            labels[i] = steps[i] + TiggoText.t("% ширины экрана") + (steps[i] == 58 ? TiggoText.t(" (По умолчанию)") : "");
            if (steps[i] == saved) cur = i;
        }
        pageChoice(TiggoText.t("Размер окна"), labels, cur, i -> {
            prefs.edit().putInt("tiggo9_window_pct", steps[i]).apply();
            return false;
        });
    }

    private void pageFreeformHelp() {
        LinearLayout col = column();
        LinearLayout c = card();
        c.addView(settingRow(null, TiggoText.t("Открыть параметры разработчика"), null,
                v -> openSystem(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), true));
        addCard(col, c, 0);
        TextView hint = text(TiggoText.t("Чтобы приложения открывались в окне, которое можно двигать пальцем, в параметрах разработчика включите «Окна свободной формы» и «Изменение размера в многооконном режиме», затем перезагрузите магнитолу. Либо по ADB: settings put global enable_freeform_support 1 и settings put global force_resizable_activities 1."), 24, false);
        hint.setAlpha(0.8f);
        hint.setGravity(Gravity.TOP);
        hint.setPadding(m(8), m(26), m(8), 0);
        col.addView(hint);
        showPage(TiggoText.t("Оконный режим"), col);
    }

    private void pageIconPack() {
        LinearLayout col = column();
        LinearLayout c = card();
        String saved = prefs.getString(TiggoTheme.K_ICON_PACK, IconPacks.AUTO);
        List<String> keys = new ArrayList<>();
        List<String> names = new ArrayList<>();
        keys.add(IconPacks.AUTO);
        names.add(TiggoText.t("Авто (Fluid, если установлен)"));
        keys.add(IconPacks.NONE);
        names.add(TiggoText.t("Системные иконки"));
        for (IconPacks.Pack p : IconPacks.installed(this)) {
            keys.add(p.pkg);
            names.add(p.label);
        }
        for (int i = 0; i < keys.size(); i++) {
            final String key = keys.get(i);
            if (i > 0) c.addView(rowDivider());
            c.addView(settingRow(null, names.get(i), key.equals(saved) ? "✓" : null, v -> {
                prefs.edit().putString(TiggoTheme.K_ICON_PACK, key).apply();
                goBack();
            }, false));
        }
        addCard(col, c, 0);
        showPage(TiggoText.t("Пак иконок"), col);
    }

    private void pageWidgets() {
        LinearLayout col = column();
        LinearLayout c = card();
        String[] ids = {"weather", "nav", "music"};
        String[] names = {TiggoText.t("Погода"), TiggoText.t("Навигация"), TiggoText.t("Музыка")};
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            if (i > 0) c.addView(rowDivider());
            c.addView(settingRow(null, names[i], TiggoTheme.widgetVisible(this, id) ? TiggoText.t("Вкл") : TiggoText.t("Выкл"), v -> {
                prefs.edit().putBoolean(TiggoTheme.K_WIDGET_OFF + id, TiggoTheme.widgetVisible(this, id)).apply();
                rerun();
            }, false));
        }
        addCard(col, c, 0);
        TextView hint = text(TiggoText.t("Порядок и размер виджетов меняются удержанием виджета на главном экране."), 22, false);
        hint.setAlpha(0.7f);
        hint.setPadding(m(8), m(22), m(8), 0);
        col.addView(hint);
        showPage(TiggoText.t("Виджеты"), col);
    }

    // ---- wallpapers

    private void loadThumb(final ImageView target, final String key) {
        new Thread(() -> {
            final Bitmap b = thumbFor(key, 480, 270);
            runOnUiThread(() -> {
                if (b != null) target.setImageBitmap(b);
            });
        }).start();
    }

    private void pageWallpapers() {
        LinearLayout col = column();
        final String current = prefs.getString(TiggoTheme.K_WALL, "auto");
        final List<String> keys = new ArrayList<>();
        final List<String> captions = new ArrayList<>();
        for (int i = 0; i < WALL_PRESETS.length; i++) {
            keys.add(WALL_PRESETS[i]);
            captions.add(TiggoText.t(WALL_CAPTIONS[i]));
        }
        for (int i = 0; i < MainActivity.WALL_KEYS.length; i++) {
            keys.add(MainActivity.WALL_KEYS[i]);
            int ci = WALL_PRESETS.length + i;
            captions.add(ci < WALL_CAPTIONS.length ? TiggoText.t(WALL_CAPTIONS[ci]) : "");
        }
        if (current.startsWith("tiggo:")) {
            keys.add(0, current);
            captions.add(0, TiggoText.t("Своё фото"));
        }
        final List<ImageView> thumbs = new ArrayList<>();
        int perRow = 3;
        // gallery tile + all wallpapers
        List<View> cells = new ArrayList<>();
        FrameLayout gallery = new FrameLayout(this);
        gallery.setBackground(pressable(m(26), cardFill()));
        TextView plus = text("+", 80, false);
        plus.setGravity(Gravity.CENTER);
        gallery.addView(plus, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        TextView gl = text(TiggoText.t("Из галереи"), 24, true);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        glp.bottomMargin = m(16);
        gallery.addView(gl, glp);
        gallery.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.setType("image/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(pick, REQ_WALL);
            } catch (Throwable t) {
                toast(TiggoText.t("Выбор фото недоступен на этом устройстве"));
            }
        });
        cells.add(gallery);
        for (int i = 0; i < keys.size(); i++) {
            final String key = keys.get(i);
            FrameLayout f = new FrameLayout(this);
            boolean sel = key.equals(current);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable fr = new GradientDrawable();
            fr.setColor(0x33FFFFFF);
            fr.setCornerRadius(m(26));
            fr.setStroke(sel ? Math.max(2, m(5)) : Math.max(1, m(2)), sel ? accent() : 0x66FFFFFF);
            iv.setBackground(fr);
            iv.setClipToOutline(true);
            f.addView(iv, match());
            thumbs.add(iv);
            TextView cap = text(captions.get(i), 24, true);
            cap.setShadowLayer(m(6), 0, m(2), 0xAA000000);
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
            cl.leftMargin = m(18);
            cl.bottomMargin = m(14);
            f.addView(cap, cl);
            f.setOnClickListener(v -> {
                prefs.edit().putString(TiggoTheme.K_WALL, key).apply();
                toast(TiggoText.t("Обои изменены"));
                goBack();
            });
            cells.add(f);
        }
        LinearLayout row = null;
        for (int i = 0; i < cells.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rl = lp(ViewGroup.LayoutParams.MATCH_PARENT, m(210));
                rl.bottomMargin = m(18);
                col.addView(row, rl);
            }
            LinearLayout.LayoutParams cp = lp(0, ViewGroup.LayoutParams.MATCH_PARENT);
            cp.weight = 1f;
            if (i % perRow != 0) cp.leftMargin = m(18);
            row.addView(cells.get(i), cp);
        }
        for (int i = cells.size() % perRow; i != 0 && i < perRow; i++) {
            View filler = new View(this);
            LinearLayout.LayoutParams fp = lp(0, ViewGroup.LayoutParams.MATCH_PARENT);
            fp.weight = 1f;
            fp.leftMargin = m(18);
            row.addView(filler, fp);
        }
        showPage(TiggoText.t("Обои"), col);
        final List<String> loadKeys = keys;
        new Thread(() -> {
            for (int i = 0; i < loadKeys.size(); i++) {
                final Bitmap b = thumbFor(loadKeys.get(i), 480, 270);
                final ImageView target = thumbs.get(i);
                runOnUiThread(() -> {
                    if (b != null) target.setImageBitmap(b);
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ results

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (req == REQ_WALL) {
            new Thread(() -> {
                try {
                    final String name = TiggoHome.importWallpaperFile(this, uri);
                    runOnUiThread(() -> {
                        prefs.edit().putString(TiggoTheme.K_WALL, "tiggo:" + name).apply();
                        toast(TiggoText.t("Обои изменены"));
                        goBack();
                    });
                } catch (Throwable t) {
                    runOnUiThread(() -> toast(TiggoText.t("Не удалось открыть изображение")));
                }
            }).start();
        } else if (req == REQ_VIDEO) {
            new Thread(() -> {
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "tiggo9_promo_user.mp4"))) {
                    byte[] buf = new byte[65536];
                    int n;
                    while (in != null && (n = in.read(buf)) > 0) out.write(buf, 0, n);
                    runOnUiThread(() -> toast(TiggoText.t("Ролик сохранён: удерживайте логотип на главном экране")));
                } catch (Throwable t) {
                    runOnUiThread(() -> toast(TiggoText.t("Не удалось сохранить видео")));
                }
            }).start();
        }
    }
}
