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

package com.android.server.security.trusttoken;

import static com.android.internal.util.FrameworkStatsLog.ACQUIRE_TRUST_TOKEN_CALLED;
import static com.android.internal.util.FrameworkStatsLog.ACQUIRE_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_ANCHOR_UNAVAILABLE;
import static com.android.internal.util.FrameworkStatsLog.ACQUIRE_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_SUCCESS;
import static com.android.internal.util.FrameworkStatsLog.ACQUIRE_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_TOKEN_EXHAUSTED;
import static com.android.internal.util.FrameworkStatsLog.ACQUIRE_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_TOKEN_INVALID;
import static com.android.internal.util.FrameworkStatsLog.ACQUIRE_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_UNKNOWN;
import static com.android.internal.util.FrameworkStatsLog.ACQUIRE_TRUST_TOKEN_CALLED__TOKEN_TYPE__TRUST_TOKEN_TYPE_DEVICE;
import static com.android.internal.util.FrameworkStatsLog.ACQUIRE_TRUST_TOKEN_CALLED__TOKEN_TYPE__TRUST_TOKEN_TYPE_IDENTITIES;
import static com.android.internal.util.FrameworkStatsLog.ACQUIRE_TRUST_TOKEN_CALLED__TOKEN_TYPE__TRUST_TOKEN_TYPE_UNKNOWN;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_FINISHED;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_FINISHED__OUTCOME__OUTCOME_PROVIDER_UNAVAILABLE;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_FINISHED__OUTCOME__OUTCOME_SERVER_ERROR;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_FINISHED__OUTCOME__OUTCOME_SERVICE_ERROR;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_FINISHED__OUTCOME__OUTCOME_SUCCESS;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_FINISHED__OUTCOME__OUTCOME_UNKNOWN;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_FINISHED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_REGULAR;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_FINISHED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_UNKNOWN;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_FINISHED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_URGENT;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_STARTED;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_STARTED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_REGULAR;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_STARTED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_UNKNOWN;
import static com.android.internal.util.FrameworkStatsLog.REFRESH_TRUST_TOKEN_STARTED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_URGENT;
import static com.android.internal.util.FrameworkStatsLog.TRUST_TOKEN_STATE;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_ANCHOR_UNAVAILABLE;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_CHALLENGE_INCORRECT;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_FAILURE_UNKNOWN;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_INVALID_POLICY;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_SIGNATURE_INVALID;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_SUCCESS;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_UNKNOWN;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__POLICY__POLICY_UNKNOWN;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__POLICY__POLICY_VERIFIED_DEVICE;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__POLICY__POLICY_VERIFIED_DEVICE_STRONG;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__POLICY__POLICY_VERIFIED_IDENTITY;
import static com.android.internal.util.FrameworkStatsLog.VERIFY_TRUST_TOKEN_CALLED__POLICY__POLICY_VERIFIED_IDENTITY_STRONG;

import static com.google.android.security.trusttoken.TrustTokenPolicy.VERIFIED_DEVICE;
import static com.google.android.security.trusttoken.TrustTokenPolicy.VERIFIED_DEVICE_STRONG;
import static com.google.android.security.trusttoken.TrustTokenPolicy.VERIFIED_IDENTITY;
import static com.google.android.security.trusttoken.TrustTokenPolicy.VERIFIED_IDENTITY_STRONG;

import android.annotation.IntDef;
import android.util.StatsEvent;

import com.android.internal.util.FrameworkStatsLog;

import com.google.android.security.trusttoken.TrustTokenPolicy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

final class MetricsLogger {

    /** Logs when acquiring trust tokens. Corresponds to the AcquireTrustTokenCalled atom. */
    static final class AcquireTokenCalled {
        @IntDef(
                prefix = {"TYPE_"},
                value = {TYPE_UNKNOWN, TYPE_DEVICE, TYPE_IDENTITIES})
        @Retention(RetentionPolicy.SOURCE)
        @interface TrustTokenType {}

