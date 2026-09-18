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

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Binder;
import android.util.Log;
import android.view.IWindow;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.WindowManager;
import android.view.WindowlessWindowManager;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Holds view hierarchy of a root surface and helps to inflate {@link DividerView} for a split.
 * Note: there can be two instances of this if two dividers are needed e.g. 2x1 split.
 */
public final class SplitWindowManager extends WindowlessWindowManager {
    private static final String TAG = SplitWindowManager.class.getSimpleName();

    private final String mWindowName;
    private final ParentContainerCallbacks mParentContainerCallbacks;
    private Context mContext;
    private SurfaceControlViewHost mViewHost;
    private SurfaceControl mLeash;

    private DividerView mDividerView;
    private SplitMode mSplitMode;
    private float mDividerPosition;

    public interface ParentContainerCallbacks {
        void attachToParentSurface(SurfaceControl.Builder b);
    }

    public SplitWindowManager(String windowName, Context context, Configuration config,
            ParentContainerCallbacks parentContainerCallbacks) {
        super(config, null /* rootSurface */, null /* hostInputToken */);
        mContext = context.createConfigurationContext(config);
        mParentContainerCallbacks = parentContainerCallbacks;
        mWindowName = windowName;
    }

    @Override
    public void setConfiguration(Configuration configuration) {
        super.setConfiguration(configuration);
        mContext = mContext.createConfigurationContext(configuration);
    }

    @Override
    protected SurfaceControl getParentSurface(IWindow window, WindowManager.LayoutParams attrs) {
        final SurfaceControl.Builder builder = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName(TAG)
                .setHidden(false)
                .setCallsite("SplitWindowManager#attachToParentSurface");
        mParentContainerCallbacks.attachToParentSurface(builder);
        mLeash = builder.build();
        return mLeash;
    }

    public interface DividerDragListener {
        void onDividerDragging(int dividerType, int offset, boolean finished);
    }

    private DividerDragListener mDragListener;

    /** Inflates {@link DividerView} on to the root surface. */
    void init(Rect dividerBounds, int dividerType) {
        if (mDividerView != null || mViewHost != null) {
            throw new UnsupportedOperationException(
                    "Try to inflate divider view again without release first");
        }

        mViewHost = new SurfaceControlViewHost(mContext, mContext.getDisplay(), this,
                "SplitWindowManager");
        // instantiate it directly or inflate from a layout in future.
        mDividerView = new DividerView(mContext, this, dividerType);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dividerBounds.width(), dividerBounds.height(), TYPE_DOCK_DIVIDER,
                FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.token = new Binder();
        lp.setTitle(mWindowName);
        lp.privateFlags |= PRIVATE_FLAG_NO_MOVE_ANIMATION | PRIVATE_FLAG_TRUSTED_OVERLAY;
        lp.accessibilityTitle = "Split-Divider";

        // WWM expects us to setView on the ViewHost.
        mViewHost.setView(mDividerView, lp);
        if (dividerType == DividerView.TYPE_VERTICAL) {
            mDividerPosition = dividerBounds.centerX();
        } else {
            mDividerPosition = dividerBounds.centerY();
        }
    }


    void setDragListener(DividerDragListener listener) {
        mDragListener = listener;
    }

    /**
     * Releases the surface control of the current {@link DividerView} and tear down the view
     * hierarchy.
     */
    void release() {
        if (mDividerView != null) {
            mDividerView = null;
        }

        if (mViewHost != null) {
            mViewHost.release();
            mViewHost = null;
        }

        if (mLeash != null && mLeash.isValid()) {
            new SurfaceControl.Transaction().remove(mLeash).apply();
        }
        mLeash = null;
    }

    void ensureVisible(Rect rootBounds, int dividerType, boolean isLeftRight,
            boolean shouldBeVisible) {
        if (!shouldBeVisible) {
            if (mDividerView != null) Log.d(TAG, "ensureVisible: hiding divider " + mWindowName);
            release();
            return;
        }

        if (mDividerView != null) {
            mDividerView.setIsLeftRight(isLeftRight);
            return;
        }

        Log.d(TAG, "ensureVisible: showing divider " + mWindowName + " bounds=" + rootBounds);

        final float density = mContext.getResources().getDisplayMetrics().density;
        final int dividerWindowWidth = (int) (DividerView.DIVIDER_SIZE_DP * density);
        Rect divBounds = new Rect(rootBounds);

        if (dividerType == DividerView.TYPE_VERTICAL) {
            int midX = rootBounds.centerX();
            divBounds.left = midX - dividerWindowWidth / 2;
            divBounds.right = midX + dividerWindowWidth / 2;
        } else {
            int midY = rootBounds.centerY();
            divBounds.top = midY - dividerWindowWidth / 2;
            divBounds.bottom = midY + dividerWindowWidth / 2;
        }

        init(divBounds, dividerType);
        mDividerView.setIsLeftRight(isLeftRight);
    }

