package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Car home screen (touch only): TIGGO 9 logo, clock, weather card, favourite-app widget,
 * a row of apps and a bottom dock. It sits on top of the original launcher screen, which stays
 * underneath untouched. Everything is operated with a finger: tap to open, tap again to close.
 */
final class CarHome extends FrameLayout {
    private static final String[] YOUTUBE = {
            "com.google.android.youtube", "com.google.android.youtube.tv", "com.google.android.youtube.googletv"};
    private static final String[] NETFLIX = {"com.netflix.mediaclient", "com.netflix.ninja"};
    private static final String[] PRIME = {
            "com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom"};
    private static final String[] KINOPOISK = {"ru.kinopoisk", "ru.kinopoisk.tv"};
    private static final String[] PLAY = {"com.android.vending"};
    private static final String[] MUSIC = {
            "ru.yandex.music", "com.google.android.apps.youtube.music", "com.spotify.music",
            "com.aimp.player", "com.maxmpz.audioplayer"};
    private static final String[] NAVI = {
            "ru.yandex.yandexnavi", "ru.yandex.yandexmaps", "ru.dublgis.dgismobile",
            "com.google.android.apps.maps", "com.waze", "ru.yandex.yandexnavi.free"};
    private static final String[] FILES = {
            "com.google.android.documentsui", "com.android.documentsui", "com.mi.android.globalFileexplorer",
            "com.android.fileexplorer", "com.estrongs.android.pop", "com.mixplorer", "pl.solidexplorer2"};

    private static final int RED = 0xFFE4202D;

    private static final class AppItem {
        String pkg;
        String label;
        Drawable icon;
        Intent intent;
    }

    private final Activity act;
    private final PackageManager pm;
    private final int sw;
    private final int sh;
    private final float u;
    private final int margin;
    private final List<AppItem> allApps = new ArrayList<>();

    private TextView tempView;
    private TextView cityView;
    private TextView descView;
    private LinearLayout appRow;
    private FrameLayout appWidget;
    private ImageView appWidgetIcon;
    private TextView appWidgetTitle;
    private TextView appWidgetSub;
    private View overlay;

    CarHome(Activity a, int w, int h) {
        super(a);
        act = a;
        pm = a.getPackageManager();
        sw = w;
        sh = h;
        u = h / 1080f;
        margin = px(48);
        setClickable(true);
        setBackground(background());
        buildLogo();
        buildClock();
        buildStatus();
        buildWeatherCard();
        buildAppWidget();
        buildAppRow();
        buildDock();
        updateWeather();
        loadAppsAsync();
    }

    // ------------------------------------------------------------ helpers

    private int px(float v) {
        return Math.round(v * u);
    }

