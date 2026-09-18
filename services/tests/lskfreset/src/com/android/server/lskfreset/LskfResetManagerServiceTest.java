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

package com.android.server.lskfreset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.lskfreset.ILskfResetManager;
import android.app.lskfreset.ILskfResetSession;
import android.app.lskfreset.flags.Flags;
import android.content.Context;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class LskfResetManagerServiceTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final UserHandle TEST_USER_0 = UserHandle.getUserHandleForUid(1000);
    private static final UserHandle TEST_USER_1 = UserHandle.getUserHandleForUid(1001);

    private FakeScheduledExecutorService mExecutor;

    private Context mContext;
    private LskfResetManagerService mService;

    private class TestInjector extends LskfResetManagerService.Injector {
        @Override
        ScheduledExecutorService getExecutor() {
            return mExecutor;
        }
    }

    @Before
    public void setUp() {
        mExecutor = new FakeScheduledExecutorService(Thread.currentThread());
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mService = new LskfResetManagerService(mContext, new TestInjector());
        mService.onStart();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testServiceStarts() {
        ILskfResetManager manager = mService.getBinderService();
        assertNotNull(manager);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testCreateSession() throws Exception {
        ILskfResetManager manager = mService.getBinderService();

        ILskfResetSession session = manager.createLskfResetSession(TEST_USER_0);
        assertNotNull(session);
        assertTrue(mService.isSessionActive(session));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testCloseSession() throws Exception {
        ILskfResetManager manager = mService.getBinderService();

        ILskfResetSession session0 = manager.createLskfResetSession(TEST_USER_0);
        ILskfResetSession session1 = manager.createLskfResetSession(TEST_USER_1);
        assertTrue(mService.isSessionActive(session0));
        assertTrue(mService.isSessionActive(session1));

        session0.close();
        assertFalse(mService.isSessionActive(session0));
        assertTrue(mService.isSessionActive(session1));

        session1.close();
        assertFalse(mService.isSessionActive(session0));
        assertFalse(mService.isSessionActive(session1));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testDoubleCloseSessionFails() throws Exception {
        ILskfResetManager manager = mService.getBinderService();
        ILskfResetSession session = manager.createLskfResetSession(TEST_USER_0);
        assertTrue(mService.isSessionActive(session));
        session.close();
        assertFalse(mService.isSessionActive(session));
        assertThrows(IllegalStateException.class, () -> session.close());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testCreateParallelSessions() throws Exception {
        ILskfResetManager manager = mService.getBinderService();

        final int numThreads = 10;
        CountDownLatch createLatch = new CountDownLatch(numThreads);
        CountDownLatch closeLatch = new CountDownLatch(numThreads);
        for (int i = 0; i < numThreads; ++i) {
            Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    ILskfResetSession session;
                                    try {
                                        session = manager.createLskfResetSession(TEST_USER_0);
                                        assertTrue(mService.isSessionActive(session));
                                    } finally {
                                        createLatch.countDown();
                                    }
                                    createLatch.await();
                                    session.close();
                                    assertFalse(mService.isSessionActive(session));
                                } catch (Exception e) {
                                    fail("Unexpected exception: " + e.getMessage());
                                } finally {
                                    closeLatch.countDown();
                                }
                            });
            thread.start();
        }
        closeLatch.await();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testCloseParallelSessions() throws Exception {
        ILskfResetManager manager = mService.getBinderService();
        ILskfResetSession session = manager.createLskfResetSession(TEST_USER_0);

        final int numThreads = 10;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch closeLatch = new CountDownLatch(numThreads);
        for (int i = 0; i < numThreads; ++i) {
            Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    session.close();
                                    successCount.incrementAndGet();
                                } catch (IllegalStateException unused) {
                                    failureCount.incrementAndGet();
                                } catch (Exception e) {
                                    fail("Unexpected exception: " + e.getMessage());
                                } finally {
                                    closeLatch.countDown();
                                }
                            });
            thread.start();
        }
        closeLatch.await();
        assertEquals(1, successCount.get());
        assertEquals(numThreads - 1, failureCount.get());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testSessionDeath() throws Exception {
        ILskfResetManager manager = mService.getBinderService();

        ILskfResetSession session = manager.createLskfResetSession(TEST_USER_0);
        assertTrue(mService.isSessionActive(session));

        mService.killSession(session);
        assertFalse(mService.isSessionActive(session));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testSessionTimeout() throws Exception {
        ILskfResetManager manager = mService.getBinderService();
        ILskfResetSession session = manager.createLskfResetSession(TEST_USER_0);

        // Session is active at the start.
        assertTrue(mService.isSessionActive(session));
        assertEquals(1, mExecutor.numTasks());

        // Session should not time out after 30s.
        assertEquals(0, mExecutor.fastForwardMillis(TimeUnit.SECONDS.toMillis(30)));
        assertTrue(mService.isSessionActive(session));
        assertEquals(1, mExecutor.numTasks(), 1);

        // Session should time out after 65s.
        assertEquals(1, mExecutor.fastForwardMillis(TimeUnit.SECONDS.toMillis(35)));
        assertFalse(mService.isSessionActive(session));
        assertEquals(0, mExecutor.numTasks());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testClosedSessionsDoNotTimeOut() throws Exception {
        ILskfResetManager manager = mService.getBinderService();

        ILskfResetSession session = manager.createLskfResetSession(TEST_USER_0);
        assertTrue(mService.isSessionActive(session));
        assertEquals(1, mExecutor.numTasks());

        session.close();
        assertFalse(mService.isSessionActive(session));
        assertEquals(0, mExecutor.numTasks());

        // Nothing should happen even after 65s.
        assertEquals(0, mExecutor.fastForwardMillis(TimeUnit.SECONDS.toMillis(65)));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testSessionTimeoutsAreIndependent() throws Exception {
        ILskfResetManager manager = mService.getBinderService();

        // Start a first session
        ILskfResetSession session0 = manager.createLskfResetSession(TEST_USER_0);
        assertTrue(mService.isSessionActive(session0));
        assertEquals(1, mExecutor.numTasks());

        // Start a second session 30s later.
        assertEquals(0, mExecutor.fastForwardMillis(TimeUnit.SECONDS.toMillis(30)));
        ILskfResetSession session1 = manager.createLskfResetSession(TEST_USER_1);
        assertTrue(mService.isSessionActive(session0));
        assertTrue(mService.isSessionActive(session1));
        assertEquals(2, mExecutor.numTasks());

        // After 70s the first session should be timed out but not the second.
        assertEquals(1, mExecutor.fastForwardMillis(TimeUnit.SECONDS.toMillis(40)));
        assertFalse(mService.isSessionActive(session0));
        assertTrue(mService.isSessionActive(session1));
        assertEquals(1, mExecutor.numTasks());

        // After 100s the second session should be timed out as well.
        assertEquals(1, mExecutor.fastForwardMillis(TimeUnit.SECONDS.toMillis(30)));
        assertFalse(mService.isSessionActive(session0));
        assertFalse(mService.isSessionActive(session1));
        assertEquals(0, mExecutor.numTasks());
    }
}
