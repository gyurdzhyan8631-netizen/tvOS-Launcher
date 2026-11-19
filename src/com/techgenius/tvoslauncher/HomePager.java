package com.techgenius.tvoslauncher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

/**
 * Horizontal home pages like the iPhone springboard: swipe left and right, the pages follow the
 * finger, a fling or half a page flips to the next one. The dock and the status bar live outside
 * the pager, so they stay in place while the pages move.
 */
final class HomePager extends ViewGroup {
    interface Listener {
        /** position: 0 = first page, 1 = second page, fractions while moving. */
        void onScrolled(float position);

        void onSettled(int page);

        /** Long press on an empty spot of a page. */
        void onLongPress(int page, float x, float y);
    }

    private final OverScroller scroller;
    private final int slop;
    private final int minFling;
    private final int longPressMs;
    private VelocityTracker vt;
    private Listener listener;
    private float downX, downY, lastX;
    private boolean dragging;
    private boolean locked;
    private int current;
    private int settledReported = -1;
    private boolean longFired;
    private float lpX, lpY;

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (dragging) return;
            longFired = true;
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            if (listener != null) listener.onLongPress(current, lpX, lpY);
        }
    };

    HomePager(Context c) {
        super(c);
        scroller = new OverScroller(c, new DecelerateInterpolator(2.2f));
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 4;
        longPressMs = ViewConfiguration.getLongPressTimeout();
        setClipChildren(false);
        setClipToPadding(false);
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** While widgets are being edited the pages do not move. */
    void setLocked(boolean on) {
        locked = on;
    }

    int current() {
        return current;
    }

    int pages() {
        return getChildCount();
    }

    void setPage(int page, boolean animate) {
        page = Math.max(0, Math.min(getChildCount() - 1, page));
        current = page;
        int target = page * getWidth();
        if (!animate || getWidth() == 0) {
            scroller.forceFinished(true);
            scrollTo(target, 0);
            report();
            settle();
            return;
        }
        scroller.forceFinished(true);
        int dx = target - getScrollX();
        scroller.startScroll(getScrollX(), 0, dx, 0, Math.min(520, 220 + Math.abs(dx) * 300 / Math.max(1, getWidth())));
        postInvalidateOnAnimation();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        int cw = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int ch = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(cw, ch);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).layout(i * w, 0, (i + 1) * w, h);
        if (changed && scroller.isFinished()) scrollTo(current * w, 0);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = false;
                if (!scroller.isFinished()) {
                    // catch the moving page
                    scroller.forceFinished(true);
                    dragging = !locked;
                    return dragging;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!locked && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    dragging = true;
                    lastX = e.getX();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                lpX = e.getX();
                lpY = e.getY();
                longFired = false;
                removeCallbacks(longPress);
                if (!dragging) postDelayed(longPress, longPressMs);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float x = e.getX();
                if (!dragging && !locked && Math.abs(x - downX) > slop) dragging = true;
                if (Math.abs(x - downX) > slop || Math.abs(e.getY() - downY) > slop) removeCallbacks(longPress);
                if (dragging && !locked) {
                    float d = lastX - x;
                    int max = (getChildCount() - 1) * getWidth();
                    int sx = getScrollX();
                    if (sx + d < 0 || sx + d > max) d *= 0.35f;  // rubber band at the ends
                    scrollBy(Math.round(d), 0);
                    report();
                }
                lastX = x;
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                if (!dragging && !locked && !longFired && e.getActionMasked() == MotionEvent.ACTION_UP
                        && Math.abs(e.getX() - downX) > getWidth() * 0.12f
                        && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY)) {
                    // a quick swipe the system delivered as down + up only
                    setPage(current + (e.getX() < downX ? 1 : -1), true);
                    dragging = false;
                    vt.recycle();
                    vt = null;
                    return true;
                }
                if (dragging) {
                    vt.computeCurrentVelocity(1000);
                    float v = vt.getXVelocity();
                    int w = Math.max(1, getWidth());
                    float pos = getScrollX() / (float) w;
                    int target = Math.round(pos);
                    if (Math.abs(v) > minFling) target = v < 0 ? (int) Math.ceil(pos) : (int) Math.floor(pos);
                    setPage(target, true);
                } else if (!longFired && e.getActionMasked() == MotionEvent.ACTION_UP) {
                    performClick();
                }
                dragging = false;
                vt.recycle();
                vt = null;
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() {
        return super.performClick();
    }

    @Override public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            report();
            postInvalidateOnAnimation();
            if (scroller.isFinished()) settle();
        } else if (!dragging && settledReported != current && getWidth() > 0
                && getScrollX() == current * getWidth()) {
            settle();
        }
    }

    private void report() {
        if (listener != null && getWidth() > 0) listener.onScrolled(getScrollX() / (float) getWidth());
    }

    private void settle() {
        settledReported = current;
        if (listener != null) listener.onSettled(current);
    }

    /** The view of a page. */
    View page(int i) {
        return getChildAt(i);
    }
}
