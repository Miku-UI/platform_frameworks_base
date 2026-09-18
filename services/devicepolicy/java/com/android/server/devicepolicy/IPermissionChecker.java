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

package com.android.server.devicepolicy;

import android.annotation.NonNull;

/**
 * Interface for {@link PermissionChecker} which allows mocking permission checks during unittests.
 * Only contains the subset of methods that are currently requires in tests.
 * Feel free to add more methods as you write more tests.
 */
public interface IPermissionChecker {
    /**
     * Checks if the calling process has been granted permission to apply a device policy.
     *
     * @param permission The name of the permission being checked.
     * @param caller     The identity of the calling application.
     * @throws SecurityException if the caller has not been granted the given permission.
     */
    void enforce(@NonNull String permission, @NonNull CallerIdentity caller)
            throws SecurityException;

    /**
     * Checks if the calling process has been granted a specific permission.
     * @param permission The name of the permission being checked.
     * @param caller The identity of the calling application.
     * @return true if the permission has been granted.
     */
    boolean hasPermission(@NonNull String permission, @NonNull CallerIdentity caller);
}
