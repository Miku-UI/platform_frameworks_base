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


import android.annotation.PermissionManuallyEnforced;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.insight.InsightFilter;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.renderer.IGetFilterCallback;
import android.service.personalcontext.renderer.IInsightRenderer;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.server.personalcontext.AccessController;
import com.android.server.personalcontext.OperatingModeProvider;
import com.android.server.personalcontext.component.Renderer;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Client for renderer services.
 *
 * @hide
 */
public class ServiceClientRenderer
        extends BaseServiceClientComponent<IInsightRenderer> implements Renderer {
    private static final String TAG = "ServiceClientRenderer";
    private InsightFilter mFilter = InsightFilter.REQUIRE_RENDER_TOKEN;

    private final int mProperties = 0;

    public ServiceClientRenderer(
            Context context,
            AccessController accessController,
            UUID componentId,
            ServiceInfo serviceInfo,
            UserHandle userHandle,
            OperatingModeProvider operatingModeProvider) {
        this(
                context,
                accessController,
                componentId,
                serviceInfo,
                userHandle,
                Executors.newSingleThreadExecutor(),
                new Handler(Looper.getMainLooper()),
                operatingModeProvider);
    }

    protected ServiceClientRenderer(
            Context context,
            AccessController accessController,
            UUID componentId,
            ServiceInfo serviceInfo,
            UserHandle userHandle,
            Executor executor,
            Handler handler,
            OperatingModeProvider operatingModeProvider) {
        super(context, accessController, componentId, serviceInfo, userHandle, executor, handler,
                operatingModeProvider);

        // Always connect to the service, whether we can fetch a filter or not, so that it can
        // distribute its RenderTokens.
        runWithScopedBinder((binder, callback) -> {
            try {
                binder.getFilter(getParcelComponentId(), new IGetFilterCallback.Stub() {
                    @PermissionManuallyEnforced
                    @Override
                    public void updateFilter(InsightFilter filter) {
                        // We check to see if a filter is allowed after requesting it to make sure
                        // that the renderer gets an onConnected call.
                        if (isAllowed(AccessController.ACCESS_FILTER_INSIGHTS_ALLOWLIST)) {
                            mFilter = filter;
                        } else {
                            Slog.d(TAG, getComponentName()
                                    + " is not allowed to filter for insights.");
                        }
                    }
                }, callback);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to get renderer filter", e);
            }
        });
    }

    @Override
    public int getProperties() {
        return mProperties;
    }

    @Override
    protected IInsightRenderer getServiceWrapper(IBinder binder) {
        return IInsightRenderer.Stub.asInterface(binder);
    }

    @Override
    protected void initializeClient(IInsightRenderer client) {
    }

    @Override
    public boolean isInterestedInInsight(PublishedContextInsight insight) {
        return mFilter.isInterestedInInsight(insight.getInsight());
    }

    @Override
    public void render(@NonNull PublishedContextInsight publishedContextInsight,
            RenderToken renderToken) {
        if (!isAllowed(
                AccessController.ACCESS_PCC_OR_AUTO_COMPANION_ROLE
                | AccessController.ACCESS_RECEIVE_INSIGHTS_PERMISSION
                | AccessController.ACCESS_RECEIVE_INSIGHTS_ALLOWLIST)) {
            Slog.w(TAG, getComponentName() + " is not allowed to receive insights.");
            return;
        }

        runWithScopedBinder((binder, opCallback) -> {
            try {
                binder.render(getParcelComponentId(),
                        new PublishedContextInsightWrapper(publishedContextInsight),
                        renderToken,
                        opCallback);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to render insight", e);
            }
        });
    }
}
