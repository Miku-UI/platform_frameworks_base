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

package com.android.server.wm;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_PACKAGE_UPDATE;
import static com.android.server.wm.RootWindowContainer.MATCH_ATTACHED_TASK_ONLY;

import android.annotation.Nullable;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.internal.content.PackageMonitor;
import com.android.internal.os.BackgroundThread;
import com.android.internal.protolog.ProtoLog;
import com.android.window.flags.Flags;

import java.util.ArrayList;
import java.util.Map;

/**
 * Manages the persistent state of tasks for a package that is undergoing an update.
 *
 * <p>This class stores the persistent state from a task's root activity *before* a package
 * update. This state is then retrieved when the task is relaunched *after* the update,
 * allowing the task to be restored. If the task is not relaunched after an update, task was not
 * visible when the update finished, then the state is still stored until the task is launched for
 * the next time.
 *
 * <p> State is stored on a per-task basis, mapped by package name.
 *
 * <p>Bundles are also managed based on the lifecycle of recent tasks:
 * <ul>
 *     <li>When a task is removed from recents (e.g., due to being trimmed or explicitly removed),
 *     the associated bundle is removed.</li>
 *     <li>If a task is removed because it's being replaced by a new task, the bundle is
 *     transferred to the new task.</li>
 * </ul>
 */
class PackageUpdateManager extends PackageMonitor implements RecentTasks.Callbacks {
    private final ActivityTaskManagerService mService;

    /**
     * A map to store tasks with persistent state for packages undergoing an update.
     * The outer map is keyed by {@code packageName}, and the value is another map of {@code taskId}
     * to
     * {@code persistentState} associated with it.
     */
    private final Map<String, Map<Integer, PersistableBundle>> mUpdatingPackageToPersistentTasks =
            new ArrayMap<>();

    /**
     * A map to store tasks for packages undergoing an update.
     * The map is keyed by {@code packageName}, and the value is {@code taskId} of the updating
     * tasks.
     */
    private final Map<String, ArrayList<Integer>> mUpdatingPackageToAllTasks =
            new ArrayMap<>();

    PackageUpdateManager(ActivityTaskManagerService service) {
        mService = service;
    }

    void onSystemReady() {
        if (!Flags.enableAppRestartAfterUpdate()) {
            return;
        }
        register(mService.mContext, UserHandle.ALL, BackgroundThread.getHandler());
        mService.getRecentTasks().registerCallback(this);
    }

