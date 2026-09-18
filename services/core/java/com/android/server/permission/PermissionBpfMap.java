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
package com.android.server.permission;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.util.SparseIntArray;

/**
 * An interface for managing permission states stored in a BPF map.
 *
 * <p>This interface provides methods to set, update, and remove permission states stored in a BPF
 * map.
 *
 * <p>Implementations of this interface are responsible for interacting with an actual BPF map and
 * ensuring that the permission states inside it are correctly updated.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@FlaggedApi(android.permission.flags.Flags.FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
public interface PermissionBpfMap {
    /**
     * Set the permission states for a set of UIDs in the BPF map.
     *
     * @param uidsPermissionBits a mapping from UIDs to a bitmap of permission states. Bits in the
     *                        bitmap are in the same order as the list of permissions that was
     *                        passed in when this BPF map is registered in
     *                        {@link
     *                        com.android.server.permission.PermissionManagerLocal#registerBpfMap},
     *                        and are set to {@code 1} when the corresponding permission is
     *                        granted. A runtime permission is considered granted only if it is
     *                        fully granted including its app op.
     */
    void setUidsPermissionBits(@NonNull SparseIntArray uidsPermissionBits);

    /**
     * Remove the permission states of all UIDs associated with an app ID from the BPF map.
     *
     * @param appId the app ID to remove
     */
    void removeAppId(int appId);

    /**
     * Remove the permission states of all UIDs associated with a user ID from the BPF map.
     *
     * @param userId the user ID to remove
     */
    void removeUser(@UserIdInt int userId);
}
