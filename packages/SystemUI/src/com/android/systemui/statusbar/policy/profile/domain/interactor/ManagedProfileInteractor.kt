/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy.profile.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.statusbar.policy.profile.data.repository.ManagedProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Interactor to manage and provide an observable state of managed profile status. */
@SysUISingleton
class ManagedProfileInteractor
@Inject
constructor(
    repository: ManagedProfileRepository,
    keyguardTransitionInteractor: KeyguardTransitionInteractor,
    deviceEntryInteractor: DeviceEntryInteractor,
) {
    private val shouldShowProfileInfo: Flow<Boolean> =
        combine(
            deviceEntryInteractor.isDeviceEntered,
            keyguardTransitionInteractor.currentKeyguardState,
        ) { deviceEntered, keyguardState ->
            deviceEntered || keyguardState == KeyguardState.OCCLUDED
        }

    /** Flow that emits the current profile info, or null if it should be hidden. */
    val currentProfileInfo =
        combine(repository.currentProfileInfo, shouldShowProfileInfo) { profileInfo, shouldShow ->
            if (shouldShow) {
                profileInfo
            } else {
                null
            }
        }
}
