/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.inputmethod;

import android.view.inputmethod.ImeTracker;

import com.android.internal.infra.AndroidFuture;

/**
 * Interface to the global IME tracker service, used by all client applications.
 * @hide
 */
oneway interface IImeTracker {

    /**
     * Called when an IME request is started.
     *
     * @param statsToken the token tracking the request.
     * @param uid the uid of the client that started the request.
     * @param type the type of the request.
     * @param origin the origin of the request.
     * @param reason the reason for starting the request.
     * @param fromUser whether this request was created directly from user interaction.
     * @param startWallTimeMs the wall time in milliseconds when the request was started.
     * @param startTimestampMs the time since boot in milliseconds when the request was started.
     */
    void onStart(in ImeTracker.Token statsToken, int uid, int type, int origin, int reason,
        boolean fromUser, long startWallTimeMs, long startTimestampMs);

    /**
     * Called when the IME request progresses to a further phase.
     *
     * @param statsToken the token tracking the request.
     * @param phase the new phase the request reached.
     */
    void onProgress(in ImeTracker.Token statsToken, int phase);

    /**
     * Called when the IME request fails.
     *
     * @param statsToken the token tracking the request.
     * @param phase the phase the request failed at.
     */
    void onFailed(in ImeTracker.Token statsToken, int phase);

    /**
     * Called when the IME request is cancelled.
     *
     * @param statsToken the token tracking the request.
     * @param phase the phase the request was cancelled at.
     */
    void onCancelled(in ImeTracker.Token statsToken, int phase);

    /**
     * Called when the show IME request is successful.
     *
     * @param statsToken the token tracking the request.
     */
    void onShown(in ImeTracker.Token statsToken);

    /**
     * Called when the hide IME request is successful.
     *
     * @param statsToken the token tracking the request.
     */
    void onHidden(in ImeTracker.Token statsToken);

    /**
     * Called when the user-controlled IME request was dispatched to the requesting app. The
     * user animation can take an undetermined amount of time, so it shouldn't be tracked.
     *
     * @param statsToken the token tracking the request.
     */
    void onDispatched(in ImeTracker.Token statsToken);

    /**
     * Waits until there are no more pending IME visibility requests, up to a given timeout, and
     * notifies the given future.
     *
     * @param future    the future to notify.
     * @param timeoutMs the timeout in milliseconds.
     */
    @EnforcePermission("TEST_INPUT_METHOD")
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(value = "
            + "android.Manifest.permission.TEST_INPUT_METHOD)")
    void waitUntilNoPendingRequests(in AndroidFuture<void> future, long timeoutMs);

    /**
     * Finishes the tracking of any pending IME visibility requests and notifies the given future.
     * This won't stop the actual requests, but allows resetting the state when starting test runs.
     *
     * @param future the future to notify.
     */
    @EnforcePermission("TEST_INPUT_METHOD")
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(value = "
            + "android.Manifest.permission.TEST_INPUT_METHOD)")
    void finishTrackingPendingRequests(in AndroidFuture<void> future);
}
