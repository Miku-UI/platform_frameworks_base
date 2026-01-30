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

package com.android.server.appfunctions;

import android.Manifest;
import android.annotation.BinderThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManager.AppFunctionsPolicy;
import android.app.appfunctions.AppFunctionAccessServiceInterface;
import android.app.appfunctions.AppFunctionManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.flags.Flags;

import com.android.internal.infra.AndroidFuture;

import java.util.Objects;

/* Validates that caller has the correct privilege to call an AppFunctionManager Api. */
class CallerValidatorImpl implements CallerValidator {
    private final Context mContext;
    private final AppFunctionAccessServiceInterface mAppFunctionAccessService;

    private final DeviceSettingHelper mDeviceSettingHelper;

    private final UserManager mUserManager;

    CallerValidatorImpl(
            @NonNull Context context,
            @NonNull AppFunctionAccessServiceInterface appFunctionAccessService,
            @NonNull UserManager userManager) {
        mContext = Objects.requireNonNull(context);
        mAppFunctionAccessService = Objects.requireNonNull(appFunctionAccessService);
        mDeviceSettingHelper = new DeviceSettingHelperImpl(context);
        mUserManager = Objects.requireNonNull(userManager);
    }

    @Override
    @NonNull
    @BinderThread
    public String validateCallingPackage(@NonNull String claimedCallingPackage) {
        int callingUid = Binder.getCallingUid();
        final long callingIdentityToken = Binder.clearCallingIdentity();
        try {
            validateCallingPackageInternal(callingUid, claimedCallingPackage);
            return claimedCallingPackage;
        } finally {
            Binder.restoreCallingIdentity(callingIdentityToken);
        }
    }

    @Override
    @BinderThread
    public void verifyTargetUserHandle(
            @NonNull UserHandle targetUserHandle, @NonNull String claimedCallingPackage) {
        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        final long callingIdentityToken = Binder.clearCallingIdentity();
        try {
            if (Flags.appFunctionAccessServiceEnabled()) {
                enforceNoCrossUserOrSecondaryProfileInteraction(targetUserHandle, callingUid);
            } else {
                verifyUserInteraction(
                        targetUserHandle.getIdentifier(),
                        callingUid,
                        callingPid,
                        claimedCallingPackage);
            }
        } finally {
            Binder.restoreCallingIdentity(callingIdentityToken);
        }
    }

    @RequiresPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @CanExecuteAppFunctionResult
    private AndroidFuture<Integer> verifyCallerCanExecuteAppFunctionWithAccessService(
            int callingUid,
            int callingPid,
            @NonNull UserHandle targetUser,
            @NonNull String callerPackageName,
            @NonNull String targetPackageName) {

        boolean hasExecutionPermission =
                mContext.checkPermission(
                                Manifest.permission.EXECUTE_APP_FUNCTIONS, callingPid, callingUid)
                        == PackageManager.PERMISSION_GRANTED;

        boolean isSamePackage = callerPackageName.equals(targetPackageName);
        int requestState =
                mAppFunctionAccessService.getAccessRequestState(
                        callerPackageName,
                        UserHandle.getUserId(callingUid),
                        mDeviceSettingHelper.getPermissionOwnerPackage(targetPackageName),
                        targetUser.getIdentifier());
        boolean hasAccessPermission =
                requestState == AppFunctionManager.ACCESS_REQUEST_STATE_GRANTED;
        if (hasExecutionPermission && hasAccessPermission) {
            return AndroidFuture.completedFuture(CAN_EXECUTE_APP_FUNCTIONS_ALLOWED_HAS_PERMISSION);
        }
        if (isSamePackage) {
            return AndroidFuture.completedFuture(CAN_EXECUTE_APP_FUNCTIONS_ALLOWED_SAME_PACKAGE);
        }
        return AndroidFuture.completedFuture(CAN_EXECUTE_APP_FUNCTIONS_DENIED);
    }

    @RequiresPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @CanExecuteAppFunctionResult
    private AndroidFuture<Integer> verifyCallerCanExecuteAppFunctionHelper(
            int callingUid,
            int callingPid,
            @NonNull String callerPackageName,
            @NonNull String targetPackageName) {
        boolean hasExecutionPermission =
                mContext.checkPermission(
                                Manifest.permission.EXECUTE_APP_FUNCTIONS, callingPid, callingUid)
                        == PackageManager.PERMISSION_GRANTED;
        if (hasExecutionPermission) {
            return AndroidFuture.completedFuture(CAN_EXECUTE_APP_FUNCTIONS_ALLOWED_HAS_PERMISSION);
        }
        if (callerPackageName.equals(targetPackageName)) {
            return AndroidFuture.completedFuture(CAN_EXECUTE_APP_FUNCTIONS_ALLOWED_SAME_PACKAGE);
        }
        return AndroidFuture.completedFuture(CAN_EXECUTE_APP_FUNCTIONS_DENIED);
    }

    @Override
    @RequiresPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @CanExecuteAppFunctionResult
    public AndroidFuture<Integer> verifyCallerCanExecuteAppFunction(
            int callingUid,
            int callingPid,
            @NonNull UserHandle targetUser,
            @NonNull String callerPackageName,
            @NonNull String targetPackageName,
            @NonNull String functionId) {
        if (callingUid == Process.ROOT_UID) {
            // Bypass any validation if calling from ROOT_ID since it is not an actual package
            // to verify.
            return AndroidFuture.completedFuture(CAN_EXECUTE_APP_FUNCTIONS_ALLOWED_HAS_PERMISSION);
        }

        if (Flags.appFunctionAccessApiEnabled() && Flags.appFunctionAccessServiceEnabled()) {
            return verifyCallerCanExecuteAppFunctionWithAccessService(
                    callingUid, callingPid, targetUser, callerPackageName, targetPackageName);
        }
        return verifyCallerCanExecuteAppFunctionHelper(
                callingUid, callingPid, callerPackageName, targetPackageName);
    }

