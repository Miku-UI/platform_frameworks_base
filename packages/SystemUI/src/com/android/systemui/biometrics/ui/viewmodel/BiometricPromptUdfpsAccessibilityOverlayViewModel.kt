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

package com.android.systemui.biometrics.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.android.systemui.accessibility.domain.interactor.AccessibilityInteractor
import com.android.systemui.biometrics.UdfpsUtils
import com.android.systemui.biometrics.domain.interactor.UdfpsOverlayInteractor
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryUdfpsInteractor
import com.android.systemui.deviceentry.ui.viewmodel.UdfpsAccessibilityOverlayViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Models the UI state for the biometric prompt UDFPS accessibility overlay */
class BiometricPromptUdfpsAccessibilityOverlayViewModel(
    @Application private val applicationContext: Context,
    deviceEntryUdfpsInteractor: DeviceEntryUdfpsInteractor,
    udfpsOverlayInteractor: UdfpsOverlayInteractor,
    udfpsUtils: UdfpsUtils,
    accessibilityInteractor: AccessibilityInteractor,
    private val promptViewModel: PromptViewModel,
) :
    UdfpsAccessibilityOverlayViewModel(
        applicationContext,
        udfpsOverlayInteractor,
        deviceEntryUdfpsInteractor,
        udfpsUtils,
        accessibilityInteractor,
    ) {

    /** Whether the under display fingerprint sensor is currently running. */
    override val isListeningForUdfps: StateFlow<Boolean> =
        promptViewModel.modalities
            .map { it.hasUdfps }
            .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = false)

    /**
     * Overlay is only visible if the prompt modalities include UDFPS and the user is not yet
     * authenticated.
     */
    override fun isVisibleWhenTouchExplorationEnabled(): Flow<Boolean> =
        combine(promptViewModel.modalities, promptViewModel.isAuthenticated) { modalities, authState
            ->
            modalities.hasUdfps && !authState.isAuthenticated
        }
}
