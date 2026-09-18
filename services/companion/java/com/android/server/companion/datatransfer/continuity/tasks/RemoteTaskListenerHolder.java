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
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.datatransfer.continuity.RemoteTask;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A holder for {@link IRemoteTaskListener}s.
 *
 * <p>This class is responsible for notifying listeners when remote tasks change.
 */
public class RemoteTaskListenerHolder {

    private static final String TAG = RemoteTaskListenerHolder.class.getSimpleName();

    @GuardedBy("this")
    private RemoteCallbackList<IRemoteTaskListener> mListeners = null;

    @GuardedBy("this")
    private List<RemoteTask> mLastBroadcastedTasks = null;

    /**
     * Adds a listener to the holder and notifies it immediately.
     *
     * @param listener the listener to add
     */
    public void addListener(@NonNull IRemoteTaskListener listener) {
        synchronized (this) {
            Slog.v(TAG, "Addinging new listener.");
            if (mListeners == null) {
                Slog.v(TAG, "Listeners is null, creating new RemoteCallbackList.");
                mListeners = new RemoteCallbackList<>();
            }

            mListeners.register(Objects.requireNonNull(listener));
            notifyListener(listener);
        }
    }

    /**
     * Removes a listener from the holder.
     *
     * @param listener the listener to remove
     */
    public void removeListener(@NonNull IRemoteTaskListener listener) {
        synchronized (this) {
            Slog.v(TAG, "Removing listener.");
            mListeners.unregister(Objects.requireNonNull(listener));
            if (mListeners.getRegisteredCallbackCount() == 0) {
                Slog.v(TAG, "Listeners is empty, clearing RemoteCallbackList.");
                mListeners = null;
            }
        }
    }

    /**
     * Notifies all listeners that a task has been handed off, removing it from the list of remote
     * tasks until it is re-added by the origin device.
     *
     * @param associationId the id of the device to notify
     * @param taskId the id of the task that has been handed off
     */
    public void notifyTaskHandedOff(int associationId, int taskId) {
        synchronized (this) {
            Slog.v(
                    TAG,
                    "Removing task with associationId: "
                            + associationId
                            + " and taskId: "
                            + taskId);
            boolean didRemoveTask =
                    mLastBroadcastedTasks.removeIf(
                            task ->
                                    task.getCompanionDeviceAssociationId() == associationId
                                            && task.getTaskId() == taskId);

            if (didRemoveTask) {
                notifyAllListeners();
            } else {
                Slog.w(
                        TAG,
                        "Task with associationId: "
                                + associationId
                                + " and taskId: "
                                + taskId
                                + " not found.");
            }
        }
    }

    /**
     * Notify all listeners the list of remote tasks available for a specific association has
     * changed, removing all existing tasks for the association and adding the new ones.
     *
     * @param associationId the id of the device to notify
     * @param remoteTasks the list of tasks available on the device
     */
    public void notifyRemoteTasksChangedForAssociation(
            int associationId, @Nullable List<RemoteTask> remoteTasks) {
        synchronized (this) {
            if (mLastBroadcastedTasks != null) {
                mLastBroadcastedTasks.removeIf(
                        task -> task.getCompanionDeviceAssociationId() == associationId);

                if (remoteTasks != null) {
                    mLastBroadcastedTasks.addAll(remoteTasks);
                }

                if (mLastBroadcastedTasks.isEmpty()) {
                    mLastBroadcastedTasks = null;
                }
            } else if (remoteTasks != null && !remoteTasks.isEmpty()) {
                mLastBroadcastedTasks = new ArrayList<>(remoteTasks);
            }

            notifyAllListeners();
        }
    }

    /**
     * Notifies all listeners that the remote tasks have changed to the given list.
     *
     * @param remoteTasks the list of remote tasks that have changed
     */
    public void notifyAllRemoteTasksChanged(@Nullable List<RemoteTask> remoteTasks) {
        synchronized (this) {
            if (remoteTasks == null || remoteTasks.isEmpty()) {
                mLastBroadcastedTasks = null;
            } else {
                mLastBroadcastedTasks = new ArrayList<>(remoteTasks);
            }

            notifyAllListeners();
        }
    }

    private void notifyAllListeners() {
        synchronized (this) {
            if (mListeners == null) {
                Slog.v(TAG, "No listeners to notify");
                return;
            }

            mListeners.broadcast(listener -> notifyListener(listener));
        }
    }

    private void notifyListener(@NonNull IRemoteTaskListener listener) {
        synchronized (this) {
            try {
                if (mLastBroadcastedTasks != null) {
                    listener.onRemoteTasksChanged(mLastBroadcastedTasks);
                } else {
                    listener.onRemoteTasksChanged(new ArrayList<>());
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to notify listener: " + e.getMessage());
            }
        }
    }
}
