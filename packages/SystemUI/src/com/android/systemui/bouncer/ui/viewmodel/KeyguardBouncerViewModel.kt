/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.bouncer.ui.viewmodel

import android.security.Flags.secureLockDevice
import android.view.View
import androidx.lifecycle.ViewModel
import com.android.keyguard.KeyguardSecurityModel
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor
import com.android.systemui.bouncer.shared.model.BouncerShowMessageModel
import com.android.systemui.bouncer.ui.BouncerView
import com.android.systemui.bouncer.ui.BouncerViewDelegate
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Models UI state for the lock screen bouncer; handles user input. */
class KeyguardBouncerViewModel
@Inject
constructor(
    private val view: BouncerView,
    private val interactor: PrimaryBouncerInteractor,
    secureLockDeviceInteractor: Lazy<SecureLockDeviceInteractor>,
    keyguardTransitionInteractor: Lazy<KeyguardTransitionInteractor>,
) : ViewModel() {
    /** Observe on bouncer expansion amount. */
    val bouncerExpansionAmount: Flow<Float> = interactor.panelExpansionAmount

    /** Can the user interact with the view? */
    val isInteractable: Flow<Boolean> = interactor.isInteractable

    /** Observe whether bouncer is showing or not. */
    val isShowing: Flow<Boolean> = interactor.isShowing

    /**
     * Whether secure lock device currently is requesting primary auth.
     *
     * @see {@link SecureLockDeviceInteractor#requiresPrimaryAuthForSecureLockDevice}
     */
    val requiresPrimaryAuthForSecureLockDevice: Flow<Boolean> =
        if (secureLockDevice()) {
            secureLockDeviceInteractor.get().requiresPrimaryAuthForSecureLockDevice
        } else {
            flowOf(false)
        }

    /**
     * Whether secure lock device currently is requesting strong biometric auth.
     *
     * @see {@link SecureLockDeviceInteractor#requiresStrongBiometricAuthForSecureLockDevice}
     */
    val requiresStrongBiometricAuthForSecureLockDevice: Flow<Boolean> =
        if (secureLockDevice()) {
            secureLockDeviceInteractor.get().requiresStrongBiometricAuthForSecureLockDevice
        } else {
            flowOf(false)
        }

    /** Whether keyguard has finished a transition to GONE. */
    val isTransitionToGoneFinished: Flow<Boolean> =
        keyguardTransitionInteractor.get().isFinishedIn(KeyguardState.GONE)

    /** Last shown keyguard security mode. */
    val lastShownSecurityMode: StateFlow<KeyguardSecurityModel.SecurityMode?> =
        interactor.lastShownSecurityMode

    /**
     * Whether secure lock device two-factor authentication is complete, all animations have been
     * played, and the UI is ready to be dismissed.
     */
    val isReadyToDismissSecureLockDeviceOnUnlock: Flow<Boolean> =
        if (secureLockDevice()) {
            secureLockDeviceInteractor.get().isFullyUnlockedAndReadyToDismiss
        } else {
            flowOf(false)
        }

    /** Whether secure lock device is showing the confirm biometric auth button. */
    val showConfirmBiometricAuthButton: Flow<Boolean> =
        if (secureLockDevice()) {
            secureLockDeviceInteractor.get().showConfirmBiometricAuthButton
        } else {
            flowOf(false)
        }

    /** Whether an error is being shown during secure lock device biometric authentication */
    val showingError: Flow<Boolean> =
        if (secureLockDevice()) {
            secureLockDeviceInteractor.get().showingError
        } else {
            flowOf(false)
        }

    /**
     * Whether the try again button is being shown during secure lock device biometric
     * authentication.
     */
    val showTryAgainButton: Flow<Boolean> =
        if (secureLockDevice()) {
            secureLockDeviceInteractor.get().showTryAgainButton
        } else {
            flowOf(false)
        }

    /** Observe whether bouncer is starting to hide. */
    val startingToHide: Flow<Unit> = interactor.startingToHide

    /** Observe whether we want to start the disappear animation. */
    val startDisappearAnimation: Flow<Runnable> = interactor.startingDisappearAnimation

    /** Observe whether we want to update keyguard position. */
    val keyguardPosition: Flow<Float> = interactor.keyguardPosition

    /** Observe whether we want to update resources. */
    val updateResources: Flow<Boolean> = interactor.resourceUpdateRequests

    /** Observe whether we want to set a keyguard message when the bouncer shows. */
    val bouncerShowMessage: Flow<BouncerShowMessageModel> = interactor.showMessage

    /** Observe whether keyguard is authenticated already. */
    val keyguardAuthenticated: Flow<Boolean> = interactor.keyguardAuthenticatedBiometrics

    /** Observe whether we want to update resources. */
    fun notifyUpdateResources() {
        interactor.notifyUpdatedResources()
    }

    /** Notify that keyguard authenticated was handled */
    fun notifyKeyguardAuthenticated() {
        interactor.notifyKeyguardAuthenticatedHandled()
    }

    /** Notifies that the message was shown. */
    fun onMessageShown() {
        interactor.onMessageShown()
    }

    /** Observe whether back button is enabled. */
    fun observeOnIsBackButtonEnabled(systemUiVisibility: () -> Int): Flow<Int> {
        return interactor.isBackButtonEnabled.map { enabled ->
            var vis: Int = systemUiVisibility()
            vis =
                if (enabled) {
                    vis and View.STATUS_BAR_DISABLE_BACK.inv()
                } else {
                    vis or View.STATUS_BAR_DISABLE_BACK
                }
            vis
        }
    }

    /** Set an abstraction that will hold reference to the ui delegate for the bouncer view. */
    fun setBouncerViewDelegate(delegate: BouncerViewDelegate?) {
        view.delegate = delegate
    }
}
