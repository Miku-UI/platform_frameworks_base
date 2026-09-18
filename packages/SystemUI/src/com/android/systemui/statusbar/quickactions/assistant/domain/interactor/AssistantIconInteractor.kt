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

package com.android.systemui.statusbar.quickactions.assistant.domain.interactor

import android.content.Context
import android.content.res.Resources
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.domain.interactor.DeviceProvisioningInteractor
import com.android.systemui.statusbar.policy.domain.interactor.UserSetupInteractor
import com.android.systemui.statusbar.quickactions.assistant.data.repository.AssistantRepository
import com.android.systemui.statusbar.quickactions.assistant.shared.model.AssistantIconSharedModel
import com.android.systemui.user.domain.interactor.UserLogoutInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * An interactor that provides information about the assistant icon, such as whether it should be
 * shown and details about the current assistant application.
 */
interface AssistantIconInteractor {
    /**
     * A [StateFlow] that provides the [AssistantIconSharedModel] which contains information
     * necessary to display the assistant icon.
     *
     * The model is updated based on whether the device is entered and the current
     * [android.content.ComponentName] of the primary assistant app.
     */
    val assistantIconSharedModel: StateFlow<AssistantIconSharedModel>

    /** This invokes the assistant app. */
    fun startAssistant(context: Context)
}

@SysUISingleton
class AssistantIconInteractorImpl
@Inject
constructor(
    @Main private val resources: Resources,
    @Background private val scope: CoroutineScope,
    private val userSetupInteractor: UserSetupInteractor,
    private val userLogoutInteractor: UserLogoutInteractor,
    private val deviceProvisioningInteractor: DeviceProvisioningInteractor,
    private val deviceEntryInteractor: DeviceEntryInteractor,
    private val assistantRepository: AssistantRepository,
) : AssistantIconInteractor {
    override val assistantIconSharedModel: StateFlow<AssistantIconSharedModel> =
        combine(
                userSetupInteractor.isUserSetUp,
                userLogoutInteractor.isLogoutEnabled,
                deviceProvisioningInteractor.isDeviceProvisioned,
                deviceEntryInteractor.isDeviceEntered,
                assistantRepository.assistInfo,
            ) { isUserSetup, isLoggedIn, isDeviceProvisioned, isDeviceEntered, assistInfo ->
                if (
                    !isUserSetup ||
                        !isLoggedIn ||
                        !isDeviceProvisioned ||
                        !isDeviceEntered ||
                        assistInfo == null ||
                        assistInfo.packageName == ""
                ) {
                    defaultSharedModel
                } else {
                    AssistantIconSharedModel(
                        assistInfo,
                        isStatusBarAssistantPackage =
                            assistInfo.packageName ==
                                resources.getString(R.string.config_statusBarAssistantPackage),
                        isAssistShown = false,
                    )
                }
            }
            .stateIn(
                scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = defaultSharedModel,
            )

    override fun startAssistant(context: Context) {
        assistantRepository.startAssistant(context)
    }

    private companion object {
        private val defaultSharedModel =
            AssistantIconSharedModel(
                assistInfo = null,
                isStatusBarAssistantPackage = false,
                isAssistShown = false,
            )
    }
}
