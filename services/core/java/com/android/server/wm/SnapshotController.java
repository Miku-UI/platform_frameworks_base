/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.os.Trace.TRACE_TAG_WINDOW_MANAGER;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_CLOSE_PREPARE_BACK_NAVIGATION;
import static android.view.WindowManager.TRANSIT_FIRST_CUSTOM;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.server.wm.RootWindowContainer.MATCH_ATTACHED_TASK_OR_RECENT_TASKS;

import android.hardware.HardwareBuffer;
import android.os.Binder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.Slog;
import android.view.WindowManager;
import android.window.ITaskSnapshotListener;
import android.window.ITaskSnapshotManager;
import android.window.TaskSnapshot;
import android.window.TaskSnapshotManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.window.flags.Flags;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Integrates common functionality from TaskSnapshotController and ActivitySnapshotController.
 */
class SnapshotController {
    private final SnapshotPersistQueue mSnapshotPersistQueue;
    final TaskSnapshotController mTaskSnapshotController;
    final ActivitySnapshotController mActivitySnapshotController;
    private final WindowManagerService mService;
    private final ArrayList<WeakReference<HardwareBuffer>> mObsoleteSnapshots = new ArrayList<>();
    final SnapshotManagerService mSnapshotManagerService = new SnapshotManagerService();
    private static final String TAG = AbsAppSnapshotController.TAG;
    SnapshotController(WindowManagerService wms) {
        mService = wms;
        mSnapshotPersistQueue = new SnapshotPersistQueue();
        mTaskSnapshotController = new TaskSnapshotController(wms, mSnapshotPersistQueue);
        mActivitySnapshotController = new ActivitySnapshotController(wms, mSnapshotPersistQueue);
        final Consumer<HardwareBuffer> releaser = hb -> {
            mService.mH.post(() -> {
                synchronized (mService.mGlobalLock) {
                    if (hb.isClosed()) {
                        return;
                    }
                    if (mService.mAtmService.getTransitionController().inTransition()) {
                        mObsoleteSnapshots.add(new WeakReference<>(hb));
                    } else {
                        hb.close();
                    }
                }
            });
        };
        mTaskSnapshotController.setSnapshotReleaser(releaser);
        mActivitySnapshotController.setSnapshotReleaser(releaser);
    }

    void systemReady() {
        mSnapshotPersistQueue.systemReady();
    }

    void setPause(boolean paused) {
        mSnapshotPersistQueue.setPaused(paused);
    }

    void onAppRemoved(ActivityRecord activity) {
        mTaskSnapshotController.onAppRemoved(activity);
        mActivitySnapshotController.onAppRemoved(activity);
    }

    void onAppDied(ActivityRecord activity) {
        mTaskSnapshotController.onAppDied(activity);
        mActivitySnapshotController.onAppDied(activity);
    }

    void notifyAppVisibilityChanged(ActivityRecord appWindowToken, boolean visible) {
        mActivitySnapshotController.notifyAppVisibilityChanged(appWindowToken, visible);
    }

