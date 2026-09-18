/*
 * Copyright (C) 2026 The Android Open Source Project
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
package android.service.personalcontext.testutil;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * Helper class for using {@link Executor} in tests.
 */
public class FakeExecutor implements Executor {
    private final Queue<Runnable> mQueue = new ArrayDeque<>();

    @Override
    public void execute(Runnable command) {
        mQueue.add(command);
    }

    /**
     * Runs all pending {@link Runnable}s
     */
    public void runAll() {
        while (!mQueue.isEmpty()) {
            mQueue.remove().run();
        }
    }

    /**
     * Removes all queued {@link Runnable}s
     */
    public void clearAll() {
        while (!mQueue.isEmpty()) {
            mQueue.remove();
        }
    }
}
