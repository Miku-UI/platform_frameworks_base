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

import android.annotation.UserIdInt;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.server.utils.Slogf;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/**
 * This is the software rate-limiter used by LockSettingsService. It rate-limits guesses of LSKFs
 * (Lock Screen Knowledge Factors), complementing the "hardware" (TEE or Secure Element)
 * rate-limiter provided by the Gatekeeper or Weaver HAL.
 *
 * <p>This has several purposes:
 *
 * <ol>
 *   <li>Keep track of recent wrong guesses, and reject duplicate wrong guesses before they reach
 *       the hardware rate-limiter and actually count as wrong guesses. This is helpful for
 *       legitimate users who may mis-enter their LSKF in the same way multiple times. It does not
 *       help capable attackers, for whom duplicate wrong guesses provide no additional information.
 *       Overall, this logic makes it feasible to more strictly rate-limit (unique) wrong guesses,
 *       which increases security. It also eliminates the need for all hardware rate-limiter
 *       implementations to implement the same duplicate wrong detection logic.
 *   <li>Enable faster validation and deployment of rate-limiter improvements, before mirroring
 *       those same changes in the hardware rate-limiters which tends to take longer.
 *   <li>Serve as a fallback just in case the hardware rate-limiter (which is a vendor component)
 *       does not work properly. The software and hardware rate-limiters operate concurrently, so
 *       the stricter of the two is what is normally observed. Of course, a properly implemented
 *       hardware rate-limiter is more secure and is always supposed to be present too.
 *   <li>Reject guesses of too-short LSKFs before they count as real guesses.
 *   <li>Log LSKF authentication attempts to <code>statsd</code>.
 * </ol>
 */
class SoftwareRateLimiter {

    private static final String TAG = "SoftwareRateLimiter";

    /**
     * The maximum number of unique wrong guesses saved per LSKF.
     *
     * <p>5 should be more than enough, considering that the chance of matching on the n-th last
     * unique wrong guess should (in general) diminish as n increases, and the rate-limiter kicks in
     * after the first 5 unique wrong guesses anyway.
     */
    @VisibleForTesting static final int MAX_SAVED_WRONG_GUESSES = 5;

    /**
     * The duration between an LSKF's most recent wrong guess to when that LSKF's saved wrong
     * guesses are forgotten.
     *
     * <p>5 minutes provides a reasonable balance between user convenience and minimizing the small
     * security risk of wrong guesses being kept around in system_server memory. (Wrong guesses can
     * be somewhat sensitive information, since they may be similar to the correct LSKF or they may
     * be the correct LSKF for another device or user.)
     */
    @VisibleForTesting static final Duration SAVED_WRONG_GUESS_TIMEOUT = Duration.ofMinutes(5);

    /**
     * A table that maps the number of (real) failures to the timeout that is enforced after that
     * number of (real) failures. Out-of-bounds indices default to not allowed.
     */
    private static final Duration[] TIMEOUT_TABLE =
            new Duration[] {
                /* 0 */ Duration.ZERO,
                /* 1 */ Duration.ZERO,
                /* 2 */ Duration.ZERO,
                /* 3 */ Duration.ZERO,
                /* 4 */ Duration.ZERO,
                /* 5 */ Duration.ofMinutes(1),
                /* 6 */ Duration.ofMinutes(5),
                /* 7 */ Duration.ofMinutes(15),
                /* 8 */ Duration.ofMinutes(30),
                /* 9 */ Duration.ofMinutes(90),
                /* 10 */ Duration.ofHours(4),
                /* 11 */ Duration.ofHours(12),
                /* 12 */ Duration.ofHours(36),
                /* 13 */ Duration.ofDays(4),
                /* 14 */ Duration.ofDays(13),
                /* 15 */ Duration.ofDays(41),
                /* 16 */ Duration.ofDays(123),
                /* 17 */ Duration.ofDays(365),
                /* 18 */ Duration.ofDays(365 * 3),
                /* 19 */ Duration.ofDays(365 * 9),
            };

    private final Injector mInjector;

    /**
     * Whether the software rate-limiter is actually in enforcing mode. In non-enforcing mode all
     * timeouts are considered to be zero, and "duplicate wrong guess" results are not returned.
     */
    private final boolean mEnforcing;

