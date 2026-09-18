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
import android.app.RemoteAction;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.service.textclassifier.ITextClassifierCallback;
import android.service.textclassifier.TextClassifierService;
import android.util.Log;
import android.util.Slog;
import android.view.textclassifier.TextClassification;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.personalcontext.PersonalContextManagerInternal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

public class PersonalContextBridgeImpl extends PersonalContextBridge {
    private static final String TAG = "PersonalContextBridge";
    private final ScheduledExecutorService mScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor();
    private final PersonalContextAsyncReceiver mReceiver;
    private final Config mConfig;

    @Override
    public void trigger(int userId, String sessionId, TextClassification.Request request) {
        PersonalContextManagerInternal pcmi =
                LocalServices.getService(PersonalContextManagerInternal.class);
        if (pcmi == null) {
            Slog.w(TAG, "Did not find PersonalContextManagerInternal system service");
            return;
        }
        mReceiver.clearSession(sessionId);
        pcmi.onTextClassifyRequest(userId, sessionId, request);
    }

    @Override
    public void merge(@NonNull TextClassificationKey key, TextClassification response) {
        mReceiver.put(key, response);
    }

    @Override
    public ITextClassifierCallback wrap(
            @NonNull String sessionId,
            @NonNull TextClassification.Request request,
            @NonNull ITextClassifierCallback callback) {
        if (!isPersonalContextEnabled()) {
            return callback;
        }
        return new MergeCallback(new TextClassificationKey(sessionId, request), callback);
    }

    @Override
    public void clearSession(@NonNull String sessionId) {
        mReceiver.clearSession(sessionId);
    }

    private class MergeCallback extends ITextClassifierCallback.Stub {
        private final TextClassificationKey mSession;
        private final ITextClassifierCallback mWrappedCallback;

        MergeCallback(
                TextClassificationKey session, @NonNull ITextClassifierCallback wrappedCallback) {
            mSession = session;
            mWrappedCallback = Objects.requireNonNull(wrappedCallback);
        }

        @Override
        public void onSuccess(Bundle result) {
            merge(result);
        }

        @Override
        public void onFailure() {
            try {
                mWrappedCallback.onFailure();
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to forward failure callback.");
            }
        }

        private void merge(Bundle result) {
            TextClassification originalResult = TextClassifierService.getResponse(result);
            mReceiver.getAsync(
                    mSession,
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(TextClassification personalContextResult) {
                            List<RemoteAction> mergedActions =
                                    mergeResults(
                                            originalResult.getActions(),
                                            personalContextResult.getActions(),
                                            mConfig.mMergeStrategy());
                            try {
                                TextClassifierService.putResponse(
                                        result,
                                        originalResult.toBuilder()
                                                .clearActions()
                                                .addActions(mergedActions)
                                                .build());
                                mWrappedCallback.onSuccess(result);
                            } catch (RemoteException e) {
                                Slog.e(TAG, "Failed to forward success callback.");
                            }
                        }

                        private static List<RemoteAction> mergeResults(
                                List<RemoteAction> originalActions,
                                List<RemoteAction> personalContextActions,
                                @MergeStrategy int mergeStrategy) {
                            List<RemoteAction> mergedActions = new ArrayList<>();
                            switch (mergeStrategy) {
                                case DEFAULT_PRIORITY:
                                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                                        Log.d(
                                                TAG,
                                                "Adding "
                                                        + originalActions.size()
                                                        + " default actions then "
                                                        + personalContextActions.size()
                                                        + "personal context actions");
                                    }
                                    mergedActions.addAll(originalActions);
                                    mergedActions.addAll(
                                            dedupeActions(originalActions, personalContextActions));
                                    break;
                                case PERSONAL_CONTEXT_PRIORITY:
                                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                                        Log.d(
                                                TAG,
                                                "Adding "
                                                        + personalContextActions.size()
                                                        + "personal context actions then "
                                                        + originalActions.size()
                                                        + " default actions");
                                    }
                                    mergedActions.addAll(personalContextActions);
                                    mergedActions.addAll(
                                            dedupeActions(personalContextActions, originalActions));
                                    break;
                                case PERSONAL_CONTEXT_OVERRIDE:
                                    if (personalContextActions.isEmpty()) {
                                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                                            Log.d(
                                                    TAG,
                                                    "Personal context actions empty. Using "
                                                            + originalActions.size()
                                                            + " default actions");
                                        }
                                        mergedActions.addAll(originalActions);
                                    } else {
                                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                                            Log.d(
                                                    TAG,
                                                    "Using "
                                                            + personalContextActions.size()
                                                            + " personal context actions");
                                        }
                                        mergedActions.addAll(personalContextActions);
                                    }
                                    break;
                            }
                            return mergedActions;
                        }

                        private static List<RemoteAction> dedupeActions(
                                List<RemoteAction> prioritizedActions,
                                List<RemoteAction> actionsToDedupe) {
                            List<RemoteAction> dedupedActions = new ArrayList<>();
                            for (RemoteAction action : actionsToDedupe) {
                                if (prioritizedActions.contains(action)) {
                                    continue;
                                }
                                dedupedActions.add(action);
                            }
                            return dedupedActions;
                        }

                        @Override
                        public void onError(@NonNull TimeoutException exception) {
                            Slog.d(
                                    TAG,
                                    "Timed out waiting for personal context results.",
                                    exception);
                            try {
                                // Pass through original text classification result
                                mWrappedCallback.onSuccess(result);
                            } catch (RemoteException e) {
                                Slog.e(TAG, "Failed to forward success callback.");
                            }
                        }
                    });
        }
    }

    public PersonalContextBridgeImpl(Config config) {
        mConfig = config;
        mReceiver =
                new PersonalContextAsyncReceiver(
                        mScheduledExecutorService, mConfig.mTimeoutInMillis());
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Initialized with config: " + mConfig);
        }
    }

    @VisibleForTesting
    PersonalContextBridgeImpl(
            Config config, PersonalContextAsyncReceiver personalContextAsyncReceiver) {
        mConfig = config;
        mReceiver = personalContextAsyncReceiver;
    }
}
