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
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.UserHandle;
import android.util.Log;

import com.android.server.companion.datatransfer.crossdevicesync.common.Clock;
import com.android.server.companion.datatransfer.crossdevicesync.user.UserHelper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/** Implementation of {@link NotificationHelper}. */
public class NotificationHelperImpl implements NotificationHelper, UserHelper.UserListener {
    private static final String TAG = "NotificationHelper";

    private final SharedPreferences mSharedPreferences;
    private final Clock mClock;
    private final NotificationManagerProxy mNotificationManager;
    private final Supplier<NotificationConfig> mNotificationConfig;
    private final Executor mMainExecutor;
    private final UserHelper mUserHelper;

    private final Object mLock = new Object();

    private boolean mInitialized = false;

    public NotificationHelperImpl(
            SharedPreferences sharedPreferences,
            Clock clock,
            NotificationManagerProxy notificationManager,
            Supplier<NotificationConfig> notificationConfig,
            Executor mainExecutor,
            UserHelper userHelper) {
        mSharedPreferences = sharedPreferences;
        mClock = clock;
        mNotificationManager = notificationManager;
        mNotificationConfig = notificationConfig;
        mMainExecutor = mainExecutor;
        mUserHelper = userHelper;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            if (mInitialized) {
                return;
            }
            mInitialized = true;
            mUserHelper.registerUserListener(mMainExecutor, this);
        }
    }

    @Override
    public void destroy() {
        synchronized (mLock) {
            if (!mInitialized) {
                return;
            }
            mInitialized = false;
            mUserHelper.unregisterUserListener(this);
        }
    }

    @Override
    public void maybeShowNotification(
            @NotificationReason String notificationReason, @NonNull UserHandle user) {
        synchronized (mLock) {
            if (!mInitialized) {
                return;
            }

            Notification notification =
                    mNotificationConfig.get().mNotificationMap.get(notificationReason);
            if (notification == null) {
                Log.e(TAG, "maybeShowNotification: Unknown Reason (" + notificationReason + ")");
                return;
            }

            if (!checkAndRecordEligibility(notificationReason, user, notification.rateLimiters())) {
                return;
            }

            // Clear any stale notifications for the same reason.
            mNotificationManager.cancel(notification.id(), user);
            // Create a channel and show the notification accordingly for the reason.
            mNotificationManager.createNotificationChannel(notification.createChannel().get());
            mNotificationManager.notify(
                    notification.id(), notification.createAndroidNotification().get(), user);
        }
    }

    @SuppressLint("UseKtx")
    private boolean checkAndRecordEligibility(
            String reason, UserHandle user, List<RateLimiter> rateLimiters) {
        String countKey = reason + "_" + user.getIdentifier();
        String dateKey = countKey + "_date";
        int countShown = mSharedPreferences.getInt(countKey, 0);

        int accumulatedShows = 0;
        RateLimiter limit = null;
        for (RateLimiter limiter : rateLimiters) {
            if (limiter.timesToRepeat() == RateLimiter.FOREVER) {
                limit = limiter;
                break;
            }
            accumulatedShows += limiter.timesToRepeat();
            if (countShown < accumulatedShows) {
                limit = limiter;
                break;
            }
        }

        if (limit == null) {
            Log.i(
                    TAG,
                    "checkAndRecordNotificationEligibility("
                            + reason
                            + "): Notification is not shown. countShown="
                            + countShown);
            return false;
        }

        LocalDateTime now =
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(mClock.currentTimeMillis()), ZoneId.systemDefault());
        LocalDateTime savedDate = null;
        if (countShown != 0 && !limit.minimumWaitPeriod().isZero()) {
            String date = mSharedPreferences.getString(dateKey, null);
            if (date != null) {
                try {
                    savedDate = LocalDateTime.parse(date);
                    // Comparison is +/- 1 hour, allowing for both battery optimization and
                    // consistency
                    LocalDateTime threshold =
                            savedDate.plus(limit.minimumWaitPeriod()).minusHours(1);

                    if (now.isBefore(threshold)) {
                        Log.i(
                                TAG,
                                "checkAndRecordNotificationEligibility("
                                        + reason
                                        + "): Notification is not shown. countShown="
                                        + countShown
                                        + " now="
                                        + now
                                        + " savedDate="
                                        + savedDate);
                        return false;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing saved date: " + date, e);
                }
            }
        }

        // Update Shared Preferences to save the shown count and the shown date/time.
        mSharedPreferences
                .edit()
                .putInt(countKey, countShown + 1)
                .putString(dateKey, now.toString())
                .apply();
        Log.i(
                TAG,
                "checkAndRecordNotificationEligibility("
                        + reason
                        + "): Notification is being shown. countShown="
                        + countShown
                        + " now="
                        + now
                        + " savedDate="
                        + savedDate);
        return true;
    }

    @Override
    public void dismissNotification(
            @NotificationReason String notificationReason, @NonNull UserHandle user) {
        synchronized (mLock) {
            if (!mInitialized) {
                return;
            }

            Notification notification =
                    mNotificationConfig.get().mNotificationMap.get(notificationReason);
            if (notification == null) {
                Log.e(TAG, "dismissNotification: Unknown Reason (" + notificationReason + ")");
                return;
            }

            // Clear any stale notifications for the reason.
            mNotificationManager.cancel(notification.id(), user);
        }
    }

    @Override
    public void reset() {
        synchronized (mLock) {
            if (!mInitialized) {
                return;
            }

            clearSharedPrefs(reason -> reason);
        }
    }

    @Override
    public void onUserRemoved(@NonNull UserHandle user) {
        synchronized (mLock) {
            if (!mInitialized) {
                return;
            }

            clearSharedPrefs(reason -> reason + "_" + user.getIdentifier());
        }
    }

    @SuppressLint("UseKtx")
    private void clearSharedPrefs(Function<String, String> getPrefixForReason) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        Map<String, ?> allPrefs = mSharedPreferences.getAll();
        for (String reason : mNotificationConfig.get().mNotificationMap.keySet()) {
            String prefix = getPrefixForReason.apply(reason);
            for (String key : allPrefs.keySet()) {
                if (key.startsWith(prefix)) {
                    editor.remove(key);
                }
            }
        }
        editor.apply();
    }
}
