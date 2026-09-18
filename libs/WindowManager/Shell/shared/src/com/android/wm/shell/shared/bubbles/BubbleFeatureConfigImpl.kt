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
package com.android.wm.shell.shared.bubbles

import android.app.ActivityManager
import android.content.Context
import com.android.wm.shell.Flags
import com.android.wm.shell.shared.desktopmode.DesktopState

/** Implementation of [BubbleFeatureConfig]. */
class BubbleFeatureConfigImpl(
    private val context: Context,
    private val desktopState: DesktopState,
) : BubbleFeatureConfig {

    override fun areAppBubblesSupported(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Fail fast if this is a low-ram device.
        if (am.isLowRamDevice) return false

        // Fail fast if Bubble Anything is disabled.
        if (!BubbleFlagHelper.enableCreateAnyBubble()) return false

        // Do not allow any app to be bubbled on a display that supports desktop windowing.
        if (Flags.disableBubbleAnythingDesktopWindowing()) {
            val desktopSupported = desktopState.isDesktopModeSupportedOnDisplay(context.displayId)
            return !desktopSupported && BubbleFlagHelper.enableCreateAnyBubble()
        }
        return true
    }

    override fun isScrimEnabled(displayId: Int): Boolean {
        return !(Flags.disableBubbleScrimLargeScreens() &&
            desktopState.isDesktopModeSupportedOnDisplay(displayId))
    }
}
