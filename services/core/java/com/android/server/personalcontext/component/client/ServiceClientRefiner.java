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

import android.annotation.EnforcePermission;
import android.annotation.PermissionManuallyEnforced;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PermissionEnforcer;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.hint.PublishedContextHintWrapper;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.interaction.InsightEvent;
import android.service.personalcontext.refiner.IGetFilterCallback;
import android.service.personalcontext.refiner.IRefineCallback;
import android.service.personalcontext.refiner.IRefiner;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.server.personalcontext.AccessController;
import com.android.server.personalcontext.OperatingModeProvider;
import com.android.server.personalcontext.RefinerWorkflow;
import com.android.server.personalcontext.component.Refiner;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Client for refiner services.
 *
 * @hide
 */
public class ServiceClientRefiner extends BaseServiceClientComponent<IRefiner> implements Refiner {
    private static final String TAG = "ServiceClientRefiner";

    private HintFilter mFilter = null;

    public ServiceClientRefiner(
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

    protected ServiceClientRefiner(
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

        runWithScopedBinder((binder, opCallback) -> {
            try {
                binder.getFilter(getParcelComponentId(), new IGetFilterCallback.Stub() {
                    @PermissionManuallyEnforced
                    @Override
                    public void updateFilter(HintFilter filter) {
                        mFilter = filter;
                    }
                }, opCallback);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to get renderer filter", e);
            }
        });
    }

    @Override
    public Set<Set<PublishedContextHint>> getInterestedHintClusters(
            Set<PublishedContextHint> allContextHints, Set<UUID> seenIDs, boolean isFirstRun) {
        if (mFilter == null) {
            return null;
        } else {
            final Set<PublishedContextHint> hints = mFilter.getInterestedHintClusters(
                    allContextHints, seenIDs);
            return hints == null || hints.isEmpty() ? null : Set.of(hints);
        }
    }

    @Override
    protected IRefiner getServiceWrapper(IBinder binder) {
        return IRefiner.Stub.asInterface(binder);
    }

    @Override
    protected void initializeClient(IRefiner client) {
    }

    @Override
    public void refine(
            @NonNull Set<PublishedContextHint> inputHints,
            @NonNull Consumer<Set<ContextHint>> callback,
            @NonNull RefinerWorkflow.InsightConsumer insightCallback) {
        if (!isAllowed(AccessController.ACCESS_RECEIVE_HINTS_PERMISSION)) {
            Slog.w(TAG, getComponentName() + " is not allowed to receive hints.");
            callback.accept(Collections.emptySet());
            return;
        }

        final IRefineCallback.Stub refinerCallback =
                new IRefineCallback.Stub(PermissionEnforcer.fromContext(mContext)) {
                    // Suppressing warning as enforcement is currently behind a flag
                    @SuppressWarnings("MissingEnforcePermissionHelper")
                    @EnforcePermission(android.Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS)
                    @Override
                    public void onHintsRefined(List<ContextHintWrapper> hints) {
                        enforcePermissions(getCallingPid(), getCallingUid(),
                                AccessController.ACCESS_PUBLISH_HINTS_PERMISSION);

                        callback.accept(ContextHintWrapper.unwrapInto(hints, new HashSet<>()));
                    }

                    @PermissionManuallyEnforced
                    @Override
                    public void onUnderstood(List<ContextInsightWrapper> insights) {
                        enforcePermissions(getCallingPid(), getCallingUid(),
                                AccessController.ACCESS_PUBLISH_INSIGHTS_PERMISSION);

                        insightCallback.accept(
                                getComponentId(),
                                ContextInsightWrapper.unwrapInto(insights, new HashSet<>()));
                    }
                };

        final List<PublishedContextHintWrapper> hints =
                PublishedContextHintWrapper.wrapList(inputHints);

        runWithScopedBinder((binder, opCallback) -> {
            try {
                binder.refine(getParcelComponentId(), hints, refinerCallback, opCallback);
            } catch (RemoteException e) {
                Slog.w(TAG, this + " refine() failed", e);
                callback.accept(Collections.emptySet());
            }
        });
    }

    @Override
    public void handleEvent(@NonNull String packageName, @NonNull InsightEvent event) {
        runWithScopedBinder((binder, opCallback) -> {
            try {
                binder.handleEvent(getParcelComponentId(), packageName, event, opCallback);
            } catch (RemoteException e) {
                Slog.w(TAG, this + " handleEvent() failed", e);
            }
        });
    }

    @Override
    public void handleFeedback(PublishedContextInsight insight, Bundle feedback) {
        throw new IllegalStateException("Refiners do not support feedback");
    }
}
