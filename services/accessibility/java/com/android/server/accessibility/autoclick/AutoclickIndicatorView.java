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
import static android.view.accessibility.AccessibilityManager.AUTOCLICK_CURSOR_AREA_SIZE_DEFAULT;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.LinearInterpolator;

import androidx.annotation.VisibleForTesting;

import com.android.internal.R;

/**
 * A view that displays a circular visual indicator for the autoclick feature.
 * The indicator animates a ring to provide visual feedback before an automatic click occurs.
 */
public class AutoclickIndicatorView extends View {
    private static final String TAG = AutoclickIndicatorView.class.getSimpleName();

    static final int SHOW_INDICATOR_DELAY_TIME = 150;

    static final int MINIMAL_ANIMATION_DURATION = 50;

    // The radius of the click point indicator.
    private static final float POINT_RADIUS_DP = 4f;

    private static final float POINT_STROKE_WIDTH_DP = 1f;

    private final int mColor = R.color.materialColorPrimary;

    // Radius of the indicator circle.
    private int mRadius = AUTOCLICK_CURSOR_AREA_SIZE_DEFAULT;

    // Paint object used to draw the indicator.
    private final Paint mPaint;
    private final Paint mPointPaint;

    private final ValueAnimator mAnimator;

    private final RectF mRingRect;

    private final float mPointSizePx;
    private final float mPointStrokeWidthPx;

    // x and y coordinates of the mouse.
    private float mMouseX;
    private float mMouseY;

    // x and y coordinates of the visual indicator, set when drawing of the indicator begins.
    private float mSnapshotX;
    private float mSnapshotY;

    // Current sweep angle of the animated ring.
    private float mSweepAngle;

    private int mAnimationDuration = AccessibilityManager.AUTOCLICK_DELAY_WITH_INDICATOR_DEFAULT;

    // Status of whether the visual indicator should display or not.
    private boolean showIndicator = false;

    private boolean mIgnoreMinorCursorMovement = false;

    public AutoclickIndicatorView(Context context) {
        super(context);

        mPaint = new Paint();
        mPaint.setColor(context.getColor(mColor));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(10);

        // Convert dp to pixels based on screen density for the point indicator.
        float density = getResources().getDisplayMetrics().density;
        mPointSizePx = POINT_RADIUS_DP * density;
        mPointStrokeWidthPx = POINT_STROKE_WIDTH_DP * density;

        // Setup paint for drawing the point indicator.
        mPointPaint = new Paint();
        mPointPaint.setAntiAlias(true);

        mAnimator = ValueAnimator.ofFloat(0, 360);
        mAnimator.setDuration(mAnimationDuration);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(
                animation -> {
                    mSweepAngle = (float) animation.getAnimatedValue();
                    // Redraw the view with the updated angle.
                    invalidate();
                });

        mRingRect = new RectF();
    }

    /**
     * Retrieves the layout params for AutoclickIndicatorView, used when it's added to the Window
     * Manager.
     */
    public final WindowManager.LayoutParams getLayoutParams() {
        final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
        layoutParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        layoutParams.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS
                | WindowManager.LayoutParams.PRIVATE_FLAG_EXCLUDE_FROM_SCREEN_MAGNIFICATION
                | WindowManager.LayoutParams.PRIVATE_FLAG_NOT_MAGNIFIABLE;
        layoutParams.setFitInsetsTypes(WindowInsets.Type.statusBars());
        layoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.setTitle(AutoclickIndicatorView.class.getSimpleName());
        layoutParams.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL;
        return layoutParams;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (showIndicator) {
            // Draw the ring indicator.
            mRingRect.set(
                    /* left= */ mSnapshotX - mRadius,
                    /* top= */ mSnapshotY - mRadius,
                    /* right= */ mSnapshotX + mRadius,
                    /* bottom= */ mSnapshotY + mRadius);
            canvas.drawArc(mRingRect, /* startAngle= */ -90, mSweepAngle, false, mPaint);

            // Draw a point indicator. When mIgnoreMinorCursorMovement is true, the point stays at
            // the center of the ring. Otherwise, it follows the mouse movement.
            final float pointX;
            final float pointY;
            if (mIgnoreMinorCursorMovement) {
                pointX = mSnapshotX;
                pointY = mSnapshotY;
            } else {
                pointX = mMouseX;
                pointY = mMouseY;
            }
            mPointPaint.setStyle(Paint.Style.FILL);
            mPointPaint.setColor(Color.BLACK);
            canvas.drawCircle(pointX, pointY, mPointSizePx, mPointPaint);

            mPointPaint.setStyle(Paint.Style.STROKE);
            mPointPaint.setStrokeWidth(mPointStrokeWidthPx);
            mPointPaint.setColor(Color.WHITE);
            canvas.drawCircle(pointX, pointY, mPointSizePx, mPointPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Get the screen dimensions.
        // TODO(b/397944891): Handle device rotation case.
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        setMeasuredDimension(screenWidth, screenHeight);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        post(new Runnable() {
            @Override
            public void run() {
                // Only color needs to be updated when system theme is changed.
                mPaint.setColor(getContext().getColor(mColor));
            }
        });
    }

    public void setCoordination(float x, float y) {
        if (mMouseX == x && mMouseY == y) {
            return;
        }
        mMouseX = x;
        mMouseY = y;

        // Redraw the click point indicator with the updated coordinates.
        if (showIndicator) {
            invalidate();
        }
    }

    public void setRadius(int radius) {
        mRadius = radius;
    }

    @VisibleForTesting
    int getRadiusForTesting() {
        return mRadius;
    }

    @VisibleForTesting
    ValueAnimator getAnimatorForTesting() {
        return mAnimator;
    }

    public void redrawIndicator() {
        mSnapshotX = mMouseX;
        mSnapshotY = mMouseY;
        showIndicator = true;
        invalidate();
        mAnimator.start();
    }

    public void clearIndicator() {
        showIndicator = false;
        mAnimator.cancel();
        invalidate();
    }

    public void setAnimationDuration(int duration) {
        mAnimationDuration = Math.max(duration, MINIMAL_ANIMATION_DURATION);
        mAnimator.setDuration(mAnimationDuration);
    }

    public void setIgnoreMinorCursorMovement(boolean ignoreMinorCursorMovement) {
        mIgnoreMinorCursorMovement = ignoreMinorCursorMovement;
    }
}
