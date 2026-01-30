/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.accessibility.autoclick;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.VisibleForTesting;


/**
 * A custom view that displays a circular visual indicator at the scroll cursor's location.
 * This indicator is shown when the autoclick scroll panel is active, providing a visual cue
 * for the point around which scrolling will occur.
 */
public class AutoclickScrollPointIndicator extends View {
    // 16dp diameter (8dp radius).
    private static final float POINT_RADIUS_DP = 8f;

    private final WindowManager mWindowManager;
    private final Paint mPaint;
    private final float mPointSizePx;

    // x and y coordinates of the cursor point indicator.
    private float mX;
    private float mY;

    private boolean mIsVisible = false;

    public AutoclickScrollPointIndicator(Context context) {
        super(context);

        mWindowManager = context.getSystemService(WindowManager.class);

        // Convert dp to pixels based on screen density.
        float density = getResources().getDisplayMetrics().density;
        mPointSizePx = POINT_RADIUS_DP * density;

        // Setup paint for drawing.
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Get the screen dimensions.
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        setMeasuredDimension(screenWidth, screenHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw a circle at (mX, mY) with default black fill and white border.
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.BLACK);
        canvas.drawCircle(mX, mY, mPointSizePx, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1f);
        mPaint.setColor(Color.WHITE);
        canvas.drawCircle(mX, mY, mPointSizePx, mPaint);
    }

    /**
     * Shows the cursor point indicator at the specified coordinates.
     *
     * @param x The x-coordinate of the cursor.
     * @param y The y-coordinate of the cursor.
     */
    public void show(float x, float y) {
        mX = x;
        mY = y;

        if (!mIsVisible) {
            mWindowManager.addView(this, getLayoutParams());
            mIsVisible = true;
        }

        invalidate();
    }

    /**
     * Hides the cursor point indicator.
     */
    public void hide() {
        if (mIsVisible) {
            mWindowManager.removeView(this);
            mIsVisible = false;
        }
    }

    /**
     * Retrieves the layout params for AutoclickScrollPointIndicator, used when it's added to the
     * Window Manager.
     */
    public WindowManager.LayoutParams getLayoutParams() {
        final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        layoutParams.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        layoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.setTitle(AutoclickScrollPointIndicator.class.getSimpleName());
        layoutParams.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL;

        return layoutParams;
    }

    @VisibleForTesting
    public boolean isVisible() {
        return mIsVisible;
    }
}
