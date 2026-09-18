/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.appfunctions.dynamic;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionActivityId;
import android.app.appfunctions.AppFunctionActivityState;
import android.app.appfunctions.AppFunctionException;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.ExecuteAppFunctionRequest;
import android.app.appfunctions.IAppFunctionExecutor;
import android.app.appfunctions.ICancellationCallback;
import android.app.appfunctions.SafeOneTimeExecuteAppFunctionCallback;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Manages the lifecycle of app functions registered at runtime for a single user. Unregisters
 * AppFunction is it's process died.
 */
final class DynamicAppFunctionRegistry {

    private static final boolean DEBUG = Build.TYPE.equals("eng");
    private static final String TAG = "DynamicAppFuncRegistry";
    private final Object mLock = new Object();
    private final Executor mFrozenStateListenerExecutor;

    private final FrozenStateListener mFrozenStateListener = new FrozenStateListener();

    @GuardedBy("mLock")
    private final ArraySet<IBinder> mFrozenBinders = new ArraySet<>();

    @GuardedBy("mLock")
    private final DynamicAppFunctionsRegistrationStore mRegistrations =
            new DynamicAppFunctionsRegistrationStore();

    /**
     * A list of remote IAppFunctionExecutor callbacks. This is used primarily to detect when a
     * client process dies so we can clean up its registrations.
     */
    private final RemoteCallbackList<IAppFunctionExecutor> mCallbacks;

    private final OnRegistrationStateChangedListener mOnRegistrationStateChangedListener;

    DynamicAppFunctionRegistry(
            @NonNull Executor frozenStateListenerExecutor,
            @NonNull OnRegistrationStateChangedListener onRegistrationStateChangedListener) {
        mFrozenStateListenerExecutor = Objects.requireNonNull(frozenStateListenerExecutor);
        mOnRegistrationStateChangedListener =
                Objects.requireNonNull(onRegistrationStateChangedListener);
        mCallbacks =
                new RemoteCallbackList<>() {
                    @Override
                    public void onCallbackDied(
                            @NonNull IAppFunctionExecutor callback, @NonNull Object cookie) {
                        if (DEBUG) {
                            Log.d(TAG, "onCallbackDied for " + callback.toString());
                        }
                        synchronized (mLock) {
                            removeFrozenStateListener(callback.asBinder());
                            ArraySet<AppFunctionRegistrationId>
                                    registrationIds =
                                            mRegistrations.removeRegistrationsForExecutor(
                                                    callback.asBinder());
                            if (registrationIds == null) {
                                return;
                            }
                            Set<AppFunctionName> unregisteredFunctionNames = new ArraySet<>();
                            for (AppFunctionRegistrationId registrationId : registrationIds) {
                                if (DEBUG) {
                                    Log.d(TAG, "Removing due to process death: " + registrationId);
                                }
                                unregisteredFunctionNames.add(registrationId.getFunctionName());
                            }
                            if (!unregisteredFunctionNames.isEmpty()) {
                                mOnRegistrationStateChangedListener.onRegistrationChanged(
                                        unregisteredFunctionNames);
                            }
                        }
                    }
                };
    }

