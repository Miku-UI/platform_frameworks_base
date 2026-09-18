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

package com.android.server.security;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.security.KeyChainException;
import android.security.keymaster.KeymasterCertificateChain;
import android.security.keystore.KeyGenParameterSpec;

/**
 * @hide Provides APIs for the DevicePolicyManagerService. Only for use within the system server.
 */
public abstract class KeyChainManagerInternal {
    /**
     * Installs a key pair and certificate chain into KeyChain for the specified user.
     *
     * @param pkcs8PrivateKey PKCS#8 encoded private key bytes.
     * @param certificate DER-encoded user/leaf certificate bytes.
     * @param certificateChain Array of DER-encoded intermediate/CA certificates bytes, if any.
     * @param alias The alias for the new entry. It is invalid to pass {@code
     *     KeyChain.KEY_ALIAS_SELECTION_DENIED}.
     * @param userId The id of the user for whom to install the key pair.
     * @return true on success, false otherwise.
     */
    public abstract boolean installUserKeyPair(
            @NonNull byte[] pkcs8PrivateKey,
            @NonNull byte[] certificate,
            @Nullable byte[][] certificateChain,
            @NonNull String alias,
            int userId);

    /**
     * Installs a key pair and certificate chain into KeyChain device-wide.
     *
     * @param pkcs8PrivateKey PKCS#8 encoded private key bytes.
     * @param certificate DER-encoded user/leaf certificate bytes.
     * @param certificateChain Array of DER-encoded intermediate/CA certificates bytes, if any.
     * @param alias The alias for the new entry. It is invalid to pass {@code
     *     KeyChain.KEY_ALIAS_SELECTION_DENIED}.
     * @return true on success, false otherwise.
     */
    public abstract boolean installDeviceKeyPair(
            @NonNull byte[] pkcs8PrivateKey,
            @NonNull byte[] certificate,
            @Nullable byte[][] certificateChain,
            @NonNull String alias);

    /**
     * Generates a key pair within KeyChain for the specified user.
     *
     * @param algorithm The key algorithm (e.g., "RSA", "EC").
     * @param keySpec Parameters for key generation.
     * @param userId The id of the user for whom to generate the key pair.
     * @return The {@link KeymasterCertificateChain} for the generated key pair, or {@code null}
     *         if generation failed.
     */
    public abstract KeymasterCertificateChain generateUserKeyPair(
            @NonNull String algorithm,
            @NonNull KeyGenParameterSpec keySpec,
            int userId) throws KeyChainException;

    /**
     * Generates a key pair within KeyChain device-wide.
     *
     * @param algorithm The key algorithm (e.g., "RSA", "EC").
     * @param keySpec Parameters for key generation.
     * @return The {@link KeymasterCertificateChain} for the generated key pair, or {@code null}
     *         if generation failed.
     */
    public abstract KeymasterCertificateChain generateDeviceKeyPair(
            @NonNull String algorithm, @NonNull KeyGenParameterSpec keySpec)
            throws KeyChainException;

    /**
     * Sets the certificate chain for an existing key pair for the specified user.
     *
     * @param alias The alias of the existing key pair.
     * @param clientCertificate DER-encoded user/leaf certificate bytes.
     * @param clientCertificateChain Array of DER-encoded intermediate/CA certificates bytes.
     * @param userId The id of the user whose key pair to update.
     * @return true on success, false otherwise.
     */
    public abstract boolean setUserKeyPairCertificate(
            @NonNull String alias,
            @NonNull byte[] clientCertificate,
            @NonNull byte[] clientCertificateChain,
            int userId);

    /**
     * Sets the certificate chain for an existing device-wide key pair.
     *
     * @param alias The alias of the existing key pair.
     * @param clientCertificate DER-encoded user/leaf certificate bytes.
     * @param clientCertificateChain Array of DER-encoded intermediate/CA certificates bytes.
     * @return true on success, false otherwise.
     */
    public abstract boolean setDeviceKeyPairCertificate(
            @NonNull String alias,
            @NonNull byte[] clientCertificate,
            @NonNull byte[] clientCertificateChain);

    /**
     * Set a grant for the specified UID for an existing user-scoped key pair.
     *
     * @param uid The UID of the grantee.
     * @param alias The alias of the existing user-scoped key pair.
     * @param userId The ID of the user whose key pair access is being managed.
     * @param granted {@code true} to grant access, {@code false} to revoke.
     * @return {@code true} on success, {@code false} otherwise.
     */
    public abstract boolean setUserGrant(
            int uid, @NonNull String alias, int userId, boolean granted);

    /**
     * Set a grant for the specified UID for an existing device-wide key pair.
     *
     * @param uid The UID of the grantee.
     * @param alias The alias of the existing device-wide key pair.
     * @param granted {@code true} to grant access, {@code false} to revoke.
     * @return {@code true} on success, {@code false} otherwise.
     */
    public abstract boolean setDeviceGrant(int uid, @NonNull String alias, boolean granted);
}
