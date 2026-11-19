package com.techgenius.tvoslauncher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid of home widgets (4 columns x 2 rows) with an iPhone-like edit mode: the widgets wiggle,
 * drag one to move it, pull the round handle in its corner to resize it, tap "−" to remove it
 * and the gear to set it up. The layout is saved as "type:col:row:w:h;...".
 */
final class WidgetBoard extends ViewGroup {
    static final int COLS = 4;
    static final int ROWS = 2;
    static final String PREF = "tiggo9_board";
    static final String DEFAULT = "weather:0:0:1:2;music:1:0:1:2;calendar:2:0:1:2;nav:3:0:1:2";

    static final class Item {
        String type;
        int c, r, w, h;

        Item(String type, int c, int r, int w, int h) {
            this.type = type;
            this.c = c;
            this.r = r;
            this.w = w;
            this.h = h;
        }
    }

    interface Factory {
        /** Builds the content of a widget for its size in cells. */
        View build(Item item);

        /** Gear in edit mode. */
        void configure(Item item);

        void changed();

        void editModeChanged(boolean editing);
    }

    private final Factory factory;
    private final SharedPreferences prefs;
    private final List<Item> items = new ArrayList<>();
    private final List<Cell> frames = new ArrayList<>();
    private final float density;
    private int gap;
    private boolean editing;

    WidgetBoard(Context c, SharedPreferences prefs, Factory factory, float k) {
        super(c);
        this.prefs = prefs;
        this.factory = factory;
        this.density = k;
        this.gap = Math.round(24 * k);
        setClipChildren(false);
        setClipToPadding(false);
        load();
    }

    // ------------------------------------------------------------------ model

    private void load() {
        items.clear();
        String s = prefs.getString(PREF, DEFAULT);
        parse(s == null ? DEFAULT : s);
        if (items.isEmpty() && s != null && !s.equals("")) parse(DEFAULT);
    }

    private void parse(String s) {
        for (String part : s.split(";")) {
            String[] f = part.split(":");
            if (f.length != 5) continue;
            try {
                Item it = new Item(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                        Integer.parseInt(f[3]), Integer.parseInt(f[4]));
                it.w = clamp(it.w, 1, COLS);
                it.h = clamp(it.h, 1, ROWS);
                it.c = clamp(it.c, 0, COLS - it.w);
                it.r = clamp(it.r, 0, ROWS - it.h);
                if (fits(it, it.c, it.r, it.w, it.h)) items.add(it);
            } catch (NumberFormatException ignored) {
                // skip a broken entry
            }
        }
    }

    void save() {
        StringBuilder b = new StringBuilder();
        for (Item it : items) {
            if (b.length() > 0) b.append(';');
            b.append(it.type).append(':').append(it.c).append(':').append(it.r).append(':')
                    .append(it.w).append(':').append(it.h);
        }
        prefs.edit().putString(PREF, b.toString()).apply();
        factory.changed();
    }

    List<Item> items() {
        return items;
    }

