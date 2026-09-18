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
package com.android.server.companion.datatransfer.crossdevicesync.network.messenger;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class MessageHandleTest extends SyncServiceTestBase {
    private static final String HANDLE_NAME = "test";
    private static final int MAX_ATTEMPTS = 3;
    private static final long TEST_HANDLE_ID = 1L;

    private MessageHandle mHandle;
    private final Object mLock = new Object();

    @Before
    public void setUp() {
        mHandle =
                new MessageHandle(
                        mLock,
                        mFakeClock,
                        TEST_HANDLE_ID,
                        MAX_ATTEMPTS,
                        HANDLE_NAME,
                        /* message= */ null);
    }

    @Test
    public void get_success_returnsTrue() throws Exception {
        AtomicBoolean result = new AtomicBoolean(false);
        Thread getter =
                new Thread(
                        () -> {
                            try {
                                result.set(mHandle.get());
                            } catch (Exception e) {
                                // Should not happen
                            }
                        });

        getter.start();
        mHandle.noteSuccess(mFakeClock.elapsedRealtime());
        getter.join();

        assertThat(result.get()).isTrue();
    }

    @Test
    public void get_cancelled_throwsCancellationException() throws InterruptedException {
        AtomicReference<Exception> exception = new AtomicReference<>();
        Thread getter =
                new Thread(
                        () -> {
                            try {
                                mHandle.get();
                            } catch (Exception e) {
                                exception.set(e);
                            }
                        });

        getter.start();
        mHandle.cancel(true);
        getter.join();

        assertThat(exception.get()).isInstanceOf(CancellationException.class);
    }

    @Test
    public void get_failed_throwsExecutionException() throws InterruptedException {
        AtomicReference<Exception> exception = new AtomicReference<>();
        Thread getter =
                new Thread(
                        () -> {
                            try {
                                mHandle.get();
                            } catch (Exception e) {
                                exception.set(e);
                            }
                        });

        getter.start();
        mHandle.noteSending(mFakeClock.elapsedRealtime());
        mHandle.noteSendingFailure(mFakeClock.elapsedRealtime(), new Exception("failure"));
        mHandle.noteSending(mFakeClock.elapsedRealtime());
        mHandle.noteSendingFailure(mFakeClock.elapsedRealtime(), new Exception("failure"));
        mHandle.noteSending(mFakeClock.elapsedRealtime());
        mHandle.noteSendingFailure(mFakeClock.elapsedRealtime(), new Exception("failure"));
        getter.join();

        assertThat(exception.get()).isInstanceOf(ExecutionException.class);
    }

    @Test
    public void getHandleId() {
        assertThat(mHandle.getHandleId()).isEqualTo(TEST_HANDLE_ID);
    }

    @Test
    public void listenerTriggeredWhenMessageDelivered() {
        boolean[] executed = {false};
        mHandle.whenComplete((res, ex) -> executed[0] = true);

        mHandle.noteSuccess(mFakeClock.elapsedRealtime());

        assertThat(executed[0]).isTrue();
    }

    @Test
    public void listenerTriggeredWhenMessageFailedEnoughTimes() {
        boolean[] executed = {false};
        mHandle.whenComplete((res, ex) -> executed[0] = true);

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            mHandle.noteSending(mFakeClock.elapsedRealtime());
            mHandle.noteSendingFailure(mFakeClock.elapsedRealtime(), new Exception());
        }

        assertThat(mHandle.getState()).isEqualTo(MessageHandle.STATE_FAILED);
        assertThat(executed[0]).isTrue();
    }

    @Test
    public void listenerTriggeredWhenTransportRequestFailedEnoughTimes() {
        boolean[] executed = {false};
        mHandle.whenComplete((res, ex) -> executed[0] = true);

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            mHandle.noteTransportFailure(mFakeClock.elapsedRealtime(), new Exception());
        }

        assertThat(mHandle.getState()).isEqualTo(MessageHandle.STATE_FAILED);
        assertThat(executed[0]).isTrue();
    }

    @Test
    public void listenerTriggeredWhenTransportAndMessageFailedEnoughTimes() {
        boolean[] executed = {false};
        mHandle.whenComplete((res, ex) -> executed[0] = true);

        mHandle.noteTransportFailure(mFakeClock.elapsedRealtime(), new Exception());
        mHandle.noteSending(mFakeClock.elapsedRealtime());
        mHandle.noteSendingFailure(mFakeClock.elapsedRealtime(), new Exception());
        mHandle.noteSending(mFakeClock.elapsedRealtime());
        mHandle.noteSendingFailure(mFakeClock.elapsedRealtime(), new Exception());

        assertThat(mHandle.getState()).isEqualTo(MessageHandle.STATE_FAILED);
        assertThat(executed[0]).isTrue();
    }

    @Test
    public void listenerTriggeredWhenCancelled() {
        boolean[] executed = {false};
        mHandle.whenComplete((res, ex) -> executed[0] = true);

        mHandle.cancel(true);

        assertThat(mHandle.getState()).isEqualTo(MessageHandle.STATE_CANCELLED);
        assertThat(executed[0]).isTrue();
    }

    @Test
    public void newListenerTriggeredImmediatelyWhenDone() {
        mHandle.noteSuccess(mFakeClock.elapsedRealtime());
        boolean[] executed = {false};

        mHandle.whenComplete((res, ex) -> executed[0] = true);

        assertThat(executed[0]).isTrue();
    }

    @Test
    public void noteSending_stateChanged() {
        mHandle.noteSending(mFakeClock.elapsedRealtime());

        assertThat(mHandle.getState()).isEqualTo(MessageHandle.STATE_SENDING);
    }

    @Test
    public void noteSendingFailure_stateChanged() {
        mHandle.noteSending(mFakeClock.elapsedRealtime());

        mHandle.noteSendingFailure(mFakeClock.elapsedRealtime(), new Exception());

        assertThat(mHandle.getState()).isEqualTo(MessageHandle.STATE_PENDING_RETRY);
    }

    @Test
    public void noteTransportFailure_stateUnchanged() {
        mHandle.noteTransportFailure(mFakeClock.elapsedRealtime(), new Exception());

        assertThat(mHandle.getState()).isEqualTo(MessageHandle.STATE_WAITING_FOR_TRANSPORT);
    }

    @Test
    public void noteSuccess_stateChanged() {
        mHandle.noteSuccess(mFakeClock.elapsedRealtime());

        assertThat(mHandle.getState()).isEqualTo(MessageHandle.STATE_DELIVERED);
    }

    @Test
    public void isDone_returnsCorrectly() {
        assertThat(mHandle.isDone()).isFalse();

        mHandle.noteSending(mFakeClock.elapsedRealtime());
        mHandle.noteSendingFailure(mFakeClock.elapsedRealtime(), new Exception());
        assertThat(mHandle.isDone()).isFalse(); // Still pending retry

        mHandle.noteSuccess(mFakeClock.elapsedRealtime());
        assertThat(mHandle.isDone()).isTrue();

        mHandle =
                new MessageHandle(
                        new Object(),
                        mFakeClock,
                        TEST_HANDLE_ID,
                        MAX_ATTEMPTS,
                        HANDLE_NAME,
                        /* message= */ null);
        mHandle.cancel(true);
        assertThat(mHandle.isDone()).isTrue();

        mHandle =
                new MessageHandle(
                        new Object(),
                        mFakeClock,
                        TEST_HANDLE_ID,
                        /* maxAttempts= */ 1,
                        HANDLE_NAME,
                        /* message= */ null);
        mHandle.noteSending(mFakeClock.elapsedRealtime());
        mHandle.noteSendingFailure(mFakeClock.elapsedRealtime(), new Exception());
        assertThat(mHandle.isDone()).isTrue(); // Failed
    }
}
