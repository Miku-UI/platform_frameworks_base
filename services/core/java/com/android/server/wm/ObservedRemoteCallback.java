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
package com.android.server.wm;

import android.os.Bundle;
import android.os.IRemoteCallback;
import android.os.RemoteException;
import android.util.Slog;

/**
 * {@link IRemoteCallback} decorator to observe whether base callback was called.
 */
final class ObservedRemoteCallback extends IRemoteCallback.Stub {
    private static final String TAG = "ObservedRemoteCallback";

    private boolean mAnyResultSent = false;
    private final IRemoteCallback mCallback;

    ObservedRemoteCallback(IRemoteCallback callback) {
        mCallback = callback;
    }

    /**
     * Sends a {@code data} as a fallback callback execution, only if callback was not already
     * invoked, otherwise it's a no-op.
     *
     * @param onFallback executed if fallback is actually triggered
     */
    void sendFallbackResult(Bundle data, Runnable onFallback) throws RemoteException {
        synchronized (this) {
            if (!mAnyResultSent) {
                onFallback.run();
                sendResult(data);
            }
        }
    }

    @Override
    public void sendResult(Bundle data) throws RemoteException {
        synchronized (this) {
            if (mAnyResultSent) {
                Slog.w(TAG,
                        "Attempted to invoke callback more than once. Skipping sending"
                                + " result with bundle=" + data);
                return;
            }
            mAnyResultSent = true;
            mCallback.sendResult(data);
        }
    }
}
