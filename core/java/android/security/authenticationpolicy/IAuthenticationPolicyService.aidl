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

package android.security.authenticationpolicy;

import android.os.UserHandle;
import android.proximity.IProximityResultCallback;
import android.security.authenticationpolicy.EnableSecureLockDeviceParams;
import android.security.authenticationpolicy.DisableSecureLockDeviceParams;
import android.security.authenticationpolicy.ISecureLockDeviceStatusListener;

/**
 * Communication channel from AuthenticationPolicyManager to AuthenticationPolicyService.
 * @hide
 */
interface IAuthenticationPolicyService {
    @EnforcePermission("MANAGE_SECURE_LOCK_DEVICE")
    int enableSecureLockDevice(in UserHandle user, in EnableSecureLockDeviceParams params);

    @EnforcePermission("MANAGE_SECURE_LOCK_DEVICE")
    int disableSecureLockDevice(in UserHandle user, in DisableSecureLockDeviceParams params);

    @EnforcePermission("MANAGE_SECURE_LOCK_DEVICE")
    int getSecureLockDeviceAvailability(in UserHandle user);

    @EnforcePermission("MANAGE_SECURE_LOCK_DEVICE")
    boolean isSecureLockDeviceEnabled();

    @EnforcePermission("MANAGE_SECURE_LOCK_DEVICE")
    void registerSecureLockDeviceStatusListener(in UserHandle user,
            in ISecureLockDeviceStatusListener listener);

    @EnforcePermission("MANAGE_SECURE_LOCK_DEVICE")
    void unregisterSecureLockDeviceStatusListener(in ISecureLockDeviceStatusListener listener);

    @EnforcePermission("TEST_BIOMETRIC")
    void setSecureLockDeviceTestStatus(boolean isTestMode);

    @EnforcePermission("USE_BIOMETRIC_INTERNAL")
    void startWatchRangingForIdentityCheck(in long authenticationRequestId, in IProximityResultCallback resultCallback);

    @EnforcePermission("USE_BIOMETRIC_INTERNAL")
    void cancelWatchRangingForRequestId(in long authenticationRequestId);

    @EnforcePermission("USE_BIOMETRIC_INTERNAL")
    void isWatchRangingAvailable(in IProximityResultCallback resultCallback);
}