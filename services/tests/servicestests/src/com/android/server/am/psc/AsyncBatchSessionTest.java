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

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

@Presubmit
public class AsyncBatchSessionTest {

    private Handler mManagedHandler;
    private TestLooperManager mTestLooperManager;

    @Before
    public void setup() {
        HandlerThread t = new HandlerThread("ManagedThread");
        t.start();
        mManagedHandler = new Handler(t.getLooper());
        mTestLooperManager = new TestLooperManager(t.getLooper());
    }

    @Test
    public void asyncBatchSession_enqueue() {
        ArrayList<String> updates = new ArrayList<>();
        AsyncBatchSession session = new AsyncBatchSession(mManagedHandler, new Object(), null,
                () -> updates.add("UPDATED"));

        // Enqueue some work and trigger an update mid way, while batching is active.
        session.enqueue(() -> updates.add("A"));
        mManagedHandler.post(() -> updates.add("X"));
        session.runUpdate();
        session.enqueue(() -> updates.add("B"));

        // Step through the looper once.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates).containsExactly("A");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates).containsExactly("A", "X");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates).containsExactly("A", "X", "UPDATED");
        // Step through the looper one last time.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates).containsExactly("A", "X", "UPDATED", "B");
    }

    @Test
    public void asyncBatchSession_enqueue_batched() {
        ArrayList<String> updates = new ArrayList<>();
        AsyncBatchSession session = new AsyncBatchSession(mManagedHandler, new Object(), null,
                () -> updates.add("UPDATED"));

        // Enqueue some work and trigger an update mid way, while batching is active.
        session.start(OOM_ADJ_REASON_ACTIVITY);
        session.enqueue(() -> updates.add("A"));
        mManagedHandler.post(() -> updates.add("X"));
        session.runUpdate();
        session.enqueue(() -> updates.add("B"));
        session.close();

        // Step through the looper once.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates).containsExactly("X");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates.get(0)).isEqualTo("X");
        assertThat(updates.get(1)).isEqualTo("A");
        assertThat(updates.get(2)).isEqualTo("B");
        assertThat(updates.get(3)).isEqualTo("UPDATED");
    }

    @Test
    public void asyncBatchSession_enqueueNoUpdate_batched() {
        ArrayList<String> updates = new ArrayList<>();
        AsyncBatchSession session = new AsyncBatchSession(mManagedHandler, new Object(), null,
                () -> updates.add("UPDATED"));

        // Enqueue some work and trigger an update mid way, while batching is active.
        session.start(OOM_ADJ_REASON_ACTIVITY);
        session.enqueue(() -> updates.add("A"));
        mManagedHandler.post(() -> updates.add("X"));
        session.enqueue(() -> updates.add("B"));
        session.close();

        // Step through the looper once.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates).containsExactly("X");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates).containsExactly("X", "A", "B");
    }

    @Test
    public void asyncBatchSession_enqueueBoostPriority_batched() {
        ArrayList<String> updates = new ArrayList<>();
        AsyncBatchSession session = new AsyncBatchSession(mManagedHandler, new Object(), null,
                () -> updates.add("UPDATED"));

        // Enqueue some work , while batching is active and boost the priority of the session.
        session.start(OOM_ADJ_REASON_ACTIVITY);
        session.enqueue(() -> updates.add("A"));
        mManagedHandler.post(() -> updates.add("X"));
        session.enqueue(() -> updates.add("B"));
        session.postToHead();
        session.close();

        assertThat(updates).isEmpty();
        // Step through the looper once.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates).containsExactly("A", "B");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates).containsExactly("A", "B", "X");
    }

    @Test
    public void asyncBatchSession_interlacedEnqueueAndStage() {
        ArrayList<String> updates = new ArrayList<>();
        ConcurrentLinkedQueue<Runnable> stagingQueue = new ConcurrentLinkedQueue<>();
        AsyncBatchSession session = new AsyncBatchSession(mManagedHandler, new Object(),
                stagingQueue, () -> updates.add("UPDATED"));

        // Enqueue some work and trigger an update mid way, while batching is active.
        session.stage(() -> updates.add("1"));
        session.enqueue(() -> updates.add("A"));
        session.runUpdate();
        session.stage(() -> updates.add("2"));

        // Run the first staged runnable.
        stagingQueue.poll().run();
        assertThat(updates).containsExactly("1");
        // Step through the looper one
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates).containsExactly("1", "A");
        // Run the second staged runnable.
        stagingQueue.poll().run();
        assertThat(updates).containsExactly("1", "A", "2");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(updates).containsExactly("1", "A", "2", "UPDATED");
    }

    @Test
    public void asyncBatchSession_interlacedEnqueueAndStage_batched() {
        ArrayList<String> updates = new ArrayList<>();
        ConcurrentLinkedQueue<Runnable> stagingQueue = new ConcurrentLinkedQueue<>();
        AsyncBatchSession session = new AsyncBatchSession(mManagedHandler, new Object(),
                stagingQueue, () -> updates.add("UPDATED"));

        // Enqueue some work and trigger an update mid way, while batching is active.
        session.start(OOM_ADJ_REASON_ACTIVITY);
        session.stage(() -> updates.add("1"));
        session.enqueue(() -> updates.add("A"));
        session.stage(() -> updates.add("2"));
        session.runUpdate();
        session.enqueue(() -> updates.add("B"));
        session.stage(() -> updates.add("3"));
        session.stage(() -> updates.add("4"));
        session.close();

        // Run the first staged runnable.
        stagingQueue.poll().run();
        // Run the second staged runnable.
        stagingQueue.poll().run();
        // Run the third staged runnable.
        stagingQueue.poll().run();
        // Step through the looper once to run all batched enqueued work.
        mTestLooperManager.execute(mTestLooperManager.next());
        // Run the last staged runnable.
        stagingQueue.poll().run();

        assertThat(updates.get(0)).isEqualTo("1");
        assertThat(updates.get(1)).isEqualTo("2");
        assertThat(updates.get(2)).isEqualTo("3");
        assertThat(updates.get(3)).isEqualTo("A");
        assertThat(updates.get(4)).isEqualTo("B");
        assertThat(updates.get(5)).isEqualTo("UPDATED");
        assertThat(updates.get(6)).isEqualTo("4");
    }
}
