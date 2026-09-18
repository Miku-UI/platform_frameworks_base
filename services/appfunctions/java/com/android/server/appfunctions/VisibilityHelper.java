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

package com.android.server.appfunctions;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionActivityState;
import android.app.appfunctions.AppFunctionAidlSearchSpec;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionSearchSpec;

import java.util.List;
import java.util.Set;

/** Helper for handling AppFunction visibility. */
public interface VisibilityHelper {
    /**
     * Filters the {@code functionNames} to only return the visible ones.
     *
     * @param functionNames The list of {@link AppFunctionName}.
     * @param callerIdentity The caller's identifier.
     * @return The list of {@link AppFunctionName} that the caller has visibility with.
     */
    @NonNull
    Set<AppFunctionName> filterVisibleAppFunctions(
            @NonNull Set<AppFunctionName> functionNames, @NonNull CallerIdentity callerIdentity);

    /**
     * Filters the {@code packagesToFilter} to only return the visible ones.
     *
     * @param packagesToFilter The list of {@link AppFunctionName}.
     * @param callerIdentity The caller's identifier.
     * @return The list of package names that the caller has visibility with.
     */
    @NonNull
    Set<String> filterVisiblePackages(
            @NonNull Set<String> packagesToFilter, @NonNull CallerIdentity callerIdentity);

    /**
     * Filters the {@code appFunctionActivityStates} to only return the visible ones.
     *
     * @param appFunctionActivityStates The list of {@link AppFunctionActivityState}.
     * @param callingPackageName The calling package name.
     * @param callingUid The calling uid.
     * @param callingPid The calling pid.
     * @return The list of {@link AppFunctionActivityState} that the caller has visibility with.
     */
    @NonNull
    List<AppFunctionActivityState> filterVisibleAppFunctionActivityStates(
            @NonNull List<AppFunctionActivityState> appFunctionActivityStates,
            @NonNull String callingPackageName,
            int callingUid,
            int callingPid);

    /**
     * Checks if {@code targetPackage} is visible from {@code callingPackage}.
     *
     * @param targetPackage The target package name.
     * @param callingPackage The calling package name.
     * @param callingUid The calling uid.
     * @param callingPid The calling pid.
     * @return True if visible. False otherwise.
     */
    boolean isPackageVisible(
            @NonNull String targetPackage,
            @NonNull String callingPackage,
            int callingUid,
            int callingPid);
}