    /** The software rate-limiter state for each LSKF */
    @GuardedBy("this")
    private final ArrayMap<LskfIdentifier, RateLimiterState> mState = new ArrayMap<>();

    /** The software rate-limiter state for a particular LSKF */
    private static class RateLimiterState {

        /**
         * The number of failed attempts since the last successful attempt, not counting attempts
         * that never reached the real credential check for a reason such as detection of a
         * duplicate wrong guess, credential too short, timeout still remaining, etc.
         */
        public int numFailures;

        /**
         * The number of the duplicate wrong guesses that have been detected since the last success
         * or reboot. Note that the ability to detect duplicate wrong guesses may vary from device
         * to device depending on whether the error codes returned by the hardware rate-limiter
         * clearly differentiate between wrong guesses and other errors.
         */
        public int numDuplicateWrongGuesses;

        /**
         * The type of the LSKF, as a value of the stats CredentialType enum. Updates after the
         * first guess.
         */
        public int statsCredentialType =
                FrameworkStatsLog.LSKF_AUTHENTICATION_ATTEMPTED__CREDENTIAL_TYPE__UNKNOWN_TYPE;

        /**
         * The time since boot at which the failure counter was last incremented, or zero if the
         * failure counter was last incremented before the current boot.
         */
        public Duration timeSinceBootOfLastFailure = Duration.ZERO;

        /**
         * The time since boot at which the lockout ends and another guess can be made, or {@link
         * Duration#ZERO} if there is not currently a lockout. Lockouts can be imposed by software
         * or externally, e.g. Weaver.
         */
        public Duration lockoutEndTime = Duration.ZERO;

        /**
         * The list of wrong guesses that were recently tried already in the current boot, ordered
         * from newest to oldest. The used portion is followed by nulls in any unused space.
         */
        public final LockscreenCredential[] savedWrongGuesses =
                new LockscreenCredential[MAX_SAVED_WRONG_GUESSES];

        RateLimiterState(int numFailures) {
            this.numFailures = numFailures;
        }
    }

    SoftwareRateLimiter(Injector injector) {
        this(injector, /* enforcing= */ true);
    }

    SoftwareRateLimiter(Injector injector, boolean enforcing) {
        mInjector = injector;
        mEnforcing = enforcing;
        if (android.security.Flags.manageLockoutEndTimeInService()) {
            // The cache doesn't work until it's initialized.
            mInjector.invalidateLockoutEndTimeCache();
        }
    }

    /**
     * Applies the software rate-limiter to the given LSKF guess.
     *
     * @param id the ID of the protector or special credential
     * @param guess the LSKF being checked
     * @return a {@link SoftwareRateLimiterResult}
     */
    synchronized SoftwareRateLimiterResult apply(LskfIdentifier id, LockscreenCredential guess) {

        // Check for too-short credential that cannot possibly be correct.
        // There's no need to waste any real guesses for such credentials.
        // Should be handled by the UI already, but check here too just in case.
        final int minLength = switch (guess.getType()) {
            case LockPatternUtils.CREDENTIAL_TYPE_PATTERN ->
                    LockPatternUtils.MIN_LOCK_PATTERN_SIZE;
            case LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PIN ->
                    LockPatternUtils.MIN_LOCK_PASSWORD_SIZE;
            default -> 0;
        };
        if (guess.size() < minLength) {
            Slogf.e(TAG, "Credential is too short; size=%d", guess.size());
            return SoftwareRateLimiterResult.credentialTooShort();
        }

        final Duration now = mInjector.getTimeSinceBoot();
        final RateLimiterState state = getOrComputeState(id, now);
        if (state.statsCredentialType
                == FrameworkStatsLog.LSKF_AUTHENTICATION_ATTEMPTED__CREDENTIAL_TYPE__UNKNOWN_TYPE) {
            state.statsCredentialType = getStatsCredentialType(guess);
        }

        // Check for remaining timeout. Note that the case of a positive remaining timeout normally
        // won't be reached, since reportFailure() will have returned the timeout when the last
        // guess was made, causing the lock screen to block inputs for that amount of time. But
        // checking for it is still needed to cover any cases where a guess gets made anyway, for
        // example following a reboot which causes the lock screen to "forget" the timeout.
        if (mEnforcing && (state.numFailures >= TIMEOUT_TABLE.length || state.numFailures < 0)) {
            Slogf.e(TAG, "No more guesses allowed; numFailures=%d", state.numFailures);
            return SoftwareRateLimiterResult.noMoreGuesses();
        }
        final Duration timeout = computeRemainingTimeout(state, now);
        if (timeout.isPositive()) {
            Slogf.e(TAG, "Rate-limited; numFailures=%d, timeout=%s", state.numFailures, timeout);
            return SoftwareRateLimiterResult.rateLimited(timeout);
        }

        // Check for duplicate wrong guess.
        for (int i = 0; i < MAX_SAVED_WRONG_GUESSES; i++) {
            LockscreenCredential wrongGuess = state.savedWrongGuesses[i];
            if (wrongGuess != null && wrongGuess.equals(guess)) {
                Slog.i(TAG, "Duplicate wrong guess");
                // The guess is now the most recent wrong guess, so move it to the front of the
                // list.
                for (int j = i; j >= 1; j--) {
                    state.savedWrongGuesses[j] = state.savedWrongGuesses[j - 1];
                }
                state.savedWrongGuesses[0] = wrongGuess;
                state.numDuplicateWrongGuesses++;
                writeStats(id, state, /* success= */ false);
                return mEnforcing
                        ? SoftwareRateLimiterResult.duplicateWrongGuess()
                        : SoftwareRateLimiterResult.continueToHardware();
            }
        }

        // Ready to make a real guess. Continue on to the real credential check.
        return SoftwareRateLimiterResult.continueToHardware();
    }

