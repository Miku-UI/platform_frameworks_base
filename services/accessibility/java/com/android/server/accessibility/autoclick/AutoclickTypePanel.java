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

import static android.provider.Settings.Secure.ACCESSIBILITY_AUTOCLICK_PANEL_POSITION;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.annotation.IntDef;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.R;
import com.android.internal.policy.SystemBarUtils;

/**
 * Manages the UI panel for selecting autoclick types.
 * This panel allows users to choose different autoclick behaviors (e.g., left
 * click, right click), pause/resume autoclick, and reposition the panel on the
 * screen. It interacts with {@link AutoclickController} to update the autoclick
 * behavior.
 */
public class AutoclickTypePanel {

    private final String TAG = AutoclickTypePanel.class.getSimpleName();

    public static final int AUTOCLICK_TYPE_LEFT_CLICK = 0;
    public static final int AUTOCLICK_TYPE_RIGHT_CLICK = 1;
    public static final int AUTOCLICK_TYPE_DOUBLE_CLICK = 2;
    public static final int AUTOCLICK_TYPE_DRAG = 3;
    public static final int AUTOCLICK_TYPE_SCROLL = 4;
    public static final int AUTOCLICK_TYPE_LONG_PRESS = 5;

    // Defines the possible corner positions for the autoclick panel.
    // These constants are used to determine where the panel is anchored on the
    // screen.
    public static final int CORNER_BOTTOM_RIGHT = 0;
    public static final int CORNER_BOTTOM_LEFT = 1;
    public static final int CORNER_TOP_LEFT = 2;
    public static final int CORNER_TOP_RIGHT = 3;

    // Used to remember and restore panel's position.
    protected static final String POSITION_DELIMITER = ",";

    // Distance between panel and screen edge.
    // TODO(b/396402941): Finalize horizontal margin.
    private static final int PANEL_HORIZONTAL_MARGIN = 15;
    // TODO(b/396402941): Finalize vertical margin.
    // Using 30 as the panel's vertical margin to keep it same with the panel position after
    // clicking position button on the panel that moves it to next corner.
    private static final int PANEL_VERTICAL_MARGIN = 30;

    // Touch point when drag starts, it can be anywhere inside the panel.
    private float mTouchStartX, mTouchStartY;
    // Initial panel position in screen coordinates.
    private int mPanelStartX, mPanelStartY;
    private boolean mIsDragging = false;
    private PointerIcon mCurrentCursor;

    // Types of click the AutoclickTypePanel supports.
    @IntDef({
        AUTOCLICK_TYPE_LEFT_CLICK,
        AUTOCLICK_TYPE_RIGHT_CLICK,
        AUTOCLICK_TYPE_DOUBLE_CLICK,
        AUTOCLICK_TYPE_DRAG,
        AUTOCLICK_TYPE_SCROLL,
        AUTOCLICK_TYPE_LONG_PRESS,
    })
    public @interface AutoclickType {}

    // Defines the possible corner positions for the autoclick panel.
    // These constants are used to determine where the panel is anchored on the
    // screen.
    @IntDef({
            CORNER_BOTTOM_RIGHT,
            CORNER_BOTTOM_LEFT,
            CORNER_TOP_LEFT,
            CORNER_TOP_RIGHT
    })
    public @interface Corner {}

    // An interface exposed to {@link AutoclickController} to handle different
    // actions on the panel, including changing autoclick type, pausing/resuming
    // autoclick, and repositioning the panel.
    public interface ClickPanelControllerInterface {
        /**
         * Allows users to change a different autoclick type.
         *
         * @param clickType The new autoclick type to use. Should be one of the
         *                  values defined in {@link AutoclickType}.
         */
        void handleAutoclickTypeChange(@AutoclickType int clickType);

        /**
         * Allows users to pause or resume autoclick.
         *
         * @param paused {@code true} to pause autoclick, {@code false} to resume.
         */
        void toggleAutoclickPause(boolean paused);

        /**
         * Called when the hovered state of the panel changes.
         *
         * @param hovered {@code true} if the panel is now hovered, {@code false} otherwise.
         */
        void onHoverChange(boolean hovered);
    }

    private Context mContext;

    private AutoclickTypeLinearLayout mContentView;

    private final WindowManager mWindowManager;

    private final int mUserId;

    private WindowManager.LayoutParams mParams;

    private final ClickPanelControllerInterface mClickPanelController;

    // Whether the panel is expanded or not.
    private boolean mExpanded = true;

    // Whether autoclick is paused.
    private boolean mPaused = false;

    private boolean mIsPanelShown = false;

    private int mStatusBarHeight = 0;

