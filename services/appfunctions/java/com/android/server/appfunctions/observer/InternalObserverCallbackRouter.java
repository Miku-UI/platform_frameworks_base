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

import static com.android.server.appfunctions.AppFunctionExecutors.SHARED_BACKGROUND_EXECUTOR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionStaticMetadataHelper;
import android.app.appfunctions.IObserveAppFunctionChangesCallback;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.GetSchemaResponse;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.SchemaChangeInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.os.BackgroundThread;
import com.android.server.appfunctions.CallerIdentity;
import com.android.server.appfunctions.FutureGlobalSearchSession;
import com.android.server.appfunctions.NamedThreadFactory;
import com.android.server.appfunctions.ServiceConfig;
import com.android.server.appfunctions.VisibilityHelper;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Routes app function change notifications to registered callbacks.
 *
 * <p>This class receives generic change notifications from AppSearch and dispatches them to the
 * appropriate {@link IObserveAppFunctionChangesCallback} instances.
 */
class InternalObserverCallbackRouter {
    private static final boolean DEBUG = Build.TYPE.equals("eng");
    private static final String TAG = InternalObserverCallbackRouter.class.getSimpleName();

    private final RemoteCallbackList<IObserveAppFunctionChangesCallback> mInternalCallbacks =
            new RemoteCallbackList<IObserveAppFunctionChangesCallback>() {
                @Override
                public void onCallbackDied(
                        IObserveAppFunctionChangesCallback callback, Object cookie) {
                    if (!(cookie instanceof CallerIdentity deadCallbackIdentity)) {
                        return;
                    }
                    synchronized (mFrozenStateLock) {
                        mFrozenCallbacks.remove(callback.asBinder());
                    }
                    maybeCleanupVisibilityCache(deadCallbackIdentity);
                }
            };

    private final Object mVisibilityCacheLock = new Object();

    @GuardedBy("mVisibilityCacheLock")
    private final ArrayMap<CallerIdentity, ArraySet<String>> mIdentityToVisiblePackages =
            new ArrayMap<>();

    private final Object mFrozenStateLock = new Object();

    // Value indicates if any updates have been received since the callback was frozen. To avoid
    // notifying the observer when nothing has changed, this could be very frequent depending on
    // how aggressive the cache process freezer is on the device.
    @GuardedBy("mFrozenStateLock")
    private final ArrayMap<IBinder, Boolean> mFrozenCallbacks = new ArrayMap<>();

    private final IBinderFrozenStateCallback mFrozenStateCallback =
            new IBinderFrozenStateCallback();

    private final int mMetadataChangeDebounceMs;
    private final long mEnabledStateDebounceMs;
    private final long mEnabledStateMaxDebounceMs;
    private final ScheduledExecutorService mExecutor;
    private final Object mDebounceLock = new Object();

    @GuardedBy("mDebounceLock")
    @Nullable
    private ScheduledFuture<?> mDebounceFuture;

    @GuardedBy("mDebounceLock")
    @NonNull
    private final Set<String> mPendingPackageChanges = new ArraySet<>();

    private final Object mEnabledStateDebounceLock = new Object();

    @GuardedBy("mEnabledStateDebounceLock")
    @Nullable
    private ScheduledFuture<?> mEnabledStateDebounceFuture;

    @GuardedBy("mEnabledStateDebounceLock")
    @NonNull
    private final Set<AppFunctionName> mPendingEnabledStateChanges = new ArraySet<>();

    @GuardedBy("mEnabledStateDebounceLock")
    private long mFirstEnabledStateChangeTimestampNanos;

    private final TimeSource mTimeSource;

    private final VisibilityHelper mVisibilityHelper;

    private final Executor mFrozenStateListenerExecutor;

    private final FutureGlobalSearchSession mFutureGlobalSearchSession;

    InternalObserverCallbackRouter(
            @NonNull FutureGlobalSearchSession futureGlobalSearchSession,
            @NonNull ServiceConfig serviceConfig,
            @NonNull VisibilityHelper visibilityHelper) {
        this(
                futureGlobalSearchSession,
                serviceConfig,
                visibilityHelper,
                Executors.newSingleThreadScheduledExecutor(
                        new NamedThreadFactory("AppFunctionsObserverRouter")),
                SystemClock::elapsedRealtimeNanos,
                SHARED_BACKGROUND_EXECUTOR);
    }