    @GuardedBy("this")
    private RateLimiterState getOrComputeState(LskfIdentifier id, Duration now) {
        return mState.computeIfAbsent(
                id,
                key -> {
                    // The state isn't cached yet. Create it.
                    //
                    // For LSKF-based synthetic password protectors the only persistent
                    // software rate-limiter state is the failure counter.
                    // timeSinceBootOfLastFailure is just set to zero, so effectively the
                    // timeout resets to its original value (for the current failure count)
                    // upon reboot. That matches what typical hardware rate-limiter
                    // implementations do; they typically do not have access to a trusted
                    // real-time clock that runs without the device being powered on.
                    //
                    // Likewise, rebooting causes any saved wrong guesses to be forgotten.
                    RateLimiterState state = new RateLimiterState(readFailureCounter(id));
                    evaluateSoftwareRateLimit(state, now);
                    return state;
                });
    }

    @GuardedBy("this")
    private Duration computeRemainingTimeout(RateLimiterState state, Duration now) {
        final Duration remainingTimeout = state.lockoutEndTime.minus(now);
        return remainingTimeout.isPositive() ? remainingTimeout : Duration.ZERO;
    }

    /**
     * Computes the software enforced lockout and updates the stored lockout end time. Invalidates
     * the lockout end time cache as needed.
     */
    @GuardedBy("this")
    private void evaluateSoftwareRateLimit(RateLimiterState state, Duration now) {
        final Duration originalTimeout = getOriginalTimeout(state.numFailures);
        final Duration softwareLockoutEndTime =
                state.timeSinceBootOfLastFailure.plus(originalTimeout);
        updateLockoutEndTime(state, now, softwareLockoutEndTime);
    }

    /**
     * Returns the tracked lockout end time for the given LSKF. If no attempts have been made for
     * this LSKF, no timeouts have happened across attempts, or the existing lockout end time is in
     * the past, returns {@link Duration#ZERO}.
     */
    synchronized Duration getLockoutEndTime(LskfIdentifier id) {
        Duration now = mInjector.getTimeSinceBoot();
        RateLimiterState state = getOrComputeState(id, now);
        Duration lockoutEndTime = state.lockoutEndTime;
        // TODO: b/322014085 - Get rid of this clear once LSS clients are not depending on
        //  "lockoutEndTime == 0 iff more LSKF guesses can be made" semantics.
        if (!lockoutEndTime.isZero() && lockoutEndTime.compareTo(now) < 0) {
            clearLockoutEndTime(state);
            return Duration.ZERO;
        }
        return lockoutEndTime;
    }