    // True when the fully expanded panel is wider than the screen display so certain buttons need
    // to be hidden.
    private boolean mIsExpandedPanelWiderThanScreen = false;

    // The current corner position of the panel, default to bottom right.
    private @Corner int mCurrentCorner = CORNER_BOTTOM_RIGHT;

    private ImageButton mLeftClickButton;
    private ImageButton mRightClickButton;
    private ImageButton mDoubleClickButton;
    private ImageButton mDragButton;
    private ImageButton mScrollButton;
    private ImageButton mPauseButton;
    private ImageButton mPositionButton;
    private ImageButton mLongPressButton;

    private ImageButton mSelectedButton;
    private int mSelectedClickType = AUTOCLICK_TYPE_LEFT_CLICK;

    private Drawable mPauseButtonDrawable;
    private Drawable mResumeButtonDrawable;
    private Drawable mPositionTopLeftDrawable;
    private Drawable mPositionTopRightDrawable;
    private Drawable mPositionBottomLeftDrawable;
    private Drawable mPositionBottomRightDrawable;

    public AutoclickTypePanel(
            Context context,
            WindowManager windowManager,
            int userId,
            ClickPanelControllerInterface clickPanelController) {
        mContext = context;
        mWindowManager = windowManager;
        mUserId = userId;
        mClickPanelController = clickPanelController;
        mParams = getDefaultLayoutParams();

        inflateViewAndResources();
    }

    private void inflateViewAndResources() {
        // Load drawables for buttons.
        mPauseButtonDrawable = mContext.getDrawable(
                R.drawable.accessibility_autoclick_pause);
        mResumeButtonDrawable = mContext.getDrawable(
                R.drawable.accessibility_autoclick_resume);
        mPositionTopLeftDrawable = mContext.getDrawable(
                R.drawable.accessibility_autoclick_position_top_left);
        mPositionTopRightDrawable = mContext.getDrawable(
                R.drawable.accessibility_autoclick_position_top_right);
        mPositionBottomLeftDrawable = mContext.getDrawable(
                R.drawable.accessibility_autoclick_position_bottom_left);
        mPositionBottomRightDrawable = mContext.getDrawable(
                R.drawable.accessibility_autoclick_position_bottom_right);

        // Inflate the panel layout.
        mContentView =
                (AutoclickTypeLinearLayout) LayoutInflater.from(mContext)
                        .inflate(R.layout.accessibility_autoclick_type_panel, null);
        mContentView.setOnHoverChangedListener(mClickPanelController::onHoverChange);
        mLeftClickButton =
                mContentView.findViewById(R.id.accessibility_autoclick_left_click_button);
        mRightClickButton =
                mContentView.findViewById(R.id.accessibility_autoclick_right_click_button);
        mDoubleClickButton =
                mContentView.findViewById(R.id.accessibility_autoclick_double_click_button);
        mScrollButton = mContentView.findViewById(R.id.accessibility_autoclick_scroll_button);
        mDragButton = mContentView.findViewById(R.id.accessibility_autoclick_drag_button);
        mPauseButton = mContentView.findViewById(R.id.accessibility_autoclick_pause_button);
        mPositionButton = mContentView.findViewById(R.id.accessibility_autoclick_position_button);
        mLongPressButton =
                mContentView.findViewById(R.id.accessibility_autoclick_long_press_button);

        // Get status bar height.
        mStatusBarHeight = SystemBarUtils.getStatusBarHeight(mContext);
        // Initialize the cursor icons.
        mCurrentCursor = PointerIcon.getSystemIcon(mContext, PointerIcon.TYPE_ARROW);

        initializeButtonState();

        // Set up touch event handling for the panel to allow the user to drag and reposition the
        // panel by touching and moving it.
        mContentView.setOnTouchListener(this::onPanelTouch);
    }

    /**
     * Handles touch events on the panel, enabling the user to drag and reposition it.
     * This function supports the draggable panel feature, allowing users to move the panel
     * to different screen locations for better usability and customization.
     */
    private boolean onPanelTouch(View v, MotionEvent event) {
        // TODO(b/397681794): Make sure this works on multiple screens.
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                onDragStart(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                onDragMove(event);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onDragEnd();
                return true;
            case MotionEvent.ACTION_OUTSIDE:
                if (mExpanded) {
                    collapsePanelWithClickType(mSelectedClickType);
                }
                return true;
        }
        return false;
    }

