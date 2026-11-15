package com.techgenius.tvoslauncher;

import android.bluetooth.BluetoothAdapter;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TIGGO 9 touch-first home screen.
 *
 * Every size is derived from the 1672x941 reference mockup and scaled to the real screen, so the
 * layout keeps the mockup's proportions on any display. Application discovery, launching, weather
 * data and wallpaper storage remain owned by MainActivity / Weather.
 */
final class TiggoHome extends FrameLayout {
    static final int REQ_WALLPAPER = 9511;

    private static final String PREF_HOME_ORDER = "tiggo9_home_order";
    private static final String PREF_DOCK = "tiggo9_dock2_";
    private static final String PREF_WALL = "tiggo9_wall";
    private static final String PREF_TILE_GLASS = "tiggo9_tile_glass";
    private static final int WHITE = 0xFFFFFFFF;

    // Each entry lists alternative packages ("tv|phone"): the first one that is installed wins.
    private static final String[] DEFAULT_PACKAGES = {
            "com.google.android.youtube.tv|com.google.android.youtube",
            "com.netflix.ninja|com.netflix.mediaclient",
            "com.amazon.amazonvideo.livingroom|com.amazon.avod.thirdpartyclient",
            "ru.kinopoisk",
            "com.android.vending",
            "com.spotify.tv.android|com.spotify.music"
    };

    // Side rail and bottom dock: 0 home, 1 all apps, 2 navigator, 3 music, 4 video (rail also: 5 settings)
    private static final String[] DOCK_NAMES = {"Домой", "Все приложения", "Навигатор", "Музыка", "Видео"};
    private static final String[][] DOCK_PACKAGES = {
            {}, {},
            {"ru.yandex.yandexnavi", "ru.yandex.yandexmaps", "ru.dublgis.dgismobile", "com.navitel",
                    "com.waze", "com.sygic.aura", "net.osmand.plus", "net.osmand", "app.organicmaps",
                    "com.google.android.apps.maps"},
            {"ru.yandex.music", "com.spotify.music", "com.spotify.tv.android",
                    "com.google.android.apps.youtube.music"},
            {"org.videolan.vlc", "com.mxtech.videoplayer.ad", "ru.kinopoisk", "com.netflix.ninja"}
    };
    private static final String[][] DOCK_WORDS = {
            {}, {},
            {"навигатор", "navigator", "navitel", "навител", "waze", "sygic", "osmand", "карты", "maps"},
            {"музык", "music", "плеер", "player", "poweramp"},
            {"видео", "video", "vlc", "кино", "плеер"}
    };
    private static final String[] WALL_CAPTIONS = {
            "TIGGO 9", "Фото 1", "Фото 2", "Фото 3", "Фото 4", "Фото 5",
            "Аврора", "Полночь", "Закат", "Океан", "Графит"
    };
    private static final String CAR_WALLPAPER = "photo:tiggo9-car.jpg";
    private static final String PREF_CARD = "tiggo9_card_app";
    private static final String PREF_ICONS = "tiggo9_icon_pack";

    private interface Pick {
        void on(MainActivity.AppEntry entry);
    }

    private final MainActivity host;
    private final float k;
    private final int side;
    private final LinearLayout appStrip;
    private final FrameLayout allAppsLayer;
    private final FrameLayout sheetLayer;
    private final List<MainActivity.AppEntry> homeApps = new ArrayList<>();
    private final List<View> homeTiles = new ArrayList<>();
    private TextView weatherTemp;
    private TextView weatherPlace;
    private TextView weatherCondition;
    private ImageView weatherIcon;
    private TextView dateView;
    private View vpnView;
    private View wifiView;
    private View btView;
    private ImageView wallView;
    private Bitmap wallBmp;
    private String wallStyle;
    private final int oy;
    private int slotCount = 6;
    private boolean lastBt;
    private boolean iconBusy;
    private boolean iconPending;
    private boolean allAppsOpen;
    private boolean sheetOpen;
    private int tickCount;
    private boolean lastVpn, lastWifi, lastCell;
    private boolean statusKnown;

    TiggoHome(MainActivity host) {
        super(host);
        this.host = host;
        int w = host.screenW;
        int h = host.screenH;
        if (w < 300 || h < 200) {
            w = host.getResources().getDisplayMetrics().widthPixels;
            h = host.getResources().getDisplayMetrics().heightPixels;
        }
        this.k = Math.max(0.2f, Math.min(w / 1074f, h / 593f));
        this.side = Math.round(w * 0.029f);
        this.oy = Math.max(0, (h - Math.round(593 * this.k)) / 2);

        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        setOnLongClickListener(v -> {
            openSettingsSheet();
            return true;
        });

        wallView = new ImageView(host);
        wallView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallView.setBackgroundColor(0xFF0B1226);
        addView(wallView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadWallpaper();

        FrameLayout layer = new FrameLayout(host);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        addView(layer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final int contentLeft = m(155);
        final int contentW = Math.max(m(500), w - contentLeft - m(29));
        slotCount = Math.max(5, Math.min(8, Math.round(contentW / (float) Math.max(1, m(148)))));

        layer.addView(buildLogo(), pos(m(190), m(34), m(127), oy + m(31)));
        layer.addView(buildStatusRow(), posEnd(ViewGroup.LayoutParams.WRAP_CONTENT, m(46), m(29), oy + m(22)));
        layer.addView(buildRail(), pos(m(89), m(430), m(23), oy + m(105)));
        layer.addView(buildClock(), pos(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                m(180), oy + m(124)));
        layer.addView(buildWeatherCard(), pos(m(234), m(102), m(452), oy + m(133)));
        appStrip = new LinearLayout(host);
        appStrip.setOrientation(LinearLayout.HORIZONTAL);
        appStrip.setGravity(Gravity.TOP);
        appStrip.setClipChildren(false);
        appStrip.setClipToPadding(false);
        layer.addView(appStrip, pos(contentW, m(130), contentLeft, oy + m(340)));
        int dockW = Math.min(m(665), contentW);
        layer.addView(buildDock(), pos(dockW, m(64), contentLeft + (contentW - dockW) / 2, oy + m(498)));

        allAppsLayer = new FrameLayout(host);
        allAppsLayer.setVisibility(GONE);
        allAppsLayer.setClickable(true);
        addView(allAppsLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        sheetLayer = new FrameLayout(host);
        sheetLayer.setVisibility(GONE);
        sheetLayer.setClickable(true);
        addView(sheetLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rebuildHomeApps();
        refreshWeather();
        updateDate();
        refreshStatus();
        scheduleIconPack();
        if (!Weather.manual(host)) requestFix();
    }

    // ------------------------------------------------------------------ metrics

    /** Mockup pixels -> real pixels. */
    private int m(float mockPx) {
        return Math.round(mockPx * k);
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
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

    /** Glass-looking text: a soft top-to-bottom fade plus a gentle shadow. */
    private void glassText(TextView tv, float mockSize) {
        float h = mockSize * k;
        tv.getPaint().setShader(new LinearGradient(0, 0, 0, h * 1.15f,
                new int[]{0xFFFFFFFF, 0xCCCFE0FF}, null, Shader.TileMode.CLAMP));
        tv.setShadowLayer(m(8), 0, m(3), 0x55000000);
    }

    private GradientDrawable glass(int radiusPx, int fill) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x55FFFFFF | (fill & 0x00FFFFFF), fill});
        d.setCornerRadius(radiusPx);
        d.setStroke(Math.max(1, m(2)), 0x66FFFFFF);
        return d;
    }

    /** Glass background that shows a touch ripple. */
    private Drawable pressable(int radiusPx, int fill) {
        return new RippleDrawable(ColorStateList.valueOf(0x66FFFFFF), glass(radiusPx, fill), rippleMask(radiusPx));
    }

    /** Transparent item that only shows a touch ripple. */
    private Drawable rippleOnly(int radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), null, rippleMask(radiusPx));
    }

