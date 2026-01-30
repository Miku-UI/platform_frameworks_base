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

package com.android.systemui.keyguard.ui.viewmodel

import com.android.internal.graphics.ColorUtils
import com.android.systemui.keyguard.domain.interactor.AodDimInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class LockscreenBehindScrimViewModel
@AssistedInject
constructor(private val aodDimInteractor: AodDimInteractor) {
    // Always apply LOCKSCREEN_WALLPAPER_DIM_AMOUNT.
    // If applicable, apply an additional dim for AOD wallpapers
    val alpha: Float
        get() =
            ColorUtils.compositeAlpha(
                    (255 * aodDimInteractor.wallpaperDimAmount).toInt(),
                    (255 * LOCKSCREEN_WALLPAPER_DIM_AMOUNT).toInt(),
                )
                .toFloat() / 255f

    @AssistedFactory
    interface Factory {
        fun create(): LockscreenBehindScrimViewModel
    }

    companion object {
        private const val LOCKSCREEN_WALLPAPER_DIM_AMOUNT = .2f
    }
}
