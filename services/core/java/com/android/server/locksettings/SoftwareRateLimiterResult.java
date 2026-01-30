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

package com.android.server.locksettings;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;

/** The result from the {@link SoftwareRateLimiter} */
class SoftwareRateLimiterResult {
    public static final int CREDENTIAL_TOO_SHORT = 0;
    public static final int NO_MORE_GUESSES = 1;
    public static final int RATE_LIMITED = 2;
    public static final int DUPLICATE_WRONG_GUESS = 3;
    public static final int CONTINUE_TO_HARDWARE = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                CREDENTIAL_TOO_SHORT,
                NO_MORE_GUESSES,
                RATE_LIMITED,
                DUPLICATE_WRONG_GUESS,
                CONTINUE_TO_HARDWARE,
            })
    public @interface Code {}

    public final @Code int code;

    /**
     * For a {@link #RATE_LIMITED} result, this is the time remaining until the next guess will be
     * allowed. Otherwise, this is null.
     *
     * <p>Rationale: {@link #DUPLICATE_WRONG_GUESS} and {@link #CONTINUE_TO_HARDWARE} are only
     * reported if the rate-limit check has passed, which implies no timeout. On the other hand, any
     * preliminary validation done before <em>before</em> the rate-limit check does not have access
     * to the timeout yet, so none is reported for {@link #CREDENTIAL_TOO_SHORT} either.
     */
    @Nullable public final Duration timeout;

    // Pre-allocate a CONTINUE_TO_HARDWARE result since it is the most common case.
    private static final SoftwareRateLimiterResult CONTINUE_TO_HARDWARE_RESULT =
            new SoftwareRateLimiterResult(CONTINUE_TO_HARDWARE, null);

    private SoftwareRateLimiterResult(@Code int resultCode, Duration timeout) {
        this.code = resultCode;
        this.timeout = timeout;
    }

    static SoftwareRateLimiterResult credentialTooShort() {
        return new SoftwareRateLimiterResult(CREDENTIAL_TOO_SHORT, null);
    }

    static SoftwareRateLimiterResult noMoreGuesses() {
        return new SoftwareRateLimiterResult(NO_MORE_GUESSES, null);
    }

    static SoftwareRateLimiterResult rateLimited(@NonNull Duration timeout) {
        return new SoftwareRateLimiterResult(RATE_LIMITED, timeout);
    }

    static SoftwareRateLimiterResult duplicateWrongGuess() {
        return new SoftwareRateLimiterResult(DUPLICATE_WRONG_GUESS, null);
    }

    static SoftwareRateLimiterResult continueToHardware() {
        return CONTINUE_TO_HARDWARE_RESULT;
    }
}
