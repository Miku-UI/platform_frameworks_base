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

import android.util.Log
import android.view.Display
import com.android.keyguard.KeyguardDisplayManager
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.log.DebugLogger.debugLog
import com.android.systemui.shade.domain.interactor.ShadeDisplaysInteractor
import com.android.systemui.statusbar.policy.data.repository.DeviceProvisioningRepository
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.KEYGUARD
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.NONE
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.PROVISIONING
import com.android.systemui.wallpapers.domain.presentation.ProvisioningPresentationCompatibility
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * The business logic of the type of wallpaper presentation that should be shown on a secondary
 * display based on the device states.
 */
@PerDisplaySingleton
class DisplayWallpaperPresentationInteractorImpl
@Inject
constructor(
    private val display: Display,
    @DisplayAware private val displayCoroutineScope: CoroutineScope,
    private val keyguardInteractor: Lazy<KeyguardInteractor>,
    private val deviceProvisioningRepository: Lazy<DeviceProvisioningRepository>,
    private val keyguardDisplayManager: Lazy<KeyguardDisplayManager>,
    private val shadeDisplaysInteractor: Lazy<ShadeDisplaysInteractor>,
    private val displayRepository: Lazy<DisplayRepository>,
) : DisplayWallpaperPresentationInteractor {

    override val presentationFactoryFlow: StateFlow<WallpaperPresentationType> by lazy {
        val keyguardShowingFlow = keyguardInteractor.get().isKeyguardShowing
        val deviceProvisionedFlow = deviceProvisioningRepository.get().isDeviceProvisioned
        // Use a "Pending" state to preemptively handle the shade's move to the default display.
        // This prevents a race condition where the keyguard is incorrectly shown on the old display
        // while the shade is in transit, avoiding visual artifacts on the lock screen.
        // If the move fails, the pending display id will be reset anyway.
        val shadeDisplayIdFlow = shadeDisplaysInteractor.get().pendingDisplayId
        val isDreamingWithOverlayFlow = keyguardInteractor.get().isDreamingWithOverlay
        val isMirroringEnabledFlow = displayRepository.get().isMirroringEnabled
        combine(
                keyguardShowingFlow,
                deviceProvisionedFlow,
                shadeDisplayIdFlow,
                isDreamingWithOverlayFlow,
                isMirroringEnabledFlow,
            ) {
                isKeyguardShowing,
                isDeviceProvisioned,
                shadeDisplayId,
                isDreamingWithOverlay,
                isMirroringEnabled ->
                determinePresentationType(
                        isKeyguardShowing,
                        isDeviceProvisioned,
                        shadeDisplayId,
                        isDreamingWithOverlay,
                        isMirroringEnabled,
                    )
                    .also { type ->
                        debugLog(enabled = DEBUG, tag = TAG) {
                            "Display ${display.displayId} - isKeyguardShowing: $isKeyguardShowing, " +
                                "isDeviceProvisioned: $isDeviceProvisioned, " +
                                "shadeDisplayId: $shadeDisplayId, " +
                                "isDreamingWithOverlay: $isDreamingWithOverlay, " +
                                "isMirroringEnabled: $isMirroringEnabled " +
                                "-> presentationType: $type"
                        }
                    }
            }
            .distinctUntilChanged()
            .stateIn(displayCoroutineScope, Eagerly, NONE)
    }

    private fun determinePresentationType(
        isKeyguardShowing: Boolean,
        isDeviceProvisioned: Boolean,
        shadeDisplayId: Int,
        isDreamingWithOverlay: Boolean,
        isMirroringEnabled: Boolean,
    ): WallpaperPresentationType {
        return when {
            isDreamingWithOverlay && isMirroringEnabled -> NONE
            !isDeviceProvisioned ->
                if (ProvisioningPresentationCompatibility.isCompatibleForDisplay(display)) {
                    PROVISIONING
                } else {
                    NONE
                }
            isKeyguardShowing ->
                if (keyguardDisplayManager.get().isKeyguardShowable(display, shadeDisplayId)) {
                    KEYGUARD
                } else {
                    NONE
                }
            else -> NONE
        }
    }

    private companion object {
        const val TAG = "WallpaperPresentation"
        val DEBUG
            get() = Log.isLoggable(TAG, Log.DEBUG)
    }
}