    private Drawable background() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1B2A66, 0xFF4A4A93, 0xFFA2688F, 0xFF3A2B5C, 0xFF12152E});
        return g;
    }

    private GradientDrawable glass(float radiusPx) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x52FFFFFF, 0x2BFFFFFF});
        g.setCornerRadius(radiusPx);
        g.setStroke(Math.max(1, px(2)), 0x66FFFFFF);
        return g;
    }

    private TextView text(String s, float sizePx, int color, Typeface tf) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void pressable(View v, Runnable click) {
        v.setClickable(true);
        v.setOnClickListener(x -> click.run());
        v.setOnTouchListener((x, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                x.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                x.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return false;
        });
    }

    private void toast(String s) {
        Toast.makeText(act, s, Toast.LENGTH_SHORT).show();
    }

    private Bitmap navIcon(int s, int color) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        Path pa = new Path();
        pa.moveTo(s * 0.92f, s * 0.08f);
        pa.lineTo(s * 0.10f, s * 0.46f);
        pa.lineTo(s * 0.46f, s * 0.56f);
        pa.lineTo(s * 0.56f, s * 0.92f);
        pa.close();
        c.drawPath(pa, p);
        return b;
    }

    private Bitmap folderIcon(int s, int color) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawRoundRect(new RectF(s * 0.08f, s * 0.20f, s * 0.46f, s * 0.40f), s * 0.05f, s * 0.05f, p);
        c.drawRoundRect(new RectF(s * 0.08f, s * 0.30f, s * 0.92f, s * 0.82f), s * 0.08f, s * 0.08f, p);
        return b;
    }

    private Bitmap plusIcon(int s, int color) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.12f);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(s * 0.2f, s * 0.5f, s * 0.8f, s * 0.5f, p);
        c.drawLine(s * 0.5f, s * 0.2f, s * 0.5f, s * 0.8f, p);
        return b;
    }

    private Bitmap playIcon(int s, int color) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        Path pa = new Path();
        pa.moveTo(s * 0.30f, s * 0.18f);
        pa.lineTo(s * 0.82f, s * 0.50f);
        pa.lineTo(s * 0.30f, s * 0.82f);
        pa.close();
        c.drawPath(pa, p);
        return b;
    }

    private Drawable bmp(Bitmap b) {
        return new BitmapDrawable(act.getResources(), b);
    }

    // ------------------------------------------------------------ top area

    private void buildLogo() {
        SpannableString s = new SpannableString("TIGGO 9");
        s.setSpan(new ForegroundColorSpan(Color.WHITE), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(RED), 6, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView logo = new TextView(act);
        logo.setText(s);
        logo.setTextSize(TypedValue.COMPLEX_UNIT_PX, 72 * u);
        logo.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC));
        logo.setLetterSpacing(0.04f);
        logo.setIncludeFontPadding(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        lp.leftMargin = margin + px(6);
        lp.topMargin = px(34);
        addView(logo, lp);
        // Long press on the logo opens the launcher's own settings.
        logo.setOnLongClickListener(v -> {
            try {
                act.startActivity(new Intent(act, SettingsActivity.class));
            } catch (Exception e) {
                toast("Не удалось открыть настройки");
            }
            return true;
        });
    }

    private void buildClock() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        TextClock clock = new TextClock(act);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 136 * u);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        box.addView(clock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextClock date = new TextClock(act);
        date.setFormat24Hour("EEE, d MMM");
        date.setFormat12Hour("EEE, d MMM");
        date.setTextColor(0xE6FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, 34 * u);
        date.setIncludeFontPadding(false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = px(4);
        box.addView(date, dlp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = px(24);
        addView(box, lp);
    }

    private void addStatusButton(LinearLayout pill, Bitmap icon, Runnable click) {
        ImageView iv = new ImageView(act);
        iv.setImageBitmap(icon);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        pressable(iv, click);
        pill.addView(iv, new LinearLayout.LayoutParams(px(84), px(84)));
    }

    private void buildStatus() {
        LinearLayout pill = new LinearLayout(act);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(glass(px(48)));
        pill.setPadding(px(18), px(6), px(18), px(6));
        int s = px(44);
        addStatusButton(pill, Glyphs.get("wifi", s, Color.WHITE), () -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        addStatusButton(pill, Glyphs.get("bluetooth", s, Color.WHITE),
                () -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        addStatusButton(pill, Glyphs.get("gear", s, Color.WHITE), () -> openSettings(Settings.ACTION_SETTINGS));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lp.rightMargin = margin;
        lp.topMargin = px(34);
        addView(pill, lp);
    }

    private void openSettings(String action) {
        try {
            act.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("Не удалось открыть настройки");
        }
    }

    // ------------------------------------------------------------ widgets

    private void buildWeatherCard() {
        FrameLayout card = new FrameLayout(act);
        card.setBackground(glass(px(44)));

        ImageView icon = new ImageView(act);
        icon.setImageBitmap(Glyphs.get("cloud", px(110), Color.WHITE));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(px(110), px(110), Gravity.TOP | Gravity.START);
        ilp.leftMargin = px(30);
        ilp.topMargin = px(24);
        card.addView(icon, ilp);

        tempView = text("--°", 88 * u, Color.WHITE, Typeface.create("sans-serif-light", Typeface.NORMAL));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        tlp.rightMargin = px(30);
        tlp.topMargin = px(34);
        card.addView(tempView, tlp);

        LinearLayout cityRow = new LinearLayout(act);
        cityRow.setOrientation(LinearLayout.HORIZONTAL);
        cityRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView pin = new ImageView(act);
        pin.setImageBitmap(Glyphs.get("pin", px(34), Color.WHITE));
        cityRow.addView(pin, new LinearLayout.LayoutParams(px(34), px(34)));
        cityView = text("", 38 * u, Color.WHITE, Typeface.DEFAULT);
        cityView.setSingleLine(true);
        cityView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = px(8);
        cityRow.addView(cityView, clp);
        FrameLayout.LayoutParams crlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        crlp.leftMargin = px(28);
        crlp.rightMargin = px(20);
        crlp.bottomMargin = px(84);
        card.addView(cityRow, crlp);

        descView = text("", 30 * u, 0xDDFFFFFF, Typeface.DEFAULT);
        descView.setSingleLine(true);
        descView.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        dlp.leftMargin = px(32);
        dlp.rightMargin = px(20);
        dlp.bottomMargin = px(34);
        card.addView(descView, dlp);

        pressable(card, this::showWeatherPanel);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (sw * 0.255f), px(280),
                Gravity.TOP | Gravity.START);
        lp.leftMargin = margin;
        lp.topMargin = px(236);
        addView(card, lp);
    }

    private void buildAppWidget() {
        appWidget = new FrameLayout(act);
        appWidget.setBackground(glass(px(44)));

        appWidgetIcon = new ImageView(act);
        appWidgetIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(px(130), px(130), Gravity.START | Gravity.CENTER_VERTICAL);
        ilp.leftMargin = px(48);
        ilp.bottomMargin = px(40);
        appWidget.addView(appWidgetIcon, ilp);

        appWidgetTitle = text("", 64 * u, Color.WHITE, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        appWidgetTitle.setSingleLine(true);
        appWidgetTitle.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        tlp.leftMargin = px(200);
        tlp.rightMargin = px(190);
        tlp.bottomMargin = px(70);
        appWidget.addView(appWidgetTitle, tlp);

        appWidgetSub = text("", 32 * u, 0xDDFFFFFF, Typeface.DEFAULT);
        appWidgetSub.setSingleLine(true);
        appWidgetSub.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.BOTTOM);
        slp.leftMargin = px(48);
        slp.rightMargin = px(190);
        slp.bottomMargin = px(44);
        appWidget.addView(appWidgetSub, slp);

        FrameLayout play = new FrameLayout(act);
        GradientDrawable pg = new GradientDrawable();
        pg.setShape(GradientDrawable.OVAL);
        pg.setColor(0x4DFFFFFF);
        play.setBackground(pg);
        ImageView pi = new ImageView(act);
        pi.setImageBitmap(playIcon(px(60), Color.WHITE));
        pi.setScaleType(ImageView.ScaleType.CENTER);
        play.addView(pi, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(px(120), px(120),
                Gravity.END | Gravity.CENTER_VERTICAL);
        plp.rightMargin = px(40);
        appWidget.addView(play, plp);

        pressable(appWidget, () -> {
            AppItem a = find(YOUTUBE);
            if (a != null) launch(a);
            else showAllApps();
        });
        int left = (int) (sw * 0.525f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sw - left - margin, px(280),
                Gravity.TOP | Gravity.START);
        lp.leftMargin = left;
        lp.topMargin = px(236);
        addView(appWidget, lp);
        refreshAppWidget();
    }

    private void refreshAppWidget() {
        AppItem a = find(YOUTUBE);
        if (a != null) {
            appWidgetIcon.setImageDrawable(a.icon);
            appWidgetTitle.setText(a.label);
            appWidgetSub.setText("Смотреть любимые видео");
        } else {
            appWidgetIcon.setImageBitmap(Glyphs.get("apps", px(110), Color.WHITE));
            appWidgetTitle.setText("Все приложения");
            appWidgetSub.setText("Нажмите, чтобы открыть список");
        }
    }

    // ------------------------------------------------------------ app row

    private void buildAppRow() {
        appRow = new LinearLayout(act);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setGravity(Gravity.TOP);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sw - 2 * margin,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.leftMargin = margin;
        lp.topMargin = px(598);
        addView(appRow, lp);
    }

    private void addSlot(String label, Drawable icon, Runnable click) {
        LinearLayout slot = new LinearLayout(act);
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout tile = new FrameLayout(act);
        tile.setBackground(glass(px(40)));
        ImageView iv = new ImageView(act);
        iv.setImageDrawable(icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(px(98), px(98), Gravity.CENTER);
        tile.addView(iv, ilp);
        slot.addView(tile, new LinearLayout.LayoutParams(px(150), px(150)));

        TextView t = text(label, 26 * u, Color.WHITE, Typeface.DEFAULT);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = px(14);
        slot.addView(t, tlp);

        pressable(slot, click);
        appRow.addView(slot, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void populateRow() {
        refreshAppWidget();
        appRow.removeAllViews();
        String[][] favs = {YOUTUBE, NETFLIX, PRIME, KINOPOISK, PLAY};
        List<AppItem> first = new ArrayList<>();
        for (String[] f : favs) {
            AppItem a = find(f);
            if (a != null && !first.contains(a)) first.add(a);
        }
        for (AppItem a : allApps) {
            if (first.size() >= 5) break;
            if (!first.contains(a)) first.add(a);
        }
        AppItem music = find(MUSIC);
        if (music == null || first.contains(music)) {
            music = null;
            for (AppItem a : allApps) {
                if (!first.contains(a)) {
                    music = a;
                    break;
                }
            }
        }
        for (AppItem a : first) {
            addSlot(a.label, a.icon, () -> launch(a));
        }
        addSlot("Bluetooth", bmp(Glyphs.get("bluetooth", px(96), Color.WHITE)),
                () -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        if (music != null) {
            final AppItem m = music;
            addSlot(m.label, m.icon, () -> launch(m));
        }
        addSlot("Все приложения", bmp(Glyphs.get("apps", px(96), Color.WHITE)), this::showAllApps);
    }

    // ------------------------------------------------------------ bottom dock

    private void addDockButton(LinearLayout dock, Bitmap icon, boolean selected, Runnable click) {
        FrameLayout cell = new FrameLayout(act);
        ImageView iv = new ImageView(act);
        iv.setImageBitmap(icon);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        cell.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (selected) {
            View bar = new View(act);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(px(4));
            bg.setColor(0xFF4DA3FF);
            bar.setBackground(bg);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(px(120), px(6),
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            cell.addView(bar, blp);
        }
        pressable(cell, click);
        dock.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void buildDock() {
        LinearLayout dock = new LinearLayout(act);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setBackground(glass(px(62)));
        dock.setPadding(px(20), px(8), px(20), px(8));
        int s = px(64);
        addDockButton(dock, Glyphs.get("house", s, Color.WHITE), true, this::closeOverlay);
        addDockButton(dock, navIcon(s, Color.WHITE), false, () -> openFirst(NAVI, "Навигатор не найден"));
        addDockButton(dock, Glyphs.get("present", s, Color.WHITE), false, () -> openFirst(YOUTUBE, "Видеоприложение не найдено"));
        addDockButton(dock, Glyphs.get("controls", s, Color.WHITE), false, () -> openSettings(Settings.ACTION_SETTINGS));
        addDockButton(dock, Glyphs.get("audio", s, Color.WHITE), false, () -> openFirst(MUSIC, "Музыкальное приложение не найдено"));
        addDockButton(dock, folderIcon(s, Color.WHITE), false, () -> openFirst(FILES, "Файловый менеджер не найден"));
        addDockButton(dock, plusIcon(s, Color.WHITE), false, this::showAllApps);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sw - 2 * margin, px(124),
                Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = margin;
        lp.bottomMargin = px(40);
        addView(dock, lp);
    }

    private void openFirst(String[] pkgs, String missing) {
        AppItem a = find(pkgs);
        if (a != null) {
            launch(a);
        } else {
            toast(missing);
            showAllApps();
        }
    }

    // ------------------------------------------------------------ apps

    private AppItem find(String[] pkgs) {
        for (String p : pkgs) {
            for (AppItem a : allApps) {
                if (a.pkg.equals(p)) return a;
            }
        }
        return null;
    }

    private void launch(AppItem a) {
        try {
            act.startActivity(a.intent);
        } catch (Exception e) {
            toast("Не удалось открыть " + a.label);
        }
    }

    private void loadAppsAsync() {
        new Thread(() -> {
            List<AppItem> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String[] cats = {Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER};
            for (String cat : cats) {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
                for (ResolveInfo ri : list) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(act.getPackageName()) || !seen.add(pkg)) continue;
                    AppItem it = new AppItem();
                    it.pkg = pkg;
                    it.label = String.valueOf(ri.loadLabel(pm));
                    it.icon = ri.loadIcon(pm);
                    it.intent = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                            .setComponent(new ComponentName(pkg, ri.activityInfo.name))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    out.add(it);
                }
            }
            Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
            post(() -> {
                allApps.clear();
                allApps.addAll(out);
                populateRow();
            });
        }, "car-apps").start();
    }

    // ------------------------------------------------------------ overlays

    boolean isOverlayOpen() {
        return overlay != null;
    }

    void closeOverlay() {
        if (overlay == null) return;
        final View v = overlay;
        overlay = null;
        v.animate().alpha(0f).setDuration(150).withEndAction(() -> removeView(v)).start();
    }

    private void openOverlay(View layer) {
        closeOverlay();
        overlay = layer;
        layer.setAlpha(0f);
        addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        layer.animate().alpha(1f).setDuration(180).start();
    }

    private void showAllApps() {
        FrameLayout layer = new FrameLayout(act);
        layer.setBackgroundColor(0xF2121733);
        layer.setClickable(true);

        TextView title = text("Все приложения", 52 * u, Color.WHITE, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        tlp.leftMargin = margin;
        tlp.topMargin = px(40);
        layer.addView(title, tlp);

        FrameLayout closeBtn = new FrameLayout(act);
        GradientDrawable cg = new GradientDrawable();
        cg.setShape(GradientDrawable.OVAL);
        cg.setColor(0x40FFFFFF);
        closeBtn.setBackground(cg);
        ImageView x = new ImageView(act);
        x.setImageBitmap(plusIcon(px(60), Color.WHITE));
        x.setRotation(45f);
        x.setScaleType(ImageView.ScaleType.CENTER);
        closeBtn.addView(x, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        pressable(closeBtn, this::closeOverlay);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(px(96), px(96), Gravity.TOP | Gravity.END);
        clp.rightMargin = margin;
        clp.topMargin = px(30);
        layer.addView(closeBtn, clp);

        ScrollView sv = new ScrollView(act);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(margin, 0, margin, px(40));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        int cols = Math.max(4, (sw - 2 * margin) / px(190));
        LinearLayout rowL = null;
        int n = 0;
        for (AppItem a : allApps) {
            if (n % cols == 0) {
                rowL = new LinearLayout(act);
                rowL.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = px(26);
                col.addView(rowL, rlp);
            }
            LinearLayout cell = new LinearLayout(act);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout tile = new FrameLayout(act);
            tile.setBackground(glass(px(34)));
            ImageView iv = new ImageView(act);
            iv.setImageDrawable(a.icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(iv, new FrameLayout.LayoutParams(px(88), px(88), Gravity.CENTER));
            cell.addView(tile, new LinearLayout.LayoutParams(px(122), px(122)));
            TextView lbl = text(a.label, 24 * u, Color.WHITE, Typeface.DEFAULT);
            lbl.setMaxLines(2);
            lbl.setEllipsize(TextUtils.TruncateAt.END);
            lbl.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = px(10);
            cell.addView(lbl, llp);
            pressable(cell, () -> {
                closeOverlay();
                launch(a);
            });
            rowL.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            n++;
        }
        if (rowL != null) {
            while (n % cols != 0) {
                rowL.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1f));
                n++;
            }
        }
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        slp.topMargin = px(150);
        layer.addView(sv, slp);
        openOverlay(layer);
    }

    // ------------------------------------------------------------ weather

    private static String signed(int t) {
        return t > 0 ? "+" + t : String.valueOf(t);
    }

    private static String describeRu(int code, boolean day) {
        switch (code) {
            case 0: return day ? "Ясно" : "Ясная ночь";
            case 1: return "Преимущественно ясно";
            case 2: return "Переменная облачность";
            case 3: return "Пасмурно";
            case 45: case 48: return "Туман";
            case 51: case 53: case 55: case 56: case 57: return "Морось";
            case 61: case 63: case 80: case 81: return "Дождь";
            case 65: case 82: return "Сильный дождь";
            case 66: case 67: return "Ледяной дождь";
            case 71: case 73: case 75: case 77: case 85: case 86: return "Снег";
            case 95: case 96: case 99: return "Гроза";
            default: return "";
        }
    }

    void updateWeather() {
        Weather.Data d = Weather.enabled(act) ? Weather.cached(act) : null;
        if (d == null) {
            tempView.setText("--°");
            cityView.setText("Погода");
            descView.setText("Нажмите, чтобы обновить");
            return;
        }
        String unit = "f".equals(Weather.units(act)) ? "F" : "C";
        tempView.setText(signed(d.temp) + "°" + unit);
        cityView.setText(d.place == null || d.place.isEmpty() ? "—" : d.place);
        descView.setText(describeRu(d.code, d.day));
    }

    private void refreshWeather(boolean force) {
        Weather.refresh(act, force, () -> post(this::updateWeather));
    }

    private void showWeatherPanel() {
        Weather.Data d = Weather.enabled(act) ? Weather.cached(act) : null;
        if (d == null) {
            Weather.refresh(act, true, () -> post(() -> {
                updateWeather();
                if (Weather.cached(act) != null) showWeatherPanel();
                else toast("Нет данных о погоде. Проверьте интернет.");
            }));
            return;
        }
        String unit = "f".equals(Weather.units(act)) ? "F" : "C";

        FrameLayout layer = new FrameLayout(act);
        layer.setBackgroundColor(0xB3080C1E);
        layer.setClickable(true);
        layer.setOnClickListener(v -> closeOverlay());

        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF01E2A63, 0xF0352C62});
        pg.setCornerRadius(px(44));
        pg.setStroke(Math.max(1, px(2)), 0x66FFFFFF);
        panel.setBackground(pg);
        panel.setPadding(px(48), px(36), px(48), px(36));
        panel.setOnClickListener(v -> closeOverlay());

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(act);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text(d.place == null || d.place.isEmpty() ? "Погода" : d.place, 46 * u, Color.WHITE,
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)));
        TextView desc = text(describeRu(d.code, d.day), 34 * u, 0xDDFFFFFF, Typeface.DEFAULT);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = px(8);
        left.addView(desc, dlp);
        head.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(text(signed(d.temp) + "°" + unit, 110 * u, Color.WHITE,
                Typeface.create("sans-serif-light", Typeface.NORMAL)));
        panel.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        String details = "Ощущается " + signed(d.feels) + "°   •   Влажность " + d.humidity + "%   •   Ветер "
                + d.wind + " " + d.windUnit + "   •   Макс " + signed(d.max) + "°  Мин " + signed(d.min) + "°";
        TextView det = text(details, 28 * u, 0xCCFFFFFF, Typeface.DEFAULT);
        LinearLayout.LayoutParams detLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detLp.topMargin = px(18);
        detLp.bottomMargin = px(14);
        panel.addView(det, detLp);

        int shown = 0;
        for (Weather.Day x : d.days) {
            if (shown >= 5) break;
            shown++;
            LinearLayout r = new LinearLayout(act);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            String name = x.label != null && !x.label.isEmpty() ? x.label : String.valueOf(x.date);
            r.addView(text(name, 30 * u, Color.WHITE, Typeface.DEFAULT),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
            r.addView(text(describeRu(x.code, true), 28 * u, 0xDDFFFFFF, Typeface.DEFAULT),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
            String rain = x.rain >= 0 ? "   " + x.rain + "%" : "";
            TextView range = text(signed(x.max) + "° / " + signed(x.min) + "°" + rain, 30 * u, Color.WHITE,
                    Typeface.DEFAULT);
            range.setGravity(Gravity.END);
            r.addView(range, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = px(14);
            panel.addView(r, rlp);
        }

        TextView hint = text("Нажмите, чтобы закрыть", 24 * u, 0x99FFFFFF, Typeface.DEFAULT);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = px(24);
        panel.addView(hint, hlp);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Math.min(px(980), sw - 2 * margin),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        layer.addView(panel, plp);
        openOverlay(layer);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) {
            loadAppsAsync();
            updateWeather();
            refreshWeather(false);
        }
    }
}
