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

package com.android.server.permission;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.TestApi;

import com.android.internal.annotations.Keep;

import java.util.List;

/**
 * In-process API for server side permission related infrastructure.
 *
 * @hide
 */
@Keep
@TestApi
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@FlaggedApi(android.permission.flags.Flags.FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
public interface PermissionManagerLocal {

    /**
     * Get whether signature permission allowlist is enforced even on debuggable builds.
     *
     * @return whether the signature permission allowlist is force enforced
     *
     * @hide
     */
    @TestApi
    boolean isSignaturePermissionAllowlistForceEnforced();

    /**
     * Set whether signature permission allowlist is enforced even on debuggable builds.
     *
     * @param forceEnforced whether the signature permission allowlist is force enforced
     *
     * @hide
     */
    @TestApi
    void setSignaturePermissionAllowlistForceEnforced(boolean forceEnforced);

    /**
     * Register a BPF map to be updated for the states of certain permissions. This is only
     * necessary because the BPF programs in the kernel cannot call into system server for
     * permission checks, while all other Java or native services should still call into system
     * server via binder instead.
     * <p>
     * Currently only the following permissions are allowed to be used with this API:
     * <ul>
     * <li>{@link android.Manifest.permission.INTERNET}</li>
     * <li>{@link android.Manifest.permission.ACCESS_LOCAL_NETWORK}</li>
     * <li>{@link android.Manifest.permission.UPDATE_DEVICE_STATS}</li>
     * </ul>
     *
     * @param bpfMap          the BPF map to register for permission state updates
     * @param permissionNames the names of the permissions to be monitored
     *
     * @hide
     */
    @FlaggedApi(android.permission.flags.Flags.FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    void registerBpfMap(@NonNull PermissionBpfMap bpfMap, @NonNull List<String> permissionNames);

    /**
     * Unregister a BPF map to stop receiving permission updates.
     *
     * @param bpfMap the BPF map to unregister
     *
     * @hide
     */
    @FlaggedApi(android.permission.flags.Flags.FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    void unregisterBpfMap(@NonNull PermissionBpfMap bpfMap);
}
