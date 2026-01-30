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

package com.android.systemui.wallpapers.domain.interactor

import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.KEYGUARD
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.NONE
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.PROVISIONING
import kotlinx.coroutines.flow.StateFlow

/**
 * Encapsulates the logic of determining the type of wallpaper presentation should be used for
 * external displays based on device states.
 */
interface DisplayWallpaperPresentationInteractor {
    /**
     * A [StateFlow] that emits [WallpaperPresentationType] that should be used for external
     * displays based on device states.
     */
    val presentationFactoryFlow: StateFlow<WallpaperPresentationType>

    /**
     * The different types of wallpaper presentations that can be active on a display.
     * - [NONE]: No special wallpaper presentation is required (e.g., default screen).
     * - [PROVISIONING]: The device is currently in a provisioning state, requiring a provisioning
     *   wallpaper presentation.
     * - [KEYGUARD]: The device is locked, requiring a keyguard wallpaper presentation.
     */
    enum class WallpaperPresentationType {
        NONE,
        PROVISIONING,
        KEYGUARD,
    }
}
