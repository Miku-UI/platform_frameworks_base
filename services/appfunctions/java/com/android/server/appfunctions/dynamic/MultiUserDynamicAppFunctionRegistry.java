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

package com.android.server.appfunctions.dynamic;

import static com.android.server.appfunctions.AppFunctionExecutors.SHARED_BACKGROUND_EXECUTOR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionActivityId;
import android.app.appfunctions.AppFunctionActivityState;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.ExecuteAppFunctionAidlRequest;
import android.app.appfunctions.IAppFunctionExecutor;
import android.app.appfunctions.SafeOneTimeExecuteAppFunctionCallback;
import android.os.Build;
import android.os.ICancellationSignal;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.SystemService;

import java.util.List;
import java.util.Objects;

/**
 * Manages the lifecycle of app functions registered at runtime cross user. Creates a per user
 * {@link DynamicAppFunctionRegistry} when user is unlocked, and deletes it when the user is
 * stopped. Redirects calls to the per user {@link DynamicAppFunctionRegistry}.
 */
public final class MultiUserDynamicAppFunctionRegistry {

    private static final boolean DEBUG = Build.TYPE.equals("eng");

    private static final String TAG = "DynamicAppFuncRegistry";

    private static MultiUserDynamicAppFunctionRegistry sInstance;

    /** Gets a singleton instance. */
    @NonNull
    public static synchronized MultiUserDynamicAppFunctionRegistry getInstance() {
        if (sInstance == null) {
            sInstance = new MultiUserDynamicAppFunctionRegistry();
        }
        return sInstance;
    }

    private final Object mCrossUserLock = new Object();

    @GuardedBy("mCrossUserLock")
    private SparseArray<DynamicAppFunctionRegistry> mPerUserRegistrations = new SparseArray<>();

    /**
     * Called after an existing user is unlocked.
     *
     * <p>This will create registry for this {@code user}.
     */
    public void onUserUnlocked(
            @NonNull OnRegistrationStateChangedListener registrationListener,
            @NonNull SystemService.TargetUser user) {
        maybePrintDebugLog("onUserUnlocked: " + user.getUserIdentifier(), null);
        synchronized (mCrossUserLock) {
            if (!mPerUserRegistrations.contains(user.getUserIdentifier())) {
                mPerUserRegistrations.put(
                        user.getUserIdentifier(),
                        new DynamicAppFunctionRegistry(
                                SHARED_BACKGROUND_EXECUTOR, registrationListener));
            }
        }
    }

    /**
     * Called when an existing user was stopped.
     *
     * <p>This will delete cache for {@code user}.
     */
    public void onUserStopped(@NonNull SystemService.TargetUser user) {
        maybePrintDebugLog("onUserStopped: " + user.getUserIdentifier(), null);
        synchronized (mCrossUserLock) {
            mPerUserRegistrations.remove(user.getUserIdentifier());
        }
    }

    /**
     * Registers one or more app functions, making them available for execution for a specific user
     * as a single atomic operation.
     *
     * <p>This method associates the provided function identifiers with the client's execution
     * session. As long as the registration is active, calls to execute these functions will be
     * routed to the provided {@code session}.
     *
     * @param packageName Name of the package that owns the app functions.
     * @param functionIdentifiers A list of unique identifiers for the app functions to register.
     * @param executor The client's executor, an {@link IAppFunctionExecutor} binder used to invoke
     *     the function implementation in the client's process.
     * @param userHandle The user for whom the app functions are being registered.
     * @param scopeIds Identifiers of the registration source corresponding to each
     *     functionIdentifier.
     * @throws IllegalStateException if any of the function identifiers are already registered for
     *     this package and user, or if the specified user has not been unlocked. No function
     *     identifiers from the list will be registered in this case.
     */
    public void registerAppFunctions(
            @NonNull String packageName,
            @NonNull List<String> functionIdentifiers,
            @NonNull IAppFunctionExecutor executor,
            @NonNull UserHandle userHandle,
            @NonNull List<RegistrationScopeId> scopeIds) {
        maybePrintDebugLog("registerAppFunction for " + packageName + " :", functionIdentifiers);
        getPerUserRegistry(userHandle)
                .registerAppFunctions(packageName, functionIdentifiers, executor, scopeIds);
    }