    // For shell transition, record snapshots before transaction start.
    void onTransactionReady(@WindowManager.TransitionType int type,
            ArrayList<Transition.ChangeInfo> changeInfos) {
        final boolean isTransitionOpen = isTransitionOpen(type);
        final boolean isTransitionClose = isTransitionClose(type);
        if (!isTransitionOpen && !isTransitionClose && type < TRANSIT_FIRST_CUSTOM) {
            return;
        }
        ActivitiesByTask activityTargets = null;
        for (int i = changeInfos.size() - 1; i >= 0; --i) {
            Transition.ChangeInfo info = changeInfos.get(i);
            // Intentionally skip record snapshot for changes originated from PiP.
            if (info.mWindowingMode == WINDOWING_MODE_PINNED) continue;
            if (info.mContainer.isActivityTypeHome()) continue;
            final Task task = info.mContainer.asTask();
            // Note that if this task is being transiently hidden, the snapshot will be captured at
            // the end of the transient transition (see Transition#finishTransition()), because IME
            // won't move be moved during the transition and the tasks are still live.
            // Also don't take the snapshot if there is a bounds change in a visible to invisible
            // transition as the app won't redraw. This can happen when a task is moving from one
            // display to another.
            if (task != null && !task.mCreatedByOrganizer && !task.isVisibleRequested()
                    && !task.mTransitionController.isTransientHide(task)
                    && task.getBounds().equals(info.mAbsoluteBounds)) {
                mTaskSnapshotController.recordSnapshot(task, info);
            }
            // Won't need to capture activity snapshot in close transition.
            if (isTransitionClose) {
                continue;
            }
            if (info.mContainer.asActivityRecord() != null
                    || info.mContainer.asTaskFragment() != null) {
                final TaskFragment tf = info.mContainer.asTaskFragment();
                final ActivityRecord ar = tf != null ? tf.getTopMostActivity()
                        : info.mContainer.asActivityRecord();
                if (ar != null && ar.getTask().isVisibleRequested()) {
                    if (activityTargets == null) {
                        activityTargets = new ActivitiesByTask();
                    }
                    activityTargets.put(ar);
                }
            }
        }
        if (activityTargets != null) {
            activityTargets.recordSnapshot(mActivitySnapshotController);
        }
    }

    private static class ActivitiesByTask {
        final ArrayMap<Task, OpenCloseActivities> mActivitiesMap = new ArrayMap<>();

        void put(ActivityRecord ar) {
            OpenCloseActivities activities = mActivitiesMap.get(ar.getTask());
            if (activities == null) {
                activities = new OpenCloseActivities();
                mActivitiesMap.put(ar.getTask(), activities);
            }
            activities.add(ar);
        }

        void recordSnapshot(ActivitySnapshotController controller) {
            for (int i = mActivitiesMap.size() - 1; i >= 0; i--) {
                final OpenCloseActivities pair = mActivitiesMap.valueAt(i);
                pair.recordSnapshot(controller);
            }
        }

        static class OpenCloseActivities {
            final ArrayList<ActivityRecord> mOpenActivities = new ArrayList<>();
            final ArrayList<ActivityRecord> mCloseActivities = new ArrayList<>();

            void add(ActivityRecord ar) {
                final ArrayList<ActivityRecord> targetList = ar.isVisibleRequested()
                        ? mOpenActivities : mCloseActivities;
                if (!targetList.contains(ar)) {
                    targetList.add(ar);
                }
            }

            boolean allOpensOptInOnBackInvoked() {
                if (mOpenActivities.isEmpty()) {
                    return false;
                }
                for (int i = mOpenActivities.size() - 1; i >= 0; --i) {
                    if (!mOpenActivities.get(i).mOptInOnBackInvoked) {
                        return false;
                    }
                }
                return true;
            }

            void recordSnapshot(ActivitySnapshotController controller) {
                if (!allOpensOptInOnBackInvoked() || mCloseActivities.isEmpty()) {
                    return;
                }
                controller.recordSnapshot(mCloseActivities);
            }
        }
    }

