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

package com.android.server.devicepolicy;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.app.admin.PolicyValue;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Slog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Tracks the enterprise RCS archival application.
 * Grants the {@link AppOpsManager#OP_READ_RESTRICTED_MESSAGES} AppOp updates when the Default SMS
 * App changes or the Archival App is changed or installed.
 */
final class RcsArchivalAppTracker {
    private static final String TAG = "RcsArchivalAppTracker";
    private static final String KEY_ARCHIVAL_PACKAGE = "messages_archival";

    private final Context mContext;
    private final AppOpsManager mAppOpsManager;
    private final RoleManager mRoleManager;
    private final Injector mInjector;
    private final Handler mHandler;
    private final DevicePolicyEngine mDevicePolicyEngine;

    // Mapping: userId -> Current Default SMS App package name
    private final ConcurrentHashMap<Integer, String> mDsaPackages
        = new ConcurrentHashMap<>();
    // Mapping: userId -> Current granted archival package name
    private final ConcurrentHashMap<Integer, String> mArchivalPackages
        = new ConcurrentHashMap<>();

    public RcsArchivalAppTracker(@NonNull Injector injector,
        @NonNull DevicePolicyEngine devicePolicyEngine,
        @NonNull Handler handler) {
        mInjector = injector;
        mContext = injector.mContext;
        mHandler = handler;
        mDevicePolicyEngine = devicePolicyEngine;
        mAppOpsManager = mInjector.getAppOpsManager();
        mRoleManager = mContext.getSystemService(RoleManager.class);
    }

    public void start() {
        initialize();
        mRoleManager.addOnRoleHoldersChangedListenerAsUser(mContext.getMainExecutor(),
            (roleName, user) -> {
                if (RoleManager.ROLE_SMS.equals(roleName)) {
                    refreshSmsRoleAndArchivalApp(user.getIdentifier());
                }
        }, UserHandle.ALL);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");

        mContext.registerReceiverAsUser(mPackageReceiver, UserHandle.ALL, filter, null, mHandler);
    }

    /**
     * Retrieves the list of all users to initialize the current DSA and Archival App for each.
     */
    private void initialize() {
        for (UserInfo userInfo : mInjector.getUserManager().getUsers()) {
            refreshSmsRoleAndArchivalApp(userInfo.id);
        }
    }

    public void onRestrictionsChanged(@NonNull String packageName, int userId) {
        Slog.d(TAG, "onRestrictionsChanged: " + packageName + " for user " + userId);
        String cachedDsa = mDsaPackages.get(userId);
        if (cachedDsa == null) {
            refreshSmsRoleAndArchivalApp(userId);
        } else if (packageName.equals(cachedDsa)) {
            updateArchivalAppOp(userId, cachedDsa);
        }
    }

    /**
     * Receiver to track package changes. If the Archival App is installed, update the AppOp mode
     * accordingly.
     */
    private final BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Uri data = intent.getData();
            if (data == null) {
                return;
            }
            String packageName = data.getSchemeSpecificPart();
            int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0);

            String cachedArchival = mArchivalPackages.get(userId);

            // Only update if the installed package is the Archival app.
            if (packageName.equals(cachedArchival)) {
                setAppOpMode(packageName, userId, AppOpsManager.MODE_ALLOWED);
            }
        }
    };

    /**
     * Helper to check if a specific package is the one defined in the DSA's restrictions.
     */
    private boolean isPackageTargetArchivalApp(String packageName, String dsaPackage, int userId) {
        if (dsaPackage == null) {
            return false;
        }
        String target = getTargetArchivalPackageFromPolicy(dsaPackage, userId);
        return Objects.equals(packageName, target);
    }

    private void refreshSmsRoleAndArchivalApp(int userId) {
        String actualDsa = getSmsRoleHolder(userId);
        if (actualDsa != null) {
            mDsaPackages.put(userId, actualDsa);
        } else {
            mDsaPackages.remove(userId);
        }
        updateArchivalAppOp(userId, actualDsa);
    }

    private void updateArchivalAppOp(int userId, @Nullable String currentDsa) {
        String targetArchivalPkg = getTargetArchivalPackageFromPolicy(currentDsa, userId);
        String lastAuthorizedArchival = mArchivalPackages.get(userId);
        Slog.d(TAG, "updateArchivalAppOp, targetArchivalPkg: " + targetArchivalPkg
                + ", lastAuthorizedArchival: " + lastAuthorizedArchival + " for User " + userId);

        if (!Objects.equals(targetArchivalPkg, lastAuthorizedArchival)) {
            if (lastAuthorizedArchival != null) {
                setAppOpMode(lastAuthorizedArchival, userId, AppOpsManager.MODE_DEFAULT);
                mArchivalPackages.remove(userId);
            }

            if (targetArchivalPkg != null) {
                setAppOpMode(targetArchivalPkg, userId, AppOpsManager.MODE_ALLOWED);
                mArchivalPackages.put(userId, targetArchivalPkg);
            }
        }
    }

    @Nullable
    private String getTargetArchivalPackageFromPolicy(@Nullable String dsaPackage, int userId) {
        if (dsaPackage == null) {
            return null;
        }
        return mInjector.binderWithCleanCallingIdentity(() -> {
            LinkedHashMap<EnforcingAdmin, PolicyValue<Bundle>> policies =
                mDevicePolicyEngine.getLocalPoliciesSetByAdmins(
                        PolicyDefinition.APPLICATION_RESTRICTIONS(dsaPackage),
                        userId);

            String archivalPkgFound = null;
            for (Map.Entry<EnforcingAdmin, PolicyValue<Bundle>> entry : policies.entrySet()) {
                Bundle value = entry.getValue().getValue();
                if (value == null || !value.containsKey(KEY_ARCHIVAL_PACKAGE)) {
                    continue;
                }
                String pkg = value.getString(KEY_ARCHIVAL_PACKAGE);
                if (entry.getKey().hasAuthority(EnforcingAdmin.DPC_AUTHORITY)) {
                    return pkg;
                }
                archivalPkgFound = pkg;
            }
            return archivalPkgFound;
        });
    }

    private void setAppOpMode(@NonNull String packageName, int userId, int mode) {
        mInjector.binderWithCleanCallingIdentity(() -> {
            try {
                int uid = mContext.getPackageManager()
                        .getApplicationInfoAsUser(packageName, 0, userId).uid;
                mAppOpsManager.setMode(AppOpsManager.OP_READ_RESTRICTED_MESSAGES,
                        uid, packageName, mode);
                Slog.d(TAG, "READ_RESTRICTED_MESSAGES appop set " + mode + " for User " + userId);
            } catch (NameNotFoundException exception) {
                Slog.d(TAG, "Package " + packageName + " not found for AppOp update.");
            }
        });
    }

    @Nullable
    private String getSmsRoleHolder(int userId) {
        return mInjector.binderWithCleanCallingIdentity(() -> {
            List<String> roleHolders = mInjector.roleManagerGetRoleHoldersAsUser(
                    RoleManager.ROLE_SMS, UserHandle.of(userId));
            if (roleHolders.isEmpty()) {
                return null;
            }
            return roleHolders.get(0);
        });
    }

    public void onUserRemoved(int userId) {
        mDsaPackages.remove(userId);
        mArchivalPackages.remove(userId);
    }
}