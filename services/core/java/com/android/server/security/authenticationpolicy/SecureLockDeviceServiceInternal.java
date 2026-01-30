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

package com.android.server.security.authenticationpolicy;

import android.os.UserHandle;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.security.authenticationpolicy.AuthenticationPolicyManager.DisableSecureLockDeviceRequestStatus;
import android.security.authenticationpolicy.AuthenticationPolicyManager.EnableSecureLockDeviceRequestStatus;
import android.security.authenticationpolicy.AuthenticationPolicyManager.GetSecureLockDeviceAvailabilityRequestStatus;
import android.security.authenticationpolicy.DisableSecureLockDeviceParams;
import android.security.authenticationpolicy.EnableSecureLockDeviceParams;
import android.security.authenticationpolicy.ISecureLockDeviceStatusListener;

/**
 * Local system service interface for {@link SecureLockDeviceService}.
 *
 * <p>No permission / argument checks will be performed inside.
 * Callers must check the calling app permission and the calling package name.
 *
 * @hide
 */
public abstract class SecureLockDeviceServiceInternal {
    private static final String TAG = "SecureLockDeviceServiceInternal";

    /**
     * @see AuthenticationPolicyManager#getSecureLockDeviceAvailability()
     * @param user {@link UserHandle} to check that secure lock device is available for
     * @return {@link GetSecureLockDeviceAvailabilityRequestStatus} int indicating whether secure
     * lock device is available for the calling user
     *
     * @hide
     */
    @GetSecureLockDeviceAvailabilityRequestStatus
    public abstract int getSecureLockDeviceAvailability(UserHandle user);

    /**
     * @see AuthenticationPolicyManager#enableSecureLockDevice(EnableSecureLockDeviceParams)
     * @param user {@link UserHandle} secure lock device is being disabled for
     * @param params {@link EnableSecureLockDeviceParams} for caller to supply params related
     *               to the secure lock request
     * @return {@link EnableSecureLockDeviceRequestStatus} int indicating the result of the
     * secure lock request
     */
    @EnableSecureLockDeviceRequestStatus
    public abstract int enableSecureLockDevice(UserHandle user,
            EnableSecureLockDeviceParams params);

    /**
     * @see AuthenticationPolicyManager#disableSecureLockDevice(DisableSecureLockDeviceParams)
     * @param user {@link UserHandle} secure lock device is being disabled for
     * @param params {@link DisableSecureLockDeviceParams} for caller to supply params related
     *               to the secure lock device request
     * @param authenticationComplete indicates if secure lock device is being disabled as a result
     *                               of successful two-factor primary and biometric authentication
     * @return {@link DisableSecureLockDeviceRequestStatus} int indicating the result of the
     * secure lock device request
     */
    @DisableSecureLockDeviceRequestStatus
    public abstract int disableSecureLockDevice(UserHandle user,
            DisableSecureLockDeviceParams params, boolean authenticationComplete);

    /**
     * @see SecureLockDeviceService#onStrongBiometricAuthenticationSuccess
     * @param user that performed the successful biometric authentication
     * @hide
     */
    public abstract void onStrongBiometricAuthenticationSuccess(UserHandle user);

    /**
     * @see SecureLockDeviceService#hasUserCompletedTwoFactorAuthentication
     * @param user to check for two-factor authentication completion
     * @hide
     */
    public abstract boolean hasUserCompletedTwoFactorAuthentication(UserHandle user);

    /**
     * @see AuthenticationPolicyManager#isSecureLockDeviceEnabled()
     * @return true if secure lock device is enabled, false otherwise
     */
    public abstract boolean isSecureLockDeviceEnabled();

    /**
     * @see AuthenticationPolicyManager#registerSecureLockDeviceStatusListener
     * @param user user associated with the calling context to notify of updates to secure
     *             lock device availability
     * @param listener {@link ISecureLockDeviceStatusListener} to register for updates to secure
     *                                                        lock device status
     */
    public abstract void registerSecureLockDeviceStatusListener(UserHandle user,
            ISecureLockDeviceStatusListener listener);

    /**
     * @see AuthenticationPolicyManager#unregisterSecureLockDeviceStatusListener
     * @param listener {@link ISecureLockDeviceStatusListener} to unregister from updates to secure
     *                                                        lock device status
     */
    public abstract void unregisterSecureLockDeviceStatusListener(
            ISecureLockDeviceStatusListener listener);

    /**
     * @see AuthenticationPolicyManager#setSecureLockDeviceTestStatus(boolean)
     */
    public abstract void setSecureLockDeviceTestStatus(boolean isTestMode);
}