package com.techgenius.tvoslauncher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class GamepadMapperService extends AccessibilityService {

    private static final float TAP_X = 500f;
    private static final float TAP_Y = 500f;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    injectTap(TAP_X, TAP_Y);
                    return true; 
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    return true;
                }
            }
        }
        return false; 
    }

    private void injectTap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        dispatchGesture(gesture, null, null);
    }
}
