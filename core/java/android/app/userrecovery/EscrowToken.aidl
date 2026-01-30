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

package android.app.userrecovery;

/**
 * Contains the escrow token data and metadata for a user's recovery factor.
 * This data is typically provided by an external recovery agent or service.
 * @hide
 */
parcelable EscrowToken {
    /** Version of this token structure. */
    int version;

    /**
     * Identifier for the backend or service key used for wrapping.
     * Similar to RecoverableKeyStoreParameters.backend_public_key in CrOS.
     */
    byte[] backendPublicKeyId;

    /**
     * The core encrypted recovery data. This might be analogous to
     * RecoverableKeyStore.wrapped_recovery_key, potentially
     * including the user's knowledge factor hash.
     */
    byte[] wrappedRecoveryData;

    // Metadata fields, similar to RecoverableKeyStoreMetadata in CrOS:

    /**
     * Type of knowledge factor (e.g., PIN, PASSWORD).
     * We should define an int enum for this in UserRecoveryManager.java.
     */
    int knowledgeFactorType;

    /** Algorithm used to hash the knowledge factor. Int enum. */
    int hashAlgorithm;

    /** Salt used in the knowledge factor hashing. */
    byte[] hashSalt;

    /**
     * Application-specific metadata for the recovery agent.
     */
    byte[] applicationMetadata;

    /**
     * Identifier for a hardware counter to limit attempts (if applicable).
     * Similar to RecoverableKeyStoreParameters.counter_id.
     */
    byte[] counterId;

    /** Maximum allowed failed attempts. */
    int maxAttempts;
}
