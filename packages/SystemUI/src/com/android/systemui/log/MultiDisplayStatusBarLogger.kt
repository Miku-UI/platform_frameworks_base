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

package com.android.systemui.log

import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.dagger.MultiDisplayStatusBarLog
import com.android.systemui.statusbar.events.PrivacyDotCorner
import javax.inject.Inject

class MultiDisplayStatusBarLogger
@Inject
constructor(@MultiDisplayStatusBarLog private val buffer: LogBuffer) {

    fun logStatusBarWindowAdded(displayId: Int) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            { int1 = displayId },
            { "StatusBar window added to WindowManager on display $int1" },
        )
    }

    fun logStatusBarWindowAddFailure(displayId: Int) {
        buffer.log(
            TAG,
            LogLevel.ERROR,
            { int1 = displayId },
            { "StatusBar failed to add window to WindowManager on invalid display $int1" },
        )
    }

    fun logStatusBarWindowRemoved(displayId: Int) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            { int1 = displayId },
            { "StatusBar window removed from WindowManager on display $int1" },
        )
    }

    fun logStatusBarWindowRemoveFailure(displayId: Int) {
        buffer.log(
            TAG,
            LogLevel.ERROR,
            { int1 = displayId },
            { "StatusBar failed to remove window from WindowManager on invalid display $int1" },
        )
    }

    fun logCreatingAndStartingComponents(displayId: Int) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            { int1 = displayId },
            { "Creating and starting components for display $int1" },
        )
    }

    fun logCreatingStatusBarRootView(displayId: Int) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            { int1 = displayId },
            { "Creating StatusBarRootView on display $int1" },
        )
    }

    fun logCreatedStatusBarRootView(displayId: Int) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            { int1 = displayId },
            { "StatusBarRootView created on display $int1" },
        )
    }

    fun logStatusBarRootViewAddedToWindow(displayId: Int) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            { int1 = displayId },
            { "StatusBarRootView added to window on display $int1" },
        )
    }

    fun logPrivacyDotWindowAdded(displayId: Int, corner: PrivacyDotCorner) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                int1 = displayId
                str1 = corner.name
            },
            { "PrivacyDot window for corner $str1 on display $int1 added to WindowManager" },
        )
    }

    fun logPrivacyDotWindowAddFailure(displayId: Int, corner: PrivacyDotCorner) {
        buffer.log(
            TAG,
            LogLevel.ERROR,
            {
                int1 = displayId
                str1 = corner.name
            },
            { "Failed to add PrivacyDot window for corner $str1 on display $int1 to WindowManager" },
        )
    }

    fun logPrivacyDotWindowRemoved(displayId: Int, corner: PrivacyDotCorner) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                int1 = displayId
                str1 = corner.name
            },
            { "PrivacyDot window for corner $str1 on display $int1 removed from WindowManager" },
        )
    }

    fun logPrivacyDotWindowRemovalFailure(displayId: Int, corner: PrivacyDotCorner) {
        buffer.log(
            TAG,
            LogLevel.ERROR,
            {
                int1 = displayId
                str1 = corner.name
            },
            {
                "Failed to remove PrivacyDot window for corner $str1 on display $int1 to WindowManager"
            },
        )
    }

    fun logDisplaysWithSystemDecorationsChange(
        previousDisplays: Set<Int>,
        currentDisplays: Set<Int>,
    ) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = previousDisplays.toString()
                str2 = currentDisplays.toString()
            },
            {
                "Displays with system decorations changed. Previous displays: $str1, Current displays: $str2"
            },
        )
    }

    fun logNewDisplaysWithSystemDecorations(newDisplays: Set<Int>) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            { str1 = newDisplays.toString() },
            { "New displays with system decorations: $str1" },
        )
    }
}

private const val TAG = "MultiDisplayStatusBar"