        private static final int TYPE_UNKNOWN =
                ACQUIRE_TRUST_TOKEN_CALLED__TOKEN_TYPE__TRUST_TOKEN_TYPE_UNKNOWN;
        static final int TYPE_DEVICE =
                ACQUIRE_TRUST_TOKEN_CALLED__TOKEN_TYPE__TRUST_TOKEN_TYPE_DEVICE;
        static final int TYPE_IDENTITIES =
                ACQUIRE_TRUST_TOKEN_CALLED__TOKEN_TYPE__TRUST_TOKEN_TYPE_IDENTITIES;

        @IntDef(
                prefix = {"OUTCOME_"},
                value = {
                    OUTCOME_UNKNOWN,
                    OUTCOME_SUCCESS,
                    OUTCOME_ANCHOR_UNAVAILABLE,
                    OUTCOME_TOKEN_EXHAUSTED,
                    OUTCOME_TOKEN_INVALID
                })
        @Retention(RetentionPolicy.SOURCE)
        @interface AcquireOutcome {}

        private static final int OUTCOME_UNKNOWN =
                ACQUIRE_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_UNKNOWN;
        static final int OUTCOME_SUCCESS = ACQUIRE_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_SUCCESS;
        static final int OUTCOME_ANCHOR_UNAVAILABLE =
                ACQUIRE_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_ANCHOR_UNAVAILABLE;
        static final int OUTCOME_TOKEN_EXHAUSTED =
                ACQUIRE_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_TOKEN_EXHAUSTED;
        static final int OUTCOME_TOKEN_INVALID =
                ACQUIRE_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_TOKEN_INVALID;

        private @TrustTokenType int mTokenType =
                ACQUIRE_TRUST_TOKEN_CALLED__TOKEN_TYPE__TRUST_TOKEN_TYPE_UNKNOWN;
        private @AcquireOutcome int mOutcome = ACQUIRE_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_UNKNOWN;

        AcquireTokenCalled setTokenType(@TrustTokenType int tokenType) {
            mTokenType = tokenType;
            return this;
        }

        AcquireTokenCalled setOutcome(@AcquireOutcome int outcome) {
            mOutcome = outcome;
            return this;
        }

        void log() {
            FrameworkStatsLog.write(ACQUIRE_TRUST_TOKEN_CALLED, mTokenType, mOutcome);
        }
    }

    /**
     * Logs when verifying tokens with TrustTokenManager. Corresponds to the VerifyTokenCalled atom.
     */
    static final class VerifyTokenCalled {
        @IntDef(
                prefix = {"OUTCOME_"},
                value = {
                    OUTCOME_UNKNOWN,
                    OUTCOME_SUCCESS,
                    OUTCOME_ANCHOR_UNAVAILABLE,
                    OUTCOME_SIGNATURE_INVALID,
                    OUTCOME_CHALLENGE_INCORRECT,
                    OUTCOME_INVALID_POLICY,
                    OUTCOME_FAILURE_UNKNOWN
                })
        @Retention(RetentionPolicy.SOURCE)
        @interface Outcome {}

        private static final int OUTCOME_UNKNOWN =
                VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_UNKNOWN;
        static final int OUTCOME_SUCCESS = VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_SUCCESS;
        static final int OUTCOME_ANCHOR_UNAVAILABLE =
                VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_ANCHOR_UNAVAILABLE;
        static final int OUTCOME_SIGNATURE_INVALID =
                VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_SIGNATURE_INVALID;
        static final int OUTCOME_CHALLENGE_INCORRECT =
                VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_CHALLENGE_INCORRECT;
        static final int OUTCOME_INVALID_POLICY =
                VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_INVALID_POLICY;
        static final int OUTCOME_FAILURE_UNKNOWN =
                VERIFY_TRUST_TOKEN_CALLED__OUTCOME__OUTCOME_FAILURE_UNKNOWN;

        @IntDef(
                prefix = {"POLICY_"},
                value = {
                    POLICY_UNKNOWN,
                    POLICY_VERIFIED_IDENTITY,
                    POLICY_VERIFIED_IDENTITY_STRONG,
                    POLICY_VERIFIED_DEVICE,
                    POLICY_VERIFIED_DEVICE_STRONG
                })
        @Retention(RetentionPolicy.SOURCE)
        @interface Policy {}

