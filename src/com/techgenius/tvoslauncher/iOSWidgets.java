package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class iOSWidgets {

    // Цвета в стиле iOS (Темная тема с эффектом стекла)
    private static final int GLASS_BG = Color.parseColor("#B31C1C1E"); // Темное полупрозрачное стекло
    private static final int TEXT_WHITE = Color.WHITE;
    private static final int TEXT_GRAY = Color.parseColor("#B0B0B8");
    private static final int ACCENT_RED = Color.parseColor("#FF3B30"); // Фирменный красный Apple

    // Вспомогательный метод для создания стеклянного фона
    private static GradientDrawable createGlassBackground(Context context) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(GLASS_BG);
        bg.setCornerRadius(dp(context, 24)); // Скругление как у iOS
        return bg;
    }

    // 1. ВИДЖЕТ: ПОГОДА (iOS Style)
    public static View createWeatherWidget(Context context, String city, String temp, String condition) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20));
        layout.setBackground(createGlassBackground(context));

        TextView cityText = new TextView(context);
        cityText.setText(city);
        cityText.setTextColor(TEXT_WHITE);
        cityText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cityText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TextView tempText = new TextView(context);
        tempText.setText(temp);
        tempText.setTextColor(TEXT_WHITE);
        tempText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        tempText.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));

        TextView conditionText = new TextView(context);
        conditionText.setText(condition);
        conditionText.setTextColor(TEXT_GRAY);
        conditionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        layout.addView(cityText);
        layout.addView(tempText);
        layout.addView(conditionText);

        return layout;
    }

    // 2. ВИДЖЕТ: ЧАСЫ (Минимализм)
    public static View createClockWidget(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20));
        layout.setBackground(createGlassBackground(context));
        layout.setGravity(Gravity.CENTER);

        TextView timeText = new TextView(context);
        timeText.setTextColor(TEXT_WHITE);
        timeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        timeText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        
        // Обновляем время при создании
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        timeText.setText(timeFormat.format(new Date()));

        TextView dateText = new TextView(context);
        dateText.setTextColor(ACCENT_RED); // Красный цвет как в iOS
        dateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dateText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMMM", new Locale("ru"));
        dateText.setText(dateFormat.format(new Date()).toUpperCase());

        layout.addView(timeText);
        layout.addView(dateText);

        return layout;
    }

    // 3. ВИДЖЕТ: КАЛЕНДАРЬ
    public static View createCalendarWidget(Context context, String eventTitle, String eventTime) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20));
        layout.setBackground(createGlassBackground(context));

        TextView monthText = new TextView(context);
        monthText.setTextColor(ACCENT_RED);
        monthText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        monthText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", new Locale("ru"));
        monthText.setText(monthFormat.format(new Date()).toUpperCase());

        TextView dayText = new TextView(context);
        dayText.setTextColor(TEXT_WHITE);
        dayText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        dayText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        
        SimpleDateFormat dayFormat = new SimpleDateFormat("d", Locale.getDefault());
        dayText.setText(dayFormat.format(new Date()));

        TextView eventText = new TextView(context);
        eventText.setText(eventTitle);
        eventText.setTextColor(TEXT_WHITE);
        eventText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        eventText.setPadding(0, dp(context, 10), 0, 0);

        TextView timeText = new TextView(context);
        timeText.setText(eventTime);
        timeText.setTextColor(TEXT_GRAY);
        timeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        layout.addView(monthText);
        layout.addView(dayText);
        layout.addView(eventText);
        layout.addView(timeText);

        return layout;
    }

    // 4. ВИДЖЕТ: КАРТЫ / НАВИГАЦИЯ (Стилизованный)
    public static View createMapsWidget(Context context, String destination, String eta) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20));
        
        // Для карт сделаем фон чуть светлее, имитируя карту
        GradientDrawable mapBg = new GradientDrawable();
        mapBg.setColor(Color.parseColor("#CC2C2C2E"));
        mapBg.setCornerRadius(dp(context, 24));
        layout.setBackground(mapBg);

        TextView label = new TextView(context);
        label.setText("🚗 Навигация");
        label.setTextColor(TEXT_GRAY);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        TextView destText = new TextView(context);
        destText.setText(destination);
        destText.setTextColor(TEXT_WHITE);
        destText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        destText.setTypeface(Typeface.DEFAULT_BOLD);
        destText.setPadding(0, dp(context, 8), 0, 0);

        TextView etaText = new TextView(context);
        etaText.setText(eta);
        etaText.setTextColor(ACCENT_RED);
        etaText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        etaText.setPadding(0, dp(context, 4), 0, 0);

        layout.addView(label);
        layout.addView(destText);
        layout.addView(etaText);

        return layout;
    }

    // Конвертер dp в пиксели
    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }
}