    @Override
    public boolean verifyEnterprisePolicyIsAllowed(
            @NonNull UserHandle callingUser, @NonNull UserHandle targetUser) {
        @AppFunctionsPolicy
        int callingUserPolicy = getDevicePolicyManagerAsUser(callingUser).getAppFunctionsPolicy();
        @AppFunctionsPolicy
        int targetUserPolicy = getDevicePolicyManagerAsUser(targetUser).getAppFunctionsPolicy();
        boolean isSameUser = callingUser.equals(targetUser);

        return isAppFunctionPolicyAllowed(targetUserPolicy, isSameUser)
                && isAppFunctionPolicyAllowed(callingUserPolicy, isSameUser);
    }

    @Override
    public void verifyUserInteraction(int targetUserId, int callingUid, int callingPid) {
        verifyUserInteraction(targetUserId, callingUid, callingPid, /* callingPackageName= */ null);
    }

    @Override
    public void verifyUserInteraction(
            int targetUserId, int callingUid, int callingPid, @Nullable String callingPackageName) {
        UserHandle targetUserHandle = UserHandle.of(targetUserId);
        UserHandle callingUserHandle = UserHandle.getUserHandleForUid(callingUid);
        if (callingUserHandle.equals(targetUserHandle)) {
            return;
        }

        // Duplicates UserController#ensureNotSpecialUser
        if (targetUserHandle.getIdentifier() < 0) {
            throw new IllegalArgumentException(
                    "Call does not support special user " + targetUserHandle);
        }

        if (mContext.checkPermission(
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL, callingPid, callingUid)
                == PackageManager.PERMISSION_GRANTED) {
            if (callingPackageName == null) return;
            try {
                mContext.createPackageContextAsUser(
                        callingPackageName, /* flags= */ 0, targetUserHandle);
            } catch (PackageManager.NameNotFoundException e) {
                throw new SecurityException(
                        "Package: "
                                + callingPackageName
                                + " haven't installed for user "
                                + targetUserHandle.getIdentifier());
            }
            return;
        }
        throw new SecurityException(
                "Permission denied while calling from uid "
                        + callingUid
                        + " with "
                        + targetUserHandle
                        + "; Requires permission: "
                        + Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    /**
     * Enforce that any cross profile interaction or calling from secondary profile are not allowed.
     *
     * @param targetUserHandle The user which the caller is requesting to execute as.
     * @param callingUid The actual uid of the caller as determined by Binder.
     * @throws SecurityException if caller trying to interact across user or within secondary
     *     profile.
     */
    private void enforceNoCrossUserOrSecondaryProfileInteraction(
            @NonNull UserHandle targetUserHandle, int callingUid) {
        UserHandle callingUserHandle = UserHandle.getUserHandleForUid(callingUid);
        if (!callingUserHandle.equals(targetUserHandle)) {
            throw new SecurityException(
                    "Permission denied while calling from uid "
                            + callingUid
                            + " with "
                            + targetUserHandle
                            + "; Cross user interaction is not allowed");
        }

        if (mUserManager.isProfile(targetUserHandle.getIdentifier())) {
            throw new SecurityException("Permission denied while calling from secondary profile");
        }
    }

    /**
     * Checks that the caller's supposed package name matches the uid making the call.
     *
     * @throws SecurityException if the package name and uid don't match.
     */
    private void validateCallingPackageInternal(
            int actualCallingUid, @NonNull String claimedCallingPackage) {
        if (actualCallingUid == Process.ROOT_UID) {
            // root does not have a package name
            return;
        }
        UserHandle callingUserHandle = UserHandle.getUserHandleForUid(actualCallingUid);
        Context actualCallingUserContext =
                mContext.createContextAsUser(callingUserHandle, /* flags= */ 0);
        int claimedCallingUid = getPackageUid(actualCallingUserContext, claimedCallingPackage);
        if (claimedCallingUid != actualCallingUid) {
            throw new SecurityException(
                    "Specified calling package ["
                            + claimedCallingPackage
                            + "] does not match the calling uid "
                            + actualCallingUid);
        }
    }

    /**
     * Finds the UID of the {@code packageName} in the given {@code context}. Returns {@link
     * Process#INVALID_UID} if unable to find the UID.
     */
    private int getPackageUid(@NonNull Context context, @NonNull String packageName) {
        try {
            return context.getPackageManager().getPackageUid(packageName, /* flags= */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            return Process.INVALID_UID;
        }
    }

    private boolean isAppFunctionPolicyAllowed(
            @AppFunctionsPolicy int userPolicy, boolean isSameUser) {
        return switch (userPolicy) {
            case DevicePolicyManager.APP_FUNCTIONS_NOT_CONTROLLED_BY_POLICY -> true;
            case DevicePolicyManager.APP_FUNCTIONS_DISABLED_CROSS_PROFILE -> isSameUser;
            default -> false;
        };
    }

    private DevicePolicyManager getDevicePolicyManagerAsUser(@NonNull UserHandle targetUser) {
        return mContext.createContextAsUser(targetUser, /* flags= */ 0)
                .getSystemService(DevicePolicyManager.class);
    }
}
