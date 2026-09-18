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

import android.annotation.Nullable;
import android.os.Handler;
import android.util.SparseArray;
import android.window.TaskSnapshot;
import android.window.TaskSnapshotManager;
import android.window.TaskSnapshotManager.Resolution;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.window.flags.Flags;

import java.io.PrintWriter;

/**
 * Caches snapshots. See {@link TaskSnapshotController}.
 * <p>
 * Access to this class should be guarded by the global window manager lock.
 */
class TaskSnapshotCache extends SnapshotCache<Task> {

    private final AppSnapshotLoader mLoader;
    @VisibleForTesting
    final DeferRemoveHighResCache mDeferRemoveCache;

    TaskSnapshotCache(AppSnapshotLoader loader, Handler handler) {
        super("Task");
        mLoader = loader;
        mDeferRemoveCache = Flags.onlyCacheLowResTaskSnapshot()
                ? new DeferRemoveHighResCache(handler) : null;
    }

    void putSnapshot(Task task, TaskSnapshot snapshot) {
        synchronized (mLock) {
            snapshot.addReference(TaskSnapshot.REFERENCE_CACHE);
            snapshot.setSafeRelease(mSafeSnapshotReleaser);
            final CacheEntry entry = mRunningCache.get(task.mTaskId);
            if (entry != null) {
                mAppIdMap.remove(entry.topApp);
                final TaskSnapshot previous = entry.snapshot;
                if (mDeferRemoveCache != null && !previous.isLowResolution()
                        && previous.getId() == snapshot.getId()) {
                    mDeferRemoveCache.putSnapshot(task.mTaskId, previous);
                } else {
                    entry.snapshot.removeReference(TaskSnapshot.REFERENCE_CACHE);
                }
            }
            final ActivityRecord top = task.getTopMostActivity();
            mAppIdMap.put(top, task.mTaskId);
            mRunningCache.put(task.mTaskId, new CacheEntry(snapshot, top));
        }
        onSnapshotEntryPutOrRemoved(task.mTaskId);
    }

    /**
     * Retrieves a snapshot from cache.
     * @deprecated Use {@link #getSnapshot(int, int, int)}
     */
    @Deprecated
    @Nullable TaskSnapshot getSnapshot(int taskId, boolean isLowResolution) {
        return getSnapshot(taskId, isLowResolution, TaskSnapshot.REFERENCE_NONE);
    }

    // TODO (b/238206323) Respect isLowResolution.
    @Deprecated
    @Nullable TaskSnapshot getSnapshot(int taskId, boolean isLowResolution,
            @TaskSnapshot.ReferenceFlags int usage) {
        synchronized (mLock) {
            final TaskSnapshot snapshot = getSnapshotInner(taskId);
            if (snapshot != null) {
                if (usage != TaskSnapshot.REFERENCE_NONE) {
                    snapshot.addReference(usage);
                }
                return snapshot;
            }
        }
        return null;
    }

    @Nullable TaskSnapshot getSnapshot(int taskId, @Resolution int retrieveResolution,
            @TaskSnapshot.ReferenceFlags int usage) {
        synchronized (mLock) {
            final TaskSnapshot snapshot = getSnapshotInner(taskId);
            if (snapshot == null) {
                return null;
            }
            if (TaskSnapshotManager.isResolutionMatch(snapshot, retrieveResolution)) {
                if (usage != TaskSnapshot.REFERENCE_NONE) {
                    snapshot.addReference(usage);
                }
                return snapshot;
            }
        }
        if (mDeferRemoveCache != null && usage != TaskSnapshot.REFERENCE_NONE) {
            synchronized (mDeferRemoveCache.mLock) {
                final TaskSnapshot snapshot = mDeferRemoveCache.getSnapshotInner(taskId);
                if (snapshot == null) {
                    return null;
                }
                if (TaskSnapshotManager.isResolutionMatch(snapshot, retrieveResolution)) {
                    snapshot.addReference(usage);
                    return snapshot;
                }
            }
        }
        return null;
    }

