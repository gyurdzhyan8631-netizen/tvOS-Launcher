package com.techgenius.tvoslauncher;

import android.app.ActivityOptions;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.graphics.Rect;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.DisplayMetrics;
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
import android.widget.VideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private static final String PREF_DOCK = "tiggo9_dock3_";
    private static final String PREF_WALL = "tiggo9_wall";
    private static final String PREF_TILE_GLASS = "tiggo9_tile_glass";
    private static final int WHITE = 0xFFFFFFFF;

    // Each entry lists alternative packages ("tv|phone"): the first one that is installed wins.
    private static final String[] DEFAULT_PACKAGES = {
            "com.google.android.youtube.tv|com.google.android.youtube",
            "com.android.vending",
            "com.netflix.ninja|com.netflix.mediaclient",
            "ru.kinopoisk"
    };

    // Bottom dock: eight shortcuts. The label is the default; the app is found by package or words.
    private static final int DOCK_COUNT = 8;
    private static final int DOCK_EXTRA_MAX = 16;
    private static final String PREF_WINDOW_MODE = "tiggo9_window_mode";
    private static final String PREF_WINDOW_PCT = "tiggo9_window_pct";
    private static final String PREF_WAPP_PKG = "tiggo9_wapp_pkg_";
    private static final String[] DOCK_LABELS = {
            "Автомобиль", "Телефон", "Настройки", "Файлы", "Chrome", "Telegram", "ADB TV", "TV Bro"};
    private static final String[][] DOCK_PACKAGES = {
            {},
            {"com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"},
            {"com.android.settings"},
            {"com.google.android.apps.nbu.files", "com.alphainventor.filemanager", "com.mixplorer",
                    "com.lonelycatgames.xplore", "com.ghisler.android.TotalCommander"},
            {"com.android.chrome"},
            {"org.telegram.messenger", "org.telegram.messenger.web"},
            {},
            {"com.phlox.tvwebbrowser"}
    };
    private static final String[][] DOCK_WORDS = {
            {}, {"телефон", "phone", "dialer"}, {"настройки", "settings"},
            {"проводник", "файлы", "files", "explorer"}, {"chrome"}, {"telegram", "телеграм"},
            {"adb tv", "adbtv"}, {"tv bro", "tvbro"}
    };

    // Right column widgets
    private static final String[] WIDGET_IDS = {"weather", "nav", "music"};
    private static final String[] WIDGET_TITLES = {"Погода", "Навигация", "Музыка"};
    private static final float[] WIDGET_BASE = {262f, 100f, 190f};
    private static final float[] WIDGET_FACTOR = {0.75f, 1f, 1.3f};
    private static final String PREF_WIDGETS = "tiggo9_widgets";
    private static final String PREF_WSIZE = "tiggo9_wsize_";
    private static final String PREF_WAPP = "tiggo9_wapp_";
    private static final String[] NAV_PACKAGES = {
            "ru.yandex.yandexnavi", "ru.yandex.yandexmaps", "ru.dublgis.dgismobile", "com.navitel",
            "com.waze", "com.sygic.aura", "net.osmand.plus", "net.osmand", "app.organicmaps",
            "com.google.android.apps.maps"};
    private static final String[] NAV_WORDS = {
            "навигатор", "navigator", "navitel", "навител", "waze", "sygic", "osmand", "карты", "maps"};
    private static final String[] MUSIC_PACKAGES = {
            "ru.yandex.music", "com.spotify.music", "com.spotify.tv.android",
            "com.google.android.apps.youtube.music"};
    private static final String[] MUSIC_WORDS = {"музык", "music", "плеер", "player", "poweramp"};

    private static final String WALL_AUTO = "auto";
    private static final String[] WALL_PRESETS = {
            WALL_AUTO, "photo:tiggo9_autumn.jpg", "photo:tiggo9_winter.jpg", "photo:tiggo9_spring.jpg",
            "photo:tiggo9_summer.jpg", "photo:tiggo9_night.jpg", "photo:tiggo9-car.jpg"
    };
    private static final String[] WALL_CAPTIONS = {
            "Авто: сезон и время суток", "Осень", "Зима", "Весна", "Лето", "Ночь", "TIGGO 9",
            "Фото 1", "Фото 2", "Фото 3", "Фото 4", "Фото 5",
            "Аврора", "Полночь", "Закат", "Океан", "Графит"
    };
    private static final String CAR_WALLPAPER = "photo:tiggo9-car.jpg";
    private static final String PREF_CARD = "tiggo9_card_app";
    private static final String PREF_ICONS = "tiggo9_icon_pack";

    private interface Pick {
        void on(MainActivity.AppEntry entry);
    }

    private final MainActivity host;
    private float k;
    private int side;
    private LinearLayout appStrip;
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
    private View wifiView;
    private View btView;
    private ImageView wallView;
    private ImageView barsView;
    private LinearLayout widgetCol;
    private LinearLayout dockSlots;
    private HorizontalScrollView dockScroll;
    private TextView dockTip;
    private FrameLayout clockHolder;
    private ImageView fgView;
    private Bitmap fgBmp;
    private TextView wBig;
    private TextView wUnitBottom;
    private TextView wCond;
    private TextView wSub;
    private final TextView[] wHourLabel = new TextView[5];
    private final ImageView[] wHourIcon = new ImageView[5];
    private final TextView[] wHourTemp = new TextView[5];
    private ImageView mArt;
    private TextView mTitle;
    private TextView mArtist;
    private TextView mElapsed;
    private TextView mRemain;
    private ImageView mPlay;
    private View mBar;
    private View mFill;
    private View mKnob;
    private Bitmap mLastArt;
    private long mDurationMs;
    private boolean mPlaying;
    private View dockView;
    private List<MainActivity.AppEntry> dockPool = new ArrayList<>();
    private FrameLayout promoLayer;
    private VideoView promoVideo;
    private boolean promoOpen;
    private FrameLayout drawerPanel;
    private GradientDrawable vpnPill;
    private TextView vpnText;
    private String shownStyle;
    private Bitmap wallBmp;
    private String wallStyle;
    private int oy;
    private FrameLayout uiLayer;
    private View tintView;
    private int uiW;
    private int uiH;
    private String lastSig;
    private int slotCount = 4;
    private boolean lastBt;
    private boolean iconBusy;
    private boolean iconPending;
    private boolean allAppsOpen;
    private boolean sheetOpen;
    private int tickCount;
    /** True while the home window is on screen: timers only run then (nothing ticks behind other apps). */
    private boolean homeShown;
    private boolean lastVpn, lastWifi, lastCell;
    /** Weather cards: tinted like the iOS Weather widget (blue sky, night navy, grey clouds...). */
    private final List<View> weatherSurfaces = new ArrayList<>();
    private int weatherTintShown;
    private FrameLayout ccLayer;
    private boolean ccOpen;
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
        this.oy = Math.max(0, (h - Math.round(941 * this.k)) / 2);

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
        wallView.setScaleX(1.12f);  // the background a little larger
        wallView.setScaleY(1.12f);
        addView(wallView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        clockHolder = new FrameLayout(host);
        clockHolder.setClipChildren(false);
        addView(clockHolder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fgView = new ImageView(host);  // mountains, trees, the car: in front of the clock
        fgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fgView.setScaleX(1.12f);
        fgView.setScaleY(1.12f);
        addView(fgView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadWallpaper();

        tintView = new View(host);
        addView(tintView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        applyTint();

        buildUi(w, h);

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

        ccLayer = new FrameLayout(host);
        ccLayer.setVisibility(GONE);
        ccLayer.setClickable(true);
        addView(ccLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        lastSig = TiggoTheme.signature(host);
        scheduleIconPack();
        if (!Weather.manual(host)) requestFix();
    }

    private void applyTint() {
        if (tintView == null) return;
        int[] t = TiggoTheme.tint(host);
        tintView.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{t[0], t[1]}));
    }

    /** (Re)creates the whole home layout for the real window size and the chosen look. */
    private void buildUi(int w, int h) {
        uiW = w;
        uiH = h;
        k = Math.max(0.2f, Math.min(w / 1672f, h / 941f));
        side = Math.round(w * 0.029f);
        oy = Math.max(0, (h - Math.round(941 * k)) / 2);
        if (uiLayer != null) removeView(uiLayer);
        FrameLayout layer = new FrameLayout(host);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        uiLayer = layer;
        addView(layer, 4, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        layer.addView(buildLogo(), pos(m(330), m(60), m(60), oy + m(34)));
        layer.addView(buildTopRight(), posEnd(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, m(39), oy + m(24)));
        clockHolder.removeAllViews();
        clockHolder.addView(buildClockBlock(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        float ds = dockScale();
        float is = iconScale();
        int dockW = Math.min(Math.round(w * 0.67f), m(1500));
        int dockH = m(132 * ds);
        int dockTop = Math.max(m(560), h - dockH - m(60));

        // the column of app icons fills the space between the clock and the dock
        int tilesTop = oy + m(143);
        int tileH = Math.max(1, m(76 * is + 42));
        int avail = Math.max(tileH, dockTop - m(20) - tilesTop);
        slotCount = Math.max(2, Math.min(6, (avail + m(6)) / tileH));
        appStrip = new LinearLayout(host);
        appStrip.setOrientation(LinearLayout.VERTICAL);
        appStrip.setClipChildren(false);
        appStrip.setClipToPadding(false);
        if (host.prefs.getBoolean(PREF_TILE_GLASS, true)) {
            // one glass column, like the CarPlay side bar
            appStrip.setBackground(glass(m(44), TiggoTheme.tileFill(host)));
            appStrip.setPadding(0, m(8), 0, m(8));
        }
        layer.addView(appStrip, pos(m(168), slotCount * tileH + m(16), m(46), tilesTop));

        widgetCol = new LinearLayout(host);
        widgetCol.setOrientation(LinearLayout.VERTICAL);
        widgetCol.setClipChildren(false);
        widgetCol.setClipToPadding(false);
        layer.addView(widgetCol, posEnd(m(366), m(560), m(38), oy + m(170)));

        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(dockW, dockH, Gravity.TOP | Gravity.START);
        dockLp.leftMargin = (w - dockW) / 2;
        dockLp.topMargin = dockTop;
        layer.addView(buildDock(), dockLp);

        statusKnown = false;
        buildWidgets();
        rebuildHomeApps();
        refreshWeather();
        updateDate();
        refreshStatus();
        post(this::refreshGlass);
        postDelayed(this::refreshGlass, 1200);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w > 0 && h > 0 && (Math.abs(w - uiW) > 2 || Math.abs(h - uiH) > 2)) {
            final int nw = w;
            final int nh = h;
            post(() -> buildUi(nw, nh));
        }
    }

    /** Called when the home comes back (for example from the settings): applies changed looks. */
    void refreshStyleIfChanged() {
        String sig = TiggoTheme.signature(host);
        if (!sig.equals(lastSig)) {
            lastSig = sig;
            applyTint();
            if (uiW > 0 && uiH > 0) buildUi(uiW, uiH);
            scheduleIconPack();
        }
        String sel = host.prefs.getString(PREF_WALL, WALL_AUTO);
        if (sel != null && !sel.equals(wallStyle)) {
            wallStyle = sel;
            String eff = effectiveStyle(sel);
            showWallpaperAsync(eff);
            host.applyTiggoWallpaper(eff);
        }
        if (host.prefs.getBoolean("tiggo9_play_promo", false)) {
            host.prefs.edit().putBoolean("tiggo9_play_promo", false).apply();
            openPromo();
        }
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

    /** iOS Liquid Glass: the blurred wallpaper behind the surface, a light tint, sheen and rim. */
    private Drawable glass(int radiusPx, int fill) {
        int a = Math.min(0x9A, Math.round(Color.alpha(fill) * 0.55f));
        return new TiggoGlass(radiusPx, (a << 24) | (fill & 0x00FFFFFF), Math.max(1f, k * 1.6f));
    }

    /** Glass over the softer, darker backdrop: menus and panels with a lot of text. */
    private Drawable heavyGlass(int radiusPx, int tint) {
        return new TiggoGlass(radiusPx, tint, Math.max(1f, k * 1.6f), true);
    }

    /** Solid iOS button (system blue, green...) with a touch ripple. */
    private Drawable solid(int radiusPx, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), g, rippleMask(radiusPx));
    }

    /** Re-renders the blurred backdrop the glass shows and redraws every glass surface. */
    private void refreshGlass() {
        if (getWidth() == 0) return;
        TiggoGlass.capture(this, 4);
        TiggoGlass.invalidateTree(this);
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
            logo.setOnLongClickListener(v -> {
                openPromo();
                return true;
            });
            return logo;
        }
        TextView fallback = text("TIGGO 9", 38, true);
        fallback.setOnLongClickListener(v -> {
            openPromo();
            return true;
        });
        return fallback;
    }

    /** Status icon: a tap opens the Control Center (like iOS), a long press the system page. */
    private ImageView statusButton(Bitmap bmp, View.OnClickListener click) {
        ImageView iv = iconView(bmp);
        iv.setPadding(m(8), m(8), m(8), m(8));
        iv.setBackground(rippleOnly(m(30)));
        iv.setOnClickListener(v -> openControlCenter());
        iv.setOnLongClickListener(v -> {
            click.onClick(v);
            return true;
        });
        pressScale(iv);
        return iv;
    }

    private void openTiggoSettings() {
        try {
            host.startActivity(new Intent(host, TiggoSettingsActivity.class));
        } catch (Throwable t) {
            openControlCenter();
        }
    }

    /** Transparent capsule (VPN, Wi-Fi, Bluetooth, signal, settings) and the large clock with the date. */
    private View buildTopRight() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout capsule = new LinearLayout(host);
        capsule.setOrientation(LinearLayout.HORIZONTAL);
        capsule.setGravity(Gravity.CENTER_VERTICAL);
        capsule.setPadding(m(14), m(4), m(14), m(4));
        capsule.setBackground(glass(m(40), TiggoTheme.barFill(host)));

        vpnView = statusButton(TiggoIcons.vpnShield(m(44), false),
                v -> openSettings(Settings.ACTION_VPN_SETTINGS));
        capsule.addView(vpnView, lp(m(66), m(64)));
        wifiView = statusButton(Glyphs.get("wifi", m(44), WHITE),
                v -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        capsule.addView(wifiView, lp(m(66), m(64)));
        btView = statusButton(TiggoIcons.bluetooth(m(40), WHITE),
                v -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        capsule.addView(btView, lp(m(66), m(64)));
        barsView = statusButton(TiggoIcons.bars(m(44), 0, WHITE),
                v -> openSettings(Settings.ACTION_WIRELESS_SETTINGS));
        capsule.addView(barsView, lp(m(66), m(64)));

        View divider = new View(host);
        divider.setBackgroundColor(0xAAFFFFFF);
        LinearLayout.LayoutParams divLp = lp(Math.max(1, m(2)), m(36));
        divLp.leftMargin = m(8);
        divLp.rightMargin = m(8);
        capsule.addView(divider, divLp);

        ImageView gear = statusButton(Glyphs.get("gear", m(44), WHITE), v -> openTiggoSettings());
        gear.setOnClickListener(v -> openTiggoSettings());
        gear.setOnLongClickListener(v -> {
            openControlCenter();
            return true;
        });
        capsule.addView(gear, lp(m(66), m(64)));
        box.addView(capsule);

        return box;
    }

    private float dockScale() {
        return Scenes.getDockScaleFactor(host);
    }

    private float iconScale() {
        return Scenes.getIconScalePercent(host) / 100f;
    }

    /** Text drawn as glass: a light gradient, a bright rim and a soft shadow. */
    private static void drawGlass(android.graphics.Canvas c, TextView tv) {
        CharSequence t = tv.getText();
        if (t == null || t.length() == 0) return;
        android.text.TextPaint src = tv.getPaint();
        float size = src.getTextSize();
        float x = tv.getPaddingLeft();
        float y = tv.getBaseline();
        // iOS lock-screen clock: frosted white digits, brighter at the top, with a soft shadow
        android.graphics.Paint fillPaint = new android.graphics.Paint(src);
        fillPaint.setStyle(android.graphics.Paint.Style.FILL);
        fillPaint.setShadowLayer(size * 0.06f, 0, size * 0.015f, 0x40000000);
        fillPaint.setShader(new LinearGradient(0, y - size * 0.75f, 0, y,
                new int[]{0xF5FFFFFF, 0xC8F2F6FF}, null, Shader.TileMode.CLAMP));
        c.drawText(t, 0, t.length(), x, y, fillPaint);
    }

    private static final class GlassClock extends TextClock {
        GlassClock(Context c) {
            super(c);
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            drawGlass(canvas, this);
        }
    }

    private static final class GlassText extends TextView {
        GlassText(Context c) {
            super(c);
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            drawGlass(canvas, this);
        }
    }

    /** Giant thin clock in the middle with the date above it; wallpaper objects overlap its lower part. */
    private View buildClockBlock() {
        int style = TiggoTheme.get(host, TiggoTheme.K_CLOCK, 1, 3);
        boolean glassy = style == 1 || style == 2;
        float timeSize = style == 2 ? 360f : (style == 3 ? 180f : 300f);
        float dateSize = style == 2 ? 34f : (style == 3 ? 26f : 32f);
        // iOS lock screen: heavy rounded-looking digits; "Classic" keeps a light, airy face
        android.graphics.Typeface thin = style == 0 ? Ui.font(host, "regular") : Ui.font(host, "display");
        FrameLayout box = new FrameLayout(host);
        box.setClipChildren(false);

        TextClock time = glassy ? new GlassClock(host) : new TextClock(host);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, timeSize * k);
        time.setTypeface(thin);
        time.setIncludeFontPadding(false);
        android.graphics.Paint probe = new android.graphics.Paint();
        probe.setTypeface(thin);
        probe.setTextSize(timeSize * k);
        time.setMinWidth(Math.round(probe.measureText("00:00")) + m(16));  // never cut the minutes
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tl.topMargin = oy + m(113f - 0.2167f * timeSize);  // top of the digits at the same height as in the photo
        box.addView(time, tl);

        dateView = new TextView(host);
        dateView.setTextColor(Color.WHITE);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dateSize * k);
        dateView.setTypeface(Ui.font(host, "semibold"));
        dateView.setIncludeFontPadding(false);
        dateView.setSingleLine(true);
        dateView.setShadowLayer(m(5), 0, m(1), 0x66000000);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        dl.topMargin = oy + m(64);
        box.addView(dateView, dl);
        return box;
    }

    private void openSettings(String action) {
        try {
            host.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            try {
                host.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t2) {
                host.toast(TiggoText.t("Настройки недоступны"));
            }
        }
    }

    private void updateDate() {
        if (dateView == null) return;
        try {
            Locale ru = new Locale("ru");
            String raw = new SimpleDateFormat("EEEE, d MMMM", ru).format(new Date());
            String line = raw.isEmpty() ? raw : raw.substring(0, 1).toUpperCase(ru) + raw.substring(1);
            Weather.Data d = Weather.enabled(host) ? Weather.cached(host) : null;
            if (d != null) {
                String place = d.place == null ? "" : d.place;
                line += "  ·  " + (place.isEmpty() ? "" : place + " ") + (d.temp > 0 ? "+" : "") + d.temp + "°";
            }
            dateView.setText(line);
        } catch (Throwable ignored) {
            // keep the previous text
        }
    }

    @SuppressWarnings("deprecation")
    private void refreshStatus() {
        boolean vpn = false;
        boolean wifi = false;
        boolean cell = false;
        boolean bt = false;
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
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) bt = adapter.isEnabled();
        } catch (Throwable ignored) {
            // permission missing
        }
        if (statusKnown && vpn == lastVpn && wifi == lastWifi && cell == lastCell && bt == lastBt) return;
        statusKnown = true;
        lastVpn = vpn;
        lastWifi = wifi;
        lastCell = cell;
        lastBt = bt;
        if (vpnView != null) vpnView.setImageBitmap(TiggoIcons.vpnShield(m(44), vpn));
        if (wifiView != null) wifiView.setAlpha(wifi ? 1f : 0.45f);
        if (barsView != null) barsView.setImageBitmap(TiggoIcons.bars(m(44), cell ? 4 : 0, WHITE));
        if (btView instanceof ImageView) {
            ((ImageView) btView).setImageBitmap(TiggoIcons.bluetooth(m(40), bt ? 0xFF6EC1FF : 0x88FFFFFF));
        }
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshStatus();
            tickCount++;
            if (tickCount % 6 == 0) updateDate();
            if (tickCount % 12 == 0) refreshGlass();  // the clock behind the glass moved on
            if (tickCount % 12 == 0) refreshWeather();
            if (tickCount % 60 == 0) checkAutoWallpaper();
            if (tickCount % 12 == 0 && TiggoTheme.isAuto(host)) refreshStyleIfChanged();
            if (tickCount % 120 == 0) Weather.refresh(host, false, TiggoHome.this::refreshWeather);
            if (homeShown) postDelayed(this, 5000);
        }
    };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTimers();
    }

    /** Starts the status / clock / music timers (the home is visible). */
    private void startTimers() {
        homeShown = true;
        removeCallbacks(ticker);
        removeCallbacks(musicTicker);
        updateDate();
        post(ticker);
        if (mTitle != null) postDelayed(musicTicker, 300);
    }

    /** Stops every periodic timer: the launcher is behind another app or the screen is off. */
    private void stopTimers() {
        homeShown = false;
        removeCallbacks(ticker);
        removeCallbacks(musicTicker);
    }

    // ------------------------------------------------------------------ weather card

    private int weatherStyle() {
        return TiggoTheme.get(host, TiggoTheme.K_WEATHER, 0, 2);
    }

    private float weatherBase() {
        int st = weatherStyle();
        return st == 0 ? 262f : (st == 1 ? 132f : 150f);
    }

    private View buildWeatherCard() {
        int style = weatherStyle();
        if (style == 0) return buildWeatherBlocks();
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(m(26), m(14), m(24), m(14));
        card.setBackground(style == 2 ? rippleOnly(m(34)) : pressable(m(34), TiggoTheme.tileFill(host)));
        if (style != 2) weatherSurfaces.add(card);
        card.setOnClickListener(v -> host.showWeather());
        pressScale(card);

        LinearLayout top = new LinearLayout(host);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(host);
        weatherIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        top.addView(weatherIcon, lp(style == 1 ? m(80) : m(96), style == 1 ? m(80) : m(96)));
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        weatherTemp = text("--°", style == 1 ? 46 : 54, false);
        col.addView(weatherTemp, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        weatherPlace = text(TiggoText.t("Погода"), style == 1 ? 22 : 24, false);
        weatherPlace.setSingleLine(true);
        weatherPlace.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(weatherPlace, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams colLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.weight = 1f;
        colLp.leftMargin = m(14);
        top.addView(col, colLp);
        card.addView(top, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        weatherCondition = text("", 18, false);
        weatherCondition.setAlpha(0.9f);
        weatherCondition.setSingleLine(true);
        weatherCondition.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (style == 0) {
            LinearLayout.LayoutParams condLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            condLp.topMargin = m(6);
            card.addView(weatherCondition, condLp);
        }
        return card;
    }

    /** Block 1: temperature and condition. Block 2: now and the next four hours. */
    private View buildWeatherBlocks() {
        android.graphics.Typeface serif = Ui.font(host, "display");
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);

        LinearLayout a = new LinearLayout(host);
        a.setOrientation(LinearLayout.HORIZONTAL);
        a.setGravity(Gravity.CENTER_VERTICAL);
        a.setPadding(m(22), m(6), m(18), m(6));
        a.setBackground(pressable(m(34), TiggoTheme.tileFill(host)));
        weatherSurfaces.add(a);
        a.setOnClickListener(v -> host.showWeather());
        pressScale(a);
        wBig = text("--", 74, true);
        wBig.setTypeface(serif);
        a.addView(wBig, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout unit = new LinearLayout(host);
        unit.setOrientation(LinearLayout.VERTICAL);
        TextView degree = text("°", 28, true);
        degree.setTypeface(serif);
        wUnitBottom = text("C", 28, true);
        wUnitBottom.setTypeface(serif);
        unit.addView(degree);
        unit.addView(wUnitBottom);
        LinearLayout.LayoutParams ul = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ul.leftMargin = m(4);
        a.addView(unit, ul);
        LinearLayout txt = new LinearLayout(host);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setGravity(Gravity.CENTER_VERTICAL);
        wCond = text("", 30, true);
        wCond.setTypeface(serif);
        wCond.setSingleLine(true);
        wCond.setEllipsize(android.text.TextUtils.TruncateAt.END);
        wSub = text("", 17, false);
        wSub.setAlpha(0.8f);
        wSub.setMaxLines(1);
        wSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        txt.addView(wCond);
        txt.addView(wSub);
        LinearLayout.LayoutParams tl = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.weight = 1f;
        tl.leftMargin = m(16);
        a.addView(txt, tl);
        LinearLayout.LayoutParams al = lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        al.weight = 0.38f;
        col.addView(a, al);

        LinearLayout b = new LinearLayout(host);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(m(12), m(10), m(12), m(12));
        b.setBackground(pressable(m(34), TiggoTheme.tileFill(host)));
        weatherSurfaces.add(b);
        b.setOnClickListener(v -> host.showWeather());
        pressScale(b);
        LinearLayout labels = new LinearLayout(host);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout pills = new LinearLayout(host);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 5; i++) {
            TextView lab = text("", 16, i == 0);
            lab.setGravity(Gravity.CENTER);
            lab.setAlpha(i == 0 ? 1f : 0.8f);
            wHourLabel[i] = lab;
            labels.addView(lab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout pill = new LinearLayout(host);
            pill.setOrientation(LinearLayout.VERTICAL);
            pill.setGravity(Gravity.CENTER);
            GradientDrawable pg = new GradientDrawable();
            pg.setCornerRadius(m(26));
            pg.setColor(i == 0 ? 0xF2FFFFFF : 0x26FFFFFF);
            pill.setBackground(pg);
            ImageView ic = new ImageView(host);
            ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            wHourIcon[i] = ic;
            pill.addView(ic, lp(m(46), m(46)));
            TextView tt = text("--°", 22, true);
            tt.setTypeface(serif);
            tt.setGravity(Gravity.CENTER);
            tt.setTextColor(i == 0 ? 0xFF1B1B22 : Color.WHITE);
            tt.setShadowLayer(0, 0, 0, 0);
            wHourTemp[i] = tt;
            pill.addView(tt, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            pl.leftMargin = m(3);
            pl.rightMargin = m(3);
            pills.addView(pill, pl);
        }
        b.addView(labels, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams pillsLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        pillsLp.weight = 1f;
        pillsLp.topMargin = m(6);
        b.addView(pills, pillsLp);
        LinearLayout.LayoutParams bl = lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        bl.weight = 0.62f;
        bl.topMargin = m(14);
        col.addView(b, bl);
        return col;
    }

    private String precipitationNote(Weather.Data d, int start) {
        for (int i = start; i < Math.min(d.hours.size(), start + 12); i++) {
            Weather.Hour h = d.hours.get(i);
            String kind = null;
            if ((h.code >= 51 && h.code <= 67) || (h.code >= 80 && h.code <= 82)) kind = TiggoText.t("Дождь");
            else if ((h.code >= 71 && h.code <= 77) || h.code == 85 || h.code == 86) kind = TiggoText.t("Снег");
            else if (h.code >= 95) kind = TiggoText.t("Гроза");
            if (kind != null) {
                return kind + TiggoText.t(" ожидается в ") + new SimpleDateFormat("HH:mm", Locale.US).format(new Date(h.time));
            }
        }
        return TiggoText.t("Осадков в ближайшие часы не ожидается");
    }

    private void refreshWeatherBlocks(Weather.Data d, boolean have) {
        if (wBig == null) return;
        String unit = "f".equals(Weather.units(host)) ? "F" : "C";
        wUnitBottom.setText(unit);
        if (!have) {
            wBig.setText("--");
            wCond.setText(TiggoText.t("Погода"));
            wSub.setText(TiggoText.t("Удерживайте — выбрать город"));
            for (int i = 0; i < 5; i++) {
                wHourLabel[i].setText("");
                wHourTemp[i].setText("--°");
                wHourIcon[i].setImageBitmap(Glyphs.get("cloud", m(40), WHITE));
            }
            return;
        }
        wBig.setText(String.valueOf(d.temp));
        wCond.setText(Weather.describe(host, d.code, d.day));
        long now = System.currentTimeMillis();
        int start = 0;
        for (int i = 0; i < d.hours.size(); i++) {
            if (d.hours.get(i).time >= now - 30 * 60 * 1000L) {
                start = i;
                break;
            }
        }
        wSub.setText(precipitationNote(d, start));
        SimpleDateFormat hf = new SimpleDateFormat("HH:mm", Locale.US);
        for (int i = 0; i < 5; i++) {
            int idx = start + i;
            if (i == 0) {
                wHourLabel[0].setText(TiggoText.t("Сейчас"));
                wHourIcon[0].setImageBitmap(WeatherIcons.get(d.code, d.day, m(46)));
                wHourTemp[0].setText(d.temp + "°");
            } else if (idx < d.hours.size()) {
                Weather.Hour h = d.hours.get(idx);
                wHourLabel[i].setText(hf.format(new Date(h.time)));
                wHourIcon[i].setImageBitmap(WeatherIcons.get(h.code, h.day, m(46)));
                wHourTemp[i].setText(h.temp + "°");
            } else {
                wHourLabel[i].setText("");
                wHourTemp[i].setText("");
                wHourIcon[i].setImageDrawable(null);
            }
        }
    }

    // ------------------------------------------------------------------ right column widgets

    private int indexOfId(String id) {
        for (int i = 0; i < WIDGET_IDS.length; i++) if (WIDGET_IDS[i].equals(id)) return i;
        return -1;
    }

    private List<String> widgetOrder() {
        List<String> out = new ArrayList<>();
        String saved = host.prefs.getString(PREF_WIDGETS, "");
        for (String id : saved.split(",")) {
            if (indexOfId(id) >= 0 && !out.contains(id)) out.add(id);
        }
        for (String id : WIDGET_IDS) if (!out.contains(id)) out.add(id);
        return out;
    }

    private void saveWidgetOrder(List<String> order) {
        StringBuilder b = new StringBuilder();
        for (String id : order) {
            if (b.length() > 0) b.append(',');
            b.append(id);
        }
        host.prefs.edit().putString(PREF_WIDGETS, b.toString()).apply();
    }

    private int widgetSize(String id) {
        return Math.max(0, Math.min(2, host.prefs.getInt(PREF_WSIZE + id, 1)));
    }

    private float widgetBase(String id) {
        return "weather".equals(id) ? weatherBase() : WIDGET_BASE[indexOfId(id)];
    }

    private void buildWidgets() {
        if (widgetCol == null) return;
        widgetCol.removeAllViews();
        weatherSurfaces.clear();
        weatherTintShown = 0;
        List<String> order = new ArrayList<>();
        for (String id : widgetOrder()) if (TiggoTheme.widgetVisible(host, id)) order.add(id);
        float avail = 560f;
        float gap = 24f;
        float total = order.isEmpty() ? 0f : gap * (order.size() - 1);
        for (String id : order) total += widgetBase(id) * WIDGET_FACTOR[widgetSize(id)];
        float scale = total > avail ? avail / total : 1f;
        boolean first = true;
        for (final String id : order) {
            View v = "weather".equals(id) ? buildWeatherCard()
                    : ("music".equals(id) ? buildMusicWidget() : buildCapsuleWidget(id));
            v.setOnLongClickListener(x -> {
                openWidgetSheet(id);
                return true;
            });
            LinearLayout.LayoutParams wl = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    m(widgetBase(id) * WIDGET_FACTOR[widgetSize(id)] * scale));
            if (!first) wl.topMargin = m(gap * scale);
            first = false;
            widgetCol.addView(v, wl);
        }
        refreshWeather();
    }

    private MainActivity.AppEntry chosenWidgetApp(String id) {
        String pkgPref = host.prefs.getString(PREF_WAPP_PKG + id, null);  // chosen in the settings
        if (pkgPref != null) {
            MainActivity.AppEntry byPkg = findAnyByPackage(pkgPref);
            if (byPkg != null) return byPkg;
        }
        String key = host.prefs.getString(PREF_WAPP + id, null);
        if (key == null) return null;
        for (MainActivity.AppEntry e : host.all.values()) if (key.equals(e.key)) return e;
        return null;
    }

    private MainActivity.AppEntry findDefaultApp(String[] pkgs, String[] words) {
        for (String pkg : pkgs) {
            MainActivity.AppEntry e = findAnyByPackage(pkg);
            if (e != null) return e;
        }
        for (String word : words) {
            String w = word.toLowerCase(Locale.ROOT);
            for (MainActivity.AppEntry e : host.all.values()) {
                String label = e.label == null ? "" : e.label.toLowerCase(Locale.ROOT);
                String pkg = e.pkg == null ? "" : e.pkg.toLowerCase(Locale.ROOT);
                if (label.contains(w) || pkg.contains(w)) return e;
            }
        }
        return null;
    }

    private View buildCapsuleWidget(final String id) {
        final boolean nav = "nav".equals(id);
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(m(24), m(8), m(22), m(8));
        card.setBackground(pressable(m(34), TiggoTheme.tileFill(host)));

        ImageView icon = new ImageView(host);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        MainActivity.AppEntry chosen = chosenWidgetApp(id);
        Drawable chosenIcon = chosen != null ? iconOf(chosen) : null;
        if (chosenIcon != null) icon.setImageDrawable(chosenIcon);
        else icon.setImageBitmap(nav ? TiggoIcons.navTile(m(80)) : TiggoIcons.musicTile(m(80)));
        card.addView(icon, lp(m(80), m(80)));

        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(text(nav ? TiggoText.t("Навигация") : TiggoText.t("Музыка"), 28, true));
        TextView sub = text(nav ? TiggoText.t("Построить маршрут") : TiggoText.t("Слушать музыку"), 19, false);
        sub.setAlpha(0.85f);
        col.addView(sub);
        LinearLayout.LayoutParams colLp = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.weight = 1f;
        colLp.leftMargin = m(20);
        card.addView(col, colLp);
        card.addView(iconView(TiggoIcons.chevron(m(34), WHITE)), lp(m(36), m(36)));

        card.setOnClickListener(v -> launchWidgetApp(id));
        pressScale(card);
        return card;
    }

    private void launchWidgetApp(String id) {
        MainActivity.AppEntry e = chosenWidgetApp(id);
        if (e == null) {
            e = "nav".equals(id) ? findDefaultApp(NAV_PACKAGES, NAV_WORDS)
                    : findDefaultApp(MUSIC_PACKAGES, MUSIC_WORDS);
        }
        if (e != null) {
            launchApp(this, e);
            return;
        }
        host.toast(TiggoText.t(WIDGET_TITLES[indexOfId(id)]) + TiggoText.t(": приложение не найдено, выберите его"));
        chooseWidgetApp(id);
    }

    private void chooseWidgetApp(final String id) {
        openAppGrid(TiggoText.t("Приложение для «") + TiggoText.t(WIDGET_TITLES[indexOfId(id)]) + "»", e -> {
            host.prefs.edit().putString(PREF_WAPP + id, e.key).remove(PREF_WAPP_PKG + id).apply();
            buildWidgets();
            closeAllApps();
        }, TiggoText.t("По умолчанию"), () -> {
            host.prefs.edit().remove(PREF_WAPP + id).remove(PREF_WAPP_PKG + id).apply();
            buildWidgets();
            closeAllApps();
        });
    }

    /** Long press on a widget: move it up or down and change its size. */
    private void openWidgetSheet(final String id) {
        LinearLayout panel = beginSheet(TiggoText.t(WIDGET_TITLES[indexOfId(id)]), false, 760);
        final List<String> order = widgetOrder();
        final int pos = order.indexOf(id);
        if (pos > 0) {
            panel.addView(sheetRow(TiggoText.t("Поднять выше"), null, v -> {
                Collections.swap(order, pos, pos - 1);
                saveWidgetOrder(order);
                buildWidgets();
                openWidgetSheet(id);
            }));
        }
        if (pos < order.size() - 1) {
            panel.addView(sheetRow(TiggoText.t("Опустить ниже"), null, v -> {
                Collections.swap(order, pos, pos + 1);
                saveWidgetOrder(order);
                buildWidgets();
                openWidgetSheet(id);
            }));
        }
        String[] names = {TiggoText.t("Маленький"), TiggoText.t("Средний"), TiggoText.t("Большой")};
        int current = widgetSize(id);
        for (int i = 0; i < 3; i++) {
            final int size = i;
            panel.addView(sheetRow((i == current ? "✓  " : "") + TiggoText.t("Размер: ") + names[i], null, v -> {
                host.prefs.edit().putInt(PREF_WSIZE + id, size).apply();
                buildWidgets();
                openWidgetSheet(id);
            }));
        }
        if ("weather".equals(id)) {
            panel.addView(sheetRow(TiggoText.t("Город и автоопределение"), null, v -> openWeatherSheet()));
        } else {
            MainActivity.AppEntry chosen = chosenWidgetApp(id);
            panel.addView(sheetRow(TiggoText.t("Выбрать приложение"), chosen != null ? chosen.label : TiggoText.t("По умолчанию"),
                    v -> chooseWidgetApp(id)));
        }
        addDoneButton(panel);
    }

    /** Colour of the weather glass by sky: like the iOS Weather widget. */
    private static int weatherTint(Weather.Data d) {
        if (d == null) return 0x7A2C4A7A;
        int c = d.code;
        if (!d.day) return c >= 51 ? 0x8C1A2033 : 0x8C0E1A3D;            // night: deep navy
        if (c >= 95) return 0x8C3A3550;                                   // thunderstorm
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return 0x80788BA6;  // snow
        if (c >= 51) return 0x8040536E;                                   // rain / drizzle
        if (c >= 45) return 0x80707A86;                                   // fog
        if (c >= 2) return 0x80487BB5;                                    // clouds
        return 0x802A7FE0;                                                // clear sky blue
    }

    private void tintWeather(Weather.Data d, boolean have) {
        int t = weatherTint(have ? d : null);
        if (t == weatherTintShown) return;
        weatherTintShown = t;
        for (View v : weatherSurfaces) v.setBackground(pressableTint(m(34), t));
    }

    /** Pressable glass whose tint is used as is (not lightened). */
    private Drawable pressableTint(int radiusPx, int tint) {
        TiggoGlass g = new TiggoGlass(radiusPx, tint, Math.max(1f, k * 1.6f));
        return new RippleDrawable(ColorStateList.valueOf(0x66FFFFFF), g, rippleMask(radiusPx));
    }

    void refreshWeather() {
        Weather.Data d = Weather.cached(host);
        boolean have = d != null && Weather.enabled(host);
        tintWeather(d, have);
        if (weatherStyle() == 0) {
            refreshWeatherBlocks(d, have);
            updateDate();
            return;
        }
        if (weatherTemp == null) return;
        if (!have) {
            weatherTemp.setText("--°");
            weatherPlace.setText(TiggoText.t("Выбрать город"));
            weatherCondition.setText(TiggoText.t("Удерживайте — настройки"));
            weatherIcon.setImageBitmap(Glyphs.get("cloud", m(70), WHITE));
            updateDate();
            return;
        }
        String unit = "f".equals(Weather.units(host)) ? "°F" : "°C";
        weatherTemp.setText((d.temp > 0 ? "+" : "") + d.temp + unit);
        weatherPlace.setText(d.place == null || d.place.isEmpty() ? TiggoText.t("Моё местоположение") : d.place);
        weatherCondition.setText(Weather.describe(host, d.code, d.day));
        weatherIcon.setImageBitmap(WeatherIcons.get(d.code, d.day, m(96)));
        updateDate();
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
            appStrip.addView(cell, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            homeTiles.add(cell);
        }
        for (int i = count; i < slotCount; i++) {
            appStrip.addView(new View(host), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
    }

    private View buildAppTile(final MainActivity.AppEntry entry, final int index) {
        FrameLayout cell = new FrameLayout(host);
        cell.setClipChildren(false);
        cell.setPadding(0, m(3), 0, m(3));

        LinearLayout tile = new LinearLayout(host);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(m(6), m(8), m(6), m(8));
        tile.setBackground(rippleOnly(m(30)));

        ImageView icon = new ImageView(host);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable packIcon = iconOf(entry);
        if (packIcon != null) icon.setImageDrawable(packIcon);
        float is = iconScale();
        tile.addView(icon, lp(m(68 * is), m(68 * is)));

        TextView label = text(entry.label == null ? "" : entry.label, Math.max(15f, 18f * Math.min(1.15f, is)), false);
        label.setTypeface(Ui.font(host, "medium"));
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = m(5);
        tile.addView(label, labelLp);

        cell.addView(tile, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tile.setOnClickListener(v -> launchApp(v, entry));
        tile.setOnLongClickListener(v -> {
            openHomeTileSheet(index);
            return true;
        });
        pressScale(tile);
        return cell;
    }

    // ------------------------------------------------------------------ bottom dock (swipe to flip)

    private String dockLabel(int slot) {
        return slot >= 0 && slot < DOCK_COUNT ? TiggoText.t(DOCK_LABELS[slot]) : TiggoText.t("Слот ") + (slot + 1);
    }

    /** After a swipe the dock settles on a whole page of apps. */
    private final Runnable snapRunnable = new Runnable() {
        @Override public void run() {
            if (dockScroll == null || dockSlots == null) return;
            int slotW = Math.max(1, m(136 * dockScale()));
            int visible = Math.max(1, dockScroll.getWidth() / slotW);
            int page = visible * slotW;
            int x = dockScroll.getScrollX();
            int max = Math.max(0, dockSlots.getWidth() - dockScroll.getWidth());
            int target = Math.round(x / (float) page) * page;
            target = Math.max(0, Math.min(max, target));
            if (target != x) dockScroll.smoothScrollTo(target, 0);
        }
    };

    private View buildDock() {
        float ds = dockScale();
        LinearLayout dock = new LinearLayout(host);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(m(30 * ds), m(8 * ds), m(34 * ds), m(8 * ds));
        dock.setBackground(glass(m(44 * ds), 0x5AFFFFFF));
        dockView = dock;
        dockTip = null;

        dockScroll = new HorizontalScrollView(host);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dockScroll.setFillViewport(true);
        dockSlots = new LinearLayout(host);
        dockSlots.setOrientation(LinearLayout.HORIZONTAL);
        dockSlots.setGravity(Gravity.CENTER_VERTICAL);
        dockScroll.addView(dockSlots, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dockScroll.setOnScrollChangeListener((v, x, y, ox, oy2) -> {
            hideDockTip();
            dockScroll.removeCallbacks(snapRunnable);
            dockScroll.postDelayed(snapRunnable, 160);
        });
        dock.addView(dockScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        rebuildDockSlots();

        // apps menu: a grey rounded square at the end of the bar
        FrameLayout menu = new FrameLayout(host);
        menu.setBackground(pressable(m(24 * ds), 0x66FFFFFF));
        menu.addView(iconView(Glyphs.get("apps", m(46 * ds), WHITE)), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        menu.setContentDescription(TiggoText.t("Все приложения"));
        menu.setOnClickListener(v -> openAllApps());
        menu.setOnLongClickListener(v -> {
            openSettingsSheet();
            return true;
        });
        pressScale(menu);
        LinearLayout.LayoutParams menuLp = lp(m(88 * ds), m(88 * ds));
        menuLp.leftMargin = m(16 * ds);
        dock.addView(menu, menuLp);
        return dock;
    }

    /** Name of the app above the pressed icon, as a small dark pill. */
    private void showDockTip(View slot, String label) {
        if (uiLayer == null || dockView == null || label == null || label.isEmpty()) return;
        if (dockTip == null) {
            dockTip = text("", 20, false);
            dockTip.setGravity(Gravity.CENTER);
            dockTip.setSingleLine(true);
            dockTip.setPadding(m(16), m(7), m(16), m(7));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xCC2E3338);
            bg.setCornerRadius(m(12));
            bg.setStroke(Math.max(1, m(1)), 0x44FFFFFF);
            dockTip.setBackground(bg);
            uiLayer.addView(dockTip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        }
        dockTip.setText(label);
        dockTip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int tw = dockTip.getMeasuredWidth();
        int th = dockTip.getMeasuredHeight();
        int[] a = new int[2];
        int[] b = new int[2];
        slot.getLocationInWindow(a);
        uiLayer.getLocationInWindow(b);
        int cx = a[0] - b[0] + slot.getWidth() / 2;
        int x = Math.max(m(8), Math.min(cx - tw / 2, Math.max(m(8), uiW - tw - m(8))));
        int y = dockView.getTop() - th - m(8);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dockTip.getLayoutParams();
        lp.leftMargin = x;
        lp.topMargin = y;
        dockTip.setLayoutParams(lp);
        dockTip.setVisibility(VISIBLE);
        dockTip.bringToFront();
    }

    private void hideDockTip() {
        if (dockTip != null) dockTip.setVisibility(GONE);
    }

    private List<MainActivity.AppEntry> sortedApps() {
        List<MainActivity.AppEntry> list = new ArrayList<>(host.all.values());
        Collections.sort(list, (a, b) -> {
            String x = a.label == null ? "" : a.label;
            String y = b.label == null ? "" : b.label;
            return x.compareToIgnoreCase(y);
        });
        return list;
    }

    /** Eight shortcuts, then the remaining apps on further swipe pages. */
    private void rebuildDockSlots() {
        if (dockSlots == null) return;
        Set<String> used = new HashSet<>();
        for (MainActivity.AppEntry e : homeApps) if (e.key != null) used.add(e.key);
        for (int i = 0; i < DOCK_COUNT; i++) {
            MainActivity.AppEntry a = chosenDockApp(i);
            if (a == null) a = defaultDockApp(i);
            if (a != null && a.key != null) used.add(a.key);
        }
        for (String id : new String[]{"nav", "music"}) {
            MainActivity.AppEntry a = chosenWidgetApp(id);
            if (a == null) {
                a = "nav".equals(id) ? findDefaultApp(NAV_PACKAGES, NAV_WORDS)
                        : findDefaultApp(MUSIC_PACKAGES, MUSIC_WORDS);
            }
            if (a != null && a.key != null) used.add(a.key);
        }
        List<MainActivity.AppEntry> pool = new ArrayList<>();
        for (MainActivity.AppEntry e : sortedApps()) {
            if (e.key != null && !used.contains(e.key)) pool.add(e);
            if (pool.size() >= DOCK_EXTRA_MAX) break;
        }
        dockPool = pool;
        dockSlots.removeAllViews();
        int total = DOCK_COUNT + pool.size();
        for (int i = 0; i < total; i++) {
            if (i != 0 && dockShown(i) == null) continue;  // only real apps, no empty placeholders
            dockSlots.addView(buildDockSlot(i), new LinearLayout.LayoutParams(m(136 * dockScale()),
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (dockScroll != null) dockScroll.scrollTo(0, 0);
    }

    private MainActivity.AppEntry dockShown(int slot) {
        MainActivity.AppEntry chosen = chosenDockApp(slot);
        if (chosen != null) return chosen;
        if (slot < DOCK_COUNT) return defaultDockApp(slot);
        int idx = slot - DOCK_COUNT;
        return idx >= 0 && idx < dockPool.size() ? dockPool.get(idx) : null;
    }

    private View buildDockSlot(final int i) {
        LinearLayout slot = new LinearLayout(host);
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setGravity(Gravity.CENTER);
        MainActivity.AppEntry chosen = chosenDockApp(i);
        MainActivity.AppEntry shown = dockShown(i);
        final String labelText = chosen != null && chosen.label != null ? chosen.label
                : (shown != null && shown.label != null && i >= DOCK_COUNT ? shown.label : dockLabel(i));

        final float ds = dockScale();
        final FrameLayout tile = new FrameLayout(host);
        tile.setBackground(rippleOnly(m(26 * ds)));
        ImageView icon = new ImageView(host);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable d = shown != null ? iconOf(shown) : null;
        if (d != null) icon.setImageDrawable(d);
        else icon.setImageBitmap(TiggoIcons.carTile(m(86 * ds)));
        tile.addView(icon, new FrameLayout.LayoutParams(m(86 * ds), m(86 * ds), Gravity.CENTER));
        slot.addView(tile, lp(m(98 * ds), m(98 * ds)));

        // while pressed: white ring around the icon and its name above (like the reference)
        final GradientDrawable ring = new GradientDrawable();
        ring.setColor(0x00000000);
        ring.setCornerRadius(m(26 * ds));
        ring.setStroke(Math.max(2, m(3)), 0xFFFFFFFF);
        slot.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tile.setBackground(ring);
                    showDockTip(tile, labelText);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    tile.setBackground(rippleOnly(m(26 * ds)));
                    hideDockTip();
                    break;
                default:
                    break;
            }
            return false;
        });
        slot.setOnClickListener(v -> launchDockSlot(i));
        slot.setOnLongClickListener(v -> {
            hideDockTip();
            openDockSlotSheet(i);
            return true;
        });
        slot.setContentDescription(labelText);
        return slot;
    }

    private MainActivity.AppEntry chosenDockApp(int slot) {
        String key = host.prefs.getString(PREF_DOCK + slot, null);
        if (key == null) return null;
        for (MainActivity.AppEntry e : host.all.values()) if (key.equals(e.key)) return e;
        return null;
    }

    private MainActivity.AppEntry defaultDockApp(int slot) {
        if (slot < 0 || slot >= DOCK_PACKAGES.length) return null;
        if (DOCK_PACKAGES[slot].length == 0 && DOCK_WORDS[slot].length == 0) return null;
        return findDefaultApp(DOCK_PACKAGES[slot], DOCK_WORDS[slot]);
    }

    private void launchDockSlot(int slot) {
        MainActivity.AppEntry e = dockShown(slot);
        if (e != null) {
            launchApp(this, e);
            return;
        }
        if (slot == 0) {
            openControlCenter();
            return;
        }
        host.toast(dockLabel(slot) + TiggoText.t(": приложение не найдено, выберите его"));
        chooseDockApp(slot);
    }

    private void chooseDockApp(final int slot) {
        openAppGrid(TiggoText.t("Приложение для «") + dockLabel(slot) + "»", e -> {
            host.prefs.edit().putString(PREF_DOCK + slot, e.key).apply();
            rebuildDockSlots();
            closeAllApps();
        }, TiggoText.t("По умолчанию"), () -> {
            host.prefs.edit().remove(PREF_DOCK + slot).apply();
            rebuildDockSlots();
            closeAllApps();
        });
    }

    private void openDockSlotSheet(final int slot) {
        LinearLayout panel = beginSheet(dockLabel(slot), false, 760);
        final MainActivity.AppEntry shownApp = dockShown(slot);
        if (shownApp != null) {
            panel.addView(sheetRow(TiggoText.t("Открыть в окне"), TiggoText.t("Экран в экране"), v -> {
                closeSheet();
                launchWindowed(shownApp);
            }));
        }
        panel.addView(sheetRow(TiggoText.t("Заменить приложение"), null, v -> chooseDockApp(slot)));
        if (chosenDockApp(slot) != null) {
            panel.addView(sheetRow(TiggoText.t("Сбросить на значение по умолчанию"), null, v -> {
                host.prefs.edit().remove(PREF_DOCK + slot).apply();
                rebuildDockSlots();
                closeSheet();
            }));
        }
        addDoneButton(panel);
    }

    private void openByKeywords(String... words) {
        for (MainActivity.AppEntry e : host.all.values()) {
            String label = e.label == null ? "" : e.label.toLowerCase(Locale.ROOT);
            String pkg = e.pkg == null ? "" : e.pkg.toLowerCase(Locale.ROOT);
            for (String word : words) {
                String w = word.toLowerCase(Locale.ROOT);
                if (label.contains(w) || pkg.contains(w)) {
                    launchApp(this, e);
                    return;
                }
            }
        }
        host.toast(TiggoText.t("Подходящее приложение не найдено"));
        openAllApps();
    }

    private void openPreferred(String keyword, String... pkgs) {
        for (String pkg : pkgs) {
            MainActivity.AppEntry e = findAnyByPackage(pkg);
            if (e != null) {
                launchApp(this, e);
                return;
            }
        }
        openByKeywords(keyword);
    }

    // ------------------------------------------------------------------ all apps / app chooser

    private void openAllApps() {
        openAppGrid(TiggoText.t("Все приложения"), null, null, null);
    }

    private void openAppGrid(final String headingText, final Pick pick, final String resetLabel,
                             final Runnable resetAction) {
        closeSheet();
        allAppsLayer.removeAllViews();
        allAppsLayer.setBackground(TiggoGlass.fullScreenBlur(host.getResources(), 0x26000000));
        allAppsLayer.setOnClickListener(v -> closeAllApps());
        final DisplayMetrics dm = host.getResources().getDisplayMetrics();
        int panelW = Math.min(Math.round(dm.widthPixels * 0.68f), m(1180));
        int panelH = Math.min(Math.round(dm.heightPixels * 0.86f), m(820));
        final FrameLayout panel = new FrameLayout(host);
        panel.setBackground(heavyGlass(m(40), 0x4D1C1C26));  // iOS App Library: dark glass
        panel.setClickable(true);
        panel.setElevation(m(14));
        drawerPanel = panel;

        final LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(m(26), m(26), m(26), m(100));
        ScrollView scroll = new ScrollView(host);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        panel.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final List<MainActivity.AppEntry> entries = new ArrayList<>(host.all.values());
        Collections.sort(entries, (a, b) -> {
            String x = a.label == null ? "" : a.label;
            String y = b.label == null ? "" : b.label;
            return x.compareToIgnoreCase(y);
        });
        final int inner = panelW - 2 * m(26);
        final int columns = Math.max(5, Math.min(8, inner / Math.max(1, m(118))));
        fillDrawer(content, entries, "", pick, headingText, resetLabel, resetAction, columns);

        final EditText search = new EditText(host);
        search.setHint(TiggoText.t("Поиск"));
        search.setHintTextColor(0xB3EBEBF5);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * k);
        search.setTypeface(Ui.font(host, "regular"));
        search.setPadding(m(20), 0, m(20), 0);
        search.setBackground(heavyGlass(m(26), 0x40767680));
        search.setCompoundDrawablesWithIntrinsicBounds(
                new BitmapDrawable(host.getResources(), TiggoIcons.search(m(26), 0xB3EBEBF5)), null, null, null);
        search.setCompoundDrawablePadding(m(10));
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override public void afterTextChanged(android.text.Editable e) {
                fillDrawer(content, entries, e.toString(), pick, headingText, resetLabel, resetAction, columns);
            }
        });
        search.setOnFocusChangeListener((v, has) ->
                panel.animate().translationY(has ? -dm.heightPixels * 0.26f : 0f).setDuration(180).start());
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(m(340), m(52),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sp.bottomMargin = m(22);
        panel.addView(search, sp);

        allAppsLayer.addView(panel, new FrameLayout.LayoutParams(panelW, panelH, Gravity.CENTER));
        allAppsLayer.setVisibility(VISIBLE);
        allAppsOpen = true;
    }

    private void fillDrawer(LinearLayout content, List<MainActivity.AppEntry> entries, String query,
                            final Pick pick, String headingText, String resetLabel, final Runnable resetAction,
                            int columns) {
        content.removeAllViews();
        if (pick != null) {
            LinearLayout head = new LinearLayout(host);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text(headingText, 26, true);
            title.setTextColor(Color.WHITE);
            title.setShadowLayer(0, 0, 0, 0);
            head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (resetLabel != null && resetAction != null) {
                TextView reset = text(resetLabel, 19, true);
                reset.setGravity(Gravity.CENTER);
                reset.setTextColor(0xFF0A84FF);
                reset.setShadowLayer(0, 0, 0, 0);
                reset.setOnClickListener(v -> resetAction.run());
                head.addView(reset, lp(m(220), m(48)));
            }
            LinearLayout.LayoutParams hl = lp(ViewGroup.LayoutParams.MATCH_PARENT, m(56));
            hl.bottomMargin = m(12);
            content.addView(head, hl);
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<MainActivity.AppEntry> shown = new ArrayList<>();
        for (MainActivity.AppEntry e : entries) {
            String label = e.label == null ? "" : e.label.toLowerCase(Locale.ROOT);
            if (q.isEmpty() || label.contains(q)) shown.add(e);
        }
        for (int i = 0; i < shown.size(); i += columns) {
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < i + columns; j++) {
                if (j >= shown.size()) {
                    row.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1f));
                    continue;
                }
                final MainActivity.AppEntry entry = shown.get(j);
                LinearLayout tile = new LinearLayout(host);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER_HORIZONTAL);
                tile.setPadding(m(4), m(10), m(4), m(10));
                GradientDrawable rm = new GradientDrawable();
                rm.setColor(Color.WHITE);
                rm.setCornerRadius(m(20));
                tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, rm));
                ImageView iv = new ImageView(host);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Drawable ic = iconOf(entry);
                if (ic != null) iv.setImageDrawable(ic);
                tile.addView(iv, lp(m(66), m(66)));
                TextView name = text(entry.label == null ? "" : entry.label, 15, false);
                name.setTextColor(Color.WHITE);
                name.setShadowLayer(0, 0, 0, 0);
                name.setGravity(Gravity.CENTER);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nameLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                nameLp.topMargin = m(6);
                tile.addView(name, nameLp);
                tile.setOnClickListener(v -> {
                    if (pick != null) {
                        pick.on(entry);
                    } else {
                        closeAllApps();
                        launchApp(v, entry);
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
            content.addView(row);
        }
    }

    void refreshApps() {
        rebuildHomeApps();
        rebuildDockSlots();
        buildWidgets();
        scheduleIconPack();
    }

    void closeAllApps() {
        closeControlCenter();
        closePromo();
        closeSheet();
        allAppsLayer.setVisibility(GONE);
        allAppsLayer.removeAllViews();
        allAppsOpen = false;
    }

    boolean isAllAppsOpen() {
        return allAppsOpen || sheetOpen || promoOpen || ccOpen;
    }

    // ------------------------------------------------------------------ Control Center (iOS style)

    private interface FloatPick {
        void on(float value, boolean done);
    }

    /** iOS vertical slider (brightness / volume): white fill rising from the bottom, icon at the foot. */
    private final class VSlider extends View {
        float value;
        final Bitmap iconLight;
        final Bitmap iconDark;
        final FloatPick pick;
        final android.graphics.Paint fillPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Path clip = new android.graphics.Path();
        final android.graphics.RectF box = new android.graphics.RectF();

        VSlider(Bitmap light, Bitmap dark, float start, FloatPick pick) {
            super(host);
            this.iconLight = light;
            this.iconDark = dark;
            this.value = Math.max(0f, Math.min(1f, start));
            this.pick = pick;
            fillPaint.setColor(0xF2FFFFFF);
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            int w = getWidth();
            int h = getHeight();
            float r = Math.min(w, h) * 0.32f;
            box.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(box, r, r, android.graphics.Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            float top = h * (1f - value);
            c.drawRect(0, top, w, h, fillPaint);
            c.restore();
            Bitmap ic = top < h - m(18) - (iconLight == null ? 0 : iconLight.getHeight()) / 2f ? iconDark : iconLight;
            if (ic != null) c.drawBitmap(ic, (w - ic.getWidth()) / 2f, h - ic.getHeight() - w * 0.22f, null);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int h = Math.max(1, getHeight());
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    animate().scaleX(1.04f).scaleY(1.02f).setDuration(120).start();
                    // fall through
                case MotionEvent.ACTION_MOVE:
                    value = Math.max(0f, Math.min(1f, 1f - e.getY() / h));
                    invalidate();
                    pick.on(value, false);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    pick.on(value, true);
                    return true;
                default:
                    return true;
            }
        }
    }

    private TextView ccMediaTitle;
    private TextView ccMediaArtist;
    private ImageView ccMediaArt;
    private ImageView ccMediaPlay;
    private final View[] ccConn = new View[4];

    private final Runnable ccTicker = new Runnable() {
        @Override public void run() {
            if (!ccOpen) return;
            refreshControlCenter();
            postDelayed(this, 1500);
        }
    };

    boolean isControlCenterOpen() {
        return ccOpen;
    }

    void closeControlCenter() {
        if (ccLayer == null || !ccOpen) return;
        ccOpen = false;
        removeCallbacks(ccTicker);
        final View content = ccLayer.getChildCount() > 0 ? ccLayer.getChildAt(0) : null;
        if (content != null) {
            content.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(160).start();
        }
        ccLayer.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            ccLayer.setVisibility(GONE);
            ccLayer.removeAllViews();
        }).start();
    }

    /** iOS Control Center: connectivity, Now Playing, brightness and volume, quick actions. */
    void openControlCenter() {
        if (ccLayer == null) return;
        closeSheet();
        if (allAppsOpen) {
            allAppsLayer.setVisibility(GONE);
            allAppsLayer.removeAllViews();
            allAppsOpen = false;
        }
        refreshGlass();
        ccLayer.removeAllViews();
        ccLayer.setBackground(TiggoGlass.fullScreenBlur(host.getResources(), 0x47000000));
        ccLayer.setOnClickListener(v -> closeControlCenter());

        int W = getWidth() > 0 ? getWidth() : uiW;
        int H = getHeight() > 0 ? getHeight() : uiH;
        int gap = m(20);
        int u = Math.min(m(178), Math.min((Math.round(W * 0.72f) - 5 * gap) / 6, (H - m(190) - 2 * gap) / 3));
        u = Math.max(m(90), u);
        int gridW = 6 * u + 5 * gap;
        int gridH = 3 * u + 2 * gap;
        int left = (W - gridW) / 2;
        int top = Math.max(m(110), (H - gridH) / 2 + m(30));

        FrameLayout root = new FrameLayout(host);
        root.setClipChildren(false);

        // header: time and date on the left, status on the right (like the top of iOS Control Center)
        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        TextClock hc = new TextClock(host);
        hc.setFormat24Hour("HH:mm");
        hc.setFormat12Hour("HH:mm");
        hc.setTextColor(WHITE);
        hc.setTypeface(Ui.font(host, "display"));
        hc.setTextSize(TypedValue.COMPLEX_UNIT_PX, 56 * k);
        hc.setIncludeFontPadding(false);
        head.addView(hc);
        TextView hd = text(dateLine(), 26, true);
        hd.setAlpha(0.85f);
        LinearLayout.LayoutParams hdl = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        hdl.weight = 1f;
        hdl.leftMargin = m(18);
        hdl.bottomMargin = m(6);
        head.addView(hd, hdl);
        TextView edit = text(TiggoText.t("Готово"), 26, true);
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(m(26), 0, m(26), 0);
        edit.setBackground(pressable(m(30), 0x55FFFFFF));
        edit.setOnClickListener(v -> closeControlCenter());
        head.addView(edit, lp(ViewGroup.LayoutParams.WRAP_CONTENT, m(60)));
        root.addView(head, pos(gridW, m(70), left, top - m(96)));

        root.addView(ccConnectivity(u, gap), pos(2 * u + gap, 2 * u + gap, left, top));
        root.addView(ccMedia(u), pos(2 * u + gap, 2 * u + gap, left + 2 * (u + gap), top));
        root.addView(ccBrightness(), pos(u, 2 * u + gap, left + 4 * (u + gap), top));
        root.addView(ccVolume(), pos(u, 2 * u + gap, left + 5 * (u + gap), top));

        int rowY = top + 2 * (u + gap);
        root.addView(ccTile(TiggoIcons.palette(m(54), WHITE), themeShortName(), v -> cycleTheme()),
                pos(u, u, left, rowY));
        root.addView(ccTile(TiggoIcons.monitor(m(54), WHITE), TiggoText.t("Обои"), v -> {
            closeControlCenter();
            openWallpaperSheet();
        }), pos(u, u, left + (u + gap), rowY));
        root.addView(ccTile(TiggoIcons.navigation(m(54), WHITE), TiggoText.t("Навигация"), v -> {
            closeControlCenter();
            launchWidgetApp("nav");
        }), pos(u, u, left + 2 * (u + gap), rowY));
        root.addView(ccTile(Glyphs.get("cloud", m(54), WHITE), weatherShort(), v -> {
            closeControlCenter();
            host.showWeather();
        }), pos(u, u, left + 3 * (u + gap), rowY));
        root.addView(ccTile(Glyphs.get("apps", m(50), WHITE), TiggoText.t("Приложения"), v -> {
            closeControlCenter();
            openAllApps();
        }), pos(u, u, left + 4 * (u + gap), rowY));
        root.addView(ccTile(Glyphs.get("gear", m(54), WHITE), TiggoText.t("Настройки"), v -> {
            closeControlCenter();
            openTiggoSettings();
        }), pos(u, u, left + 5 * (u + gap), rowY));

        ccLayer.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ccLayer.setAlpha(0f);
        ccLayer.setVisibility(VISIBLE);
        ccLayer.bringToFront();
        ccLayer.animate().alpha(1f).setDuration(200).start();
        root.setAlpha(0f);
        root.setScaleX(1.06f);
        root.setScaleY(1.06f);
        root.setTranslationY(-m(30));
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        ccOpen = true;
        refreshControlCenter();
        removeCallbacks(ccTicker);
        postDelayed(ccTicker, 1500);
    }

    private String dateLine() {
        try {
            Locale ru = new Locale("ru");
            String raw = new SimpleDateFormat("EEEE, d MMMM", ru).format(new Date());
            return raw.isEmpty() ? raw : raw.substring(0, 1).toUpperCase(ru) + raw.substring(1);
        } catch (Throwable t) {
            return "";
        }
    }

    private String themeShortName() {
        int t = TiggoTheme.chosenTheme(host);
        return TiggoText.t(TiggoTheme.THEME_NAMES[t]).replace(" (по солнцу)", "").replace(" (by the sun)", "");
    }

    private String weatherShort() {
        Weather.Data d = Weather.enabled(host) ? Weather.cached(host) : null;
        if (d == null) return TiggoText.t("Погода");
        return (d.temp > 0 ? "+" : "") + d.temp + "°";
    }

    /** Day -> Dawn -> Night -> Auto -> Day... (the themes of v3), applied at once. */
    private void cycleTheme() {
        int t = TiggoTheme.chosenTheme(host);
        int next = t < TiggoTheme.THEME_DAY || t >= TiggoTheme.THEME_AUTO ? TiggoTheme.THEME_DAY : t + 1;
        TiggoTheme.put(host, TiggoTheme.K_THEME, next);
        host.toast(TiggoText.t("Тема: ") + TiggoText.t(TiggoTheme.THEME_NAMES[next]));
        closeControlCenter();
        postDelayed(this::refreshStyleIfChanged, 220);
    }

    private View ccTile(Bitmap icon, String label, View.OnClickListener click) {
        LinearLayout t = new LinearLayout(host);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(m(8), m(10), m(8), m(10));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF),
                heavyGlass(m(40), 0x33FFFFFF), rippleMask(m(40))));
        t.addView(iconView(icon), lp(m(60), m(60)));
        TextView l = text(label, 19, true);
        l.setGravity(Gravity.CENTER);
        l.setSingleLine(true);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        l.setShadowLayer(0, 0, 0, 0);
        LinearLayout.LayoutParams ll = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.topMargin = m(10);
        t.addView(l, ll);
        t.setOnClickListener(click);
        pressScale(t);
        return t;
    }

    private View ccRound(Bitmap icon, View.OnClickListener click, View.OnLongClickListener longClick) {
        FrameLayout f = new FrameLayout(host);
        f.addView(iconView(icon), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.setOnClickListener(click);
        f.setOnLongClickListener(longClick);
        pressScale(f);
        return f;
    }

    private static void setRoundOn(View v, boolean on, int onColor) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? onColor : 0x40FFFFFF);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), g, null));
    }

    private View ccConnectivity(int u, int gap) {
        FrameLayout box = new FrameLayout(host);
        box.setBackground(heavyGlass(m(48), 0x33FFFFFF));
        box.setClickable(true);
        int cell = (2 * u + gap) / 2;
        int d = Math.round(cell * 0.62f);
        int pad = (cell - d) / 2;
        Bitmap[] icons = {
                TiggoIcons.vpnShield(Math.round(d * 0.56f), true),
                Glyphs.get("wifi", Math.round(d * 0.5f), WHITE),
                TiggoIcons.bluetooth(Math.round(d * 0.5f), WHITE),
                TiggoIcons.bars(Math.round(d * 0.5f), 4, WHITE)};
        final String[] actions = {Settings.ACTION_VPN_SETTINGS, Settings.ACTION_WIFI_SETTINGS,
                Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            View b = ccRound(icons[i], v -> ccToggle(idx, actions[idx]), v -> {
                closeControlCenter();
                openSettings(actions[idx]);
                return true;
            });
            ccConn[i] = b;
            int col = i % 2;
            int row = i / 2;
            box.addView(b, pos(d, d, col * cell + pad + (col == 0 ? pad / 3 : -pad / 3),
                    row * cell + pad + (row == 0 ? pad / 3 : -pad / 3)));
        }
        return box;
    }

    @SuppressWarnings({"deprecation", "MissingPermission"})
    private void ccToggle(int idx, String action) {
        boolean done = false;
        try {
            if (idx == 1) {
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                        host.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) done = wm.setWifiEnabled(!wm.isWifiEnabled());
            } else if (idx == 2) {
                BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                if (a != null) done = a.isEnabled() ? a.disable() : a.enable();
            }
        } catch (Throwable ignored) {
            done = false;
        }
        if (done) {
            postDelayed(this::refreshControlCenter, 600);
            postDelayed(this::refreshControlCenter, 2000);
            return;
        }
        // Android does not let apps switch it directly: open the system panel instead
        closeControlCenter();
        if (idx == 1 && android.os.Build.VERSION.SDK_INT >= 29) {
            openSettings(Settings.Panel.ACTION_WIFI);
        } else if (idx == 3 && android.os.Build.VERSION.SDK_INT >= 29) {
            openSettings(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
        } else {
            openSettings(action);
        }
    }

    private View ccMedia(int u) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(m(24), m(22), m(24), m(14));
        card.setBackground(heavyGlass(m(48), 0x33FFFFFF));
        card.setClickable(true);
        LinearLayout top = new LinearLayout(host);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ccMediaArt = new ImageView(host);
        ccMediaArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ab = new GradientDrawable();
        ab.setColor(0x33FFFFFF);
        ab.setCornerRadius(m(18));
        ccMediaArt.setBackground(ab);
        ccMediaArt.setClipToOutline(true);
        top.addView(ccMediaArt, lp(m(92), m(92)));
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);
        ccMediaTitle = text(TiggoText.t("Не исполняется"), 26, true);
        ccMediaTitle.setSingleLine(true);
        ccMediaTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ccMediaArtist = text("", 21, false);
        ccMediaArtist.setAlpha(0.7f);
        ccMediaArtist.setSingleLine(true);
        ccMediaArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(ccMediaTitle);
        LinearLayout.LayoutParams al = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.topMargin = m(6);
        col.addView(ccMediaArtist, al);
        LinearLayout.LayoutParams cl = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.weight = 1f;
        cl.leftMargin = m(18);
        top.addView(col, cl);
        top.setOnClickListener(v -> {
            closeControlCenter();
            if (!MediaWatchService.granted(host)) openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            else musicOpenApp();
        });
        card.addView(top, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout ctl = new LinearLayout(host);
        ctl.setOrientation(LinearLayout.HORIZONTAL);
        ctl.setGravity(Gravity.CENTER);
        ctl.addView(centered(controlButton(TiggoIcons.skipPrev(m(46), WHITE), m(76), v -> {
            musicSkip(false);
            postDelayed(this::refreshControlCenter, 500);
        })), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ccMediaPlay = controlButton(TiggoIcons.play(m(56), WHITE), m(88), v -> {
            musicPlayPause();
            postDelayed(this::refreshControlCenter, 450);
        });
        ctl.addView(centered(ccMediaPlay), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ctl.addView(centered(controlButton(TiggoIcons.skipNext(m(46), WHITE), m(76), v -> {
            musicSkip(true);
            postDelayed(this::refreshControlCenter, 500);
        })), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams ctlLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        ctlLp.weight = 1f;
        card.addView(ctl, ctlLp);
        return card;
    }

    private View ccBrightness() {
        float start = 0.6f;
        try {
            start = Settings.System.getInt(host.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
        } catch (Throwable ignored) {
            // unknown: keep the middle
        }
        VSlider s = new VSlider(TiggoIcons.sun(m(44), WHITE), TiggoIcons.sun(m(44), 0xFF8E8E93), start,
                (v, done) -> setBrightness(v, done));
        s.setBackground(heavyGlass(m(48), 0x33FFFFFF));
        return s;
    }

    private View ccVolume() {
        float start = 0.5f;
        try {
            AudioManager am = (AudioManager) host.getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            start = max > 0 ? am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max : 0.5f;
        } catch (Throwable ignored) {
            // keep the middle
        }
        VSlider s = new VSlider(TiggoIcons.volumeColored(m(44), WHITE, WHITE),
                TiggoIcons.volumeColored(m(44), 0xFF8E8E93, 0xFF8E8E93), start, (v, done) -> {
            try {
                AudioManager am = (AudioManager) host.getSystemService(Context.AUDIO_SERVICE);
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max), 0);
            } catch (Throwable ignored) {
                // volume fixed by the system
            }
        });
        s.setBackground(heavyGlass(m(48), 0x33FFFFFF));
        return s;
    }

    private void setBrightness(float v, boolean done) {
        float b = Math.max(0.03f, v);
        try {
            android.view.WindowManager.LayoutParams a = host.getWindow().getAttributes();
            a.screenBrightness = b;
            host.getWindow().setAttributes(a);
        } catch (Throwable ignored) {
            // no window yet
        }
        if (!done) return;
        try {
            if (Settings.System.canWrite(host)) {
                Settings.System.putInt(host.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(host.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                        Math.round(b * 255));
                android.view.WindowManager.LayoutParams a = host.getWindow().getAttributes();
                a.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                host.getWindow().setAttributes(a);
            } else if (!host.prefs.getBoolean("tiggo9_asked_write_settings", false)) {
                host.prefs.edit().putBoolean("tiggo9_asked_write_settings", true).apply();
                host.toast(TiggoText.t("Разрешите «Изменение системных настроек», чтобы менять яркость всего экрана"));
                host.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + host.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (Throwable ignored) {
            // the window brightness above still applies on the home screen
        }
    }

    private void refreshControlCenter() {
        if (!ccOpen) return;
        refreshStatus();
        boolean bt = false;
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            bt = a != null && a.isEnabled();
        } catch (Throwable ignored) {
            // no permission
        }
        boolean wifiOn = lastWifi;
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    host.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) wifiOn = wm.isWifiEnabled() || lastWifi;
        } catch (Throwable ignored) {
            // keep the network state
        }
        if (ccConn[0] != null) setRoundOn(ccConn[0], lastVpn, 0xFF0A84FF);
        if (ccConn[1] != null) setRoundOn(ccConn[1], wifiOn, 0xFF0A84FF);
        if (ccConn[2] != null) setRoundOn(ccConn[2], bt, 0xFF0A84FF);
        if (ccConn[3] != null) setRoundOn(ccConn[3], lastCell, 0xFF30D158);

        if (ccMediaTitle == null) return;
        MediaController c = activeController();
        if (c == null) {
            MainActivity.AppEntry app = musicApp();
            ccMediaTitle.setText(TiggoText.t("Не исполняется"));
            ccMediaArtist.setText(app != null && app.label != null ? app.label : "");
            Drawable d = app != null ? iconOf(app) : null;
            if (d != null) ccMediaArt.setImageDrawable(d);
            else ccMediaArt.setImageBitmap(TiggoIcons.musicTile(m(92)));
            ccMediaPlay.setImageBitmap(TiggoIcons.play(m(56), WHITE));
            return;
        }
        MediaMetadata md = c.getMetadata();
        PlaybackState st = c.getPlaybackState();
        String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        Bitmap art = null;
        if (md != null) {
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        ccMediaTitle.setText(title != null && !title.isEmpty() ? title : TiggoText.t("Музыка"));
        ccMediaArtist.setText(artist != null ? artist : "");
        if (art != null) ccMediaArt.setImageBitmap(art);
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        ccMediaPlay.setImageBitmap(playing ? TiggoIcons.pause(m(56), WHITE) : TiggoIcons.play(m(56), WHITE));
    }

    // swipe down from the top edge (right half) opens the Control Center, like on the iPhone
    private float ccDownX;
    private float ccDownY;
    private boolean ccTracking;

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ccDownX = e.getX();
                ccDownY = e.getY();
                ccTracking = !ccOpen && !allAppsOpen && !sheetOpen && !promoOpen && e.getY() < m(70);
                break;
            case MotionEvent.ACTION_MOVE:
                if (ccTracking && e.getY() - ccDownY > m(90) && Math.abs(e.getX() - ccDownX) < m(160)) {
                    ccTracking = false;
                    openControlCenter();
                    return true;
                }
                break;
            default:
                ccTracking = false;
                break;
        }
        return super.onInterceptTouchEvent(e);
    }

    // ------------------------------------------------------------------ promo video (long press on the logo)

    private void openPromo() {
        if (promoOpen) return;
        new Thread(() -> {
            final File user = new File(host.getFilesDir(), "tiggo9_promo_user.mp4");
            if (user.exists() && user.length() > 1000) {
                host.runOnUiThread(() -> showPromo(user));
                return;
            }
            final File f = new File(host.getCacheDir(), "tiggo9_promo.mp4");
            try {
                if (!f.exists() || f.length() < 1000) {
                    try (InputStream in = host.getAssets().open("backgrounds/tiggo9_promo.mp4");
                         FileOutputStream out = new FileOutputStream(f)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                }
            } catch (Throwable t) {
                f.delete();
                host.runOnUiThread(() -> host.toast(TiggoText.t("Видео не найдено. Настройки → О лаунчере → выбрать видео")));
                return;
            }
            host.runOnUiThread(() -> showPromo(f));
        }).start();
    }

    private void showPromo(File f) {
        if (promoOpen) return;
        closeAllApps();
        promoLayer = new FrameLayout(host);
        promoLayer.setBackgroundColor(Color.BLACK);
        promoLayer.setClickable(true);
        promoVideo = new VideoView(host);
        promoLayer.addView(promoVideo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        TextView close = text("✕", 32, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(pressable(m(32), 0x66000000));
        close.setOnClickListener(v -> closePromo());
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(m(64), m(64), Gravity.TOP | Gravity.END);
        cl.topMargin = m(24);
        cl.rightMargin = m(24);
        promoLayer.addView(close, cl);
        promoLayer.setOnClickListener(v -> closePromo());
        promoVideo.setOnCompletionListener(mp -> closePromo());
        promoVideo.setOnErrorListener((mp, what, extra) -> {
            host.toast(TiggoText.t("Не удалось воспроизвести видео"));
            closePromo();
            return true;
        });
        promoVideo.setOnPreparedListener(mp -> promoVideo.start());
        promoVideo.setVideoPath(f.getAbsolutePath());
        addView(promoLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        promoOpen = true;
    }

    private void closePromo() {
        if (!promoOpen) return;
        promoOpen = false;
        try {
            if (promoVideo != null) promoVideo.stopPlayback();
        } catch (Throwable ignored) {
            // already stopped
        }
        if (promoLayer != null) removeView(promoLayer);
        promoLayer = null;
        promoVideo = null;
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
        sheetLayer.setBackground(TiggoGlass.fullScreenBlur(host.getResources(), 0x33000000));
        sheetLayer.setOnClickListener(v -> closeSheet());
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(m(32), m(26), m(32), m(28));
        panel.setBackground(heavyGlass(m(38), 0x5C1C1C26));
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
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF),
                heavyGlass(m(24), 0x24FFFFFF), rippleMask(m(24))));
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
        TextView done = text(TiggoText.t("Готово"), 26, true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(solid(m(24), 0xFF0A84FF));
        done.setOnClickListener(v -> closeSheet());
        pressScale(done);
        LinearLayout.LayoutParams lp = lp(ViewGroup.LayoutParams.MATCH_PARENT, m(72));
        lp.topMargin = m(18);
        panel.addView(done, lp);
    }

    private void openSettingsSheet() {
        LinearLayout panel = beginSheet(TiggoText.t("Настроить главный экран"), false, 820);
        panel.addView(sheetRow(TiggoText.t("Обои"), TiggoText.t("Авто по сезону и времени суток, фото, градиенты, своё изображение"),
                v -> openWallpaperSheet()));
        panel.addView(sheetRow(TiggoText.t("Погода"), TiggoText.t("Автоопределение по местоположению или свой город"),
                v -> openWeatherSheet()));
        panel.addView(sheetRow(TiggoText.t("Нижняя панель"), TiggoText.t("Восемь приложений в доке"), v -> openDockSheet()));
        panel.addView(sheetRow(TiggoText.t("Виджеты"), TiggoText.t("Удерживайте виджет, чтобы переместить или изменить размер"),
                v -> openWidgetsResetSheet()));
        boolean glassOn = host.prefs.getBoolean(PREF_TILE_GLASS, true);
        panel.addView(sheetRow(TiggoText.t("Стеклянная подложка под иконками: ") + (glassOn ? TiggoText.t("вкл") : TiggoText.t("выкл")),
                TiggoText.t("Только оригинальные иконки без подложки"), v -> {
                    host.prefs.edit().putBoolean(PREF_TILE_GLASS, !host.prefs.getBoolean(PREF_TILE_GLASS, true)).apply();
                    buildAppStrip();
                    openSettingsSheet();
                }));
        panel.addView(sheetRow(TiggoText.t("Иконки приложений"), TiggoText.t("Fluid или другой установленный пак"), v -> openIconsSheet()));
        addDoneButton(panel);
    }

    private void openWidgetsResetSheet() {
        LinearLayout panel = beginSheet(TiggoText.t("Виджеты"), false, 820);
        for (final String id : widgetOrder()) {
            panel.addView(sheetRow(TiggoText.t(WIDGET_TITLES[indexOfId(id)]), TiggoText.t("Переместить, изменить размер"),
                    v -> openWidgetSheet(id)));
        }
        panel.addView(sheetRow(TiggoText.t("Сбросить порядок и размеры"), null, v -> {
            SharedPreferences.Editor ed = host.prefs.edit().remove(PREF_WIDGETS);
            for (String id : WIDGET_IDS) ed.remove(PREF_WSIZE + id);
            ed.apply();
            buildWidgets();
            closeSheet();
        }));
        addDoneButton(panel);
    }

    private void openDockSheet() {
        LinearLayout panel = beginSheet(TiggoText.t("Нижняя панель"), false, 820);
        for (int i = 0; i < DOCK_COUNT; i++) {
            final int slot = i;
            MainActivity.AppEntry chosen = chosenDockApp(slot);
            String sub = chosen != null ? chosen.label : TiggoText.t("Выбирается автоматически");
            panel.addView(sheetRow(dockLabel(slot), sub, v -> chooseDockApp(slot)));
        }
        addDoneButton(panel);
    }

    // ---- wallpaper

    private void openWallpaperSheet() {
        LinearLayout panel = beginSheet(TiggoText.t("Обои"), false, 1180);
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
        TextView gl = text(TiggoText.t("Из галереи"), 22, true);
        gl.setGravity(Gravity.CENTER);
        gallery.addView(gl);
        gallery.setOnClickListener(v -> pickFromGallery());
        pressScale(gallery);
        LinearLayout.LayoutParams gLp = lp(m(236), m(133));
        gLp.rightMargin = m(18);
        strip.addView(gallery, gLp);

        final int n = WALL_PRESETS.length + MainActivity.WALL_KEYS.length;
        final ImageView[] thumbs = new ImageView[n];
        final String[] keys = new String[n];
        for (int i = 0; i < n; i++) {
            final String key = i < WALL_PRESETS.length ? WALL_PRESETS[i] : MainActivity.WALL_KEYS[i - WALL_PRESETS.length];
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
                host.toast(TiggoText.t("Обои изменены"));
                closeSheet();
            });
            pressScale(iv);
            thumbs[i] = iv;
            item.addView(iv, lp(m(236), m(133)));
            TextView cap = text(i < WALL_CAPTIONS.length ? TiggoText.t(WALL_CAPTIONS[i]) : "", 20, false);
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
                    String key = effectiveStyle(keys[i]);
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
            host.toast(TiggoText.t("Выбор фото недоступен на этом устройстве"));
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
                    host.toast(TiggoText.t("Обои изменены"));
                    closeSheet();
                });
            } catch (Throwable t) {
                host.runOnUiThread(() -> host.toast(TiggoText.t("Не удалось открыть изображение")));
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

    /** Copies a picture chosen in the gallery into private storage (scaled); returns the file name. */
    static String importWallpaperFile(Context c, Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bm;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            bm = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bm == null) throw new IllegalStateException("decode");
        File dir = c.getFilesDir();
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) if (f.getName().startsWith("tiggo_wall_")) f.delete();
        }
        String name = "tiggo_wall_" + System.currentTimeMillis() + ".jpg";
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            bm.compress(Bitmap.CompressFormat.JPEG, 92, out);
        }
        bm.recycle();
        return name;
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
            host.toast(TiggoText.t("Без доступа к местоположению город определяется по интернету"));
            return;
        }
        requestFix();
        Weather.changed(host);
        Weather.refresh(host, true, this::refreshWeather);
    }

    private void openWeatherSheet() {
        LinearLayout panel = beginSheet(TiggoText.t("Погода"), true, 980);
        String now = Weather.manual(host) ? TiggoText.t("Город: ") + Weather.place(host) : TiggoText.t("Автоопределение");
        TextView current = text(now, 22, false);
        current.setAlpha(0.8f);
        panel.addView(current);

        panel.addView(sheetRow(TiggoText.t("Определять автоматически"),
                TiggoText.t("По местоположению автомобиля (GPS и сеть)"), v -> {
                    Weather.setPlace(host, null);
                    if (hasLocationPermission()) {
                        requestFix();
                    } else {
                        host.requestPermissions(new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION}, Perms.REQ_LOCATION);
                    }
                    Weather.refresh(host, true, this::refreshWeather);
                    host.toast(TiggoText.t("Погода: автоопределение города"));
                    closeSheet();
                }));

        LinearLayout searchRow = new LinearLayout(host);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        final EditText input = new EditText(host);
        input.setHint(TiggoText.t("Введите город"));
        input.setHintTextColor(0x99FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28 * k);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(m(24), m(10), m(24), m(10));
        input.setBackground(glass(m(26), 0x33FFFFFF));
        searchRow.addView(input, new LinearLayout.LayoutParams(0, m(76), 1f));
        TextView find = text(TiggoText.t("Найти"), 26, true);
        find.setGravity(Gravity.CENTER);
        find.setBackground(solid(m(26), 0xFF0A84FF));
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
            TextView wait = text(TiggoText.t("Поиск…"), 22, false);
            wait.setPadding(0, m(14), 0, 0);
            results.addView(wait);
            new Thread(() -> {
                List<Weather.Place> found;
                try {
                    found = Weather.search(q);
                } catch (Throwable t) {
                    host.runOnUiThread(() -> {
                        results.removeAllViews();
                        host.toast(TiggoText.t("Нет сети или город не найден"));
                    });
                    return;
                }
                final List<Weather.Place> shown = found;
                host.runOnUiThread(() -> {
                    results.removeAllViews();
                    if (shown.isEmpty()) {
                        TextView none = text(TiggoText.t("Ничего не найдено"), 22, false);
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
                            host.toast(TiggoText.t("Город: ") + chosen.name);
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
        if (!host.prefs.getBoolean("tiggo9_auto_init", false)) {
            host.prefs.edit().putBoolean("tiggo9_auto_init", true).putString(PREF_WALL, WALL_AUTO).apply();
        }
        String style = host.prefs.getString(PREF_WALL, WALL_AUTO);
        if (style == null || style.isEmpty()) style = WALL_AUTO;
        wallStyle = style;
        showWallpaperAsync(effectiveStyle(style));
    }

    private String effectiveStyle(String style) {
        return WALL_AUTO.equals(style) ? autoWallpaperStyle() : style;
    }

    /** Season from the date and hemisphere (location), day or night from the weather data. */
    private String autoWallpaperStyle() {
        return autoWallpaperStyle(host);
    }

    /** Season from the date and hemisphere (location), day or night from the weather data. */
    static String autoWallpaperStyle(Context c) {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);  // 0 = January
        double lat = Weather.latitude(c);
        if (!Double.isNaN(lat) && lat < 0) month = (month + 6) % 12;  // southern hemisphere
        int season;  // 0 winter, 1 spring, 2 summer, 3 autumn
        if (month == 11 || month <= 1) season = 0;
        else if (month <= 4) season = 1;
        else if (month <= 7) season = 2;
        else season = 3;
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        boolean night;
        Weather.Data d = Weather.cached(c);
        if (d != null && Weather.enabled(c)) night = !d.day;
        else night = TiggoTheme.isNight(c, cal.getTimeInMillis());  // sunrise / sunset at the location
        if (night) return "photo:tiggo9_night.jpg";
        if (season == 3 && hour >= 17) return "photo:tiggo9_autumn_evening.jpg";
        switch (season) {
            case 0: return "photo:tiggo9_winter.jpg";
            case 1: return "photo:tiggo9_spring.jpg";
            case 2: return "photo:tiggo9_summer.jpg";
            default: return "photo:tiggo9_autumn.jpg";
        }
    }

    private void checkAutoWallpaper() {
        if (!WALL_AUTO.equals(wallStyle)) return;
        String eff = autoWallpaperStyle();
        if (!eff.equals(shownStyle)) {
            showWallpaperAsync(eff);
            host.applyTiggoWallpaper(eff);
        }
    }

    private void showWallpaperAsync(final String style) {
        if (style.equals(shownStyle) && wallBmp != null && !wallBmp.isRecycled()) return;
        shownStyle = style;
        new Thread(() -> {
            final Bitmap b = decodeWallpaper(style);
            final Bitmap cut = b == null ? null : DepthMask.cutout(b);
            host.runOnUiThread(() -> {
                if (b == null) return;
                if (!style.equals(shownStyle)) {
                    // a newer wallpaper was asked for while this one was decoding: drop it
                    b.recycle();
                    if (cut != null) cut.recycle();
                    return;
                }
                final Bitmap oldWall = wallBmp;
                final Bitmap oldFg = fgBmp;
                wallBmp = b;
                fgBmp = cut;
                fgView.setAlpha(0f);
                fgView.setImageBitmap(cut);
                fgView.animate().alpha(1f).setDuration(700).start();
                Drawable old = wallView.getDrawable();
                Drawable from = old instanceof TransitionDrawable ? ((TransitionDrawable) old).getDrawable(1) : old;
                final BitmapDrawable next = new BitmapDrawable(host.getResources(), b);
                if (from != null) {
                    final TransitionDrawable td = new TransitionDrawable(new Drawable[]{from, next});
                    wallView.setImageDrawable(td);
                    td.startTransition(700);
                    // when the cross-fade is over keep only the new picture and free the old ones
                    postDelayed(() -> {
                        if (wallView.getDrawable() == td) wallView.setImageDrawable(next);
                        recycleLater(oldWall, oldFg);
                        refreshGlass();
                    }, 900);
                } else {
                    wallView.setImageDrawable(next);
                    recycleLater(oldWall, oldFg);
                    postDelayed(TiggoHome.this::refreshGlass, 750);
                }
            });
        }).start();
    }

    /** Frees wallpapers that are no longer shown (a few frames later, so nothing still draws them). */
    private void recycleLater(final Bitmap oldWall, final Bitmap oldFg) {
        postDelayed(() -> {
            if (oldWall != null && oldWall != wallBmp && !oldWall.isRecycled()) oldWall.recycle();
            if (oldFg != null && oldFg != fgBmp && !oldFg.isRecycled()) oldFg.recycle();
        }, 300);
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
        String eff = effectiveStyle(style);
        showWallpaperAsync(eff);
        host.applyTiggoWallpaper(eff);  // keeps the blurred glass behind other panels in sync
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            closePromo();
            stopTimers();
        } else if (!homeShown && isAttachedToWindow()) {
            startTimers();
        }
        if (visibility == VISIBLE && uiW > 0) refreshStyleIfChanged();
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
                rebuildDockSlots();
                buildWidgets();
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
        host.toast(TiggoText.t("Иконки применяются…"));
        scheduleIconPack();
    }

    private void openIconsSheet() {
        LinearLayout panel = beginSheet(TiggoText.t("Иконки приложений"), false, 820);
        String saved = host.prefs.getString(PREF_ICONS, IconPacks.AUTO);
        boolean fluid = IconPacks.isInstalled(host, IconPacks.FLUID);
        panel.addView(sheetRow((IconPacks.AUTO.equals(saved) ? "✓  " : "") + TiggoText.t("Автоматически"),
                fluid ? TiggoText.t("Будет использован пак Fluid") : TiggoText.t("Пак Fluid не установлен: установите его APK"),
                v -> setIconPack(IconPacks.AUTO)));
        panel.addView(sheetRow((IconPacks.NONE.equals(saved) ? "✓  " : "") + TiggoText.t("Системные иконки"), null,
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
        LinearLayout panel = beginSheet(entry.label == null ? TiggoText.t("Приложение") : entry.label, false, 760, false);
        panel.addView(sheetRow(TiggoText.t("Открыть в окне"), TiggoText.t("Экран в экране"), v -> {
            closeSheet();
            launchWindowed(entry);
        }));
        panel.addView(sheetRow(TiggoText.t("Заменить другим приложением"), null,
                v -> openAppGrid(TiggoText.t("Заменить «") + entry.label + "»", e -> {
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
            panel.addView(sheetRow(TiggoText.t("Сдвинуть влево"), null, v -> {
                Collections.swap(homeApps, index, index - 1);
                saveHomeOrder();
                buildAppStrip();
                closeSheet();
            }));
        }
        if (index < homeApps.size() - 1) {
            panel.addView(sheetRow(TiggoText.t("Сдвинуть вправо"), null, v -> {
                Collections.swap(homeApps, index, index + 1);
                saveHomeOrder();
                buildAppStrip();
                closeSheet();
            }));
        }
        panel.addView(sheetRow(TiggoText.t("Убрать с главного экрана"), null, v -> {
            homeApps.remove(index);
            saveHomeOrder();
            buildAppStrip();
            closeSheet();
        }));
        addDoneButton(panel);
    }

    private void openAppActionsSheet(final MainActivity.AppEntry entry) {
        final boolean onHome = homeApps.contains(entry);
        LinearLayout panel = beginSheet(entry.label == null ? TiggoText.t("Приложение") : entry.label, false, 760, true);
        panel.addView(sheetRow(TiggoText.t("Открыть"), null, v -> {
            closeAllApps();
            launchApp(this, entry);
        }));
        panel.addView(sheetRow(TiggoText.t("Открыть в окне"), TiggoText.t("Экран в экране"), v -> {
            closeAllApps();
            launchWindowed(entry);
        }));
        if (!onHome) {
            panel.addView(sheetRow(TiggoText.t("Добавить на главный экран"),
                    homeApps.size() >= slotCount ? TiggoText.t("Выберите, какое приложение заменить") : null, v -> {
                        if (homeApps.size() < slotCount) {
                            homeApps.add(entry);
                            saveHomeOrder();
                            buildAppStrip();
                            host.toast(entry.label + TiggoText.t(": добавлено на главный экран"));
                            closeAllApps();
                        } else {
                            openReplaceSlotSheet(entry);
                        }
                    }));
        } else {
            panel.addView(sheetRow(TiggoText.t("Убрать с главного экрана"), null, v -> {
                homeApps.remove(entry);
                saveHomeOrder();
                buildAppStrip();
                closeAllApps();
            }));
        }
        addDoneButton(panel);
    }

    private void openReplaceSlotSheet(final MainActivity.AppEntry entry) {
        LinearLayout panel = beginSheet(TiggoText.t("Какое приложение заменить?"), false, 760, true);
        for (int i = 0; i < homeApps.size(); i++) {
            final int slot = i;
            MainActivity.AppEntry old = homeApps.get(i);
            panel.addView(sheetRow(old.label == null ? TiggoText.t("Приложение") : old.label, null, v -> {
                homeApps.set(slot, entry);
                saveHomeOrder();
                buildAppStrip();
                host.toast(entry.label + TiggoText.t(": добавлено на главный экран"));
                closeAllApps();
            }));
        }
        addDoneButton(panel);
    }

    // ------------------------------------------------------------------ screen in screen

    private void launchApp(View v, MainActivity.AppEntry e) {
        if (e == null) return;
        if (host.prefs.getBoolean(PREF_WINDOW_MODE, false)) {
            launchWindowed(e);
            return;
        }
        host.launchAnimated(v, e);
    }

    /** Opens the app in a floating window of a comfortable size (needs Android's freeform windows). */
    private void launchWindowed(MainActivity.AppEntry e) {
        if (e == null) return;
        try {
            Intent i = new Intent(e.launch);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            DisplayMetrics dm = host.getResources().getDisplayMetrics();
            int sw = dm.widthPixels;
            int sh = dm.heightPixels;
            int pct = Math.max(40, Math.min(85, host.prefs.getInt(PREF_WINDOW_PCT, 58)));
            int w = sw * pct / 100;
            int h = Math.min(Math.round(w * 9f / 16f), Math.round(sh * 0.78f));
            int left = Math.max(0, sw - w - Math.round(sw * 0.05f));
            int top = Math.round(sh * 0.14f);
            ActivityOptions o = ActivityOptions.makeBasic();
            o.setLaunchBounds(new Rect(left, top, left + w, top + h));
            try {
                ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class).invoke(o, 5);  // freeform
            } catch (Throwable ignored) {
                // the system decides
            }
            host.startActivity(i, o.toBundle());
            if (!host.getPackageManager().hasSystemFeature("android.software.freeform_window_management")) {
                host.toast(TiggoText.t("Оконный режим не включён в Android: Настройки → Система → Окна свободной формы"));
            }
        } catch (Throwable t) {
            host.launchAnimated(this, e);
        }
    }

    // ------------------------------------------------------------------ music player widget

    private MainActivity.AppEntry musicApp() {
        MainActivity.AppEntry e = chosenWidgetApp("music");
        return e != null ? e : findDefaultApp(MUSIC_PACKAGES, MUSIC_WORDS);
    }

    /** The media session of the chosen music app (any app when none is chosen). */
    private MediaController activeController() {
        try {
            if (!MediaWatchService.granted(host)) return null;
            MediaSessionManager msm = (MediaSessionManager) host.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return null;
            List<MediaController> list = msm.getActiveSessions(new ComponentName(host, MediaWatchService.class));
            MainActivity.AppEntry app = musicApp();
            String wanted = app != null ? app.pkg : null;
            MediaController best = null;
            for (MediaController c : list) {
                if (wanted != null && !wanted.equals(c.getPackageName())) continue;
                PlaybackState st = c.getPlaybackState();
                boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
                if (best == null || playing) best = c;
                if (playing) break;
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    private void sendMediaKey(int code) {
        try {
            AudioManager am = (AudioManager) host.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            long t = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
        } catch (Throwable ignored) {
            // no media key support
        }
    }

    private void musicPlayPause() {
        MediaController c = activeController();
        if (c != null) {
            PlaybackState st = c.getPlaybackState();
            boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            if (playing) c.getTransportControls().pause();
            else c.getTransportControls().play();
            postDelayed(this::refreshMusic, 400);
            return;
        }
        MainActivity.AppEntry app = musicApp();
        if (app == null) {
            host.toast(TiggoText.t("Музыка: приложение не найдено, выберите его"));
            chooseWidgetApp("music");
            return;
        }
        launchApp(this, app);
        postDelayed(() -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY), 2500);
        postDelayed(this::refreshMusic, 3200);
    }

    private void musicSkip(boolean next) {
        MediaController c = activeController();
        if (c != null) {
            if (next) c.getTransportControls().skipToNext();
            else c.getTransportControls().skipToPrevious();
            postDelayed(this::refreshMusic, 400);
        } else {
            sendMediaKey(next ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
    }

    private void musicSeek(long ms) {
        MediaController c = activeController();
        if (c != null) c.getTransportControls().seekTo(Math.max(0, ms));
        postDelayed(this::refreshMusic, 300);
    }

    private void musicOpenApp() {
        MainActivity.AppEntry app = musicApp();
        if (app != null) launchApp(this, app);
        else chooseWidgetApp("music");
    }

    private ImageView controlButton(Bitmap bmp, int sizePx, View.OnClickListener click) {
        ImageView iv = iconView(bmp);
        iv.setPadding(m(6), m(6), m(6), m(6));
        iv.setBackground(rippleOnly(sizePx / 2));
        iv.setOnClickListener(click);
        pressScale(iv);
        iv.setLayoutParams(lp(sizePx, sizePx));
        return iv;
    }

    private static String clock(long ms) {
        long sec = Math.max(0, ms / 1000);
        return (sec / 60) + ":" + String.format(Locale.US, "%02d", sec % 60);
    }

    private final Runnable musicTicker = new Runnable() {
        @Override public void run() {
            if (mTitle == null || !isAttachedToWindow() || !homeShown) return;
            refreshMusic();
            postDelayed(this, 1000);
        }
    };

    private View buildMusicWidget() {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(m(22), m(14), m(22), m(10));
        card.setBackground(pressable(m(34), TiggoTheme.tileFill(host)));

        LinearLayout top = new LinearLayout(host);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        mArt = new ImageView(host);
        mArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable artBg = new GradientDrawable();
        artBg.setColor(0x33FFFFFF);
        artBg.setCornerRadius(m(16));
        mArt.setBackground(artBg);
        mArt.setClipToOutline(true);
        top.addView(mArt, lp(m(66), m(66)));
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        mTitle = text(TiggoText.t("Музыка"), 26, true);
        mTitle.setSingleLine(true);
        mTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mArtist = text("", 19, false);
        mArtist.setAlpha(0.8f);
        mArtist.setSingleLine(true);
        mArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(mTitle);
        col.addView(mArtist);
        LinearLayout.LayoutParams cl = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.weight = 1f;
        cl.leftMargin = m(16);
        top.addView(col, cl);
        top.setOnClickListener(v -> {
            if (!MediaWatchService.granted(host)) openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            else musicOpenApp();
        });
        card.addView(top, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout prog = new LinearLayout(host);
        prog.setOrientation(LinearLayout.HORIZONTAL);
        prog.setGravity(Gravity.CENTER_VERTICAL);
        mElapsed = text("0:00", 15, false);
        mRemain = text("-0:00", 15, false);
        final FrameLayout bar = new FrameLayout(host);
        View track = new View(host);
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(0x44FFFFFF);
        tg.setCornerRadius(m(4));
        track.setBackground(tg);
        bar.addView(track, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m(6), Gravity.CENTER_VERTICAL));
        mFill = new View(host);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(Color.WHITE);
        fg.setCornerRadius(m(4));
        mFill.setBackground(fg);
        bar.addView(mFill, new FrameLayout.LayoutParams(0, m(6), Gravity.CENTER_VERTICAL));
        mKnob = new View(host);
        GradientDrawable kg = new GradientDrawable();
        kg.setShape(GradientDrawable.OVAL);
        kg.setColor(Color.WHITE);
        mKnob.setBackground(kg);
        bar.addView(mKnob, new FrameLayout.LayoutParams(m(16), m(16), Gravity.CENTER_VERTICAL | Gravity.START));
        mBar = bar;
        bar.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == MotionEvent.ACTION_UP && v.getWidth() > 0 && mDurationMs > 0) {
                musicSeek((long) (mDurationMs * Math.max(0f, Math.min(1f, ev.getX() / v.getWidth()))));
            }
            return true;
        });
        prog.addView(mElapsed, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams bl = lp(0, m(28));
        bl.weight = 1f;
        bl.leftMargin = m(10);
        bl.rightMargin = m(10);
        prog.addView(bar, bl);
        prog.addView(mRemain, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams pl = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.topMargin = m(8);
        card.addView(prog, pl);

        LinearLayout ctl = new LinearLayout(host);
        ctl.setOrientation(LinearLayout.HORIZONTAL);
        ctl.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ew = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        ctl.addView(centered(controlButton(TiggoIcons.replay(m(34), WHITE), m(46), v -> musicSeek(0))), ew);
        ctl.addView(centered(controlButton(TiggoIcons.skipPrev(m(36), WHITE), m(50), v -> musicSkip(false))),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        mPlay = controlButton(TiggoIcons.play(m(44), WHITE), m(60), v -> musicPlayPause());
        ctl.addView(centered(mPlay), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ctl.addView(centered(controlButton(TiggoIcons.skipNext(m(36), WHITE), m(50), v -> musicSkip(true))),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ctl.addView(centered(controlButton(TiggoIcons.shareUp(m(32), WHITE), m(46), v -> musicOpenApp())),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams ctlLp = lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        ctlLp.weight = 1f;
        ctlLp.topMargin = m(2);
        card.addView(ctl, ctlLp);

        mLastArt = null;
        removeCallbacks(musicTicker);
        if (homeShown) postDelayed(musicTicker, 300);
        return card;
    }

    private FrameLayout centered(View child) {
        FrameLayout f = new FrameLayout(host);
        f.addView(child, new FrameLayout.LayoutParams(child.getLayoutParams().width,
                child.getLayoutParams().height, Gravity.CENTER));
        return f;
    }

    private void setMusicProgress(float fraction) {
        if (mBar == null || mFill == null || mKnob == null) return;
        int w = mBar.getWidth();
        if (w <= 0) return;
        float f = Math.max(0f, Math.min(1f, fraction));
        ViewGroup.LayoutParams lp = mFill.getLayoutParams();
        int fillW = Math.round(w * f);
        if (lp.width != fillW) {
            lp.width = fillW;
            mFill.setLayoutParams(lp);
        }
        mKnob.setTranslationX(Math.max(0f, fillW - m(8)));
    }

    /** Shows what the chosen music app plays and the real state of the controls. */
    private void refreshMusic() {
        if (mTitle == null) return;
        MediaController c = activeController();
        MainActivity.AppEntry app = musicApp();
        if (c == null) {
            mTitle.setText(app != null && app.label != null ? app.label : TiggoText.t("Музыка"));
            mArtist.setText(MediaWatchService.granted(host) ? TiggoText.t("Нажмите Play, чтобы начать")
                    : TiggoText.t("Нужен доступ к уведомлениям — нажмите"));
            Drawable d = app != null ? iconOf(app) : null;
            if (d != null) {
                mArt.setImageDrawable(d);
            } else {
                mArt.setImageBitmap(TiggoIcons.musicTile(m(66)));
            }
            mLastArt = null;
            mElapsed.setText("0:00");
            mRemain.setText("-0:00");
            mDurationMs = 0;
            setMusicProgress(0f);
            if (mPlaying || mPlay.getTag() == null) {
                mPlay.setImageBitmap(TiggoIcons.play(m(44), WHITE));
                mPlay.setTag("play");
            }
            mPlaying = false;
            return;
        }
        MediaMetadata md = c.getMetadata();
        PlaybackState st = c.getPlaybackState();
        String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null && md != null) artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        Bitmap art = null;
        if (md != null) {
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        long dur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        long pos = st == null ? 0 : st.getPosition();
        if (playing && st != null) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        if (dur > 0) pos = Math.min(pos, dur);
        mTitle.setText(title != null && !title.isEmpty() ? title
                : (app != null && app.label != null ? app.label : TiggoText.t("Музыка")));
        mArtist.setText(artist != null ? artist : "");
        if (art != null && art != mLastArt) {
            mArt.setImageBitmap(art);
            mLastArt = art;
        } else if (art == null && mLastArt == null) {
            Drawable d = app != null ? iconOf(app) : null;
            if (d != null) mArt.setImageDrawable(d);
        }
        mDurationMs = dur;
        mElapsed.setText(clock(pos));
        mRemain.setText(dur > 0 ? "-" + clock(Math.max(0, dur - pos)) : "");
        setMusicProgress(dur > 0 ? pos / (float) dur : 0f);
        if (mPlaying != playing || mPlay.getTag() == null) {
            mPlay.setImageBitmap(playing ? TiggoIcons.pause(m(44), WHITE) : TiggoIcons.play(m(44), WHITE));
            mPlay.setTag(playing ? "pause" : "play");
        }
        mPlaying = playing;
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(ticker);
        removeCallbacks(musicTicker);
        closeAllApps();
    }
}
