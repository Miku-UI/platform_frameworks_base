/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.keyguard;

import static android.security.Flags.secureLockDevice;

import static com.android.systemui.DejankUtils.whitelistIpcs;

import android.app.admin.DevicePolicyManager;
import android.content.res.Resources;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor;
import com.android.systemui.util.kotlin.JavaAdapter;

import dagger.Lazy;

import javax.inject.Inject;
import javax.inject.Provider;

@SysUISingleton
public class KeyguardSecurityModel {
    /**
     * The different types of security available.
     * @see KeyguardSecurityContainerController#showSecurityScreen
     */
    public enum SecurityMode {
        Invalid, // NULL state
        None, // No security enabled
        Pattern, // Unlock by drawing a pattern.
        Password, // Unlock by entering an alphanumeric password
        PIN, // Strictly numeric password
        SimPin, // Unlock by entering a sim pin.
        SimPuk, // Unlock by entering a sim puk
        // TODO(b/427071498): remove upon SceneContainerFlag removal
        SecureLockDeviceBiometricAuth // Unlock by authenticating biometric for secure lock device
    }

    private final boolean mIsPukScreenAvailable;
    private boolean mRequiresBiometricForSecureLockDevice;
    private boolean mAuthenticatedInSecureLockDevice;

    private final LockPatternUtils mLockPatternUtils;
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private final Provider<JavaAdapter> mJavaAdapter;
    private final Lazy<SecureLockDeviceInteractor> mSecureLockDeviceInteractor;

    @Inject
    KeyguardSecurityModel(
            @Main Resources resources,
            LockPatternUtils lockPatternUtils,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            Provider<JavaAdapter> javaAdapter,
            Lazy<SecureLockDeviceInteractor> secureLockDeviceInteractor
    ) {
        mIsPukScreenAvailable = resources.getBoolean(
                com.android.internal.R.bool.config_enable_puk_unlock_screen);
        mLockPatternUtils = lockPatternUtils;
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mJavaAdapter = javaAdapter;
        mSecureLockDeviceInteractor = secureLockDeviceInteractor;

        if (secureLockDevice()) {
            mJavaAdapter.get().alwaysCollectFlow(
                    mSecureLockDeviceInteractor
                            .get()
                            .getRequiresStrongBiometricAuthForSecureLockDevice(),
                    this::onRequiresStrongBiometricAuthForSecureLockDevice
            );
            mJavaAdapter.get().alwaysCollectFlow(
                    mSecureLockDeviceInteractor.get().isAuthenticatedButPendingDismissal(),
                    this::onSecureLockDeviceAuthenticationComplete
            );
        }
    }

    private void onRequiresStrongBiometricAuthForSecureLockDevice(boolean requiresBiometricAuth) {
        mRequiresBiometricForSecureLockDevice = requiresBiometricAuth;
    }

    private void onSecureLockDeviceAuthenticationComplete(boolean authenticatedInSecureLockDevice) {
        mAuthenticatedInSecureLockDevice = authenticatedInSecureLockDevice;
    }

    public SecurityMode getSecurityMode(int userId) {
        if (secureLockDevice()
                && (mRequiresBiometricForSecureLockDevice || mAuthenticatedInSecureLockDevice)) {
            return SecurityMode.SecureLockDeviceBiometricAuth;
        }

        if (mIsPukScreenAvailable && SubscriptionManager.isValidSubscriptionId(
                mKeyguardUpdateMonitor.getNextSubIdForState(
                        TelephonyManager.SIM_STATE_PUK_REQUIRED))) {
            return SecurityMode.SimPuk;
        }

        if (SubscriptionManager.isValidSubscriptionId(
                mKeyguardUpdateMonitor.getNextSubIdForState(
                        TelephonyManager.SIM_STATE_PIN_REQUIRED))) {
            return SecurityMode.SimPin;
        }

        final int security = whitelistIpcs(() ->
                mLockPatternUtils.getActivePasswordQuality(userId));
        switch (security) {
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
                return SecurityMode.PIN;

            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
            case DevicePolicyManager.PASSWORD_QUALITY_MANAGED:
                return SecurityMode.Password;

            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                return SecurityMode.Pattern;
            case DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED:
                return SecurityMode.None;

            default:
                throw new IllegalStateException("Unknown security quality:" + security);
        }
    }
}
