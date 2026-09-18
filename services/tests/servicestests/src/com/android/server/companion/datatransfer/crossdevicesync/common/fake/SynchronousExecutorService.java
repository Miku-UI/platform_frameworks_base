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
package com.android.server.companion.datatransfer.crossdevicesync.common.fake;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** An ExecutorService that runs tasks synchronously on the calling thread. */
public class SynchronousExecutorService extends AbstractExecutorService {

    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final LinkedList<Runnable> mRunnableQueue = new LinkedList<>();

    @Override
    public void execute(Runnable command) {
        if (isShutdown.get()) {
            throw new RejectedExecutionException("Executor has been shut down");
        }
        mRunnableQueue.add(command);
        // Run the command directly on the calling thread, but prevent recursive calls to better
        // simulate an executor.
        if (mRunnableQueue.size() == 1) {
            while (!mRunnableQueue.isEmpty()) {
                mRunnableQueue.peek().run();
                mRunnableQueue.poll();
            }
        }
    }

    @Override
    public void shutdown() {
        isShutdown.set(true);
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        return Collections.emptyList(); // No pending tasks to return
    }

    @Override
    public boolean isShutdown() {
        return isShutdown.get();
    }

    @Override
    public boolean isTerminated() {
        // Since it's synchronous, it's considered terminated as soon as it's shut down.
        return isShutdown.get();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        // No threads to wait for, so it "terminates" immediately.
        return true;
    }
}
