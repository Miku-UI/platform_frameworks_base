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

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import com.android.internal.graphics.ColorUtils
import com.android.systemui.keyguard.domain.interactor.AodDimInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class LockscreenBehindScrimViewModel
@AssistedInject
constructor(
    private val aodDimInteractor: AodDimInteractor,
    private val sceneInteractor: SceneInteractor,
) {
    /**
     * Whether the behind scrim should even be visible, this applies more strongly than the [alpha];
     * if this is `false`, the scrim shouldn't even be composed.
     */
    val isVisible: Boolean by derivedStateOf {
        // The behind scrim is only shown if not transitioning currently into the Occluded
        // scene. This is a small hack in the service of b/454374625 where there was a flicker
        // that would happen when transitioning from the Bouncer overlay (shown on top of the
        // Lockscreen scene) to the Occluded scene. The flicker was caused by the behind scrim
        // having a non-zero alpha for a frame or two when this would happen; that caused a dim
        // and undim "flicker". Note that this isn't limited to the bouncer only as the same
        // would happen also when opening occluding apps directly (for example: lock screen
        // shortcut or double-press power button to show up the camera app on top of the lock
        // screen).
        !sceneInteractor.transitionState.isTransitioning(to = Scenes.Occluded)
    }

    /**
     * The alpha for the scrim. Note that, if [isVisible] is `false`, the scrim shouldn't even be
     * composed, regardless of the value of the `alpha` property.
     */
    val alpha: Float
        get() =
            // Always apply LOCKSCREEN_WALLPAPER_DIM_AMOUNT.
            // If applicable, apply an additional dim for AOD wallpapers
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