    boolean has(String type) {
        for (Item it : items) if (it.type.equals(type)) return true;
        return false;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** True if a w x h block at (c, r) is inside the grid and free of every item but {@code self}. */
    /**
     * Drop {@code it} at (c, r) and move every widget it covers to the place it left or to any free
     * spot. Nothing changes if they cannot all be placed.
     */
    private boolean pushAside(Item it, int c, int r) {
        int oc = it.c, or = it.r;
        java.util.List<Item> moved = new java.util.ArrayList<>();
        java.util.List<int[]> saved = new java.util.ArrayList<>();
        for (Item o : items) {
            if (o == it) continue;
            boolean overlap = o.c < c + it.w && c < o.c + o.w && o.r < r + it.h && r < o.r + o.h;
            if (overlap) {
                moved.add(o);
                saved.add(new int[]{o.c, o.r});
            }
        }
        if (moved.isEmpty()) return false;
        // park the covered widgets off the grid, then put the dragged one in place
        for (Item o : moved) o.c = -100;
        it.c = c;
        it.r = r;
        boolean ok = true;
        for (Item o : moved) {
            int[] spot = null;
            if (fits(o, oc, or, o.w, o.h)) spot = new int[]{oc, or};
            for (int rr = 0; spot == null && rr <= ROWS - o.h; rr++) {
                for (int cc = 0; spot == null && cc <= COLS - o.w; cc++) {
                    if (fits(o, cc, rr, o.w, o.h)) spot = new int[]{cc, rr};
                }
            }
            if (spot == null) {
                ok = false;
                break;
            }
            o.c = spot[0];
            o.r = spot[1];
        }
        if (!ok) {
            for (int i = 0; i < moved.size(); i++) {
                moved.get(i).c = saved.get(i)[0];
                moved.get(i).r = saved.get(i)[1];
            }
            it.c = oc;
            it.r = or;
        }
        return ok;
    }

    boolean fits(Item self, int c, int r, int w, int h) {
        if (c < 0 || r < 0 || c + w > COLS || r + h > ROWS) return false;
        for (Item o : items) {
            if (o == self) continue;
            if (c < o.c + o.w && o.c < c + w && r < o.r + o.h && o.r < r + h) return false;
        }
        return true;
    }

    /** Adds a widget at the first free place (trying the given size, then smaller). */
    boolean add(String type) {
        int[][] sizes = {{1, 2}, {1, 1}};
        for (int[] s : sizes) {
            for (int c = 0; c < COLS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    Item it = new Item(type, c, r, s[0], s[1]);
                    if (fits(null, c, r, s[0], s[1])) {
                        items.add(it);
                        save();
                        rebuild();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    void remove(Item it) {
        items.remove(it);
        save();
        rebuild();
    }

    /** Applies a size chosen in a menu; false if it does not fit. */
    boolean resize(Item it, int w, int h) {
        int c = Math.min(it.c, COLS - w);
        int r = Math.min(it.r, ROWS - h);
        if (!fits(it, c, r, w, h)) {
            // try every position for the new size
            for (int cc = 0; cc <= COLS - w; cc++) {
                for (int rr = 0; rr <= ROWS - h; rr++) {
                    if (fits(it, cc, rr, w, h)) {
                        c = cc;
                        r = rr;
                        cc = COLS;
                        break;
                    }
                }
            }
            if (!fits(it, c, r, w, h)) return false;
        }
        it.c = c;
        it.r = r;
        it.w = w;
        it.h = h;
        save();
        rebuild();
        return true;
    }

    void changeType(Item it, String type) {
        it.type = type;
        save();
        rebuild();
    }

    void reset() {
        prefs.edit().remove(PREF).apply();
        load();
        factory.changed();
        rebuild();
    }

    // ------------------------------------------------------------------ views

    /** Re-creates every widget view (sizes or content changed). */
    void rebuild() {
        for (Cell f : frames) f.stopWiggle();
        removeAllViews();
        frames.clear();
        for (Item it : items) {
            Cell f = new Cell(getContext(), it);
            frames.add(f);
            addView(f);
        }
        if (editing) for (Cell f : frames) f.setEditing(true);
        requestLayout();
    }

    /** Re-creates the widgets of one type (for example after the favourite apps changed). */
    void rebuildType(String type) {
        for (Cell f : frames) if (f.item.type.equals(type)) f.fill();
    }

    boolean isEditing() {
        return editing;
    }

    void setEditing(boolean on) {
        if (on == editing) return;
        editing = on;
        for (Cell f : frames) f.setEditing(on);
        if (on) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        factory.editModeChanged(on);
    }

    private float cellW() {
        return (getWidth() - gap * (COLS - 1f)) / COLS;
    }

    private float cellH() {
        return (getHeight() - gap * (ROWS - 1f)) / ROWS;
    }

    private int px(int c) {
        return Math.round(c * (cellW() + gap));
    }

    private int py(int r) {
        return Math.round(r * (cellH() + gap));
    }

    private int pw(int w) {
        return Math.round(w * cellW() + (w - 1) * gap);
    }

    private int ph(int h) {
        return Math.round(h * cellH() + (h - 1) * gap);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        for (Cell f : frames) {
            f.measure(MeasureSpec.makeMeasureSpec(Math.max(0, pw(f.shownW())), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.max(0, ph(f.shownH())), MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (Cell f : frames) {
            int x = px(f.item.c);
            int y = py(f.item.r);
            f.layout(x, y, x + pw(f.shownW()), y + ph(f.shownH()));
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (ow != 0 && (w != ow || h != oh)) post(this::rebuild);
    }

    // ------------------------------------------------------------------ one widget

    private final class Cell extends FrameLayout {
        final Item item;
        View content;
        private ObjectAnimator wiggle;
        private boolean edit;
        private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tmp = new RectF();
        // drag state
        private int mode;  // 0 none, 1 move, 2 resize
        private float sx, sy;
        private int previewW, previewH;

        Cell(Context c, Item item) {
            super(c);
            this.item = item;
            setClipChildren(false);
            setClipToPadding(false);
            setWillNotDraw(false);
            mark.setStyle(Paint.Style.STROKE);
            mark.setStrokeCap(Paint.Cap.ROUND);
            fill();
        }

        void fill() {
            if (content != null) removeView(content);
            content = factory.build(item);
            if (content == null) content = new View(getContext());
            content.setOnLongClickListener(v -> {
                setEditing(true);
                return true;
            });
            addView(content, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        int shownW() {
            return previewW > 0 ? previewW : item.w;
        }

        int shownH() {
            return previewH > 0 ? previewH : item.h;
        }

        void setEditing(boolean on) {
            edit = on;
            if (on) startWiggle();
            else stopWiggle();
            invalidate();
        }

        void startWiggle() {
            stopWiggle();
            float a = 0.9f + (frames.indexOf(this) % 2) * 0.3f;
            wiggle = ObjectAnimator.ofFloat(this, "rotation", -a, a);
            wiggle.setDuration(150 + (frames.indexOf(this) % 3) * 20L);
            wiggle.setRepeatCount(ValueAnimator.INFINITE);
            wiggle.setRepeatMode(ValueAnimator.REVERSE);
            wiggle.start();
        }

        void stopWiggle() {
            if (wiggle != null) wiggle.cancel();
            wiggle = null;
            setRotation(0);
        }

        private float br() {
            return 22 * density;
        }

        @Override protected void dispatchDraw(Canvas c) {
            super.dispatchDraw(c);
            if (!edit) return;
            float r = br();
            // "−" badge, top left
            badge.setColor(0xF2E5E5EA);
            c.drawCircle(r * 0.6f, r * 0.6f, r, badge);
            mark.setColor(0xFF1C1C1E);
            mark.setStrokeWidth(r * 0.22f);
            c.drawLine(r * 0.6f - r * 0.45f, r * 0.6f, r * 0.6f + r * 0.45f, r * 0.6f, mark);
            // gear badge, top right
            float gx = getWidth() - r * 0.6f;
            c.drawCircle(gx, r * 0.6f, r, badge);
            mark.setStrokeWidth(r * 0.16f);
            c.drawCircle(gx, r * 0.6f, r * 0.38f, mark);
            for (int i = 0; i < 6; i++) {
                double an = Math.PI * i / 3;
                float x1 = gx + (float) Math.cos(an) * r * 0.5f;
                float y1 = r * 0.6f + (float) Math.sin(an) * r * 0.5f;
                float x2 = gx + (float) Math.cos(an) * r * 0.68f;
                float y2 = r * 0.6f + (float) Math.sin(an) * r * 0.68f;
                c.drawLine(x1, y1, x2, y2, mark);
            }
            // resize handle, bottom right: white arc like iOS 17+
            float hx = getWidth() - r * 0.7f;
            float hy = getHeight() - r * 0.7f;
            mark.setColor(Color.WHITE);
            mark.setStrokeWidth(r * 0.32f);
            tmp.set(hx - r * 1.6f, hy - r * 1.6f, hx, hy);
            c.drawArc(tmp, 0, 90, false, mark);
            if (mode != 0) {
                badge.setColor(0x33FFFFFF);
                tmp.set(0, 0, getWidth(), getHeight());
                c.drawRoundRect(tmp, r * 1.4f, r * 1.4f, badge);
            }
        }

        private int hit(float x, float y) {
            float r = br() * 1.6f;
            if (x < r * 1.4f && y < r * 1.4f) return 1;                                   // remove
            if (x > getWidth() - r * 1.4f && y < r * 1.4f) return 2;                       // gear
            if (x > getWidth() - r * 2.2f && y > getHeight() - r * 2.2f) return 3;         // resize
            return 0;
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            return edit;  // in edit mode the widget's own buttons do not react
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!edit) return super.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    sx = e.getRawX();
                    sy = e.getRawY();
                    int h = hit(e.getX(), e.getY());
                    if (h == 1 || h == 2) {
                        mode = -h;  // a tap on a badge, decided on UP
                        return true;
                    }
                    mode = h == 3 ? 2 : 1;
                    if (mode == 1) {
                        stopWiggle();
                        bringToFront();
                        animate().scaleX(1.05f).scaleY(1.05f).alpha(0.92f).setDuration(120).start();
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - sx;
                    float dy = e.getRawY() - sy;
                    if (mode == 1) {
                        setTranslationX(dx);
                        setTranslationY(dy);
                    } else if (mode == 2) {
                        int nw = clamp(Math.round((pw(item.w) + dx + gap) / (cellW() + gap)), 1, COLS - item.c);
                        int nh = clamp(Math.round((ph(item.h) + dy + gap) / (cellH() + gap)), 1, ROWS - item.r);
                        if ((nw != shownW() || nh != shownH()) && fits(item, item.c, item.r, nw, nh)) {
                            previewW = nw;
                            previewH = nh;
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            WidgetBoard.this.requestLayout();
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                    float moved = Math.abs(e.getRawX() - sx) + Math.abs(e.getRawY() - sy);
                    if (mode == 1 && up) {
                        // the last finger position counts even if no move event came before it
                        setTranslationX(e.getRawX() - sx);
                        setTranslationY(e.getRawY() - sy);
                    }
                    int m = mode;
                    mode = 0;
                    if (m == -1 && up && moved < 30 * density) {
                        remove(item);
                        return true;
                    }
                    if (m == -2 && up && moved < 30 * density) {
                        factory.configure(item);
                        return true;
                    }
                    if (m == 1) finishMove(up);
                    if (m == 2) finishResize();
                    invalidate();
                    return true;
                }
                default:
                    return true;
            }
        }

        private void finishMove(boolean up) {
            float left = getLeft() + getTranslationX();
            float top = getTop() + getTranslationY();
            int nc = clamp(Math.round(left / (cellW() + gap)), 0, COLS - item.w);
            int nr = clamp(Math.round(top / (cellH() + gap)), 0, ROWS - item.h);
            boolean placed = false;
            if (up && (nc != item.c || nr != item.r)) {
                if (fits(item, nc, nr, item.w, item.h)) {
                    item.c = nc;
                    item.r = nr;
                    placed = true;
                } else {
                    // swap with the widget that is there if both fit
                    Item other = null;
                    for (Item o : items) {
                        if (o != item && o.c == nc && o.r == nr) other = o;
                    }
                    if (other == null) placed = pushAside(item, nc, nr);
                    if (other != null) {
                        int oc = item.c, or = item.r;
                        int[] save = {other.c, other.r};
                        other.c = oc;
                        other.r = or;
                        item.c = nc;
                        item.r = nr;
                        if (fits(item, item.c, item.r, item.w, item.h) && fits(other, other.c, other.r, other.w, other.h)) {
                            placed = true;
                        } else {
                            other.c = save[0];
                            other.r = save[1];
                            item.c = oc;
                            item.r = or;
                            placed = pushAside(item, nc, nr);
                        }
                    }
                }
            }
            // fly from where the finger left it to the new place
            final float fromX = left;
            final float fromY = top;
            WidgetBoard.this.requestLayout();
            WidgetBoard.this.post(() -> {
                for (Cell f : frames) {
                    if (f == this) {
                        setTranslationX(fromX - getLeft());
                        setTranslationY(fromY - getTop());
                    }
                }
                animate().translationX(0).translationY(0).scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(220).withEndAction(() -> {
                            if (edit) startWiggle();
                        }).start();
            });
            if (placed) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                save();
            }
        }

        private void finishResize() {
            if (previewW > 0 && (previewW != item.w || previewH != item.h)) {
                item.w = previewW;
                item.h = previewH;
                previewW = previewH = 0;
                save();
                fill();
                WidgetBoard.this.requestLayout();
            } else {
                previewW = previewH = 0;
                WidgetBoard.this.requestLayout();
            }
        }
    }
}
