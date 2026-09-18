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

import com.android.server.companion.datatransfer.crossdevicesync.common.DelayedExecutor;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

/** Implementation of {@link DelayedExecutor} based on {@link FakeClock}. */
public class ClockBasedExecutor implements DelayedExecutor {
    private final FakeClock mClock;
    private final Map<Long, Queue<Runnable>> mRunnables = new TreeMap<>();
    private boolean mInvalidating;

    public ClockBasedExecutor(FakeClock clock) {
        mClock = clock;
        mClock.addListener(this::onClockTick);
    }

    private void onClockTick() {
        invalidate(mClock.elapsedRealtime());
    }

    private void invalidate(long now) {
        if (mInvalidating) {
            // Prevent recursively invalidation.
            return;
        }
        mInvalidating = true;
        while (!mRunnables.isEmpty()) {
            long schedule = getNextSchedule();
            if (schedule > now) {
                break;
            }
            Queue<Runnable> queue = mRunnables.get(schedule);
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
            mRunnables.remove(schedule);
        }
        mInvalidating = false;
    }

    @Override
    public void executeDelayed(Runnable runnable, long delayMillis) {
        long now = mClock.elapsedRealtime();
        mRunnables.computeIfAbsent(now + delayMillis, k -> new LinkedList<>()).add(runnable);
        invalidate(now);
    }

    @Override
    public void cancel(Runnable runnable) {
        mRunnables.values().forEach(queue -> queue.remove(runnable));
        if (!mInvalidating) {
            mRunnables.values().removeIf(Queue::isEmpty);
        }
    }

    @Override
    public void execute(Runnable command) {
        executeDelayed(command, 0);
    }

    public long getNextSchedule() {
        if (mRunnables.isEmpty()) {
            return -1;
        }
        return mRunnables.keySet().iterator().next();
    }

    public void advanceTimeToNextSchedule() {
        long schedule = getNextSchedule();
        if (schedule > 0) {
            mClock.setCurrentTime(schedule);
        }
    }
}
