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

package com.android.server.accessibility;

import android.util.Log;

/**
 * Common logging utility for Accessibility services.
 */
public final class AccessibilityLogUtil {
    /**
     * Global log tag for all Accessibility services.
     * To enable debug logs for this tag, run:
     * adb shell setprop log.tag.AccessibilityLogUtil DEBUG
     */
    public static final String TAG = AccessibilityLogUtil.class.getSimpleName();

    /**
     * Checks if DEBUG logging is enabled for EITHER the specific localTag OR the
     * global AccessibilityLogUtil tag.
     *
     * Usage:
     *   adb shell setprop log.tag.MyClassTag DEBUG (specific file)
     *   adb shell setprop log.tag.AccessibilityLogUtil DEBUG (accessibility system classes)
     *   adb shell am restart
     *
     * @param localTag The specific tag for the calling class.
     * @return true if DEBUG level is loggable for either tag.
     */
    public static boolean isDebugEnabled(String localTag) {
        return Log.isLoggable(localTag, Log.DEBUG) || Log.isLoggable(TAG, Log.DEBUG);
    }

    private AccessibilityLogUtil() {
    }
}
