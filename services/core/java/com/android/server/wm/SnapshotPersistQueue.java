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

import static android.graphics.Bitmap.CompressFormat.JPEG;
import static android.graphics.Bitmap.CompressFormat.PNG;
import static android.os.Trace.TRACE_TAG_WINDOW_MANAGER;

import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.annotation.NonNull;
import android.app.ActivityTaskManager;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import android.util.AtomicFile;
import android.util.Slog;
import android.window.TaskSnapshot;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.BaseAppSnapshotPersister.LowResSnapshotSupplier;
import com.android.server.wm.BaseAppSnapshotPersister.PersistInfoProvider;
import com.android.server.wm.nano.WindowManagerProtos.TaskSnapshotProto;
import com.android.window.flags.Flags;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Singleton worker thread to queue up persist or delete tasks of {@link TaskSnapshot}s to disk.
 */
class SnapshotPersistQueue {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "TaskSnapshotPersister" : TAG_WM;
    private static final long DELAY_MS = 100;
    static final int MAX_HW_STORE_QUEUE_DEPTH = 2;
    private static final int COMPRESS_QUALITY = 95;

    @GuardedBy("mLock")
    private final ArrayDeque<WriteQueueItem> mWriteQueue = new ArrayDeque<>();
    @GuardedBy("mLock")
    private final ArrayDeque<StoreWriteQueueItem> mStoreQueueItems = new ArrayDeque<>();
    @GuardedBy("mLock")
    private boolean mQueueIdling;
    @GuardedBy("mLock")
    private boolean mPaused;
    private boolean mStarted;
    private final Object mLock = new Object();
    private final UserManagerInternal mUserManagerInternal;
    private boolean mShutdown;
    final int mMaxTotalStoreQueue;

    SnapshotPersistQueue() {
        mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
        mMaxTotalStoreQueue = ActivityTaskManager.getMaxRecentTasksStatic();
    }

    Object getLock() {
        return mLock;
    }

    void systemReady() {
        start();
    }

    /**
     * Starts persisting.
     */
    void start() {
        if (!mStarted) {
            mStarted = true;
            mPersister.start();
        }
    }

    /**
     * Temporarily pauses/unpauses persisting of task snapshots.
     *
     * @param paused Whether task snapshot persisting should be paused.
     */
    void setPaused(boolean paused) {
        synchronized (mLock) {
            mPaused = paused;
            if (!paused) {
                mLock.notifyAll();
            }
        }
    }

    /**
     * Prepare to enqueue all visible task snapshots because of shutdown.
     */
    void prepareShutdown() {
        synchronized (mLock) {
            mShutdown = true;
        }
    }

    private boolean isQueueEmpty() {
        synchronized (mLock) {
            return mWriteQueue.isEmpty() || mQueueIdling || mPaused;
        }
    }

    void waitFlush(long timeout) {
        if (timeout <= 0) {
            return;
        }
        final long endTime = System.currentTimeMillis() + timeout;
        while (true) {
            if (!isQueueEmpty()) {
                long timeRemaining = endTime - System.currentTimeMillis();
                if (timeRemaining > 0) {
                    synchronized (mLock) {
                        try {
                            mLock.wait(timeRemaining);
                        } catch (InterruptedException e) {
                        }
                    }
                } else {
                    Slog.w(TAG, "Snapshot Persist Queue flush timed out");
                    break;
                }
            } else {
                break;
            }
        }
    }

    @VisibleForTesting
    void waitForQueueEmpty() {
        while (true) {
            synchronized (mLock) {
                if (mWriteQueue.isEmpty() && mQueueIdling) {
                    return;
                }
            }
            SystemClock.sleep(DELAY_MS);
        }
    }

    int peekWriteQueueSize() {
        synchronized (mLock) {
            return mStoreQueueItems.size();
        }
    }

    int peekQueueSize() {
        synchronized (mLock) {
            return mWriteQueue.size();
        }
    }