    /**
     * Registers one or more app functions, making them available for execution as a single atomic
     * operation.
     *
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifiers A list of identifiers of the app functions.
     * @param executor Executor of the app function.
     * @param scopeIds Identifiers of the source corresponding to each functionIdentifier. Activity
     *     identifier for activity scoped functions, empty class for global scoped functions.
     * @throws IllegalStateException If any of the provided app functions is already registered.
     */
    public void registerAppFunctions(
            @NonNull String packageName,
            @NonNull List<String> functionIdentifiers,
            @NonNull IAppFunctionExecutor executor,
            @NonNull List<RegistrationScopeId> scopeIds) {
        if (functionIdentifiers.size() != scopeIds.size()) {
            throw new IllegalArgumentException(
                    "Scope identifiers list must be same size as function identifiers");
        }
        synchronized (mLock) {
            ArrayList<AppFunctionRegistrationId> registrationIds =
                    new ArrayList<>(functionIdentifiers.size());
            for (int index = 0; index < functionIdentifiers.size(); index++) {
                AppFunctionName name =
                        new AppFunctionName(packageName, functionIdentifiers.get(index));
                RegistrationScopeId source = scopeIds.get(index);
                if (mRegistrations.hasRegistration(name, source)) {
                    throw new IllegalStateException(
                            "App function already registered: "
                                    + name
                                    + " with activity token: "
                                    + source);
                }
                registrationIds.add(new AppFunctionRegistrationId(name, source));
            }

            ArraySet<AppFunctionName> registeredFunctionNames =
                    new ArraySet<>(registrationIds.size());
            for (AppFunctionRegistrationId registrationId : registrationIds) {
                boolean isNewExecutor = !mRegistrations.hasExecutor(executor.asBinder());
                mRegistrations.putRegistration(
                        registrationId.getFunctionName(),
                        registrationId.getScopeId(),
                        executor);
                registeredFunctionNames.add(registrationId.getFunctionName());

                if (isNewExecutor) {
                    mCallbacks.register(executor);
                    IBinder binder = executor.asBinder();
                    try {
                        binder.addFrozenStateChangeCallback(
                                mFrozenStateListenerExecutor, mFrozenStateListener);
                    } catch (UnsupportedOperationException | RemoteException e) {
                        Log.w(TAG, "Unable to monitor frozen state for " + binder, e);
                    }
                }

                if (DEBUG) {
                    Log.d(TAG, "registerAppFunction with ID:" + registrationId);
                }
            }
            mOnRegistrationStateChangedListener.onRegistrationChanged(registeredFunctionNames);
        }
    }

    /**
     * Unregisters one or more app functions, making them unavailable for execution.
     *
     * <p>This removes the association between the function identifiers and the client's execution
     * session. If a function is not currently registered, the request to unregister for it will be
     * silently ignored.
     *
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifiers List of identifier of the app functions.
     * @param executor Executor of the app function.
     */
    public void unregisterAppFunctions(
            @NonNull String packageName,
            @NonNull List<String> functionIdentifiers,
            @NonNull IAppFunctionExecutor executor) {
        synchronized (mLock) {
            ArraySet<AppFunctionName> unregisteredFunctionNames = new ArraySet<>();
            for (int index = 0; index < functionIdentifiers.size(); index++) {
                AppFunctionName name =
                        new AppFunctionName(packageName, functionIdentifiers.get(index));

                RegistrationScopeId registeredScopeId =
                        mRegistrations.getScopeIdByExecutor(name, executor.asBinder());
                if (registeredScopeId == null) {
                    if (DEBUG) {
                        Log.w(
                                TAG,
                                "Skip unregistering function with name:"
                                        + name
                                        + ", as the executor is not found. Available executors: "
                                        + mRegistrations.getScopes(name));
                    }
                    continue;
                }

                mRegistrations.removeRegistration(name, registeredScopeId, executor.asBinder());
                unregisteredFunctionNames.add(name);

                if (!mRegistrations.hasExecutor(executor.asBinder())) {
                    // This was the last registration for this executor.
                    mCallbacks.unregister(executor);
                    removeFrozenStateListener(executor.asBinder());
                }
                if (DEBUG) {
                    Log.d(
                            TAG,
                            "unregisterAppFunction with name:"
                                    + name
                                    + " scopeId:"
                                    + registeredScopeId);
                }
            }
            mOnRegistrationStateChangedListener.onRegistrationChanged(unregisteredFunctionNames);
        }
    }

