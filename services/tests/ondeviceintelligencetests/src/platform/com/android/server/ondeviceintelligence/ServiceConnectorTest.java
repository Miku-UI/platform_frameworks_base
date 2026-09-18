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

package com.android.server.ondeviceintelligence;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.infra.AndroidFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RunWith(AndroidJUnit4.class)
public class ServiceConnectorTest {

    @Mock Context mMockContext;

    private static final String TEST_SERVICE_NAME = "com.test.package/.TestService";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(TEST_SERVICE_NAME);

    /**
     * A Handler that queues asynchronous messages instead of executing them in the background.
     * This allows the test to explicitly call flush() to process all queued messages synchronously,
     * maintaining exactly the same code paths as production while avoiding race conditions.
     */
    private static class TestHandler extends Handler {
        private final List<Runnable> mRunnables = new ArrayList<>();

        public TestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public boolean sendMessageAtTime(android.os.Message msg, long uptimeMillis) {
            Runnable callback = msg.getCallback();
            if (callback != null) {
                // If the message is scheduled far into the future (like the 30 second timeout),
                // we ignore it. We only want to process immediate messages synchronous logic.
                if (uptimeMillis <= android.os.SystemClock.uptimeMillis() + 1000) {
                    mRunnables.add(callback);
                }
            }
            return true;
        }

        /**
         * Processes all queued messages synchronously.
         */
        public void flush() {
            List<Runnable> runnables = new ArrayList<>(mRunnables);
            mRunnables.clear();
            for (Runnable r : runnables) {
                r.run();
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.bindService(any(Intent.class), anyInt(), any(), any())).thenReturn(true);
        when(mMockContext.createContextAsUser(any(UserHandle.class), anyInt()))
                .thenReturn(mMockContext);
    }

    @Test
    public void testCancelPendingJobs_failsUnfinishedJobs() throws Exception {
        TestHandler handler = new TestHandler(Looper.getMainLooper());
        android.os.IInterface mockInterface = org.mockito.Mockito.mock(android.os.IInterface.class);
        ServiceConnector.Impl<android.os.IInterface> connector =
                new ServiceConnector.Impl<>(
                        mMockContext,
                        new Intent().setComponent(TEST_COMPONENT_NAME),
                        0,
                        UserHandle.SYSTEM.getIdentifier(),
                        binder -> mockInterface,
                        handler);

        CompletableFuture<Void> jobFuture = new CompletableFuture<>();
        ServiceConnector.Job<android.os.IInterface, CompletableFuture<Void>> asyncJob =
                service -> jobFuture;

        AndroidFuture<Void> resultFuture = connector.postAsync(asyncJob);

        // Process the queued message to enqueue the job synchronously.
        handler.flush();

        // Simulate service connection to process the queue and move the job to mUnfinishedJobs
        connector.onServiceConnected(TEST_COMPONENT_NAME, new android.os.Binder());

        // Verify the job is not yet completed
        assertTrue(!resultFuture.isDone());

        // Cancel pending jobs, which should now also fail unfinished jobs
        // Simulate service disconnection using public API.
        connector.unbind();

        // Process the queued unbindJobThread message.
        handler.flush();

        // Verify the job future was cancelled
        assertTrue(resultFuture.isCancelled());
        assertThrows(java.util.concurrent.CancellationException.class, () -> resultFuture.get());
    }
}