    private void addToQueueInternal(WriteQueueItem item, boolean insertToFront) {
        final Iterator<WriteQueueItem> iterator = mWriteQueue.iterator();
        while (iterator.hasNext()) {
            final WriteQueueItem next = iterator.next();
            if (item.isDuplicateOrExclusiveItem(next)) {
                iterator.remove();
                break;
            }
        }
        if (insertToFront) {
            mWriteQueue.addFirst(item);
        } else {
            mWriteQueue.addLast(item);
        }
        item.onQueuedLocked();
        if (!mShutdown) {
            ensureStoreQueueDepthLocked();
        }
        if (!mPaused) {
            mLock.notifyAll();
        }
    }

    @GuardedBy("mLock")
    void sendToQueueLocked(WriteQueueItem item) {
        addToQueueInternal(item, false /* insertToFront */);
    }

    @GuardedBy("mLock")
    void insertQueueAtFirstLocked(WriteQueueItem item) {
        addToQueueInternal(item, true /* insertToFront */);
    }

    @GuardedBy("mLock")
    private void ensureStoreQueueDepthLocked() {
        // Rules for store queue depth:
        //  - Hardware render involved items < MAX_HW_STORE_QUEUE_DEPTH
        //  - Total (SW + HW) items < mMaxTotalStoreQueue
        int hwStoreCount = 0;
        int totalStoreCount = 0;
        // Use descending iterator to keep the latest items.
        final Iterator<StoreWriteQueueItem> iterator = mStoreQueueItems.descendingIterator();
        while (iterator.hasNext() && mStoreQueueItems.size() > MAX_HW_STORE_QUEUE_DEPTH) {
            final StoreWriteQueueItem item = iterator.next();
            totalStoreCount++;
            boolean removeItem = false;
            if (TaskSnapshotConvertUtil.mustPersistByHardwareRender(item.mSnapshot)) {
                hwStoreCount++;
                if (hwStoreCount > MAX_HW_STORE_QUEUE_DEPTH) {
                    removeItem = true;
                }
            }
            if (!removeItem && totalStoreCount > mMaxTotalStoreQueue) {
                removeItem = true;
            }
            if (removeItem) {
                iterator.remove();
                mWriteQueue.remove(item);
                Slog.i(TAG, "Queue is too deep! Purged item with index=" + item.mId);
            }
        }
    }

    void deleteSnapshot(int index, int userId, PersistInfoProvider provider) {
        final File protoFile = provider.getProtoFile(index, userId);
        final File bitmapLowResFile = provider.getLowResolutionBitmapFile(index, userId);
        if (protoFile.exists()) {
            protoFile.delete();
        }
        if (bitmapLowResFile.exists()) {
            bitmapLowResFile.delete();
        }
        final File bitmapFile = provider.getHighResolutionBitmapFile(index, userId);
        if (bitmapFile.exists()) {
            bitmapFile.delete();
        }
    }

