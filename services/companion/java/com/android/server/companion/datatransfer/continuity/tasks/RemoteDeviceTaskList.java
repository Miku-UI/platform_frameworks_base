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
import android.util.Slog;

import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;

import java.util.function.Consumer;
import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Objects;

/**
 * Tracks remote tasks currently available on a specific remote device.
 */
class RemoteDeviceTaskList {

    private static final String TAG = "RemoteDeviceTaskList";

    private final int mAssociationId;
    private final String mDeviceName;
    private final Consumer<RemoteTask> mOnMostRecentTaskChangedListener;
    private final PriorityQueue<RemoteTaskInfo> mTasks;

    RemoteDeviceTaskList(
        int associationId,
        @NonNull String deviceName,
        @NonNull Consumer<RemoteTask> onMostRecentTaskChangedListener) {

        mAssociationId = associationId;
        mDeviceName = Objects.requireNonNull(deviceName);
        mOnMostRecentTaskChangedListener = Objects.requireNonNull(onMostRecentTaskChangedListener);
        mTasks = new PriorityQueue<>(new Comparator<RemoteTaskInfo>() {
            @Override
            public int compare(RemoteTaskInfo task1, RemoteTaskInfo task2) {
                long lastUsedTime1 = task1.lastUsedTimeMillis();
                long lastUsedTime2 = task2.lastUsedTimeMillis();
                if (lastUsedTime1 < lastUsedTime2) {
                    return 1;
                } else if (lastUsedTime1 > lastUsedTime2) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });
    }

    /**
     * Returns the association ID of the remote device.
     */
    int getAssociationId() {
        return mAssociationId;
    }

    /**
     * Returns the device name of the remote device.
     */
    @NonNull
    String getDeviceName() {
        return mDeviceName;
    }

    /**
     * Adds a task to the list of tasks currently available on the remote
     * device.
     */
    void addTask(@NonNull RemoteTaskInfo taskInfo) {
        Objects.requireNonNull(taskInfo);

        synchronized (mTasks) {
            Slog.v(TAG, "Adding task: " + taskInfo.id() + " to association: " + mAssociationId);
            int previousTopTaskId
                = mTasks.peek() == null ? -1 : mTasks.peek().id();

            mTasks.add(taskInfo);
            if (taskInfo.id() != previousTopTaskId) {
                Slog.v(
                    TAG,
                    "Notifying most recent task changed for association: " + mAssociationId);
                mOnMostRecentTaskChangedListener.accept(getMostRecentTask());
            }
        }
    }

    /**
     * Sets the list of tasks currently available on the remote device.
     */
    void setTasks(@NonNull Collection<RemoteTaskInfo> tasks) {
        Objects.requireNonNull(tasks);

        synchronized (mTasks) {
            Slog.v(TAG, "Setting remote tasks for association: " + mAssociationId);
            mTasks.clear();
            mTasks.addAll(tasks);
            mOnMostRecentTaskChangedListener.accept(getMostRecentTask());
        }
    }

    /**
     * Removes a task from the list of tasks currently available on the remote device.
     */
    void removeTask(int taskId) {
        synchronized (mTasks) {
            Slog.v(TAG, "Removing task: " + taskId + " for association: " + mAssociationId);
            boolean shouldNotifyListeners
                = (mTasks.peek() != null && mTasks.peek().id() == taskId);
            mTasks.removeIf(task -> task.id() == taskId);
            if (shouldNotifyListeners) {
                Slog.v(
                    TAG,
                    "Notifying most recent task changed for association: " + mAssociationId);
                mOnMostRecentTaskChangedListener.accept(getMostRecentTask());
            }
        }
    }

    // Replaces tasks with the same ID as provided and notifies listeners.
    void updateTask(@NonNull RemoteTaskInfo taskInfo) {
        Objects.requireNonNull(taskInfo);

        synchronized(mTasks) {
            Slog.v(
                TAG,
                "Updating task: " + taskInfo.id() + " for association: " + mAssociationId);
            int previousTopTaskId
                = mTasks.peek() == null ? -1 : mTasks.peek().id();
            mTasks.removeIf(task -> task.id() == taskInfo.id());
            mTasks.add(taskInfo);
            boolean isTopTaskDifferent = previousTopTaskId != mTasks.peek().id();
            boolean didTopTaskChange
                = mTasks.peek() != null && mTasks.peek().id() == taskInfo.id();
            boolean shouldNotifyListeners = isTopTaskDifferent || didTopTaskChange;
            if (shouldNotifyListeners) {
                Slog.v(
                    TAG,
                    "Notifying most recent task changed for association: " + mAssociationId);
                mOnMostRecentTaskChangedListener.accept(getMostRecentTask());
            }
        }
    }

    /**
     * Gets the most recently used task on this device, or null if there are no
     * tasks.
     */
    @Nullable
    RemoteTask getMostRecentTask() {
        synchronized (mTasks) {
            Slog.v(TAG, "Getting most recent task for association: " + mAssociationId);
            if (mTasks.isEmpty()) {
                return null;
            }

            return mTasks.peek().toRemoteTask(mAssociationId, mDeviceName);
        }
    }
}