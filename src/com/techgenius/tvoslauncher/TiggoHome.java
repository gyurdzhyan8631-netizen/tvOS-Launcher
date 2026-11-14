package com.techgenius.tvoslauncher;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
    private static final String PREF_DOCK = "tiggo9_dock_";
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

    private static final String[] DOCK_NAMES = {"Домой", "Навигация", "TV", "Авто", "Музыка", "Файлы"};
    private static final String[][] DOCK_PACKAGES = {
            {},
            {"ru.yandex.yandexnavi", "ru.yandex.yandexmaps", "ru.dublgis.dgismobile", "com.navitel",
                    "com.waze", "com.sygic.aura", "net.osmand.plus", "net.osmand", "app.organicmaps",
                    "com.google.android.apps.maps"},
            {"ru.iptvremote.android.iptv.pro", "ru.iptvremote.android.iptv", "ar.tvplayer.tv"},
            {},
            {"ru.yandex.music", "com.spotify.music", "com.spotify.tv.android",
                    "com.google.android.apps.youtube.music"},
            {"com.google.android.apps.nbu.files", "com.alphainventor.filemanager", "com.mixplorer",
                    "com.lonelycatgames.xplore", "com.ghisler.android.TotalCommander"}
    };
    private static final String[][] DOCK_WORDS = {
            {},
            {"навигатор", "navigator", "navitel", "навител", "waze", "sygic", "osmand", "карты", "maps"},
            {"iptv", "tivimate", "televizo", "ottplay", "kion", "wink", "телевид"},
            {},
            {"музык", "music", "плеер", "player", "poweramp", "vlc"},
            {"проводник", "файл", "explorer", "files", "file manager"}
    };
    private static final String[] WALL_CAPTIONS = {
            "Фото 1", "Фото 2", "Фото 3", "Фото 4", "Фото 5",
            "Аврора", "Полночь", "Закат", "Океан", "Графит"
    };

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
    private ImageView vpnView;
    private ImageView wifiView;
    private ImageView barsView;
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
        this.k = Math.max(0.2f, Math.min(w / 1672f, h / 941f));
        this.side = Math.round(w * 0.029f);

        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        setOnLongClickListener(v -> {
            openSettingsSheet();
            return true;
        });

        LinearLayout page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(side, m(24), side, m(46));
        page.setClipChildren(false);
        page.setClipToPadding(false);
        addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        page.addView(buildHeader(), lp(ViewGroup.LayoutParams.MATCH_PARENT, m(150)));

        LinearLayout cards = new LinearLayout(host);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.TOP);
        cards.setClipChildren(false);
        cards.setClipToPadding(false);
        cards.addView(buildWeatherCard(), lp(m(426), m(246)));
        cards.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1f));
        cards.addView(buildMediaCard(), lp(m(747), m(236)));
        LinearLayout.LayoutParams cardsLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardsLp.topMargin = m(30);
        page.addView(cards, cardsLp);

        page.addView(new View(host), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.5f));

        appStrip = new LinearLayout(host);
        appStrip.setOrientation(LinearLayout.HORIZONTAL);
        appStrip.setGravity(Gravity.TOP);
        appStrip.setClipChildren(false);
        appStrip.setClipToPadding(false);
        page.addView(appStrip, lp(ViewGroup.LayoutParams.MATCH_PARENT, m(196)));

        page.addView(new View(host), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        page.addView(buildDock(), lp(ViewGroup.LayoutParams.MATCH_PARENT, m(116)));

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

    private FrameLayout buildHeader() {
        FrameLayout header = new FrameLayout(host);
        header.setClipChildren(false);

        LinearLayout logo = new LinearLayout(host);
        logo.setOrientation(LinearLayout.HORIZONTAL);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        TextView tiggo = text("TIGGO", 62, false);
        tiggo.setTypeface(Ui.font(host, "display"), 2);
        TextView nine = text("9", 66, true);
        nine.setTextColor(0xFFFF2946);
        LinearLayout.LayoutParams nineLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nineLp.leftMargin = m(6);
        logo.addView(tiggo);
        logo.addView(nine, nineLp);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.TOP);
        logoLp.topMargin = m(8);
        header.addView(logo, logoLp);

        LinearLayout clock = new LinearLayout(host);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        TextClock time = new TextClock(host);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, 80 * k);
        time.setTypeface(Ui.font(host, "regular"));
        time.setGravity(Gravity.CENTER);
        time.setIncludeFontPadding(false);
        time.setShadowLayer(m(5), 0, m(2), 0x66000000);
        dateView = text("", 26, false);
        dateView.setGravity(Gravity.CENTER);
        dateView.setTextColor(0xEFFFFFFF);
        clock.addView(time);
        LinearLayout.LayoutParams dateLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = m(4);
        clock.addView(dateView, dateLp);
        FrameLayout.LayoutParams clockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        clockLp.topMargin = m(6);
        header.addView(clock, clockLp);

        LinearLayout status = new LinearLayout(host);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER);
        status.setPadding(m(20), 0, m(20), 0);
        status.setBackground(glass(m(40), 0x553E4E91));

        vpnView = iconView(TiggoIcons.vpn(m(46), false));
        vpnView.setBackground(rippleOnly(m(32)));
        vpnView.setOnClickListener(v -> openSettings(Settings.ACTION_VPN_SETTINGS));
        pressScale(vpnView);
        status.addView(vpnView, lp(m(66), m(66)));

        wifiView = iconView(Glyphs.get("wifi", m(40), WHITE));
        wifiView.setBackground(rippleOnly(m(32)));
        wifiView.setOnClickListener(v -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        pressScale(wifiView);
        status.addView(wifiView, lp(m(66), m(66)));

        barsView = iconView(TiggoIcons.bars(m(38), 0, WHITE));
        barsView.setBackground(rippleOnly(m(32)));
        barsView.setOnClickListener(v -> openSettings(Settings.ACTION_WIRELESS_SETTINGS));
        pressScale(barsView);
        status.addView(barsView, lp(m(66), m(66)));

        View divider = new View(host);
        divider.setBackgroundColor(0x88FFFFFF);
        LinearLayout.LayoutParams divLp = lp(Math.max(1, m(2)), m(36));
        divLp.leftMargin = m(10);
        divLp.rightMargin = m(10);
        status.addView(divider, divLp);

        ImageView gear = iconView(Glyphs.get("gear", m(40), WHITE));
        gear.setBackground(rippleOnly(m(32)));
        gear.setOnClickListener(v -> host.showControlCenter());
        pressScale(gear);
        status.addView(gear, lp(m(66), m(66)));

        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, m(80), Gravity.END | Gravity.TOP);
        statusLp.topMargin = m(8);
        header.addView(status, statusLp);
        return header;
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

    /** Real Wi-Fi / VPN / mobile state in the status capsule. */
    @SuppressWarnings("deprecation")
    private void refreshStatus() {
        boolean vpn = false;
        boolean wifi = false;
        boolean cell = false;
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
                    if (internet && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) cell = true;
                }
            }
        } catch (Throwable ignored) {
            // leave everything dimmed
        }
        if (statusKnown && vpn == lastVpn && wifi == lastWifi && cell == lastCell) return;
        statusKnown = true;
        lastVpn = vpn;
        lastWifi = wifi;
        lastCell = cell;
        if (vpnView != null) vpnView.setImageBitmap(TiggoIcons.vpn(m(46), vpn));
        if (wifiView != null) {
            wifiView.setImageBitmap(Glyphs.get("wifi", m(40), WHITE));
            wifiView.setAlpha(wifi ? 1f : 0.4f);
        }
        if (barsView != null) barsView.setImageBitmap(TiggoIcons.bars(m(38), cell ? 4 : 0, WHITE));
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
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(m(32), m(18), m(30), m(18));
        card.setBackground(pressable(m(34), 0x443C4A85));
        card.setOnClickListener(v -> host.showWeather());
        card.setOnLongClickListener(v -> {
            openWeatherSheet();
            return true;
        });
        pressScale(card);

        LinearLayout top = new LinearLayout(host);
        top.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(host);
        weatherIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        top.addView(weatherIcon, lp(m(112), m(100)));
        weatherTemp = text("--°", 64, false);
        weatherTemp.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        top.addView(weatherTemp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top, lp(ViewGroup.LayoutParams.MATCH_PARENT, m(104)));

        LinearLayout placeRow = new LinearLayout(host);
        placeRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView pin = iconView(Glyphs.get("pin", m(30), WHITE));
        placeRow.addView(pin, lp(m(34), m(34)));
        weatherPlace = text("Погода", 29, true);
        weatherPlace.setSingleLine(true);
        weatherPlace.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams placeLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        placeLp.weight = 1f;
        placeLp.leftMargin = m(8);
        placeRow.addView(weatherPlace, placeLp);
        LinearLayout.LayoutParams placeRowLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        placeRowLp.topMargin = m(6);
        card.addView(placeRow, placeRowLp);

        weatherCondition = text("", 23, false);
        weatherCondition.setAlpha(0.9f);
        weatherCondition.setSingleLine(true);
        weatherCondition.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams condLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        condLp.topMargin = m(4);
        card.addView(weatherCondition, condLp);
        return card;
    }

    void refreshWeather() {
        if (weatherTemp == null) return;
        Weather.Data d = Weather.cached(host);
        if (d == null || !Weather.enabled(host)) {
            weatherTemp.setText("--°");
            weatherPlace.setText("Погода");
            weatherCondition.setText("Удерживайте — выбрать город");
            weatherIcon.setImageBitmap(Glyphs.get("cloud", m(84), WHITE));
            return;
        }
        String unit = "f".equals(Weather.units(host)) ? "°F" : "°C";
        weatherTemp.setText((d.temp > 0 ? "+" : "") + d.temp + unit);
        weatherPlace.setText(d.place == null || d.place.isEmpty() ? "Моё местоположение" : d.place);
        weatherCondition.setText(Weather.describe(host, d.code, d.day));
        weatherIcon.setImageBitmap(WeatherIcons.get(d.code, d.day, m(100)));
    }

    // ------------------------------------------------------------------ YouTube card

    private View buildMediaCard() {
        FrameLayout card = new FrameLayout(host);
        card.setBackground(pressable(m(34), 0x553C4A85));
        card.setClipToOutline(true);
        card.setOnClickListener(v -> openPreferred("youtube",
                "com.google.android.youtube.tv", "com.google.android.youtube"));
        pressScale(card);

        LinearLayout info = new LinearLayout(host);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(m(50), m(12), m(150), m(12));

        LinearLayout titleRow = new LinearLayout(host);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout logo = new FrameLayout(host);
        GradientDrawable red = new GradientDrawable();
        red.setColor(0xFFFF0000);
        red.setCornerRadius(m(24));
        logo.setBackground(red);
        ImageView logoPlay = iconView(TiggoIcons.play(m(56), WHITE));
        logo.addView(logoPlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        titleRow.addView(logo, lp(m(115), m(80)));
        TextView title = text("YouTube", 58, true);
        LinearLayout.LayoutParams titleLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = m(26);
        titleRow.addView(title, titleLp);
        info.addView(titleRow);
        TextView subtitle = text("Смотреть любимые видео", 24, false);
        subtitle.setAlpha(0.88f);
        LinearLayout.LayoutParams subLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = m(14);
        info.addView(subtitle, subLp);
        card.addView(info, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout play = new FrameLayout(host);
        play.setBackground(pressable(m(52), 0x55566090));
        play.addView(iconView(TiggoIcons.play(m(54), WHITE)), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(m(104), m(104),
                Gravity.END | Gravity.CENTER_VERTICAL);
        pp.rightMargin = m(30);
        card.addView(play, pp);
        return card;
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
        if (homeApps.isEmpty()) {
            for (String group : DEFAULT_PACKAGES) {
                for (String pkg : group.split("\\|")) {
                    MainActivity.AppEntry e = findByPackage(pkg);
                    if (e != null) {
                        if (!homeApps.contains(e)) homeApps.add(e);
                        break;
                    }
                }
            }
        }
        for (MainActivity.AppEntry e : host.apps) {
            if (homeApps.size() >= 7) break;
            if (e != null && e.pkg != null && !homeApps.contains(e)) homeApps.add(e);
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
        int count = Math.min(7, homeApps.size());
        for (int i = 0; i < count; i++) {
            View tile = buildAppTile(homeApps.get(i), i);
            appStrip.addView(tile, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            homeTiles.add(tile);
        }
        appStrip.addView(buildAllAppsTile(),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private LinearLayout newCell() {
        LinearLayout cell = new LinearLayout(host);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        return cell;
    }

    private TextView cellLabel(String label) {
        TextView t = text(label, 22, false);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private View buildAppTile(MainActivity.AppEntry entry, int index) {
        LinearLayout cell = newCell();

        FrameLayout tile = new FrameLayout(host);
        tile.setBackground(pressable(m(34), 0x554D5A91));
        tile.setClipToOutline(true);
        tile.setElevation(m(3));
        ImageView icon = new ImageView(host);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(m(10), m(10), m(10), m(10));
        if (entry.icon != null) icon.setImageDrawable(entry.icon);
        tile.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cell.addView(tile, lp(m(142), m(142)));
        LinearLayout.LayoutParams labelLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, m(36));
        labelLp.topMargin = m(10);
        cell.addView(cellLabel(entry.label), labelLp);

        tile.setOnClickListener(v -> host.launchAnimated(v, entry));
        cell.setOnClickListener(v -> host.launchAnimated(tile, entry));
        pressScale(tile);
        tile.setOnLongClickListener(v -> {
            ClipData data = ClipData.newPlainText("tiggo-app-key", entry.key);
            try {
                tile.startDragAndDrop(data, new View.DragShadowBuilder(tile), entry.key, 0);
                host.toast("Перетащите приложение на нужное место");
                return true;
            } catch (Throwable t) {
                host.toast("Перемещение недоступно на этой версии системы");
                return true;
            }
        });
        tile.setOnDragListener((v, event) -> handleHomeDrag(index, v, event));
        return cell;
    }

    private boolean handleHomeDrag(int targetIndex, View target, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof String;
            case DragEvent.ACTION_DRAG_ENTERED:
                target.animate().scaleX(1.06f).scaleY(1.06f).setDuration(100).start();
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                target.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                return true;
            case DragEvent.ACTION_DROP:
                target.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                String key = (String) event.getLocalState();
                int from = -1;
                for (int i = 0; i < homeApps.size(); i++) {
                    if (key.equals(homeApps.get(i).key)) { from = i; break; }
                }
                if (from >= 0 && targetIndex >= 0 && targetIndex < homeApps.size() && from != targetIndex) {
                    MainActivity.AppEntry moved = homeApps.remove(from);
                    homeApps.add(Math.min(targetIndex, homeApps.size()), moved);
                    saveHomeOrder();
                    buildAppStrip();
                }
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                target.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                return true;
            default:
                return true;
        }
    }

    private View buildAllAppsTile() {
        LinearLayout cell = newCell();
        FrameLayout tile = new FrameLayout(host);
        tile.setBackground(pressable(m(34), 0x554D5A91));
        tile.setClipToOutline(true);
        tile.addView(iconView(TiggoIcons.apps(m(76), WHITE)), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cell.addView(tile, lp(m(142), m(142)));
        LinearLayout.LayoutParams labelLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, m(36));
        labelLp.topMargin = m(10);
        cell.addView(cellLabel("Все приложения"), labelLp);
        tile.setOnClickListener(v -> openAllApps());
        cell.setOnClickListener(v -> openAllApps());
        pressScale(tile);
        return cell;
    }

    // ------------------------------------------------------------------ dock

    private View buildDock() {
        LinearLayout dock = new LinearLayout(host);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(m(20), m(8), m(20), m(8));
        dock.setBackground(glass(m(58), 0x553B477D));
        String[] labels = {"Домой", "Навигация", "TV", "Авто", "Музыка", "Файлы", "Добавить"};
        int ic = m(62);
        Bitmap[] icons = {
                Glyphs.get("house", ic, WHITE),
                TiggoIcons.navigation(ic, WHITE),
                TiggoIcons.tv(ic, WHITE),
                TiggoIcons.car(ic, WHITE),
                TiggoIcons.note(ic, WHITE),
                TiggoIcons.folder(ic, WHITE),
                null
        };
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean last = i == labels.length - 1;
            if (last) {
                View divider = new View(host);
                divider.setBackgroundColor(0x88FFFFFF);
                LinearLayout.LayoutParams divLp = lp(Math.max(1, m(2)), m(58));
                divLp.leftMargin = m(8);
                divLp.rightMargin = m(8);
                dock.addView(divider, divLp);
            }
            FrameLayout item = new FrameLayout(host);
            item.setBackground(rippleOnly(m(44)));
            item.setContentDescription(labels[i]);
            if (last) {
                TextView plus = text("+", 46, false);
                plus.setGravity(Gravity.CENTER);
                plus.setBackground(pressable(m(36), 0x44FFFFFF));
                item.addView(plus, new FrameLayout.LayoutParams(m(72), m(72), Gravity.CENTER));
            } else {
                item.addView(iconView(icons[i]), new FrameLayout.LayoutParams(m(76), m(68), Gravity.CENTER));
            }
            if (index == 0) {
                View active = new View(host);
                GradientDrawable accent = new GradientDrawable();
                accent.setColor(0xFF278BFF);
                accent.setCornerRadius(m(3));
                active.setBackground(accent);
                FrameLayout.LayoutParams activeLp = new FrameLayout.LayoutParams(m(132), Math.max(2, m(5)),
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                item.addView(active, activeLp);
            }
            item.setOnClickListener(v -> onDockClick(index));
            if (index >= 1 && index <= 5) {
                item.setOnLongClickListener(v -> {
                    chooseDockApp(index);
                    return true;
                });
            }
            pressScale(item);
            dock.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                    last ? 0.7f : 1f));
        }
        return dock;
    }

    private void onDockClick(int index) {
        if (index == 0) {
            closeAllApps();
        } else if (index == 6) {
            openSettingsSheet();
        } else {
            launchDockSlot(index);
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
        if (e == null && slot != 3) e = defaultDockApp(slot);
        if (e != null) {
            host.launchAnimated(this, e);
            return;
        }
        if (slot == 3) {
            host.showControlCenter();
            return;
        }
        if (slot == 5 && openFilesFallback()) return;
        host.toast(DOCK_NAMES[slot] + ": приложение не найдено, выберите его");
        chooseDockApp(slot);
    }

    private boolean openFilesFallback() {
        try {
            host.startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Throwable t) {
            return false;
        }
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
                iconBox.setBackground(glass(m(28), 0x554D5A91));
                ImageView iv = new ImageView(host);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setPadding(m(6), m(6), m(6), m(6));
                if (entry.icon != null) iv.setImageDrawable(entry.icon);
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
        allAppsLayer.setVisibility(GONE);
        allAppsLayer.removeAllViews();
        allAppsOpen = false;
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
        panel.addView(sheetRow("Ярлыки дока", "Навигатор, TV, Авто, Музыка, Файлы", v -> openDockSheet()));
        addDoneButton(panel);
    }

    private void openDockSheet() {
        LinearLayout panel = beginSheet("Ярлыки дока", false, 820);
        for (int i = 1; i <= 5; i++) {
            final int slot = i;
            MainActivity.AppEntry chosen = chosenDockApp(slot);
            String sub = chosen != null ? chosen.label
                    : (slot == 3 ? "Панель управления (по умолчанию)" : "Выбирается автоматически");
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

        final int n = MainActivity.WALL_KEYS.length;
        final ImageView[] thumbs = new ImageView[n];
        final String[] keys = new String[n];
        for (int i = 0; i < n; i++) {
            final String key = MainActivity.WALL_KEYS[i];
            keys[i] = key;
            LinearLayout item = new LinearLayout(host);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView iv = new ImageView(host);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable frame = new GradientDrawable();
            frame.setColor(0x33FFFFFF);
            frame.setCornerRadius(m(26));
            boolean selected = key.equals(host.wallStyle);
            frame.setStroke(Math.max(2, m(selected ? 5 : 2)), selected ? 0xFF278BFF : 0x66FFFFFF);
            iv.setBackground(frame);
            iv.setClipToOutline(true);
            iv.setOnClickListener(v -> {
                host.applyTiggoWallpaper(key);
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
                    host.applyTiggoWallpaper("tiggo:" + name);
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

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(ticker);
        closeAllApps();
    }
}
