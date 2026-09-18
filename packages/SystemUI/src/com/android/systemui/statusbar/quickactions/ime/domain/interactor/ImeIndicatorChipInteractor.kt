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

package com.android.systemui.statusbar.quickactions.ime.domain.interactor

import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.inputmethod.data.repository.InputMethodRepository
import com.android.systemui.inputmethod.domain.interactor.InputMethodInteractor
import com.android.systemui.statusbar.policy.domain.interactor.DeviceProvisioningInteractor
import com.android.systemui.statusbar.policy.domain.interactor.UserSetupInteractor
import com.android.systemui.statusbar.quickactions.ime.shared.model.ImeIndicatorChipModel
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.user.domain.interactor.UserLogoutInteractor
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Interactor for managing the state of the IME indicator chip in the status bar. */
@SysUISingleton
class ImeIndicatorChipInteractor
@Inject
constructor(
    @param:Background private val scope: CoroutineScope,
    private val inputMethodInteractor: InputMethodInteractor,
    private val inputMethodRepository: InputMethodRepository,
    private val userRepository: UserRepository,
    private val secureSettings: SecureSettings,
    private val userSetupInteractor: UserSetupInteractor,
    private val userLogoutInteractor: UserLogoutInteractor,
    private val deviceProvisioningInteractor: DeviceProvisioningInteractor,
    private val deviceEntryInteractor: DeviceEntryInteractor,
) {
    private val isFeatureEnabled: Boolean
        get() = Flags.statusBarImeChip()

    // The IME chip can be shown when:
    // 1. the user is logged in and the device is unlocked, to allow the user to switch between
    // their enabled IME subtypes, or
    // 2. the device is not provisioned or the user is not set up, to allow a new user to switch
    // between default IME subtypes while completing the setup process.
    // The chip is not shown on the lock screen or login screen since it isn't needed in these
    // cases.
    //
    // TODO(b/485341693): Long term, it would be better to have a mechanism to switch between IME
    // subtypes in OOBE that doesn't rely on the status bar. When this has been implemented, we can
    // go back to showing the IME chip only when the user is logged in and device is unlocked.
    private val isUnlockedOrInSetup: Flow<Boolean> =
        combine(
            userSetupInteractor.isUserSetUp,
            userLogoutInteractor.isLogoutEnabled,
            deviceProvisioningInteractor.isDeviceProvisioned,
            deviceEntryInteractor.isDeviceEntered,
        ) { isUserSetUp, isLoggedIn, isDeviceProvisioned, isDeviceEntered ->
            (isLoggedIn && isDeviceEntered) || !isDeviceProvisioned || !isUserSetUp
        }

    /** The current state of the IME indicator chip. */
    val chipModel: StateFlow<ImeIndicatorChipModel> =
        isUnlockedOrInSetup
            .map { it && isFeatureEnabled }
            .flatMapLatest { preconditionsMet ->
                if (preconditionsMet) {
                    userRepository.selectedUserInfo.flatMapLatest { userInfo ->
                        val user = userInfo.userHandle
                        val hasMultipleEnabledImesOrSubtypes =
                            secureSettings
                                .observerFlow(
                                    user.identifier,
                                    Settings.Secure.ENABLED_INPUT_METHODS,
                                )
                                .onStart { emit(Unit) }
                                .mapLatest {
                                    inputMethodInteractor.hasMultipleEnabledImesOrSubtypes(
                                        user.identifier
                                    )
                                }

                        combine(
                            hasMultipleEnabledImesOrSubtypes,
                            inputMethodRepository.selectedInputMethodSubtype(user),
                        ) { isVisible, subtype ->
                            ImeIndicatorChipModel(isVisible = isVisible, selectedSubtype = subtype)
                        }
                    }
                } else {
                    flowOf(ImeIndicatorChipModel(isVisible = false, selectedSubtype = null))
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = ImeIndicatorChipModel(isFeatureEnabled, selectedSubtype = null),
            )

    fun toggleInputMethodPicker(displayId: Int) {
        scope.launch {
            inputMethodRepository.toggleInputMethodPicker(
                showAuxiliarySubtypes = true,
                entryPoint = InputMethodManager.IM_PICKER_ENTRY_POINT_STATUS_BAR_CHIP,
                displayId = displayId,
            )
        }
    }

    fun hideInputMethodPicker(displayId: Int) {
        scope.launch { inputMethodRepository.hideInputMethodPicker(displayId) }
    }
}
