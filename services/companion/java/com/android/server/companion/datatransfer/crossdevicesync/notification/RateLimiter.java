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
package com.android.server.companion.datatransfer.crossdevicesync.notification;

import android.annotation.NonNull;

import java.time.Period;

/**
 * The Rate Limiters for the notification that should be applied in order. A Rate Limiter denotes
 * the minimum waiting period and the number of times that period applies, which means the
 * notification will be shown at most X number of times, with at least Y minimum waiting period
 * between each display of the notification. This means that any requests to show a notification
 * sooner than the recommended minimum wait period will be dropped and the notification will not be
 * shown.
 *
 * <p>For a notification, this is a repeated list, so a different waiting period can be used based
 * on the number of times the notification is shown.
 *
 * <ol>
 *   <li>The first waiting period must be zero which means it's shown immediately.
 *   <li>The first limiter's timesToRepeat denotes how many times the notification will be shown
 *       immediately without waiting for any period after it's shown.
 *   <li>Optionally, the last waiting period may set the timesToRepeat to FOREVER which indicates
 *       that we will keep showing the notification for eternity, but wait for at least it's minimum
 *       waiting period before showing it again.
 * </ol>
 *
 * <p>For example, if the list contains :
 *
 * <ul>
 *   <li>wait = 0 days, repeat 2 times
 *   <li>wait = 5 days, repeat = 3 times
 *   <li>wait = 10 days, repeat = 5 times
 * </ul>
 *
 * <p>This means the notification is shown once twice on demand, then 3 times at least 5 days apart
 * and then 5 times at least 10 days apart and never after that.
 *
 * <p>If instead, the last Limiter is [wait = 10 days, repeat = -1], then instead of showing the
 * notification only 5 times at least 10 days apart, it's shown forever, waiting at least 10 days
 * before showing it again when requested.
 */
record RateLimiter(@NonNull Period minimumWaitPeriod, int timesToRepeat) {
    /**
     * This is a proxy for the {@link RateLimiter#timesToRepeat()} to indicate that it repeats
     * forever
     */
    static final int FOREVER = -1;

    RateLimiter {
        if (minimumWaitPeriod.isNegative()) {
            throw new IllegalArgumentException("minimumWaitPeriod can not be negative");
        }
        if (timesToRepeat != FOREVER && timesToRepeat <= 0) {
            throw new IllegalArgumentException("timesToShow can only be -1 (FOREVER) or >0");
        }
    }
}
