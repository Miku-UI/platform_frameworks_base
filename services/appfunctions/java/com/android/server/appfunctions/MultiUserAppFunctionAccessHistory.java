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

package com.android.server.appfunctions;

import static com.android.server.appfunctions.AppFunctionExecutors.SCHEDULED_EXECUTOR_SERVICE;

import android.annotation.NonNull;
import android.content.Context;
import android.os.UserHandle;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.SystemService;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Manages {@link AppFunctionAccessHistory} in a multi-user environment. */
public class MultiUserAppFunctionAccessHistory {
    private static final String TAG = "MultiUserAppFunctionAccess";

    private static MultiUserAppFunctionAccessHistory sInstance = null;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<AppFunctionAccessHistory> mUserAccessHistoryMap = new SparseArray<>();

    private final SparseArray<ScheduledFuture<?>> mUserPeriodicCleanUpFutures = new SparseArray<>();

    private final ServiceConfig mServiceConfig;

    private final ScheduledExecutorService mScheduledExecutorService;

    private final Function<UserHandle, AppFunctionAccessHistory> mUserAccessHistoryFactory;

    public MultiUserAppFunctionAccessHistory(
            @NonNull ServiceConfig serviceConfig,
            @NonNull ScheduledExecutorService scheduledExecutorService,
            @NonNull Function<UserHandle, AppFunctionAccessHistory> userAccessHistoryFactory) {
        mServiceConfig = Objects.requireNonNull(serviceConfig);
        mScheduledExecutorService = Objects.requireNonNull(scheduledExecutorService);
        mUserAccessHistoryFactory = Objects.requireNonNull(userAccessHistoryFactory);
    }

    /**
     * Called after an existing user is unlocked.
     *
     * <p>This will start tracking {@code user}'s AppFunction access history.
     */
    public void onUserUnlocked(@NonNull SystemService.TargetUser user) {
        synchronized (mLock) {
            if (!mUserAccessHistoryMap.contains(user.getUserIdentifier())) {
                mUserAccessHistoryMap.put(
                        user.getUserIdentifier(),
                        mUserAccessHistoryFactory.apply(user.getUserHandle()));
            }
            schedulePeriodicAccessHistoryCleanUpJob(user.getUserIdentifier());
        }
    }

    /**
     * Called when an existing user is stopping.
     *
     * <p>This will stop tracking {@code user}'s AppFunction access history.
     */
    public void onUserStopping(@NonNull SystemService.TargetUser user) {
        synchronized (mLock) {
            final AppFunctionAccessHistory removed =
                    mUserAccessHistoryMap.removeReturnOld(user.getUserIdentifier());
            if (removed != null) {
                cancelScheduledCleanUpJob(user.getUserHandle());
                try {
                    removed.close();
                } catch (Exception e) {
                    Slog.e(TAG, "Fail to close database for user " + user.getUserIdentifier(), e);
                }
            }
        }
    }

    @GuardedBy("mLock")
    private void schedulePeriodicAccessHistoryCleanUpJob(int userId) {
        if (mScheduledExecutorService.isShutdown()) {
            Slog.e(TAG, "Scheduled executor service is shut down.");
            return;
        }

        if (mUserPeriodicCleanUpFutures.contains(userId)) {
            Slog.i(
                    TAG,
                    "Periodic history clean up job for user " + userId + " is already scheduled");
            return;
        }

        ScheduledFuture<?> scheduledFuture =
                mScheduledExecutorService.scheduleAtFixedRate(
                        () -> {
                            try {
                                asUser(userId)
                                        .deleteExpiredAppFunctionAccessHistories(
                                                mServiceConfig
                                                    .getAppFunctionAccessHistoryRetentionMillis());
                            } catch (IllegalStateException e) {
                                Slog.w(
                                        TAG,
                                        "Fail to delete expired AppFunction access history for user"
                                                + " "
                                                + userId,
                                        e);
                            }
                        },
                        0,
                        mServiceConfig.getAppFunctionExpiredAccessHistoryDeletionIntervalMillis(),
                        TimeUnit.MILLISECONDS);
        mUserPeriodicCleanUpFutures.put(userId, scheduledFuture);
    }

    @GuardedBy("mLock")
    private void cancelScheduledCleanUpJob(@NonNull UserHandle user) {
        ScheduledFuture<?> removed =
                mUserPeriodicCleanUpFutures.removeReturnOld(user.getIdentifier());
        if (removed != null) {
            removed.cancel(/* mayInterruptIfRunning= */ true);
        }
    }

    /**
     * Starts a {@link AppFunctionAccessHistory} as {@code userId}. The caller can use this to
     * interact with {@link AppFunctionAccessHistory} for the target user.
     *
     * @throws IllegalStateException if the {@code user} is not unlocked yet.
     */
    @NonNull
    public AppFunctionAccessHistory asUser(int userId) throws IllegalStateException {
        synchronized (mLock) {
            final AppFunctionAccessHistory cache = mUserAccessHistoryMap.get(userId);
            if (cache == null) {
                throw new IllegalStateException("User " + userId + " is not unlocked yet");
            }
            return cache;
        }
    }

    /** Gets a singleton instance. */
    public static synchronized MultiUserAppFunctionAccessHistory getInstance(
            @NonNull Context context) {
        if (sInstance == null) {
            sInstance =
                    new MultiUserAppFunctionAccessHistory(
                            new ServiceConfigImpl(),
                            SCHEDULED_EXECUTOR_SERVICE,
                            /* userAccessHistoryFactory */ new Function<>() {
                                @Override
                                @NonNull
                                public AppFunctionAccessHistory apply(
                                        @NonNull UserHandle userHandle) {
                                    Objects.requireNonNull(userHandle);
                                    return new AppFunctionSQLiteAccessHistory(
                                            context.createContextAsUser(
                                                    userHandle, /* flags= */ 0));
                                }
                            });
        }
        return sInstance;
    }
}
