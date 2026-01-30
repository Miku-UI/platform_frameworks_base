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

package com.android.server.companion.datatransfer.continuity;

import android.annotation.NonNull;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.os.RemoteException;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskRemovedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskUpdatedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.tasks.RunningTaskFetcher;
import com.android.server.wm.ActivityTaskManagerInternal;

import java.util.List;
import java.util.Objects;

/**
 * Responsible for broadcasting recent tasks on the current device to the user's
 *
 * other devices via {@link CompanionDeviceManager}.
 */
class TaskBroadcaster
    extends TaskStackListener implements ActivityTaskManagerInternal.HandoffEnablementListener {

    private static final String TAG = "TaskBroadcaster";

    private final ActivityTaskManager mActivityTaskManager;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final TaskContinuityMessenger mTaskContinuityMessenger;
    private final RunningTaskFetcher mRunningTaskFetcher;

    private boolean mIsListeningToActivityTaskManager = false;

    public TaskBroadcaster(
        @NonNull Context context,
        @NonNull TaskContinuityMessenger taskContinuityMessenger) {
        this(
            Objects.requireNonNull(taskContinuityMessenger),
            Objects.requireNonNull(context).getSystemService(ActivityTaskManager.class),
            Objects.requireNonNull(LocalServices.getService(ActivityTaskManagerInternal.class)),
            new RunningTaskFetcher(Objects.requireNonNull(context)));
    }

    public TaskBroadcaster(
        @NonNull TaskContinuityMessenger taskContinuityMessenger,
        @NonNull ActivityTaskManager activityTaskManager,
        @NonNull ActivityTaskManagerInternal activityTaskManagerInternal,
        @NonNull RunningTaskFetcher runningTaskFetcher) {

        mTaskContinuityMessenger = Objects.requireNonNull(taskContinuityMessenger);
        mActivityTaskManager = Objects.requireNonNull(activityTaskManager);
        mActivityTaskManagerInternal = Objects.requireNonNull(activityTaskManagerInternal);
        mRunningTaskFetcher = Objects.requireNonNull(runningTaskFetcher);
    }

    public void onDeviceConnected(int associationId) {
        Slog.v(TAG, "Transport connected for association id: " + associationId);
        mTaskContinuityMessenger.sendMessage(
            associationId,
            new ContinuityDeviceConnected(mRunningTaskFetcher.getRunningTasks()));

        synchronized (this) {
            if (!mIsListeningToActivityTaskManager) {
                mActivityTaskManager.registerTaskStackListener(this);
                mActivityTaskManagerInternal.registerHandoffEnablementListener(this);
                mIsListeningToActivityTaskManager = true;
            }
        }
    }

    public void onAllDevicesDisconnected() {
        synchronized (this) {
            if (mIsListeningToActivityTaskManager) {
                mActivityTaskManager.unregisterTaskStackListener(this);
                mActivityTaskManagerInternal.unregisterHandoffEnablementListener(this);
                mIsListeningToActivityTaskManager = false;
            }
        }
    }

    @Override
    public void onTaskCreated(int taskId, ComponentName componentName) throws RemoteException {
        Slog.v(TAG, "onTaskCreated: taskId=" + taskId);

        RemoteTaskInfo remoteTaskInfo = mRunningTaskFetcher.getRunningTaskById(taskId);
        if (remoteTaskInfo == null) {
            Slog.w(TAG, "Could not create RemoteTaskInfo for task: " + taskId);
            return;
        }

        mTaskContinuityMessenger.sendMessage(new RemoteTaskAddedMessage(remoteTaskInfo));
    }

    @Override
    public void onTaskRemoved(int taskId) throws RemoteException {
        Slog.v(TAG, "onTaskRemoved: taskId=" + taskId);
        mTaskContinuityMessenger.sendMessage(new RemoteTaskRemovedMessage(taskId));
    }

    @Override
    public void onTaskMovedToFront(RunningTaskInfo taskInfo) throws RemoteException {
        Slog.v(TAG, "onTaskMovedToFront: taskId=" + taskInfo.taskId);

        sendTaskUpdatedMessage(taskInfo.taskId);
    }

    @Override
    public void onHandoffEnabledChanged(int taskId, boolean isHandoffEnabled) {
        Slog.v(TAG, "onHandoffEnabledChanged: taskId=" + taskId
                + ", isHandoffEnabled=" + isHandoffEnabled);

        sendTaskUpdatedMessage(taskId);
    }

    private void sendTaskUpdatedMessage(int taskId) {
        RemoteTaskInfo remoteTaskInfo = mRunningTaskFetcher.getRunningTaskById(taskId);
        if (remoteTaskInfo == null) {
            Slog.w(TAG, "Could not create RemoteTaskInfo for task: " + taskId);
            return;
        }

        RemoteTaskUpdatedMessage taskUpdatedMessage = new RemoteTaskUpdatedMessage(remoteTaskInfo);
        mTaskContinuityMessenger.sendMessage(taskUpdatedMessage);
    }
}