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

package com.android.server.security.trusttoken;

import android.annotation.NonNull;
import android.security.trusttoken.TrustConfiguration;
import android.security.trusttoken.TrustTokenUnavailableException;

import java.util.List;
import java.util.function.Predicate;

/** The database for pending and existing trust tokens. */
abstract class TrustTokenDatabase {
    /**
     * Gets a new {@link TrustTokenSet} of the specified type from the database.
     *
     * @throws TrustTokenUnavailableException if the database contains no more valid trust tokens of
     *     the specified type.
     */
    @NonNull
    abstract TrustTokenSetWithKey getTrustTokenSet(@TrustTokenSet.Type int type)
            throws TrustTokenUnavailableException;

    /**
     * Adds a batch of {@link TrustTokenSet} to the database. The keys associated with the trust
     * tokens must be unique in the database.
     *
     * @throws IllegalArgumentException if the keys associated with the trust tokens do already
     *     exist in the database.
     */
    abstract void addTrustTokenSets(@NonNull List<TrustTokenSetWithKey> tokens)
            throws IllegalArgumentException;

    /**
     * Counts the number of trust token sets of a specific type in the database.
     *
     * @param type the type of the trust token sets to count.
     * @return the number of trust token sets of the specified type.
     */
    abstract int countTrustTokenSets(@TrustTokenSet.Type int type);

    /**
     * Cleans up the stored tokens. It removes expired, invalid and excess tokens from the database.
     *
     * @param type the token type to clean up.
     * @param maxTokenNum the maximum number of tokens to keep.
     * @param verifier the token verifier. It should returns true if the token is valid.
     */
    abstract int cleanUpTrustTokenSets(
            @TrustTokenSet.Type int type, int maxTokenNum, Predicate<TrustTokenSet> verifier);

    /**
     * Gets the TrustConfiguration from the database.
     *
     * @throws TrustConfigurationUnavailableException if the configuration is not available.
     */
    @NonNull
    abstract TrustConfiguration getTrustConfiguration()
            throws TrustConfigurationUnavailableException;

    /** Updates the TrustConfiguration in the database. */
    abstract void updateTrustConfiguration(@NonNull TrustConfiguration configuration);
}
