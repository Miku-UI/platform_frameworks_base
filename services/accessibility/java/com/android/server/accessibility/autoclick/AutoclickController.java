/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.view.MotionEvent.BUTTON_PRIMARY;
import static android.view.MotionEvent.BUTTON_SECONDARY;
import static android.view.accessibility.AccessibilityManager.AUTOCLICK_CURSOR_AREA_SIZE_DEFAULT;
import static android.view.accessibility.AccessibilityManager.AUTOCLICK_DELAY_DEFAULT;
import static android.view.accessibility.AccessibilityManager.AUTOCLICK_DELAY_WITH_INDICATOR_DEFAULT;
import static android.view.accessibility.AccessibilityManager.AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT_DEFAULT;
import static android.view.accessibility.AccessibilityManager.AUTOCLICK_REVERT_TO_LEFT_CLICK_DEFAULT;

import static com.android.server.accessibility.autoclick.AutoclickIndicatorView.SHOW_INDICATOR_DELAY_TIME;
import static com.android.server.accessibility.autoclick.AutoclickScrollPanel.DIRECTION_NONE;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AUTOCLICK_TYPE_DOUBLE_CLICK;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AUTOCLICK_TYPE_DRAG;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AUTOCLICK_TYPE_LEFT_CLICK;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AUTOCLICK_TYPE_LONG_PRESS;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AUTOCLICK_TYPE_RIGHT_CLICK;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AutoclickType;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.ClickPanelControllerInterface;

import android.accessibilityservice.AccessibilityTrace;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityThread;
import android.content.ComponentCallbacks;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import androidx.annotation.VisibleForTesting;

import com.android.internal.accessibility.util.AccessibilityUtils;
import com.android.server.accessibility.AccessibilityTraceManager;
import com.android.server.accessibility.BaseEventStreamTransformation;
import com.android.server.accessibility.Flags;

/**
 * Implements "Automatically click on mouse stop" feature, targeting users with limited dexterity
 * struggle to use a mouse due to the fine motor control required for clicking and cursor control.
 * The feature supports several types of auto click, covering most if not all mouse functionalities,
 * naming left click by default, right click, double click, scroll, drag and move, long press.
 *
 * If enabled, it will observe motion events from mouse source, and send click event sequence
 * shortly after mouse stops moving. The click will only be performed if mouse movement had been
 * actually detected.
 *
 * Movement detection has tolerance to jitter that may be caused by poor motor control to prevent:
 * <ul>
 *   <li>Initiating unwanted clicks with no mouse movement.</li>
 *   <li>Autoclick never occurring after mouse arriving at target.</li>
 * </ul>
 *
 * Non-mouse motion events, key events (excluding modifiers) and non-movement mouse events cancel
 * the automatic click.
 *
 * It is expected that each instance will receive mouse events from a single mouse device. User of
 * the class should handle cases where multiple mouse devices are present.
 *
 * Each instance is associated to a single user (and it does not handle user switch itself).
 */
