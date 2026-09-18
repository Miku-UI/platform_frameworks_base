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

package com.android.server.companion.devicetrust;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;

/**
 * Interface for plugins that provide pre-shared keys for CDM to use as means of initial transport
 * session verification.
 */
public interface PskProvider {

    /**
     * Uniquely identifiable provider name. Must not exceed 32-bytes in UTF-8 format.
     * @return PSK provider's unique name.
     */
    @NonNull
    String getProviderName();

    /**
     * Gets the pre-shared key for given userId and associationId if available.
     * @param userId user ID
     * @param associationId association ID
     * @return the pre-shared key if available. Returns null otherwise.
     */
    @Nullable
    byte[] getKey(@UserIdInt int userId, int associationId);

    /**
     * Loads keys for user if necessary. CDM will call this for all registered providers after
     * user unlock.
     * @param userId user ID
     */
    default void load(@UserIdInt int userId) {}
}
