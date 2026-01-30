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

package com.android.server.accessibility.magnification;

import static android.provider.Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CENTER;
import static android.provider.Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CONTINUOUS;
import static android.provider.Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_EDGE;

import static com.android.server.accessibility.AccessibilityManagerService.MAGNIFICATION_GESTURE_HANDLER_ID;

import android.annotation.NonNull;
import android.provider.Settings.Secure.AccessibilityMagnificationCursorFollowingMode;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.input.InputManagerInternal;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles pointer motion event for full screen magnification.
 * Responsible for controlling magnification's cursor following feature.
 */
public class FullScreenMagnificationPointerMotionEventFilter implements
        InputManagerInternal.AccessibilityPointerMotionFilter {
    private static final String TAG =
            FullScreenMagnificationPointerMotionEventFilter.class.getSimpleName();

    // TODO(b/413146817): Convert this from px to dip.
    @VisibleForTesting
    static final float EDGE_MODE_MARGIN_PX = 100.f;

    @NonNull
    private final FullScreenMagnificationController mController;

    @NonNull
    private final FullScreenMagnificationController.FullScreenMagnificationData mMagnificationData;

    private final AtomicInteger mMode = new AtomicInteger(
            ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CONTINUOUS);

    public FullScreenMagnificationPointerMotionEventFilter(
            @NonNull FullScreenMagnificationController controller) {
        mController = controller;
        mMagnificationData = new FullScreenMagnificationController.FullScreenMagnificationData();
    }

    /**
     * Sets cursor following mode.
     *
     * @param mode The cursor following mode
     */
    public void setMode(@AccessibilityMagnificationCursorFollowingMode int mode) {
        mMode.set(mode);
    }

    /**
     * This call happens on the input hot path and it is extremely performance sensitive. It
     * also must not call back into native code.
     */
    @Override
    @NonNull
    public float[] filterPointerMotionEvent(float dx, float dy, float currentX, float currentY,
            int displayId) {
        mController.getFullScreenMagnificationData(displayId, mMagnificationData);

        // Unrelated display.
        if (!mMagnificationData.isActivated()) {
            return new float[]{dx, dy};
        }

        final int currentMode = mMode.get();
        final boolean continuousMode =
                currentMode == ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CONTINUOUS;
        final boolean centerMode =
                currentMode == ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CENTER;
        final boolean edgeMode =
                currentMode == ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_EDGE;

        // Center or edge mode.
        if (centerMode || edgeMode) {
            final float marginWidth;
            final float marginHeight;
            if (centerMode) {
                marginWidth = mMagnificationData.getBounds().width() / 2.f;
                marginHeight = mMagnificationData.getBounds().height() / 2.f;
            } else {
                marginWidth = EDGE_MODE_MARGIN_PX;
                marginHeight = EDGE_MODE_MARGIN_PX;
            }

            float moveX = 0;
            float newX = dx;
            if (dx > 0 && currentX >= mMagnificationData.getBounds().right - marginWidth) {
                moveX = dx * mMagnificationData.getScale();
                newX = 0;
                if (moveX > mMagnificationData.getOffsetX() - mMagnificationData.getMinOffsetX()) {
                    moveX = mMagnificationData.getOffsetX() - mMagnificationData.getMinOffsetX();
                    newX = dx - moveX / mMagnificationData.getScale();
                }
            } else if (dx < 0 && currentX <= mMagnificationData.getBounds().left + marginWidth) {
                moveX = dx * mMagnificationData.getScale();
                newX = 0;
                if (moveX < mMagnificationData.getOffsetX() - mMagnificationData.getMaxOffsetX()) {
                    moveX = mMagnificationData.getOffsetX() - mMagnificationData.getMaxOffsetX();
                    newX = dx - moveX / mMagnificationData.getScale();
                }
            }

            float moveY = 0;
            float newY = dy;
            if (dy > 0 && currentY >= mMagnificationData.getBounds().bottom - marginHeight) {
                moveY = dy * mMagnificationData.getScale();
                newY = 0;
                if (moveY > mMagnificationData.getOffsetY() - mMagnificationData.getMinOffsetY()) {
                    moveY = mMagnificationData.getOffsetY() - mMagnificationData.getMinOffsetY();
                    newY = dy - moveY / mMagnificationData.getScale();
                }
            } else if (dy < 0 && currentY <= mMagnificationData.getBounds().top + marginHeight) {
                moveY = dy * mMagnificationData.getScale();
                newY = 0;
                if (moveY < mMagnificationData.getOffsetY() - mMagnificationData.getMaxOffsetY()) {
                    moveY = mMagnificationData.getOffsetY() - mMagnificationData.getMaxOffsetY();
                    newY = dy - moveY / mMagnificationData.getScale();
                }
            }

            mController.offsetMagnifiedRegion(displayId, moveX, moveY,
                    MAGNIFICATION_GESTURE_HANDLER_ID);

            return new float[]{newX, newY};
        }

        // For unexpected mode, fall back to continuous mode.
        if (!continuousMode) {
            Slog.e(TAG, "Magnification cursor following falling back "
                    + "to continuous mode with unexpected mode: " + currentMode);
        }

        // Continuous cursor following.
        final float newCursorX = currentX + dx;
        final float newCursorY = currentY + dy;
        mController.setOffset(displayId, newCursorX - newCursorX * mMagnificationData.getScale(),
                newCursorY - newCursorY * mMagnificationData.getScale(),
                MAGNIFICATION_GESTURE_HANDLER_ID);

        // In the continuous mode, the cursor speed in physical display is kept. Thus, we don't
        // consume any motion delta.
        return new float[]{dx, dy};
    }
}
