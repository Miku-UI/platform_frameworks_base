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
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.app.AppOpsManager;
import android.app.HandoffActivityParams;
import android.app.TaskStackListener;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Slog;
import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffOptions;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskStackBroadcastMessage;
import com.android.server.wm.ActivityTaskManagerInternal;
import java.util.List;
import java.util.Objects;

/**
 * Responsible for broadcasting recent tasks on the current device to the user's
 *
 * <p>other devices via {@link CompanionDeviceManager}.
 */
public class TaskBroadcaster extends TaskStackListener
        implements ActivityTaskManagerInternal.HandoffEnablementListener {

    private static final String TAG = TaskBroadcaster.class.getSimpleName();

    private final int mUserId;
    private final TaskContinuityMessenger mTaskContinuityMessenger;
    private final Context mContext;

    public TaskBroadcaster(
            int userId,
            @NonNull Context context,
            @NonNull TaskContinuityMessenger taskContinuityMessenger) {

        mUserId = userId;
        mTaskContinuityMessenger = Objects.requireNonNull(taskContinuityMessenger);
        mContext = Objects.requireNonNull(context);
    }

    public void onDeviceConnected() {
        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, "onDeviceConnected");
        broadcastTaskStack();
        Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
    }

    @Override
    public void onTaskStackChanged() throws RemoteException {
        Slog.v(TAG, "onTaskStackChanged");
        broadcastTaskStack();
    }

    @Override
    public void onHandoffEnabledChanged(int taskId, boolean isHandoffEnabled) {
        Slog.v(
                TAG,
                "onHandoffEnabledChanged: taskId="
                        + taskId
                        + ", isHandoffEnabled="
                        + isHandoffEnabled);

        broadcastTaskStack();
    }

    private void broadcastTaskStack() {
        TaskStackBroadcastMessage.Builder taskStackBroadcastMessageBuilder =
                new TaskStackBroadcastMessage.Builder();
        List<RunningTaskInfo> runningTaskInfos =
                ((ActivityTaskManager) mContext.getSystemService(Context.ACTIVITY_TASK_SERVICE))
                        .getTasks(Integer.MAX_VALUE, true);
        if (runningTaskInfos != null) {
            for (RunningTaskInfo taskInfo : runningTaskInfos) {
                RemoteTaskInfo remoteTaskInfo = createRemoteTaskInfo(taskInfo);
                if (remoteTaskInfo != null) {
                    taskStackBroadcastMessageBuilder.addRemoteTask(remoteTaskInfo);
                }
            }
        }

        mTaskContinuityMessenger.sendMessage(
                new TaskContinuityMessage.Builder()
                        .setTaskStackBroadcastMessage(taskStackBroadcastMessageBuilder.build())
                        .build());
    }

    @Nullable
    private RemoteTaskInfo createRemoteTaskInfo(@Nullable RunningTaskInfo taskInfo) {
        if (taskInfo == null) {
            return null;
        }

        if (taskInfo.userId != mUserId) {
            Slog.w(TAG, "Task " + taskInfo.taskId + " is not for user " + mUserId);
            return null;
        }

        if (taskInfo.baseActivity == null || taskInfo.baseActivity.getPackageName() == null) {
            Slog.w(TAG, "Package name is null for task: " + taskInfo.taskId);
            return null;
        }

        if (!isContinueAcrossDevicesAllowedForPackage(taskInfo.baseActivity.getPackageName())) {
            Slog.w(
                    TAG,
                    "AppOpsManager.OP_CONTINUE_ACROSS_DEVICES is not allowed for task: "
                            + taskInfo.taskId);
            return null;
        }

        ActivityTaskManagerInternal activityTaskManagerInternal =
                LocalServices.getService(ActivityTaskManagerInternal.class);
        if (!activityTaskManagerInternal.isHandoffEnabledForTask(taskInfo.taskId)) {
            return null;
        }

        RemoteTaskInfo.Builder remoteTaskInfoBuilder =
                new RemoteTaskInfo.Builder()
                        .setId(taskInfo.taskId)
                        .setPackageName(taskInfo.baseActivity.getPackageName())
                        .setIsInForeground(taskInfo.isFocused)
                        .setLastUsedTimeMillis(taskInfo.lastActiveTime);

        HandoffOptions.Builder handoffOptionsBuilder =
                new HandoffOptions.Builder().setHandoffEnabled(true);
        HandoffActivityParams params =
                activityTaskManagerInternal.getHandoffActivityParamsForTask(taskInfo.taskId);
        if (params != null) {
            handoffOptionsBuilder.setRequirePackageInstalled(
                    !params.isAllowHandoffWithoutPackageInstalled());
        }

        return remoteTaskInfoBuilder.setHandoffOptions(handoffOptionsBuilder.build()).build();
    }

    private boolean isContinueAcrossDevicesAllowedForPackage(@NonNull String packageName) {
        try {
            return ((AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE))
                            .checkOpNoThrow(
                                    AppOpsManager.OP_CONTINUE_ACROSS_DEVICES,
                                    mContext.getPackageManager().getPackageUid(packageName, 0),
                                    packageName)
                    != AppOpsManager.MODE_IGNORED;
        } catch (NameNotFoundException e) {
            Slog.w(TAG, "Failed to note op for package: " + packageName, e);
            return false;
        }
    }
}
