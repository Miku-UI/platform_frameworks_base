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

package com.android.systemui.wallpapers.domain.presentation

import android.view.Display
import android.view.DisplayInfo
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType

/**
 * A helper class which determines if [WallpaperPresentationType.PROVISIONING] can be shown for a
 * given display.
 */
object ProvisioningPresentationCompatibility {

    /**
     * Returns `true` if if [WallpaperPresentationType.PROVISIONING] can be shown for a [display].
     * Otherwise, returns `false`.
     */
    fun isCompatibleForDisplay(display: Display): Boolean {
        if (display.displayId == Display.DEFAULT_DISPLAY) {
            return false
        }

        val displayInfo = DisplayInfo()
        display.getDisplayInfo(displayInfo)
        return displayInfo.flags and Display.FLAG_PRIVATE != Display.FLAG_PRIVATE
    }
}