    @VisibleForTesting
    InternalObserverCallbackRouter(
            @NonNull FutureGlobalSearchSession futureGlobalSearchSession,
            @NonNull ServiceConfig serviceConfig,
            @NonNull VisibilityHelper visibilityHelper,
            @NonNull ScheduledExecutorService executor,
            @NonNull TimeSource timeSource,
            @NonNull Executor frozenStateListenerExecutor) {
        mFutureGlobalSearchSession = Objects.requireNonNull(futureGlobalSearchSession);
        mMetadataChangeDebounceMs =
                serviceConfig.getAppFunctionMetadataChangeDebounceMilliseconds();
        mEnabledStateDebounceMs =
                serviceConfig.getAppFunctionEnabledStateChangeDebounceMilliseconds();
        mEnabledStateMaxDebounceMs =
                serviceConfig.getAppFunctionEnabledStateChangeMaxDebounceMilliseconds();
        mVisibilityHelper = visibilityHelper;
        mExecutor = executor;
        mTimeSource = timeSource;
        mFrozenStateListenerExecutor = frozenStateListenerExecutor;
    }

    @VisibleForTesting
    interface TimeSource {
        long elapsedRealtimeNanos();
    }

    public void onDocumentChanged(@NonNull DocumentChangeInfo documentChangeInfo) {
        String changedPackageName =
                getPackageNameFromSchemaName(documentChangeInfo.getSchemaName());
        if (changedPackageName != null) {
            schedulePackageLevelChange(Set.of(changedPackageName));
        }
    }

    public void onSchemaChanged(@NonNull SchemaChangeInfo schemaChangeInfo) {
        Set<String> changedPackageNames = new ArraySet<>();
        for (String changedSchemaName : schemaChangeInfo.getChangedSchemaNames()) {
            String changedPackageName = getPackageNameFromSchemaName(changedSchemaName);
            if (changedPackageName != null) {
                changedPackageNames.add(changedPackageName);
            }
        }
        schedulePackageLevelChange(changedPackageNames);
    }

    /**
     * Notifies observers of a change to the enabled state of an app function.
     *
     * <p>This is used for the runtime enabled state changes, which do not originate from app
     * search.
     */
    public void onEnabledStatesChanged(@NonNull Set<AppFunctionName> changedFunctionNames) {
        scheduleEnabledStateChanges(changedFunctionNames);
    }

    private void scheduleEnabledStateChanges(@NonNull Set<AppFunctionName> changedFunctionNames) {
        if (changedFunctionNames.isEmpty()) {
            return;
        }

        synchronized (mEnabledStateDebounceLock) {
            if (mPendingEnabledStateChanges.isEmpty()) {
                // Use elapsedRealtimeNanos to ensure we account for time spent in deep sleep.
                mFirstEnabledStateChangeTimestampNanos = mTimeSource.elapsedRealtimeNanos();
            }
            mPendingEnabledStateChanges.addAll(changedFunctionNames);

            if (mEnabledStateDebounceFuture != null) {
                mEnabledStateDebounceFuture.cancel(false);
            }

            long now = mTimeSource.elapsedRealtimeNanos();
            // We impose a maximum deadline to prevent the scenario where the debounce is
            // extended indefinitely by incoming changes.
            long maxDeadline =
                    mFirstEnabledStateChangeTimestampNanos
                            + TimeUnit.MILLISECONDS.toNanos(mEnabledStateMaxDebounceMs);
            long targetTime = now + TimeUnit.MILLISECONDS.toNanos(mEnabledStateDebounceMs);

            long delayNanos = Math.min(targetTime, maxDeadline) - now;
            delayNanos = Math.max(0, delayNanos);

            Runnable scheduledRunnable =
                    () -> {
                        Set<AppFunctionName> functionsToNotify;
                        synchronized (mEnabledStateDebounceLock) {
                            functionsToNotify = new ArraySet<>(mPendingEnabledStateChanges);
                            mPendingEnabledStateChanges.clear();
                            mEnabledStateDebounceFuture = null;
                        }
                        if (!functionsToNotify.isEmpty()) {
                            onAppFunctionStatesChanged(functionsToNotify);
                        }
                    };

            try {
                mEnabledStateDebounceFuture =
                        mExecutor.schedule(scheduledRunnable, delayNanos, TimeUnit.NANOSECONDS);
            } catch (RejectedExecutionException ex) {
                Slog.w(
                        TAG,
                        "Failed to schedule enabled state change due to executor shutdown.",
                        ex);
            }
        }
    }

