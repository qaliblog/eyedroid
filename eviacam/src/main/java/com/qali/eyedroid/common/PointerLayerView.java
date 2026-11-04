/*
 * Enable Viacam for Android, a camera based mouse emulator
 *
 * Copyright (C) 2015-17 Cesar Mauri Loba (CREA Software Systems)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.qali.eyedroid.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.TypedValue;
import android.view.View;

import com.qali.eyedroid.R;

/**
 * Layout to draw click progress feedback and the pointer.
 *
 * The pointer is a small translucent white circle. While the user winks (half blink)
 * the circle shrinks and becomes more opaque, which gives feedback of the click and
 * of the hold-to-click gesture.
 */
public class PointerLayerView extends View implements OnSharedPreferenceChangeListener {

    // Radius of the pointer circle (in DIP)
    private static final float POINTER_RADIUS_DIP = 9;

    // Radius of the pointer circle while winking (in DIP)
    private static final float POINTER_RADIUS_PRESSED_DIP = 5;

    // Radius of the visual progress feedback (in DIP)
    private static final float PROGRESS_INDICATOR_RADIUS_DIP = 30;

    // default alpha value
    private static final int DEFAULT_ALPHA = 255;
    private final int DISABLED_ALPHA;

    // alpha of the circle fill and border, normal and while winking
    private static final int CIRCLE_FILL_ALPHA = 110;
    private static final int CIRCLE_STROKE_ALPHA = 170;
    private static final int CIRCLE_PRESSED_FILL_ALPHA = 220;
    private static final int CIRCLE_PRESSED_STROKE_ALPHA = 255;

    // cached paint box
    private final Paint mPaintBox;

    // the location where the pointer needs to be painted
    private PointF mPointerLocation;

    // alpha value for drawing pointer
    private int mAlphaPointer = DEFAULT_ALPHA;

    // click progress indicator radius in px
    private float mProgressIndicatorRadius;

    // click progress percent so far (0 disables)
    private int mClickProgressPercent = 0;

    // circle radius in px, normal and while winking
    private float mRadius;
    private float mRadiusPressed;

    // border width in px
    private float mStrokeWidth;

    // the user is winking (half blink) right now?
    private boolean mBlinkPressed = false;

    public PointerLayerView(Context c) {
        super(c);

        mPaintBox = new Paint();
        setWillNotDraw(false);
        mPointerLocation = new PointF();

        DISABLED_ALPHA = c.getResources().getColor(R.color.disabled_alpha) >> 24;

        // preferences
        SharedPreferences sp = Preferences.get().getSharedPreferences();
        sp.registerOnSharedPreferenceChangeListener(this);
        updateSettings();
    }

    public void cleanup() {
        Preferences.get().getSharedPreferences().
            unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (key.equals(Preferences.KEY_UI_ELEMENTS_SIZE) ||
            key.equals(Preferences.KEY_GAMEPAD_TRANSPARENCY)) {
            updateSettings();
        }
    }

    private float dipToPx(float dip) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dip, getResources().getDisplayMetrics());
    }

    private void updateSettings() {
        float size = Preferences.get().getUIElementsSize();

        mAlphaPointer = (255 * Preferences.get().getGamepadTransparency()) / 100;

        mRadius = dipToPx(POINTER_RADIUS_DIP) * size;
        mRadiusPressed = dipToPx(POINTER_RADIUS_PRESSED_DIP) * size;
        mStrokeWidth = dipToPx(1.5f) * size;
        mProgressIndicatorRadius = dipToPx(PROGRESS_INDICATOR_RADIUS_DIP) * size;
    }

    private static int scaleAlpha(int alpha, int multiplier) {
        return (alpha * multiplier) / 255;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // draw progress indicator
        if (mClickProgressPercent > 0) {
            float radius = ((float)
                    (100 - mClickProgressPercent) * mProgressIndicatorRadius) / 100.0f;

            mPaintBox.setStyle(Paint.Style.FILL_AND_STROKE);
            mPaintBox.setColor(0x80000000);
            canvas.drawCircle(mPointerLocation.x, mPointerLocation.y, radius, mPaintBox);

            mPaintBox.setStyle(Paint.Style.STROKE);
            mPaintBox.setStrokeWidth(mStrokeWidth);
            mPaintBox.setColor(0x80FFFFFF);
            canvas.drawCircle(mPointerLocation.x, mPointerLocation.y, radius, mPaintBox);
        }

        // draw the pointer circle, smaller and brighter while winking
        final float radius = mBlinkPressed ? mRadiusPressed : mRadius;
        final int fillAlpha = scaleAlpha(
                mBlinkPressed ? CIRCLE_PRESSED_FILL_ALPHA : CIRCLE_FILL_ALPHA, mAlphaPointer);
        final int strokeAlpha = scaleAlpha(
                mBlinkPressed ? CIRCLE_PRESSED_STROKE_ALPHA : CIRCLE_STROKE_ALPHA, mAlphaPointer);

        mPaintBox.setStyle(Paint.Style.FILL);
        mPaintBox.setColor(0xFFFFFF);
        mPaintBox.setAlpha(fillAlpha);
        canvas.drawCircle(mPointerLocation.x, mPointerLocation.y, radius, mPaintBox);

        mPaintBox.setStyle(Paint.Style.STROKE);
        mPaintBox.setStrokeWidth(mStrokeWidth);
        mPaintBox.setColor(0xFFFFFF);
        mPaintBox.setAlpha(strokeAlpha);
        canvas.drawCircle(mPointerLocation.x, mPointerLocation.y, radius, mPaintBox);
    }

    public void updatePosition(PointF p) {
        mPointerLocation.x = p.x;
        mPointerLocation.y = p.y;
    }

    public void updateClickProgress(int percent) {
        mClickProgressPercent = percent;
    }

    /**
     * Notify whether the user is winking (half blink) right now
     *
     * @param pressed true while the wink is held
     */
    public void setBlinkPressed(boolean pressed) {
        if (mBlinkPressed == pressed) return;

        mBlinkPressed = pressed;
        postInvalidate();
    }

    /**
     * Enable or disable faded appearance of the pointer for the rest mode
     *
     * @param value true for faded appearance
     */
    public void setRestModeAppearance(boolean value) {
        mAlphaPointer = (value ? DISABLED_ALPHA : DEFAULT_ALPHA);
    }
}
