/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.IAppFunctionExecutor;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.ArraySet;

/**
 * A container for app function registrations. This class is not thread safe. Do not modify the
 * returned collection, it is a direct reference to the internal data.
 */
final class DynamicAppFunctionsRegistrationStore {
    private final ArrayMap<AppFunctionName, ArrayMap<RegistrationScopeId, IAppFunctionExecutor>>
            mFunctionNameToRegistrationInfo = new ArrayMap<>();
    private final ArrayMap<RegistrationScopeId, ArraySet<AppFunctionName>>
            mScopeToFunctionNames = new ArrayMap<>();
    private final ArrayMap<IBinder, ArraySet<AppFunctionRegistrationId>>
            mExecutorToRegistrationIds = new ArrayMap<>();

    /**
     * Adds an app function registration. If the registration already exists, it will be
     * overwritten.
     *
     * @param name The name of the app function.
     * @param scopeId The scope of the registration.
     * @param executor The executor for the app function.
     */
    public void putRegistration(
            @NonNull AppFunctionName name,
            @NonNull RegistrationScopeId scopeId,
            @NonNull IAppFunctionExecutor executor) {
        mFunctionNameToRegistrationInfo.computeIfAbsent(name, k -> new ArrayMap<>())
                .put(scopeId, executor);
        mScopeToFunctionNames.computeIfAbsent(scopeId, k -> new ArraySet<>())
                .add(name);
        mExecutorToRegistrationIds.computeIfAbsent(executor.asBinder(), k -> new ArraySet<>())
                .add(new AppFunctionRegistrationId(name, scopeId));
    }

    /**
     * Removes an app function registration.
     *
     * @param name The name of the app function.
     * @param scopeId The scope of the registration.
     * @param executorBinder The binder of the executor.
     */
    public void removeRegistration(
            @NonNull AppFunctionName name,
            @NonNull RegistrationScopeId scopeId,
            @NonNull IBinder executorBinder) {
        ArrayMap<RegistrationScopeId, IAppFunctionExecutor> scopes =
                mFunctionNameToRegistrationInfo.get(name);
        if (scopes != null) {
            scopes.remove(scopeId);
            if (scopes.isEmpty()) {
                mFunctionNameToRegistrationInfo.remove(name);
            }
        }

        ArraySet<AppFunctionName> functions = mScopeToFunctionNames.get(scopeId);
        if (functions != null) {
            functions.remove(name);
            if (functions.isEmpty()) {
                mScopeToFunctionNames.remove(scopeId);
            }
        }

        ArraySet<AppFunctionRegistrationId> registrationIds =
                mExecutorToRegistrationIds.get(executorBinder);
        if (registrationIds != null) {
            registrationIds.remove(new AppFunctionRegistrationId(name, scopeId));
            if (registrationIds.isEmpty()) {
                mExecutorToRegistrationIds.remove(executorBinder);
            }
        }
    }

    /**
     * Removes all registrations associated with a specific executor.
     *
     * @param executorBinder The binder of the executor to remove.
     * @return The set of registration IDs that were removed, or {@code null} if no registrations
     *     were found for the given executor.
     */
    @Nullable
    public ArraySet<AppFunctionRegistrationId> removeRegistrationsForExecutor(
            @NonNull IBinder executorBinder) {
        ArraySet<AppFunctionRegistrationId> registrationIds =
                mExecutorToRegistrationIds.get(executorBinder);
        if (registrationIds == null) {
            return null;
        }

        // Create a copy of the registration IDs to iterate over because removeRegistration
        // modifies the underlying collection.
        ArraySet<AppFunctionRegistrationId> copy = new ArraySet<>(registrationIds);
        for (int i = 0; i < copy.size(); i++) {
            AppFunctionRegistrationId registrationId = copy.valueAt(i);
            removeRegistration(
                    registrationId.getFunctionName(),
                    registrationId.getScopeId(),
                    executorBinder);
        }
        return copy;
    }

