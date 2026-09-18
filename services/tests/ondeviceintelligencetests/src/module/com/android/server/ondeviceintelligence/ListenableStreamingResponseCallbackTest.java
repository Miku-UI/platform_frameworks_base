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

package com.android.server.ondeviceintelligence;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.ondeviceintelligence.IStreamingResponseCallback;
import android.app.ondeviceintelligence.InferenceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.RemoteCallback;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.AndroidFuture;
import com.android.server.ondeviceintelligence.callbacks.ListenableStreamingResponseCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class ListenableStreamingResponseCallbackTest {
    private IStreamingResponseCallback mMockCallback;
    private AndroidFuture<Void> mFuture;
    private Handler mHandler;
    private static final long TEST_TIMEOUT_MS = 200;
    private static final long TIMEOUT_WAIT_MS = TEST_TIMEOUT_MS + 100;

    @Before
    public void setUp() {
        mHandler = new Handler(Looper.getMainLooper());
        mMockCallback = mock(IStreamingResponseCallback.class);
        mFuture = new AndroidFuture<>();
    }

    @After
    public void tearDown() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    @Test
    public void onPartialResult_resetsTimeout()
            throws RemoteException, InterruptedException, ExecutionException {
        ListenableStreamingResponseCallback callback =
                new ListenableStreamingResponseCallback(
                        mMockCallback, mHandler, mFuture, TEST_TIMEOUT_MS);
        final CountDownLatch latch = new CountDownLatch(1);
        mFuture.whenComplete((res, ex) -> latch.countDown());

        // Wait a bit, but less than timeout
        assertFalse(latch.await(TEST_TIMEOUT_MS - 50, TimeUnit.MILLISECONDS));

        // Send content, should reset timer
        Bundle bundle = new Bundle();
        callback.onPartialResult(bundle);
        verify(mMockCallback).onPartialResult(bundle);

        // Wait a bit more, still less than new timeout
        assertFalse(latch.await(TEST_TIMEOUT_MS - 50, TimeUnit.MILLISECONDS));

        // Wait for timeout to happen
        assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(mFuture.isDone());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> mFuture.get());
        assertTrue(ex.getCause() instanceof TimeoutException);
    }

    @Test
    public void onSuccess_completesFuture() throws RemoteException {
        ListenableStreamingResponseCallback callback =
                new ListenableStreamingResponseCallback(
                        mMockCallback, mHandler, mFuture, TEST_TIMEOUT_MS);
        Bundle bundle = new Bundle();
        callback.onSuccess(bundle);

        verify(mMockCallback).onSuccess(bundle);
        assertTrue(mFuture.isDone());
        assertFalse(mFuture.isCompletedExceptionally());
    }

    @Test
    public void onFailure_completesFuture() throws RemoteException {
        ListenableStreamingResponseCallback callback =
                new ListenableStreamingResponseCallback(
                        mMockCallback, mHandler, mFuture, TEST_TIMEOUT_MS);
        PersistableBundle bundle = new PersistableBundle();
        callback.onFailure(0, "error", bundle);

        verify(mMockCallback).onFailure(0, "error", bundle);
        assertTrue(mFuture.isDone());
        assertFalse(mFuture.isCompletedExceptionally());
    }

    @Test
    public void onDataAugmentRequest_resetsTimeout()
            throws RemoteException, InterruptedException, ExecutionException {
        ListenableStreamingResponseCallback callback =
                new ListenableStreamingResponseCallback(
                        mMockCallback, mHandler, mFuture, TEST_TIMEOUT_MS);
        final CountDownLatch latch = new CountDownLatch(1);
        mFuture.whenComplete((res, ex) -> latch.countDown());

        assertFalse(latch.await(TEST_TIMEOUT_MS - 50, TimeUnit.MILLISECONDS));

        Bundle bundle = new Bundle();
        RemoteCallback remoteCallback = mock(RemoteCallback.class);
        callback.onDataAugmentRequest(bundle, remoteCallback);
        verify(mMockCallback).onDataAugmentRequest(bundle, remoteCallback);

        assertFalse(latch.await(TEST_TIMEOUT_MS - 50, TimeUnit.MILLISECONDS));

        // Wait for timeout to happen
        assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(mFuture.isDone());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> mFuture.get());
        assertTrue(ex.getCause() instanceof TimeoutException);
    }

    @Test
    public void timeout_completesFutureExceptionally()
            throws InterruptedException, ExecutionException {
        new ListenableStreamingResponseCallback(mMockCallback, mHandler, mFuture, TEST_TIMEOUT_MS);
        final CountDownLatch latch = new CountDownLatch(1);
        mFuture.whenComplete((res, ex) -> latch.countDown());

        assertTrue(latch.await(TIMEOUT_WAIT_MS, TimeUnit.MILLISECONDS));

        assertTrue(mFuture.isDone());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> mFuture.get());
        assertTrue(ex.getCause() instanceof TimeoutException);
    }

    @Test
    public void onInferenceInfo_doesNotResetTimeout() throws Exception {
        ListenableStreamingResponseCallback callback =
                new ListenableStreamingResponseCallback(
                        mMockCallback, mHandler, mFuture, TEST_TIMEOUT_MS);
        final CountDownLatch latch = new CountDownLatch(1);
        mFuture.whenComplete((res, ex) -> latch.countDown());

        assertFalse(latch.await(TEST_TIMEOUT_MS / 2, TimeUnit.MILLISECONDS));

        InferenceInfo info = new InferenceInfo.Builder(1).build();
        callback.onInferenceInfo(info);
        verify(mMockCallback).onInferenceInfo(info);

        assertTrue(
                latch.await(TEST_TIMEOUT_MS / 2 + 50, TimeUnit.MILLISECONDS)); // wait for timeout
        assertTrue(mFuture.isDone());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> mFuture.get());
        assertTrue(ex.getCause() instanceof TimeoutException);
    }
}
