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

package com.android.server.companion.datatransfer.continuity.handoff;

import android.annotation.NonNull;
import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class HandoffRequestCallbackHolder {

    private static final String TAG = "HandoffRequestCallbackHolder";

    @GuardedBy("mCallbacks")
    private final RemoteCallbackList<IHandoffRequestCallback> mCallbacks
        = new RemoteCallbackList<>();

    private record RequestCookie(int associationId, int taskId) {}

    /**
     * Registers a callback for the given association and task.
     *
     * @param associationId The association ID of the handoff request.
     * @param taskId The task ID of the handoff request.
     * @param callback The callback to register.
     */
    public void registerCallback(
        int associationId,
        int taskId,
        @NonNull IHandoffRequestCallback callback) {

        Objects.requireNonNull(callback);
        synchronized (mCallbacks) {
            Slog.i(
                TAG,
                "Registering HandoffRequestCallback for association " + associationId + " and task "
                + taskId);

            mCallbacks.register(callback, new RequestCookie(associationId, taskId));
        }
    }

    /**
     * Notifies all callbacks for the given association and task, and removes them from the list of
     * pending callbacks.
     *
     * @param associationId The association ID of the handoff request.
     * @param taskId The task ID of the handoff request.
     * @param statusCode The status code of the handoff request.
     */
    public void notifyAndRemoveCallbacks(int associationId, int taskId, int statusCode) {
        synchronized (mCallbacks) {
            Slog.i(
                TAG,
                "Notifying HandoffRequestCallbacks for association " + associationId + " and task "
                + taskId + " of status code " + statusCode);

            RequestCookie request = new RequestCookie(associationId, taskId);
            List<IHandoffRequestCallback> callbacksToRemove = new ArrayList<>();
            mCallbacks.broadcast(
                (callback, cookie) -> {
                    if (request.equals(cookie)) {
                        try {
                            callback.onHandoffRequestFinished(associationId, taskId, statusCode);
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Failed to notify callback of handoff request result", e);
                        }

                        callbacksToRemove.add(callback);
                    }
                }
            );

            clearCallbacks(callbacksToRemove);
        }
    }

    private void clearCallbacks(@NonNull List<IHandoffRequestCallback> callbacks) {
        Objects.requireNonNull(callbacks);
        synchronized (mCallbacks) {
            Slog.i(TAG, "Clearing " + callbacks.size() + " callbacks.");
            for (IHandoffRequestCallback callback : callbacks) {
                mCallbacks.unregister(callback);
            }
        }
    }
}