    /**
     * Returns whether a registration exists for the given name and scope.
     *
     * @param name The name of the app function.
     * @param scopeId The scope of the registration.
     * @return {@code true} if a registration exists, {@code false} otherwise.
     */
    public boolean hasRegistration(
            @NonNull AppFunctionName name, @NonNull RegistrationScopeId scopeId) {
        ArrayMap<RegistrationScopeId, IAppFunctionExecutor> scopes =
                mFunctionNameToRegistrationInfo.get(name);
        return scopes != null && scopes.containsKey(scopeId);
    }

    /**
     * Returns whether any registration exists for the given app function name.
     *
     * @param name The name of the app function.
     * @return {@code true} if at least one registration exists, {@code false} otherwise.
     */
    public boolean hasFunction(@NonNull AppFunctionName name) {
        return mFunctionNameToRegistrationInfo.containsKey(name);
    }

    /**
     * Returns whether any registration exists for the given executor.
     *
     * @param executorBinder The binder of the executor.
     * @return {@code true} if at least one registration exists, {@code false} otherwise.
     */
    public boolean hasExecutor(@NonNull IBinder executorBinder) {
        return mExecutorToRegistrationIds.containsKey(executorBinder);
    }

    /**
     * Returns the executor for the given name and scope.
     *
     * @param name The name of the app function.
     * @param scopeId The scope of the registration.
     * @return The executor, or {@code null} if no registration exists.
     */
    @Nullable
    public IAppFunctionExecutor getExecutor(
            @NonNull AppFunctionName name, @NonNull RegistrationScopeId scopeId) {
        ArrayMap<RegistrationScopeId, IAppFunctionExecutor> scopes =
                mFunctionNameToRegistrationInfo.get(name);
        return scopes != null ? scopes.get(scopeId) : null;
    }

    /**
     * Returns the scopes for which the given app function is registered.
     *
     * @param name The name of the app function.
     * @return The set of scopes, or {@code null} if no registration exists.
     */
    @Nullable
    public ArraySet<RegistrationScopeId> getScopes(@NonNull AppFunctionName name) {
        ArrayMap<RegistrationScopeId, IAppFunctionExecutor> scopes =
                mFunctionNameToRegistrationInfo.get(name);
        return scopes != null ? new ArraySet<>(scopes.keySet()) : null;
    }

    /**
     * Returns the functions registered for the given scope.
     *
     * @param scopeId The scope.
     * @return The set of function names, or {@code null} if no registration exists.
     */
    @Nullable
    public ArraySet<AppFunctionName> getFunctionsByScope(@NonNull RegistrationScopeId scopeId) {
        return mScopeToFunctionNames.get(scopeId);
    }

    /**
     * Returns the registrations associated with the given executor.
     *
     * @param executorBinder The binder of the executor.
     * @return The set of registration IDs, or {@code null} if no registration exists.
     */
    @Nullable
    public ArraySet<AppFunctionRegistrationId> getRegistrationsByExecutor(
            @NonNull IBinder executorBinder) {
        return mExecutorToRegistrationIds.get(executorBinder);
    }

    /**
     * Returns the scope ID for the given function name and executor.
     *
     * @param name The name of the app function.
     * @param executorBinder The binder of the executor.
     * @return The scope ID, or {@code null} if no registration exists.
     */
    @Nullable
    public RegistrationScopeId getScopeIdByExecutor(
            @NonNull AppFunctionName name, @NonNull IBinder executorBinder) {
        ArraySet<AppFunctionRegistrationId> registrationIds =
                mExecutorToRegistrationIds.get(executorBinder);
        if (registrationIds != null) {
            for (AppFunctionRegistrationId regId : registrationIds) {
                if (regId.getFunctionName().equals(name)) {
                    return regId.getScopeId();
                }
            }
        }
        return null;
    }
}
