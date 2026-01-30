/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.wm;

import static android.os.Trace.TRACE_TAG_WINDOW_MANAGER;
import static android.window.TaskSnapshot.REFERENCE_NONE;

import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_SCREENSHOT;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Environment;
import android.os.Handler;
import android.os.Trace;
import android.util.ArraySet;
import android.util.Slog;
import android.view.Display;
import android.window.ScreenCapture.ScreenCaptureParams;
import android.window.ScreenCaptureInternal;
import android.window.TaskSnapshot;
import android.window.TaskSnapshotManager;

import com.android.server.policy.WindowManagerPolicy.ScreenOffListener;
import com.android.server.wm.BaseAppSnapshotPersister.PersistInfoProvider;
import com.android.window.flags.Flags;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * When an app token becomes invisible, we take a snapshot (bitmap) of the corresponding task and
 * put it into our cache. Internally we use gralloc buffers to be able to draw them wherever we
 * like without any copying.
 * <p>
 * System applications may retrieve a snapshot to represent the current state of a task, and draw
 * them in their own process.
 * <p>
 * When we task becomes visible again, we show a starting window with the snapshot as the content to
 * make app transitions more responsive.
 * <p>
 * To access this class, acquire the global window manager lock.
 */
class TaskSnapshotController extends AbsAppSnapshotController<Task, TaskSnapshotCache> {
    static final String SNAPSHOTS_DIRNAME = "snapshots";

    private final TaskSnapshotPersister mPersister;
    private final ArraySet<Task> mTmpTasks = new ArraySet<>();
    private final Handler mHandler = new Handler();

    private final PersistInfoProvider mPersistInfoProvider;
    final boolean mOnlyCacheLowResSnapshot;

    TaskSnapshotController(WindowManagerService service, SnapshotPersistQueue persistQueue) {
        super(service);
        final boolean snapshotEnabled =
                !service.mContext
                        .getResources()
                        .getBoolean(com.android.internal.R.bool.config_disableTaskSnapshots);
        setSnapshotEnabled(snapshotEnabled);
        mPersistInfoProvider = createPersistInfoProvider(service,
                Environment::getDataSystemCeDirectory);

        mPersister = new TaskSnapshotPersister(
                persistQueue,
                mPersistInfoProvider,
                shouldDisableSnapshots());
        initialize(new TaskSnapshotCache(new AppSnapshotLoader(mPersistInfoProvider)));
        mOnlyCacheLowResSnapshot = Flags.reduceTaskSnapshotMemoryUsage()
                && Flags.respectRequestedTaskSnapshotResolution()
                && mPersistInfoProvider.enableLowResSnapshots();
    }