    private final Thread mPersister = new Thread("TaskSnapshotPersister") {
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            while (true) {
                WriteQueueItem next;
                boolean isReadyToWrite = false;
                synchronized (mLock) {
                    if (mPaused) {
                        next = null;
                    } else {
                        next = mWriteQueue.poll();
                        if (next != null) {
                            if (next.isReady(mUserManagerInternal)) {
                                isReadyToWrite = true;
                                next.onDequeuedLocked();
                            } else if (!mShutdown) {
                                mWriteQueue.addLast(next);
                            } else {
                                // User manager is locked and device is shutting down, skip writing
                                // this item.
                                next.onDequeuedLocked();
                                next = null;
                            }
                        }
                    }
                }
                if (next != null) {
                    if (isReadyToWrite) {
                        next.write();
                    }
                    if (!mShutdown) {
                        SystemClock.sleep(DELAY_MS);
                    }
                }
                synchronized (mLock) {
                    final boolean writeQueueEmpty = mWriteQueue.isEmpty();
                    if (!writeQueueEmpty && !mPaused) {
                        continue;
                    }
                    if (mShutdown && writeQueueEmpty) {
                        mLock.notifyAll();
                    }
                    try {
                        mQueueIdling = writeQueueEmpty;
                        mLock.wait();
                        mQueueIdling = false;
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    };

    abstract static class WriteQueueItem {
        protected final PersistInfoProvider mPersistInfoProvider;
        protected final int mUserId;
        WriteQueueItem(@NonNull PersistInfoProvider persistInfoProvider, int userId) {
            mPersistInfoProvider = persistInfoProvider;
            mUserId = userId;
        }
        /**
         * @return {@code true} if item is ready to have {@link WriteQueueItem#write} called
         */
        boolean isReady(UserManagerInternal userManager) {
            return userManager.isUserUnlocked(mUserId);
        }

        abstract void write();

        /**
         * Called when this queue item has been put into the queue.
         */
        void onQueuedLocked() {
        }

        /**
         * Called when this queue item has been taken out of the queue.
         */
        void onDequeuedLocked() {
        }

        boolean isDuplicateOrExclusiveItem(WriteQueueItem testItem) {
            return false;
        }
    }

    StoreWriteQueueItem createStoreWriteQueueItem(int id, int userId, TaskSnapshot snapshot,
            PersistInfoProvider provider,
            Consumer<LowResSnapshotSupplier> lowResSnapshotConsumer) {
        return new StoreWriteQueueItem(id, userId, snapshot, provider, lowResSnapshotConsumer);
    }

    void updateKnownLowResSnapshotIfPossible(int id, TaskSnapshot lowResSnapshot) {
        synchronized (mLock) {
            for (StoreWriteQueueItem item : mStoreQueueItems) {
                if (item.mId == id && item.updateKnownLowResSnapshotIfPossible(lowResSnapshot)) {
                    break;
                }
            }
        }
    }

    class StoreWriteQueueItem extends WriteQueueItem {
        private final int mId;
        private final TaskSnapshot mSnapshot;
        private final Consumer<LowResSnapshotSupplier> mLowResSnapshotConsumer;
        private TaskSnapshot mKnownLowResSnapshot;

        StoreWriteQueueItem(int id, int userId, TaskSnapshot snapshot,
                PersistInfoProvider provider,
                Consumer<LowResSnapshotSupplier> lowResSnapshotConsumer) {
            super(provider, userId);
            mId = id;
            snapshot.addReference(TaskSnapshot.REFERENCE_PERSIST);
            mSnapshot = snapshot;
            mLowResSnapshotConsumer = lowResSnapshotConsumer;
        }

        @GuardedBy("mLock")
        @Override
        void onQueuedLocked() {
            // Remove duplicate request.
            mStoreQueueItems.removeIf(item -> {
                if (item.equals(this) && item.mSnapshot != mSnapshot) {
                    item.mSnapshot.removeReference(TaskSnapshot.REFERENCE_PERSIST);
                    item.removeKnownLowResSnapshot();
                    return true;
                }
                return false;
            });
            mStoreQueueItems.offer(this);
        }

        @GuardedBy("mLock")
        @Override
        void onDequeuedLocked() {
            mStoreQueueItems.remove(this);
        }

        boolean updateKnownLowResSnapshotIfPossible(TaskSnapshot lowResSnapshot) {
            if (mSnapshot.getId() == lowResSnapshot.getId() && mKnownLowResSnapshot == null) {
                mKnownLowResSnapshot = lowResSnapshot;
                mKnownLowResSnapshot.addReference(TaskSnapshot.REFERENCE_WILL_UPDATE_TO_CACHE);
                return true;
            }
            return false;
        }

        private void removeKnownLowResSnapshot() {
            if (mKnownLowResSnapshot != null) {
                mKnownLowResSnapshot.removeReference(
                        TaskSnapshot.REFERENCE_WILL_UPDATE_TO_CACHE);
            }
        }

        @Override
        void write() {
            if (Trace.isTagEnabled(TRACE_TAG_WINDOW_MANAGER)) {
                Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "StoreWriteQueueItem#" + mId);
            }
            if (!mPersistInfoProvider.createDirectory(mUserId)) {
                Slog.e(TAG, "Unable to create snapshot directory for user dir="
                        + mPersistInfoProvider.getDirectory(mUserId));
            }
            boolean failed = false;
            if (!writeProto()) {
                failed = true;
            }
            if (!writeBuffer()) {
                failed = true;
            }
            if (failed) {
                deleteSnapshot(mId, mUserId, mPersistInfoProvider);
            }
            mSnapshot.removeReference(TaskSnapshot.REFERENCE_PERSIST);
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }

        boolean writeProto() {
            final TaskSnapshotProto proto = new TaskSnapshotProto();
            proto.orientation = mSnapshot.getOrientation();
            proto.rotation = mSnapshot.getRotation();
            proto.taskWidth = mSnapshot.getTaskSize().x;
            proto.taskHeight = mSnapshot.getTaskSize().y;
            proto.insetLeft = mSnapshot.getContentInsets().left;
            proto.insetTop = mSnapshot.getContentInsets().top;
            proto.insetRight = mSnapshot.getContentInsets().right;
            proto.insetBottom = mSnapshot.getContentInsets().bottom;
            proto.letterboxInsetLeft = mSnapshot.getLetterboxInsets().left;
            proto.letterboxInsetTop = mSnapshot.getLetterboxInsets().top;
            proto.letterboxInsetRight = mSnapshot.getLetterboxInsets().right;
            proto.letterboxInsetBottom = mSnapshot.getLetterboxInsets().bottom;
            proto.isRealSnapshot = mSnapshot.isRealSnapshot();
            proto.windowingMode = mSnapshot.getWindowingMode();
            proto.appearance = mSnapshot.getAppearance();
            proto.isTranslucent = mSnapshot.isTranslucent();
            proto.topActivityComponent = mSnapshot.getTopActivityComponent().flattenToString();
            proto.uiMode = mSnapshot.getUiMode();
            proto.id = mSnapshot.getId();
            proto.densityDpi = mSnapshot.getDensityDpi();
            final byte[] bytes = TaskSnapshotProto.toByteArray(proto);
            final File file = mPersistInfoProvider.getProtoFile(mId, mUserId);
            final AtomicFile atomicFile = new AtomicFile(file);
            FileOutputStream fos = null;
            try {
                fos = atomicFile.startWrite();
                fos.write(bytes);
                atomicFile.finishWrite(fos);
            } catch (IOException e) {
                atomicFile.failWrite(fos);
                Slog.e(TAG, "Unable to open " + file + " for persisting. " + e);
                return false;
            }
            return true;
        }

        boolean writeBuffer() {
            if (AbsAppSnapshotController.isInvalidHardwareBuffer(mSnapshot)) {
                Slog.e(TAG, "Invalid task snapshot hw buffer, taskId=" + mId);
                return false;
            }

            final Bitmap swBitmap = TaskSnapshotConvertUtil.copySWBitmap(mSnapshot);
            if (swBitmap == null) {
                return false;
            }
            final File file = mPersistInfoProvider.getHighResolutionBitmapFile(mId, mUserId);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                swBitmap.compress(Flags.respectRequestedTaskSnapshotResolution() ? PNG : JPEG,
                        COMPRESS_QUALITY, fos);
            } catch (IOException e) {
                Slog.e(TAG, "Unable to open " + file + " for persisting.", e);
                return false;
            }

            if (!mPersistInfoProvider.enableLowResSnapshots()) {
                swBitmap.recycle();
                return true;
            }

            final int width;
            final int height;
            if (Flags.reduceTaskSnapshotMemoryUsage()) {
                width = mSnapshot.getHardwareBufferWidth();
                height = mSnapshot.getHardwareBufferHeight();
            } else {
                final HardwareBuffer hwBuffer = mSnapshot.getHardwareBuffer();
                width = hwBuffer.getWidth();
                height = hwBuffer.getHeight();
            }
            final Bitmap lowResBitmap = Bitmap.createScaledBitmap(swBitmap,
                    (int) (width * mPersistInfoProvider.lowResScaleFactor()),
                    (int) (height * mPersistInfoProvider.lowResScaleFactor()),
                    true /* filter */);
            swBitmap.recycle();

            final File lowResFile = mPersistInfoProvider.getLowResolutionBitmapFile(mId, mUserId);
            try (FileOutputStream lowResFos = new FileOutputStream(lowResFile)) {
                lowResBitmap.compress(JPEG, COMPRESS_QUALITY, lowResFos);
            } catch (IOException e) {
                Slog.e(TAG, "Unable to open " + lowResFile + " for persisting.", e);
                return false;
            }
            if (mLowResSnapshotConsumer != null) {
                mLowResSnapshotConsumer.accept(new LowResSnapshotSupplier() {
                    @Override
                    public TaskSnapshot getLowResSnapshot() {
                        if (mKnownLowResSnapshot != null) {
                            lowResBitmap.recycle();
                            return mKnownLowResSnapshot;
                        }
                        final TaskSnapshot result = TaskSnapshotConvertUtil
                                .convertLowResSnapshot(mSnapshot, lowResBitmap);
                        lowResBitmap.recycle();
                        return result;
                    }

                    @Override
                    public void abort() {
                        lowResBitmap.recycle();
                        removeKnownLowResSnapshot();
                    }
                });
            } else {
                lowResBitmap.recycle();
                removeKnownLowResSnapshot();
            }

            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            final StoreWriteQueueItem other = (StoreWriteQueueItem) o;
            return mId == other.mId && mUserId == other.mUserId
                    && mPersistInfoProvider == other.mPersistInfoProvider;
        }

        /** Called when the item is going to be removed from write queue. */
        void onRemovedFromWriteQueue() {
            mStoreQueueItems.remove(this);
            mSnapshot.removeReference(TaskSnapshot.REFERENCE_PERSIST);
            removeKnownLowResSnapshot();
        }

        @Override
        boolean isDuplicateOrExclusiveItem(WriteQueueItem testItem) {
            if (equals(testItem)) {
                final StoreWriteQueueItem swqi = (StoreWriteQueueItem) testItem;
                if (swqi.mSnapshot != mSnapshot) {
                    swqi.onRemovedFromWriteQueue();
                    return true;
                }
            } else if (testItem instanceof DeleteWriteQueueItem) {
                final DeleteWriteQueueItem dwqi = (DeleteWriteQueueItem) testItem;
                return dwqi.mId == mId && dwqi.mUserId == mUserId
                        && dwqi.mPersistInfoProvider == mPersistInfoProvider;
            }
            return false;
        }

        @Override
        public String toString() {
            return "StoreWriteQueueItem{ID=" + mId + ", UserId=" + mUserId + "}";
        }
    }

    DeleteWriteQueueItem createDeleteWriteQueueItem(int id, int userId,
            PersistInfoProvider provider) {
        return new DeleteWriteQueueItem(id, userId, provider);
    }

    private class DeleteWriteQueueItem extends WriteQueueItem {
        private final int mId;

        DeleteWriteQueueItem(int id, int userId, PersistInfoProvider provider) {
            super(provider, userId);
            mId = id;
        }

        @Override
        void write() {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "DeleteWriteQueueItem");
            deleteSnapshot(mId, mUserId, mPersistInfoProvider);
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        }

        @Override
        public String toString() {
            return "DeleteWriteQueueItem{ID=" + mId + ", UserId=" + mUserId + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            final DeleteWriteQueueItem other = (DeleteWriteQueueItem) o;
            return mId == other.mId && mUserId == other.mUserId
                    && mPersistInfoProvider == other.mPersistInfoProvider;
        }

        @Override
        boolean isDuplicateOrExclusiveItem(WriteQueueItem testItem) {
            if (testItem instanceof StoreWriteQueueItem) {
                final StoreWriteQueueItem swqi = (StoreWriteQueueItem) testItem;
                if (swqi.mId == mId && swqi.mUserId == mUserId
                        && swqi.mPersistInfoProvider == mPersistInfoProvider) {
                    swqi.onRemovedFromWriteQueue();
                    return true;
                }
                return false;
            }
            return equals(testItem);
        }
    }

    void dump(PrintWriter pw, String prefix) {
        final WriteQueueItem[] items;
        synchronized (mLock) {
            items = mWriteQueue.toArray(new WriteQueueItem[0]);
        }
        if (items.length == 0) {
            return;
        }
        pw.println(prefix + "PersistQueue contains:");
        for (int i = items.length - 1; i >= 0; --i) {
            pw.println(prefix + "  " + items[i] + "");
        }
    }
}
