package com.techgenius.tvoslauncher;

import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.provider.Settings;
import android.view.MotionEvent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TIGGO 9 touch-first home screen.
 *
 * This is an additive home presentation layer: application discovery, launching, weather,
 * settings, wallpaper storage and the existing TV remote navigation remain owned by MainActivity.
 */
final class TiggoHome extends FrameLayout {
    private static final String PREF_HOME_ORDER = "tiggo9_home_order";
    // Each entry lists alternative packages ("tv|phone"): the first one that is installed wins.
    private static final String[] DEFAULT_PACKAGES = {
            "com.google.android.youtube.tv|com.google.android.youtube",
            "com.netflix.ninja|com.netflix.mediaclient",
            "com.amazon.amazonvideo.livingroom|com.amazon.avod.thirdpartyclient",
            "ru.kinopoisk",
            "com.android.vending",
            "com.spotify.tv.android|com.spotify.music"
    };

    private final MainActivity host;
    private final LinearLayout appStrip;
    private final FrameLayout allAppsLayer;
    private final List<MainActivity.AppEntry> homeApps = new ArrayList<>();
    private final List<View> homeTiles = new ArrayList<>();
    private TextView weatherTemp;
    private TextView weatherPlace;
    private TextView weatherCondition;
    private ImageView weatherIcon;
    private boolean allAppsOpen;

    TiggoHome(MainActivity host) {
        super(host);
        this.host = host;
        setFocusable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);