    /**
     * Unregisters one or more app functions, making them unavailable for execution.
     *
     * <p>This removes the association between the function identifiers and the client's execution
     * session. If a function is not currently registered, the request to unregister it will be
     * silently ignored.
     *
     * @param packageName Name of the package that owns the app functions.
     * @param functionIdentifiers A list of identifiers for the app functions to unregister.
     * @param executor The client's executor that was used for registration. The system verifies
     *     this to ensure that only the original registrant can unregister the function.
     * @param userHandle The user for whom the app functions should be unregistered.
     * @throws IllegalStateException if the specified {@code userHandle} has not been unlocked.
     */
    public void unregisterAppFunctions(
            @NonNull String packageName,
            @NonNull List<String> functionIdentifiers,
            @NonNull IAppFunctionExecutor executor,
            @NonNull UserHandle userHandle) {
        maybePrintDebugLog("unregisterAppFunction " + packageName + ": ", functionIdentifiers);
        getPerUserRegistry(userHandle)
                .unregisterAppFunctions(packageName, functionIdentifiers, executor);
    }

    /**
     * Checks if dynamic app function has any registrations.
     *
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifier Identifier of the app function.
     * @param userHandle Handle of the user to register the app function for.
     * @return True if the app function is registered, false otherwise.
     * @throws IllegalStateException If the user was not unlocked.
     */
    public boolean hasRegistrations(
            @NonNull String packageName,
            @NonNull String functionIdentifier,
            @NonNull UserHandle userHandle) {
        return getPerUserRegistry(userHandle)
                .hasRegistrations(packageName, functionIdentifier);
    }

    /**
     * Executes an app function.
     *
     * @param request Request to execute.
     * @param safeExecuteAppFunctionCallback Callback to report results to.
     * @param cancellationTransport The cancellation signal.
     * @throws IllegalStateException If the user was not unlocked.
     */
    public void executeAppFunction(
            @NonNull ExecuteAppFunctionAidlRequest request,
            @NonNull SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback,
            @NonNull ICancellationSignal cancellationTransport) {
        getPerUserRegistry(request.getUserHandle())
                .executeAppFunction(
                        request.getClientRequest(),
                        safeExecuteAppFunctionCallback,
                        cancellationTransport);
    }

    /**
     * Checks if a dynamic app function is registered with a specific scope.
     *
     * @param packageName package name of the app function.
     * @param functionIdentifier identifier of the app function.
     * @param userHandle user to check for.
     * @param scopeId registration scope to check for.
     * @return {@code true} if the app function is registered.
     */
    public boolean isRegistered(
            @NonNull String packageName,
            @NonNull String functionIdentifier,
            @NonNull UserHandle userHandle,
            @NonNull RegistrationScopeId scopeId) {
        return getPerUserRegistry(userHandle).isRegistered(
                packageName, functionIdentifier, scopeId
        );
    }

    /**
     * Returns the currently registered {@link android.app.appfunctions.AppFunctionActivityId}s for
     * a given {@code functionName}.
     *
     * @param functionName Name of the app function to search for.
     * @param userHandle Handle of the user where the app function is registered.
     * @return ArraySet of {@link android.app.appfunctions.AppFunctionActivityId}s which registered
     *     the given function. Null of no activities registered the function or the function is
     *     registered with a global scope.
     */
    @Nullable
    public ArraySet<AppFunctionActivityId> getRegisteredActivityIds(
            @NonNull AppFunctionName functionName, @NonNull UserHandle userHandle) {
        return getPerUserRegistry(userHandle).getRegisteredActivityIds(functionName);
    }

    @NonNull
    public List<AppFunctionActivityState> getAppFunctionActivityStates(
            @NonNull List<AppFunctionActivityId> activityIds, @NonNull UserHandle userHandle) {
        return getPerUserRegistry(userHandle).getAppFunctionActivityStates(activityIds);
    }

    @NonNull
    private DynamicAppFunctionRegistry getPerUserRegistry(@NonNull UserHandle userHandle) {
        synchronized (mCrossUserLock) {
            if (!mPerUserRegistrations.contains(userHandle.getIdentifier())) {
                throw new IllegalStateException(
                        "User " + userHandle.getIdentifier() + " has not been unlocked yet");
            }
            return mPerUserRegistrations.get(userHandle.getIdentifier());
        }
    }

    private static void maybePrintDebugLog(
            @NonNull String message, @Nullable List<String> identifiers) {
        if (DEBUG) {
            if (identifiers == null) {
                Log.d(TAG, message);
                return;
            }
            Log.d(TAG, message + String.join(", ", identifiers));
        }
    }
}
