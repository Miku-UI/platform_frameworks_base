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

package com.android.server.textclassifier.personalcontext;

import android.annotation.NonNull;
import android.os.OutcomeReceiver;
import android.text.TextUtils;
import android.util.Slog;
import android.view.textclassifier.TextClassification;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.textclassifier.personalcontext.PersonalContextBridge.TextClassificationKey;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Retriever that supports asynchronously getting Personal Context results with a timeout. */
public class PersonalContextAsyncReceiver {

    private static final String TAG = "PersonalContextAsyncReceiver";

    /** Based on MAX_PENDING_REQUESTS of TextClassificationManagerService */
    @VisibleForTesting static final int MAX_SESSIONS = 20;

    private final ScheduledExecutorService mScheduledExecutorService;
    private final ConcurrentHashMap<TextClassificationKey, TextClassification> mIdToResults =
            new ConcurrentHashMap<>(MAX_SESSIONS);

    @GuardedBy("mIdToResults")
    private final ConcurrentLinkedQueue<TextClassificationKey> mSessions =
            new ConcurrentLinkedQueue<>();

    @GuardedBy("mIdToResults")
    private final ConcurrentHashMap<TextClassificationKey, PersonalContextBridgeTask> mIdToTasks =
            new ConcurrentHashMap<>(MAX_SESSIONS);

    private final long mTimeoutInMillis;

    PersonalContextAsyncReceiver(
            ScheduledExecutorService scheduledExecutorService, long timeoutInMillis) {
        mScheduledExecutorService = scheduledExecutorService;
        mTimeoutInMillis = timeoutInMillis;
    }

    /**
     * Puts TextClassification result in the results map. If there is a receiver awaiting this
     * result, notify and pass the result to the receiver, and cancel the timeout task. Else, put
     * the result in the result map to wait for immediate retrieval in {@link #getAsync}.
     */
    public void put(
            @NonNull TextClassificationKey key, @NonNull TextClassification textClassification) {
        if (mIdToTasks.containsKey(key)) {
            synchronized (mIdToResults) {
                mIdToTasks.get(key).receiver.onResult(textClassification);
                mIdToTasks.get(key).cancellationTask.cancel(false);
                clearSession(key);
            }
        } else {
            synchronized (mIdToResults) {
                mIdToResults.put(key, textClassification);
                mSessions.add(key);
                maybePopOldestSession();
            }
        }
    }

    /**
     * Gets result for the TextClassification session into the receiver.
     *
     * <p>If result for session is already present, immediately call receiver. Else, register the
     * receiver to await until {@link #put} notifies the receiver, or the timeout task cancels the
     * task.
     *
     * <p>Only one get request can be made for a session at once. If there already is a request
     * waiting, it'll be overridden by the more recent request.
     */
    public void getAsync(
            @NonNull TextClassificationKey key,
            @NonNull OutcomeReceiver<TextClassification, TimeoutException> callback) {
        if (mIdToTasks.containsKey(key)) {
            Slog.w(TAG, "Only one callback is allowed to wait on given session.");
            mIdToTasks
                    .get(key)
                    .receiver
                    .onError(new TimeoutException("Overridden by new request."));
            mIdToTasks.get(key).cancellationTask.cancel(true);
            mIdToTasks.remove(key);
        }
        if (mIdToResults.containsKey(key)) {
            synchronized (mIdToResults) {
                callback.onResult(mIdToResults.remove(key));
                clearSession(key);
            }
            return;
        }

        ScheduledFuture<?> cancellationTask =
                mScheduledExecutorService.schedule(
                        () -> {
                            callback.onError(
                                    new TimeoutException(
                                            "Timed out while waiting for personal context result"));
                            clearSession(key);
                        },
                        mTimeoutInMillis,
                        TimeUnit.MILLISECONDS);
        synchronized (mIdToResults) {
            mIdToTasks.put(key, new PersonalContextBridgeTask(callback, cancellationTask));
            mSessions.add(key);
            maybePopOldestSession();
        }
    }

    /**
     * Clears the cache of any session results or tasks.
     *
     * <p>Used when session is destroyed before personal context service returns or result is
     * requested.
     */
    private void clearSession(TextClassificationKey key) {
        synchronized (mIdToResults) {
            mSessions.remove(key);
            mIdToResults.remove(key);
            mIdToTasks.remove(key);
        }
    }

    /**
     * Clears the cache of any session results or tasks.
     *
     * <p>Used when session is destroyed before personal context service returns
     */
    public void clearSession(String sessionId) {
        synchronized (mIdToResults) {
            for (TextClassificationKey foundSession :
                    mSessions.stream()
                            .filter(key -> TextUtils.equals(key.sessionId(), sessionId))
                            .toList()) {
                mSessions.remove(foundSession);
                mIdToResults.remove(foundSession);
                mIdToTasks.remove(foundSession);
            }
        }
    }

    /** Pops oldest session if adding a new session has exceeded the limit. */
    @GuardedBy("mIdToResults")
    private void maybePopOldestSession() {
        if (mSessions.size() <= MAX_SESSIONS) {
            return;
        }
        TextClassificationKey oldestSession = mSessions.poll();
        mIdToResults.remove(oldestSession);
        if (mIdToTasks.containsKey(oldestSession)) {
            mIdToTasks.get(oldestSession).cancellationTask.cancel(false);
            mIdToTasks.remove(oldestSession);
        }
    }

    private record PersonalContextBridgeTask(
            OutcomeReceiver<TextClassification, TimeoutException> receiver,
            ScheduledFuture<?> cancellationTask) {}
}
