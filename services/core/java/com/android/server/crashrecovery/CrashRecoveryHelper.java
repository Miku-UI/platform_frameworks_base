/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.crashrecovery;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.VersionedPackage;
import android.net.ConnectivityModuleConnector;
import android.text.TextUtils;
import android.util.Slog;

import com.android.server.PackageWatchdog;

import java.util.Collections;
import java.util.List;

/**
 * Provides helper methods for the CrashRecovery APEX
 *  TODO: b/354112511 Add tests for this class when it is finalized.
 * @hide
 */
public final class CrashRecoveryHelper {
    private static final String TAG = "CrashRecoveryHelper";

    private final Context mContext;
    private final ConnectivityModuleConnector mConnectivityModuleConnector;


    /** @hide */
    public CrashRecoveryHelper(@NonNull Context context) {
        mContext = context;
        mConnectivityModuleConnector = ConnectivityModuleConnector.getInstance();
    }

    /**
     * Register health listeners for Connectivity packages health.
     *
     * TODO: b/354112511 Have an internal method to trigger a rollback by reporting high severity errors,
     * and rely on ActivityManager to inform the watchdog of severe network stack crashes
     * instead of having this listener in parallel.
     */
    public void registerConnectivityModuleHealthListener() {
        // register listener for ConnectivityModule
        mConnectivityModuleConnector.registerHealthListener(
                packageName -> {
                final VersionedPackage pkg = getVersionedPackage(packageName);
                if (pkg == null) {
                    Slog.wtf(TAG, "NetworkStack failed but could not find its package");
                    return;
                }
                final List<VersionedPackage> pkgList = Collections.singletonList(pkg);
                PackageWatchdog.getInstance(mContext).notifyPackageFailure(pkgList,
                        PackageWatchdog.FAILURE_REASON_EXPLICIT_HEALTH_CHECK);
            });
    }

    @Nullable
    private VersionedPackage getVersionedPackage(String packageName) {
        final PackageManager pm = mContext.getPackageManager();
        if (pm == null || TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            final long versionCode = getPackageInfo(packageName).getLongVersionCode();
            return new VersionedPackage(packageName, versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Gets PackageInfo for the given package. Matches any user and apex.
     *
     * @throws PackageManager.NameNotFoundException if no such package is installed.
     */
    private PackageInfo getPackageInfo(String packageName)
            throws PackageManager.NameNotFoundException {
        PackageManager pm = mContext.getPackageManager();
        try {
            // The MATCH_ANY_USER flag doesn't mix well with the MATCH_APEX
            // flag, so make two separate attempts to get the package info.
            // We don't need both flags at the same time because we assume
            // apex files are always installed for all users.
            return pm.getPackageInfo(packageName, PackageManager.MATCH_ANY_USER);
        } catch (PackageManager.NameNotFoundException e) {
            return pm.getPackageInfo(packageName, PackageManager.MATCH_APEX);
        }
    }
}