    private GradientDrawable rippleMask(int radiusPx) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusPx);
        return mask;
    }

    /** Small "pressed" squeeze on touch; never consumes the event, so clicks and long-presses still work. */
    private void pressScale(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private TextView text(String value, float mockSize, boolean bold) {
        TextView t = new TextView(host);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, mockSize * k);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Ui.font(host, bold ? "semibold" : "regular"));
        t.setIncludeFontPadding(false);
        t.setShadowLayer(m(3), 0, m(1), 0x66000000);
        return t;
    }

    private ImageView iconView(Bitmap bmp) {
        ImageView iv = new ImageView(host);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setImageBitmap(bmp);
        return iv;
    }

    // ------------------------------------------------------------------ header

    private Bitmap assetBitmap(String path) {
        try (InputStream in = host.getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (Throwable t) {
            return null;
        }
    }

    private View buildLogo() {
        Bitmap logoBmp = assetBitmap("backgrounds/tiggo9_logo.png");
        if (logoBmp != null) {
            ImageView logo = new ImageView(host);
            logo.setScaleType(ImageView.ScaleType.FIT_START);
            logo.setImageBitmap(logoBmp);
            return logo;
        }
        return text("TIGGO 9", 38, true);
    }

    private View buildStatusRow() {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x00000000);
        pill.setCornerRadius(m(13));
        pill.setStroke(Math.max(1, m(2)), WHITE);
        TextView vpn = text("VPN", 15, true);
        vpn.setGravity(Gravity.CENTER);
        vpn.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), pill, rippleMask(m(13))));
        vpn.setOnClickListener(v -> openSettings(Settings.ACTION_VPN_SETTINGS));
        pressScale(vpn);
        vpnView = vpn;
        row.addView(vpn, lp(m(54), m(32)));

        ImageView wifi = iconView(Glyphs.get("wifi", m(28), WHITE));
        wifi.setBackground(rippleOnly(m(22)));
        wifi.setOnClickListener(v -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        pressScale(wifi);
        wifiView = wifi;
        LinearLayout.LayoutParams wifiLp = lp(m(46), m(44));
        wifiLp.leftMargin = m(14);
        row.addView(wifi, wifiLp);

        ImageView bt = iconView(TiggoIcons.bluetooth(m(26), WHITE));
        bt.setBackground(rippleOnly(m(22)));
        bt.setOnClickListener(v -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        pressScale(bt);
        btView = bt;
        LinearLayout.LayoutParams btLp = lp(m(40), m(44));
        btLp.leftMargin = m(8);
        row.addView(bt, btLp);

        View divider = new View(host);
        divider.setBackgroundColor(0xCCFFFFFF);
        LinearLayout.LayoutParams divLp = lp(Math.max(1, m(2)), m(26));
        divLp.leftMargin = m(14);
        divLp.rightMargin = m(14);
        row.addView(divider, divLp);

        TextClock time = new TextClock(host);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, 27 * k);
        time.setTypeface(Ui.font(host, "regular"));
        time.setIncludeFontPadding(false);
        time.setShadowLayer(m(3), 0, m(1), 0x66000000);
        row.addView(time);
        return row;
    }

    private View buildClock() {
        LinearLayout clock = new LinearLayout(host);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.START);
        TextClock time = new TextClock(host);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, 88 * k);
        time.setTypeface(Ui.font(host, "regular"));
        time.setIncludeFontPadding(false);
        glassText(time, 88);
        clock.addView(time);
        dateView = text("", 25, false);
        dateView.setTextColor(Color.WHITE);
        glassText(dateView, 25);
        LinearLayout.LayoutParams dateLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = m(6);
        clock.addView(dateView, dateLp);
        return clock;
    }

    private void openSettings(String action) {
        try {
            host.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            try {
                host.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t2) {
                host.toast("Настройки недоступны");
            }
        }
    }

    private void updateDate() {
        if (dateView == null) return;
        try {
            Locale ru = new Locale("ru");
            String s = new SimpleDateFormat("EEE, d MMM", ru).format(new Date());
            if (!s.isEmpty()) s = s.substring(0, 1).toUpperCase(ru) + s.substring(1);
            dateView.setText(s);
        } catch (Throwable ignored) {
            // keep the previous text
        }
    }

    /** Real Wi-Fi / VPN / Bluetooth state in the top status row. */
    @SuppressWarnings("deprecation")
    private void refreshStatus() {
        boolean vpn = false;
        boolean wifi = false;
        boolean bt = true;
        try {
            ConnectivityManager cm = (ConnectivityManager) host.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                    if (nc == null) continue;
                    if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpn = true;
                    boolean internet = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    if (internet && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) wifi = true;
                }
            }
        } catch (Throwable ignored) {
            // leave everything dimmed
        }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) bt = adapter.isEnabled();
        } catch (Throwable ignored) {
            // permission missing: show it as available
        }
        if (statusKnown && vpn == lastVpn && wifi == lastWifi && bt == lastBt) return;
        statusKnown = true;
        lastVpn = vpn;
        lastWifi = wifi;
        lastBt = bt;
        if (vpnView != null) vpnView.setAlpha(vpn ? 1f : 0.55f);
        if (wifiView != null) wifiView.setAlpha(wifi ? 1f : 0.45f);
        if (btView != null) btView.setAlpha(bt ? 1f : 0.45f);
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshStatus();
            if (++tickCount % 6 == 0) updateDate();
            postDelayed(this, 5000);
        }
    };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(ticker);
        updateDate();
        post(ticker);
    }

    // ------------------------------------------------------------------ weather card

    private View buildWeatherCard() {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(m(14), m(8), m(16), m(8));
        card.setBackground(pressable(m(26), 0x3D2B3F78));
        card.setOnClickListener(v -> host.showWeather());
        card.setOnLongClickListener(v -> {
            openWeatherSheet();
            return true;
        });
        pressScale(card);

        weatherIcon = new ImageView(host);
        weatherIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(weatherIcon, lp(m(76), m(76)));

        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        weatherTemp = text("--°", 38, false);
        col.addView(weatherTemp, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        weatherPlace = text("Погода", 20, false);
        weatherPlace.setSingleLine(true);
        weatherPlace.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(weatherPlace, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        weatherCondition = text("", 16, false);  // kept for the forecast panel, not shown on the card
        LinearLayout.LayoutParams colLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.weight = 1f;
        colLp.leftMargin = m(8);
        card.addView(col, colLp);
        return card;
    }

    void refreshWeather() {
        if (weatherTemp == null) return;
        Weather.Data d = Weather.cached(host);
        if (d == null || !Weather.enabled(host)) {
            weatherTemp.setText("--°");
            weatherPlace.setText("Выбрать город");
            weatherCondition.setText("");
            weatherIcon.setImageBitmap(Glyphs.get("cloud", m(56), WHITE));
            return;
        }
        String unit = "f".equals(Weather.units(host)) ? "°F" : "°C";
        weatherTemp.setText(d.temp + unit);
        weatherPlace.setText(d.place == null || d.place.isEmpty() ? "Моё местоположение" : d.place);
        weatherCondition.setText(Weather.describe(host, d.code, d.day));
        weatherIcon.setImageBitmap(WeatherIcons.get(d.code, d.day, m(76)));
    }

    // ------------------------------------------------------------------ home apps

    private void rebuildHomeApps() {
        homeApps.clear();
        String saved = host.prefs.getString(PREF_HOME_ORDER, null);
        if (saved != null && !saved.trim().isEmpty()) {
            String[] keys = saved.split("\\n");
            for (String key : keys) {
                MainActivity.AppEntry e = findByKey(key);
                if (e != null && !homeApps.contains(e)) homeApps.add(e);
            }
        }
        if (saved == null) {
            for (String group : DEFAULT_PACKAGES) {
                for (String pkg : group.split("\\|")) {
                    MainActivity.AppEntry e = findByPackage(pkg);
                    if (e != null) {
                        if (!homeApps.contains(e)) homeApps.add(e);
                        break;
                    }
                }
            }
            for (MainActivity.AppEntry e : host.apps) {
                if (homeApps.size() >= slotCount) break;
                if (e != null && e.pkg != null && !homeApps.contains(e)) homeApps.add(e);
            }
        }
        buildAppStrip();
    }

    private MainActivity.AppEntry findByKey(String key) {
        if (key == null) return null;
        for (MainActivity.AppEntry e : host.apps) if (key.equals(e.key)) return e;
        return null;
    }

    private MainActivity.AppEntry findByPackage(String pkg) {
        for (MainActivity.AppEntry e : host.apps) if (pkg.equals(e.pkg)) return e;
        return null;
    }

    /** Looks through every installed app, not only the ones on the home row. */
    private MainActivity.AppEntry findAnyByPackage(String pkg) {
        for (MainActivity.AppEntry e : host.all.values()) if (pkg.equals(e.pkg)) return e;
        return null;
    }

    private void saveHomeOrder() {
        StringBuilder b = new StringBuilder();
        for (MainActivity.AppEntry e : homeApps) {
            if (b.length() > 0) b.append('\n');
            b.append(e.key);
        }
        host.prefs.edit().putString(PREF_HOME_ORDER, b.toString()).apply();
    }

    private void buildAppStrip() {
        appStrip.removeAllViews();
        homeTiles.clear();
        int count = Math.min(slotCount, homeApps.size());
        for (int i = 0; i < count; i++) {
            View cell = buildAppTile(homeApps.get(i), i);
            appStrip.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            homeTiles.add(cell);
        }
        for (int i = count; i < slotCount; i++) {
            appStrip.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    private View buildAppTile(final MainActivity.AppEntry entry, final int index) {
        FrameLayout cell = new FrameLayout(host);
        cell.setClipChildren(false);
        boolean glassOn = host.prefs.getBoolean(PREF_TILE_GLASS, true);

        LinearLayout tile = new LinearLayout(host);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(m(6), m(10), m(6), m(10));
        tile.setBackground(glassOn ? pressable(m(26), 0x3D2B3F78) : rippleOnly(m(26)));

        ImageView icon = new ImageView(host);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable packIcon = iconOf(entry);
        if (packIcon != null) icon.setImageDrawable(packIcon);
        tile.addView(icon, lp(m(70), m(70)));

        TextView label = text(entry.label == null ? "" : entry.label, 19, true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = m(8);
        tile.addView(label, labelLp);

        cell.addView(tile, new FrameLayout.LayoutParams(m(134), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        tile.setOnClickListener(v -> host.launchAnimated(v, entry));
        tile.setOnLongClickListener(v -> {
            openHomeTileSheet(index);
            return true;
        });
        pressScale(tile);
        return cell;
    }

    // ------------------------------------------------------------------ side rail and dock

    private Bitmap navIcon(int id, int px) {
        switch (id) {
            case 0: return Glyphs.get("house", px, WHITE);
            case 1: return Glyphs.get("apps", px, WHITE);
            case 2: return TiggoIcons.car(px, WHITE);
            case 3: return TiggoIcons.note(px, WHITE);
            case 4: return TiggoIcons.video(px, WHITE);
            default: return Glyphs.get("gear", px, WHITE);
        }
    }

    private String navName(int id) {
        return id >= 0 && id < DOCK_NAMES.length ? DOCK_NAMES[id] : "Настройки";
    }

    private void wireNav(View v, final int id) {
        v.setContentDescription(navName(id));
        v.setOnClickListener(x -> onNavClick(id));
        if (id >= 2 && id <= 4) {
            v.setOnLongClickListener(x -> {
                chooseDockApp(id);
                return true;
            });
        } else if (id == 5) {
            v.setOnLongClickListener(x -> {
                openSettingsSheet();
                return true;
            });
        }
        pressScale(v);
    }

    private View buildRail() {
        LinearLayout rail = new LinearLayout(host);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(0, m(6), 0, m(6));
        rail.setBackground(glass(m(44), 0x4D1E2C5C));
        for (int id = 0; id <= 5; id++) {
            FrameLayout slot = new FrameLayout(host);
            FrameLayout button = new FrameLayout(host);
            if (id == 0) {
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(0x662B6BFF);
                ring.setStroke(Math.max(2, m(3)), 0xFF5AA9FF);
                button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x66FFFFFF), ring, rippleMask(m(31))));
            } else {
                button.setBackground(rippleOnly(m(31)));
            }
            button.addView(iconView(navIcon(id, m(36))), new FrameLayout.LayoutParams(m(40), m(40), Gravity.CENTER));
            wireNav(button, id);
            slot.addView(button, new FrameLayout.LayoutParams(m(62), m(62), Gravity.CENTER));
            rail.addView(slot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return rail;
    }

    private View buildDock() {
        LinearLayout dock = new LinearLayout(host);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(m(14), m(4), m(14), m(4));
        dock.setBackground(glass(m(32), 0x4D1E2C5C));
        for (int id = 0; id <= 4; id++) {
            FrameLayout item = new FrameLayout(host);
            item.setBackground(rippleOnly(m(26)));
            item.addView(iconView(navIcon(id, m(38))), new FrameLayout.LayoutParams(m(46), m(46), Gravity.CENTER));
            if (id == 0) {
                View active = new View(host);
                GradientDrawable accent = new GradientDrawable();
                accent.setColor(0xFF278BFF);
                accent.setCornerRadius(m(2));
                active.setBackground(accent);
                item.addView(active, new FrameLayout.LayoutParams(m(108), Math.max(2, m(3)),
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
            }
            wireNav(item, id);
            dock.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        return dock;
    }

    private void onNavClick(int id) {
        switch (id) {
            case 0:
                closeAllApps();
                break;
            case 1:
                openAllApps();
                break;
            case 5:
                host.showControlCenter();
                break;
            default:
                launchDockSlot(id);
                break;
        }
    }

    private MainActivity.AppEntry chosenDockApp(int slot) {
        String key = host.prefs.getString(PREF_DOCK + slot, null);
        if (key == null) return null;
        for (MainActivity.AppEntry e : host.all.values()) if (key.equals(e.key)) return e;
        return null;
    }

    private MainActivity.AppEntry defaultDockApp(int slot) {
        if (slot < 0 || slot >= DOCK_PACKAGES.length) return null;
        for (String pkg : DOCK_PACKAGES[slot]) {
            MainActivity.AppEntry e = findAnyByPackage(pkg);
            if (e != null) return e;
        }
        for (String word : DOCK_WORDS[slot]) {
            String w = word.toLowerCase(Locale.ROOT);
            for (MainActivity.AppEntry e : host.all.values()) {
                String label = e.label == null ? "" : e.label.toLowerCase(Locale.ROOT);
                String pkg = e.pkg == null ? "" : e.pkg.toLowerCase(Locale.ROOT);
                if (label.contains(w) || pkg.contains(w)) return e;
            }
        }
        return null;
    }

    private void launchDockSlot(int slot) {
        MainActivity.AppEntry e = chosenDockApp(slot);
        if (e == null) e = defaultDockApp(slot);
        if (e != null) {
            host.launchAnimated(this, e);
            return;
        }
        host.toast(DOCK_NAMES[slot] + ": приложение не найдено, выберите его");
        chooseDockApp(slot);
    }

    private void chooseDockApp(final int slot) {
        openAppGrid("Приложение для «" + DOCK_NAMES[slot] + "»", e -> {
            host.prefs.edit().putString(PREF_DOCK + slot, e.key).apply();
            host.toast(DOCK_NAMES[slot] + ": " + e.label);
            closeAllApps();
        }, "По умолчанию", () -> {
            host.prefs.edit().remove(PREF_DOCK + slot).apply();
            host.toast(DOCK_NAMES[slot] + ": выбор по умолчанию");
            closeAllApps();
        });
    }

    private void openByKeywords(String... words) {
        for (MainActivity.AppEntry e : host.all.values()) {
            String label = e.label == null ? "" : e.label.toLowerCase(Locale.ROOT);
            String pkg = e.pkg == null ? "" : e.pkg.toLowerCase(Locale.ROOT);
            for (String word : words) {
                String w = word.toLowerCase(Locale.ROOT);
                if (label.contains(w) || pkg.contains(w)) {
                    host.launchAnimated(this, e);
                    return;
                }
            }
        }
        host.toast("Подходящее приложение не найдено");
        openAllApps();
    }

    private void openPreferred(String keyword, String... pkgs) {
        for (String pkg : pkgs) {
            MainActivity.AppEntry e = findAnyByPackage(pkg);
            if (e != null) {
                host.launchAnimated(this, e);
                return;
            }
        }
        openByKeywords(keyword);
    }

    // ------------------------------------------------------------------ all apps / app chooser

    private void openAllApps() {
        openAppGrid("Все приложения", null, null, null);
    }

    private void openAppGrid(String headingText, Pick pick, String resetLabel, Runnable resetAction) {
        closeSheet();
        allAppsLayer.removeAllViews();
        allAppsLayer.setBackgroundColor(0xE9161C36);
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(side, m(24), side, m(20));

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(headingText, 36, true);
        head.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (resetLabel != null && resetAction != null) {
            TextView reset = text(resetLabel, 22, true);
            reset.setGravity(Gravity.CENTER);
            reset.setBackground(pressable(m(26), 0x554D5A91));
            reset.setOnClickListener(v -> resetAction.run());
            pressScale(reset);
            LinearLayout.LayoutParams rLp = lp(m(250), m(64));
            rLp.rightMargin = m(14);
            head.addView(reset, rLp);
        }
        TextView close = text("✕  Закрыть", 22, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(pressable(m(26), 0x554D5A91));
        close.setOnClickListener(v -> closeAllApps());
        pressScale(close);
        head.addView(close, lp(m(220), m(64)));
        panel.addView(head, lp(ViewGroup.LayoutParams.MATCH_PARENT, m(74)));
        if (pick == null) {
            TextView hint = text("Удерживайте приложение, чтобы добавить его на главный экран", 20, false);
            hint.setAlpha(0.7f);
            panel.addView(hint, lp(ViewGroup.LayoutParams.MATCH_PARENT, m(30)));
        }

        ScrollView scroll = new ScrollView(host);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout grid = new LinearLayout(host);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, 0, 0, m(24));
        List<MainActivity.AppEntry> entries = new ArrayList<>(host.all.values());
        int cellW = Math.max(1, m(170));
        int available = Math.max(1, host.getResources().getDisplayMetrics().widthPixels - 2 * side);
        int columns = Math.max(4, Math.min(8, available / cellW));
        for (int i = 0; i < entries.size(); i += columns) {
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < i + columns; j++) {
                if (j >= entries.size()) {
                    row.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1f));
                    continue;
                }
                final MainActivity.AppEntry entry = entries.get(j);
                LinearLayout tile = new LinearLayout(host);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(m(8), m(10), m(8), m(10));
                tile.setBackground(rippleOnly(m(26)));
                pressScale(tile);
                FrameLayout iconBox = new FrameLayout(host);
                ImageView iv = new ImageView(host);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setPadding(0, 0, 0, 0);
                Drawable gridIcon = iconOf(entry);
                if (gridIcon != null) iv.setImageDrawable(gridIcon);
                iconBox.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                tile.addView(iconBox, lp(m(104), m(104)));
                TextView name = text(entry.label, 20, false);
                name.setGravity(Gravity.CENTER);
                name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nameLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, m(56));
                nameLp.topMargin = m(6);
                tile.addView(name, nameLp);
                tile.setOnClickListener(v -> {
                    if (pick != null) {
                        pick.on(entry);
                    } else {
                        closeAllApps();
                        host.launchAnimated(v, entry);
                    }
                });
                if (pick == null) {
                    tile.setOnLongClickListener(v -> {
                        openAppActionsSheet(entry);
                        return true;
                    });
                }
                row.addView(tile, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            grid.addView(row);
        }
        scroll.addView(grid);
        LinearLayout.LayoutParams scrollLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        scrollLp.weight = 1f;
        scrollLp.topMargin = m(12);
        panel.addView(scroll, scrollLp);
        allAppsLayer.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        allAppsLayer.setVisibility(VISIBLE);
        allAppsOpen = true;
    }

    void refreshApps() {
        rebuildHomeApps();
        scheduleIconPack();
    }

    void closeAllApps() {
        closeSheet();
        allAppsLayer.setVisibility(GONE);
        allAppsLayer.removeAllViews();
        allAppsOpen = false;
    }

    boolean isAllAppsOpen() {
        return allAppsOpen || sheetOpen;
    }

    // ------------------------------------------------------------------ settings sheets

    private void closeSheet() {
        if (sheetLayer == null) return;
        try {
            InputMethodManager imm = (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && sheetOpen) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        } catch (Throwable ignored) {
            // keyboard already gone
        }
        sheetLayer.setVisibility(GONE);
        sheetLayer.removeAllViews();
        sheetOpen = false;
    }

    private LinearLayout beginSheet(String title, boolean top, int mockWidth) {
        return beginSheet(title, top, mockWidth, false);
    }

    private LinearLayout beginSheet(String title, boolean top, int mockWidth, boolean keepAllApps) {
        if (!keepAllApps) {
            allAppsLayer.setVisibility(GONE);
            allAppsLayer.removeAllViews();
            allAppsOpen = false;
        }
        sheetLayer.removeAllViews();
        sheetLayer.setBackgroundColor(0xB0101426);
        sheetLayer.setOnClickListener(v -> closeSheet());
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(m(32), m(26), m(32), m(28));
        panel.setBackground(glass(m(38), 0xEE1C2554));
        panel.setClickable(true);
        TextView heading = text(title, 36, true);
        panel.addView(heading, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int width = Math.min(m(mockWidth),
                host.getResources().getDisplayMetrics().widthPixels - 2 * side);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                top ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL) : Gravity.CENTER);
        if (top) plp.topMargin = m(30);
        sheetLayer.addView(panel, plp);
        sheetLayer.setVisibility(VISIBLE);
        sheetOpen = true;
        return panel;
    }

    private View sheetRow(String title, String sub, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(m(26), m(12), m(26), m(12));
        row.setMinimumHeight(m(84));
        row.setBackground(pressable(m(28), 0x33FFFFFF));
        row.addView(text(title, 29, true));
        if (sub != null) {
            TextView s = text(sub, 21, false);
            s.setAlpha(0.8f);
            row.addView(s);
        }
        row.setOnClickListener(click);
        pressScale(row);
        LinearLayout.LayoutParams lp = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = m(14);
        row.setLayoutParams(lp);
        return row;
    }

    private void addDoneButton(LinearLayout panel) {
        TextView done = text("Готово", 26, true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(pressable(m(28), 0x554D5A91));
        done.setOnClickListener(v -> closeSheet());
        pressScale(done);
        LinearLayout.LayoutParams lp = lp(ViewGroup.LayoutParams.MATCH_PARENT, m(72));
        lp.topMargin = m(18);
        panel.addView(done, lp);
    }

    private void openSettingsSheet() {
        LinearLayout panel = beginSheet("Настроить главный экран", false, 820);
        panel.addView(sheetRow("Обои", "Фото, градиенты или своё изображение", v -> openWallpaperSheet()));
        panel.addView(sheetRow("Погода", "Автоопределение по местоположению или свой город",
                v -> openWeatherSheet()));
        panel.addView(sheetRow("Навигатор, музыка, видео", "Какие приложения открывают кнопки панели и дока",
                v -> openDockSheet()));
        boolean glassOn = host.prefs.getBoolean(PREF_TILE_GLASS, true);
        panel.addView(sheetRow("Стеклянная подложка под иконками: " + (glassOn ? "вкл" : "выкл"),
                "Только оригинальные иконки без подложки", v -> {
                    host.prefs.edit().putBoolean(PREF_TILE_GLASS, !host.prefs.getBoolean(PREF_TILE_GLASS, true)).apply();
                    buildAppStrip();
                    openSettingsSheet();
                }));
        panel.addView(sheetRow("Иконки приложений", "Fluid или другой установленный пак", v -> openIconsSheet()));
        addDoneButton(panel);
    }

    private void openDockSheet() {
        LinearLayout panel = beginSheet("Кнопки панели и дока", false, 820);
        for (int i = 2; i <= 4; i++) {
            final int slot = i;
            MainActivity.AppEntry chosen = chosenDockApp(slot);
            String sub = chosen != null ? chosen.label : "Выбирается автоматически";
            panel.addView(sheetRow(DOCK_NAMES[slot], sub, v -> chooseDockApp(slot)));
        }
        addDoneButton(panel);
    }

    // ---- wallpaper

    private void openWallpaperSheet() {
        LinearLayout panel = beginSheet("Обои", false, 1180);
        HorizontalScrollView hs = new HorizontalScrollView(host);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout strip = new LinearLayout(host);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(0, m(6), 0, m(6));

        // first tile: choose a picture from the gallery
        LinearLayout gallery = new LinearLayout(host);
        gallery.setOrientation(LinearLayout.VERTICAL);
        gallery.setGravity(Gravity.CENTER);
        gallery.setBackground(pressable(m(26), 0x44FFFFFF));
        gallery.addView(text("+", 70, false), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView gl = text("Из галереи", 22, true);
        gl.setGravity(Gravity.CENTER);
        gallery.addView(gl);
        gallery.setOnClickListener(v -> pickFromGallery());
        pressScale(gallery);
        LinearLayout.LayoutParams gLp = lp(m(236), m(133));
        gLp.rightMargin = m(18);
        strip.addView(gallery, gLp);

        final int n = MainActivity.WALL_KEYS.length + 1;
        final ImageView[] thumbs = new ImageView[n];
        final String[] keys = new String[n];
        for (int i = 0; i < n; i++) {
            final String key = i == 0 ? CAR_WALLPAPER : MainActivity.WALL_KEYS[i - 1];
            keys[i] = key;
            LinearLayout item = new LinearLayout(host);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView iv = new ImageView(host);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable frame = new GradientDrawable();
            frame.setColor(0x33FFFFFF);
            frame.setCornerRadius(m(26));
            boolean selected = key.equals(wallStyle);
            frame.setStroke(Math.max(2, m(selected ? 5 : 2)), selected ? 0xFF278BFF : 0x66FFFFFF);
            iv.setBackground(frame);
            iv.setClipToOutline(true);
            iv.setOnClickListener(v -> {
                setOwnWallpaper(key);
                host.toast("Обои изменены");
                closeSheet();
            });
            pressScale(iv);
            thumbs[i] = iv;
            item.addView(iv, lp(m(236), m(133)));
            TextView cap = text(i < WALL_CAPTIONS.length ? WALL_CAPTIONS[i] : "", 20, false);
            cap.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams capLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, m(34));
            capLp.topMargin = m(6);
            item.addView(cap, capLp);
            LinearLayout.LayoutParams iLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            iLp.rightMargin = m(18);
            strip.addView(item, iLp);
        }
        hs.addView(strip);
        panel.addView(hs, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new Thread(() -> {
            for (int i = 0; i < n; i++) {
                final int idx = i;
                Bitmap bm = null;
                try {
                    String key = keys[i];
                    bm = key.startsWith("photo:")
                            ? Scenes.loadAssetBitmap(host, "backgrounds/" + key.substring(6), 480, 270)
                            : MainActivity.makeWallpaperBase(key);
                } catch (Throwable ignored) {
                    // leave the placeholder
                }
                final Bitmap shown = bm;
                host.runOnUiThread(() -> {
                    if (sheetOpen && shown != null) thumbs[idx].setImageBitmap(shown);
                });
            }
        }).start();
        addDoneButton(panel);
    }

    private void pickFromGallery() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            host.startActivityForResult(pick, REQ_WALLPAPER);
            return;
        } catch (Throwable ignored) {
            // try the documents UI next
        }
        try {
            Intent doc = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            doc.setType("image/*");
            doc.addCategory(Intent.CATEGORY_OPENABLE);
            host.startActivityForResult(doc, REQ_WALLPAPER);
        } catch (Throwable t) {
            host.toast("Выбор фото недоступен на этом устройстве");
        }
    }

    /** Result of the gallery picker: scale the picture, store a private copy and use it as wallpaper. */
    void onWallpaperPicked(Intent data) {
        if (data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        new Thread(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = host.getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                Bitmap bm;
                try (InputStream in = host.getContentResolver().openInputStream(uri)) {
                    bm = BitmapFactory.decodeStream(in, null, opts);
                }
                if (bm == null) throw new IllegalStateException("decode");
                File dir = host.getFilesDir();
                File[] old = dir.listFiles();
                if (old != null) {
                    for (File f : old) if (f.getName().startsWith("tiggo_wall_")) f.delete();
                }
                final String name = "tiggo_wall_" + System.currentTimeMillis() + ".jpg";
                try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
                    bm.compress(Bitmap.CompressFormat.JPEG, 92, out);
                }
                bm.recycle();
                host.runOnUiThread(() -> {
                    setOwnWallpaper("tiggo:" + name);
                    host.toast("Обои изменены");
                    closeSheet();
                });
            } catch (Throwable t) {
                host.runOnUiThread(() -> host.toast("Не удалось открыть изображение"));
            }
        }).start();
    }

    /** Own (gallery) wallpaper stored by {@link #onWallpaperPicked}; null when missing. */
    static Bitmap loadOwnWallpaper(Context c, String name) {
        try {
            if (name == null || name.contains("/") || name.contains("..")) return null;
            File f = new File(c.getFilesDir(), name);
            if (!f.exists()) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 1280 && bounds.outHeight / (sample * 2) >= 720) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap raw = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            if (raw == null) return null;
            Bitmap copy = raw.copy(Bitmap.Config.ARGB_8888, true);
            if (copy != raw) raw.recycle();
            return copy;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- weather: automatic position or a chosen city

    private boolean hasLocationPermission() {
        try {
            return host.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                    || host.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** One fresh position from the network or GPS, so the automatic city follows the car. */
    @SuppressWarnings("deprecation")
    private void requestFix() {
        try {
            if (!hasLocationPermission()) return;
            final android.location.LocationManager lm =
                    (android.location.LocationManager) host.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;
            String provider = null;
            if (lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                provider = android.location.LocationManager.NETWORK_PROVIDER;
            } else if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                provider = android.location.LocationManager.GPS_PROVIDER;
            }
            if (provider == null) return;
            lm.requestSingleUpdate(provider, new android.location.LocationListener() {
                @Override public void onLocationChanged(android.location.Location l) {
                    Weather.saveFix(host, l.getLatitude(), l.getLongitude());
                    if (!Weather.manual(host)) {
                        Weather.changed(host);
                        Weather.refresh(host, true, TiggoHome.this::refreshWeather);
                    }
                }

                public void onStatusChanged(String p, int s, android.os.Bundle b) {
                }

                public void onProviderEnabled(String p) {
                }

                public void onProviderDisabled(String p) {
                }
            }, android.os.Looper.getMainLooper());
        } catch (Throwable ignored) {
            // no position available right now
        }
    }

    void onLocationPermission(boolean granted) {
        if (!granted) {
            host.toast("Без доступа к местоположению город определяется по интернету");
            return;
        }
        requestFix();
        Weather.changed(host);
        Weather.refresh(host, true, this::refreshWeather);
    }

    private void openWeatherSheet() {
        LinearLayout panel = beginSheet("Погода", true, 980);
        String now = Weather.manual(host) ? "Город: " + Weather.place(host) : "Автоопределение";
        TextView current = text(now, 22, false);
        current.setAlpha(0.8f);
        panel.addView(current);

        panel.addView(sheetRow("Определять автоматически",
                "По местоположению автомобиля (GPS и сеть)", v -> {
                    Weather.setPlace(host, null);
                    if (hasLocationPermission()) {
                        requestFix();
                    } else {
                        host.requestPermissions(new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION}, Perms.REQ_LOCATION);
                    }
                    Weather.refresh(host, true, this::refreshWeather);
                    host.toast("Погода: автоопределение города");
                    closeSheet();
                }));

        LinearLayout searchRow = new LinearLayout(host);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        final EditText input = new EditText(host);
        input.setHint("Введите город");
        input.setHintTextColor(0x99FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28 * k);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(m(24), m(10), m(24), m(10));
        input.setBackground(glass(m(26), 0x33FFFFFF));
        searchRow.addView(input, new LinearLayout.LayoutParams(0, m(76), 1f));
        TextView find = text("Найти", 26, true);
        find.setGravity(Gravity.CENTER);
        find.setBackground(pressable(m(26), 0x554D5A91));
        pressScale(find);
        LinearLayout.LayoutParams findLp = lp(m(170), m(76));
        findLp.leftMargin = m(14);
        searchRow.addView(find, findLp);
        LinearLayout.LayoutParams srLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srLp.topMargin = m(16);
        panel.addView(searchRow, srLp);

        final LinearLayout results = new LinearLayout(host);
        results.setOrientation(LinearLayout.VERTICAL);
        panel.addView(results, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Runnable search = () -> {
            final String q = input.getText().toString().trim();
            if (q.isEmpty()) return;
            results.removeAllViews();
            TextView wait = text("Поиск…", 22, false);
            wait.setPadding(0, m(14), 0, 0);
            results.addView(wait);
            new Thread(() -> {
                List<Weather.Place> found;
                try {
                    found = Weather.search(q);
                } catch (Throwable t) {
                    host.runOnUiThread(() -> {
                        results.removeAllViews();
                        host.toast("Нет сети или город не найден");
                    });
                    return;
                }
                final List<Weather.Place> shown = found;
                host.runOnUiThread(() -> {
                    results.removeAllViews();
                    if (shown.isEmpty()) {
                        TextView none = text("Ничего не найдено", 22, false);
                        none.setPadding(0, m(14), 0, 0);
                        results.addView(none);
                        return;
                    }
                    for (Weather.Place pl : shown) {
                        final Weather.Place chosen = pl;
                        String label = pl.name + (pl.detail == null || pl.detail.isEmpty() ? "" : ", " + pl.detail);
                        results.addView(sheetRow(label, null, v -> {
                            Weather.setPlace(host, chosen);
                            Weather.refresh(host, true, this::refreshWeather);
                            host.toast("Город: " + chosen.name);
                            closeSheet();
                        }));
                    }
                });
            }).start();
        };
        find.setOnClickListener(v -> search.run());
        input.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search.run();
                return true;
            }
            return false;
        });
        addDoneButton(panel);
    }

    // ------------------------------------------------------------------ wallpaper (kept by the home itself)

    private void loadWallpaper() {
        String style = host.prefs.getString(PREF_WALL, null);
        if (style == null) style = host.prefs.getString("wallpaper", CAR_WALLPAPER);
        if (style == null || style.isEmpty()) style = CAR_WALLPAPER;
        wallStyle = style;
        showWallpaperAsync(style);
    }

    private void showWallpaperAsync(final String style) {
        new Thread(() -> {
            final Bitmap b = decodeWallpaper(style);
            host.runOnUiThread(() -> {
                if (b != null) {
                    wallBmp = b;
                    wallView.setImageBitmap(b);
                }
            });
        }).start();
    }

    private Bitmap decodeWallpaper(String style) {
        try {
            Bitmap b;
            if (style.startsWith("photo:")) {
                b = Scenes.loadAssetBitmap(host, "backgrounds/" + style.substring(6), 1920, 1080);
            } else if (style.startsWith("tiggo:")) {
                b = loadOwnWallpaper(host, style.substring(6));
            } else {
                b = MainActivity.makeWallpaperBase(style);
            }
            if (b == null) b = Scenes.loadAssetBitmap(host, "backgrounds/tiggo9-car.jpg", 1920, 1080);
            if (b == null) return null;
            Bitmap m = b.isMutable() ? b : b.copy(Bitmap.Config.ARGB_8888, true);
            if (m != b) b.recycle();
            return MainActivity.bakeShade(m);
        } catch (Throwable t) {
            return null;
        }
    }

    private void setOwnWallpaper(String style) {
        host.prefs.edit().putString(PREF_WALL, style).apply();
        wallStyle = style;
        showWallpaperAsync(style);
        host.applyTiggoWallpaper(style);  // keeps the blurred glass behind other panels in sync
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && (wallBmp == null || wallBmp.isRecycled()) && wallView != null) {
            loadWallpaper();
        }
    }

    // ------------------------------------------------------------------ app icons

    private Drawable iconOf(MainActivity.AppEntry e) {
        Drawable d = null;
        try {
            d = IconPacks.get(host, e);
        } catch (Throwable ignored) {
            // use the app's own icon
        }
        return d != null ? d : e.icon;
    }

    /** Loads the chosen icon pack in the background, then redraws the icons. */
    private void scheduleIconPack() {
        if (iconBusy) {
            iconPending = true;
            return;
        }
        iconBusy = true;
        final String saved = host.prefs.getString(PREF_ICONS, IconPacks.AUTO);
        final List<MainActivity.AppEntry> snapshot = new ArrayList<>(host.all.values());
        new Thread(() -> {
            try {
                String pack = IconPacks.resolve(host, saved);
                IconPacks.load(host, pack);
                if (!pack.isEmpty()) {
                    for (MainActivity.AppEntry e : snapshot) IconPacks.get(host, e);
                }
            } catch (Throwable ignored) {
                // keep the system icons
            }
            host.runOnUiThread(() -> {
                iconBusy = false;
                rebuildHomeApps();
                if (iconPending) {
                    iconPending = false;
                    scheduleIconPack();
                }
            });
        }).start();
    }

    private void setIconPack(String saved) {
        host.prefs.edit().putString(PREF_ICONS, saved).apply();
        closeSheet();
        host.toast("Иконки применяются…");
        scheduleIconPack();
    }

    private void openIconsSheet() {
        LinearLayout panel = beginSheet("Иконки приложений", false, 820);
        String saved = host.prefs.getString(PREF_ICONS, IconPacks.AUTO);
        boolean fluid = IconPacks.isInstalled(host, IconPacks.FLUID);
        panel.addView(sheetRow((IconPacks.AUTO.equals(saved) ? "✓  " : "") + "Автоматически",
                fluid ? "Будет использован пак Fluid" : "Пак Fluid не установлен: установите его APK",
                v -> setIconPack(IconPacks.AUTO)));
        panel.addView(sheetRow((IconPacks.NONE.equals(saved) ? "✓  " : "") + "Системные иконки", null,
                v -> setIconPack(IconPacks.NONE)));
        for (IconPacks.Pack p : IconPacks.installed(host)) {
            final String pkg = p.pkg;
            panel.addView(sheetRow((pkg.equals(saved) ? "✓  " : "") + p.label, pkg, v -> setIconPack(pkg)));
        }
        addDoneButton(panel);
    }

    // ------------------------------------------------------------------ moving apps to / on the home row

    private void openHomeTileSheet(final int index) {
        if (index < 0 || index >= homeApps.size()) return;
        final MainActivity.AppEntry entry = homeApps.get(index);
        LinearLayout panel = beginSheet(entry.label == null ? "Приложение" : entry.label, false, 760, false);
        panel.addView(sheetRow("Заменить другим приложением", null,
                v -> openAppGrid("Заменить «" + entry.label + "»", e -> {
                    int existing = homeApps.indexOf(e);
                    if (existing >= 0 && existing != index) {
                        Collections.swap(homeApps, existing, index);
                    } else {
                        homeApps.set(index, e);
                    }
                    saveHomeOrder();
                    buildAppStrip();
                    closeAllApps();
                }, null, null)));
        if (index > 0) {
            panel.addView(sheetRow("Сдвинуть влево", null, v -> {
                Collections.swap(homeApps, index, index - 1);
                saveHomeOrder();
                buildAppStrip();
                closeSheet();
            }));
        }
        if (index < homeApps.size() - 1) {
            panel.addView(sheetRow("Сдвинуть вправо", null, v -> {
                Collections.swap(homeApps, index, index + 1);
                saveHomeOrder();
                buildAppStrip();
                closeSheet();
            }));
        }
        panel.addView(sheetRow("Убрать с главного экрана", null, v -> {
            homeApps.remove(index);
            saveHomeOrder();
            buildAppStrip();
            closeSheet();
        }));
        addDoneButton(panel);
    }

    private void openAppActionsSheet(final MainActivity.AppEntry entry) {
        final boolean onHome = homeApps.contains(entry);
        LinearLayout panel = beginSheet(entry.label == null ? "Приложение" : entry.label, false, 760, true);
        panel.addView(sheetRow("Открыть", null, v -> {
            closeAllApps();
            host.launchAnimated(this, entry);
        }));
        if (!onHome) {
            panel.addView(sheetRow("Добавить на главный экран",
                    homeApps.size() >= slotCount ? "Выберите, какое приложение заменить" : null, v -> {
                        if (homeApps.size() < slotCount) {
                            homeApps.add(entry);
                            saveHomeOrder();
                            buildAppStrip();
                            host.toast(entry.label + ": добавлено на главный экран");
                            closeAllApps();
                        } else {
                            openReplaceSlotSheet(entry);
                        }
                    }));
        } else {
            panel.addView(sheetRow("Убрать с главного экрана", null, v -> {
                homeApps.remove(entry);
                saveHomeOrder();
                buildAppStrip();
                closeAllApps();
            }));
        }
        addDoneButton(panel);
    }

    private void openReplaceSlotSheet(final MainActivity.AppEntry entry) {
        LinearLayout panel = beginSheet("Какое приложение заменить?", false, 760, true);
        for (int i = 0; i < homeApps.size(); i++) {
            final int slot = i;
            MainActivity.AppEntry old = homeApps.get(i);
            panel.addView(sheetRow(old.label == null ? "Приложение" : old.label, null, v -> {
                homeApps.set(slot, entry);
                saveHomeOrder();
                buildAppStrip();
                host.toast(entry.label + ": добавлено на главный экран");
                closeAllApps();
            }));
        }
        addDoneButton(panel);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(ticker);
        closeAllApps();
    }
}
