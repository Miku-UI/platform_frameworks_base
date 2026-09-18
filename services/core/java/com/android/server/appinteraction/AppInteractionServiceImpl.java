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

import android.annotation.CurrentTimeMillisLong;
import android.app.AppInteractionAttribution;
import android.app.appfunctions.flags.Flags;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.Slog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.os.BackgroundThread;
import com.android.server.SystemService;

import java.util.Objects;
import java.util.concurrent.Executor;

public class AppInteractionServiceImpl implements AppInteractionService {
    private static final String TAG = "AppInteractionService";

    @NonNull private final Context mContext;

    @NonNull private final MultiUserAppInteractionHistory mMultiUserAppInteractionHistory;

    @NonNull private final Executor mBackgroundExecutor;

    public AppInteractionServiceImpl(@NonNull Context context) {
        this(
                Objects.requireNonNull(context),
                MultiUserAppInteractionHistory.getInstance(context),
                BackgroundThread.getExecutor());
    }

    @GuardedBy("mLock")
    private boolean mPackageMonitorRegistered = false;

    @NonNull private final Object mLock = new Object();

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public AppInteractionServiceImpl(
            @NonNull Context context,
            @NonNull MultiUserAppInteractionHistory multiUserAppInteractionHistory,
            @NonNull Executor backgroundExecutor) {
        mContext = Objects.requireNonNull(context);
        mMultiUserAppInteractionHistory = Objects.requireNonNull(multiUserAppInteractionHistory);
        mBackgroundExecutor = Objects.requireNonNull(backgroundExecutor);
    }

    @Override
    public void onUserUnlocked(@NonNull SystemService.TargetUser user) {
        if (!Flags.enableAppInteractionApi()) return;

        Objects.requireNonNull(user);
        synchronized (mLock) {
            if (!mPackageMonitorRegistered) {
                mPackageMonitor.register(mContext, UserHandle.ALL, BackgroundThread.getHandler());
                mPackageMonitorRegistered = true;
            }
        }
        mMultiUserAppInteractionHistory.onUserUnlocked(user);
    }

    @Override
    public void onUserStopping(@NonNull SystemService.TargetUser user) {
        if (!Flags.enableAppInteractionApi()) return;

        Objects.requireNonNull(user);
        mMultiUserAppInteractionHistory.onUserStopping(user);
    }

    @Override
    public void noteAppInteraction(
            @NonNull String sourcePackage,
            @NonNull String targetPackage,
            @Nullable AppInteractionAttribution appInteractionAttribution,
            @CurrentTimeMillisLong long accessTime,
            int userId) {
        if (!Flags.enableAppInteractionApi()) return;

        Objects.requireNonNull(sourcePackage);
        Objects.requireNonNull(targetPackage);

        try {
            checkPackageExist(sourcePackage, userId);
            checkPackageExist(targetPackage, userId);
        } catch (SecurityException e) {
            Slog.w(TAG, "Package validation fail", e);
            return;
        }

        mBackgroundExecutor.execute(
                () -> {
                    try {
                        mMultiUserAppInteractionHistory
                                .asUser(userId)
                                .insertAppInteractionHistory(
                                        sourcePackage,
                                        targetPackage,
                                        appInteractionAttribution,
                                        accessTime);
                    } catch (IllegalStateException e) {
                        Slog.e(TAG, "Unable to insert App Interaction history", e);
                    }
                });
    }

    private void checkPackageExist(@NonNull String packageName, int userId)
            throws SecurityException {
        Objects.requireNonNull(packageName);

        UserHandle user = UserHandle.of(userId);
        try {
            mContext.createPackageContextAsUser(packageName, /* flags= */ 0, user);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException(
                    "Package: " + packageName + " haven't installed for user " + userId);
        }
    }

    @VisibleForTesting
    final PackageMonitor mPackageMonitor =
            new PackageMonitor() {
                @Override
                public void onPackageRemoved(String packageName, int uid) {
                    int userId = UserHandle.getUserId(uid);
                    try {
                        mMultiUserAppInteractionHistory
                                .asUser(userId)
                                .deleteAppInteractionHistories(packageName);
                    } catch (IllegalStateException e) {
                        Slog.w(TAG, "Unable to delete App Interaction history", e);
                    }
                }
            };
}
