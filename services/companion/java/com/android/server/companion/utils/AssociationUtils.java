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

package com.android.server.companion.utils;

import android.annotation.UserIdInt;
import android.companion.AssociationInfo;
import android.companion.DeviceId;
import android.net.MacAddress;

public final class AssociationUtils {

    /** Range of Association IDs allocated for a user. */
    private static final int ASSOCIATIONS_IDS_PER_USER_RANGE = 100000;

    /**
     * Get the left boundary of the association id range for the user.
     */
    public static int getFirstAssociationIdForUser(@UserIdInt int userId) {
        // We want the IDs to start from 1, not 0.
        return userId * ASSOCIATIONS_IDS_PER_USER_RANGE + 1;
    }

    /**
     * Get the right boundary of the association id range for the user.
     */
    public static int getLastAssociationIdForUser(@UserIdInt int userId) {
        return (userId + 1) * ASSOCIATIONS_IDS_PER_USER_RANGE;
    }

    /**
     * Get the mac address of the association. Checks the device ID mac address as fallback.
     */
    public static MacAddress getMacAddress(AssociationInfo association) {
        if (association == null) {
            return null;
        }
        if (association.getDeviceMacAddress() != null) {
            return association.getDeviceMacAddress();
        }
        final DeviceId deviceId = association.getDeviceId();
        if (deviceId != null && deviceId.getMacAddress() != null) {
            return deviceId.getMacAddress();
        }
        return null;
    }

    private AssociationUtils() {}
}
