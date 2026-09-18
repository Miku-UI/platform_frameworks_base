/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.hierarchy.experimental.testsplit;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.hierarchy.containers.Container;

import java.util.List;

/**
 * Controller for managing split screen dividers and their interactions.
 */
public class SplitDividerController implements SplitWindowManager.DividerDragListener {
    private static final String TAG = SplitDividerController.class.getSimpleName();

    private final Context mContext;
    private final SplitWindowManager mVerticalDivider;
    private final SplitWindowManager mHorizontalDivider;
    private final DividerCallback mCallback;

    private boolean mIsVerticalDividerInitialized = false;
    private boolean mIsHorizontalDividerInitialized = false;
    private boolean mIsLeftRightSplit = false;

    /**
     * Callback interface to communicate divider events back to the SplitMode.
     */
    public interface DividerCallback {
        /**
         * Called when a layout update is needed, e.g. during drag or after drag finish.
         *
         * @param finished True if the drag interaction has finished.
         * @param wct      Transaction to be applied.
         */
        void onLayoutNeeded(boolean finished, WindowContainerTransaction wct);

        /**
         * Called when a dismiss action is triggered by dragging the divider to the edge.
         *
         * @param indicesToDismiss List of child indices to be dismissed.
         * @param wct              Transaction to be applied.
         */
        void onDismiss(List<Integer> indicesToDismiss, WindowContainerTransaction wct);

        /**
         * Returns the root container of the split mode.
         */
        Container getSplitRoot();

        /**
         * Returns the number of active containers in the split mode.
         */
        int getActiveContainerCount();

        /**
         * Returns true if the split is currently in a left-right configuration.
         */
        boolean isLeftRightSplit();
    }

    public SplitDividerController(Context context, SplitWindowManager verticalDivider,
            SplitWindowManager horizontalDivider, DividerCallback callback) {
        mContext = context;
        mVerticalDivider = verticalDivider;
        mHorizontalDivider = horizontalDivider;
        mCallback = callback;

        mVerticalDivider.setDragListener(this);
        mHorizontalDivider.setDragListener(this);
    }

    public void updateDividers(int containerCount, Rect bounds, boolean isLeftRightSplit) {
        mIsLeftRightSplit = isLeftRightSplit;
        boolean showVertical = containerCount >= 3 || (containerCount == 2 && isLeftRightSplit);
        boolean showHorizontal = containerCount >= 3 || (containerCount == 2 && !isLeftRightSplit);

        Log.d(TAG, "updateDividers count=" + containerCount + " showVert=" + showVertical
                + " showHoriz=" + showHorizontal);

        try {
            mVerticalDivider.ensureVisible(
                    bounds, DividerView.TYPE_VERTICAL, true, showVertical
            );
            mIsVerticalDividerInitialized = showVertical;
        } catch (Exception e) {
            Log.e(TAG, "Failed to update vertical divider", e);
        }

        try {
            mHorizontalDivider.ensureVisible(
                    bounds, DividerView.TYPE_HORIZONTAL, false, showHorizontal
            );
            mIsHorizontalDividerInitialized = showHorizontal;
        } catch (Exception e) {
            Log.e(TAG, "Failed to update horizontal divider", e);
        }
    }

    public void cleanup() {
        if (mIsVerticalDividerInitialized) {
            mVerticalDivider.release();
            mIsVerticalDividerInitialized = false;
        }
        if (mIsHorizontalDividerInitialized) {
            mHorizontalDivider.release();
            mIsHorizontalDividerInitialized = false;
        }
    }

    public void updateLayout(boolean isLeftRightSplit, int activeContainerCount, int verticalPos,
            int horizontalPos, int density) {
        int dividerWindowWidth = (int) (DividerView.DIVIDER_SIZE_DP * density);

        // Vertical divider layout
        Rect verticalCrop = null;
        if (!isLeftRightSplit && activeContainerCount == 3) {
            verticalCrop = new Rect(0, 0, dividerWindowWidth, horizontalPos);
        }
        if (mIsVerticalDividerInitialized) {
            mVerticalDivider.updateLayout(true /*isLeftRight */, verticalCrop);
        }

        // Horizontal divider layout
        Rect horizontalCrop = null;
        if (isLeftRightSplit && activeContainerCount == 3) {
            horizontalCrop = new Rect(0, 0, verticalPos, dividerWindowWidth);
        }
        if (mIsHorizontalDividerInitialized) {
            mHorizontalDivider.updateLayout(false /*isLeftRight */, horizontalCrop);
        }
    }

    @Override
    public void onDividerDragging(int dividerType, int offset, boolean finished) {
        Container splitRoot = mCallback.getSplitRoot();
        if (splitRoot == null) return;

        RectF boundsF = splitRoot.getProps().getBounds();
        Rect bounds = new Rect();
        boundsF.round(bounds);

        SplitWindowManager manager =
                (dividerType == DividerView.TYPE_VERTICAL) ? mVerticalDivider : mHorizontalDivider;
        float max = (dividerType == DividerView.TYPE_VERTICAL) ? bounds.width() : bounds.height();

        manager.clampPosition(0f, max);

        int dismissDirection = SplitWindowManager.DISMISS_NONE;
        if (finished) {
            float density = mContext.getResources().getDisplayMetrics().density;
            dismissDirection = manager.getDismissDirection(bounds, dividerType, density);
        }

        WindowContainerTransaction wct = new WindowContainerTransaction();
        int containerCount = mCallback.getActiveContainerCount();
        boolean leftRight = mCallback.isLeftRightSplit();

        List<Integer> indicesToRemove = SplitWindowManager.getIndicesToDismiss(
                dismissDirection, containerCount, leftRight, dividerType
        );

        if (!indicesToRemove.isEmpty()) {
            handleDismissInteraction(indicesToRemove, containerCount, leftRight, dividerType,
                    bounds, wct);
        } else {
            mCallback.onLayoutNeeded(finished, wct);
        }
    }

    private void handleDismissInteraction(List<Integer> indicesToRemove, int containerCount,
            boolean leftRight, int dividerType, Rect bounds, WindowContainerTransaction wct) {
        // Reset divider if needed before callback
        if (containerCount - indicesToRemove.size() == 2) {
            // Transitioning to 1x1
            if (leftRight && dividerType == DividerView.TYPE_VERTICAL) {
                mVerticalDivider.setDividerPosition(bounds.width() / 2f);
            } else if (!leftRight && dividerType == DividerView.TYPE_HORIZONTAL) {
                mHorizontalDivider.setDividerPosition(bounds.height() / 2f);
            }
        }
        mCallback.onDismiss(indicesToRemove, wct);
    }

    public float getVerticalDividerPosition() {
        return mVerticalDivider.getDividerPosition();
    }

    public float getHorizontalDividerPosition() {
        return mHorizontalDivider.getDividerPosition();
    }
}