    /**
     * Updates state.lockoutEndTime to be the later of lockoutEndTime and state.lockoutEndTime, or
     * zero if that time has already been reached. If it changed, invalidate the cache.
     */
    @GuardedBy("this")
    private void updateLockoutEndTime(
            RateLimiterState state, Duration now, Duration lockoutEndTime) {
        if (state.lockoutEndTime.compareTo(lockoutEndTime) > 0) {
            lockoutEndTime = state.lockoutEndTime; // state.lockoutEndTime is later
        }
        if (now.compareTo(lockoutEndTime) >= 0) {
            lockoutEndTime = Duration.ZERO; // end time has already been reached
        }
        if (!lockoutEndTime.equals(state.lockoutEndTime)) {
            state.lockoutEndTime = lockoutEndTime;
            if (android.security.Flags.manageLockoutEndTimeInService()) {
                mInjector.invalidateLockoutEndTimeCache();
            }
        }
    }

    @GuardedBy("this")
    private void clearLockoutEndTime(RateLimiterState state) {
        if (!state.lockoutEndTime.isZero()) {
            state.lockoutEndTime = Duration.ZERO;
            if (android.security.Flags.manageLockoutEndTimeInService()) {
                mInjector.invalidateLockoutEndTimeCache();
            }
        }
    }

    /**
     * Reports a successful guess to the software rate-limiter. This causes the failure counter and
     * saved wrong guesses to be cleared.
     */
    synchronized void reportSuccess(LskfIdentifier id) {
        RateLimiterState state = getExistingState(id);
        writeStats(id, state, /* success= */ true);
        // If the failure counter is still 0, then there is no need to write it. Nor can there be
        // any saved wrong guesses, so there is no need to forget them. This optimizes for the
        // common case where the first guess is correct.
        if (state.numFailures != 0) {
            state.numFailures = 0;
            state.numDuplicateWrongGuesses = 0;
            writeFailureCounter(id, state);
            forgetSavedWrongGuesses(state);
            clearLockoutEndTime(state);
        }
    }

    // Inserts a new wrong guess into the given list of saved wrong guesses.
    private void insertNewWrongGuess(RateLimiterState state, LockscreenCredential newWrongGuess) {
        // Shift the saved wrong guesses over by one to make room for the new one. If the list is
        // full, zeroize and evict the oldest entry.
        if (state.savedWrongGuesses[MAX_SAVED_WRONG_GUESSES - 1] != null) {
            state.savedWrongGuesses[MAX_SAVED_WRONG_GUESSES - 1].zeroize();
        }
        for (int i = MAX_SAVED_WRONG_GUESSES - 1; i >= 1; i--) {
            state.savedWrongGuesses[i] = state.savedWrongGuesses[i - 1];
        }

        // Store the new wrong guess. Duplicate it, since it may be held onto for some time. This
        // class is responsible for zeroizing the duplicated credential once its lifetime expires.
        state.savedWrongGuesses[0] = newWrongGuess.duplicate();
    }

