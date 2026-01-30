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
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import com.android.systemui.res.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * An empty presentation for showing wallpaper in connected displays.
 *
 * Unlike [com.android.keyguard.ConnectedDisplayConstraintLayoutKeyguardPresentation], this
 * presentation is purely a translucent window with window type, TYPE_PRESENTATION. This
 * presentation should be used only when the device is unlocked.
 */
class ConnectedDisplayWallpaperPresentation
@AssistedInject
internal constructor(@Assisted display: Display, context: Context) :
    Presentation(
        context,
        display,
        R.style.Theme_SystemUI_WallpaperPresentation,
        WindowManager.LayoutParams.TYPE_PRESENTATION,
    ) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val window = window ?: return
        val layoutParams = window.attributes
        layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        window.attributes = layoutParams
    }

    @AssistedFactory
    interface Factory {
        /** Creates a new [Presentation] for the given [display]. */
        fun create(display: Display): ConnectedDisplayWallpaperPresentation
    }
}