    static PersistInfoProvider createPersistInfoProvider(WindowManagerService service,
            BaseAppSnapshotPersister.DirectoryResolver resolver) {
        final float highResTaskSnapshotScale = service.mContext.getResources().getFloat(
                com.android.internal.R.dimen.config_highResTaskSnapshotScale);
        final float lowResTaskSnapshotScale = service.mContext.getResources().getFloat(
                com.android.internal.R.dimen.config_lowResTaskSnapshotScale);

        if (lowResTaskSnapshotScale < 0 || 1 <= lowResTaskSnapshotScale) {
            throw new RuntimeException("Low-res scale must be between 0 and 1");
        }
        if (highResTaskSnapshotScale <= 0 || 1 < highResTaskSnapshotScale) {
            throw new RuntimeException("High-res scale must be between 0 and 1");
        }
        if (highResTaskSnapshotScale <= lowResTaskSnapshotScale) {
            throw new RuntimeException("High-res scale must be greater than low-res scale");
        }

        final float lowResScaleFactor;
        final boolean enableLowResSnapshots;
        if (lowResTaskSnapshotScale > 0) {
            lowResScaleFactor = lowResTaskSnapshotScale / highResTaskSnapshotScale;
            enableLowResSnapshots = true;
        } else {
            lowResScaleFactor = 0;
            enableLowResSnapshots = false;
        }
        final boolean use16BitFormat = service.mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_use16BitTaskSnapshotPixelFormat);
        return new PersistInfoProvider(resolver, SNAPSHOTS_DIRNAME,
                enableLowResSnapshots, lowResScaleFactor, use16BitFormat);
    }

    void snapshotTasks(ArraySet<Task> tasks) {
        for (int i = tasks.size() - 1; i >= 0; i--) {
            recordSnapshot(tasks.valueAt(i));
        }
    }

    /**
     * The attributes of task snapshot are based on task configuration. But sometimes the
     * configuration may have been changed during a transition, so supply the ChangeInfo that
     * stored the previous appearance of the closing task.
     *
     * The snapshot won't be created immediately if it should be captured as fake snapshot.
     */
    void recordSnapshot(Task task, Transition.ChangeInfo changeInfo) {
        mCurrentChangeInfo = changeInfo;
        try {
            recordSnapshot(task);
        } finally {
            mCurrentChangeInfo = null;
        }
    }

    void recordSnapshot(Task task) {
        if (shouldDisableSnapshots()) {
            return;
        }
        final SnapshotSupplier supplier = getRecordSnapshotSupplier(task, REFERENCE_NONE);
        if (supplier == null) {
            return;
        }
        final int mode = getSnapshotMode(task);
        if (mode == SNAPSHOT_MODE_APP_THEME) {
            mService.mH.post(supplier::handleSnapshot);
        } else {
            supplier.handleSnapshot();
        }
    }

    /**
     * Note that the snapshot is not created immediately, if the returned supplier is non-null, the
     * caller must call {@link AbsAppSnapshotController.SnapshotSupplier#get} or
     * {@link AbsAppSnapshotController.SnapshotSupplier#handleSnapshot} to complete the entire
     * record request.
     */
    SnapshotSupplier getRecordSnapshotSupplier(Task task,
            @TaskSnapshot.ReferenceFlags int initialUsage) {
        return recordSnapshotInner(task, true /* allowAppTheme */, snapshot -> {
            if (initialUsage != REFERENCE_NONE) {
                snapshot.addReference(initialUsage);
            }
            if (!task.isActivityTypeHome()) {
                final var updateCacheFunction = mOnlyCacheLowResSnapshot
                        ? updateLowResToCacheFunction(task, snapshot.getId()) : null;
                mPersister.persistSnapshotAndConvert(
                        task.mTaskId, task.mUserId, snapshot, updateCacheFunction);
                task.onSnapshotChanged(snapshot);
            }
        });
    }

    private Consumer<BaseAppSnapshotPersister.LowResSnapshotSupplier> updateLowResToCacheFunction(
            Task task, long snapshotId) {
        return supplier -> mHandler.post(() -> {
            boolean abort = false;
            synchronized (mService.mGlobalLock) {
                if (!task.isAttached()) {
                    abort = true;
                } else {
                    final TaskSnapshot previous = mCache.getSnapshot(task.mTaskId,
                            TaskSnapshotManager.RESOLUTION_ANY, TaskSnapshot.REFERENCE_NONE);
                    if (previous == null || previous.isLowResolution()
                            || previous.getId() != snapshotId) {
                        abort = true;
                    }
                }
            }
            if (abort) {
                supplier.abort();
                return;
            }
            try {
                Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "updateLowSnapshotToCache");
                final TaskSnapshot converted = supplier.getLowResSnapshot();
                if (converted == null) {
                    return;
                }
                synchronized (mService.mGlobalLock) {
                    if (!task.isAttached()
                            || !updateCacheWithLowResSnapshotIfNeeded(task, converted)) {
                        converted.closeBuffer();
                    }
                }
            } finally {
                Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            }
        });
    }

    /**
     * Updates the cache with the low-resolution snapshot if the existing snapshot in the cache
     * is a high-resolution snapshot with the same id.
     *
     * @param task The task associated with the snapshot.
     * @param lowSnapshot The low-resolution snapshot to update the cache with.
     * @return {@code true} if the cache was updated, {@code false} otherwise.
     */
    boolean updateCacheWithLowResSnapshotIfNeeded(Task task, TaskSnapshot lowSnapshot) {
        final TaskSnapshot tmp = getSnapshot(
                task.mTaskId, TaskSnapshotManager.RESOLUTION_ANY);
        if (tmp != null && !tmp.isLowResolution() && tmp.getId() == lowSnapshot.getId()) {
            mCache.putSnapshot(task, lowSnapshot);
            return true;
        }
        return false;
    }

    /**
     * Retrieves a snapshot from cache.
     * @deprecated Use {@link #getSnapshot(int, int)}
     */
    @Deprecated
    @Nullable
    TaskSnapshot getSnapshot(int taskId, boolean isLowResolution) {
        return getSnapshot(taskId, false /* isLowResolution */, REFERENCE_NONE);
    }

    /**
     * Retrieves a snapshot from cache.
     */
    @Nullable
    TaskSnapshot getSnapshot(int taskId,
            @TaskSnapshotManager.Resolution int retrieveResolution) {
        return getSnapshot(taskId, retrieveResolution /* retrieveResolution */,
                TaskSnapshot.REFERENCE_NONE);
    }

    /**
     * Retrieves a snapshot from cache.
     * @deprecated Use {@link #getSnapshot(int, int, int)}
     */
    @Deprecated
    @Nullable
    TaskSnapshot getSnapshot(int taskId, boolean isLowResolution,
            @TaskSnapshot.ReferenceFlags int usage) {
        return mCache.getSnapshot(taskId, isLowResolution
                && mPersistInfoProvider.enableLowResSnapshots(), usage);
    }

    /**
     * Retrieves a snapshot from cache.
     */
    @Nullable
    TaskSnapshot getSnapshot(int taskId,
            @TaskSnapshotManager.Resolution int retrieveResolution,
            @TaskSnapshot.ReferenceFlags int usage) {
        if (retrieveResolution == TaskSnapshotManager.RESOLUTION_LOW
                && !mPersistInfoProvider.enableLowResSnapshots()) {
            retrieveResolution = TaskSnapshotManager.RESOLUTION_HIGH;
        }
        return mCache.getSnapshot(taskId, retrieveResolution, usage);
    }

    /**
     * Retrieves a snapshot from disk.
     * DO NOT HOLD THE WINDOW MANAGER LOCK WHEN CALLING THIS METHOD!
     */
    @Nullable
    TaskSnapshot getSnapshotFromDisk(int taskId, int userId,
            boolean isLowResolution, @TaskSnapshot.ReferenceFlags int usage) {
        return mCache.getSnapshotFromDisk(taskId, userId, isLowResolution
                && mPersistInfoProvider.enableLowResSnapshots(), usage);
    }

    /**
     * Creates a low-resolution snapshot from a given high-resolution snapshot.
     * This is only valid if persisting low-resolution snapshots is enabled.
     *
     * @param inCacheSnapshot The high-resolution snapshot from cache which to create the
     *                        low-resolution version.
     * @return The low-resolution snapshot, or {@code null} if persisting low-resolution snapshots
     *         is not enabled.
     */
    TaskSnapshot createLowResSnapshot(TaskSnapshot inCacheSnapshot) {
        if (!mPersistInfoProvider.enableLowResSnapshots()) {
            return null;
        }
        return TaskSnapshotConvertUtil.convertSnapshotToLowRes(
                inCacheSnapshot, mPersistInfoProvider.lowResScaleFactor());
    }

    /**
     * Returns the elapsed real time (in nanoseconds) at which a snapshot for the given task was
     * last taken, or -1 if no such snapshot exists for that task.
     */
    long getSnapshotCaptureTime(int taskId) {
        final TaskSnapshot snapshot;
        if (Flags.reduceTaskSnapshotMemoryUsage()) {
            snapshot = mCache.getSnapshot(taskId, TaskSnapshotManager.RESOLUTION_ANY,
                    TaskSnapshot.REFERENCE_NONE);
        } else {
            snapshot = mCache.getSnapshot(taskId, false /* isLowResolution */);
        }
        if (snapshot != null) {
            return snapshot.getCaptureTime();
        }
        return -1;
    }

    /**
     * @see WindowManagerInternal#clearSnapshotCache
     */
    public void clearSnapshotCache() {
        mCache.clearRunningCache();
    }

    /**
     * Find the window for a given task to take a snapshot. Top child of the task is usually the one
     * we're looking for, but during app transitions, trampoline activities can appear in the
     * children, which should be ignored.
     */
    @Nullable protected ActivityRecord findAppTokenForSnapshot(Task task) {
        return task.getActivity(ActivityRecord::canCaptureSnapshot);
    }


    @Override
    protected boolean use16BitFormat() {
        return mPersistInfoProvider.use16BitFormat();
    }

    @Nullable
    private ScreenCaptureInternal.ScreenshotHardwareBuffer createImeScreenshot(
            @NonNull Task task, @PixelFormat.Format int pixelFormat) {
        if (task.getSurfaceControl() == null) {
            if (DEBUG_SCREENSHOT) {
                Slog.w(TAG_WM, "Failed to create IME screenshot. No surface control for " + task);
            }
            return null;
        }
        final WindowState imeWindow = task.getDisplayContent().mInputMethodWindow;
        if (imeWindow != null && imeWindow.isVisible()) {
            final Rect bounds = imeWindow.getParentFrame();
            bounds.offsetTo(0, 0);
            final var captureArgs =
                    new ScreenCaptureInternal.LayerCaptureArgs.Builder(
                                    imeWindow.getSurfaceControl())
                            .setSourceCrop(bounds)
                            .setFrameScale(1.0f)
                            .setPixelFormat(pixelFormat)
                            .setSecureContentPolicy(
                                    ScreenCaptureParams.SECURE_CONTENT_POLICY_CAPTURE)
                            .build();
            return ScreenCaptureInternal.captureLayers(captureArgs);
        }
        return null;
    }

    /**
     * Captures the screenshot of the IME surface on the task. This will be placed on the closing
     * task snapshot, to maintain the IME visibility while transitioning to a different task.
     */
    @Nullable
    ScreenCaptureInternal.ScreenshotHardwareBuffer screenshotImeFromAttachedTask(
            @NonNull Task task) {
        // Check if the IME target task is ready to capture the IME screenshot. If not, this means
        // the task is not yet visible for some reason, so it doesn't need the screenshot.
        if (checkIfReadyToScreenshot(task) == null) {
            return null;
        }
        final int pixelFormat = mPersistInfoProvider.use16BitFormat()
                    ? PixelFormat.RGB_565
                    : PixelFormat.RGBA_8888;
        return createImeScreenshot(task, pixelFormat);
    }

    @Override
    ActivityRecord getTopActivity(Task source) {
        return source.getTopMostActivity();
    }

    @Override
    ActivityManager.TaskDescription getTaskDescription(Task source) {
        return source.getTaskDescription();
    }

    @Override
    protected Rect getLetterboxInsets(ActivityRecord topActivity) {
        return topActivity.getLetterboxInsets();
    }

    void removeAndDeleteSnapshot(int taskId, int userId) {
        mCache.onIdRemoved(taskId);
        mPersister.removeSnapshot(taskId, userId);
    }

    void removeSnapshotCache(int taskId) {
        mCache.removeRunningEntry(taskId);
    }

    /**
     * See {@link TaskSnapshotPersister#removeObsoleteFiles}
     */
    void removeObsoleteTaskFiles(ArraySet<Integer> persistentTaskIds, int[] runningUserIds) {
        mPersister.removeObsoleteFiles(persistentTaskIds, runningUserIds);
    }

    /**
     * Record task snapshots before shutdown.
     */
    void prepareShutdown() {
        final ArrayList<SnapshotSupplier> supplierArrayList = new ArrayList<>();
        synchronized (mService.mGlobalLock) {
            // Make write items run in a batch.
            mPersister.mSnapshotPersistQueue.setPaused(true);
            mPersister.mSnapshotPersistQueue.prepareShutdown();
            for (int i = 0; i < mService.mRoot.getChildCount(); i++) {
                mService.mRoot.getChildAt(i).forAllLeafTasks(task -> {
                    if (task.isVisible() && !task.isActivityTypeHome()) {
                        final SnapshotSupplier supplier = captureSnapshot(task,
                                true /* allowAppTheme */);
                        if (supplier != null) {
                            supplier.setConsumer(t ->
                                    mPersister.persistSnapshot(task.mTaskId, task.mUserId, t));
                            supplierArrayList.add(supplier);
                        }
                    }
                }, true /* traverseTopToBottom */);
            }
        }
        for (int i = supplierArrayList.size() - 1; i >= 0; --i) {
            supplierArrayList.get(i).handleSnapshot();
        }
        synchronized (mService.mGlobalLock) {
            mPersister.mSnapshotPersistQueue.setPaused(false);
        }
    }

    void waitFlush(long timeout) {
        mPersister.mSnapshotPersistQueue.waitFlush(timeout);
    }

    /**
     * Called when screen is being turned off.
     */
    void screenTurningOff(int displayId, ScreenOffListener listener) {
        if (shouldDisableSnapshots()) {
            listener.onScreenOff();
            return;
        }

        // We can't take a snapshot when screen is off, so take a snapshot now!
        mHandler.post(() -> {
            try {
                synchronized (mService.mGlobalLock) {
                    snapshotForSleeping(displayId);
                }
            } finally {
                listener.onScreenOff();
            }
        });
    }

    /** Called when the device is going to sleep (e.g. screen off, AOD without screen off). */
    void snapshotForSleeping(int displayId) {
        if (shouldDisableSnapshots() || !mService.mDisplayEnabled) {
            return;
        }
        final DisplayContent displayContent = mService.mRoot.getDisplayContent(displayId);
        if (displayContent == null) {
            return;
        }
        // Allow taking snapshot of home when turning screen off to reduce the delay of waking from
        // secure lock to home.
        final boolean allowSnapshotHome = displayId == Display.DEFAULT_DISPLAY
                && mService.mPolicy.isKeyguardSecure(mService.mCurrentUserId);
        mTmpTasks.clear();
        displayContent.forAllLeafTasks(task -> {
            if (!allowSnapshotHome && task.isActivityTypeHome()) {
                return;
            }
            // Since RecentsAnimation will handle task snapshot while switching apps with the best
            // capture timing (e.g. IME window capture), No need additional task capture while task
            // is controlled by RecentsAnimation.
            if (task.isVisible() && !task.isAnimatingByRecents()) {
                mTmpTasks.add(task);
            }
        }, true /* traverseTopToBottom */);
        snapshotTasks(mTmpTasks);
    }
}
