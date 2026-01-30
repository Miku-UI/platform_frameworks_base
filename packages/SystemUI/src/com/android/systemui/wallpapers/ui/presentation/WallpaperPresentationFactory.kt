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

package com.android.systemui.wallpapers.ui.presentation

import android.app.Presentation
import android.view.Display
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType
import dagger.MapKey

/** A factory interface for creating and managing wallpaper presentations on different displays. */
interface WallpaperPresentationFactory {
    /**
     * Creates a new [Presentation] instance for the specified [display].
     *
     * @param display The [Display] on which the presentation should be created.
     * @return A [Presentation] instance.
     */
    fun create(display: Display): Presentation
}

/** Key for multibinding [WallpaperPresentationFactory]s. */
@MapKey annotation class WallpaperPresentationTypeKey(val value: WallpaperPresentationType)
