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
package com.android.server.appfunctions.observer;

import static com.android.server.appfunctions.AppFunctionExecutors.THREAD_POOL_EXECUTOR;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionSearchSpec;
import android.app.appfunctions.IObserveAppFunctionChangesCallback;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.observer.ObserverSpec;
import android.content.Context;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.SystemService;
import com.android.server.appfunctions.CallerIdentity;
import com.android.server.appfunctions.FutureGlobalSearchSession;
import com.android.server.appfunctions.MetadataSyncAdapter;
import com.android.server.appfunctions.MetadataSyncPerUser;
import com.android.server.appfunctions.ServiceConfig;
import com.android.server.appfunctions.VisibilityHelper;
import com.android.server.appfunctions.reader.AppFunctionMetadataReader;

import java.util.Objects;
import java.util.Set;

/**
 * Manages AppSearch observer registrations and notification routing for all users.
 *
 * <p>For each user, this class sets up an {@link AppFunctionMetadataObserverCallback2} to listen
 * for changes in AppSearch. It also maintains an {@link InternalObserverCallbackRouter} to dispatch
 * these changes to client-provided callbacks.
 */
public class AppFunctionMetadataObserver {
    private static final String TAG = AppFunctionMetadataObserver.class.getSimpleName();
    private static final String ANDROID_PACKAGE = "android";

    private final Object mRoutersLock = new Object();

    @GuardedBy("mRoutersLock")
    private final SparseArray<InternalObserverCallbackRouter> mInternalCallbackRouters =
            new SparseArray<>();

    @NonNull private final AppFunctionMetadataReader mAppFunctionMetadataReader;
    @NonNull private final ServiceConfig mServiceConfig;
    @NonNull private final VisibilityHelper mVisibilityHelper;

    @NonNull private final Context mContext;

    public AppFunctionMetadataObserver(
            @NonNull Context context,
            @NonNull AppFunctionMetadataReader appFunctionMetadataReader,
            @NonNull ServiceConfig serviceConfig,
            @NonNull VisibilityHelper visibilityHelper) {
        mAppFunctionMetadataReader = Objects.requireNonNull(appFunctionMetadataReader);
        mContext = Objects.requireNonNull(context);
        mServiceConfig = Objects.requireNonNull(serviceConfig);
        mVisibilityHelper = Objects.requireNonNull(visibilityHelper);
    }

    /** Registers a new {@link AppFunctionMetadataObserver} for {@code targetUser}. */
    public void registerAppSearchObserverForUser(@NonNull SystemService.TargetUser targetUser) {
        Context userContext =
                mContext.createContextAsUser(targetUser.getUserHandle(), /* flags= */ 0);
        MetadataSyncAdapter mPerUserMetadataSyncAdapter =
                MetadataSyncPerUser.getPerUserMetadataSyncAdapter(
                        targetUser.getUserHandle(), userContext);
        AppSearchManager perUserAppSearchManager =
                userContext.getSystemService(AppSearchManager.class);
        if (perUserAppSearchManager == null) {
            Slog.d(TAG, "AppSearch Manager not found for user: " + targetUser.getUserIdentifier());
            return;
        }
        FutureGlobalSearchSession futureGlobalSearchSession =
                new FutureGlobalSearchSession(perUserAppSearchManager, THREAD_POOL_EXECUTOR);

        InternalObserverCallbackRouter userCallbackRouter =
                new InternalObserverCallbackRouter(
                        futureGlobalSearchSession, mServiceConfig, mVisibilityHelper);
        AppFunctionMetadataObserverCallback2 observerCallback =
                new AppFunctionMetadataObserverCallback2(
                        mPerUserMetadataSyncAdapter,
                        userCallbackRouter,
                        mAppFunctionMetadataReader,
                        targetUser.getUserHandle());

        var unused =
                futureGlobalSearchSession
                        .registerObserverCallbackAsync(
                                ANDROID_PACKAGE,
                                new ObserverSpec.Builder().build(),
                                Runnable::run,
                                observerCallback)
                        .whenComplete(
                                (voidResult, ex) -> {
                                    if (ex != null) {
                                        Slog.e(TAG, "Failed to register observer: ", ex);
                                    } else {
                                        mAppFunctionMetadataReader.onMetadataObserveStartedForUser(
                                                targetUser.getUserHandle());
                                        synchronized (mRoutersLock) {
                                            mInternalCallbackRouters.put(
                                                    targetUser.getUserIdentifier(),
                                                    userCallbackRouter);
                                        }
                                    }
                                    futureGlobalSearchSession.close();
                                });
    }

    /** Unregisters the {@link AppFunctionMetadataObserver} for {@code targetUser}. */
    public void unregisterAppSearchObserverForUser(@NonNull SystemService.TargetUser targetUser) {
        MetadataSyncPerUser.removeUserSyncAdapter(targetUser.getUserHandle());
        synchronized (mRoutersLock) {
            if (mInternalCallbackRouters.contains(targetUser.getUserIdentifier())) {
                mInternalCallbackRouters.get(targetUser.getUserIdentifier()).shutDown();
                mInternalCallbackRouters.remove(targetUser.getUserIdentifier());
            }
        }
    }

    /**
     * Registers the provided {@link IObserveAppFunctionChangesCallback} to receive updates to app
     * functions matching the {@link AppFunctionSearchSpec} for a specific user.
     */
    public void registerClientAppCallback(
            @NonNull CallerIdentity callerIdentity,
            @NonNull IObserveAppFunctionChangesCallback proxyCallback)
            throws RemoteException {
        requireNonNull(callerIdentity);
        requireNonNull(proxyCallback);

        InternalObserverCallbackRouter router;
        synchronized (mRoutersLock) {
            router = mInternalCallbackRouters.get(callerIdentity.getUserHandle().getIdentifier());
        }
        if (router != null) {
            router.addCallback(proxyCallback, callerIdentity);
        } else {
            throw new IllegalStateException(
                    "Unable to register callback for user "
                            + callerIdentity.getUserHandle().toString());
        }
    }

    /**
     * Unregisters the given {@link IObserveAppFunctionChangesCallback} from receiving app functions
     * updates.
     */
    public void unregisterClientAppCallback(
            @NonNull CallerIdentity callerIdentity,
            @NonNull IObserveAppFunctionChangesCallback proxyCallback) {
        requireNonNull(callerIdentity);
        requireNonNull(proxyCallback);

        InternalObserverCallbackRouter router;
        synchronized (mRoutersLock) {
            router = mInternalCallbackRouters.get(callerIdentity.getUserHandle().getIdentifier());
        }
        if (router != null) {
            router.removeCallback(proxyCallback, callerIdentity);
        }
    }

    /** Notifies observers of a change to an app function's enabled state. */
    public void onEnabledStatesChanged(
            @NonNull UserHandle userHandle, @NonNull Set<AppFunctionName> functionNames) {
        requireNonNull(userHandle);
        requireNonNull(functionNames);

        InternalObserverCallbackRouter router;

        synchronized (mRoutersLock) {
            router = mInternalCallbackRouters.get(userHandle.getIdentifier());
        }

        if (router != null) {
            router.onEnabledStatesChanged(functionNames);
        }
    }
}