    /**
     * Updates the surface position and crop.
     */
    void updateSurface(SurfaceControl.Transaction t, float x, float y, @Nullable Rect crop) {
        if (mLeash != null && mLeash.isValid()) {
            t.setPosition(mLeash, x, y);
            t.setCrop(mLeash, crop);
        }
    }

    /**
     * Updates the divider layout and surface.
     *
     * @param isLeftRight Whether the split is left-right (true) or top-bottom (false).
     * @param crop        Optional crop rectangle for the divider surface.
     */
    void updateLayout(boolean isLeftRight, @Nullable Rect crop) {
        if (mDividerView != null) {
            mDividerView.setIsLeftRight(isLeftRight);
        }
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        updateDividerSurface(t, crop);
        t.apply();
    }

    void updateDividerSurface(SurfaceControl.Transaction t, @Nullable Rect crop) {
        if (mLeash == null || !mLeash.isValid()) return;

        final float density = mContext.getResources().getDisplayMetrics().density;
        final int size = (int) (DividerView.DIVIDER_SIZE_DP * density);

        float x = 0;
        float y = 0;

        if (mDividerView != null && mDividerView.getDividerType() == DividerView.TYPE_VERTICAL) {
            x = mDividerPosition - size / 2f;
        } else {
            y = mDividerPosition - size / 2f;
        }

        updateSurface(t, x, y, crop);
    }

    void onDrag(int offset, boolean finished) {
        mDividerPosition += offset;
        if (mDragListener != null) {
            mDragListener.onDividerDragging(mDividerView.getDividerType(), offset, finished);
        }
    }

    float getDividerPosition() {
        return mDividerPosition;
    }

    void setDividerPosition(float pos) {
        mDividerPosition = pos;
    }

    void clampPosition(float min, float max) {
        if (mDividerPosition < min) mDividerPosition = min;
        if (mDividerPosition > max) mDividerPosition = max;
    }

    /**
     * Splits the root bounds into Left and Right.
     * Note: generated by Gemini.
     * <pre>
     * +-------+-------+
     * |       |       |
     * |   0   |   1   |
     * |       |       |
     * +-------+-------+
     * </pre>
     *
     * @param rootBounds  The total bounds of the split area.
     * @param dividerPos  The x-coordinate of the vertical divider.
     * @param childBounds Array to store the resulting bounds: [0] Left, [1] Right.
     */
    static void splitOneVertical(Rect rootBounds, int dividerPos, RectF[] childBounds) {
        // 1. Left
        childBounds[0].set(
                rootBounds.left,
                rootBounds.top,
                rootBounds.left + dividerPos,
                rootBounds.bottom);

        // 2. Right
        childBounds[1].set(
                rootBounds.left + dividerPos,
                rootBounds.top,
                rootBounds.right,
                rootBounds.bottom);
    }

    /**
     * Splits the root bounds into Top and Bottom.
     * Note: generated by Gemini
     * <pre>
     * +-------+
     * |   0   |
     * +-------+
     * |   1   |
     * +-------+
     * </pre>
     *
     * @param rootBounds  The total bounds of the split area.
     * @param dividerPos  The y-coordinate of the horizontal divider.
     * @param childBounds Array to store the resulting bounds: [0] Top, [1] Bottom.
     */
    static void splitOneHorizontal(Rect rootBounds, int dividerPos, RectF[] childBounds) {
        // 1. Top
        childBounds[0].set(
                rootBounds.left,
                rootBounds.top,
                rootBounds.right,
                rootBounds.top + dividerPos);

        // 2. Bottom
        childBounds[1].set(
                rootBounds.left,
                rootBounds.top + dividerPos,
                rootBounds.right,
                rootBounds.bottom);
    }

    /**
     * Splits the root bounds into 2 Left and 1 Right.
     * Note: generated by Gemini
     * <pre>
     * +-------+-------+
     * |       |       |
     * |   0   |       |
     * |       |   2   |
     * +-------+       |
     * |       |       |
     * |   1   |       |
     * |       |       |
     * +-------+-------+
     * </pre>
     *
     * @param rootBounds    The total bounds of the split area.
     * @param horizontalPos The y-coordinate of the horizontal divider (separating 0 and 1).
     * @param verticalPos   The x-coordinate of the vertical divider (separating 0/1 and 2).
     * @param childBounds   Array to store the resulting bounds: [0] Top-Left, [1] Bottom-Left, [2]
     *                      Right.
     */
    static void splitTwoVerticalOneHorizontal(
            Rect rootBounds, int horizontalPos, int verticalPos, RectF[] childBounds) {
        // 1. Top-Left
        childBounds[0].set(
                rootBounds.left,
                rootBounds.top,
                rootBounds.left + verticalPos,
                rootBounds.top + horizontalPos);

        // 2. Bottom-Left
        childBounds[1].set(
                rootBounds.left,
                rootBounds.top + horizontalPos,
                rootBounds.left + verticalPos,
                rootBounds.bottom);

        // 3. Right
        childBounds[2].set(
                rootBounds.left + verticalPos,
                rootBounds.top,
                rootBounds.right,
                rootBounds.bottom);
    }

