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
package com.android.keyguard

import android.content.res.ColorStateList
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.android.systemui.bouncer.ui.helper.BouncerHapticPlayer
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import com.android.systemui.securelockdevice.ui.composable.SecureLockDeviceContent
import com.android.systemui.securelockdevice.ui.viewmodel.SecureLockDeviceBiometricAuthContentViewModel
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import kotlinx.coroutines.launch

/**
 * Controller for the biometric auth view in the legacy keyguard implementation of secure lock
 * device.
 */
class KeyguardSecureLockDeviceBiometricAuthViewController(
    private val view: KeyguardSecureLockDeviceBiometricAuthView,
    private val secureLockDeviceViewModelFactory:
        SecureLockDeviceBiometricAuthContentViewModel.Factory,
    private val secureLockDeviceInteractor: SecureLockDeviceInteractor,
    selectedUserInteractor: SelectedUserInteractor,
    securityMode: KeyguardSecurityModel.SecurityMode?,
    keyguardSecurityCallback: KeyguardSecurityCallback?,
    mEmergencyButtonController: EmergencyButtonController,
    messageAreaControllerFactory: KeyguardMessageAreaController.Factory?,
    featureFlags: FeatureFlags,
    bouncerHapticPlayer: BouncerHapticPlayer?,
) :
    KeyguardInputViewController<KeyguardSecureLockDeviceBiometricAuthView>(
        view,
        securityMode,
        keyguardSecurityCallback,
        mEmergencyButtonController,
        messageAreaControllerFactory,
        featureFlags,
        selectedUserInteractor,
        bouncerHapticPlayer,
    ) {

    public override fun onInit() {
        if (SceneContainerFlag.isEnabled) {
            return
        }

        super.onInit()

        val composeView =
            ComposeView(context).apply {
                id = R.id.secure_lock_device_biometric_auth_content
                setContent {
                    SecureLockDeviceContent(
                        secureLockDeviceViewModelFactory = secureLockDeviceViewModelFactory,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        view.repeatWhenAttached { lifecycleScope.launch { view.addView(composeView) } }
    }

    override fun updateMessageAreaVisibility() {}

    override fun showMessage(
        message: CharSequence?,
        colorState: ColorStateList?,
        animated: Boolean,
    ) {}

    override fun needsInput(): Boolean = false

    override fun startAppearAnimation() {
        super.startAppearAnimation()
    }

    override fun startDisappearAnimation(finishRunnable: Runnable?): Boolean {
        secureLockDeviceInteractor.setDisappearAnimationFinishedRunnable(finishRunnable)
        return true
    }

    override fun getInitialMessageResId(): Int = 0
}
