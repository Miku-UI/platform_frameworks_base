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

package com.android.server.personalcontext.component.client;

import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.util.Slog;

import com.android.server.personalcontext.AccessController;
import com.android.server.personalcontext.OperatingModeProvider;

import java.util.UUID;

/**
 * Client for understander services.
 *
 * @hide
 */
public class ServiceClientUnderstander extends ServiceClientRefiner {
    private static final String TAG = "ServiceClientUnderstander";

    public ServiceClientUnderstander(
            Context context,
            AccessController accessController,
            UUID componentId,
            ServiceInfo serviceInfo,
            UserHandle userHandle,
            OperatingModeProvider operatingModeProvider) {
        super(context, accessController, componentId, serviceInfo, userHandle,
                operatingModeProvider);
    }

    @Override
    public void handleFeedback(PublishedContextInsight insight, Bundle feedback) {
        runWithScopedBinder((binder, opCallback) -> {
            try {
                binder.handleFeedback(getParcelComponentId(),
                        new PublishedContextInsightWrapper(insight),
                        feedback,
                        opCallback);
            } catch (RemoteException e) {
                Slog.w(TAG, this + " handleFeedback() failed", e);
            }
        });
    }
}