    void onTransitionFinish(@WindowManager.TransitionType int type,
            ArrayList<Transition.ChangeInfo> changeInfos) {
        final boolean isTransitionOpen = isTransitionOpen(type);
        final boolean isTransitionClose = isTransitionClose(type);
        if (!isTransitionOpen && !isTransitionClose && type < TRANSIT_FIRST_CUSTOM
                || (changeInfos.isEmpty())) {
            closeObsoleteSnapshots();
            return;
        }
        Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "SnapshotController_analysis");
        mActivitySnapshotController.beginSnapshotProcess();
        final ArrayList<WindowContainer> windows = new ArrayList<>();
        for (int i = changeInfos.size() - 1; i >= 0; --i) {
            final WindowContainer wc = changeInfos.get(i).mContainer;
            if (wc.asTask() == null && wc.asTaskFragment() == null
                    && wc.asActivityRecord() == null) {
                continue;
            }
            windows.add(wc);
        }
        mActivitySnapshotController.handleTransitionFinish(windows);
        mActivitySnapshotController.endSnapshotProcess();
        // Remove task snapshot if it is visible at the end of transition, except for PiP.
        for (int i = changeInfos.size() - 1; i >= 0; --i) {
            final WindowContainer wc = changeInfos.get(i).mContainer;
            final Task task = wc.asTask();
            if (task != null && wc.isVisibleRequested() && !task.inPinnedWindowingMode()) {
                final TaskSnapshot snapshot;
                if (Flags.reduceTaskSnapshotMemoryUsage()) {
                    snapshot = mTaskSnapshotController.getSnapshot(task.mTaskId,
                            TaskSnapshotManager.RESOLUTION_ANY);
                } else {
                    snapshot = mTaskSnapshotController.getSnapshot(task.mTaskId,
                            false /* isLowResolution */);
                }
                if (snapshot != null) {
                    mTaskSnapshotController.removeAndDeleteSnapshot(task.mTaskId, task.mUserId);
                }
            }
        }
        closeObsoleteSnapshots();
        Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
    }

    private void closeObsoleteSnapshots() {
        if (mObsoleteSnapshots.isEmpty()) {
            return;
        }
        for (int i = mObsoleteSnapshots.size() - 1; i >= 0; --i) {
            final HardwareBuffer hb = mObsoleteSnapshots.remove(i).get();
            if (hb != null && !hb.isClosed()) {
                hb.close();
            }
        }
    }

    private static boolean isTransitionOpen(int type) {
        return type == TRANSIT_OPEN || type == TRANSIT_TO_FRONT
                || type == TRANSIT_PREPARE_BACK_NAVIGATION;
    }
    private static boolean isTransitionClose(int type) {
        return type == TRANSIT_CLOSE || type == TRANSIT_TO_BACK
                || type == TRANSIT_CLOSE_PREPARE_BACK_NAVIGATION;
    }

    void dump(PrintWriter pw, String prefix) {
        mTaskSnapshotController.dump(pw, prefix);
        mActivitySnapshotController.dump(pw, prefix);
        mSnapshotPersistQueue.dump(pw, prefix);
    }

    @VisibleForTesting
    TaskSnapshot getTaskSnapshotInner(int taskId, Task task, long latestCaptureTime,
            @TaskSnapshotManager.Resolution int retrieveResolution) {
        final boolean requestLowResolution =
                retrieveResolution == TaskSnapshotManager.RESOLUTION_LOW;
        TaskSnapshot inCacheSnapshot;
        boolean convertToLow;
        synchronized (mService.mGlobalLock) {
            inCacheSnapshot = mTaskSnapshotController.getSnapshot(
                    taskId, retrieveResolution);
            if (inCacheSnapshot != null) {
                if (inCacheSnapshot.getCaptureTime() > latestCaptureTime) {
                    inCacheSnapshot.addReference(TaskSnapshot.REFERENCE_WRITE_TO_PARCEL);
                    return inCacheSnapshot;
                } else {
                    return null;
                }
            }
            inCacheSnapshot = mTaskSnapshotController.getSnapshot(
                    taskId, TaskSnapshotManager.RESOLUTION_ANY);
            if (latestCaptureTime > 0 && inCacheSnapshot != null) {
                // return null if the client already has the latest snapshot.
                if (inCacheSnapshot.getCaptureTime() <= latestCaptureTime) {
                    return null;
                }
            }
            convertToLow = requestLowResolution && mTaskSnapshotController.mOnlyCacheLowResSnapshot
                    && inCacheSnapshot != null && !inCacheSnapshot.isLowResolution();
            if (convertToLow) {
                inCacheSnapshot.addReference(TaskSnapshot.REFERENCE_CONVERT_RESOLUTION);
            }
        }

        if (convertToLow) {
            final TaskSnapshot convertLowResSnapshot =
                    convertToLowResSnapshot(taskId, inCacheSnapshot);
            if (convertLowResSnapshot != null) {
                convertLowResSnapshot.addReference(TaskSnapshot.REFERENCE_WRITE_TO_PARCEL);
                return convertLowResSnapshot;
            }
        }
        // Don't call this while holding the lock as this operation might hit the disk.
        return mTaskSnapshotController.getSnapshotFromDisk(taskId,
                task.mUserId, requestLowResolution, TaskSnapshot.REFERENCE_WRITE_TO_PARCEL);
    }

    /**
     * Converts a high-resolution snapshot to a low-resolution snapshot.
     *
     * <p>Note: If the snapshot is in the cache, a {@link TaskSnapshot#REFERENCE_CONVERT_RESOLUTION}
     * reference should be added. This is to prevent the high-resolution snapshot
     * from being released as soon as it is replaced by the newly-created low-resolution snapshot.
     * </p>
     *
     * @param snapshot The high resolution snapshot.
     */
    TaskSnapshot convertToLowResSnapshot(int taskId, TaskSnapshot snapshot) {
        try {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "createLowResSnapshot");
            final TaskSnapshot lowResSnapshot = mTaskSnapshotController
                    .createLowResSnapshot(snapshot);
            if (lowResSnapshot != null) {
                mSnapshotPersistQueue.updateKnownLowResSnapshotIfPossible(taskId, lowResSnapshot);
            }
            return lowResSnapshot;
        } finally {
            snapshot.removeReference(TaskSnapshot.REFERENCE_CONVERT_RESOLUTION);
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }
    }

    void notifySnapshotChanged(int taskId, TaskSnapshot snapshot) {
        mSnapshotManagerService.notifySnapshotChanged(taskId, snapshot);
    }

    void notifySnapshotInvalidate(int taskId) {
        mSnapshotManagerService.notifySnapshotInvalidate(taskId);
    }

    class SnapshotManagerService extends ITaskSnapshotManager.Stub {

        @Override
        public TaskSnapshot getTaskSnapshot(int taskId, long latestCaptureTime,
                @TaskSnapshotManager.Resolution int retrieveResolution) {
            final long ident = Binder.clearCallingIdentity();
            try {
                TaskSnapshotManager.validateResolution(retrieveResolution);
                final Task task;
                synchronized (mService.mGlobalLock) {
                    task = mService.mRoot.anyTaskForId(taskId,
                            MATCH_ATTACHED_TASK_OR_RECENT_TASKS);
                    if (task == null) {
                        Slog.w(TAG, "getTaskSnapshot: taskId=" + taskId + " not found");
                        return null;
                    }
                }
                return SnapshotController.this.getTaskSnapshotInner(taskId, task, latestCaptureTime,
                        retrieveResolution);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public TaskSnapshot takeTaskSnapshot(int taskId, boolean updateCache,
                boolean lowResolution) {
            final long ident = Binder.clearCallingIdentity();
            try {
                Supplier<TaskSnapshot> supplier = null;
                TaskSnapshot freshSnapshot = null;
                final Task task;
                final boolean convertToLow = lowResolution
                        && mTaskSnapshotController.mOnlyCacheLowResSnapshot;
                synchronized (mService.mGlobalLock) {
                    task = mService.mRoot.anyTaskForId(taskId,
                            MATCH_ATTACHED_TASK_OR_RECENT_TASKS);
                    if (task == null || !task.isVisible()) {
                        Slog.w(TAG, "takeTaskSnapshot: taskId=" + taskId
                                + " not found or not visible");
                        return null;
                    }
                    // Note that if updateCache is true, ActivityRecord#shouldUseAppThemeSnapshot
                    // will be used to decide whether the task is allowed to be captured because
                    // that may be retrieved by recents. While if updateCache is false, the real
                    // snapshot will always be taken and the snapshot won't be put into
                    // SnapshotPersister.
                    if (updateCache) {
                        supplier = mTaskSnapshotController.getRecordSnapshotSupplier(task,
                                convertToLow
                                        ? TaskSnapshot.REFERENCE_CONVERT_RESOLUTION
                                        : TaskSnapshot.REFERENCE_WRITE_TO_PARCEL);
                    } else {
                        freshSnapshot = mTaskSnapshotController.snapshot(task);
                    }
                }
                // Don't call supplier.get while holding the lock.
                if (freshSnapshot == null && supplier != null) {
                    freshSnapshot = supplier.get();
                }
                if (freshSnapshot == null) {
                    return null;
                }
                if (convertToLow) {
                    final TaskSnapshot convert = SnapshotController.this
                            .convertToLowResSnapshot(taskId, freshSnapshot);
                    if (convert != null) {
                        convert.addReference(TaskSnapshot.REFERENCE_WRITE_TO_PARCEL);
                        return convert;
                    }
                }
                freshSnapshot.addReference(TaskSnapshot.REFERENCE_WRITE_TO_PARCEL);
                return freshSnapshot;
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        /** Sets the task snapshot listener that gets callbacks when a task changes. */
        @Override
        public void registerTaskSnapshotListener(ITaskSnapshotListener listener) {
            ActivityTaskManagerService.enforceTaskPermission("registerTaskStackListener()");
            mRemoteTaskSnapshotListeners.register(listener);
        }

        /** Unregister a task snapshot listener so that it stops receiving callbacks. */
        @Override
        public void unregisterTaskSnapshotListener(ITaskSnapshotListener listener) {
            ActivityTaskManagerService.enforceTaskPermission("unregisterTaskStackListener()");
            mRemoteTaskSnapshotListeners.unregister(listener);
        }

        private interface TaskSnapshotConsumer {
            void accept(ITaskSnapshotListener t) throws RemoteException;
        }

        void notifySnapshotChanged(int taskId, TaskSnapshot snapshot) {
            snapshot.addReference(TaskSnapshot.REFERENCE_BROADCAST);
            mService.mH.post(() -> {
                forAllRemoteListeners(l -> l.onTaskSnapshotChanged(taskId, snapshot));
                snapshot.removeReference(TaskSnapshot.REFERENCE_BROADCAST);
            });
        }

        void notifySnapshotInvalidate(int taskId) {
            mService.mH.post(() -> forAllRemoteListeners(l ->
                    l.onTaskSnapshotInvalidated(taskId)));
        }

        /**
         * Task snapshot listeners in remote processes.
         * <p>
         * Note that mRemoteTaskSnapshotListeners can be modified on any thread, but it can only be
         * invoked on the handler thread of {@link WindowManagerService#mH}.
         */
        private final RemoteCallbackList<ITaskSnapshotListener> mRemoteTaskSnapshotListeners =
                new RemoteCallbackList<>();

        /**
         * Iterates through all the registered remote listeners and executes the provided callback
         * for each of them.
         * <p>
         * Note: {@link RemoteCallbackList#beginBroadcast()} must be called on the handler thread
         *       of {@link WindowManagerService#mH}.
         *
         * @param callback The callback to execute for each remote listener.
         */
        private void forAllRemoteListeners(TaskSnapshotConsumer callback) {
            for (int i = mRemoteTaskSnapshotListeners.beginBroadcast() - 1; i >= 0; i--) {
                try {
                    // Make a one-way callback to the listener
                    callback.accept(mRemoteTaskSnapshotListeners.getBroadcastItem(i));
                } catch (RemoteException e) {
                    // Handled by the RemoteCallbackList.
                }
            }
            mRemoteTaskSnapshotListeners.finishBroadcast();
        }
    }
}
