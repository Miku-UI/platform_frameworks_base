/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Slog;
import android.view.Display;

import com.android.server.accessibility.AccessibilityManagerService;

/**
 * A debugger that is used for triggering magnification activation in debug builds.
 *
 * It contains a broadcast receiver to receive commands from adb.
 */
public class FullScreenMagnificationActivationDebugger {

    private static final String TAG = "FullScreenMagnificationActivationDebugger";
    // Test-only action to activate and deactivate magnification zoom via adb broadcast.
    private static final String DEBUG_ACTIVATE_ZOOM =
            "com.android.server.accessibility.magnification.FULLSCREEN_MAG_ACTIVATE";
    private static final String DEBUG_RESET_ZOOM =
            "com.android.server.accessibility.magnification.FULLSCREEN_MAG_RESET";

    private final Context mContext;
    private final FullScreenMagnificationController mController;
    private final DebugReceiver mDebugReceiver;
    private boolean isRegistered = false;

    public FullScreenMagnificationActivationDebugger(Context context,
            FullScreenMagnificationController controller) {
        mContext = context;
        mController = controller;
        mDebugReceiver = new DebugReceiver();
    }

    public void registerIfNecessary() {
        if (!isRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(DEBUG_ACTIVATE_ZOOM);
            filter.addAction(DEBUG_RESET_ZOOM);
            mContext.registerReceiver(this.mDebugReceiver, filter, Context.RECEIVER_EXPORTED);
            isRegistered = true;
        }
    }

    public void unregister() {
        if (isRegistered) {
            mContext.unregisterReceiver(this.mDebugReceiver);
            isRegistered = false;
        }
    }

    /**
     * BroadcastReceiver used to activate or reset magnification via adb broadcast.
     */
    private class DebugReceiver extends BroadcastReceiver {

        /**
         * Called when intent to activate or reset magnification is received.
         *
         * @param context The context of the receiver.
         * @param intent The intent that was received.
         *
         * To activate magnification:
         *   adb shell am broadcast -a \
         *   com.android.server.accessibility.magnification.FULLSCREEN_MAG_ACTIVATE
         *
         * Default scale is 2.0. To change the scale, use the extra --ef scale <value>
         * Default coordinates is the screen center.
         * To change the center x coordinate, use the extra --ef centerX <value>
         * To change the center y coordinate, use the extra --ef centerY <value>
         * Default display id is 0. To change the display id, use the extra --ei displayId <value>
         *
         * To reset magnification:
         *   adb shell am broadcast -a \
         *   com.android.server.accessibility.magnification.FULLSCREEN_MAG_RESET
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            int displayId = intent.getIntExtra("displayId", Display.DEFAULT_DISPLAY);
            if (intent.getAction().equals(DEBUG_ACTIVATE_ZOOM)) {
                float scale = intent.getFloatExtra("scale", 2.0f);
                // The value Float.NaN will take the screen center coordinates of the screen.
                float centerX = intent.getFloatExtra("centerX", Float.NaN);
                float centerY = intent.getFloatExtra("centerY", Float.NaN);
                int serviceId = AccessibilityManagerService.MAGNIFICATION_GESTURE_HANDLER_ID;

                boolean success = mController.setScaleAndCenter(
                        displayId, scale, centerX, centerY, /* animate= */ true, serviceId);
                if (!success) {
                    Slog.e(
                            TAG,
                            "Magnification activation failed: setScaleAndCenter returned false");
                }
            } else if (intent.getAction().equals(DEBUG_RESET_ZOOM)) {
                boolean success = mController.reset(displayId, /* animate= */ true);
                if (!success) {
                    Slog.e(TAG, "Magnification reset failed: reset returned false");
                }
            }
        }
    }
}
