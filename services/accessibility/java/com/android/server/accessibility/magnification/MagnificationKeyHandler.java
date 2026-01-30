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

import android.view.Display;
import android.view.KeyEvent;

import com.android.server.accessibility.AccessibilityManagerService;
import com.android.server.accessibility.BaseEventStreamTransformation;

/*
 * A class that listens to key presses used to control magnification.
 */
public class MagnificationKeyHandler extends BaseEventStreamTransformation {

    /** Callback interface to report that a user is intending to interact with Magnification. */
    public interface Callback {
        /**
         * Called when a keyboard shortcut to pan magnification in direction {@code direction} is
         * pressed by a user. Note that this can be called for multiple directions if multiple
         * arrows are pressed at the same time (e.g. diagonal panning).
         *
         * @param displayId The logical display ID
         * @param direction The direction to start panning
         */
        void onPanMagnificationStart(int displayId,
                @MagnificationController.PanDirection int direction);

        /**
         * Called when a keyboard shortcut to pan magnification in direction {@code direction} is
         * unpressed by a user. Note that this can be called for multiple directions if multiple
         * arrows had been pressed at the same time (e.g. diagonal panning).
         *
         * @param displayId The logical display ID
         */
        void onPanMagnificationStop(int displayId);

        /**
         * Called when a keyboard shortcut to scale magnification in direction `direction` is
         * pressed by a user.
         *
         * @param displayId The logical display ID
         * @param direction The direction in which scaling started
         */
        void onScaleMagnificationStart(int displayId,
                @MagnificationController.ZoomDirection int direction);

        /**
         * Called when a keyboard shortcut to scale magnification in direction `direction` is
         * unpressed by a user.
         *
         * @param direction The direction in which scaling stopped
         */
        void onScaleMagnificationStop(@MagnificationController.ZoomDirection int direction);

        /**
         * Called when all keyboard interaction with magnification should be stopped.
         */
        void onKeyboardInteractionStop();

        /**
         * Check for whether magnification is active on the given display. If it is not,
         * there is no need to send scale and zoom events.
         * Note that magnification may be enabled (so this handler is installed) but not
         * activated, for example when the shortcut button is shown or if an A11y service
         * using magnification is active.
         *
         * @param displayId The logical display ID
         * @return true if magnification is activated.
         */
        boolean isMagnificationActivated(int displayId);
    }

    protected final MagnificationKeyHandler.Callback mCallback;
    private final AccessibilityManagerService mAms;
    private boolean mIsKeyboardInteracting = false;

    public MagnificationKeyHandler(Callback callback, AccessibilityManagerService ams) {
        mCallback = callback;
        mAms = ams;
    }

    @Override
    public void onKeyEvent(KeyEvent event, int policyFlags) {
        // Look for exactly Alt and Meta.
        boolean modifiersPressed = event.isAltPressed() && event.isMetaPressed()
                && !event.isCtrlPressed() && !event.isShiftPressed();
        if (!modifiersPressed) {
            super.onKeyEvent(event, policyFlags);
            if (mIsKeyboardInteracting) {
                // When modifier keys are no longer pressed, ensure that scaling and
                // panning are fully stopped.
                mCallback.onKeyboardInteractionStop();
                mIsKeyboardInteracting = false;
            }
            return;
        }
        int keyCode = event.getKeyCode();
        boolean isArrowKeyCode = keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean isZoomKeyCode = keyCode == KeyEvent.KEYCODE_EQUALS
                || keyCode == KeyEvent.KEYCODE_MINUS;
        if (!isArrowKeyCode && !isZoomKeyCode) {
            // Some other key was pressed.
            super.onKeyEvent(event, policyFlags);
            return;
        }
        int displayId = getDisplayId(event);
        // Check magnification is active only when we know we have the correct keys pressed.
        // This requires `synchronized` which is expensive to do on every key event.
        if (!mCallback.isMagnificationActivated(displayId)) {
            // Magnification isn't active.
            super.onKeyEvent(event, policyFlags);
            return;
        }
        boolean isDown = event.getAction() == KeyEvent.ACTION_DOWN;
        if (isArrowKeyCode) {
            int panDirection = switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT -> MagnificationController.PAN_DIRECTION_LEFT;
                case KeyEvent.KEYCODE_DPAD_RIGHT -> MagnificationController.PAN_DIRECTION_RIGHT;
                case KeyEvent.KEYCODE_DPAD_UP -> MagnificationController.PAN_DIRECTION_UP;
                default -> MagnificationController.PAN_DIRECTION_DOWN;
            };
            if (isDown) {
                mCallback.onPanMagnificationStart(displayId, panDirection);
                mIsKeyboardInteracting = true;
            } else {
                mCallback.onPanMagnificationStop(panDirection);
            }
            return;
        }
        // Zoom key code.
        int zoomDirection = MagnificationController.ZOOM_DIRECTION_OUT;
        if (keyCode == KeyEvent.KEYCODE_EQUALS) {
            zoomDirection = MagnificationController.ZOOM_DIRECTION_IN;
        }
        if (isDown) {
            mCallback.onScaleMagnificationStart(displayId, zoomDirection);
            mIsKeyboardInteracting = true;
        } else {
            mCallback.onScaleMagnificationStop(zoomDirection);
        }
    }

    private int getDisplayId(KeyEvent event) {
        // Display ID may be invalid, e.g. for external keyboard attached to phone.
        // In that case, use the default display.
        if (event.getDisplayId() != Display.INVALID_DISPLAY) {
            return event.getDisplayId();
        }
        int topFocusedDisplayId = mAms.getTopFocusedDisplayId();
        if (topFocusedDisplayId != Display.INVALID_DISPLAY) {
            return topFocusedDisplayId;
        }
        return Display.DEFAULT_DISPLAY;
    }
}
