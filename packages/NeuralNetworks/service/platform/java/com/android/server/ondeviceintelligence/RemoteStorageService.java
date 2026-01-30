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

package com.android.server.ondeviceintelligence;

import static com.android.server.ondeviceintelligence.BundleUtil.sanitizeStateParams;
import static com.android.server.ondeviceintelligence.BundleUtil.validatePfdReadOnly;
import static com.android.server.ondeviceintelligence.BundleUtil.tryCloseResource;

import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallback;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.Executor;
import com.android.internal.infra.AndroidFuture;
import android.app.ondeviceintelligence.Feature;
import android.service.ondeviceintelligence.IRemoteStorageService;
import android.util.Slog;

/**
 * Implementation of {@link IRemoteStorageService.Stub} for the OnDeviceIntelligenceManagerService.
 * This class handles calls from the sandboxed inference service to the main intelligence service
 * for storage-related operations.
 */
public class RemoteStorageService extends IRemoteStorageService.Stub {
    private static final String TAG = "RemoteStorageService";

    private final Runnable mInitRunnable;
    private final RemoteOnDeviceIntelligenceService mRemoteOnDeviceIntelligenceService;
    private final Executor mCallbackExecutor;
    private final Executor mResourceClosingExecutor;

    RemoteStorageService(
            Runnable initRunnable,
            RemoteOnDeviceIntelligenceService remoteOnDeviceIntelligenceService,
            Executor callbackExecutor,
            Executor resourceClosingExecutor) {
        mInitRunnable = initRunnable;
        mRemoteOnDeviceIntelligenceService = remoteOnDeviceIntelligenceService;
        mCallbackExecutor = callbackExecutor;
        mResourceClosingExecutor = resourceClosingExecutor;
    }

    @Override
    public void getReadOnlyFileDescriptor(
            String filePath, AndroidFuture<ParcelFileDescriptor> future) {
        mInitRunnable.run();
        AndroidFuture<ParcelFileDescriptor> pfdFuture = new AndroidFuture<>();
        mRemoteOnDeviceIntelligenceService.run(
                service -> service.getReadOnlyFileDescriptor(filePath, pfdFuture));
        pfdFuture.whenCompleteAsync(
                (pfd, error) -> {
                    try {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            validatePfdReadOnly(pfd);
                            future.complete(pfd);
                        }
                    } catch (BadParcelableException e) {
                        Slog.e(TAG, "Failed to send result", e);
                        future.completeExceptionally(e);
                    } finally {
                        tryClosePfd(pfd);
                    }
                },
                mCallbackExecutor);
    }

    @Override
    public void getReadOnlyFeatureFileDescriptorMap(
            Feature feature, RemoteCallback remoteCallback) {
        mInitRunnable.run();
        mRemoteOnDeviceIntelligenceService.run(
                service ->
                        service.getReadOnlyFeatureFileDescriptorMap(
                                feature,
                                new RemoteCallback(
                                        result ->
                                            handleFileDescriptorMapResult(
                                                        result, remoteCallback))));
    }

    @Override
    public void getFeatureMetadata(Feature feature, RemoteCallback remoteCallback) {
        mInitRunnable.run();
        mRemoteOnDeviceIntelligenceService.run(
                service ->
                        service.getFeatureMetadata(
                                feature,
                                new RemoteCallback(
                                        result -> handleMetadataResult(result, remoteCallback))));
    }

    private void handleFileDescriptorMapResult(Bundle result, RemoteCallback remoteCallback) {
        try {
            if (result == null) {
                remoteCallback.sendResult(null);
                return;
            }
            for (String key : result.keySet()) {
                ParcelFileDescriptor pfd = result.getParcelable(key, ParcelFileDescriptor.class);
                validatePfdReadOnly(pfd);
            }
            remoteCallback.sendResult(result);
        } catch (BadParcelableException e) {
            Slog.e(TAG, "Failed to send result", e);
            remoteCallback.sendResult(null);
        } finally {
            mResourceClosingExecutor.execute(() -> tryCloseResource(result));
        }
    }

    private void handleMetadataResult(Bundle result, RemoteCallback remoteCallback) {
        try {
            if (result == null) {
                remoteCallback.sendResult(null);
                return;
            }
            sanitizeStateParams(result);
            remoteCallback.sendResult(result);
        } catch (BadParcelableException e) {
            Slog.e(TAG, "Failed to send result", e);
            remoteCallback.sendResult(null);
        } finally {
            mResourceClosingExecutor.execute(() -> tryCloseResource(result));
        }
    }

    private static void tryClosePfd(ParcelFileDescriptor pfd) {
        if (pfd != null) {
            try {
                pfd.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close parcel file descriptor ", e);
            }
        }
    }
}
