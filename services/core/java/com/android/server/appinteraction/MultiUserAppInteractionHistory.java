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

package com.android.server.appinteraction;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.UserHandle;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.os.BackgroundThread;
import com.android.server.SystemService;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Manages {@link AppInteractionHistory} in a multi-user environment. */
public class MultiUserAppInteractionHistory {
    private static final String TAG = "MultiUserAppInteraction";

    private final boolean DEBUG = Build.TYPE.equals("eng");

    private static MultiUserAppInteractionHistory sInstance = null;

    private static final String APP_INTERACTION_DIR = "app_interaction";

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<AppInteractionHistory> mUserHistoryMap = new SparseArray<>();

    private final SparseArray<ScheduledFuture<?>> mUserPeriodicCleanUpFutures = new SparseArray<>();

    private final ServiceConfig mServiceConfig;

    private final ScheduledExecutorService mScheduledExecutorService;

    private final Function<UserHandle, AndroidFuture<AppInteractionHistory>> mUserHistoryFactory;

    @GuardedBy("mLock")
    private SparseArray<AndroidFuture<AppInteractionHistory>> mUserHistoryCreationFutures =
            new SparseArray<>();

    public MultiUserAppInteractionHistory(
            @NonNull ServiceConfig serviceConfig,
            @NonNull ScheduledExecutorService scheduledExecutorService,
            @NonNull
                    Function<UserHandle, AndroidFuture<AppInteractionHistory>> userHistoryFactory) {
        mServiceConfig = Objects.requireNonNull(serviceConfig);
        mScheduledExecutorService = Objects.requireNonNull(scheduledExecutorService);
        mUserHistoryFactory = Objects.requireNonNull(userHistoryFactory);
    }

    /**
     * Called after an existing user is unlocked.
     *
     * <p>This will start tracking {@code user}'s App Interaction history.
     */
    public void onUserUnlocked(@NonNull SystemService.TargetUser user) {
        synchronized (mLock) {
            if (mUserHistoryCreationFutures.contains(user.getUserIdentifier())) {
                if (DEBUG) {
                    Slog.d(
                            TAG,
                            "History for user " + user.getUserIdentifier() + " is still creating.");
                }
                return;
            }

            AndroidFuture<AppInteractionHistory> future =
                    mUserHistoryFactory.apply(user.getUserHandle());
            mUserHistoryCreationFutures.put(user.getUserIdentifier(), future);
            future.whenComplete(
                    (appInteractionHistory, throwable) -> {
                        synchronized (mLock) {
                            mUserHistoryCreationFutures.remove(user.getUserIdentifier());
                            if (throwable != null) {
                                Slog.e(
                                        TAG,
                                        "Fail to create AppInteractionHistory for user "
                                                + user.getUserIdentifier(),
                                        throwable);
                                return;
                            }
                            if (!mUserHistoryMap.contains(user.getUserIdentifier())) {
                                mUserHistoryMap.put(
                                        user.getUserIdentifier(), appInteractionHistory);
                            }
                            schedulePeriodicInteractionCleanUpJob(user.getUserIdentifier());
                        }
                    });
        }
    }

    /**
     * Called when an existing user is stopping.
     *
     * <p>This will stop tracking {@code user}'s App Interaction history.
     */
    public void onUserStopping(@NonNull SystemService.TargetUser user) {
        synchronized (mLock) {
            // Cancel the creation future if it is still running.
            final AndroidFuture<AppInteractionHistory> creationFuture =
                    mUserHistoryCreationFutures.removeReturnOld(user.getUserIdentifier());
            if (creationFuture != null) {
                creationFuture.cancel(/* mayInterruptIfRunning= */ true);
            }

            final AppInteractionHistory removed =
                    mUserHistoryMap.removeReturnOld(user.getUserIdentifier());
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
    private void schedulePeriodicInteractionCleanUpJob(int userId) {
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
                                        .deleteExpiredAppInteractionHistories(
                                                mServiceConfig
                                                        .getAppInteractionHistoryRetentionMillis());
                            } catch (IllegalStateException e) {
                                Slog.w(
                                        TAG,
                                        "Fail to delete expired App Interaction history for user"
                                                + " "
                                                + userId,
                                        e);
                            }
                        },
                        0,
                        mServiceConfig.getAppInteractionExpiredHistoryDeletionIntervalMillis(),
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
     * Starts a {@link AppInteractionHistory} as {@code userId}. The caller can use this to interact
     * with {@link AppInteractionHistory} for the target user.
     *
     * @throws IllegalStateException if the {@code user} is not unlocked yet.
     */
    @NonNull
    public AppInteractionHistory asUser(int userId) throws IllegalStateException {
        synchronized (mLock) {
            if (mUserHistoryCreationFutures.contains(userId)) {
                throw new IllegalStateException("User " + userId + " is still unlocking");
            }
            final AppInteractionHistory cache = mUserHistoryMap.get(userId);
            if (cache == null) {
                throw new IllegalStateException("User " + userId + " is not unlocked yet");
            }
            return cache;
        }
    }

    /** Gets a singleton instance. */
    public static synchronized MultiUserAppInteractionHistory getInstance(
            @NonNull Context context) {
        if (sInstance == null) {
            sInstance =
                    new MultiUserAppInteractionHistory(
                            new ServiceConfigImpl(),
                            Executors.newSingleThreadScheduledExecutor(
                                    new NamedThreadFactory("AppInteractionScheduledExecutors")),
                            new UserHistoryFactory(context, BackgroundThread.getExecutor()));
        }
        return sInstance;
    }

    private static final class UserHistoryFactory
            implements Function<UserHandle, AndroidFuture<AppInteractionHistory>> {
        private final Context mContext;
        private final Executor mBackgroundExecutor;

        UserHistoryFactory(@NonNull Context context, @NonNull Executor backgroundExecutor) {
            mContext = Objects.requireNonNull(context);
            mBackgroundExecutor = Objects.requireNonNull(backgroundExecutor);
        }

        @Override
        public AndroidFuture<AppInteractionHistory> apply(@NonNull UserHandle userHandle) {
            Objects.requireNonNull(userHandle);

            AndroidFuture<AppInteractionHistory> future = new AndroidFuture<>();
            mBackgroundExecutor.execute(
                    () -> {
                        try {
                            int userId = userHandle.getIdentifier();
                            File ceDir = Environment.getDataSystemCeDirectory(userId);
                            File appInteractionDir = new File(ceDir, APP_INTERACTION_DIR);
                            appInteractionDir.mkdirs();
                            File dbFile =
                                    new File(
                                            appInteractionDir,
                                            AppInteractionSQLiteHistory.DB_NAME);
                            Context userContext =
                                    mContext.createContextAsUser(userHandle, /* flags= */ 0);
                            String dbPath = dbFile.getAbsolutePath();
                            future.complete(
                                    new AppInteractionSQLiteHistory(userContext, dbPath));
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        }
                    });
            return future;
        }
    }
}
