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
import android.app.ActivityTaskManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.Intent;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.tasks.PackageMetadata;
import com.android.server.companion.datatransfer.continuity.tasks.PackageMetadataCache;
import com.android.server.wm.ActivityTaskManagerInternal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Fetches the running tasks from the system, converts them to {@link RemoteTaskInfo} and filters
 * out the tasks that should not be synced.
 */
public class RunningTaskFetcher {

    private static final String TAG = "RunningTaskFetcher";

    private final ActivityTaskManager mActivityTaskManager;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final PackageManager mPackageManager;
    private final PackageMetadataCache mPackageMetadataCache;

    public RunningTaskFetcher(@NonNull Context context) {
        this(
            Objects.requireNonNull(context).getSystemService(ActivityTaskManager.class),
            Objects.requireNonNull(LocalServices.getService(ActivityTaskManagerInternal.class)),
            Objects.requireNonNull(context).getPackageManager(),
            new PackageMetadataCache(Objects.requireNonNull(context).getPackageManager()));
    }

    public RunningTaskFetcher(
        @NonNull ActivityTaskManager activityTaskManager,
        @NonNull ActivityTaskManagerInternal activityTaskManagerInternal,
        @NonNull PackageManager packageManager,
        @NonNull PackageMetadataCache packageMetadataCache) {

        mActivityTaskManager = Objects.requireNonNull(activityTaskManager);
        mActivityTaskManagerInternal = Objects.requireNonNull(activityTaskManagerInternal);
        mPackageManager = Objects.requireNonNull(packageManager);
        mPackageMetadataCache = Objects.requireNonNull(packageMetadataCache);
    }

    @Nullable
    public RemoteTaskInfo getRunningTaskById(int taskId) {
        RemoteTaskInfo taskInfo = getRunningTasks()
            .stream()
            .filter(info -> info.id() == taskId)
            .findFirst()
            .orElse(null);

        if (taskInfo == null) {
            Slog.w(TAG, "Could not find RunningTaskInfo for taskId: " + taskId);
            return null;
        }

        return taskInfo;
    }

    @NonNull
    public List<RemoteTaskInfo> getRunningTasks() {
        return getRunningTaskInfos()
            .stream()
            .filter(this::shouldTaskBeSynced)
            .map(this::createRemoteTaskInfo)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Nullable
    private RemoteTaskInfo createRemoteTaskInfo(@Nullable RunningTaskInfo taskInfo) {
        if (taskInfo == null) {
            return null;
        }

        if (taskInfo.baseActivity == null || taskInfo.baseActivity.getPackageName() == null) {
            Slog.w(TAG, "Package name is null for task: " + taskInfo.taskId);
            return null;
        }

        String packageName = taskInfo.baseActivity.getPackageName();
        PackageMetadata packageMetadata = mPackageMetadataCache.getMetadataForPackage(packageName);
        if (packageMetadata == null) {
            Slog.w(TAG, "Could not get package metadata for task: " + taskInfo.taskId);
            return null;
        }

        boolean isHandoffEnabled
            = mActivityTaskManagerInternal.isHandoffEnabledForTask(taskInfo.taskId);

        return new RemoteTaskInfo(
            taskInfo.taskId,
            packageMetadata.label(),
            taskInfo.lastActiveTime,
            packageMetadata.icon(),
            isHandoffEnabled);
    }

    @NonNull
    private List<RunningTaskInfo> getRunningTaskInfos() {
        List<RunningTaskInfo> runningTaskInfos
            = mActivityTaskManager.getTasks(Integer.MAX_VALUE, true);
        if (runningTaskInfos == null) {
            return new ArrayList<>();
        }

        return runningTaskInfos;
    }

    private boolean shouldTaskBeSynced(@Nullable RunningTaskInfo taskInfo) {
        if (taskInfo == null) {
            return false;
        }

        Intent intent = new Intent("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.HOME");
        String defaultLauncherPackage = mPackageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .activityInfo
            .packageName;
        if (defaultLauncherPackage == null) {
            Slog.w(TAG, "Could not get default launcher package");
            return true;
        }

        return !defaultLauncherPackage.equals(taskInfo.baseActivity.getPackageName());
    }
}