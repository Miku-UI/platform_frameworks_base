/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.ondeviceintelligence;

import android.app.ondeviceintelligence.embedding.EmbeddingModel;
import android.app.ondeviceintelligence.embedding.EmbeddingResponse;
import android.app.ondeviceintelligence.embedding.IEmbeddingCallback;
import android.app.ondeviceintelligence.embedding.IEmbeddingModelCallback;
import android.app.ondeviceintelligence.embedding.IEmbeddingModelListCallback;
import android.app.ondeviceintelligence.imagedescription.IImageDescriptionCallback;
import android.app.ondeviceintelligence.imagedescription.IImageDescriptionModelCallback;
import android.app.ondeviceintelligence.imagedescription.IImageDescriptionModelListCallback;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionModel;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionResponse;
import android.os.PersistableBundle;
import android.os.RemoteException;

import com.android.internal.infra.AndroidFuture;

import java.util.List;

/**
 * Util methods for ensuring callbacks are properly handled with futures i.e. they are completed
 * upon success or failure.
 */
// TODO: b/479090677 - Refactor this class to handle common logic in a single method.
public class CallbackUtil {
    private static final String TAG = "CallbackUtil";

    private CallbackUtil() {}

    /**
     * Wraps an {@link IEmbeddingModelListCallback} to complete the provided future upon success or
     * failure.
     */
    public static IEmbeddingModelListCallback wrapWithCompletion(
            IEmbeddingModelListCallback callback, AndroidFuture<Void> future) {
        return new IEmbeddingModelListCallback.Stub() {
            @Override
            public void onSuccess(List<EmbeddingModel> result) throws RemoteException {
                completeFuture(future, () -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, PersistableBundle errorParams)
                    throws RemoteException {
                completeFuture(
                        future, () -> callback.onFailure(errorCode, errorMessage, errorParams));
            }
        };
    }

    /**
     * Wraps an {@link IEmbeddingModelCallback} to complete the provided future upon success or
     * failure.
     */
    public static IEmbeddingModelCallback wrapWithCompletion(
            IEmbeddingModelCallback callback, AndroidFuture<Void> future) {
        return new IEmbeddingModelCallback.Stub() {
            @Override
            public void onSuccess(EmbeddingModel result) throws RemoteException {
                completeFuture(future, () -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, PersistableBundle errorParams)
                    throws RemoteException {
                completeFuture(
                        future, () -> callback.onFailure(errorCode, errorMessage, errorParams));
            }
        };
    }

    /**
     * Wraps an {@link IEmbeddingCallback} to complete the provided future upon success or failure.
     */
    public static IEmbeddingCallback wrapWithCompletion(
            IEmbeddingCallback callback, AndroidFuture<Void> future) {
        return new IEmbeddingCallback.Stub() {
            @Override
            public void onSuccess(EmbeddingResponse result) throws RemoteException {
                completeFuture(future, () -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, PersistableBundle errorParams)
                    throws RemoteException {
                completeFuture(
                        future, () -> callback.onFailure(errorCode, errorMessage, errorParams));
            }
        };
    }

    /**
     * Wraps an {@link IImageDescriptionModelListCallback} to complete the provided future upon
     * success or failure.
     */
    public static IImageDescriptionModelListCallback wrapWithCompletion(
            IImageDescriptionModelListCallback callback, AndroidFuture<Void> future) {
        return new IImageDescriptionModelListCallback.Stub() {
            @Override
            public void onSuccess(List<ImageDescriptionModel> result) throws RemoteException {
                completeFuture(future, () -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, PersistableBundle errorParams)
                    throws RemoteException {
                completeFuture(
                        future, () -> callback.onFailure(errorCode, errorMessage, errorParams));
            }
        };
    }

    /**
     * Wraps an {@link IImageDescriptionModelCallback} to complete the provided future upon success
     * or failure.
     */
    public static IImageDescriptionModelCallback wrapWithCompletion(
            IImageDescriptionModelCallback callback, AndroidFuture<Void> future) {
        return new IImageDescriptionModelCallback.Stub() {
            @Override
            public void onSuccess(ImageDescriptionModel result) throws RemoteException {
                completeFuture(future, () -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, PersistableBundle errorParams)
                    throws RemoteException {
                completeFuture(
                        future, () -> callback.onFailure(errorCode, errorMessage, errorParams));
            }
        };
    }

    /**
     * Wraps an {@link IImageDescriptionCallback} to complete the provided future upon success or
     * failure.
     */
    public static IImageDescriptionCallback wrapWithCompletion(
            IImageDescriptionCallback callback, AndroidFuture<Void> future) {
        return new IImageDescriptionCallback.Stub() {
            @Override
            public void onSuccess(ImageDescriptionResponse result) throws RemoteException {
                completeFuture(future, () -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, PersistableBundle errorParams)
                    throws RemoteException {
                completeFuture(
                        future, () -> callback.onFailure(errorCode, errorMessage, errorParams));
            }

            @Override
            public void onNewText(String text) throws RemoteException {
                callback.onNewText(text);
            }
        };
    }

    private static void completeFuture(AndroidFuture<Void> future, ThrowingRunnable runnable)
            throws RemoteException {
        try {
            runnable.run();
        } finally {
            future.complete(null);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws RemoteException;
    }
}