        private static final int POLICY_UNKNOWN = VERIFY_TRUST_TOKEN_CALLED__POLICY__POLICY_UNKNOWN;
        static final int POLICY_VERIFIED_IDENTITY =
                VERIFY_TRUST_TOKEN_CALLED__POLICY__POLICY_VERIFIED_IDENTITY;
        static final int POLICY_VERIFIED_IDENTITY_STRONG =
                VERIFY_TRUST_TOKEN_CALLED__POLICY__POLICY_VERIFIED_IDENTITY_STRONG;
        static final int POLICY_VERIFIED_DEVICE =
                VERIFY_TRUST_TOKEN_CALLED__POLICY__POLICY_VERIFIED_DEVICE;
        static final int POLICY_VERIFIED_DEVICE_STRONG =
                VERIFY_TRUST_TOKEN_CALLED__POLICY__POLICY_VERIFIED_DEVICE_STRONG;

        private @Outcome int mOutcome = OUTCOME_UNKNOWN;
        private int mPolicy = POLICY_UNKNOWN;

        VerifyTokenCalled setOutcome(@Outcome int outcome) {
            mOutcome = outcome;
            return this;
        }

        VerifyTokenCalled setPolicy(TrustTokenPolicy policy) {
            switch (policy) {
                case VERIFIED_IDENTITY:
                    mPolicy = POLICY_VERIFIED_IDENTITY;
                    break;
                case VERIFIED_IDENTITY_STRONG:
                    mPolicy = POLICY_VERIFIED_IDENTITY_STRONG;
                    break;
                case VERIFIED_DEVICE:
                    mPolicy = POLICY_VERIFIED_DEVICE;
                    break;
                case VERIFIED_DEVICE_STRONG:
                    mPolicy = POLICY_VERIFIED_DEVICE_STRONG;
                    break;
                default:
                    throw new AssertionError("Unknown policy: " + policy);
            }
            return this;
        }

        void log() {
            FrameworkStatsLog.write(VERIFY_TRUST_TOKEN_CALLED, mOutcome, mPolicy);
        }
    }

    /** Logs when refreshing trust tokens starts. Corresponds to the RefreshTokenStarted atom. */
    static final class RefreshTokenStarted {
        @IntDef(
                prefix = {"REFRESH_TYPE_"},
                value = {REFRESH_TYPE_UNKNOWN, REFRESH_TYPE_REGULAR, REFRESH_TYPE_URGENT})
        @Retention(RetentionPolicy.SOURCE)
        @interface RefreshStartedRefreshType {}

        private static final int REFRESH_TYPE_UNKNOWN =
                REFRESH_TRUST_TOKEN_STARTED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_UNKNOWN;
        static final int REFRESH_TYPE_REGULAR =
                REFRESH_TRUST_TOKEN_STARTED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_REGULAR;
        static final int REFRESH_TYPE_URGENT =
                REFRESH_TRUST_TOKEN_STARTED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_URGENT;

        private @RefreshStartedRefreshType int mRefreshType = REFRESH_TYPE_UNKNOWN;

        RefreshTokenStarted setRefreshType(@RefreshStartedRefreshType int refreshType) {
            mRefreshType = refreshType;
            return this;
        }

        void log() {
            FrameworkStatsLog.write(REFRESH_TRUST_TOKEN_STARTED, mRefreshType);
        }
    }

    /** Logs when refreshing trust tokens finishes. Corresponds to the RefreshTokenFinished atom. */
    static final class RefreshTokenFinished {
        @IntDef(
                prefix = {"REFRESH_TYPE_"},
                value = {REFRESH_TYPE_UNKNOWN, REFRESH_TYPE_REGULAR, REFRESH_TYPE_URGENT})
        @Retention(RetentionPolicy.SOURCE)
        @interface RefreshFinishedRefreshType {}

