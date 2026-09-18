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
import android.app.NotificationChannel;

import java.time.Period;
import java.util.List;
import java.util.function.Supplier;

/** Details about the notification being displayed. */
record Notification(
        int id,
        @NonNull Supplier<NotificationChannel> createChannel,
        @NonNull Supplier<android.app.Notification> createAndroidNotification,
        @NonNull List<RateLimiter> rateLimiters) {

    Notification {
        if (rateLimiters.isEmpty()) {
            throw new IllegalArgumentException("Rate Limiters must be specified");
        }
        if (!rateLimiters.get(0).minimumWaitPeriod().equals(Period.ZERO)) {
            throw new IllegalArgumentException("First Notification must be shown immediately");
        }
        for (int i = 0; i < rateLimiters.size() - 1; i++) {
            if (rateLimiters.get(i).timesToRepeat() == RateLimiter.FOREVER) {
                throw new IllegalArgumentException(
                        "Rate Limiters other than the last one cannot be shown forever");
            }
        }
    }
}
