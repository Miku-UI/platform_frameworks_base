/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.ondeviceintelligence.callbacks;

import android.app.ondeviceintelligence.IStreamingResponseCallback;
import android.app.ondeviceintelligence.InferenceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.RemoteCallback;
import android.os.RemoteException;

import com.android.internal.infra.AndroidFuture;

import java.util.concurrent.TimeoutException;

/**
 * This class extends the {@link IStreamingResponseCallback} and adds a timeout Runnable to the
 * callback such that, in the case where the callback methods are not invoked, we do not have to
 * wait for timeout. Instead, in such cases we rely on the remote service sending partial results
 * and if there are *no* invocations of {@link #onPartialResult} in the duration of
 * {@link #mIdleTimeoutMs}, we can assume the streaming will not complete, enabling faster cleanup.
 */
public class ListenableStreamingResponseCallback extends IStreamingResponseCallback.Stub
        implements Runnable {
    private final IStreamingResponseCallback mCallback;
    private final Handler mHandler;
    private final AndroidFuture<?> mFuture;
    private final long mIdleTimeoutMs;

    /**
     * Constructor to create a ListenableStreamingResponseCallback.
     *
     * @param callback      callback to send streaming updates to caller.
     * @param handler       handler to schedule timeout runnable.
     * @param future        future to complete to signal the callback has reached a terminal state.
     * @param idleTimeoutMs timeout within which download updates should be received.
     */
    public ListenableStreamingResponseCallback(
            IStreamingResponseCallback callback, Handler handler, AndroidFuture<?> future,
            long idleTimeoutMs) {
        this.mCallback = callback;
        this.mHandler = handler;
        this.mFuture = future;
        this.mIdleTimeoutMs = idleTimeoutMs;
        resetTimeout(); // init the timeout runnable in case no callback is ever invoked
    }

    @Override
    public void onPartialResult(Bundle processedResult) throws RemoteException {
        mCallback.onPartialResult(processedResult);
        resetTimeout();
    }

    @Override
    public void onSuccess(Bundle resultBundle) throws RemoteException {
        mCallback.onSuccess(resultBundle);
        complete();
    }

    @Override
    public void onFailure(int errorCode, String errorMessage, PersistableBundle errorParams)
            throws RemoteException {
        mCallback.onFailure(errorCode, errorMessage, errorParams);
        complete();
    }

    @Override
    public void onDataAugmentRequest(Bundle processedContent, RemoteCallback remoteCallback)
            throws RemoteException {
        mCallback.onDataAugmentRequest(processedContent, remoteCallback);
        resetTimeout();
    }

    @Override
    public void onInferenceInfo(InferenceInfo info) throws RemoteException {
        mCallback.onInferenceInfo(info);
    }

    @Override
    public void run() {
        mFuture.completeExceptionally(
                new TimeoutException("Did not receive streaming updates within timeout."));
    }

    private void resetTimeout() {
        mHandler.removeCallbacks(this);
        mHandler.postDelayed(this, mIdleTimeoutMs);
    }

    private void complete() {
        mHandler.removeCallbacks(this);
        mFuture.complete(null);
    }
}