        private static final int REFRESH_TYPE_UNKNOWN =
                REFRESH_TRUST_TOKEN_FINISHED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_UNKNOWN;
        static final int REFRESH_TYPE_REGULAR =
                REFRESH_TRUST_TOKEN_FINISHED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_REGULAR;
        static final int REFRESH_TYPE_URGENT =
                REFRESH_TRUST_TOKEN_FINISHED__REFRESH_TYPE__TRUST_TOKEN_REFRESH_TYPE_URGENT;

        @IntDef(
                prefix = {"OUTCOME_"},
                value = {
                    OUTCOME_UNKNOWN,
                    OUTCOME_SUCCESS,
                    OUTCOME_PROVIDER_UNAVAILABLE,
                    OUTCOME_SERVICE_ERROR,
                    OUTCOME_SERVER_ERROR
                })
        @Retention(RetentionPolicy.SOURCE)
        @interface RefreshFinishedOutcome {}

        private static final int OUTCOME_UNKNOWN =
                REFRESH_TRUST_TOKEN_FINISHED__OUTCOME__OUTCOME_UNKNOWN;
        static final int OUTCOME_SUCCESS = REFRESH_TRUST_TOKEN_FINISHED__OUTCOME__OUTCOME_SUCCESS;
        static final int OUTCOME_PROVIDER_UNAVAILABLE =
                REFRESH_TRUST_TOKEN_FINISHED__OUTCOME__OUTCOME_PROVIDER_UNAVAILABLE;
        static final int OUTCOME_SERVICE_ERROR =
                REFRESH_TRUST_TOKEN_FINISHED__OUTCOME__OUTCOME_SERVICE_ERROR;
        static final int OUTCOME_SERVER_ERROR =
                REFRESH_TRUST_TOKEN_FINISHED__OUTCOME__OUTCOME_SERVER_ERROR;

        private @RefreshFinishedRefreshType int mRefreshType = REFRESH_TYPE_UNKNOWN;
        private @RefreshFinishedOutcome int mOutcome = OUTCOME_UNKNOWN;
        private int mServerErrorCode = 0;

        RefreshTokenFinished setRefreshType(@RefreshFinishedRefreshType int refreshType) {
            mRefreshType = refreshType;
            return this;
        }

        RefreshTokenFinished setOutcome(@RefreshFinishedOutcome int outcome) {
            mOutcome = outcome;
            return this;
        }

        RefreshTokenFinished setServerErrorCode(int serverErrorCode) {
            mServerErrorCode = serverErrorCode;
            return this;
        }

        void log() {
            FrameworkStatsLog.write(
                    REFRESH_TRUST_TOKEN_FINISHED, mRefreshType, mOutcome, mServerErrorCode);
        }
    }

    /**
     * Logs the internal state in TrustTokenManagerService. Corresponds to the TrustTokenState atom.
     */
    static final class TrustTokenState {
        static final int ATOM_TAG = TRUST_TOKEN_STATE;
        private boolean mHasProvider = false;
        private boolean mHasTrustAnchor = false;
        private boolean mAuthorityFallback = false;
        private int mNumRootKey = 0;
        private int mDeviceTokenCounts = 0;

        TrustTokenState setHasProvider(boolean hasProvider) {
            mHasProvider = hasProvider;
            return this;
        }

        TrustTokenState setHasTrustAnchor(boolean hasTrustAnchor) {
            mHasTrustAnchor = hasTrustAnchor;
            return this;
        }

        TrustTokenState setAuthorityFallback(boolean authorityFallback) {
            mAuthorityFallback = authorityFallback;
            return this;
        }

        TrustTokenState setNumRootKey(int numRootKey) {
            mNumRootKey = numRootKey;
            return this;
        }

        TrustTokenState setDeviceTokenCounts(int deviceTokenCounts) {
            mDeviceTokenCounts = deviceTokenCounts;
            return this;
        }

        StatsEvent build() {
            return FrameworkStatsLog.buildStatsEvent(
                    TRUST_TOKEN_STATE,
                    mHasProvider,
                    mHasTrustAnchor,
                    mAuthorityFallback,
                    mNumRootKey,
                    mDeviceTokenCounts);
        }
    }
}
