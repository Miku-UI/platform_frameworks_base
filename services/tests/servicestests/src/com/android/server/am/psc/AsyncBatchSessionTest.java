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

import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_ACTIVITY;

import static com.google.common.truth.Truth.assertThat;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.TestLooperManager;
import android.platform.test.annotations.Presubmit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Presubmit
public final class AsyncBatchSessionTest {
    private static final String UPDATE_MESSAGE = "UPDATED";

    @Rule
    public final TestHandlerRule mHandlerRule = new TestHandlerRule();

    @Test
    public void asyncBatchSession_enqueue() {
        List<String> updates = new ArrayList<>();
        try (AsyncBatchSession session = new AsyncBatchSession(mHandlerRule.getHandler(),
                new Object(), null,
                () -> updates.add(UPDATE_MESSAGE))) {

            // Enqueue some work and trigger an update mid way, while batching is active.
            session.enqueue(() -> updates.add("A"));
            mHandlerRule.getHandler().post(() -> updates.add("X"));
            session.runUpdate();
            session.enqueue(() -> updates.add("B"));
        }

        // Step through the looper once.
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("A");
        // Step through the looper once more.
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("A", "X").inOrder();
        // Step through the looper once more.
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("A", "X", UPDATE_MESSAGE).inOrder();
        // Step through the looper one last time.
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("A", "X", UPDATE_MESSAGE, "B").inOrder();
    }

    @Test
    public void asyncBatchSession_enqueue_batched() {
        List<String> updates = new ArrayList<>();
        try (AsyncBatchSession session = new AsyncBatchSession(mHandlerRule.getHandler(),
                new Object(), null,
                () -> updates.add(UPDATE_MESSAGE))) {
            // Enqueue some work and trigger an update mid way, while batching is active.
            session.start(OOM_ADJ_REASON_ACTIVITY);
            session.enqueue(() -> updates.add("A"));
            mHandlerRule.getHandler().post(() -> updates.add("X"));
            session.runUpdate();
            session.enqueue(() -> updates.add("B"));
        }

        // Step through the looper once.
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("X");
        // Step through the looper once more.
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("X", "A", "B", UPDATE_MESSAGE).inOrder();
    }

    @Test
    public void asyncBatchSession_enqueueNoUpdate_batched() {
        List<String> updates = new ArrayList<>();
        try (AsyncBatchSession session = new AsyncBatchSession(mHandlerRule.getHandler(),
                new Object(), null,
                () -> updates.add(UPDATE_MESSAGE))) {
            // Enqueue some work and trigger an update mid way, while batching is active.
            session.start(OOM_ADJ_REASON_ACTIVITY);
            session.enqueue(() -> updates.add("A"));
            mHandlerRule.getHandler().post(() -> updates.add("X"));
            session.enqueue(() -> updates.add("B"));
        }

        // Step through the looper once.
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("X");
        // Step through the looper once more.
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("X", "A", "B").inOrder();
    }

    @Test
    public void asyncBatchSession_enqueueBoostPriority_batched() {
        List<String> updates = new ArrayList<>();
        try (AsyncBatchSession session = new AsyncBatchSession(mHandlerRule.getHandler(),
                new Object(), null,
                () -> updates.add(UPDATE_MESSAGE))) {

            // Enqueue some work , while batching is active and boost the priority of the session.
            session.start(OOM_ADJ_REASON_ACTIVITY);
            session.enqueue(() -> updates.add("A"));
            mHandlerRule.getHandler().post(() -> updates.add("X"));
            session.enqueue(() -> updates.add("B"));
            session.postToHead();
        }

        assertThat(updates).isEmpty();
        // Step through the looper once.
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("A", "B").inOrder();
        // Step through the looper once more.
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("A", "B", "X").inOrder();
    }

    @Test
    public void asyncBatchSession_interlacedEnqueueAndStage() {
        List<String> updates = new ArrayList<>();
        ConcurrentLinkedQueue<Runnable> stagingQueue = new ConcurrentLinkedQueue<>();
        try (AsyncBatchSession session = new AsyncBatchSession(mHandlerRule.getHandler(),
                new Object(),
                stagingQueue, () -> updates.add(UPDATE_MESSAGE))) {

            // Enqueue some work and trigger an update mid way, while batching is active.
            session.stage(() -> updates.add("1"));
            session.enqueue(() -> updates.add("A"));
            session.runUpdate();
            session.stage(() -> updates.add("2"));
        }

        // Run the first staged runnable.
        stagingQueue.poll().run();
        assertThat(updates).containsExactly("1");
        // Step through the looper one
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("1", "A").inOrder();
        // Run the second staged runnable.
        stagingQueue.poll().run();
        assertThat(updates).containsExactly("1", "A", "2").inOrder();
        // Step through the looper once more.
        mHandlerRule.dispatchNext();
        assertThat(updates).containsExactly("1", "A", "2", UPDATE_MESSAGE).inOrder();
    }

    @Test
    public void asyncBatchSession_interlacedEnqueueAndStage_batched() {
        List<String> updates = new ArrayList<>();
        ConcurrentLinkedQueue<Runnable> stagingQueue = new ConcurrentLinkedQueue<>();
        try (AsyncBatchSession session = new AsyncBatchSession(mHandlerRule.getHandler(),
                new Object(),
                stagingQueue, () -> updates.add(UPDATE_MESSAGE))) {

            // Enqueue some work and trigger an update mid way, while batching is active.
            session.start(OOM_ADJ_REASON_ACTIVITY);
            session.stage(() -> updates.add("1"));
            session.enqueue(() -> updates.add("A"));
            session.stage(() -> updates.add("2"));
            session.runUpdate();
            session.enqueue(() -> updates.add("B"));
            session.stage(() -> updates.add("3"));
            session.stage(() -> updates.add("4"));
        }

        // Run the first staged runnable.
        stagingQueue.poll().run();
        // Run the second staged runnable.
        stagingQueue.poll().run();
        // Run the third staged runnable.
        stagingQueue.poll().run();
        // Step through the looper once to run all batched enqueued work.
        mHandlerRule.dispatchNext();
        // Run the last staged runnable.
        stagingQueue.poll().run();

        assertThat(updates).containsExactly("1", "2", "3", "A", "B", UPDATE_MESSAGE, "4").inOrder();
    }

    /**
     * A JUnit rule for managing a HandlerThread and TestLooperManager.
     */
    private static final class TestHandlerRule extends ExternalResource {
        private static final String HANDLER_THREAD_NAME = "TestHandlerRuleThread";
        private Handler mHandler;
        private TestLooperManager mTestLooperManager;
        private HandlerThread mHandlerThread;

        @Override
        protected void before() {
            mHandlerThread = new HandlerThread(HANDLER_THREAD_NAME);
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
            mTestLooperManager = new TestLooperManager(mHandlerThread.getLooper());
        }

        @Override
        protected void after() {
            mHandlerThread.getLooper().quitSafely();
        }

        /**
         * Returns the handler associated with this test rule.
         */
        Handler getHandler() {
            return mHandler;
        }

        /**
         * Executes the next message on the looper.
         */
        void dispatchNext() {
            mTestLooperManager.execute(mTestLooperManager.next());
        }
    }
}