    /**
     * Add a task for a package that can be used to provide a persistent state next time the root
     * activity of the task is being started.
     */
    void addPersistentTaskForPackage(String pkg, int taskId, PersistableBundle persistableBundle) {
        if (!Flags.enableAppRestartAfterUpdate()) {
            return;
        }
        ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE, "Adding pkg= %s with task %d to persistent tasks", pkg,
                taskId);
        mUpdatingPackageToPersistentTasks
                .computeIfAbsent(pkg, k -> new ArrayMap<>())
                .put(taskId, persistableBundle);
    }

    /**
     * Retrieves the correct persistent state to used for the start of the given task.
     * Returns null if no such state is found.
     */
    @Nullable
    PersistableBundle getPersistentStateForTask(Task task) {
        if (!Flags.enableAppRestartAfterUpdate()) {
            return null;
        }
        final String pkg = task.getBasePackageName();
        final Map<Integer, PersistableBundle> tasks = mUpdatingPackageToPersistentTasks.get(pkg);

        if (tasks == null) {
            ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE, "Couldn't find a persistent state for task= %d",
                    task.mTaskId);
            return null;
        }

        final PersistableBundle persistentStateMatchingTaskId = tasks.remove(task.mTaskId);

        // If the inner map is now empty, remove it from the outer map to prevent leaks.
        if (tasks.isEmpty()) {
            mUpdatingPackageToPersistentTasks.remove(pkg);
        }

        // If a matching persistent state was found.
        if (persistentStateMatchingTaskId != null) {
            ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE,
                    "Found a persistent state for task= %d through id match!", task.mTaskId);
            return persistentStateMatchingTaskId;
        }

        ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE,
                "Package found but couldn't find a persistent state for task= %d", task.mTaskId);

        // TODO: b/457451784 - Handle if a launch is using a new task.

        // TODO: b/457393309 - For multi-instance applications we should provide the correct
        // persistentState for the correct task

        // TODO: b/457453769 - Remove tasks when they are removed from recents
        return null;
    }

    void addUpdatingTasksForPackage(String pkg, ArraySet<Task> updatingTasks) {
        if (!Flags.enableAppRestartAfterUpdate()) {
            return;
        }
        ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE, "Adding updating tasks for pkg= %s", pkg);
        ArrayList<Integer> allTasks = mUpdatingPackageToAllTasks.computeIfAbsent(pkg,
                k -> new ArrayList<>());
        for (int i = 0; i < updatingTasks.size(); i++) {
            Task task = updatingTasks.valueAt(i);
            ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE, "Added task: %d", task.mTaskId);
            allTasks.add(task.mTaskId);
        }
    }

    @Override
    public void onPackageUpdateFinished(String packageName, int uid) {
        if (!Flags.enableAppRestartAfterUpdate()) {
            return;
        }
        synchronized (mService.mGlobalLock) {
            ArrayList<Integer> updatingTasks = mUpdatingPackageToAllTasks.remove(packageName);

            if (updatingTasks == null) {
                return;
            }

            ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE, "Package update finished for pkg= %s", packageName);
            ArrayList<Task> updatedTasks = new ArrayList<>();
            for (int i = 0; i < updatingTasks.size(); i++) {
                Task task = mService.mRootWindowContainer.anyTaskForId(updatingTasks.get(i),
                        MATCH_ATTACHED_TASK_ONLY);
                if (task != null) {
                    ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE, "Notifying for task: %d", task.mTaskId);
                    updatedTasks.add(task);
                } else {
                    ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE, "Previously updating task not found: %d",
                            updatingTasks.get(i));
                }
            }

            if (!updatedTasks.isEmpty()) {
                mService.mTaskOrganizerController.onPackageUpdateFinished(updatedTasks);
            }
        }
    }

    @Override
    public void onRecentTaskAdded(Task task) {}

    @Override
    public void onRecentTaskRemoved(Task task, boolean wasTrimmed, boolean killProcess,
            Task replacingTask) {
        if (!Flags.enableAppRestartAfterUpdate()) {
            return;
        }
        ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE, "onRecentTaskRemoved Removing task: %d", task.mTaskId);
        final String pkg = task.getBasePackageName();
        final Map<Integer, PersistableBundle> tasks = mUpdatingPackageToPersistentTasks.get(
                pkg);

        if (tasks == null) {
            ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE, "Couldn't find a persistent state for task= %d",
                    task.mTaskId);
            return;
        }
        PersistableBundle bundle = tasks.remove(task.mTaskId);

        // If a task is removed due to it being replaced by a new task, keep the same bundle for the
        // new task.
        if (shouldTransferBundle(task, replacingTask)) {
            ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE, "Moving bundle for task %d to task %d",
                    task.mTaskId, replacingTask.mTaskId);
            tasks.put(replacingTask.mTaskId, bundle);
        }

        if (tasks.isEmpty()) {
            mUpdatingPackageToPersistentTasks.remove(pkg);
        }
    }

    private boolean shouldTransferBundle(Task task, Task replacingTask) {
        if (replacingTask == null) {
            return false;
        }
        boolean hasSameBasePackage = task.getBasePackageName().equals(
                replacingTask.getBasePackageName());
        boolean hasSameAffinity = task.affinity != null & task.affinity.equals(
                replacingTask.affinity);
        boolean hasSameIntent = task.intent != null && replacingTask.intent != null
                && task.intent.getComponent().equals(replacingTask.intent.getComponent());
        ProtoLog.d(WM_DEBUG_PACKAGE_UPDATE,
                "hasSameBasePackage: %b, hasSameAffinity: %b, hasSameIntent: %b",
                hasSameBasePackage, hasSameAffinity, hasSameIntent);
        return hasSameBasePackage && hasSameAffinity && hasSameIntent;
    }
}
