/*
 * Copyright 2025 The Android Open Source Project
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

import android.annotation.IntDef;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.R;
import com.android.internal.policy.SystemBarUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Manages the UI panel for controlling scroll actions in autoclick mode.
 * This panel provides buttons for scrolling in four directions (up, down, left, right)
 * and an exit button to leave scroll mode. It interacts with a
 * {@link ScrollPanelControllerInterface} to notify about hover events and exit requests.
 * It also displays a {@link AutoclickScrollPointIndicator} at the cursor position
 * when the panel is shown.
 */
public class AutoclickScrollPanel {
    public static final int DIRECTION_UP = 0;
    public static final int DIRECTION_DOWN = 1;
    public static final int DIRECTION_LEFT = 2;
    public static final int DIRECTION_RIGHT = 3;
    public static final int DIRECTION_EXIT = 4;
    public static final int DIRECTION_NONE = 5;

    // Distance between panel and screen edge.
    // TODO(b/388845721): Finalize edge margin.
    private static final int PANEL_EDGE_MARGIN = 15;

    /**
     * Defines the possible scroll directions and actions for the panel buttons.
     */
    @IntDef({
            DIRECTION_UP,
            DIRECTION_DOWN,
            DIRECTION_LEFT,
            DIRECTION_RIGHT,
            DIRECTION_EXIT,
            DIRECTION_NONE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScrollDirection {}

    private Context mContext;
    private AutoclickLinearLayout mContentView;
    private final WindowManager mWindowManager;
    private final WindowManager.LayoutParams mParams;
    private ScrollPanelControllerInterface mScrollPanelController;
    private final AutoclickScrollPointIndicator mAutoclickScrollPointIndicator;

    // Scroll panel buttons.
    private ImageButton mUpButton;
    private ImageButton mDownButton;
    private ImageButton mLeftButton;
    private ImageButton mRightButton;
    private ImageButton mExitButton;

    private final int mStatusBarHeight;

    private boolean mInScrollMode = false;

    // Panel size determined after measuring.
    private int mPanelWidth;
    private int mPanelHeight;

    /**
     * Interface for handling scroll operations.
     */
    public interface ScrollPanelControllerInterface {
        /**
         * Called when a button hover state changes.
         *
         * @param direction The direction associated with the button.
         * @param hovered Whether the button is being hovered or not.
         */
        void onHoverButtonChange(@ScrollDirection int direction, boolean hovered);

        /**
         * Called when the scroll panel should be exited.
         */
        void onExitScrollMode();
    }

    public AutoclickScrollPanel(Context context, WindowManager windowManager,
            ScrollPanelControllerInterface controller) {
        mContext = context;
        mWindowManager = windowManager;
        mScrollPanelController = controller;
        mAutoclickScrollPointIndicator = new AutoclickScrollPointIndicator(context);
        mParams = getDefaultLayoutParams();
        mStatusBarHeight = SystemBarUtils.getStatusBarHeight(context);

        inflateViewAndResources();
    }

    private void inflateViewAndResources() {
        // Inflate the panel layout.
        mContentView = (AutoclickLinearLayout) LayoutInflater.from(mContext).inflate(
                R.layout.accessibility_autoclick_scroll_panel, null);

        // Initialize buttons.
        mUpButton = mContentView.findViewById(R.id.scroll_up);
        mLeftButton = mContentView.findViewById(R.id.scroll_left);
        mRightButton = mContentView.findViewById(R.id.scroll_right);
        mDownButton = mContentView.findViewById(R.id.scroll_down);
        mExitButton = mContentView.findViewById(R.id.scroll_exit);

        initializeButtonState();

        // Measure the panel to get its dimensions.
        mContentView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        mPanelWidth = mContentView.getMeasuredWidth();
        mPanelHeight = mContentView.getMeasuredHeight();
    }

    /**
     * Sets up hover listeners for scroll panel buttons.
     */
    private void initializeButtonState() {
        // Set up hover listeners for all buttons.
        setupHoverListenerForButton(mUpButton, DIRECTION_UP);
        setupHoverListenerForButton(mLeftButton, DIRECTION_LEFT);
        setupHoverListenerForButton(mRightButton, DIRECTION_RIGHT);
        setupHoverListenerForButton(mDownButton, DIRECTION_DOWN);
        setupHoverListenerForButton(mExitButton, DIRECTION_EXIT);

        // Add click listener for exit button.
        mExitButton.setOnClickListener(v -> {
            if (mScrollPanelController != null) {
                mScrollPanelController.onExitScrollMode();
            }
        });
    }

    /**
     * Shows the autoclick scroll panel.
     */
    public void show() {
        if (mInScrollMode) {
            return;
        }
        mWindowManager.addView(mContentView, mParams);
        mInScrollMode = true;
    }

    /**
     * Shows the autoclick scroll panel positioned at the bottom right of the cursor.
     *
     * @param cursorX The x-coordinate of the cursor.
     * @param cursorY The y-coordinate of the cursor.
     */
    public void show(float cursorX, float cursorY) {
        if (mInScrollMode) {
            return;
        }
        // Position the panel at the cursor location
        positionPanelAtCursor(cursorX, cursorY);
        mAutoclickScrollPointIndicator.show(cursorX, cursorY);
        mWindowManager.addView(mContentView, mParams);
        mInScrollMode = true;
    }

    /**
     * Positions the panel at the bottom right of the cursor coordinates,
     * ensuring it stays within the screen boundaries.
     * If the panel would go off the right or bottom edge, tries other diagonal directions.
     * The panel's gravity is set to TOP|LEFT for absolute positioning.
     */
    protected void positionPanelAtCursor(float cursorX, float cursorY) {
        // Set gravity to TOP|LEFT for absolute positioning.
        mParams.gravity = Gravity.LEFT | Gravity.TOP;

        // Get screen dimensions.
        // TODO(b/388845721): Make sure this works on multiple screens.
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        // Adjust Y for status bar height.
        float adjustedCursorY = cursorY - mStatusBarHeight;

        // Offset from cursor point to panel center.
        int margin = 10;
        int xOffset = mPanelWidth / 2 + margin;
        int yOffset = mPanelHeight / 2 + margin;

        // Try 4 diagonal positions: bottom-right, bottom-left, top-right, top-left.
        int[][] directions = {{+1, +1}, {-1, +1}, {+1, -1}, {-1, -1}};
        for (int[] dir : directions) {
            // (panelX, panelY) is the top-left point of the panel.
            int panelX = (int) (cursorX + dir[0] * xOffset - mPanelWidth / 2);
            int panelY = (int) (adjustedCursorY + dir[1] * yOffset - mPanelHeight / 2);
            if (isWithinBounds(panelX, panelY, screenWidth, screenHeight)) {
                mParams.x = panelX;
                mParams.y = panelY;
                return;
            }
        }
    }

    /**
     * Returns true if the panel fits on screen with margin.
     */
    private boolean isWithinBounds(int x, int y, int screenWidth, int screenHeight) {
        return x > PANEL_EDGE_MARGIN && x + mPanelWidth + PANEL_EDGE_MARGIN < screenWidth
                && y > PANEL_EDGE_MARGIN && y + mPanelHeight + PANEL_EDGE_MARGIN < screenHeight;
    }

    /**
     * Hides the autoclick scroll panel.
     */
    public void hide() {
        if (!mInScrollMode) {
            return;
        }
        mAutoclickScrollPointIndicator.hide();
        mWindowManager.removeView(mContentView);
        mInScrollMode = false;
    }

    /**
     * Sets up a hover listener for a button.
     * When the button is hovered or unhovered, it updates the button's style
     * and notifies the {@link ScrollPanelControllerInterface}.
     * @param button The ImageButton to set the listener for.
     * @param direction The {@link ScrollDirection} associated with this button.
     */
    private void setupHoverListenerForButton(ImageButton button, @ScrollDirection int direction) {
        button.setOnHoverListener((v, event) -> {
            if (mScrollPanelController == null) {
                return true;
            }

            boolean hovered;
            switch (event.getAction()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    hovered = true;
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    hovered = false;
                    break;
                default:
                    return true;
            }

            // Update the button background color based on hover state.
            toggleSelectedButtonStyle(button, hovered);
            // Notify the controller about the hover change.
            mScrollPanelController.onHoverButtonChange(direction, hovered);
            return true;
        });
    }

    /**
     * Updates the button's style based on hover state.
     * When hovered, a semi-transparent tint is applied to the button's background.
     * When not hovered, the tint is cleared.
     *
     * @param button  The button to update the style for.
     * @param hovered Whether the button is being hovered or not.
     */
    private void toggleSelectedButtonStyle(ImageButton button, boolean hovered) {
        if (hovered) {
            int tintColor = mContext.getColor(
                    com.android.internal.R.color.materialColorOnSurface);

            // Apply semi-transparent (11%) tint.
            // SRC_ATOP preserves the button's texture and shadows while applying the tint.
            button.getBackground().setColorFilter(new BlendModeColorFilter(
                    Color.argb(28, Color.red(tintColor), Color.green(tintColor),
                            Color.blue(tintColor)),

                    BlendMode.SRC_ATOP));
        } else {
            // Clear the color filter to remove the effect.
            button.getBackground().clearColorFilter();
        }
    }

    /**
     * Updates the autoclick scroll panel when the system configuration is changed.
     * @param newConfig The new system configuration.
     */
    public void onConfigurationChanged(@android.annotation.NonNull Configuration newConfig) {
        mContext.getMainThreadHandler().post(() -> {
            // Only remove the view if it's currently shown.
            if (mInScrollMode) {
                mWindowManager.removeView(mContentView);
            }

            // Update mContext with the new configuration.
            mContext = mContext.createConfigurationContext(newConfig);

            // Always re-inflate the views and resources to adopt the new configuration.
            // This is important even if the panel is hidden.
            inflateViewAndResources();

            // If the panel was shown before the configuration change, add the newly
            // inflated view back to the window to restore its state.
            if (mInScrollMode) {
                mWindowManager.addView(mContentView, mParams);
            }
        });
    }

    public boolean isHovered() {
        return mContentView.isHovered();
    }

    /**
     * Retrieves the layout params for AutoclickScrollPanel, used when it's added to the Window
     * Manager.
     */
    @NonNull
    private WindowManager.LayoutParams getDefaultLayoutParams() {
        final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        layoutParams.setFitInsetsTypes(
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        layoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.setTitle(AutoclickScrollPanel.class.getSimpleName());
        layoutParams.accessibilityTitle =
                mContext.getString(R.string.accessibility_autoclick_scroll_panel_title);
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        return layoutParams;
    }

    @VisibleForTesting
    public boolean isVisible() {
        return mInScrollMode;
    }

    @VisibleForTesting
    public AutoclickLinearLayout getContentViewForTesting() {
        return mContentView;
    }

    @VisibleForTesting
    public WindowManager.LayoutParams getLayoutParamsForTesting() {
        return mParams;
    }

    @VisibleForTesting
    public int getPanelWidthForTesting() {
        return mPanelWidth;
    }

    @VisibleForTesting
    public int getPanelHeightForTesting() {
        return mPanelHeight;
    }

    @VisibleForTesting
    public int getStatusBarHeightForTesting() {
        return mStatusBarHeight;
    }
}