    /**
     * Reports a failure to the software rate-limiter.
     *
     * <p>This must be called immediately after the hardware rate-limiter reported a failure, before
     * the credential check failure is made visible in the UI. It is assumed that {@link
     * #apply(LskfIdentifier, LockscreenCredential)} was previously called with the same parameters
     * and returned a {@code CONTINUE_TO_HARDWARE} result.
     *
     * @param id the ID of the protector or special credential
     * @param guess the LSKF that was attempted
     * @param isCertainlyWrongGuess true if it's certain that the failure was caused by the guess
     *     being wrong, as opposed to e.g. a transient hardware glitch
     * @param hwTimeout an externally-imposed timeout from the current time since boot
     * @return the remaining timeout until when the next guess will be allowed
     */
    synchronized Duration reportFailure(
            LskfIdentifier id,
            LockscreenCredential guess,
            boolean isCertainlyWrongGuess,
            Duration hwTimeout) {
        RateLimiterState state = getExistingState(id);

        Duration now = mInjector.getTimeSinceBoot();
        if (android.security.Flags.manageLockoutEndTimeInService() && hwTimeout.isPositive()) {
            // Always track any hardware timeouts, including when not enforcing.
            updateLockoutEndTime(state, now, now.plus(hwTimeout));
        }

        // In non-enforcing mode, ignore duplicate wrong guesses here since they were already
        // counted by apply(), including having stats written for them. In enforcing mode, this
        // method isn't passed duplicate wrong guesses.
        if (!mEnforcing && ArrayUtils.contains(state.savedWrongGuesses, guess)) {
            return android.security.Flags.manageLockoutEndTimeInService()
                    ? computeRemainingTimeout(state, now)
                    : Duration.ZERO;
        }

        // Increment the failure counter regardless of whether the failure is a certainly wrong
        // guess or not. A generic failure might still be caused by a wrong guess. Gatekeeper only
        // ever returns generic failures, and some Weaver implementations prefer THROTTLE to
        // INCORRECT_KEY once the timeout becomes nonzero. Instead of making the software
        // rate-limiter ineffective on all such devices, still apply it. This does mean that correct
        // guesses that encountered an error will be rate-limited. However, by design the
        // rate-limiter kicks in gradually anyway, so there will be a chance for the user to try
        // again.
        state.numFailures++;
        state.timeSinceBootOfLastFailure = now;

        evaluateSoftwareRateLimit(state, now);

        // Update the counter on-disk. It is important that this be done before the failure is
        // reported to the UI, and that it be done synchronously e.g. by fsync()-ing the file and
        // its containing directory. This minimizes the risk of the counter being rolled back.
        writeFailureCounter(id, state);

        writeStats(id, state, /* success= */ false);

        // Save certainly wrong guesses so that duplicates of them can be detected.
        if (isCertainlyWrongGuess) {
            insertNewWrongGuess(state, guess);

            // Schedule the saved wrong guesses to be forgotten after a few minutes, extending the
            // existing timeout if one was already running.
            mInjector.removeCallbacksAndMessages(/* token= */ state);
            mInjector.postDelayed(
                    () -> {
                        Slogf.i(
                                TAG,
                                "Forgetting wrong LSKF guesses for user %d, protector %016x",
                                id.userId,
                                id.protectorId);
                        synchronized (this) {
                            forgetSavedWrongGuessesNoCancel(state);
                        }
                    },
                    /* token= */ state,
                    SAVED_WRONG_GUESS_TIMEOUT.toMillis());
        }

        return computeRemainingTimeout(state, now);
    }

    private Duration getOriginalTimeout(int numFailures) {
        if (!mEnforcing) {
            return Duration.ZERO;
        }
        if (numFailures >= TIMEOUT_TABLE.length || numFailures < 0) {
            // In this case actually no more guesses are allowed, but currently there is no way to
            // convey that information. For now just report the final timeout again.
            return TIMEOUT_TABLE[TIMEOUT_TABLE.length - 1];
        }
        return TIMEOUT_TABLE[numFailures];
    }

    private static int getStatsCredentialType(LockscreenCredential firstGuess) {
        if (firstGuess.isPin()) {
            return FrameworkStatsLog.LSKF_AUTHENTICATION_ATTEMPTED__CREDENTIAL_TYPE__PIN;
        } else if (firstGuess.isPattern()) {
            return FrameworkStatsLog.LSKF_AUTHENTICATION_ATTEMPTED__CREDENTIAL_TYPE__PATTERN;
            // Check isUnifiedProfilePassword() before isPassword(), since
            // isUnifiedProfilePassword() is a subset of isPassword().
        } else if (firstGuess.isUnifiedProfilePassword()) {
            return FrameworkStatsLog
                    .LSKF_AUTHENTICATION_ATTEMPTED__CREDENTIAL_TYPE__UNIFIED_PROFILE_PASSWORD;
        } else if (firstGuess.isPassword()) {
            return FrameworkStatsLog.LSKF_AUTHENTICATION_ATTEMPTED__CREDENTIAL_TYPE__PASSWORD;
        } else {
            return FrameworkStatsLog.LSKF_AUTHENTICATION_ATTEMPTED__CREDENTIAL_TYPE__UNKNOWN_TYPE;
        }
    }

    @GuardedBy("this")
    private void writeStats(LskfIdentifier id, RateLimiterState state, boolean success) {
        FrameworkStatsLog.write(
                FrameworkStatsLog.LSKF_AUTHENTICATION_ATTEMPTED,
                /* success= */ success,
                /* num_failures= */ state.numFailures,
                /* num_duplicate_guesses= */ state.numDuplicateWrongGuesses,
                /* credential_type= */ state.statsCredentialType,
                /* software_rate_limiter_enforcing= */ mEnforcing,
                /* hardware_rate_limiter= */ mInjector.getHardwareRateLimiter(id));
    }

