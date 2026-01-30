/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.ondeviceintelligence.IDownloadCallback;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.RemoteException;

import com.android.internal.infra.AndroidFuture;

import java.util.concurrent.TimeoutException;

/**
 * This class extends the {@link IDownloadCallback} and adds a timeout Runnable to the callback such
 * that, in the case where the callback methods are not invoked, we do not have to wait for timeout
 * based on {@link #onDownloadCompleted} which might take minutes or hours to complete in some
 * cases. Instead, in such cases we rely on the remote service sending progress updates and if there
 * are *no* progress callbacks in the duration of {@link #idleTimeoutMs}, we can assume the download
 * will not complete and enabling faster cleanup.
 */
public class ListenableDownloadCallback extends IDownloadCallback.Stub implements Runnable {
    private final IDownloadCallback callback;
    private final Handler handler;
    private final AndroidFuture<?> future;
    private final long idleTimeoutMs;

    /**
     * Constructor to create a ListenableDownloadCallback.
     *
     * @param callback callback to send download updates to caller.
     * @param handler handler to schedule timeout runnable.
     * @param future future to complete to signal the callback has reached a terminal state.
     * @param idleTimeoutMs timeout within which download updates should be received.
     */
    public ListenableDownloadCallback(
            IDownloadCallback callback, Handler handler, AndroidFuture<?> future,
            long idleTimeoutMs) {
        this.callback = callback;
        this.handler = handler;
        this.future = future;
        this.idleTimeoutMs = idleTimeoutMs;
        resetTimeout(); // init the timeout runnable in case no callback is ever invoked
    }

    @Override
    public void onDownloadStarted(long bytesToDownload) throws RemoteException {
        callback.onDownloadStarted(bytesToDownload);
        resetTimeout();
    }

    @Override
    public void onDownloadProgress(long bytesDownloaded) throws RemoteException {
        callback.onDownloadProgress(bytesDownloaded);
        resetTimeout();
    }

    @Override
    public void onDownloadFailed(
            int failureStatus, String errorMessage, PersistableBundle errorParams)
            throws RemoteException {
        callback.onDownloadFailed(failureStatus, errorMessage, errorParams);
        complete();
    }

    @Override
    public void onDownloadCompleted(android.os.PersistableBundle downloadParams)
            throws RemoteException {
        callback.onDownloadCompleted(downloadParams);
        complete();
    }

    @Override
    public void run() {
        future.completeExceptionally(
                new TimeoutException()); // complete the future as we haven't received updates
        // for download progress.
    }

    private void resetTimeout() {
        handler.removeCallbacks(this);
        handler.postDelayed(this, idleTimeoutMs);
    }

    private void complete() {
        handler.removeCallbacks(this);
        future.complete(null);
    }
}
