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
package com.android.server.am.psc;

import android.os.Handler;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import com.android.server.am.ProcessStateController;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A {@link BatchSession} that manages work to be done on another thread. The session will
 * trigger a {@link ProcessStateController} update at the end of the session if prompted to do so.
 */
@RavenwoodKeepWholeClass
public class AsyncBatchSession extends BatchSession {
    final Handler mHandler;
    final Object mLock;
    final ConcurrentLinkedQueue<Runnable> mStagingQueue;
    private final Runnable mUpdateRunnable;
    private final Runnable mLockedUpdateRunnable;
    private boolean mRunUpdate = false;
    private boolean mBoostPriority = false;

    private ArrayList<Runnable> mBatchList = new ArrayList<>();

    public AsyncBatchSession(Handler handler, Object lock,
            ConcurrentLinkedQueue<Runnable> stagingQueue, Runnable updateRunnable) {
        mHandler = handler;
        mLock = lock;
        mStagingQueue = stagingQueue;
        mUpdateRunnable = updateRunnable;
        mLockedUpdateRunnable = () -> {
            synchronized (lock) {
                updateRunnable.run();
            }
        };
    }

    /**
     * If the BatchSession is currently active, posting the batched work to the front of the
     * Handler queue when the session is closed.
     */
    public void postToHead() {
        if (isActive()) {
            mBoostPriority = true;
        }
    }

    /**
     * Stage the runnable to be run on the next ProcessStateController update. The work may be
     * opportunistically run if an update triggers before the WindowManager posted update is
     * handled.
     */
    public void stage(Runnable runnable) {
        mStagingQueue.add(runnable);
    }

    /**
     * Enqueue the work to be run asynchronously done on a Handler thread.
     * If batch session is currently active, queue up the work to be run when the session ends.
     * Otherwise, the work will be immediately enqueued on to the Handler thread.
     */
    public void enqueue(Runnable runnable) {
        if (isActive()) {
            mBatchList.add(runnable);
        } else {
            // Not in session, just post to the handler immediately.
            mHandler.post(() -> {
                synchronized (mLock) {
                    runnable.run();
                }
            });
        }
    }

    /**
     * Trigger an update to be asynchronously done on a Handler thread.
     * If batch session is currently active, the update will be run at the end of the batched
     * work.
     * Otherwise, the update will be immediately enqueued on to the Handler thread (and any
     * previously posted update will be removed in favor of this most recent trigger).
     */
    public void runUpdate() {
        if (isActive()) {
            // Mark that an update should be done after the batched work is done.
            mRunUpdate = true;
        } else {
            // Not in session, just post to the handler immediately (and clear any existing
            // posted update).
            mHandler.removeCallbacks(mLockedUpdateRunnable);
            mHandler.post(mLockedUpdateRunnable);
        }
    }

    @Override
    protected void onClose() {
        final ArrayList<Runnable> list = new ArrayList<>(mBatchList);
        final boolean runUpdate = mRunUpdate;

        // Return if there is nothing to do.
        if (list.isEmpty() && !runUpdate) return;

        mBatchList.clear();
        mRunUpdate = false;

        // offload all of the queued up work to the Handler thread.
        final Runnable batchedWorkload = () -> {
            synchronized (mLock) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    list.get(i).run();
                }
                if (runUpdate) {
                    mUpdateRunnable.run();
                }
            }
        };

        if (mBoostPriority) {
            // The priority of this BatchSession has been boosted. Post to the front of the
            // Handler queue.
            mBoostPriority = false;
            mHandler.postAtFrontOfQueue(batchedWorkload);
        } else {
            mHandler.post(batchedWorkload);
        }
    }
}
