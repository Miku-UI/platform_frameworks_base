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

import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryBypassInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryUdfpsInteractor
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.wallpapers.domain.interactor.WallpaperFocalAreaInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope

class LockscreenRootViewModel
@AssistedInject
constructor(
    val touchHandlingFactory: KeyguardTouchHandlingViewModel.Factory,
    shadeModeInteractor: ShadeModeInteractor,
    deviceEntryBypassInteractor: DeviceEntryBypassInteractor,
    deviceEntryUdfpsInteractor: DeviceEntryUdfpsInteractor,
    private val burnInMovementFactory: BurnInMovementState.Factory,
    private val wallpaperFocalAreaInteractor: WallpaperFocalAreaInteractor,
) : HydratedActivatable() {
    val burnIn: BurnInMovementState = burnInMovementFactory.create()

    /** @see ShadeModeInteractor.isFullWidthShade */
    val isFullWidthShade: Boolean by shadeModeInteractor.isFullWidthShade.hydratedStateOf()

    /** @see DeviceEntryBypassInteractor.isBypassEnabled */
    val isBypassEnabled: Boolean by deviceEntryBypassInteractor.isBypassEnabled.hydratedStateOf()

    /** Whether udfps is supported. */
    val isUdfpsSupported: Boolean by
        deviceEntryUdfpsInteractor.isUdfpsSupported.hydratedStateOf()

    override suspend fun onActivated() {
        coroutineScope { launch("BurnIn") { burnIn.activate() } }
    }

    fun setMediaPlayerBottom(bottom: Float) {
        wallpaperFocalAreaInteractor.setMediaPlayerBottom(bottom)
    }

    fun setShortcutTop(top: Float) {
        wallpaperFocalAreaInteractor.setShortcutTop(top)
    }

    fun setSmallClockBottom(bottom: Float) {
        wallpaperFocalAreaInteractor.setSmallClockBottom(bottom)
    }

    fun setSmartspaceCardBottom(bottom: Float) {
        wallpaperFocalAreaInteractor.setSmartspaceCardBottom(bottom)
    }

    @AssistedFactory
    interface Factory {
        fun create(): LockscreenRootViewModel
    }
}