    /**
     * Snaps the panel to the nearest screen edge after a drag operation.
     * It determines whether the panel is closer to the left or right edge and adjusts its
     * position accordingly, maintaining vertical position within screen bounds.
     */
    private void snapToNearestEdge(WindowManager.LayoutParams params) {
        // Get screen width to determine which side to snap to.
        // TODO(b/397944891): Handle device rotation case.
        int screenWidth = mContext.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = mContext.getResources().getDisplayMetrics().heightPixels;
        int taskbarHeight = SystemBarUtils.getTaskbarHeight(mContext.getResources());
        int panelHeight = mContentView.getMeasuredHeight();
        int yPosition = params.y;

        // Determine which half of the screen the panel is on.
        @Corner int visualCorner = getVisualCorner();
        boolean isOnLeftHalf =
                (visualCorner == CORNER_TOP_LEFT || visualCorner == CORNER_BOTTOM_LEFT);

        if (isOnLeftHalf) {
            // Snap to left edge. Set params.gravity to make sure x, y offsets from correct anchor.
            params.gravity = Gravity.START | Gravity.TOP;
            // Set the current corner to be bottom-left to ensure that the subsequent reposition
            // action rotates the panel clockwise from bottom-left towards top-left.
            mCurrentCorner = CORNER_BOTTOM_LEFT;
        } else {
            // Snap to right edge. Set params.gravity to make sure x, y offsets from correct anchor.
            params.gravity = Gravity.END | Gravity.TOP;
            // Set the current corner to be top-right to ensure that the subsequent reposition
            // action rotates the panel clockwise from top-right towards bottom-right.
            mCurrentCorner = CORNER_TOP_RIGHT;
        }

        // Apply final position: set params.x to be edge margin, params.y to maintain vertical
        // position, with a minimal margin of PANEL_VERTICAL_MARGIN with taskbar and status bar.
        final int bottomPosition = screenHeight - taskbarHeight - panelHeight - mStatusBarHeight
                - PANEL_VERTICAL_MARGIN;
        params.x = PANEL_HORIZONTAL_MARGIN;
        params.y = Math.min(Math.max(PANEL_VERTICAL_MARGIN, yPosition), bottomPosition);
        mWindowManager.updateViewLayout(mContentView, params);

        // Use actual position for icon (not mCurrentCorner which is mainly used for rotation
        // sequence).
        updatePositionButtonIcon(getVisualCorner());
    }

