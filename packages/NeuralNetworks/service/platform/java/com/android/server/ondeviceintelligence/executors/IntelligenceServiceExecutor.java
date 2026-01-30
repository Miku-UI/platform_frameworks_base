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

package com.android.server.ondeviceintelligence.executors;

import android.Manifest;
import android.os.RemoteException;
import android.service.ondeviceintelligence.IOnDeviceIntelligenceService;
import android.util.Slog;

import com.android.internal.infra.AndroidFuture;
import com.android.server.ondeviceintelligence.OnDeviceIntelligenceManagerService;

import java.util.concurrent.TimeoutException;

/**
 * An executor for making remote calls to the {@link IOnDeviceIntelligenceService}.
 *
 * <p>This class centralizes the logic for permission checks, ensuring the remote service is
 * available, and handling various failure scenarios like timeouts or remote exceptions before
 * dispatching the call.
 */
public final class IntelligenceServiceExecutor
        extends RemoteCallExecutor<IOnDeviceIntelligenceService> {

    private IntelligenceServiceExecutor(Builder builder) {
        super(builder);
    }

    @Override
    public AndroidFuture<?> execute(RemoteCallRunner<IOnDeviceIntelligenceService> remoteCall) {
        OnDeviceIntelligenceManagerService manager =
                OnDeviceIntelligenceManagerService.getInstance();
        manager.getContext()
                .enforceCallingPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE,
                        OnDeviceIntelligenceManagerService.TAG);
        if (!manager.isServiceEnabled()) {
            Slog.w(OnDeviceIntelligenceManagerService.TAG, "Service not available");
            executeOnRemoteExecutor(() -> {
                try {
                    mFailureConsumer.accept(FailureType.SERVICE_UNAVAILABLE);
                } catch (RemoteException e) {
                    Slog.e(OnDeviceIntelligenceManagerService.TAG,
                            "Failed to call service unavailable callback", e);
                }
            });
            return null;
        }

        // Ensure the remote service is initialized.
        manager.ensureRemoteIntelligenceServiceInitialized(/* shouldThrow= */ true);

        AndroidFuture<?> future =
                manager.getRemoteOnDeviceIntelligenceService().postAsync(remoteCall::run);
        future.whenComplete(
                (res, ex) -> {
                    if (ex != null) {
                        Slog.e(
                                OnDeviceIntelligenceManagerService.TAG,
                                "Remote intelligence service call failed",
                                ex);
                        if (ex instanceof TimeoutException) {
                            executeOnRemoteExecutor(() -> {
                                try {
                                    mFailureConsumer.accept(FailureType.TIMEOUT);
                                } catch (RemoteException e) {
                                    Slog.e(OnDeviceIntelligenceManagerService.TAG,
                                            "Failed to call timeout callback", e);
                                }
                            });
                        } else {
                            executeOnRemoteExecutor(() -> {
                                try {
                                    mFailureConsumer.accept(FailureType.REMOTE_FAILURE);
                                } catch (RemoteException e) {
                                    Slog.e(OnDeviceIntelligenceManagerService.TAG,
                                            "Failed to call remote failure callback", e);
                                }
                            });
                        }
                    }
                });
        return future;
    }

    /** Builder for {@link IntelligenceServiceExecutor}. */
    public static class Builder
            extends RemoteCallExecutor.Builder<IOnDeviceIntelligenceService, Builder> {

        public Builder() {
            // empty constructor.
        }

        @Override
        public IntelligenceServiceExecutor build() {
            return new IntelligenceServiceExecutor(this);
        }
    }
}
