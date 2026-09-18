/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.binder

import android.annotation.DrawableRes
import com.android.systemui.CoreStartable
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.Text
import com.android.systemui.common.shared.model.TintedIcon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.OccludingAppDeviceEntryInteractor
import com.android.systemui.res.R
import com.android.systemui.temporarydisplay.ViewPriority
import com.android.systemui.temporarydisplay.chipbar.ChipbarCoordinator
import com.android.systemui.temporarydisplay.chipbar.ChipbarInfo
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Handles showing chipbar for keyguard-related features. */
@SysUISingleton
class KeyguardChipbarViewBinder
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val chipbarCoordinator: ChipbarCoordinator,
    private val occludingAppDeviceEntryInteractor: OccludingAppDeviceEntryInteractor,
) : CoreStartable {

    override fun start() {
        handleBiometricMessagesOverOccludingApps()
    }

    private fun handleBiometricMessagesOverOccludingApps() {
        applicationScope.launch {
            occludingAppDeviceEntryInteractor.message.collect { biometricMessage ->
                if (biometricMessage?.message != null) {
                    chipbarCoordinator.displayView(
                        createChipbarInfo(
                            message = biometricMessage.message,
                            id = CHIPBAR_BIOMETRIC_MSG_ID,
                            icon = R.drawable.ic_lock,
                        )
                    )
                } else {
                    chipbarCoordinator.removeView(CHIPBAR_BIOMETRIC_MSG_ID, "occludingAppMsgNull")
                }
            }
        }
    }

    /**
     * Creates an instance of [ChipbarInfo] that can be sent to [ChipbarCoordinator] for display.
     */
    private fun createChipbarInfo(
        message: String,
        id: String,
        @DrawableRes icon: Int,
    ): ChipbarInfo {
        return ChipbarInfo(
            startIcon = TintedIcon(Icon.Resource(icon, null), ChipbarInfo.DEFAULT_ICON_TINT),
            text = Text.Loaded(message),
            endItem = null,
            vibrationEffect = null,
            windowTitle = "KeyguardChipbar",
            wakeReason = "KEYGUARD_CHIPBAR",
            timeoutMs = 3500,
            id = id,
            priority = ViewPriority.CRITICAL,
            instanceId = null,
        )
    }

    companion object {
        private const val CHIPBAR_BIOMETRIC_MSG_ID = "occluding_app_device_entry_unlock_msg"
    }
}