        LinearLayout page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(host.dp(24), host.dp(12), host.dp(24), host.dp(12));
        page.setClipChildren(false);
        page.setClipToPadding(false);
        addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        page.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.19f));

        LinearLayout cards = new LinearLayout(host);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER_VERTICAL);
        cards.setClipChildren(false);
        cards.setClipToPadding(false);
        cards.addView(buildWeatherCard(), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.36f));
        LinearLayout.LayoutParams mediaLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.64f);
        mediaLp.leftMargin = host.dp(22);
        cards.addView(buildMediaCard(), mediaLp);
        LinearLayout.LayoutParams cardsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.34f);
        cardsLp.topMargin = host.dp(4);
        page.addView(cards, cardsLp);

        appStrip = new LinearLayout(host);
        appStrip.setOrientation(LinearLayout.HORIZONTAL);
        appStrip.setGravity(Gravity.CENTER_VERTICAL);
        appStrip.setClipChildren(false);
        appStrip.setClipToPadding(false);
        LinearLayout.LayoutParams appsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.28f);
        appsLp.topMargin = host.dp(10);
        appsLp.bottomMargin = host.dp(8);
        page.addView(appStrip, appsLp);

        page.addView(buildDock(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.19f));

        allAppsLayer = new FrameLayout(host);
        allAppsLayer.setVisibility(GONE);
        allAppsLayer.setClickable(true);
        addView(allAppsLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rebuildHomeApps();
        refreshWeather();
    }

    private int dp(float value) { return host.dp(value); }

    private GradientDrawable glass(float radiusDp, int fill) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x55FFFFFF | (fill & 0x00FFFFFF), fill});
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(Math.max(1, dp(1)), 0x66FFFFFF);
        return d;
    }

    /** Glass background that shows a touch ripple. */
    private Drawable pressable(float radiusDp, int fill) {
        return new RippleDrawable(ColorStateList.valueOf(0x66FFFFFF), glass(radiusDp, fill), rippleMask(radiusDp));
    }

    /** Transparent item that only shows a touch ripple. */
    private Drawable rippleOnly(float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), null, rippleMask(radiusDp));
    }

    private GradientDrawable rippleMask(float radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radiusDp));
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

    /** White "navigation" arrow for the dock. */
    private Bitmap navArrow(int sizeDp) {
        int s = dp(sizeDp);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(s * 0.07f);
        Path path = new Path();
        path.moveTo(s * 0.84f, s * 0.16f);
        path.lineTo(s * 0.18f, s * 0.46f);
        path.lineTo(s * 0.50f, s * 0.54f);
        path.lineTo(s * 0.58f, s * 0.84f);
        path.close();
        c.drawPath(path, paint);
        return b;
    }

    private TextView text(String value, float size, boolean bold) {
        TextView t = new TextView(host);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Ui.font(host, bold ? "semibold" : "regular"));
        t.setShadowLayer(dp(3), 0, dp(1), 0x66000000);
        return t;
    }

    private FrameLayout buildHeader() {
        FrameLayout header = new FrameLayout(host);
        header.setClipChildren(false);

        LinearLayout logo = new LinearLayout(host);
        logo.setOrientation(LinearLayout.HORIZONTAL);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        TextView tiggo = text("TIGGO", 29, false);
        tiggo.setTypeface(Ui.font(host, "display"), 2);
        TextView nine = text("9", 31, true);
        nine.setTextColor(0xFFFF2946);
        LinearLayout.LayoutParams nineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nineLp.leftMargin = dp(4);
        logo.addView(tiggo);
        logo.addView(nine, nineLp);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL);
        header.addView(logo, logoLp);

        LinearLayout clock = new LinearLayout(host);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER);
        TextClock time = new TextClock(host);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(43);
        time.setTypeface(Ui.font(host, "regular"));
        time.setGravity(Gravity.CENTER);
        time.setShadowLayer(dp(5), 0, dp(2), 0x66000000);
        TextClock date = new TextClock(host);
        date.setFormat24Hour("EEE, d MMM");
        date.setFormat12Hour("EEE, d MMM");
        date.setTextColor(0xEFFFFFFF);
        date.setTextSize(14);
        date.setGravity(Gravity.CENTER);
        date.setTypeface(Ui.font(host, "medium"));
        clock.addView(time);
        clock.addView(date);
        FrameLayout.LayoutParams clockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        header.addView(clock, clockLp);

        LinearLayout status = new LinearLayout(host);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(14), dp(8), dp(14), dp(8));
        status.setBackground(glass(28, 0x553E4E91));
        TextView vpn = text("VPN", 12, true);
        vpn.setGravity(Gravity.CENTER);
        vpn.setTextColor(0xFFBFE7FF);
        vpn.setBackground(rippleOnly(22));
        vpn.setOnClickListener(v -> openSettings(Settings.ACTION_VPN_SETTINGS));
        pressScale(vpn);
        status.addView(vpn, new LinearLayout.LayoutParams(dp(52), dp(48)));
        addStatusGlyph(status, "wifi", 22, Settings.ACTION_WIFI_SETTINGS);
        addStatusGlyph(status, "antenna", 22, Settings.ACTION_WIRELESS_SETTINGS);
        View divider = new View(host);
        divider.setBackgroundColor(0x88FFFFFF);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(1), dp(26));
        divLp.leftMargin = dp(10);
        divLp.rightMargin = dp(10);
        status.addView(divider, divLp);
        ImageView gear = glyph("gear", 23);
        gear.setBackground(rippleOnly(24));
        gear.setOnClickListener(v -> host.showControlCenter());
        pressScale(gear);
        status.addView(gear, new LinearLayout.LayoutParams(dp(52), dp(48)));
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(status, statusLp);
        return header;
    }

    private void addStatusGlyph(LinearLayout parent, String name, int size, String settingsAction) {
        ImageView icon = glyph(name, size);
        icon.setBackground(rippleOnly(24));
        icon.setOnClickListener(v -> openSettings(settingsAction));
        pressScale(icon);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(48));
        lp.leftMargin = dp(4);
        parent.addView(icon, lp);
    }

    private ImageView glyph(String name, int size) {
        ImageView iv = new ImageView(host);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setImageBitmap(Glyphs.get(name, dp(size), Color.WHITE));
        return iv;
    }

    private View buildWeatherCard() {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(22), dp(12), dp(18), dp(12));
        card.setBackground(pressable(28, 0x443C4A85));
        card.setOnClickListener(v -> host.showWeather());
        pressScale(card);

        LinearLayout top = new LinearLayout(host);
        top.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(host);
        weatherIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        top.addView(weatherIcon, new LinearLayout.LayoutParams(dp(72), dp(72)));
        weatherTemp = text("--°", 35, false);
        LinearLayout.LayoutParams tempLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tempLp.leftMargin = dp(14);
        top.addView(weatherTemp, tempLp);
        card.addView(top);

        weatherPlace = text("Погода", 19, true);
        card.addView(weatherPlace);
        weatherCondition = text("Нажмите, чтобы открыть прогноз", 14, false);
        weatherCondition.setAlpha(0.9f);
        card.addView(weatherCondition);
        return card;
    }

    void refreshWeather() {
        if (weatherTemp == null) return;
        Weather.Data d = Weather.cached(host);
        if (d == null || !Weather.enabled(host)) {
            weatherTemp.setText("--°");
            weatherPlace.setText("Погода");
            weatherCondition.setText("Откройте прогноз");
            weatherIcon.setImageBitmap(Glyphs.get("cloud", dp(58), Color.WHITE));
            return;
        }
        weatherTemp.setText(d.temp + "°");
        weatherPlace.setText(d.place == null || d.place.isEmpty() ? "Моё местоположение" : d.place);
        weatherCondition.setText(Weather.describe(host, d.code, d.day));
        weatherIcon.setImageBitmap(WeatherIcons.get(d.code, d.day, dp(62)));
    }

    private View buildMediaCard() {
        FrameLayout card = new FrameLayout(host);
        card.setBackground(pressable(28, 0x553C4A85));
        card.setClipToOutline(true);
        card.setOnClickListener(v -> openPreferred("youtube",
                "com.google.android.youtube.tv", "com.google.android.youtube"));
        pressScale(card);
        LinearLayout info = new LinearLayout(host);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(28), dp(12), dp(70), dp(12));
        LinearLayout titleRow = new LinearLayout(host);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView logoPlay = text("▶", 22, true);
        logoPlay.setGravity(Gravity.CENTER);
        GradientDrawable red = new GradientDrawable();
        red.setColor(0xFFFF0000);
        red.setCornerRadius(dp(14));
        logoPlay.setBackground(red);
        titleRow.addView(logoPlay, new LinearLayout.LayoutParams(dp(64), dp(46)));
        TextView title = text("YouTube", 30, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = dp(14);
        titleRow.addView(title, titleLp);
        TextView subtitle = text("Смотреть любимые видео", 16, false);
        subtitle.setAlpha(0.85f);
        info.addView(titleRow);
        info.addView(subtitle);
        card.addView(info, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView play = text("▶", 25, true);
        play.setGravity(Gravity.CENTER);
        play.setBackground(pressable(50, 0x443C4A85));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.END | Gravity.CENTER_VERTICAL);
        pp.rightMargin = dp(24);
        card.addView(play, pp);
        return card;
    }

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
            MainActivity.AppEntry entry = homeApps.get(i);
            View tile = buildAppTile(entry, i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.leftMargin = dp(14);
            appStrip.addView(tile, lp);
            homeTiles.add(tile);
        }
        View all = buildAllAppsTile();
        LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (count > 0) allLp.leftMargin = dp(14);
        appStrip.addView(all, allLp);
    }

    private View buildAppTile(MainActivity.AppEntry entry, int index) {
        LinearLayout cell = new LinearLayout(host);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);

        FrameLayout tile = new FrameLayout(host);
        tile.setBackground(pressable(24, 0x554D5A91));
        tile.setClipToOutline(true);
        tile.setElevation(dp(2));
        ImageView icon = new ImageView(host);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (entry.icon != null) icon.setImageDrawable(entry.icon);
        tile.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cell.addView(tile, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView label = text(entry.label, 14, false);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26));
        cell.addView(label, labelLp);

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
        LinearLayout cell = new LinearLayout(host);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        FrameLayout tile = new FrameLayout(host);
        tile.setBackground(pressable(24, 0x554D5A91));
        tile.setClipToOutline(true);
        ImageView icon = glyph("apps", 50);
        tile.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cell.addView(tile, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView label = text("Все приложения", 14, false);
        label.setGravity(Gravity.CENTER);
        cell.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));
        tile.setOnClickListener(v -> openAllApps());
        cell.setOnClickListener(v -> openAllApps());
        pressScale(tile);
        return cell;
    }

    private View buildDock() {
        LinearLayout dock = new LinearLayout(host);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(12), dp(6), dp(12), dp(6));
        dock.setBackground(glass(42, 0x553B477D));
        String[] labels = {"Домой", "Навигация", "TV", "Авто", "Музыка", "Файлы", "Добавить"};
        String[] icons = {"house", "nav", "present", "car", "audio", "folder", "plus"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean last = i == labels.length - 1;
            if (last) {
                View divider = new View(host);
                divider.setBackgroundColor(0x88FFFFFF);
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(1), dp(40));
                divLp.leftMargin = dp(6);
                divLp.rightMargin = dp(6);
                dock.addView(divider, divLp);
            }
            LinearLayout item = new LinearLayout(host);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setBackground(rippleOnly(32));
            item.setContentDescription(labels[i]);
            View icon;
            int iconW = dp(56);
            int iconH = dp(48);
            if ("nav".equals(icons[i])) {
                ImageView arrow = new ImageView(host);
                arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                arrow.setImageBitmap(navArrow(30));
                icon = arrow;
            } else if ("car".equals(icons[i])) {
                TextView symbol = text("🚘", 27, false);
                symbol.setGravity(Gravity.CENTER);
                icon = symbol;
            } else if ("folder".equals(icons[i])) {
                TextView symbol = text("▰", 29, true);
                symbol.setGravity(Gravity.CENTER);
                icon = symbol;
            } else if ("plus".equals(icons[i])) {
                TextView symbol = text("+", 30, false);
                symbol.setGravity(Gravity.CENTER);
                symbol.setBackground(glass(50, 0x44FFFFFF));
                icon = symbol;
                iconW = dp(56);
                iconH = dp(56);
            } else {
                icon = glyph(icons[i], 29);
            }
            item.addView(icon, new LinearLayout.LayoutParams(iconW, iconH));
            if (index == 0) {
                View active = new View(host);
                GradientDrawable accent = new GradientDrawable();
                accent.setColor(0xFF278BFF);
                accent.setCornerRadius(dp(3));
                active.setBackground(accent);
                LinearLayout.LayoutParams activeLp = new LinearLayout.LayoutParams(dp(54), dp(3));
                activeLp.topMargin = dp(2);
                item.addView(active, activeLp);
            }
            item.setOnClickListener(v -> onDockClick(index));
            pressScale(item);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, last ? 0.7f : 1f);
            dock.addView(item, lp);
        }
        return dock;
    }

    private void onDockClick(int index) {
        switch (index) {
            case 0:
                closeAllApps();
                break;
            case 1:
                openByKeywords("navigation", "maps", "навиг", "яндекс");
                break;
            case 2:
                openByKeywords("tv", "iptv", "телевид");
                break;
            case 3:
                host.showControlCenter();
                break;
            case 4:
                openByKeywords("music", "spotify", "музык");
                break;
            case 5:
                openByKeywords("file", "проводник", "файлы");
                break;
            case 6:
                openAllApps();
                break;
        }
    }

    private void openByKeywords(String... words) {
        for (MainActivity.AppEntry e : host.all.values()) {
            String label = e.label == null ? "" : e.label.toLowerCase(java.util.Locale.ROOT);
            String pkg = e.pkg == null ? "" : e.pkg.toLowerCase(java.util.Locale.ROOT);
            for (String word : words) {
                String w = word.toLowerCase(java.util.Locale.ROOT);
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
            MainActivity.AppEntry e = findByPackage(pkg);
            if (e != null) {
                host.launchAnimated(this, e);
                return;
            }
        }
        openByKeywords(keyword);
    }

    private void openAllApps() {
        allAppsLayer.removeAllViews();
        allAppsLayer.setBackgroundColor(0xE9161C36);
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(18), dp(24), dp(18));
        TextView heading = text("Все приложения", 28, true);
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        TextView close = text("✕  Закрыть", 16, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(pressable(20, 0x554D5A91));
        close.setOnClickListener(v -> closeAllApps());
        pressScale(close);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(170), dp(56));
        closeLp.gravity = Gravity.END;
        panel.addView(close, closeLp);

        ScrollView scroll = new ScrollView(host);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout grid = new LinearLayout(host);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, 0, 0, dp(24));
        List<MainActivity.AppEntry> entries = new ArrayList<>(host.all.values());
        int columns = 6;
        for (int i = 0; i < entries.size(); i += columns) {
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(entries.size(), i + columns); j++) {
                MainActivity.AppEntry entry = entries.get(j);
                LinearLayout tile = new LinearLayout(host);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(8), dp(8), dp(8), dp(8));
                tile.setBackground(rippleOnly(22));
                pressScale(tile);
                FrameLayout iconBox = new FrameLayout(host);
                iconBox.setBackground(glass(20, 0x554D5A91));
                ImageView iv = new ImageView(host);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (entry.icon != null) iv.setImageDrawable(entry.icon);
                iconBox.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                tile.addView(iconBox, new LinearLayout.LayoutParams(dp(88), dp(88)));
                TextView name = text(entry.label, 13, false);
                name.setGravity(Gravity.CENTER);
                name.setMaxLines(2);
                tile.addView(name, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
                tile.setOnClickListener(v -> {
                    closeAllApps();
                    host.launchAnimated(v, entry);
                });
                row.addView(tile, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            grid.addView(row);
        }
        scroll.addView(grid);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = dp(12);
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
        allAppsLayer.setVisibility(GONE);
        allAppsLayer.removeAllViews();
        allAppsOpen = false;
    }

    boolean isAllAppsOpen() { return allAppsOpen; }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        closeAllApps();
    }
}
