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

package com.android.server.pm.verify.developer;

import android.annotation.CurrentTimeMillisLong;
import android.annotation.DurationMillisLong;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;

import com.android.internal.annotations.VisibleForTesting;

/**
 * This class keeps record of the current timeout status of a verification request.
 */
public final class DeveloperVerificationRequestStatusTracker {
    private final @CurrentTimeMillisLong long mStartTime;
    private @CurrentTimeMillisLong long mTimeoutTime;
    private final @CurrentTimeMillisLong long mMaxTimeoutTime;
    @NonNull
    private final DeveloperVerifierController.Injector mInjector;
    private final @UserIdInt int mUserId;

    /**
     * By default, the timeout time is the default timeout duration plus the current time (when
     * the timer starts for a verification request). Both the default timeout time and the max
     * timeout time cannot be changed after the timer has started, but the actual timeout time
     * can be extended via {@link #extendTimeoutMillis} to the maximum allowed.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public DeveloperVerificationRequestStatusTracker(@DurationMillisLong long defaultTimeoutMillis,
            @DurationMillisLong long maxExtendedTimeoutMillis,
            @NonNull DeveloperVerifierController.Injector injector, @UserIdInt int userId) {
        mStartTime = injector.getCurrentTimeMillis();
        mTimeoutTime = mStartTime + defaultTimeoutMillis;
        mMaxTimeoutTime = mStartTime + maxExtendedTimeoutMillis;
        mInjector = injector;
        mUserId = userId;
    }

    /**
     * Used by the controller to inform the verifier agent about the timestamp when the verification
     * request will timeout.
     */
    public @CurrentTimeMillisLong long getTimeoutTime() {
        return mTimeoutTime;
    }

    /**
     * Used by the controller to decide when to check for timeout again.
     * @return 0 if the timeout time has been reached, otherwise the remaining time in milliseconds
     * before the timeout is reached.
     */
    public @DurationMillisLong long getRemainingTime() {
        final long remainingTime = mTimeoutTime - mInjector.getCurrentTimeMillis();
        if (remainingTime < 0) {
            return 0;
        }
        return remainingTime;
    }

    /**
     * Used by the controller to extend the timeout duration of the verification request, upon
     * receiving the callback from the verifier agent.
     * @return the amount of time in millis that the timeout has been extended, subject to the max
     * amount allowed.
     */
    public @DurationMillisLong long extendTimeoutMillis(@DurationMillisLong long additionalMs) {
        if (mTimeoutTime + additionalMs > mMaxTimeoutTime) {
            additionalMs = mMaxTimeoutTime - mTimeoutTime;
        }
        mTimeoutTime += additionalMs;
        return additionalMs;
    }

    /**
     * Used by the controller to get the timeout status of the request.
     * @return False if the request still has some time left before timeout, otherwise return True.
     */
    public boolean isTimeout() {
        return mInjector.getCurrentTimeMillis() >= mTimeoutTime;
    }

    public @UserIdInt int getUserId() {
        return mUserId;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o instanceof DeveloperVerificationRequestStatusTracker that) {
            return this.mStartTime == that.mStartTime
                    && this.mTimeoutTime == that.mTimeoutTime
                    && this.mMaxTimeoutTime == that.mMaxTimeoutTime
                    && this.mInjector == that.mInjector
                    && this.mUserId == that.mUserId;
        }
        return false;
    }
}
