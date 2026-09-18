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

package com.android.server.contentsafety;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Settings;
import android.service.contentsafety.IContentSafetySandboxedService;
import android.util.Slog;

import com.android.internal.infra.ServiceConnector;
import com.android.server.contentsafety.ContentSafetyManagerService.SandboxedServiceType;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Manages the connection to the remote content safety sand boxed service. Also, handles unbinding
 * logic set by the service implementation via a SecureSettings flag.
 */
public class RemoteContentSafetySandboxedService
        extends ServiceConnector.Impl<IContentSafetySandboxedService> {
    private static final String TAG = "RemoteContentSafetySandboxedService";

    private final Intent mIntent;
    private final int mBindingFlags;
    @SandboxedServiceType private final int mSandboxedServiceType;
    private static final long LONG_TIMEOUT = TimeUnit.HOURS.toMillis(1);

    /**
     * Creates an instance of {@link ServiceConnector}
     *
     * <p>See {@code protected} methods for optional parameters you can override.
     *
     * @param context to be used for {@link Context#bindServiceAsUser binding} and {@link
     *     Context#unbindService unbinding}
     * @param serviceIntent to bond to the service.
     * @param bindingFlags flags to be attached at binding time.
     * @param userId to be used for {@link Context#bindServiceAsUser binding}
     * @param sandboxedServiceType either PCC or Isolated.
     */
    RemoteContentSafetySandboxedService(
            @NonNull Context context,
            @NonNull Intent serviceIntent,
            int bindingFlags,
            int userId,
            @Nullable Function<IBinder, IContentSafetySandboxedService> binderAsInterface,
            @SandboxedServiceType int sandboxedServiceType) {

        super(context, serviceIntent, bindingFlags, userId, binderAsInterface);
        this.mIntent = serviceIntent;
        this.mBindingFlags = bindingFlags;
        this.mSandboxedServiceType = sandboxedServiceType;
    }

    @Override
    protected boolean bindService(@NonNull android.content.ServiceConnection serviceConnection) {
        try {
            boolean bindResult = false;
            if (mSandboxedServiceType != SandboxedServiceType.SERVICE_TYPE_PCC) {
                bindResult =
                        mContext.bindIsolatedService(
                                mIntent,
                                mBindingFlags,
                                "Sandboxed_isolated_service",
                                mExecutor,
                                serviceConnection);
                if (!bindResult) {
                    Slog.w(
                            TAG,
                            "bindService failure mSandboxedServiceType = " + mSandboxedServiceType);
                }
            } else {
                mContext.bindService(mIntent, serviceConnection, mBindingFlags);
            }
            return bindResult;
        } catch (IllegalArgumentException e) {
            Slog.wtf(
                    TAG,
                    "Can't bind to the remote sandboxed service of type " + mSandboxedServiceType,
                    e);
            return false;
        }
    }

    @Override
    protected long getRequestTimeoutMs() {
        return LONG_TIMEOUT;
    }

    @Override
    protected long getAutoDisconnectTimeoutMs() {
        return Settings.Secure.getLongForUser(
                mContext.getContentResolver(),
                Settings.Secure.CONTENT_SAFETY_SANDBOXED_UNBIND_TIMEOUT_MS,
                TimeUnit.SECONDS.toMillis(5),
                mContext.getUserId());
    }
}