public class AutoclickController extends BaseEventStreamTransformation implements
        ComponentCallbacks {

    // Default duration between mouse movement stops and the auto click happens.
    public static final int DEFAULT_AUTOCLICK_DELAY_TIME = Flags.enableAutoclickIndicator()
            ? AUTOCLICK_DELAY_WITH_INDICATOR_DEFAULT : AUTOCLICK_DELAY_DEFAULT;

    // Time interval between two left click events are considered as a double click.
    public static long DOUBLE_CLICK_MINIMUM_TIMEOUT = ViewConfiguration.getDoubleTapMinTime();

    // Duration before a press turns into a long press.
    // Factor 1.5 is needed, otherwise a long press is not safely detected.
    public static final long LONG_PRESS_TIMEOUT =
            (long) (ViewConfiguration.getLongPressTimeout() * 1.5f);

    private static final String LOG_TAG = AutoclickController.class.getSimpleName();

    // When cursor hovers over auto scrolling direction panel, continuous scrolling happens. These
    // two constants are the auto scrolling distance per scrolling interval and the value of scroll
    // interval. They control how fast the continuous scrolling performs. The values could be
    // further fine tuned based on user feedback.
    private static final float SCROLL_AMOUNT = 0.5f;
    protected static final long CONTINUOUS_SCROLL_INTERVAL = 30;
    // Timestamp when autoclick was enabled, used to calculate session duration.
    private final long mAutoclickEnabledTimestamp;
    private Handler mContinuousScrollHandler;
    private Runnable mContinuousScrollRunnable;

    private final AccessibilityTraceManager mTrace;
    private final Context mContext;
    private int mCursorAreaSize;
    private final int mUserId;

    // The position where scroll actually happens.
    @VisibleForTesting
    float mScrollCursorX;
    @VisibleForTesting
    float mScrollCursorY;

    // Lazily created on the first mouse motion event.
    @VisibleForTesting
    ClickScheduler mClickScheduler;
    @VisibleForTesting
    AutoclickSettingsObserver mAutoclickSettingsObserver;
    @VisibleForTesting
    AutoclickIndicatorScheduler mAutoclickIndicatorScheduler;
    @VisibleForTesting
    AutoclickIndicatorView mAutoclickIndicatorView;
    @VisibleForTesting
    AutoclickTypePanel mAutoclickTypePanel;
    @VisibleForTesting
    AutoclickScrollPanel mAutoclickScrollPanel;
    private WindowManager mWindowManager;

    // Default click type is left-click.
    private @AutoclickType int mActiveClickType = AUTOCLICK_TYPE_LEFT_CLICK;

    // Default scroll direction is DIRECTION_NONE.
    @VisibleForTesting
    protected @AutoclickScrollPanel.ScrollDirection int mHoveredDirection = DIRECTION_NONE;

    // True during the duration of a dragging event.
    private boolean mDragModeIsDragging = false;

    // The MotionEvent downTime attribute associated with the originating click for a dragging
    // move.
    private long mDragModeClickDownTime;

    // True during the duration of a long press event.
    private boolean mHasOngoingLongPress = false;

    // The MotionEvent downTime attribute associated with the originating click for a long press
    // event.
    private long mLongPressDownTime;

    private boolean mHasOngoingDoubleClick = false;

    /**
     * Controller for the auto click type UI panel, allowing users to 1) select what type of auto
     * click they want to perform, 2) pause the auto click, and 3) move the UI panel itself to a
     * different location on screen. See {@code AutoclickTypePanel} class.
     */
    @VisibleForTesting
    final ClickPanelControllerInterface clickPanelController =
            new ClickPanelControllerInterface() {
                @Override
                public void handleAutoclickTypeChange(@AutoclickType int clickType) {
                    mActiveClickType = clickType;

                    // Hide scroll panel when type is not scroll.
                    if (clickType != AUTOCLICK_TYPE_SCROLL && mAutoclickScrollPanel != null) {
                        mAutoclickScrollPanel.hide();
                    }

                    if (clickType != AUTOCLICK_TYPE_DRAG && mDragModeIsDragging) {
                        mClickScheduler.clearDraggingState();
                    }
                }

                @Override
                public void toggleAutoclickPause(boolean paused) {
                    if (paused) {
                        cancelPendingClick();
                        if (mActiveClickType == AUTOCLICK_TYPE_SCROLL) {
                            if (mAutoclickScrollPanel != null) {
                                mAutoclickScrollPanel.hide();
                            }
                            stopContinuousScroll();
                        }
                    }
                }

                @Override
                public void onHoverChange(boolean hovered) {
                    // Cancel all pending clicks when the mouse moves outside the panel while
                    // autoclick is still paused.
                    if (!hovered && isPaused()) {
                        cancelPendingClick();
                    }
                }
            };

    /**
     * Controller for auto scrolling UI panel, allowing users to perform auto scrolling on different
     * directions and exit the scrolling UI panel itself. See {@code AutoclickScrollPanel} class.
     */
    @VisibleForTesting
    final AutoclickScrollPanel.ScrollPanelControllerInterface mScrollPanelController =
            new AutoclickScrollPanel.ScrollPanelControllerInterface() {
                @Override
                public void onHoverButtonChange(
                        @AutoclickScrollPanel.ScrollDirection int direction,
                        boolean hovered) {
                    // Update the hover direction.
                    if (hovered) {
                        mHoveredDirection = direction;

                        // For exit button, return early and the autoclick system will handle the
                        // countdown then exit scroll mode.
                        if (direction == AutoclickScrollPanel.DIRECTION_EXIT) {
                            return;
                        }

                        // For scroll directions, start continuous scrolling.
                        if (direction != DIRECTION_NONE) {
                            startContinuousScroll(direction);
                        }
                    } else if (mHoveredDirection == direction) {
                        // If not hovered, stop scrolling — but only if the mouse leaves the same
                        // button that started it. This avoids stopping the scroll when the mouse
                        // briefly moves over other buttons.
                        stopContinuousScroll();
                    }
                }

                @Override
                public void onExitScrollMode() {
                    exitScrollMode();
                }
            };

    @VisibleForTesting InputManagerWrapper mInputManagerWrapper;

    private final InputManager.InputDeviceListener mInputDeviceListener =
            new InputManager.InputDeviceListener() {
                // True when the pointing device is connected, including mouse, touchpad, etc.
                private boolean mIsPointingDeviceConnected = false;

                // True when the autoclick type panel is temporarily hidden due to the pointing
                // device being disconnected.
                private boolean mTemporaryHideAutoclickTypePanel = false;

                @Override
                public void onInputDeviceAdded(int deviceId) {
                    onInputDeviceChanged(deviceId);
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    onInputDeviceChanged(deviceId);
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    boolean wasConnected = mIsPointingDeviceConnected;
                    mIsPointingDeviceConnected = false;
                    for (final int id : mInputManagerWrapper.getInputDeviceIds()) {
                        final InputDeviceWrapper device = mInputManagerWrapper.getInputDevice(id);
                        if (device == null || !device.isEnabled() || device.isVirtual()) {
                            continue;
                        }
                        if (device.supportsSource(InputDevice.SOURCE_MOUSE)
                                || device.supportsSource(InputDevice.SOURCE_TOUCHPAD)) {
                            mIsPointingDeviceConnected = true;
                            break;
                        }
                    }

                    // If the device state did not change, do nothing.
                    if (wasConnected == mIsPointingDeviceConnected) {
                        return;
                    }

                    // Pointing device state changes from connected to disconnected.
                    if (!mIsPointingDeviceConnected) {
                        if (mAutoclickTypePanel != null) {
                            mTemporaryHideAutoclickTypePanel = true;
                            mAutoclickTypePanel.hide();

                            if (mAutoclickScrollPanel != null) {
                                mAutoclickScrollPanel.hide();
                            }
                        }

                    // Pointing device state changes from disconnected to connected and the panel
                    // was temporarily hidden due to the pointing device being disconnected.
                    } else if (mTemporaryHideAutoclickTypePanel && mIsPointingDeviceConnected) {
                        if (mAutoclickTypePanel != null) {
                            mTemporaryHideAutoclickTypePanel = false;
                            mAutoclickTypePanel.show();

                            // No need to explicitly show the scroll panel here since we don't know
                            // the cursor position when the pointing device is connected. If the
                            // user disconnects the pointing device in scroll mode, another auto
                            // click will trigger the scroll panel to be shown.
                        }
                    }
                }
            };

    public AutoclickController(Context context, int userId, AccessibilityTraceManager trace) {
        mTrace = trace;
        mContext = context;
        mUserId = userId;

        // Record when autoclick is enabled, and store the enabled timestamp.
        mAutoclickEnabledTimestamp = SystemClock.elapsedRealtime();
        AutoclickLogger.logAutoclickEnabled();
    }

    @Override
    public void onMotionEvent(MotionEvent event, MotionEvent rawEvent, int policyFlags) {
        if (mTrace.isA11yTracingEnabledForTypes(AccessibilityTrace.FLAGS_INPUT_FILTER)) {
            mTrace.logTrace(LOG_TAG + ".onMotionEvent", AccessibilityTrace.FLAGS_INPUT_FILTER,
                    "event=" + event + ";rawEvent=" + rawEvent + ";policyFlags=" + policyFlags);
        }
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (mClickScheduler == null) {
                Handler handler = new Handler(mContext.getMainLooper());
                if (Flags.enableAutoclickIndicator()) {
                    initiateAutoclickIndicator(handler);
                }

                mClickScheduler = new ClickScheduler(
                        handler, DEFAULT_AUTOCLICK_DELAY_TIME);
                mAutoclickSettingsObserver = new AutoclickSettingsObserver(mUserId, handler);
                mAutoclickSettingsObserver.start(
                        mContext.getContentResolver(),
                        mClickScheduler,
                        mAutoclickIndicatorScheduler);
            }

            if (mAutoclickTypePanel != null && mAutoclickTypePanel.getIsDragging()
                    && event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE) {
                mAutoclickTypePanel.onDragMove(event);
            }

            if (!isPaused()) {
                scheduleClick(event, policyFlags);

                // When dragging, HOVER_MOVE events need to be manually converted to MOVE events
                // using the initiating click's down time to simulate dragging.
                if (mDragModeIsDragging
                        && event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE) {
                    event.setAction(MotionEvent.ACTION_MOVE);
                    event.setDownTime(mDragModeClickDownTime);
                    event.setButtonState(BUTTON_PRIMARY);
                }
            }
        } else {
            cancelPendingClick();
        }

        super.onMotionEvent(event, rawEvent, policyFlags);
    }

    /**
     * Autoclick indicator is a ring shape animation on the cursor location indicating when the
     * click will happen. See {@code AutoclickIndicatorView} class.
     */
    private void initiateAutoclickIndicator(Handler handler) {
        // Try to get the SystemUI context which has dynamic colors properly themed.
        // Fall back to the regular context if it's not available (in tests).
        Context uiContext;
        if (!mContext.getClass().getSimpleName().contains("Testable")) {
            // Use SystemUI context in production.
            uiContext = ActivityThread.currentActivityThread().getSystemUiContext();
        } else {
            // Use the original context in test environments.
            uiContext = mContext;
        }
        mAutoclickIndicatorScheduler = new AutoclickIndicatorScheduler(handler);
        mAutoclickIndicatorView = new AutoclickIndicatorView(uiContext);
        mWindowManager = mContext.getSystemService(WindowManager.class);
        mAutoclickTypePanel = new AutoclickTypePanel(
                uiContext, mWindowManager, mUserId, clickPanelController);
        mAutoclickScrollPanel = new AutoclickScrollPanel(
                uiContext, mWindowManager, mScrollPanelController);

        // Initialize continuous scroll handler and runnable.
        mContinuousScrollHandler = new Handler(handler.getLooper());
        mContinuousScrollRunnable = new Runnable() {
            @Override
            public void run() {
                handleScroll(mHoveredDirection);
                mContinuousScrollHandler.postDelayed(this, CONTINUOUS_SCROLL_INTERVAL);
            }
        };

        mAutoclickTypePanel.show();
        mContext.registerComponentCallbacks(this);
        mWindowManager.addView(mAutoclickIndicatorView, mAutoclickIndicatorView.getLayoutParams());

        if (mInputManagerWrapper == null) {
            mInputManagerWrapper =
                    new InputManagerWrapper(mContext.getSystemService(InputManager.class));
        }
        mInputManagerWrapper.registerInputDeviceListener(mInputDeviceListener, handler);
        // Trigger listener to register currently connected input device.
        mInputDeviceListener.onInputDeviceChanged(/* deviceId= */ 0);
    }

    @Override
    public void onKeyEvent(KeyEvent event, int policyFlags) {
        if (mTrace.isA11yTracingEnabledForTypes(AccessibilityTrace.FLAGS_INPUT_FILTER)) {
            mTrace.logTrace(LOG_TAG + ".onKeyEvent", AccessibilityTrace.FLAGS_INPUT_FILTER,
                    "event=" + event + ";policyFlags=" + policyFlags);
        }
        if (mClickScheduler != null) {
            if (KeyEvent.isModifierKey(event.getKeyCode())) {
                mClickScheduler.updateMetaState(event.getMetaState());
            } else {
                cancelPendingClick();
            }
        }

        super.onKeyEvent(event, policyFlags);
    }

    @Override
    public void clearEvents(int inputSource) {
        if (inputSource == InputDevice.SOURCE_MOUSE) {
            cancelPendingClick();
        }

        if (mAutoclickScrollPanel != null) {
            mAutoclickScrollPanel.hide();
        }

        super.clearEvents(inputSource);
    }

    @Override
    public void onDestroy() {
        mContext.unregisterComponentCallbacks(this);
        if (mInputManagerWrapper != null) {
            mInputManagerWrapper.unregisterInputDeviceListener(mInputDeviceListener);
        }

        if (mAutoclickSettingsObserver != null) {
            mAutoclickSettingsObserver.stop();
            mAutoclickSettingsObserver = null;
        }
        if (mClickScheduler != null) {
            // Log the current autoclick settings state (delay, cursor size etc.)
            AutoclickLogger.logAutoclickSettingsState(
                    mClickScheduler.mDelay,
                    mCursorAreaSize,
                    mClickScheduler.mIgnoreMinorCursorMovement,
                    mClickScheduler.mRevertToLeftClick);
            mClickScheduler.cancel();
            mClickScheduler = null;
        }

        if (mAutoclickIndicatorScheduler != null) {
            mAutoclickIndicatorScheduler.cancel();
            mAutoclickIndicatorScheduler = null;
            mWindowManager.removeView(mAutoclickIndicatorView);
            mAutoclickTypePanel.hide();
        }

        if (mAutoclickScrollPanel != null) {
            mAutoclickScrollPanel.hide();
            mAutoclickScrollPanel = null;
        }

        if (mContinuousScrollHandler != null) {
            mContinuousScrollHandler.removeCallbacks(mContinuousScrollRunnable);
            mContinuousScrollHandler = null;
        }

        // Calculate session duration and log when autoclick is disabled.
        if (mAutoclickEnabledTimestamp > 0) {
            int sessionDurationSeconds =
                    (int) ((SystemClock.elapsedRealtime() - mAutoclickEnabledTimestamp) / 1000);
            AutoclickLogger.logAutoclickSessionDuration(sessionDurationSeconds);
        }
    }

    private void scheduleClick(MotionEvent event, int policyFlags) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_MOVE: {
                if (event.getPointerCount() == 1) {
                    mClickScheduler.update(event, policyFlags);
                } else {
                    cancelPendingClick();
                }
            }
            break;
            // Ignore hover enter and exit.
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_EXIT:
                break;
            default:
                cancelPendingClick();
        }
    }

    /**
     * Autoclick type panel contains a pause button, upon clicking (either by the autoclick feature
     * itself or a manual click), the auto clicking will temporary be paused. This is different from
     * turning off the autoclick feature. Pausing the autoclick keeps the click type panel visible,
     * while turning off the autoclick feature will also remove the click type panel from screen.
     * The other key point is that hovering over the click type panel will always trigger auto click
     * even during the pause state. This is to allow dexterity users to resume from pause state
     * without really clicking the mouse button.
     */
    private boolean isPaused() {
        return Flags.enableAutoclickIndicator() && mAutoclickTypePanel.isPaused()
                && !isClickTypePanelHovered();
    }

    private boolean isClickTypePanelHovered() {
        return Flags.enableAutoclickIndicator() && mAutoclickTypePanel.isHovered();
    }

    private boolean isScrollPanelHovered() {
        return Flags.enableAutoclickIndicator() && mAutoclickScrollPanel.isHovered();
    }

    private void cancelPendingClick() {
        if (mClickScheduler != null) {
            mClickScheduler.cancel();
        }
        if (mAutoclickIndicatorScheduler != null) {
            mAutoclickIndicatorScheduler.cancel();
        }
        if (mAutoclickTypePanel != null && mAutoclickTypePanel.getIsDragging()) {
            mAutoclickTypePanel.onDragEnd();
        }
    }

    /**
     * Handles scroll operations in the specified direction.
     */
    private void handleScroll(@AutoclickScrollPanel.ScrollDirection int direction) {
        // Remove the autoclick indicator view when hovering on directional buttons.
        if (mAutoclickIndicatorScheduler != null) {
            mAutoclickIndicatorScheduler.cancel();
            if (mAutoclickIndicatorView != null) {
                mAutoclickIndicatorView.clearIndicator();
            }
        }

        final long now = SystemClock.uptimeMillis();

        // Create pointer properties.
        PointerProperties[] pointerProps = new PointerProperties[1];
        pointerProps[0] = new PointerProperties();
        pointerProps[0].id = 0;
        pointerProps[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

        // Create pointer coordinates at the scroll cursor position.
        PointerCoords[] pointerCoords = new PointerCoords[1];
        pointerCoords[0] = new PointerCoords();
        pointerCoords[0].x = mScrollCursorX;
        pointerCoords[0].y = mScrollCursorY;

        // Set scroll values based on direction.
        switch (direction) {
            case AutoclickScrollPanel.DIRECTION_UP:
                pointerCoords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, SCROLL_AMOUNT);
                break;
            case AutoclickScrollPanel.DIRECTION_DOWN:
                pointerCoords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, -SCROLL_AMOUNT);
                break;
            case AutoclickScrollPanel.DIRECTION_LEFT:
                pointerCoords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, SCROLL_AMOUNT);
                break;
            case AutoclickScrollPanel.DIRECTION_RIGHT:
                pointerCoords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, -SCROLL_AMOUNT);
                break;
            case AutoclickScrollPanel.DIRECTION_EXIT:
            case AutoclickScrollPanel.DIRECTION_NONE:
            default:
                return;
        }

        // Get device ID from last motion event if possible.
        int deviceId = mClickScheduler != null && mClickScheduler.mLastMotionEvent != null
                ? mClickScheduler.mLastMotionEvent.getDeviceId() : 0;

        // Create a scroll event.
        MotionEvent scrollEvent = MotionEvent.obtain(
                /* downTime= */ now, /* eventTime= */ now,
                MotionEvent.ACTION_SCROLL, /* pointerCount= */ 1, pointerProps,
                pointerCoords, /* metaState= */ 0, /* actionButton= */ 0, /* xPrecision= */
                1.0f, /* yPrecision= */ 1.0f, deviceId, /* edgeFlags= */ 0,
                InputDevice.SOURCE_MOUSE, /* flags= */ 0);

        // Send the scroll event.
        super.onMotionEvent(scrollEvent, scrollEvent, mClickScheduler.mEventPolicyFlags);

        // Clean up.
        scrollEvent.recycle();
    }

    /**
     * Exits scroll mode and hides the scroll panel UI.
     */
    public void exitScrollMode() {
        if (mAutoclickScrollPanel != null) {
            mAutoclickScrollPanel.hide();
        }
        stopContinuousScroll();

        // Reset click type to left click if necessary.
        if (mClickScheduler != null) {
            mClickScheduler.resetSelectedClickTypeIfNecessary();
        }
    }

    private void startContinuousScroll(@AutoclickScrollPanel.ScrollDirection int direction) {
        if (mContinuousScrollHandler != null) {
            handleScroll(direction);
            mContinuousScrollHandler.postDelayed(mContinuousScrollRunnable,
                    CONTINUOUS_SCROLL_INTERVAL);
        }
    }

    private void stopContinuousScroll() {
        if (mContinuousScrollHandler != null) {
            mContinuousScrollHandler.removeCallbacks(mContinuousScrollRunnable);
        }
        mHoveredDirection = DIRECTION_NONE;
    }


    @VisibleForTesting
    void onChangeForTesting(boolean selfChange, Uri uri) {
        mAutoclickSettingsObserver.onChange(selfChange, uri);
    }

    @VisibleForTesting
    boolean isDraggingForTesting() {
        return mDragModeIsDragging;
    }

    @VisibleForTesting
    boolean hasOngoingLongPressForTesting() {
        return mHasOngoingLongPress;
    }

    @VisibleForTesting
    @AutoclickType int getActiveClickTypeForTest() {
        return mActiveClickType;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        // When system configuration is changed, update the indicator view
        // and type panel configuration.
        if (mAutoclickIndicatorView != null) {
            mAutoclickIndicatorView.onConfigurationChanged(newConfig);
        }
        if (mAutoclickTypePanel != null) {
            mAutoclickTypePanel.onConfigurationChanged(newConfig);
        }
        if (mAutoclickScrollPanel != null) {
            mAutoclickScrollPanel.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onLowMemory() {

    }

    /** A wrapper for the final InputManager class, to allow mocking in tests. */
    @VisibleForTesting
    public static class InputManagerWrapper {
        private final InputManager mInputManager;

        InputManagerWrapper(InputManager inputManager) {
            mInputManager = inputManager;
        }

        public void registerInputDeviceListener(
                InputManager.InputDeviceListener listener, Handler handler) {
            if (mInputManager == null) return;
            mInputManager.registerInputDeviceListener(listener, handler);
        }

        public void unregisterInputDeviceListener(InputManager.InputDeviceListener listener) {
            if (mInputManager == null) return;
            mInputManager.unregisterInputDeviceListener(listener);
        }

        public int[] getInputDeviceIds() {
            if (mInputManager == null) return new int[0];
            return mInputManager.getInputDeviceIds();
        }

        public InputDeviceWrapper getInputDevice(int id) {
            if (mInputManager == null) return null;
            InputDevice device = mInputManager.getInputDevice(id);
            return device == null ? null : new InputDeviceWrapper(device);
        }
    }

    /** A wrapper for the final InputDevice class, to allow mocking in tests. */
    @VisibleForTesting
    public static class InputDeviceWrapper {
        private final InputDevice mInputDevice;

        InputDeviceWrapper(InputDevice inputDevice) {
            mInputDevice = inputDevice;
        }

        public boolean isEnabled() {
            return mInputDevice.isEnabled();
        }

        public boolean isVirtual() {
            return mInputDevice.isVirtual();
        }

        public boolean supportsSource(int source) {
            return mInputDevice.supportsSource(source);
        }

        public int getSources() {
            return mInputDevice.getSources();
        }
    }

    /**
     * Observes and updates various autoclick setting values.
     */
    final private static class AutoclickSettingsObserver extends ContentObserver {
        /**
         * URI used to identify the autoclick delay setting with content resolver. This is the
         * duration between mouse movement stops and the auto click happens.
         */
        private final Uri mAutoclickDelaySettingUri = Settings.Secure.getUriFor(
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_DELAY);

        /**
         * URI used to identify the autoclick cursor area size setting with content resolver. This
         * setting has two functions: 1) it corresponds to the radius of the ring shape animated
         * click indicator visually; 2) it is the movement slop of whether a mouse movement is
         * considered as minor cursor movement. When the "ignore minor cursor movement" setting is
         * on, this minor movement will not interrupt the current click scheduler.
         */
        private final Uri mAutoclickCursorAreaSizeSettingUri =
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE);

        /**
         * URI used to identify ignore minor cursor movement setting with content resolver. If it is
         * on, a cursor movement within the slop will be ignored and won't restart the current click
         * scheduler.
         */
        private final Uri mAutoclickIgnoreMinorCursorMovementSettingUri =
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT);

        /**
         * URI used to identify whether to revert to left click after a click action setting with
         * content resolver. When it is on, after any click type is triggered once such as a double
         * click, the click type will be changed to the default left click type. This setting could
         * be useful based on the assumption that the left click is the most common action. Other
         * click types are for one time usage. For example, when the user wants to click a button
         * on the context menu, one would perform right click once, and then left click.
         */
        private final Uri mAutoclickRevertToLeftClickSettingUri =
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK);

        private ContentResolver mContentResolver;
        private ClickScheduler mClickScheduler;
        private AutoclickIndicatorScheduler mAutoclickIndicatorScheduler;
        private final int mUserId;

        public AutoclickSettingsObserver(int userId, Handler handler) {
            super(handler);
            mUserId = userId;
        }

        /**
         * Starts the observer. And makes sure various up-to-date settings are propagated.
         *
         * @param contentResolver Content resolver that should be observed for setting's value
         *                        changes.
         * @param clickScheduler  ClickScheduler that should be updated when click delay changes.
         * @throws IllegalStateException If internal state is already setup when the method is
         *                               called.
         * @throws NullPointerException  If any of the arguments is a null pointer.
         */
        public void start(
                @NonNull ContentResolver contentResolver,
                @NonNull ClickScheduler clickScheduler,
                @Nullable AutoclickIndicatorScheduler autoclickIndicatorScheduler) {
            if (mContentResolver != null || mClickScheduler != null) {
                throw new IllegalStateException("Observer already started.");
            }
            if (contentResolver == null) {
                throw new NullPointerException("contentResolver not set.");
            }
            if (clickScheduler == null) {
                throw new NullPointerException("clickScheduler not set.");
            }

            mContentResolver = contentResolver;
            mClickScheduler = clickScheduler;
            mAutoclickIndicatorScheduler = autoclickIndicatorScheduler;
            mContentResolver.registerContentObserver(
                    mAutoclickDelaySettingUri,
                    /* notifyForDescendants= */ false,
                    /* observer= */ this,
                    mUserId);

            // Initialize mClickScheduler's initial delay value.
            onChange(/* selfChange= */ true, mAutoclickDelaySettingUri);

            if (Flags.enableAutoclickIndicator()) {
                // Register observer to listen to cursor area size setting change.
                mContentResolver.registerContentObserver(
                        mAutoclickCursorAreaSizeSettingUri,
                        /* notifyForDescendants= */ false,
                        /* observer= */ this,
                        mUserId);
                // Initialize mAutoclickIndicatorView's initial size.
                onChange(/* selfChange= */ true, mAutoclickCursorAreaSizeSettingUri);

                // Register observer to listen to ignore minor cursor movement setting change.
                mContentResolver.registerContentObserver(
                        mAutoclickIgnoreMinorCursorMovementSettingUri,
                        /* notifyForDescendants= */ false,
                        /* observer= */ this,
                        mUserId);
                onChange(/* selfChange= */ true, mAutoclickIgnoreMinorCursorMovementSettingUri);

                // Register observer to listen to revert to left click setting change.
                mContentResolver.registerContentObserver(
                        mAutoclickRevertToLeftClickSettingUri,
                        /* notifyForDescendants= */ false,
                        /* observer= */ this,
                        mUserId);
                onChange(/* selfChange= */ true, mAutoclickRevertToLeftClickSettingUri);
            }
        }

        /**
         * Stops the the observer. Should only be called if the observer has been started.
         *
         * @throws IllegalStateException If internal state hasn't yet been initialized by calling
         *                               {@link #start}.
         */
        public void stop() {
            if (mContentResolver == null || mClickScheduler == null) {
                throw new IllegalStateException("AutoclickSettingsObserver not started.");
            }

            mContentResolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (mAutoclickDelaySettingUri.equals(uri)) {
                int delay =
                        Settings.Secure.getIntForUser(
                                mContentResolver,
                                Settings.Secure.ACCESSIBILITY_AUTOCLICK_DELAY,
                                DEFAULT_AUTOCLICK_DELAY_TIME,
                                mUserId);
                mClickScheduler.updateDelay(delay);
            }

            if (Flags.enableAutoclickIndicator()) {
                if (mAutoclickCursorAreaSizeSettingUri.equals(uri)) {
                    int size =
                            Settings.Secure.getIntForUser(
                                    mContentResolver,
                                    Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                                    AUTOCLICK_CURSOR_AREA_SIZE_DEFAULT,
                                    mUserId);
                    if (mAutoclickIndicatorScheduler != null) {
                        mAutoclickIndicatorScheduler.updateCursorAreaSize(size);
                    }
                    mClickScheduler.updateMovementSlop(size);
                }

                if (mAutoclickIgnoreMinorCursorMovementSettingUri.equals(uri)) {
                    boolean ignoreMinorCursorMovement =
                            Settings.Secure.getIntForUser(
                                    mContentResolver,
                                    Settings.Secure
                                            .ACCESSIBILITY_AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT,
                                    AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT_DEFAULT
                                            ? AccessibilityUtils.State.ON
                                            : AccessibilityUtils.State.OFF,
                                    mUserId)
                                    == AccessibilityUtils.State.ON;
                    mClickScheduler.setIgnoreMinorCursorMovement(ignoreMinorCursorMovement);
                }

                if (mAutoclickRevertToLeftClickSettingUri.equals(uri)) {
                    boolean revertToLeftClick =
                            Settings.Secure.getIntForUser(
                                    mContentResolver,
                                    Settings.Secure
                                            .ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK,
                                    AUTOCLICK_REVERT_TO_LEFT_CLICK_DEFAULT
                                            ? AccessibilityUtils.State.ON
                                            : AccessibilityUtils.State.OFF,
                                    mUserId)
                                    == AccessibilityUtils.State.ON;
                    mClickScheduler.setRevertToLeftClick(revertToLeftClick);
                }
            }
        }
    }

    /**
     * Schedules and draws the ring shape animated click indicator that should be initiated when
     * mouse pointer stops moving.
     */
    private final class AutoclickIndicatorScheduler implements Runnable {
        private final Handler mHandler;
        private long mScheduledShowIndicatorTime;
        private boolean mIndicatorCallbackActive = false;

        public AutoclickIndicatorScheduler(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            // Indicator was rescheduled after task was posted. Post new run task at updated time.
            if (now < mScheduledShowIndicatorTime) {
                mHandler.postDelayed(this, mScheduledShowIndicatorTime - now);
                return;
            }

            mAutoclickIndicatorView.redrawIndicator();
            mIndicatorCallbackActive = false;
        }

        public void update() {
            long scheduledShowIndicatorTime =
                    SystemClock.uptimeMillis() + SHOW_INDICATOR_DELAY_TIME;
            // If there already is a scheduled show indicator at time before the updated time, just
            // update scheduled time.
            if (mIndicatorCallbackActive
                    && scheduledShowIndicatorTime > mScheduledShowIndicatorTime) {
                // Clear any existing indicator.
                mAutoclickIndicatorView.clearIndicator();
                mScheduledShowIndicatorTime = scheduledShowIndicatorTime;
                return;
            }

            if (mIndicatorCallbackActive) {
                mHandler.removeCallbacks(this);
            }

            mIndicatorCallbackActive = true;
            mScheduledShowIndicatorTime = scheduledShowIndicatorTime;

            mHandler.postDelayed(this, SHOW_INDICATOR_DELAY_TIME);
        }

        public void cancel() {
            if (!mIndicatorCallbackActive) {
                return;
            }

            mIndicatorCallbackActive = false;
            mScheduledShowIndicatorTime = -1;
            mHandler.removeCallbacks(this);
        }

        // Cursor area size corresponds to the ring indicator radius size.
        public void updateCursorAreaSize(int size) {
            mCursorAreaSize = size;
            mAutoclickIndicatorView.setRadius(mCursorAreaSize);
        }
    }

    /**
     * Schedules and performs click event sequence that should be initiated when mouse pointer stops
     * moving. The click is first scheduled when a mouse movement is detected, and then further
     * delayed on every sufficient mouse movement.
     */
    @VisibleForTesting
    final class ClickScheduler implements Runnable {
        /**
         * Default minimal distance pointer has to move relative to anchor in order for movement not
         * to be discarded as noise. Anchor is the position of the last MOVE event that was not
         * considered noise.
         */
        private static final double DEFAULT_MOVEMENT_SLOP = 20f;

        /**
         * A reduced minimal distance to make the closely spaced buttons easier to click. Used when
         * the pointer is hovering either the click type panel or the scroll panel.
         */
        private static final double PANEL_HOVERED_SLOP = 5f;

        private double mMovementSlop = DEFAULT_MOVEMENT_SLOP;

        /** Whether the minor cursor movement should be ignored. */
        private boolean mIgnoreMinorCursorMovement = AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT_DEFAULT;

        /** Whether the autoclick type reverts to left click once performing an action. */
        private boolean mRevertToLeftClick = AUTOCLICK_REVERT_TO_LEFT_CLICK_DEFAULT;

        /** Whether there is pending click. */
        private boolean mActive;

        /** If active, time at which pending click is scheduled. */
        private long mScheduledClickTime;

        /** Last observed motion event. null if no events have been observed yet. */
        private MotionEvent mLastMotionEvent;

        /** Last observed motion event's policy flags. */
        private int mEventPolicyFlags;

        /** Current meta state. This value will be used as meta state for click event sequence. */
        private int mMetaState;

        /** Last observed panel hovered state when click was scheduled. */
        private boolean mHoveredState;

        /**
         * The current anchor's coordinates. Should be ignored if #mLastMotionEvent is null.
         * Note that these are not necessary coords of #mLastMotionEvent (because last observed
         * motion event may have been labeled as noise).
         */
        private PointerCoords mAnchorCoords;

        /** Delay that should be used to schedule click. */
        private int mDelay;

        /** Handler for scheduling delayed operations. */
        private Handler mHandler;

        private PointerProperties mTempPointerProperties[];
        private PointerCoords mTempPointerCoords[];

        public ClickScheduler(Handler handler, int delay) {
            mHandler = handler;

            mLastMotionEvent = null;
            resetInternalState();
            mDelay = delay;
            mAnchorCoords = new PointerCoords();
        }

        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();

            // Click was rescheduled after task was posted. Post new run task at updated time.
            if (now < mScheduledClickTime) {
                mHandler.postDelayed(this, mScheduledClickTime - now);
                return;
            }

            sendClick();
            // Hold off resetting internal state until double click complete.
            if (!mHasOngoingDoubleClick) {
                resetInternalState();
            }


            boolean stillDragging = mActiveClickType == AUTOCLICK_TYPE_DRAG
                    && mDragModeIsDragging;
            boolean inScrollMode =
                    mActiveClickType == AUTOCLICK_TYPE_SCROLL && mAutoclickScrollPanel != null
                            && mAutoclickScrollPanel.isVisible();
            // Reset only if the user is not dragging, not in scroll mode, and not hovering over
            // the panel.
            if (!stillDragging && !inScrollMode && !mHoveredState) {
                resetSelectedClickTypeIfNecessary();
            }
        }

        /**
         * Updates properties that should be used for click event sequence initiated by this object,
         * as well as the time at which click will be scheduled.
         * Should be called whenever new motion event is observed.
         *
         * @param event       Motion event whose properties should be used as a base for click event
         *                    sequence.
         * @param policyFlags Policy flags that should be send with click event sequence.
         */
        public void update(MotionEvent event, int policyFlags) {
            mMetaState = event.getMetaState();

            boolean moved = detectMovement(event);
            cacheLastEvent(event, policyFlags, mLastMotionEvent == null || moved /* useAsAnchor */);

            if (Flags.enableAutoclickIndicator()) {
                // Give the indicator the latest mouse coordinates for when the indicator is ready
                // to redraw.
                final int pointerIndex = event.getActionIndex();
                mAutoclickIndicatorView.setCoordination(
                        event.getX(pointerIndex), event.getY(pointerIndex));
            }

            if (moved) {
                rescheduleClick(mDelay);

                if (Flags.enableAutoclickIndicator()) {
                    mAutoclickIndicatorScheduler.update();
                }
            }
        }

        /** Cancels any pending clicks and resets the object state. */
        public void cancel() {
            if (!mActive) {
                return;
            }

            if (mDragModeIsDragging) {
                clearDraggingState();
            }

            if (mHasOngoingLongPress) {
                clearLongPressState();
            }

            resetInternalState();
            mHandler.removeCallbacks(this);
        }

        /**
         * Resets the drag state after a canceled click to avoid potential side effects from
         * leaving it in an inconsistent state.
         */
        private void clearDraggingState() {
            if (mLastMotionEvent != null) {
                // A final ACTION_UP event needs to be sent to alert the system that dragging has
                // ended.
                MotionEvent upEvent = MotionEvent.obtain(mLastMotionEvent);
                upEvent.setAction(MotionEvent.ACTION_UP);
                upEvent.setDownTime(mDragModeClickDownTime);
                AutoclickController.super.onMotionEvent(upEvent, upEvent,
                        mEventPolicyFlags);
            }

            resetSelectedClickTypeIfNecessary();
            mDragModeIsDragging = false;
        }

        /**
         * Cancels the pending long press to avoid potential side effects from
         * leaving it in an inconsistent state.
         */
        private void clearLongPressState() {
            if (mLastMotionEvent != null) {
                // A final ACTION_CANCEL event needs to be sent to alert the system that long press
                // has ended.
                MotionEvent cancelEvent = MotionEvent.obtain(mLastMotionEvent);
                cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                cancelEvent.setDownTime(mLongPressDownTime);
                AutoclickController.super.onMotionEvent(cancelEvent, cancelEvent,
                        mEventPolicyFlags);
            }

            resetSelectedClickTypeIfNecessary();
            mHasOngoingLongPress = false;
        }

        /**
         * Updates the meta state that should be used for click sequence.
         */
        public void updateMetaState(int state) {
            mMetaState = state;
        }

        @VisibleForTesting
        int getMetaStateForTesting() {
            return mMetaState;
        }

        @VisibleForTesting
        boolean getIsActiveForTesting() {
            return mActive;
        }

        @VisibleForTesting
        long getScheduledClickTimeForTesting() {
            return mScheduledClickTime;
        }

        /**
         * Updates delay that should be used when scheduling clicks. The delay will be used only for
         * clicks scheduled after this point (pending click tasks are not affected).
         *
         * @param delay New delay value.
         */
        public void updateDelay(int delay) {
            mDelay = delay;

            if (Flags.enableAutoclickIndicator() && mAutoclickIndicatorView != null) {
                mAutoclickIndicatorView.setAnimationDuration(delay - SHOW_INDICATOR_DELAY_TIME);
            }
        }

        @VisibleForTesting
        int getDelayForTesting() {
            return mDelay;
        }

        @VisibleForTesting
        boolean getRevertToLeftClickForTesting() {
            return mRevertToLeftClick;
        }

        /**
         * Updates the time at which click sequence should occur.
         *
         * @param delay Delay (from now) after which click should occur.
         */
        private void rescheduleClick(int delay) {
            long clickTime = SystemClock.uptimeMillis() + delay;

            // If there already is a scheduled click at time before the updated time, just update
            // scheduled time. The click will actually be rescheduled when pending callback is
            // run.
            if (mActive && clickTime > mScheduledClickTime) {
                mScheduledClickTime = clickTime;
                return;
            }

            if (mActive) {
                mHandler.removeCallbacks(this);
            }

            mActive = true;
            mScheduledClickTime = clickTime;

            mHandler.postDelayed(this, delay);
        }

        /**
         * Updates last observed motion event.
         *
         * @param event       The last observed event.
         * @param policyFlags The policy flags used with the last observed event.
         * @param useAsAnchor Whether the event coords should be used as a new anchor.
         */
        private void cacheLastEvent(MotionEvent event, int policyFlags, boolean useAsAnchor) {
            if (mLastMotionEvent != null) {
                mLastMotionEvent.recycle();
            }
            mLastMotionEvent = MotionEvent.obtain(event);
            mEventPolicyFlags = policyFlags;
            mHoveredState = isClickTypePanelHovered();

            if (useAsAnchor) {
                final int pointerIndex = mLastMotionEvent.getActionIndex();
                mLastMotionEvent.getPointerCoords(pointerIndex, mAnchorCoords);
            }
        }

        private void resetInternalState() {
            mActive = false;
            if (mLastMotionEvent != null) {
                mLastMotionEvent.recycle();
                mLastMotionEvent = null;
            }
            mScheduledClickTime = -1;

            if (Flags.enableAutoclickIndicator() && mAutoclickIndicatorView != null) {
                mAutoclickIndicatorView.clearIndicator();
            }
        }

        private void resetSelectedClickTypeIfNecessary() {
            if ((mRevertToLeftClick && mActiveClickType != AUTOCLICK_TYPE_LEFT_CLICK)
                    || mActiveClickType == AUTOCLICK_TYPE_LONG_PRESS) {
                mAutoclickTypePanel.collapsePanelWithClickType(AUTOCLICK_TYPE_LEFT_CLICK);
            }
        }

        /**
         * @param event Observed motion event.
         * @return Whether the event coords are far enough from the anchor for the event not to be
         * considered noise.
         */
        private boolean detectMovement(MotionEvent event) {
            if (mLastMotionEvent == null) {
                return false;
            }
            final int pointerIndex = event.getActionIndex();
            float deltaX = mAnchorCoords.x - event.getX(pointerIndex);
            float deltaY = mAnchorCoords.y - event.getY(pointerIndex);
            double delta = Math.hypot(deltaX, deltaY);

            // If a panel is hovered, use the special slop to make clicking the panel buttons
            // easier.
            double slop;
            if (!Flags.enableAutoclickIndicator()) {
                slop = DEFAULT_MOVEMENT_SLOP;
            } else if (isClickTypePanelHovered() || isScrollPanelHovered()) {
                slop = PANEL_HOVERED_SLOP;
            } else {
                slop = mMovementSlop;
            }

            return delta > slop;
        }

        public void setIgnoreMinorCursorMovement(boolean ignoreMinorCursorMovement) {
            mIgnoreMinorCursorMovement = ignoreMinorCursorMovement;
            if (mAutoclickIndicatorView != null) {
                mAutoclickIndicatorView.setIgnoreMinorCursorMovement(ignoreMinorCursorMovement);
            }
        }

        public void setRevertToLeftClick(boolean revertToLeftClick) {
            mRevertToLeftClick = revertToLeftClick;
        }

        private void updateMovementSlop(double slop) {
            mMovementSlop = slop;
        }

        /**
         * Creates and forwards click event sequence.
         */
        private void sendClick() {
            if (mLastMotionEvent == null || getNext() == null) {
                return;
            }

            // Clear pending long press in case another click action jumps between long pressing
            // down and up events.
            if (mHasOngoingLongPress) {
                clearLongPressState();
            }

            if (mAutoclickTypePanel.isHoveringDraggableArea(mLastMotionEvent)
                    && !mAutoclickTypePanel.getIsDragging()) {
                mAutoclickTypePanel.onDragStart(mLastMotionEvent);
                return;
            }

            // Always triggers left-click when the cursor hovers over the autoclick type panel, to
            // always allow users to change a different click type. Otherwise, if one chooses the
            // right-click, this user won't be able to rely on autoclick to select other click
            // types.
            int selectedClickType = mHoveredState ? AUTOCLICK_TYPE_LEFT_CLICK : mActiveClickType;

            AutoclickLogger.logSelectedClickType(selectedClickType);

            // Handle scroll-specific click behavior.
            if (handleScrollClick()) {
                return;
            }

            final int pointerIndex = mLastMotionEvent.getActionIndex();

            if (mTempPointerProperties == null) {
                mTempPointerProperties = new PointerProperties[1];
                mTempPointerProperties[0] = new PointerProperties();
            }

            mLastMotionEvent.getPointerProperties(pointerIndex, mTempPointerProperties[0]);

            if (mTempPointerCoords == null) {
                mTempPointerCoords = new PointerCoords[1];
                mTempPointerCoords[0] = new PointerCoords();
            }
            if (mIgnoreMinorCursorMovement) {
                mTempPointerCoords[0].x = mAnchorCoords.x;
                mTempPointerCoords[0].y = mAnchorCoords.y;
            } else {
                mLastMotionEvent.getPointerCoords(pointerIndex, mTempPointerCoords[0]);
            }

            int actionButton = BUTTON_PRIMARY;
            switch (selectedClickType) {
                case AUTOCLICK_TYPE_RIGHT_CLICK:
                    actionButton = BUTTON_SECONDARY;
                    break;
                case AUTOCLICK_TYPE_DOUBLE_CLICK:
                    actionButton = BUTTON_PRIMARY;
                    sendDoubleClickEvent();
                    return;
                case AUTOCLICK_TYPE_DRAG:
                    if (mDragModeIsDragging) {
                        endDragEvent();
                    } else {
                        startDragEvent();
                    }
                    return;
                case AUTOCLICK_TYPE_LONG_PRESS:
                    actionButton = BUTTON_PRIMARY;
                    sendLongPress();
                    return;
                default:
                    break;
            }
            sendMotionEventsForClick(actionButton);

            // End panel drag operation if one is active (autoclick triggered after user stopped
            // moving during drag).
            if (mAutoclickTypePanel != null && mAutoclickTypePanel.getIsDragging()) {
                mAutoclickTypePanel.onDragEnd();
            }
        }

        /**
         * Handles scroll-specific click behavior when autoclick is triggered.
         *
         * @return true if scroll handling was performed (no further click processing needed),
         * false if regular click processing should continue.
         */
        private boolean handleScrollClick() {
            // Only handle scroll type clicks.
            if (mActiveClickType != AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL
                    || mAutoclickScrollPanel == null) {
                return false;
            }

            // Trigger left click instead of scroll when hovering over type panel.
            if (mAutoclickTypePanel.isHovered()) {
                return false;
            }

            boolean isPanelVisible = mAutoclickScrollPanel.isVisible();
            boolean isPanelHovered = mAutoclickScrollPanel.isHovered();

            // Handle exit button hover case.
            if (isPanelVisible && mHoveredDirection == AutoclickScrollPanel.DIRECTION_EXIT) {
                exitScrollMode();
                return true;
            }

            // Update cursor position when not hovering over panels.
            if (!isPanelHovered) {
                final int pointerIndex = mLastMotionEvent.getActionIndex();
                mScrollCursorX = mLastMotionEvent.getX(pointerIndex);
                mScrollCursorY = mLastMotionEvent.getY(pointerIndex);
            }

            // Show or reposition panel.
            if (isPanelVisible && !isPanelHovered) {
                // Reposition panel when cursor is outside the panel.
                mAutoclickScrollPanel.hide();
                mAutoclickScrollPanel.show(mScrollCursorX, mScrollCursorY);
            } else if (!isPanelVisible) {
                // First time showing the panel.
                mAutoclickScrollPanel.show(mScrollCursorX, mScrollCursorY);
            }

            return true;
        }

        /**
         * Sends a set of motion events consisting of downEvent, pressEvent, releaseEvent
         * and downEvent for a specific action button.
         */
        private void sendMotionEventsForClick(int actionButton) {
            final long now = SystemClock.uptimeMillis();
            MotionEvent downEvent = buildMotionEvent(
                    now, now, actionButton, mLastMotionEvent);

            MotionEvent pressEvent = MotionEvent.obtain(downEvent);
            pressEvent.setAction(MotionEvent.ACTION_BUTTON_PRESS);
            pressEvent.setActionButton(actionButton);

            MotionEvent releaseEvent = MotionEvent.obtain(downEvent);
            releaseEvent.setAction(MotionEvent.ACTION_BUTTON_RELEASE);
            releaseEvent.setActionButton(actionButton);
            releaseEvent.setButtonState(0);

            MotionEvent upEvent = MotionEvent.obtain(downEvent);
            upEvent.setAction(MotionEvent.ACTION_UP);
            upEvent.setButtonState(0);

            AutoclickController.super.onMotionEvent(downEvent, downEvent, mEventPolicyFlags);
            downEvent.recycle();

            AutoclickController.super.onMotionEvent(pressEvent, pressEvent, mEventPolicyFlags);
            pressEvent.recycle();

            AutoclickController.super.onMotionEvent(releaseEvent, releaseEvent, mEventPolicyFlags);
            releaseEvent.recycle();

            AutoclickController.super.onMotionEvent(upEvent, upEvent, mEventPolicyFlags);
            upEvent.recycle();
        }

        private void sendDoubleClickEvent() {
            mHasOngoingDoubleClick = true;
            sendMotionEventsForClick(BUTTON_PRIMARY);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sendMotionEventsForClick(BUTTON_PRIMARY);
                    mHasOngoingDoubleClick = false;
                    resetInternalState();
                }
            }, DOUBLE_CLICK_MINIMUM_TIMEOUT);
        }

        private void sendLongPress() {
            mHasOngoingLongPress = true;
            mLongPressDownTime = SystemClock.uptimeMillis();
            MotionEvent downEvent = buildMotionEvent(
                    mLongPressDownTime, mLongPressDownTime, BUTTON_PRIMARY, mLastMotionEvent);

            MotionEvent pressEvent = MotionEvent.obtain(downEvent);
            pressEvent.setAction(MotionEvent.ACTION_BUTTON_PRESS);

            AutoclickController.super.onMotionEvent(downEvent, downEvent, mEventPolicyFlags);
            AutoclickController.super.onMotionEvent(pressEvent, pressEvent, mEventPolicyFlags);

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    long upTime = SystemClock.uptimeMillis();
                    MotionEvent releaseEvent = buildMotionEvent(
                            mLongPressDownTime, upTime, BUTTON_PRIMARY, downEvent);
                    releaseEvent.setAction(MotionEvent.ACTION_BUTTON_RELEASE);
                    releaseEvent.setButtonState(0);

                    MotionEvent upEvent = MotionEvent.obtain(releaseEvent);
                    upEvent.setAction(MotionEvent.ACTION_UP);
                    upEvent.setButtonState(0);

                    AutoclickController.super.onMotionEvent(
                            releaseEvent, releaseEvent, mEventPolicyFlags);
                    AutoclickController.super.onMotionEvent(
                            upEvent, upEvent, mEventPolicyFlags);

                    downEvent.recycle();
                    pressEvent.recycle();
                    releaseEvent.recycle();
                    upEvent.recycle();
                    mHasOngoingLongPress = false;
                    resetInternalState();
                }
            }, LONG_PRESS_TIMEOUT);
        }

        private @NonNull MotionEvent buildMotionEvent(
                long downTime, long eventTime, int actionButton,
                @NonNull MotionEvent lastMotionEvent) {
            return MotionEvent.obtain(
                            /* downTime= */ downTime,
                            /* eventTime= */ eventTime,
                            MotionEvent.ACTION_DOWN,
                            /* pointerCount= */ 1,
                            mTempPointerProperties,
                            mTempPointerCoords,
                            mMetaState,
                            actionButton,
                            /* xPrecision= */ 1.0f,
                            /* yPrecision= */ 1.0f,
                            lastMotionEvent.getDeviceId(),
                            /* edgeFlags= */ 0,
                            lastMotionEvent.getSource(),
                            lastMotionEvent.getFlags());
        }

        /**
         * To start a drag event, only send the DOWN and BUTTON_PRESS events.
         */
        private void startDragEvent() {
            mDragModeClickDownTime = SystemClock.uptimeMillis();
            mDragModeIsDragging = true;

            MotionEvent downEvent =
                    MotionEvent.obtain(
                            /* downTime= */ mDragModeClickDownTime,
                            /* eventTime= */ mDragModeClickDownTime,
                            MotionEvent.ACTION_DOWN,
                            /* pointerCount= */ 1,
                            mTempPointerProperties,
                            mTempPointerCoords,
                            mMetaState,
                            BUTTON_PRIMARY,
                            /* xPrecision= */ 1.0f,
                            /* yPrecision= */ 1.0f,
                            mLastMotionEvent.getDeviceId(),
                            /* edgeFlags= */ 0,
                            mLastMotionEvent.getSource(),
                            mLastMotionEvent.getFlags());
            MotionEvent pressEvent = MotionEvent.obtain(downEvent);
            pressEvent.setAction(MotionEvent.ACTION_BUTTON_PRESS);
            pressEvent.setActionButton(BUTTON_PRIMARY);
            AutoclickController.super.onMotionEvent(downEvent, downEvent,
                    mEventPolicyFlags);
            downEvent.recycle();
            AutoclickController.super.onMotionEvent(pressEvent, pressEvent,
                    mEventPolicyFlags);
            pressEvent.recycle();
        }

        /**
         * To end a drag event, only send the BUTTON_RELEASE and UP events, making sure to
         * include the originating drag click's down time.
         */
        private void endDragEvent() {
            mDragModeIsDragging = false;

            MotionEvent releaseEvent =
                    MotionEvent.obtain(
                            /* downTime= */ mDragModeClickDownTime,
                            /* eventTime= */ mDragModeClickDownTime,
                            MotionEvent.ACTION_BUTTON_RELEASE,
                            /* pointerCount= */ 1,
                            mTempPointerProperties,
                            mTempPointerCoords,
                            mMetaState,
                            /* buttonState= */ 0,
                            /* xPrecision= */ 1.0f,
                            /* yPrecision= */ 1.0f,
                            mLastMotionEvent.getDeviceId(),
                            /* edgeFlags= */ 0,
                            mLastMotionEvent.getSource(),
                            mLastMotionEvent.getFlags());
            MotionEvent upEvent = MotionEvent.obtain(releaseEvent);
            releaseEvent.setActionButton(BUTTON_PRIMARY);
            upEvent.setAction(MotionEvent.ACTION_UP);
            AutoclickController.super.onMotionEvent(releaseEvent, releaseEvent,
                    mEventPolicyFlags);
            AutoclickController.super.onMotionEvent(upEvent, upEvent, mEventPolicyFlags);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("ClickScheduler: { active=").append(mActive);
            builder.append(", delay=").append(mDelay);
            builder.append(", scheduledClickTime=").append(mScheduledClickTime);
            builder.append(", anchor={x:").append(mAnchorCoords.x);
            builder.append(", y:").append(mAnchorCoords.y).append("}");
            builder.append(", metastate=").append(mMetaState);
            builder.append(", policyFlags=").append(mEventPolicyFlags);
            builder.append(", lastMotionEvent=").append(mLastMotionEvent);
            builder.append(" }");
            return builder.toString();
        }
    }
}