    @GuardedBy("this")
    private RateLimiterState getExistingState(LskfIdentifier id) {
        RateLimiterState state = mState.get(id);
        if (state == null) {
            // This should never happen, since reportSuccess() and reportFailure() are always
            // supposed to be paired with a call to apply() that created the state if it did not
            // exist. Nor is it supported to call clearLskfState() or clearUserState() in between;
            // higher-level locking in LockSettingsService guarantees that never happens.
            throw new IllegalStateException("Could not find RateLimiterState");
        }
        return state;
    }

    @GuardedBy("this")
    private void forgetSavedWrongGuesses(RateLimiterState state) {
        mInjector.removeCallbacksAndMessages(/* token= */ state);
        forgetSavedWrongGuessesNoCancel(state);
    }

    @GuardedBy("this")
    private void forgetSavedWrongGuessesNoCancel(RateLimiterState state) {
        for (int i = 0; i < MAX_SAVED_WRONG_GUESSES; i++) {
            if (state.savedWrongGuesses[i] != null) {
                state.savedWrongGuesses[i].zeroize();
                state.savedWrongGuesses[i] = null;
            }
        }
    }

    /**
     * Clears the in-memory software rate-limiter state for a protector that is being removed.
     *
     * @param id the ID of the protector or special credential
     */
    synchronized void clearLskfState(LskfIdentifier id) {
        int index = mState.indexOfKey(id);
        if (index >= 0) {
            clearLskfStateAtIndex(index);
        }
    }

    /**
     * Clears the in-memory software rate-limiter state for a user that is being removed.
     *
     * @param userId the ID of the user being removed
     */
    synchronized void clearUserState(@UserIdInt int userId) {
        for (int index = mState.size() - 1; index >= 0; index--) {
            LskfIdentifier id = mState.keyAt(index);
            if (id.userId == userId) {
                clearLskfStateAtIndex(index);
            }
        }
    }

    @GuardedBy("this")
    private void clearLskfStateAtIndex(int index) {
        final RateLimiterState state = mState.valueAt(index);
        forgetSavedWrongGuesses(state);
        if (android.security.Flags.manageLockoutEndTimeInService()) {
            clearLockoutEndTime(state);
        }
        mState.removeAt(index);
    }

    private int readFailureCounter(LskfIdentifier id) {
        if (id.isSpecialCredential()) {
            // Special credentials (e.g. FRP credential and repair mode exit credential) do not yet
            // store a persistent failure counter.
            return 0;
        }
        return mInjector.readFailureCounter(id);
    }

    private void writeFailureCounter(LskfIdentifier id, RateLimiterState state) {
        if (id.isSpecialCredential()) {
            // Special credentials (e.g. FRP credential and repair mode exit credential) do not yet
            // store a persistent failure counter.
            return;
        }
        mInjector.writeFailureCounter(id, state.numFailures);
    }

    // Only for unit tests.
    @VisibleForTesting
    Duration[] getTimeoutTable() {
        return TIMEOUT_TABLE;
    }

    synchronized void dump(IndentingPrintWriter pw) {
        pw.println("Enforcing: " + mEnforcing);
        for (int index = 0; index < mState.size(); index++) {
            final LskfIdentifier lskfId = mState.keyAt(index);
            pw.println(
                    TextUtils.formatSimple(
                            "userId=%d, protectorId=%016x", lskfId.userId, lskfId.protectorId));
            final RateLimiterState state = mState.valueAt(index);
            pw.increaseIndent();
            pw.println("numFailures=" + state.numFailures);
            pw.println("numDuplicateWrongGuesses=" + state.numDuplicateWrongGuesses);
            pw.println("statsCredentialType=" + state.statsCredentialType);
            pw.println("timeSinceBootOfLastFailure=" + state.timeSinceBootOfLastFailure);
            pw.println("lockoutEndTime=" + state.lockoutEndTime);
            pw.println(
                    "numSavedWrongGuesses="
                            + Arrays.stream(state.savedWrongGuesses)
                                    .filter(Objects::nonNull)
                                    .count());
            pw.decreaseIndent();
        }
    }

    interface Injector {
        int readFailureCounter(LskfIdentifier id);

        void writeFailureCounter(LskfIdentifier id, int count);

        Duration getTimeSinceBoot();

        void removeCallbacksAndMessages(Object token);

        void postDelayed(Runnable runnable, Object token, long delayMillis);

        int getHardwareRateLimiter(LskfIdentifier id);

        void invalidateLockoutEndTimeCache();
    }
}
