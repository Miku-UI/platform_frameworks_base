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

package com.android.server.notification;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Process;
import android.util.Log;

import com.android.server.LocalServices;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;

import java.util.Objects;

/** NotificationManagerService helper for computer control notifications. */
public interface ComputerControlHelper {
    /** Returns true if the notification is associated with a ComputerControlSession. */
    boolean isComputerControlNotification(
            int notificationId, String notificationTag, String packageName);

    /**
     * Returns true if the given uid is eligible to apply FLAG_COMPUTER_CONTROL on a
     * notification.
     */
    boolean isUidEligibleToSetComputerControlFlag(int uid);

    @Nullable
    static ComputerControlHelper forLocalService() {
        VirtualDeviceManagerInternal virtualDeviceManagerInternal =
                LocalServices.getService(VirtualDeviceManagerInternal.class);
        if (virtualDeviceManagerInternal == null) {
            return null;
        }
        return new ComputerControlHelper.Impl(virtualDeviceManagerInternal);
    }

    class Impl implements ComputerControlHelper {
        private static final String TAG = "ComputerControlHelper";
        static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

        @NonNull
        private final VirtualDeviceManagerInternal mVirtualDeviceManagerInternal;

        public Impl(@NonNull VirtualDeviceManagerInternal virtualDeviceManagerInternal) {
            mVirtualDeviceManagerInternal = Objects.requireNonNull(virtualDeviceManagerInternal);
        }

        @Override
        public boolean isComputerControlNotification(
                int notificationId, String notificationTag, String packageName) {
            return mVirtualDeviceManagerInternal.isComputerControlNotification(
                    notificationId, notificationTag, packageName);
        }

        @Override
        public boolean isUidEligibleToSetComputerControlFlag(int uid) {
            return uid == Process.SYSTEM_UID;
        }
    }
}
