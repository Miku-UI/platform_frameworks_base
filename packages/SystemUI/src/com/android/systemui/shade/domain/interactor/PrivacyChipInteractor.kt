/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shade.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.privacy.OngoingPrivacyChip
import com.android.systemui.privacy.PrivacyDialogController
import com.android.systemui.privacy.PrivacyDialogControllerV2
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.shade.data.repository.PrivacyChipRepository
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@SysUISingleton
class PrivacyChipInteractor
@Inject
constructor(
    @Application val applicationScope: CoroutineScope,
    private val repository: PrivacyChipRepository,
    private val privacyDialogController: PrivacyDialogController,
    private val privacyDialogControllerV2: PrivacyDialogControllerV2,
    private val deviceProvisionedController: DeviceProvisionedController,
    private val shadeDialogContextInteractor: ShadeDialogContextInteractor,
) {
    /** The list of PrivacyItems to be displayed by the privacy chip. */
    val privacyItems: StateFlow<List<PrivacyItem>> = repository.privacyItems

    /** Whether or not mic & camera indicators are enabled in the device privacy config. */
    val isMicCameraIndicationEnabled: StateFlow<Boolean> = repository.isMicCameraIndicationEnabled

    /** Whether or not location indicators are enabled in the device privacy config. */
    val isLocationIndicationEnabled: StateFlow<Boolean> = repository.isLocationIndicationEnabled

    /** Notifies that the privacy chip was clicked. */
    fun onPrivacyChipClicked(privacyChip: OngoingPrivacyChip) {
        if (!deviceProvisionedController.isDeviceProvisioned) return

        applicationScope.launch {
            if (repository.isSafetyCenterEnabled()) {
                privacyDialogControllerV2.showDialog(
                    shadeDialogContextInteractor.context,
                    privacyChip,
                )
            } else {
                privacyDialogController.showDialog(shadeDialogContextInteractor.context)
            }
        }
    }
}