    /**
     * Splits the root bounds into 2 Top and 1 Bottom.
     * Note: generated by Gemini.
     * <pre>
     * +-------+-------+
     * |       |       |
     * |   0   |   1   |
     * |       |       |
     * +-------+-------+
     * |               |
     * |       2       |
     * |               |
     * +---------------+
     * </pre>
     *
     * @param rootBounds    The total bounds of the split area.
     * @param horizontalPos The y-coordinate of the horizontal divider (separating 0/1 and 2).
     * @param verticalPos   The x-coordinate of the vertical divider (separating 0 and 1).
     * @param childBounds   Array to store the resulting bounds: [0] Top-Left, [1] Top-Right, [2]
     *                      Bottom.
     */
    static void splitTwoHorizontalOneVertical(
            Rect rootBounds, int horizontalPos, int verticalPos, RectF[] childBounds) {
        // 1. Top-Left
        childBounds[0].set(
                rootBounds.left,
                rootBounds.top,
                rootBounds.left + verticalPos,
                rootBounds.top + horizontalPos);

        // 2. Top-Right
        childBounds[1].set(
                rootBounds.left + verticalPos,
                rootBounds.top,
                rootBounds.right,
                rootBounds.top + horizontalPos);

        // 3. Bottom
        childBounds[2].set(
                rootBounds.left,
                rootBounds.top + horizontalPos,
                rootBounds.right,
                rootBounds.bottom);
    }

    static final int DISMISS_NONE = 0;
    static final int DISMISS_START = 1;
    static final int DISMISS_END = 2;

    int getDismissDirection(Rect rootBounds, int dividerType, float density) {
        float threshold = 50 * density;
        if (dividerType == DividerView.TYPE_VERTICAL) {
            float max = rootBounds.width();
            if (mDividerPosition < threshold) return DISMISS_START;
            if (mDividerPosition > max - threshold) return DISMISS_END;
        } else {
            float max = rootBounds.height();
            if (mDividerPosition < threshold) return DISMISS_START;
            if (mDividerPosition > max - threshold) return DISMISS_END;
        }
        return DISMISS_NONE;
    }

    /**
     * Returns list of indices to dismiss based on the drag direction and split configuration.
     * Note: Generated using Gemini
     *
     * @param direction            The dismiss direction (DISMISS_START or DISMISS_END).
     * @param activeContainerCount Number of active containers.
     * @param isLeftRightSplit     Whether the split is Left-Right (true) or Top-Bottom (false).
     * @param dividerType          The type of divider being dragged.
     * @return List of indices to dismiss.
     */
    static List<Integer> getIndicesToDismiss(int direction, int activeContainerCount,
            boolean isLeftRightSplit, int dividerType) {
        List<Integer> indicesToRemove = new java.util.ArrayList<>();
        if (direction == DISMISS_NONE) return indicesToRemove;

        if (activeContainerCount == 2) {
            // 1x1 Split
            // 0: Left/Top, 1: Right/Bottom
            if (direction == DISMISS_START) {
                indicesToRemove.add(0);
            } else {
                indicesToRemove.add(1);
            }
        } else if (activeContainerCount == 3) {
            // 2x1 Split
            if (isLeftRightSplit) {
                // 0: TL, 1: BL, 2: R
                // Vertical Divider (Primary) splits (0+1) and 2
                // Horizontal Divider (Secondary) splits 0 and 1
                if (dividerType == DividerView.TYPE_VERTICAL) {
                    // Primary: Drag Left -> Dismiss 0&1. Right -> Dismiss 2
                    if (direction == DISMISS_START) {
                        indicesToRemove.add(0);
                        indicesToRemove.add(1);
                    } else {
                        indicesToRemove.add(2);
                    }
                } else { // Horizontal (Secondary)
                    // Secondary: Drag Up -> Dismiss 0. Down -> Dismiss 1
                    if (direction == DISMISS_START) {
                        indicesToRemove.add(0);
                    } else {
                        indicesToRemove.add(1);
                    }
                }
            } else { // Top/Bottom Split
                // 0: TL, 1: TR, 2: B
                // Horizontal Divider (Primary) splits (0+1) and 2
                // Vertical Divider (Secondary) splits 0 and 1
                if (dividerType == DividerView.TYPE_HORIZONTAL) {
                    // Primary: Drag Up -> Dismiss 0&1. Down -> Dismiss 2
                    if (direction == DISMISS_START) {
                        indicesToRemove.add(0);
                        indicesToRemove.add(1);
                    } else {
                        indicesToRemove.add(2);
                    }
                } else { // Vertical (Secondary)
                    // Secondary: Drag Left -> Dismiss 0. Right -> Dismiss 1
                    if (direction == DISMISS_START) {
                        indicesToRemove.add(0);
                    } else {
                        indicesToRemove.add(1);
                    }
                }
            }
        }
        return indicesToRemove;
    }
}