    private void schedulePackageLevelChange(@NonNull Set<String> changedPackageNames) {
        if (changedPackageNames.isEmpty()) {
            return;
        }

        synchronized (mDebounceLock) {
            if (mDebounceFuture != null) {
                mDebounceFuture.cancel(false);
            }
            mPendingPackageChanges.addAll(changedPackageNames);

            Runnable scheduledRunnable =
                    () -> {
                        Set<String> packagesToNotify;
                        synchronized (mDebounceLock) {
                            packagesToNotify = new ArraySet<>(mPendingPackageChanges);
                            mPendingPackageChanges.clear();
                            mDebounceFuture = null;
                        }
                        if (!packagesToNotify.isEmpty()) {
                            onPackageLevelChange(packagesToNotify);
                        }
                    };

            try {
                mDebounceFuture =
                        mExecutor.schedule(
                                scheduledRunnable,
                                mMetadataChangeDebounceMs,
                                TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ex) {
                Slog.w(
                        TAG,
                        "Failed to schedule package level change due to executor shutdown.",
                        ex);
            }
        }
    }

    private void onPackageLevelChange(@NonNull Set<String> changedPackageNames) {
        if (changedPackageNames.isEmpty()) {
            return;
        }
        mInternalCallbacks.broadcast(
                (callback, cookie) -> {
                    if (cookie instanceof CallerIdentity callerIdentity) {
                        synchronized (mFrozenStateLock) {
                            if (mFrozenCallbacks.containsKey(callback.asBinder())) {
                                mFrozenCallbacks.put(callback.asBinder(), true);
                                return;
                            }
                        }
                        try {
                            Set<String> packagesToNotify =
                                    filterPackagesToNotify(
                                            changedPackageNames,
                                            Objects.requireNonNull(callerIdentity));

                            if (!packagesToNotify.isEmpty()) {
                                callback.onPackagesChanged(new ArrayList<>(packagesToNotify));
                            }
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Failed to execute callback#onPackagesChanged.", e);
                        }
                    }
                });
    }

    private void onAppFunctionStatesChanged(@NonNull Set<AppFunctionName> changedFunctionNames) {
        if (changedFunctionNames.isEmpty()) {
            return;
        }
        mInternalCallbacks.broadcast(
                (callback, cookie) -> {
                    if (cookie instanceof CallerIdentity callerIdentity) {
                        synchronized (mFrozenStateLock) {
                            if (mFrozenCallbacks.containsKey(callback.asBinder())) {
                                mFrozenCallbacks.put(callback.asBinder(), true);
                                return;
                            }
                        }
                        try {
                            Set<AppFunctionName> functionsToNotify =
                                    filterFunctionsToNotify(
                                            changedFunctionNames,
                                            Objects.requireNonNull(callerIdentity));

                            if (!functionsToNotify.isEmpty()) {
                                callback.onAppFunctionStatesChanged(
                                        new ArrayList<>(functionsToNotify));
                            }
                        } catch (RemoteException e) {
                            Slog.e(
                                    TAG,
                                    "Failed to execute callback#onAppFunctionStatesChanged.",
                                    e);
                        }
                    }
                });
    }

    void shutDown() {
        mInternalCallbacks.kill();
        mExecutor.shutdown();
    }

    void addCallback(
            @NonNull IObserveAppFunctionChangesCallback callbackToAdd,
            @NonNull CallerIdentity callerIdentity) {
        mInternalCallbacks.register(callbackToAdd, callerIdentity);
        try {
            callbackToAdd
                    .asBinder()
                    .addFrozenStateChangeCallback(
                            mFrozenStateListenerExecutor, mFrozenStateCallback);
        } catch (RemoteException | UnsupportedOperationException e) {
            Slog.e(TAG, "Failed to register callback.", e);
        }
    }

    void removeCallback(
            @NonNull IObserveAppFunctionChangesCallback callbackToRemove,
            @NonNull CallerIdentity callerIdentity) {
        mInternalCallbacks.unregister(callbackToRemove);
        synchronized (mFrozenStateLock) {
            mFrozenCallbacks.remove(callbackToRemove.asBinder());
        }
        try {
            callbackToRemove.asBinder().removeFrozenStateChangeCallback(mFrozenStateCallback);
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            Slog.e(TAG, "Failed to remove frozen state change callback.", e);
        }
        maybeCleanupVisibilityCache(callerIdentity);
    }

    private Set<String> filterPackagesToNotify(
            @NonNull Set<String> changedPackageNames, @NonNull CallerIdentity callerIdentity) {
        Objects.requireNonNull(changedPackageNames);
        Objects.requireNonNull(callerIdentity);
        Set<String> visibleChangedPackages =
                mVisibilityHelper.filterVisiblePackages(changedPackageNames, callerIdentity);

        synchronized (mVisibilityCacheLock) {
            Set<String> visiblePackagesHistory =
                    mIdentityToVisiblePackages.computeIfAbsent(
                            callerIdentity, callerIdentity1 -> new ArraySet<>());
            // Incrementally update the cache with newly visible packages.
            // We intentionally do not remove packages that have become invisible since visibility
            // loss implies uninstallation, and we require these cache entries to successfully
            // notify all relevant observers of the uninstallation.
            visiblePackagesHistory.addAll(visibleChangedPackages);

            Set<String> packagesToNotify = new ArraySet<>(changedPackageNames);
            packagesToNotify.retainAll(visiblePackagesHistory);
            return packagesToNotify;
        }
    }

    private Set<AppFunctionName> filterFunctionsToNotify(
            @NonNull Set<AppFunctionName> changedFunctionNames,
            @NonNull CallerIdentity callerIdentity) {
        Objects.requireNonNull(changedFunctionNames);
        Objects.requireNonNull(callerIdentity);

        Set<AppFunctionName> visibleChangedFunctions =
                mVisibilityHelper.filterVisibleAppFunctions(changedFunctionNames, callerIdentity);

        synchronized (mVisibilityCacheLock) {
            Set<String> visiblePackagesHistory =
                    mIdentityToVisiblePackages.computeIfAbsent(
                            callerIdentity, callerIdentity1 -> new ArraySet<>());
            for (AppFunctionName functionName : visibleChangedFunctions) {
                visiblePackagesHistory.add(functionName.getPackageName());
            }

            Set<AppFunctionName> functionsToNotify = new ArraySet<>(changedFunctionNames);
            functionsToNotify.removeIf(
                    function -> !visiblePackagesHistory.contains(function.getPackageName()));
            return functionsToNotify;
        }
    }

    /** Removes cached visibility list for the given caller identity, if no longer needed. */
    private void maybeCleanupVisibilityCache(@NonNull CallerIdentity identityToRemove) {
        Runnable scheduledRunnable =
                () -> {
                    int callbacksSize = mInternalCallbacks.beginBroadcast();
                    try {
                        for (int i = 0; i < callbacksSize; i++) {
                            Object activeCookie = mInternalCallbacks.getBroadcastCookie(i);
                            if (identityToRemove.equals(activeCookie)) {
                                return;
                            }
                        }
                    } finally {
                        mInternalCallbacks.finishBroadcast();
                    }
                    synchronized (mVisibilityCacheLock) {
                        mIdentityToVisiblePackages.remove(identityToRemove);
                    }
                };
        try {
            mExecutor.execute(scheduledRunnable);
        } catch (RejectedExecutionException ex) {
            Slog.w(TAG, "Failed to execute visibility cleanup due to executor shutdown.", ex);
        }
    }

    private void notifyUnfrozenObserver(@NonNull IBinder who) {
        if (DEBUG) {
            Slog.d(TAG, "notifyUnfrozenObserver#started");
        }
        // The number of possible observer is limited since related permissions are all
        // either privileged or role-based. Therefore, instead of storing the changed package,
        // we query from AppSearch directly to get all packages to notify the unfrozen observer
        // to update the snapshot. If the number of possible observers becomes more in the future,
        // we can consider caching the changed packages instead.
        mFutureGlobalSearchSession
                .getSchema(
                        AppFunctionStaticMetadataHelper.APP_FUNCTION_INDEXER_PACKAGE,
                        AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_METADATA_DB)
                .thenComposeAsync(
                        result -> {
                            if (!result.isSuccess()) {
                                return AndroidFuture.failedFuture(
                                        new RuntimeException(
                                                "Unable to get AppFunction packages."
                                                        + result.getErrorMessage()));
                            }
                            GetSchemaResponse schemaResponse = result.getResultValue();
                            Set<String> appFunctionPackages = new ArraySet<>();
                            for (AppSearchSchema schema : schemaResponse.getSchemas()) {
                                if (!schema.getSchemaType()
                                        .startsWith(
                                                AppFunctionStaticMetadataHelper
                                                        .STATIC_SCHEMA_TYPE)) {
                                    continue;
                                }
                                String packageName =
                                        getPackageNameFromSchemaName(schema.getSchemaType());
                                if (packageName != null) {
                                    appFunctionPackages.add(packageName);
                                }
                            }
                            return AndroidFuture.completedFuture(appFunctionPackages);
                        },
                        mExecutor)
                .whenComplete(
                        (packages, exception) -> {
                            if (exception != null) {
                                Slog.e(TAG, "Failed to get app function packages.", exception);
                                return;
                            }
                            mInternalCallbacks.broadcast(
                                    (callback, cookie) -> {
                                        if (!who.equals(callback.asBinder())) {
                                            return;
                                        }
                                        if (cookie instanceof CallerIdentity callerIdentity) {
                                            synchronized (mFrozenStateLock) {
                                                boolean shouldNotify =
                                                        mFrozenCallbacks.getOrDefault(
                                                                callback.asBinder(), false);
                                                if (!shouldNotify) {
                                                    return;
                                                }
                                                mFrozenCallbacks.remove(callback.asBinder());
                                            }
                                            try {
                                                Set<String> packagesToNotify =
                                                        filterPackagesToNotify(
                                                                packages, callerIdentity);

                                                if (!packagesToNotify.isEmpty()) {
                                                    if (DEBUG) {
                                                        Slog.d(
                                                                TAG,
                                                                "notifyUnfrozenObserver#notifying "
                                                                        + callerIdentity
                                                                        + " with packages: "
                                                                        + packagesToNotify);
                                                    }
                                                    callback.onPackagesChanged(
                                                            new ArrayList<>(packagesToNotify));
                                                }
                                            } catch (RemoteException e) {
                                                Slog.e(
                                                        TAG,
                                                        "Failed to execute"
                                                                + " callback#onPackagesChanged.",
                                                        e);
                                            }
                                        }
                                    });
                        });
    }

    // AppSearch schema names for app functions are expected to be in the format
    // "schemaType-packageName".
    @Nullable
    private String getPackageNameFromSchemaName(String schemaName) {
        int indexBeforePackageName = schemaName.lastIndexOf('-');
        if (indexBeforePackageName == -1) {
            return null;
        }
        try {
            return schemaName.substring(indexBeforePackageName + 1);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private final class IBinderFrozenStateCallback implements IBinder.FrozenStateChangeCallback {
        @Override
        public void onFrozenStateChanged(
                @NonNull IBinder who, @IBinder.FrozenStateChangeCallback.State int state) {
            boolean isFrozen = state == IBinder.FrozenStateChangeCallback.STATE_FROZEN;
            synchronized (mFrozenStateLock) {
                if (isFrozen) {
                    mFrozenCallbacks.put(who, false);
                } else {
                    boolean shouldNotify = mFrozenCallbacks.getOrDefault(who, false);
                    if (shouldNotify) {
                        notifyUnfrozenObserver(who);
                    } else {
                        mFrozenCallbacks.remove(who);
                    }
                }
            }
        }
    }
}
