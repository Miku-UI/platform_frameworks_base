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

package com.android.systemui.deviceentry.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.deviceentry.data.repository.DeviceEntryBypassRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Encapsulates business logic and state for functionality related to bypassing the lockscreen
 * when/if biometric authentication succeeds.
 */
@SysUISingleton
class DeviceEntryBypassInteractor @Inject constructor(repository: DeviceEntryBypassRepository) {

    /**
     * Whether lockscreen bypass is enabled. When enabled, the lockscreen will be automatically
     * dismissed once the authentication challenge is completed. For example, completing a biometric
     * authentication challenge via face unlock or fingerprint sensor can automatically bypass the
     * lockscreen.
     */
    val isBypassEnabled: StateFlow<Boolean> = repository.isBypassEnabled
}
