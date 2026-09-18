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

package com.android.server.companion.datatransfer.continuity.tasks;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.datatransfer.continuity.RemoteTask;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Slog;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import java.util.Objects;

public class RemoteTaskFactory {

    private static final String TAG = RemoteTaskFactory.class.getSimpleName();

    @Nullable
    public static RemoteTask create(
            @NonNull PackageManager packageManager,
            int userId,
            int associationId,
            @Nullable String associationDisplayName,
            @NonNull RemoteTaskInfo remoteTaskInfo) {
        Objects.requireNonNull(remoteTaskInfo);

        boolean canTaskBeHandedOffLocally =
                canTaskBeHandedOffNatively(packageManager, remoteTaskInfo);
        boolean canTaskBeHandedOffByBrowser =
                canTaskBeHandedOffByBrowser(userId, packageManager, remoteTaskInfo);

        if (!canTaskBeHandedOffLocally && !canTaskBeHandedOffByBrowser) {
            return null;
        }

        String packageName =
                canTaskBeHandedOffLocally
                        ? remoteTaskInfo.packageName()
                        : packageManager.getDefaultBrowserPackageNameAsUser(userId);

        return new RemoteTask.Builder(associationId, remoteTaskInfo.id())
                .setPackageName(packageName)
                .setLabel(getPackageLabel(packageManager, packageName))
                .setLastUsedTimestampMillis(remoteTaskInfo.lastUsedTimeMillis())
                .setAssociationDisplayName(associationDisplayName)
                .setTaskInForeground(remoteTaskInfo.isInForeground())
                .setHandoffEnabled(remoteTaskInfo.handoffOptions().isHandoffEnabled())
                .build();
    }

    @Nullable
    private static String getPackageLabel(
            @NonNull PackageManager packageManager, @NonNull String packageName) {
        PackageInfo packageInfo = getPackageInfo(packageManager, packageName);
        if (packageInfo == null) {
            return null;
        }

        return packageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
    }

    @Nullable
    private static PackageInfo getPackageInfo(
            @NonNull PackageManager packageManager, @NonNull String packageName) {
        try {
            return packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.w(TAG, "Could not find package info for package: " + packageName);
            return null;
        }
    }

    private static boolean canTaskBeHandedOffNatively(
            @NonNull PackageManager packageManager, @NonNull RemoteTaskInfo remoteTaskInfo) {
        Objects.requireNonNull(remoteTaskInfo);

        if (!remoteTaskInfo.handoffOptions().isHandoffEnabled()) {
            return false;
        }

        return getPackageInfo(packageManager, remoteTaskInfo.packageName()) != null;
    }

    private static boolean canTaskBeHandedOffByBrowser(
            int userId,
            @NonNull PackageManager packageManager,
            @NonNull RemoteTaskInfo remoteTaskInfo) {
        Objects.requireNonNull(remoteTaskInfo);
        return packageManager.getDefaultBrowserPackageNameAsUser(userId) != null
                && remoteTaskInfo.handoffOptions().isHandoffEnabled()
                && !remoteTaskInfo.handoffOptions().requirePackageInstalled();
    }
}
