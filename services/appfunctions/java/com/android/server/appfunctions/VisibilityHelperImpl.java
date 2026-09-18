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

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionActivityState;
import android.app.appfunctions.AppFunctionAidlSearchSpec;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionSearchSpec;
import android.app.appfunctions.flags.Flags;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class VisibilityHelperImpl implements VisibilityHelper {

    @NonNull private final Context mContext;

    @NonNull private final PackageManagerInternal mPmInternal;

    public VisibilityHelperImpl(
            @NonNull Context context, @NonNull PackageManagerInternal packageManagerInternal) {
        mContext = Objects.requireNonNull(context);
        mPmInternal = Objects.requireNonNull(packageManagerInternal);
    }

    @Override
    public boolean isPackageVisible(
            @NonNull String targetPackage,
            @NonNull String callingPackage,
            int callingUid,
            int callingPid) {
        if (callingPackage.equals(targetPackage)) {
            return true;
        }

        final long token = Binder.clearCallingIdentity();
        try {
            if (!hasPermissionsToQueryRuntimeMetadata(callingUid, callingPid)) {
                return false;
            }
            return mPmInternal.canQueryPackage(callingUid, targetPackage);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @NonNull
    @Override
    public Set<String> filterVisiblePackages(
            @NonNull Set<String> packagesToFilter, @NonNull CallerIdentity callerIdentity) {
        Objects.requireNonNull(callerIdentity);
        Objects.requireNonNull(packagesToFilter);

        final long token = Binder.clearCallingIdentity();
        try {
            if (hasPermissionsToQueryRuntimeMetadata(
                    callerIdentity.getCallingUid(), callerIdentity.getCallingPid())) {
                if (mContext.checkPermission(
                                Manifest.permission.QUERY_ALL_PACKAGES,
                                callerIdentity.getCallingPid(),
                                callerIdentity.getCallingUid())
                        == PackageManager.PERMISSION_GRANTED) {
                    return new ArraySet<>(packagesToFilter);
                } else {
                    Set<String> allVisiblePackages = getVisiblePackages(callerIdentity);
                    Set<String> filteredPackages = new ArraySet<>();
                    for (String pkg : packagesToFilter) {
                        if (allVisiblePackages.contains(pkg)) {
                            filteredPackages.add(pkg);
                        }
                    }
                    return filteredPackages;
                }
            }
            if (packagesToFilter.contains(callerIdentity.getCallingPackageName())) {
                return Set.of(callerIdentity.getCallingPackageName());
            }
            return Set.of();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @NonNull
    @Override
    public Set<AppFunctionName> filterVisibleAppFunctions(
            @NonNull Set<AppFunctionName> functionNames, @NonNull CallerIdentity callerIdentity) {
        Objects.requireNonNull(functionNames);
        Objects.requireNonNull(callerIdentity);

        final long token = Binder.clearCallingIdentity();
        try {
            if (hasPermissionsToQueryRuntimeMetadata(
                    callerIdentity.getCallingUid(), callerIdentity.getCallingPid())) {
                if (mContext.checkPermission(
                                Manifest.permission.QUERY_ALL_PACKAGES,
                                callerIdentity.getCallingPid(),
                                callerIdentity.getCallingUid())
                        == PackageManager.PERMISSION_GRANTED) {
                    return new ArraySet<>(functionNames);
                } else {
                    Set<String> allVisiblePackages = getVisiblePackages(callerIdentity);
                    Set<AppFunctionName> filteredFunctions = new ArraySet<>();
                    for (AppFunctionName functionName : functionNames) {
                        if (allVisiblePackages.contains(functionName.getPackageName())) {
                            filteredFunctions.add(functionName);
                        }
                    }
                    return filteredFunctions;
                }
            }

            Set<AppFunctionName> selfFunctionNames = new ArraySet<>();
            for (AppFunctionName functionName : functionNames) {
                if (functionName.getPackageName().equals(callerIdentity.getCallingPackageName())) {
                    selfFunctionNames.add(functionName);
                }
            }
            return selfFunctionNames;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    @NonNull
    public List<AppFunctionActivityState> filterVisibleAppFunctionActivityStates(
            @NonNull List<AppFunctionActivityState> appFunctionActivityStates,
            @NonNull String callingPackageName,
            int callingUid,
            int callingPid) {
        Objects.requireNonNull(appFunctionActivityStates);
        Objects.requireNonNull(callingPackageName);

        if (hasPermissionsToQueryRuntimeMetadata(callingUid, callingPid)) {
            return appFunctionActivityStates;
        }
        // Otherwise, only return the function names from the calling package, if any.
        List<AppFunctionActivityState> visibleAppFunctionActivityStates = new ArrayList<>();
        for (AppFunctionActivityState appFunctionActivityState : appFunctionActivityStates) {
            if (appFunctionActivityState.getFunctionNames().isEmpty()) {
                continue;
            }
            // All the function names from the same activity must come from the same package.
            if (appFunctionActivityState
                    .getFunctionNames()
                    .valueAt(0)
                    .getPackageName()
                    .equals(callingPackageName)) {
                visibleAppFunctionActivityStates.add(appFunctionActivityState);
            }
        }
        return visibleAppFunctionActivityStates;
    }

    private boolean hasPermissionsToQueryRuntimeMetadata(int callingUid, int callingPid) {
        if (Flags.enableAppFunctionPermissionV2()
                && mContext.checkPermission(
                                Manifest.permission.DISCOVER_APP_FUNCTIONS, callingPid, callingUid)
                        == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (Flags.enableAppFunctionPermissionV2()
                && mContext.checkPermission(
                                Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
                                callingPid,
                                callingUid)
                        == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return mContext.checkPermission(
                        Manifest.permission.EXECUTE_APP_FUNCTIONS, callingPid, callingUid)
                == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    private Set<String> getFilteredPackages(
            @NonNull Set<String> visiblePackages, @Nullable Set<String> originalPackages) {
        ArraySet<String> updatedPackages = new ArraySet<>();
        if (originalPackages == null) {
            // Missing package means search all packages, therefore use all visible packages here
            updatedPackages.addAll(visiblePackages);
        } else {
            for (String originalPackage : originalPackages) {
                if (visiblePackages.contains(originalPackage)) {
                    updatedPackages.add(originalPackage);
                }
            }
        }
        return updatedPackages;
    }

    @Nullable
    private Set<AppFunctionName> getFilteredFunctionNames(
            @NonNull Set<String> visiblePackages,
            @Nullable Set<AppFunctionName> originalFunctionNames) {
        ArraySet<AppFunctionName> updatedFunctionNames = null;
        if (originalFunctionNames != null) {
            updatedFunctionNames = new ArraySet<>();
            for (AppFunctionName functionName : originalFunctionNames) {
                if (visiblePackages.contains(functionName.getPackageName())) {
                    updatedFunctionNames.add(functionName);
                }
            }
        }
        return updatedFunctionNames;
    }

    @NonNull
    private Set<String> getVisiblePackages(@NonNull CallerIdentity callerIdentity) {
        return getVisiblePackages(
                callerIdentity.getCallingUid(), callerIdentity.getUserHandle().getIdentifier());
    }

    @NonNull
    private Set<String> getVisiblePackages(int callingUid, int userId) {
        final long token = Binder.clearCallingIdentity();
        try {
            ArraySet<String> visiblePackages = new ArraySet<>();
            List<ApplicationInfo> allApps =
                    mPmInternal.getInstalledApplications(/* flags= */ 0, userId, callingUid);
            for (ApplicationInfo appInfo : allApps) {
                visiblePackages.add(appInfo.packageName);
            }
            return visiblePackages;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Checks if searching with {@code packageNames} and {@code functionNames} is an invalid search.
     *
     * <p>It is an invalid search when attempting to search empty package or empty function names,
     * since it would always return empty search result.
     */
    private boolean isInvalidSearch(
            @NonNull Set<String> packageNames, @Nullable Set<AppFunctionName> functionNames) {
        return packageNames.isEmpty() || (functionNames != null && functionNames.isEmpty());
    }
}
