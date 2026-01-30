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

package com.android.systemui.statusbar.phone.domain.interactor

import android.graphics.Rect
import android.view.Display
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.shade.domain.interactor.ShadeDisplaysInteractor
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher.DarkChange
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

/** States pertaining to calculating colors for shade header icons in dark mode. */
interface ShadeDarkIconInteractor {
    /**
     * Returns a flow of [IsAreaDark], a function that answers whether a given [Rect] should be
     * tinted dark or not, on the display where the shade window is present.
     *
     * This flow ignores [DarkChange.tint] and [DarkChange.darkIntensity].
     */
    val isShadeAreaDark: Flow<IsAreaDark>
}

@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class ShadeDarkIconInteractorImpl
@Inject
constructor(
    darkIconInteractor: DarkIconInteractor,
    shadeDisplaysInteractor: Lazy<ShadeDisplaysInteractor>,
) : ShadeDarkIconInteractor {
    override val isShadeAreaDark: Flow<IsAreaDark> =
        if (ShadeWindowGoesAround.isEnabled) {
            shadeDisplaysInteractor.get().displayId.flatMapLatest(darkIconInteractor::isAreaDark)
        } else {
            darkIconInteractor.isAreaDark(Display.DEFAULT_DISPLAY)
        }
}
