package com.techgenius.tvoslauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import android.service.notification.NotificationListenerService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** tvOS-style home screen: blurred backdrop, top shelf title, rounded app tile grid. */
public class MainActivity extends Activity {

    static final int COLS = 6;
    static final String SETTINGS_KEY = "special:settings";
    static final String PRESENT_KEY = "special:present";
    static final String INPUTS_KEY = "special:inputs";
    static final String FOLDER_KEY = "special:folder";
    static final String PREF_ORDER = "order";
    static final String PREF_HIDDEN = "hidden";
    static final String PREF_OTHER = "other_apps";
    static final String TV_SETTINGS = "com.android.tv.settings";

    /** Packages placed first (in this order) until the user rearranges the grid. */
    static final List<String> DEFAULT_ORDER = Arrays.asList(
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "org.smarttube.stable",
            "in.startv.hotstar",
            "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android",
            "com.mitv.tvhome.mitvplus",
            "com.google.android.youtube.tv",
            "org.videolan.vlc",
            "io.github.jqssun.airplay");

    static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** tvOS focused icon is ~1.2x its resting size (303x183 vs 248x148 px in Apple's reference). */
    static final float FOCUS_SCALE = 1.2f;
    /** Focus lift with a slight spring, like tvOS. */
    static final android.view.animation.OvershootInterpolator LIFT =
            new android.view.animation.OvershootInterpolator(1.3f);

    static class AppEntry {
        String key, pkg, label, default