    /**
     * Restore snapshot from disk, DO NOT HOLD THE WINDOW MANAGER LOCK!
     */
    @Nullable TaskSnapshot getSnapshotFromDisk(int taskId, int userId, boolean isLowResolution,
            @TaskSnapshot.ReferenceFlags int usage) {
        final TaskSnapshot snapshot;
        if (!isLowResolution && mDeferRemoveCache != null) {
            synchronized (mLoader) {
                final TaskSnapshot cachedSnapshot = mDeferRemoveCache.getSnapshotInner(taskId);
                if (cachedSnapshot != null) {
                    if (usage != TaskSnapshot.REFERENCE_NONE) {
                        cachedSnapshot.addReference(usage);
                    }
                    // Update timer
                    mDeferRemoveCache.scheduleRemoval(taskId);
                    return cachedSnapshot;
                }
                snapshot = mLoader.loadTask(taskId, userId, false /* isLowResolution */);
            }
            if (snapshot != null) {
                synchronized (mLock) {
                    final TaskSnapshot inCacheSnapshot = getSnapshotInner(taskId);
                    if (inCacheSnapshot != null) {
                        mDeferRemoveCache.putSnapshot(taskId, snapshot);
                    }
                }
            }
        } else {
            snapshot = mLoader.loadTask(taskId, userId, isLowResolution);
        }
        // Note: This can be weird if the caller didn't ask for reference.
        if (snapshot != null && usage != TaskSnapshot.REFERENCE_NONE) {
            snapshot.addReference(usage);
        }
        return snapshot;
    }

    @Override
    void dump(PrintWriter pw, String prefix) {
        super.dump(pw, prefix);
        if (mDeferRemoveCache != null) {
            mDeferRemoveCache.dump(pw, prefix);
        }
    }

    @Override
    void clearRunningCache() {
        super.clearRunningCache();
        if (mDeferRemoveCache != null) {
            mDeferRemoveCache.clearRunningCache();
        }
    }

    @Override
    void removeRunningEntry(Integer id) {
        super.removeRunningEntry(id);
        if (mDeferRemoveCache != null) {
            mDeferRemoveCache.removeRunningEntry(id);
        }
    }

    /**
     * Cache the high-resolution snapshot for a short time.
     */
    static class DeferRemoveHighResCache extends SnapshotCache<Task> {
        static final long REMOVE_SNAPSHOT_AFTER_MS = 5000;
        private final Handler mHandler;
        @GuardedBy("mLock")
        private final SparseArray<Runnable> mRemovals = new SparseArray<>();

        DeferRemoveHighResCache(Handler handler) {
            super("Defer High-res");
            mHandler = handler;
        }

        @Override
        void putSnapshot(Task task, TaskSnapshot snapshot) {
            // no-op
        }

        void putSnapshot(int taskId, TaskSnapshot snapshot) {
            // mAppIdMap is unnecessary because the cached item is removed by TaskSnapshotCache.
            synchronized (mLock) {
                snapshot.addReference(TaskSnapshot.REFERENCE_CACHE);
                snapshot.setSafeRelease(mSafeSnapshotReleaser);
                final CacheEntry entry = mRunningCache.get(taskId);
                if (entry != null) {
                    entry.snapshot.removeReference(TaskSnapshot.REFERENCE_CACHE);
                }
                mRunningCache.put(taskId, new CacheEntry(snapshot, null /* topApp */));
            }
            scheduleRemoval(taskId);
        }

        @Override
        void clearRunningCache() {
            super.clearRunningCache();
            synchronized (mLock) {
                for (int i = mRemovals.size() - 1; i >= 0; --i) {
                    mHandler.removeCallbacks(mRemovals.valueAt(i));
                }
                mRemovals.clear();
            }
        }

        @Override
        void removeRunningEntry(Integer id) {
            super.removeRunningEntry(id);
            removeRemovalCallback(id);
        }

        void scheduleRemoval(int taskId) {
            removeRemovalCallback(taskId);
            final Runnable clear = () -> {
                removeRunningEntry(taskId);
            };
            synchronized (mLock) {
                mRemovals.append(taskId, clear);
            }
            mHandler.postDelayed(clear, REMOVE_SNAPSHOT_AFTER_MS);
        }

        private void removeRemovalCallback(int taskId) {
            synchronized (mLock) {
                final Runnable clear = mRemovals.get(taskId);
                if (clear != null) {
                    mHandler.removeCallbacks(clear);
                    mRemovals.remove(taskId);
                }
            }
        }
    }
}
