package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Navigation island next to the dock: Back, Home and Recents on one glass pill.
 * Home carries a lighter tile, like the reference. The glass background is supplied by the home.
 */
final class NavIsland extends LinearLayout {
    /** Width of one button in design pixels (before the dock scale). */
    static final int ITEM_W = 132;
    /** Side padding of the pill in design pixels. */
    static final int SIDE_PAD = 14;

    private final float u;

    NavIsland(Context c, float unit, Drawable background, Runnable back, Runnable home, Runnable recents) {
        super(c);
        u = unit;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        int side = Math.round(SIDE_PAD * u);
        setPadding(side, Math.round(10 * u), side, Math.round(10 * u));
        setBackground(background);
        add(0, "Назад", back, false);
        add(1, "Домой", home, true);
        add(2, "Недавние", recents, false);
    }

    private void add(int type, String label, final Runnable action, boolean highlighted) {
        Context c = getContext();
        LinearLayout item = new LinearLayout(c);
        item.setOrientation(VERTICAL);
        item.setGravity(Gravity.CENTER);
        if (highlighted) {
            GradientDrawable tile = new GradientDrawable();
            tile.setColor(0x33FFFFFF);
            tile.setCornerRadius(30f * u);
            tile.setStroke(Math.max(1, Math.round(1.2f * u)), 0x30FFFFFF);
            item.setBackground(tile);
        }
        item.setContentDescription(TiggoText.t(label));
        item.addView(new Glyph(c, type, u), new LinearLayout.LayoutParams(Math.round(52 * u), Math.round(52 * u)));
        TextView t = new TextView(c);
        t.setText(TiggoText.t(label));
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 19f * u);
        t.setTypeface(Ui.font(c, "regular"));
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tl.topMargin = Math.round(4 * u);
        item.addView(t, tl);
        item.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    break;
                default:
                    break;
            }
            return false;
        });
        item.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (action != null) action.run();
        });
        addView(item, new LinearLayout.LayoutParams(Math.round(ITEM_W * u), LayoutParams.MATCH_PARENT));
    }

    /** Back (outlined triangle), Home (filled house), Recents (two squares). */
    private static final class Glyph extends View {
        private final int type;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();

        Glyph(Context c, int type, float u) {
            super(c);
            this.type = type;
        }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            if (s <= 0f) return;
            float x = (getWidth() - s) / 2f;
            float y = (getHeight() - s) / 2f;
            p.setColor(0xFFFFFFFF);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setPathEffect(new CornerPathEffect(s * 0.06f));
            path.reset();
            if (type == 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.07f);
                path.moveTo(x + s * 0.74f, y + s * 0.16f);
                path.lineTo(x + s * 0.74f, y + s * 0.84f);
                path.lineTo(x + s * 0.20f, y + s * 0.50f);
                path.close();
                c.drawPath(path, p);
            } else if (type == 1) {
                p.setStyle(Paint.Style.FILL);
                path.moveTo(x + s * 0.50f, y + s * 0.12f);
                path.lineTo(x + s * 0.90f, y + s * 0.48f);
                path.lineTo(x + s * 0.80f, y + s * 0.48f);
                path.lineTo(x + s * 0.80f, y + s * 0.86f);
                path.lineTo(x + s * 0.60f, y + s * 0.86f);
                path.lineTo(x + s * 0.60f, y + s * 0.62f);
                path.lineTo(x + s * 0.40f, y + s * 0.62f);
                path.lineTo(x + s * 0.40f, y + s * 0.86f);
                path.lineTo(x + s * 0.20f, y + s * 0.86f);
                path.lineTo(x + s * 0.20f, y + s * 0.48f);
                path.lineTo(x + s * 0.10f, y + s * 0.48f);
                path.close();
                c.drawPath(path, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.07f);
                r.set(x + s * 0.14f, y + s * 0.30f, x + s * 0.70f, y + s * 0.86f);
                c.drawRoundRect(r, s * 0.09f, s * 0.09f, p);
                path.moveTo(x + s * 0.30f, y + s * 0.30f);
                path.lineTo(x + s * 0.30f, y + s * 0.14f);
                path.lineTo(x + s * 0.86f, y + s * 0.14f);
                path.lineTo(x + s * 0.86f, y + s * 0.70f);
                path.lineTo(x + s * 0.70f, y + s * 0.70f);
                c.drawPath(path, p);
            }
        }
    }
}