    /**
     * Executes an app function. Calls {@link
     * SafeOneTimeExecuteAppFunctionCallback#onError(AppFunctionException)} if the function was not
     * registered.
     *
     * @param request Request to execute.
     * @param safeExecuteAppFunctionCallback Callback to report results to.
     * @param cancellationTransport Transport to report cancellation to.
     */
    public void executeAppFunction(
            @NonNull ExecuteAppFunctionRequest request,
            @NonNull SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback,
            @NonNull ICancellationSignal cancellationTransport) {
        AppFunctionName name =
                new AppFunctionName(
                        request.getTargetPackageName(), request.getFunctionIdentifier());
        RegistrationScopeId sourceId = new RegistrationScopeId(request.getActivityId());
        if (DEBUG) {
            Log.d(TAG, "executeAppFunction with ID:" + name + " with activity token: " + sourceId);
        }
        IAppFunctionExecutor executor = null;
        boolean isFrozen = false;
        synchronized (mLock) {
            executor = mRegistrations.getExecutor(name, sourceId);
            if (executor != null) {
                isFrozen = mFrozenBinders.contains(executor.asBinder());
            }
        }

        if (executor == null) {
            if (request.getActivityId() == null) {
                safeExecuteAppFunctionCallback.onError(
                        new AppFunctionException(
                                AppFunctionException.ERROR_DISABLED,
                                "Function with ID: "
                                        + request.getFunctionIdentifier()
                                        + " is disabled."));
            } else {
                safeExecuteAppFunctionCallback.onError(
                        new AppFunctionException(
                                AppFunctionException.ERROR_FUNCTION_NOT_FOUND,
                                "Function with ID: "
                                        + request.getFunctionIdentifier()
                                        + " is not found for provided activity. "));
            }
            return;
        }

        if (isFrozen) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_DISABLED,
                            "Function with ID: "
                                    + request.getFunctionIdentifier()
                                    + " is disabled."));
            return;
        }

        try {
            safeExecuteAppFunctionCallback.attachOnDeathListener(executor.asBinder());
            ExecuteAppFunctionRequest clientRequest;
            if (android.app.appfunctions.flags.Flags.enableAppInteractionApi()) {
                // Attribution contains caller information that can be used to identify the caller.
                // This information should not be leaked to the target app.
                clientRequest = request.copyWithoutAttribution();
            } else {
                clientRequest = request;
            }
            executor.execute(
                    clientRequest,
                    getICancellationCallback(cancellationTransport),
                    safeExecuteAppFunctionCallback.wrapToExecutionCallback());
        } catch (RemoteException e) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_APP_UNKNOWN_ERROR,
                            "Error executing app function: " + e.getMessage()));
        }
    }

    /**
     * Checks if dynamic app function has any registrations.
     *
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifier Identifier of the app function.
     * @return True if the app function is registered, false otherwise.
     */
    public boolean hasRegistrations(
            @NonNull String packageName, @NonNull String functionIdentifier) {
        synchronized (mLock) {
            AppFunctionName functionName = new AppFunctionName(packageName, functionIdentifier);
            ArraySet<RegistrationScopeId> scopes = mRegistrations.getScopes(functionName);
            if (scopes == null || scopes.isEmpty()) {
                return false;
            }

            for (RegistrationScopeId scopeId : scopes) {
                IAppFunctionExecutor executor = mRegistrations.getExecutor(functionName, scopeId);
                if (executor != null && !mFrozenBinders.contains(executor.asBinder())) {
                    // At least one is not frozen, so we consider it as having registrations.
                    return true;
                }
            }
            // All executors are frozen
            return false;
        }
    }

    /**
     * Checks if a dynamic app function is registered with a specific scope.
     *
     * @param packageName package name of the app function.
     * @param functionIdentifier identifier of the app function.
     * @param scopeId registration scope to check for.
     * @return {@code true} if the app function is registered.
     */
    boolean isRegistered(
            @NonNull String packageName,
            @NonNull String functionIdentifier,
            @NonNull RegistrationScopeId scopeId) {
        synchronized (mLock) {
            AppFunctionName name = new AppFunctionName(packageName, functionIdentifier);
            IAppFunctionExecutor executor = mRegistrations.getExecutor(name, scopeId);
            if (executor == null) {
                return false;
            }
            return !mFrozenBinders.contains(executor.asBinder());
        }
    }

    /**
     * Returns the currently registered {@link android.app.appfunctions.AppFunctionActivityId}s for
     * a given {@code functionName}.
     *
     * @param functionName Name of the app function to search for.
     * @return ArraySet of {@link android.app.appfunctions.AppFunctionActivityId}s which registered
     *     the given function. Null of no activities registered the function or the function is
     *     registered with a global scope.
     */
    @Nullable
    public ArraySet<AppFunctionActivityId> getRegisteredActivityIds(
            @NonNull AppFunctionName functionName) {
        synchronized (mLock) {
            ArraySet<RegistrationScopeId> registeredScopes =
                    mRegistrations.getScopes(functionName);

            if (registeredScopes == null || registeredScopes.isEmpty()) {
                return null;
            }

            ArraySet<AppFunctionActivityId> activities = new ArraySet<>();
            for (RegistrationScopeId scopeId : registeredScopes) {
                if (scopeId.getAppFunctionActivityId() != null) {
                    activities.add(scopeId.getAppFunctionActivityId());
                } else {
                    return null;
                }
            }
            return activities;
        }
    }

    // TODO(b/481676087): Either disallow registering disabled appfunctions or update this method
    // to filter them out.
    @NonNull
    public List<AppFunctionActivityState> getAppFunctionActivityStates(
            @NonNull List<AppFunctionActivityId> activityIds) {
        List<AppFunctionActivityState> result = new ArrayList<>(activityIds.size());
        synchronized (mLock) {
            for (AppFunctionActivityId activityId : activityIds) {
                RegistrationScopeId targetScope = new RegistrationScopeId(activityId);
                ArraySet<AppFunctionName> functions =
                        mRegistrations.getFunctionsByScope(targetScope);
                if (functions != null && !functions.isEmpty()) {
                    result.add(
                            new AppFunctionActivityState(activityId, new ArraySet<>(functions)));
                }
            }
        }
        return result;
    }

    @GuardedBy("mLock")
    private void removeFrozenStateListener(@NonNull IBinder binder) {
        mFrozenBinders.remove(binder);
        try {
            binder.removeFrozenStateChangeCallback(mFrozenStateListener);
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            if (DEBUG) {
                Log.d(TAG, "Failed to remove frozen state listener for " + binder, e);
            }
        }
    }

    private class FrozenStateListener implements IBinder.FrozenStateChangeCallback {
        @Override
        public void onFrozenStateChanged(@NonNull IBinder who, int state) {
            final boolean isFrozen = (state != IBinder.FrozenStateChangeCallback.STATE_UNFROZEN);
            Set<AppFunctionName> changedFunctionNames = new ArraySet<>();
            synchronized (mLock) {
                if (isFrozen) {
                    mFrozenBinders.add(who);
                } else {
                    mFrozenBinders.remove(who);
                }
                Set<AppFunctionRegistrationId> registrations =
                        mRegistrations.getRegistrationsByExecutor(who);
                if (registrations != null) {
                    for (AppFunctionRegistrationId registration :
                            registrations) {
                        changedFunctionNames.add(registration.getFunctionName());
                    }
                }
            }
            mOnRegistrationStateChangedListener.onRegistrationChanged(changedFunctionNames);
        }
    }

    @NonNull
    private static ICancellationCallback getICancellationCallback(
            @NonNull ICancellationSignal localCancelTransport) {
        CancellationSignal cancellationSignal =
                CancellationSignal.fromTransport(localCancelTransport);
        ICancellationCallback cancellationCallback =
                new ICancellationCallback.Stub() {
                    @Override
                    public void sendCancellationTransport(
                            @NonNull ICancellationSignal cancellationTransport) {
                        cancellationSignal.setRemote(cancellationTransport);
                    }
                };
        return cancellationCallback;
    }
}
