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

package android.security;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Manages KeyChain certificates and grants.
 */
@FlaggedApi(android.security.Flags.FLAG_ENABLE_DEVICE_CERTIFICATES)
public final class KeyChainManager {

    private KeyChainManager() {
    }

    /**
     * Annotation for Key Pair scope.
     *
     * @hide
     */
    @IntDef(prefix = {"KEYPAIR_SCOPE_"}, value = {
            KEYPAIR_SCOPE_USER,
            KEYPAIR_SCOPE_DEVICE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface KeyPairScope {
    }

    /**
     * Scope indicating the key pair is installed device-wide and is visible
     * to all affiliated users on the device.
     */
    @FlaggedApi(android.security.Flags.FLAG_ENABLE_DEVICE_CERTIFICATES)
    public static final int KEYPAIR_SCOPE_DEVICE = 1;

    /**
     * Scope indicating the key pair is installed for the current user only.
     * This is the default scope for existing KeyChain credentials.
     */
    @FlaggedApi(android.security.Flags.FLAG_ENABLE_DEVICE_CERTIFICATES)
    public static final int KEYPAIR_SCOPE_USER = 2;

}