    /**
     * Initializes the state and listeners for all buttons on the panel.
     */
    private void initializeButtonState() {
        // Use `createButtonListener()` to append extra pause logic to each button's click.
        mLeftClickButton.setOnClickListener(
                wrapWithTogglePauseListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_LEFT_CLICK)));
        mRightClickButton.setOnClickListener(
                wrapWithTogglePauseListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_RIGHT_CLICK)));
        mDoubleClickButton.setOnClickListener(
                wrapWithTogglePauseListener(
                        v -> togglePanelExpansion(AUTOCLICK_TYPE_DOUBLE_CLICK)));
        mScrollButton.setOnClickListener(
                wrapWithTogglePauseListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_SCROLL)));
        mDragButton.setOnClickListener(
                wrapWithTogglePauseListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_DRAG)));
        mLongPressButton.setOnClickListener(
                wrapWithTogglePauseListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_LONG_PRESS)));
        mPositionButton.setOnClickListener(wrapWithTogglePauseListener(v -> moveToNextCorner()));

        // The pause button calls `togglePause()` directly so it does not need extra logic.
        mPauseButton.setOnClickListener(v -> togglePause());

        setSelectedClickType(mSelectedClickType);

        // Set up hover listeners on panel and buttons to dynamically change cursor icons.
        setupHoverListenersForCursor();

        // Update the type panel to be expanded or not based on its state before theme change.
        if (mExpanded) {
            showAllClickTypeButtons();
        } else {
            // If it was collapsed, re-apply that state by hiding other buttons.
            hideAllClickTypeButtons();
            getButtonFromClickType(mSelectedClickType).setVisibility(View.VISIBLE);
        }
        // Adjust the panel spacing based on if it is expanded.
        adjustPanelSpacing(mExpanded);
        // Update the pause button and position button icons
        // based on their states before theme change.
        updatePauseButtonAppearance();
        mContentView.post(() -> {
            updatePositionButtonIcon(getVisualCorner());

            // If the expanded panel is too wide for the display, hide the rightmost buttons.
            if (mContentView.getWidth()
                    >= mContext.getResources().getDisplayMetrics().widthPixels) {
                mIsExpandedPanelWiderThanScreen = true;
                mPauseButton.setVisibility(View.GONE);
                mPositionButton.setVisibility(View.GONE);
            }
        });
    }

    /** Reset panel as collapsed state and only displays selected button. */
    public void collapsePanelWithClickType(@AutoclickType int clickType) {
        // When collapsing the panel show these buttons again.
        if (mIsExpandedPanelWiderThanScreen) {
            mPauseButton.setVisibility(View.VISIBLE);
            mPositionButton.setVisibility(View.VISIBLE);
        }

        hideAllClickTypeButtons();
        final ImageButton selectedButton = getButtonFromClickType(clickType);
        selectedButton.setVisibility(View.VISIBLE);

        // Sets the newly selected button.
        setSelectedClickType(clickType);

        // Remove spacing between buttons when collapsed.
        adjustPanelSpacing(/* isExpanded= */ false);

        mExpanded = false;
    }

    /** Sets the selected button and updates the newly and previously selected button styling. */
    private void setSelectedClickType(@AutoclickType int clickType) {
        final ImageButton selectedButton = getButtonFromClickType(clickType);

        // Updates the previously selected button styling.
        if (mSelectedButton != null) {
            toggleSelectedButtonStyle(mSelectedButton, /* isSelected= */ false);
        }

        mSelectedButton = selectedButton;
        mSelectedClickType = clickType;
        mClickPanelController.handleAutoclickTypeChange(clickType);

        // Updates the newly selected button styling.
        toggleSelectedButtonStyle(selectedButton, /* isSelected= */ true);
    }

    private void toggleSelectedButtonStyle(@NonNull ImageButton button, boolean isSelected) {
        button.setSelected(isSelected);
    }

    public void show() {
        if (mIsPanelShown) {
            return;
        }

        // Restores the panel position from saved settings. If no valid position is saved,
        // defaults to bottom-right corner.
        restorePanelPosition();
        mWindowManager.addView(mContentView, mParams);
        mIsPanelShown = true;

        // Update icon after view is laid out on screen to ensure accurate position detection
        // (getLocationOnScreen only works properly after layout is complete).
        mContentView.post(() -> {
            updatePositionButtonIcon(getVisualCorner());
        });

        // Make sure the selected button is highlighted if not already. This is to handle the
        // case that the panel is shown when a pointing device is reconnected.
        toggleSelectedButtonStyle(mSelectedButton, /* isSelected= */ true);
    }

    public void hide() {
        if (!mIsPanelShown) {
            return;
        }

        // Sets the button background to unselected styling, this is necessary to make sure the
        // button background styling is correct when the panel shows up next time.
        toggleSelectedButtonStyle(mSelectedButton, /* isSelected= */ false);

        // Save the panel's position when user turns off the autoclick.
        savePanelPosition();

        mWindowManager.removeView(mContentView);
        mIsPanelShown = false;
    }

    /**
     * Checks if autoclick is currently paused.
     */
    public boolean isPaused() {
        return mPaused;
    }

    /**
     * Checks if the panel is currently being hovered over by the mouse pointer.
     */
    public boolean isHovered() {
        return mContentView.isHovered();
    }

    /**
     * Updates the autoclick type panel when the system configuration is changed.
     * @param newConfig The new system configuration.
     */
    public void onConfigurationChanged(@android.annotation.NonNull Configuration newConfig) {
        mContext.getMainThreadHandler().post(() -> {
            // Only remove the view if it's currently shown.
            if (mIsPanelShown) {
                mWindowManager.removeView(mContentView);
            }

            // Update mContext with the new configuration.
            mContext = mContext.createConfigurationContext(newConfig);

            // Always re-inflate the views and resources to adopt the new configuration.
            // This is important even if the panel is hidden.
            inflateViewAndResources();

            // If the panel was shown before the configuration change, add the newly
            // inflated view back to the window to restore its state.
            if (mIsPanelShown) {
                mWindowManager.addView(mContentView, mParams);
            }
        });
    }

    /** Toggles the panel expanded or collapsed state. */
    private void togglePanelExpansion(@AutoclickType int clickType) {
        if (mExpanded) {
            // If the panel is already in expanded state, we should collapse it by hiding all
            // buttons except the one user selected.
            collapsePanelWithClickType(clickType);
        } else {
            // If the panel is already collapsed, we just need to expand it.
            showAllClickTypeButtons();

            // Add spacing when panel is expanded.
            adjustPanelSpacing(/* isExpanded= */ true);

            // If the expanded panel is too wide for the display, hide the rightmost buttons.
            if (mIsExpandedPanelWiderThanScreen) {
                mPauseButton.setVisibility(View.GONE);
                mPositionButton.setVisibility(View.GONE);
            }

            // Toggle the state.
            mExpanded = true;
        }
    }

    /**
     * Toggles the pause/resume state of the autoclick feature and updates the pause button UI.
     */
    private void togglePause() {
        mPaused = !mPaused;
        mClickPanelController.toggleAutoclickPause(mPaused);
        updatePauseButtonAppearance();
    }

    /**
     * Updates the Pause/Resume button's icon and text based on the current {@code mPaused} state,
     * without changing the state itself.
     */
    private void updatePauseButtonAppearance() {
        if (mPaused) {
            String resumeText = mContext.getString(R.string.accessibility_autoclick_resume);
            mPauseButton.setTooltipText(resumeText);
            mPauseButton.setContentDescription(resumeText);
            mPauseButton.setImageDrawable(mResumeButtonDrawable);
        } else {
            String pauseText = mContext.getString(R.string.accessibility_autoclick_pause);
            mPauseButton.setTooltipText(pauseText);
            mPauseButton.setContentDescription(pauseText);
            mPauseButton.setImageDrawable(mPauseButtonDrawable);
        }
    }

    /** Hide all buttons on the panel except pause and position buttons. */
    private void hideAllClickTypeButtons() {
        mLeftClickButton.setVisibility(View.GONE);
        mRightClickButton.setVisibility(View.GONE);
        mDoubleClickButton.setVisibility(View.GONE);
        mDragButton.setVisibility(View.GONE);
        mScrollButton.setVisibility(View.GONE);
        mLongPressButton.setVisibility(View.GONE);
    }

    /** Show all buttons on the panel except pause and position buttons. */
    private void showAllClickTypeButtons() {
        mLeftClickButton.setVisibility(View.VISIBLE);
        mRightClickButton.setVisibility(View.VISIBLE);
        mDoubleClickButton.setVisibility(View.VISIBLE);
        mDragButton.setVisibility(View.VISIBLE);
        mScrollButton.setVisibility(View.VISIBLE);
        mLongPressButton.setVisibility(View.VISIBLE);
    }

    /**
     * Retrieves the ImageButton corresponding to the given autoclick type.
     * @param clickType The autoclick type.
     * @return The ImageButton for the specified click type.
     */
    private ImageButton getButtonFromClickType(@AutoclickType int clickType) {
        return switch (clickType) {
            case AUTOCLICK_TYPE_LEFT_CLICK -> mLeftClickButton;
            case AUTOCLICK_TYPE_RIGHT_CLICK -> mRightClickButton;
            case AUTOCLICK_TYPE_DOUBLE_CLICK -> mDoubleClickButton;
            case AUTOCLICK_TYPE_DRAG -> mDragButton;
            case AUTOCLICK_TYPE_SCROLL -> mScrollButton;
            case AUTOCLICK_TYPE_LONG_PRESS -> mLongPressButton;
            default -> throw new IllegalArgumentException("Unknown clickType " + clickType);
        };
    }

    /** Moves the panel to the next corner in clockwise direction. */
    private void moveToNextCorner() {
        @Corner int nextCorner = (mCurrentCorner + 1) % 4;
        mCurrentCorner = nextCorner;

        setPanelPositionForCorner(mParams, mCurrentCorner);
        mWindowManager.updateViewLayout(mContentView, mParams);
        updatePositionButtonIcon(mCurrentCorner);
    }

    /** Resets the panel position to bottom-right corner. */
    @VisibleForTesting
    void resetPanelPositionForTesting() {
        setPanelPositionForCorner(mParams, CORNER_BOTTOM_RIGHT);
        mCurrentCorner = CORNER_BOTTOM_RIGHT;
    }

    /**
     * Sets the panel's gravity and initial x/y offsets based on the specified corner.
     * @param params The WindowManager.LayoutParams to modify.
     * @param corner The corner to position the panel in.
     */
    private void setPanelPositionForCorner(WindowManager.LayoutParams params, @Corner int corner) {
        // TODO(b/396402941): Current values are experimental and may not work correctly across
        // different device resolutions and configurations.
        params.x = PANEL_HORIZONTAL_MARGIN;
        params.y = PANEL_VERTICAL_MARGIN;
        switch (corner) {
            case CORNER_BOTTOM_RIGHT:
                params.gravity = Gravity.END | Gravity.BOTTOM;
                break;
            case CORNER_BOTTOM_LEFT:
                params.gravity = Gravity.START | Gravity.BOTTOM;
                break;
            case CORNER_TOP_LEFT:
                params.gravity = Gravity.START | Gravity.TOP;
                break;
            case CORNER_TOP_RIGHT:
                params.gravity = Gravity.END | Gravity.TOP;
                break;
            default:
                throw new IllegalArgumentException("Invalid corner: " + corner);
        }
    }

    /**
     * Saves the panel's current position (gravity, x, y, corner) to secure settings.
     */
    private void savePanelPosition() {
        String positionString = TextUtils.join(POSITION_DELIMITER, new String[]{
                String.valueOf(mParams.gravity),
                String.valueOf(mParams.x),
                String.valueOf(mParams.y),
                String.valueOf(mCurrentCorner)
        });
        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                ACCESSIBILITY_AUTOCLICK_PANEL_POSITION, positionString, mUserId);
    }

    /**
     * Restores the panel position from saved settings. If no valid position is saved,
     * defaults to bottom-right corner.
     */
    private void restorePanelPosition() {
        // Try to get saved position from settings.
        String savedPosition = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                ACCESSIBILITY_AUTOCLICK_PANEL_POSITION, mUserId);
        if (savedPosition == null) {
            setPanelPositionForCorner(mParams, CORNER_BOTTOM_RIGHT);
            mCurrentCorner = CORNER_BOTTOM_RIGHT;
            return;
        }

        // Parse saved position string in "gravity,x,y,corner" format.
        String[] parts = TextUtils.split(savedPosition, POSITION_DELIMITER);
        if (!isValidPositionParts(parts)) {
            setPanelPositionForCorner(mParams, CORNER_BOTTOM_RIGHT);
            mCurrentCorner = CORNER_BOTTOM_RIGHT;
            return;
        }

        // Restore the saved position values.
        mParams.gravity = Integer.parseInt(parts[0]);
        mParams.x = Integer.parseInt(parts[1]);
        mParams.y = Integer.parseInt(parts[2]);
        mCurrentCorner = Integer.parseInt(parts[3]);
    }

    /**
     * Validates the parsed position parts from settings.
     * @param parts The string array of position parts ("gravity,x,y,corner").
     * @return {@code true} if the parts are valid, {@code false} otherwise.
     */
    private boolean isValidPositionParts(String[] parts) {
        // Check basic array validity.
        if (parts == null || parts.length != 4) {
            return false;
        }

        // Parse values after validating they are numbers.
        int gravity = Integer.parseInt(parts[0]);
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);
        int cornerIndex = Integer.parseInt(parts[3]);

        // Check gravity is valid (START/END | TOP/BOTTOM).
        if (gravity != (Gravity.START | Gravity.TOP) && gravity != (Gravity.END | Gravity.TOP)
                && gravity != (Gravity.START | Gravity.BOTTOM) && gravity != (Gravity.END
                | Gravity.BOTTOM)) {
            return false;
        }

        // Check coordinates are positive and within screen bounds.
        int screenWidth = mContext.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = mContext.getResources().getDisplayMetrics().heightPixels;
        if (x < 0 || x > screenWidth || y < 0 || y > screenHeight) {
            return false;
        }

        // Check corner index is valid.
        if (cornerIndex < 0 || cornerIndex >= 4) {
            return false;
        }
        return true;
    }

    /**
     * Wraps an existing OnClickListener to add logic for unpausing autoclick if it's paused
     * when a click type button (excluding the pause button itself) is clicked.
     * @param listener The original OnClickListener for the button.
     * @return A new OnClickListener that includes the unpausing logic.
     */
    private View.OnClickListener wrapWithTogglePauseListener(View.OnClickListener listener) {
        return v -> {
            listener.onClick(v);

            // Resumes autoclick if the button is clicked while in a paused state.
            if (mPaused) {
                togglePause();
            }
        };
    }

    /**
     * Updates the icon of the position button based on the current corner.
     * @param corner The corner to set the icon for.
     */
    private void updatePositionButtonIcon(@Corner int corner) {
        switch (corner) {
            case CORNER_TOP_LEFT:
                mPositionButton.setImageDrawable(mPositionTopLeftDrawable);
                break;
            case CORNER_TOP_RIGHT:
                mPositionButton.setImageDrawable(mPositionTopRightDrawable);
                break;
            case CORNER_BOTTOM_LEFT:
                mPositionButton.setImageDrawable(mPositionBottomLeftDrawable);
                break;
            case CORNER_BOTTOM_RIGHT:
            default:
                mPositionButton.setImageDrawable(mPositionBottomRightDrawable);
                break;
        }
    }

    /**
     * Determines the visual corner based on the panel's position on screen.
     *
     * @return The corner that visually represents the panel's current position.
     */
    private @Corner int getVisualCorner() {
        // Get screen dimensions.
        int screenWidth = mContext.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = mContext.getResources().getDisplayMetrics().heightPixels;

        // Get panel's current position on screen.
        int[] location = new int[2];
        mContentView.getLocationOnScreen(location);
        int panelX = location[0];
        int panelY = location[1];

        // Determine which quadrant of the screen the panel is in.
        boolean isOnLeftHalf = panelX < screenWidth / 2;
        boolean isOnTopHalf = panelY < screenHeight / 2;

        // Return the corresponding corner.
        if (isOnLeftHalf) {
            return isOnTopHalf ? CORNER_TOP_LEFT : CORNER_BOTTOM_LEFT;
        } else {
            return isOnTopHalf ? CORNER_TOP_RIGHT : CORNER_BOTTOM_RIGHT;
        }
    }

    /**
     * Applies the appropriate spacing between buttons based on panel state.
     *
     * @param isExpanded Whether the panel is in expanded state.
     */
    private void adjustPanelSpacing(boolean isExpanded) {
        int spacing = (int) mContext.getResources().getDimension(
                R.dimen.accessibility_autoclick_type_panel_button_spacing);

        // Get the button container and button group.
        LinearLayout buttonGroupContainer = mContentView.findViewById(
                R.id.accessibility_autoclick_button_group_container);
        LinearLayout buttonGroup = (LinearLayout) buttonGroupContainer.getChildAt(0);

        if (isExpanded) {
            // When expanded: Apply padding to the button group with rounded background (all sides).
            buttonGroup.setPadding(spacing, spacing, spacing, spacing);
            // Remove extra padding from container.
            buttonGroupContainer.setPadding(0, 0, 0, 0);
        } else {
            // When collapsed: Remove button group padding.
            buttonGroup.setPadding(0, 0, 0, 0);
            // Add extra vertical padding to the button group container.
            buttonGroupContainer.setPadding(0, spacing, 0, spacing);
        }

        // Set end margin on each button (for spacing between buttons) when expanded.
        int buttonSpacing = isExpanded ? spacing : 0;
        for (int i = 0; i < buttonGroup.getChildCount() - 1; i++) {
            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) buttonGroup.getChildAt(i).getLayoutParams();
            params.setMarginEnd(buttonSpacing);
            buttonGroup.getChildAt(i).setLayoutParams(params);
        }
    }

    /**
     * Starts drag operation, capturing initial positions and updating cursor icon.
     */
    public void onDragStart(MotionEvent event) {
        mIsDragging = true;

        // Store initial touch positions.
        mTouchStartX = event.getRawX();
        mTouchStartY = event.getRawY();

        // Store initial panel position relative to screen's top-left corner.
        // getLocationOnScreen provides coordinates relative to the top-left corner of the
        // screen's display. We are using this coordinate system to consistently track the
        // panel's position during drag operations.
        int[] location = new int[2];
        mContentView.getLocationOnScreen(location);
        mPanelStartX = location[0];
        mPanelStartY = location[1];

        // Show grabbing cursor when dragging starts.
        mCurrentCursor = PointerIcon.getSystemIcon(mContext, PointerIcon.TYPE_GRABBING);
        mContentView.setPointerIcon(mCurrentCursor);
    }

    /**
     * Updates panel position during drag.
     */
    public void onDragMove(MotionEvent event) {
        mIsDragging = true;

        // Calculate touch distance moved from start position.
        float deltaX = event.getRawX() - mTouchStartX;
        float deltaY = event.getRawY() - mTouchStartY;

        // Set panel gravity to TOP|LEFT to match getLocationOnScreen's coordinate system.
        mParams.gravity = Gravity.LEFT | Gravity.TOP;

        // Update panel position, based on Top-Left absolute positioning.
        mParams.x = mPanelStartX + (int) deltaX;

        // Adjust Y by status bar height:
        // Note: mParams.y is relative to the content area (below the status bar),
        // but mPanelStartY uses absolute screen coordinates. Subtract status bar
        // height to align coordinates properly.
        mParams.y = Math.max(0, mPanelStartY + (int) deltaY - mStatusBarHeight);
        mWindowManager.updateViewLayout(mContentView, mParams);

        // Keep grabbing cursor during drag.
        if (mCurrentCursor.getType() != PointerIcon.TYPE_GRABBING) {
            mCurrentCursor = PointerIcon.getSystemIcon(mContext, PointerIcon.TYPE_GRABBING);
            mContentView.setPointerIcon(mCurrentCursor);
        }
    }

    /**
     * Ends drag operation and snaps to the nearest edge.
     */
    public void onDragEnd() {
        if (mIsDragging) {
            mIsDragging = false;
            // When drag ends, snap panel to nearest edge.
            snapToNearestEdge(mParams);
            // Show grab cursor when dragging ends.
            mCurrentCursor = PointerIcon.getSystemIcon(mContext, PointerIcon.TYPE_GRAB);
            mContentView.setPointerIcon(mCurrentCursor);
        }
    }

    /**
     * Returns true if cursor is over content view but not over any buttons.
     */
    public boolean isHoveringDraggableArea(MotionEvent event) {
        if (!mContentView.isHovered()) {
            return false;
        }

        // Get the absolute raw coordinates of the cursor on the screen.
        final float rawX = event.getRawX();
        final float rawY = event.getRawY();

        // Create a reusable array to hold a view's location on the screen.
        final int[] location = new int[2];


        View[] buttons = {mLeftClickButton, mRightClickButton, mDoubleClickButton,
                mScrollButton, mDragButton, mLongPressButton, mPauseButton, mPositionButton};
        for (View button : buttons) {
            if (button.isShown()) {
                // Get the absolute top-left corner of the button on the screen.
                button.getLocationOnScreen(location);
                final int left = location[0];
                final int top = location[1];

                // Calculate the absolute right and bottom edges.
                final int right = left + button.getWidth();
                final int bottom = top + button.getHeight();

                if (rawX >= left && rawX <= right && rawY >= top && rawY <= bottom) {
                    // The cursor is definitively inside this button's bounds.
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Sets up hover listeners to update cursor icons (grab for draggable areas, arrow for buttons).
     */
    private void setupHoverListenersForCursor() {
        View[] mAllButtons = new View[]{
                mLeftClickButton, mRightClickButton, mDoubleClickButton,
                mScrollButton, mDragButton, mLongPressButton,
                mPauseButton, mPositionButton
        };

        // Set elevation and cursor icon as hover behavior for the panel.
        mContentView.setOnHoverListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    v.setElevation(mContext.getResources().getDimensionPixelSize(
                            R.dimen.accessibility_autoclick_panel_hover_elevation));
                    break;

                case MotionEvent.ACTION_HOVER_MOVE:
                    updateCursorIcon(event);
                    break;

                case MotionEvent.ACTION_HOVER_EXIT:
                    v.setElevation(mContext.getResources().getDimensionPixelSize(
                            R.dimen.accessibility_autoclick_panel_resting_elevation));
                    break;
            }
            return true;
        });
    }

    /**
     * Updates cursor based on hover state: grab for draggable areas, arrow for buttons.
     */
    private void updateCursorIcon(MotionEvent event) {
        // Don't update cursor icon while dragging to avoid overriding the grabbing cursor during
        // drag.
        if (mIsDragging) {
            return;
        }
        int cursorType = isHoveringDraggableArea(event)
                ? PointerIcon.TYPE_GRAB : PointerIcon.TYPE_ARROW;
        mCurrentCursor = PointerIcon.getSystemIcon(mContext, cursorType);
        mContentView.setPointerIcon(mCurrentCursor);
    }

    @VisibleForTesting
    boolean getExpansionStateForTesting() {
        return mExpanded;
    }

    @VisibleForTesting
    @NonNull
    AutoclickTypeLinearLayout getContentViewForTesting() {
        return mContentView;
    }

    @VisibleForTesting
    @Corner
    int getCurrentCornerForTesting() {
        return mCurrentCorner;
    }

    @VisibleForTesting
    WindowManager.LayoutParams getLayoutParamsForTesting() {
        return mParams;
    }

    boolean getIsDragging() {
        return mIsDragging;
    }

    @VisibleForTesting
    int getStatusBarHeightForTesting() {
        return mStatusBarHeight;
    }

    PointerIcon getCurrentCursorForTesting() {
        return mCurrentCursor;
    }

    @VisibleForTesting
    void setIsExpandedPanelWiderThanScreenForTesting(boolean isExpandedPanelWiderThanScreen) {
        mIsExpandedPanelWiderThanScreen = isExpandedPanelWiderThanScreen;
    }

    /**
     * Retrieves the layout params for AutoclickIndicatorView, used when it's added to the Window
     * Manager.
     */
    @NonNull
    private WindowManager.LayoutParams getDefaultLayoutParams() {
        final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        layoutParams.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        layoutParams.setFitInsetsTypes(WindowInsets.Type.statusBars()
                | WindowInsets.Type.navigationBars());
        layoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.setTitle(AutoclickTypePanel.class.getSimpleName());
        layoutParams.accessibilityTitle =
                mContext.getString(R.string.accessibility_autoclick_type_settings_panel_title);
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        setPanelPositionForCorner(layoutParams, CORNER_BOTTOM_RIGHT);
        return layoutParams;
    }
}
