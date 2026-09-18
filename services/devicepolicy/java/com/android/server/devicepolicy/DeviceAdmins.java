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

package com.android.server.devicepolicy;

import static android.app.admin.DevicePolicyManager.AFFILIATED_FULL_USER_PROFILE_OWNER;
import static android.app.admin.DevicePolicyManager.DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.DEVICE_OWNER_TYPE_DEFAULT;
import static android.app.admin.DevicePolicyManager.DEVICE_OWNER_TYPE_FINANCED;
import static android.app.admin.DevicePolicyManager.FINANCED_DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE;
import static android.app.admin.DevicePolicyManager.MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE;
import static android.app.admin.DevicePolicyManager.NOT_A_DPC;
import static android.app.admin.DevicePolicyManager.PROFILE_OWNER_ON_USER_0;
import static android.app.admin.DevicePolicyManager.UNAFFILIATED_FULL_USER_PROFILE_OWNER;

import android.Manifest.permission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.admin.DeviceAdminInfo;
import android.app.admin.DevicePolicyManager.DeviceOwnerType;
import android.app.admin.DevicePolicyManager.DpcType;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.Pair;
import android.util.SparseArray;
import androidx.annotation.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.server.utils.Slogf;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class DeviceAdmins {

    private static final String LOG_TAG = DevicePolicyManagerService.LOG_TAG;

    private static final Set<Integer> DA_DISALLOWED_POLICIES;

    static {
        DA_DISALLOWED_POLICIES = new ArraySet<>();
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA);
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES);
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD);
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
    }

    interface Delegate {
        void initializePolicyDataFromXml(int userHandle, @NonNull DevicePolicyData policy);

        int getTargetSdk(String packageName, int userId);
    }

    final Injector mInjector;
    // Stores and loads state on device and profile owners.
    final Owners mOwners;

    @VisibleForTesting final SparseArray<DevicePolicyData> mUserData = new SparseArray<>();
    final UserManager mUserManager;
    final Delegate mDelegate;
    final Lock mLock;

    DeviceAdmins(
            Lock lock,
            Injector injector,
            Owners owners,
            Delegate delegate) {
        mLock = lock;
        mInjector = injector;
        mOwners = owners;
        mUserManager = injector.getUserManager();
        mDelegate = delegate;
    }

    /**
     * Return the DPC type of the given caller.
     */
    public @DpcType int getDpcType(CallerIdentity caller) {
        // Check the permissions of DPCs
        if (isDefaultDeviceOwner(caller)) {
            return DEVICE_OWNER;
        }
        if (isFinancedDeviceOwner(caller)) {
            return FINANCED_DEVICE_OWNER;
        }
        if (isProfileOwner(caller)) {
            if (isProfileOwnerOfOrganizationOwnedDevice(caller)) {
                return MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE;
            }
            if (isManagedProfile(caller.getUserId())) {
                return MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE;
            }
            if (isProfileOwnerOnUser0(caller)) {
                return PROFILE_OWNER_ON_USER_0;
            }
            if (isUserAffiliatedWithDevice(caller.getUserId())) {
                return AFFILIATED_FULL_USER_PROFILE_OWNER;
            }
            return UNAFFILIATED_FULL_USER_PROFILE_OWNER;
        }
        return NOT_A_DPC;
    }

    /**
     * Returns the DPC type present on any app in the given user.
     *
     * @param userId The id of the user to check.
     */
    public @DpcType int getDpcType(@UserIdInt int userId) {
        if (isDefaultDeviceOwnerUserId(userId)) {
            return DEVICE_OWNER;
        }
        if (isFinancedDeviceOwnerUserId(userId)) {
            return FINANCED_DEVICE_OWNER;
        }
        if (isProfileOwnerOfOrganizationOwnedDevice(userId)) {
            return MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE;
        }
        if (userId == 0 && hasProfileOwner(0)) {
            return PROFILE_OWNER_ON_USER_0;
        }
        if (hasProfileOwner(userId)) {
            return MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE;
        }
        return NOT_A_DPC;
    }

    @Nullable
    ActiveAdmin getDeviceOwnerAdmin() {
        synchronized (mLock.getLockObject()) {
            ComponentName component = mOwners.getDeviceOwnerComponent();
            if (component == null) {
                return null;
            }

            DevicePolicyData policy = getUserData(mOwners.getDeviceOwnerUserId());
            final int n = policy.mAdminList.size();
            for (int i = 0; i < n; i++) {
                ActiveAdmin admin = policy.mAdminList.get(i);
                if (component.equals(admin.info.getComponent())) {
                    return admin;
                }
            }
            Slogf.wtf(LOG_TAG, "Active admin for device owner not found. component=" + component);
            return null;
        }
    }

    @Nullable
    ActiveAdmin getDeviceOwner(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            ComponentName doComponent = mOwners.getDeviceOwnerComponent();
            ActiveAdmin doAdmin = getUserData(userId).mAdminMap.get(doComponent);
            return doAdmin;
        }
    }

    /**
     * @deprecated Use the version which does not take a user id.
     */
    @Deprecated
    @Nullable
    ActiveAdmin getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDevice(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            ActiveAdmin admin = getDeviceOwnerAdmin();
            if (admin == null) {
                admin = getProfileOwnerOfOrganizationOwnedDevice(userId);
            }
            return admin;
        }
    }

    @Nullable
    ActiveAdmin getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDevice() {
        synchronized (mLock.getLockObject()) {
            ActiveAdmin admin = getDeviceOwnerAdmin();
            if (admin == null) {
                admin = getProfileOwnerOfOrganizationOwnedDevice();
            }
            return admin;
        }
    }

    /** Returns the ActiveAdmin associated with the PO or DO on the given user. */
    @Nullable
    ActiveAdmin getDeviceOrProfileOwnerAdmin(int userHandle) {
        synchronized (mLock.getLockObject()) {
            ActiveAdmin admin = getProfileOwnerAdmin(userHandle);
            if (admin == null && getDeviceOwnerUserIdUnchecked() == userHandle) {
                admin = getDeviceOwnerAdmin();
            }
            return admin;
        }
    }

    @Nullable
    private ActiveAdmin getProfileOwnerOfOrganizationOwnedDevice(int userHandle) {
        synchronized (mLock.getLockObject()) {
            return mInjector.binderWithCleanCallingIdentity(
                    () -> {
                        for (UserInfo userInfo : mUserManager.getProfiles(userHandle)) {
                            if (userInfo.isManagedProfile()) {
                                if (getProfileOwnerAsUser(userInfo.id) != null
                                        && isProfileOwnerOfOrganizationOwnedDevice(userInfo.id)) {
                                    ComponentName who = getProfileOwnerAsUser(userInfo.id);
                                    return getActiveAdminUnchecked(who, userInfo.id);
                                }
                            }
                        }
                        return null;
                    });
        }
    }

    @Nullable
    ActiveAdmin getProfileOwnerOfOrganizationOwnedDevice() {
        synchronized (mLock.getLockObject()) {
            return mInjector.binderWithCleanCallingIdentity(
                    () -> {
                        for (UserInfo userInfo : mUserManager.getUsers()) {
                            if (userInfo.isManagedProfile()) {
                                if (getProfileOwnerAsUser(userInfo.id) != null
                                        && isProfileOwnerOfOrganizationOwnedDevice(userInfo.id)) {
                                    ComponentName who = getProfileOwnerAsUser(userInfo.id);
                                    return getActiveAdminUnchecked(who, userInfo.id);
                                }
                            }
                        }
                        return null;
                    });
        }
    }

    // Returns the active profile owner for this user or null if the current user has no
    // profile owner.
    @Nullable
    ActiveAdmin getProfileOwnerAdmin(int userHandle) {
        synchronized (mLock.getLockObject()) {
            ComponentName profileOwner = mOwners.getProfileOwnerComponent(userHandle);
            if (profileOwner == null) {
                return null;
            }
            DevicePolicyData policy = getUserData(userHandle);
            final int n = policy.mAdminList.size();
            for (int i = 0; i < n; i++) {
                ActiveAdmin admin = policy.mAdminList.get(i);
                if (profileOwner.equals(admin.info.getComponent())) {
                    return admin;
                }
            }
            return null;
        }
    }

    @Nullable
    ActiveAdmin getDefaultDeviceOwner(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            ComponentName doComponent = mOwners.getDeviceOwnerComponent();
            if (mOwners.getDeviceOwnerType(doComponent.getPackageName()) == DEVICE_OWNER) {
                ActiveAdmin doAdmin = getUserData(userId).mAdminMap.get(doComponent);
                return doAdmin;
            }
            return null;
        }
    }

    @Nullable
    ActiveAdmin getProfileOwner(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            final ComponentName poAdminComponent = mOwners.getProfileOwnerComponent(userId);
            ActiveAdmin poAdmin = getUserData(userId).mAdminMap.get(poAdminComponent);
            return poAdmin;
        }
    }

    @Nullable
    ActiveAdmin getProfileOwnerOrDeviceOwner(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            // Try to find an admin which can use reqPolicy
            final ComponentName poAdminComponent = mOwners.getProfileOwnerComponent(userId);

            if (poAdminComponent != null) {
                return getProfileOwner(userId);
            }

            return getDeviceOwner(userId);
        }
    }

    @Nullable
    ActiveAdmin getActiveAdminUnchecked(CallerIdentity caller) {
        return getActiveAdminUnchecked(
                caller.getComponentName(), caller.getUserId(), caller.getPackageName());
    }

    @Nullable
    ActiveAdmin getActiveAdminUnchecked(ComponentName who, int userHandle) {
        return getActiveAdminUnchecked(who, userHandle, /* packageName= */ null);
    }

    @Nullable
    ActiveAdmin getActiveAdminUnchecked(ComponentName who, int userHandle, String packageName) {
        synchronized (mLock.getLockObject()) {
            final DevicePolicyData policy = getUserData(userHandle);

            // Find ActiveAdmin by component name.
            ActiveAdmin componentAdmin = (who != null) ? policy.mAdminMap.get(who) : null;
            if (componentAdmin != null) {
                ActivityInfo activityInfo = componentAdmin.info.getActivityInfo();
                if (who.getPackageName().equals(activityInfo.packageName)
                        && who.getClassName().equals(activityInfo.name)) {
                    return componentAdmin;
                }
            }

            // Find ActiveAdmin by package name. There must be only one.
            if (packageName != null) {
                List<ActiveAdmin> packageAdmins = new ArrayList<>();
                for (ActiveAdmin admin : policy.mAdminList) {
                    if (admin.info.getPackageName().equals(packageName)) {
                        packageAdmins.add(admin);
                    }
                }
                Preconditions.checkState(
                        packageAdmins.size() == 1,
                        String.format(
                                "There must be exactly one ActiveAdmin for specified package %s",
                                packageName));
                return packageAdmins.get(0);
            }

            return null;
        }
    }

    ActiveAdmin getActiveAdminUnchecked(ComponentName who, int userHandle, boolean parent) {
        synchronized (mLock.getLockObject()) {
            if (parent) {
                Preconditions.checkCallAuthorization(isManagedProfile(userHandle));
            }
            ActiveAdmin admin = getActiveAdminUnchecked(who, userHandle);
            if (parent && admin != null) {
                return admin.getParentActiveAdmin();
            }
            return admin;
        }
    }

    @Nullable
    ActiveAdmin getActiveAdmin(CallerIdentity caller) {
        synchronized (mLock.getLockObject()) {
            if (caller.getComponentName() != null) {
                return getActiveAdminUnchecked(caller.getComponentName(), caller.getUserId());
            }
            return mInjector.binderWithCleanCallingIdentity(
                    () -> {
                        List<ComponentName> activeAdmins = getActiveAdmins(caller.getUserId());
                        if (activeAdmins != null) {
                            for (ComponentName admin : activeAdmins) {
                                if (admin.getPackageName().equals(caller.getPackageName())) {
                                    return getActiveAdminUnchecked(admin, caller.getUserId());
                                }
                            }
                        }
                        return null;
                    });
        }
    }

    /**
     * Find the admin for the component and userId bit of the uid, then check the admin's uid
     * matches the uid.
     */
    @Nullable
    ActiveAdmin getActiveAdminForUid(ComponentName who, int uid) {
        synchronized (mLock.getLockObject()) {
            final int userId = UserHandle.getUserId(uid);
            final DevicePolicyData policy = getUserData(userId);
            ActiveAdmin admin = policy.mAdminMap.get(who);
            if (admin == null) {
                throw new SecurityException("No active admin " + who + " for UID " + uid);
            }
            if (admin.getUid() != uid) {
                throw new SecurityException("Admin " + who + " is not owned by uid " + uid);
            }
            return admin;
        }
    }

    /**
     * Returns the active admin for the user of the caller as denoted by uid, which implements the
     * {@code reqPolicy}.
     *
     * <p>The {@code who} parameter is used as a hint: If provided, it must be the component name of
     * the active admin for that user and the caller uid must match the uid of the admin. If not
     * provided, iterate over all of the active admins in the DevicePolicyData for that user and
     * return the one with the uid specified as parameter, and has the policy specified.
     */
    @Nullable
    ActiveAdmin getActiveAdminWithPolicyForUid(ComponentName who, int reqPolicy, int uid) {
        synchronized (mLock.getLockObject()) {
            // Try to find an admin which can use reqPolicy
            final int userId = UserHandle.getUserId(uid);
            final DevicePolicyData policy = getUserData(userId);
            if (who != null) {
                ActiveAdmin admin = policy.mAdminMap.get(who);
                if (admin == null || admin.getUid() != uid) {
                    throw new SecurityException(
                            "Admin " + who + " is not active or not owned by uid " + uid);
                }
                if (isActiveAdminWithPolicyForUser(admin, reqPolicy, userId)) {
                    return admin;
                }
            } else {
                for (ActiveAdmin admin : policy.mAdminList) {
                    if (admin.getUid() == uid
                            && isActiveAdminWithPolicyForUser(admin, reqPolicy, userId)) {
                        return admin;
                    }
                }
            }

            return null;
        }
    }

    @Nullable
    ActiveAdmin getActiveAdminForCaller(CallerIdentity caller, int reqPolicy)
            throws SecurityException {
        return getActiveAdminOrCheckPermissionsForCaller(
                caller, reqPolicy, /* permissions= */ Set.of());
    }

    @Nullable
    ActiveAdmin getActiveAdminForCaller(CallerIdentity caller, int reqPolicy, boolean parent)
            throws SecurityException {
        if (parent) {
            Preconditions.checkCallingUser(isManagedProfile(caller.getUserId()));
        }
        ActiveAdmin admin =
                getActiveAdminOrCheckPermissionsForCaller(
                        caller, reqPolicy, /* permissions= */ Set.of());
        if (parent && admin != null) {
            return admin.getParentActiveAdmin();
        }
        return admin;
    }

    /**
     * Finds an active admin for the caller then checks {@code permissions} if admin check failed.
     *
     * @return an active admin or {@code null} if there is no active admin but one of {@code
     *     permissions} is granted
     * @throws SecurityException if caller neither has an active admin nor {@code permission}
     */
    @Nullable
    ActiveAdmin getActiveAdminOrCheckPermissionsForCaller(
            CallerIdentity caller, int reqPolicy, Set<String> permissions)
            throws SecurityException {
        synchronized (mLock.getLockObject()) {
            ActiveAdmin result =
                    getActiveAdminWithPolicyForUid(
                            caller.getComponentName(), reqPolicy, caller.getUid());
            if (result != null) {
                return result;
            } else {
                for (String permission : permissions) {
                    if (hasPermission(caller, permission)) {
                        return null;
                    }
                }
            }

            // Code for handling failure from getActiveAdminWithPolicyForUid to find an admin
            // that satisfies the required policy.
            // Throws a security exception with the right error message.
            if (caller.hasAdminComponent()) {
                final DevicePolicyData policy = getUserData(caller.getUserId());
                ActiveAdmin admin = policy.mAdminMap.get(caller.getComponentName());
                final boolean isDeviceOwner =
                        isDeviceOwner(admin.info.getComponent(), caller.getUserId());
                final boolean isProfileOwner =
                        isProfileOwner(admin.info.getComponent(), caller.getUserId());

                if (DA_DISALLOWED_POLICIES.contains(reqPolicy)
                        && !isDeviceOwner
                        && !isProfileOwner) {
                    throw new SecurityException(
                            "Admin "
                                    + admin.info.getComponent()
                                    + " is not a device owner or profile owner, so may not use"
                                    + " policy: "
                                    + admin.info.getTagForPolicy(reqPolicy));
                }
                throw new SecurityException(
                        "Admin "
                                + admin.info.getComponent()
                                + " did not specify uses-policy for: "
                                + admin.info.getTagForPolicy(reqPolicy));
            } else {
                throw new SecurityException(
                        "No active admin owned by uid "
                                + caller.getUid()
                                + " for policy #"
                                + reqPolicy
                                + (permissions.isEmpty()
                                        ? ""
                                        : ", which doesn't have " + permissions));
            }
        }
    }

    ActiveAdmin getActiveAdminOrCheckPermissionForCaller(
            CallerIdentity caller, int reqPolicy, boolean parent, @NonNull String permission)
            throws SecurityException {
        if (parent) {
            Preconditions.checkCallAuthorization(isManagedProfile(caller.getUserId()));
        }
        ActiveAdmin admin =
                getActiveAdminOrCheckPermissionsForCaller(caller, reqPolicy, Set.of(permission));
        if (parent && admin != null) {
            return admin.getParentActiveAdmin();
        }
        return admin;
    }

    @NonNull
    List<ComponentName> getActiveAdmins(int userHandle) {
        synchronized (mLock.getLockObject()) {
            DevicePolicyData policy = getUserData(userHandle);
            final int N = policy.mAdminList.size();
            if (N <= 0) {
                return null;
            }
            ArrayList<ComponentName> res = new ArrayList<ComponentName>(N);
            for (int i = 0; i < N; i++) {
                res.add(policy.mAdminList.get(i).info.getComponent());
            }
            return res;
        }
    }

    /**
     * Returns the most probable admin to have set a policy on the given {@code userId} according to
     * the following heuristics:
     *
     * <ul>
     *   <li>The device owner on the given userId
     *   <li>The profile owner on the given userId
     *   <li>The org owned profile owner of which the given userId is its parent
     *   <li>The profile owner of which the given userId is its parent
     *   <li>The device owner on any user
     *   <li>The profile owner on any user
     * </ul>
     */
    @Nullable
    // TODO(b/266928216): Check what the admin capabilities are when deciding which admin to return.
    ActiveAdmin getMostProbableDPCAdminForLocalPolicy(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            ActiveAdmin localDeviceOwner = getDeviceOwner(userId);
            if (localDeviceOwner != null) {
                return localDeviceOwner;
            }

            ActiveAdmin localProfileOwner = getProfileOwner(userId);
            if (localProfileOwner != null) {
                return localProfileOwner;
            }

            int[] profileIds = mUserManager.getProfileIds(userId, /* enabledOnly= */ false);
            for (int id : profileIds) {
                if (id == userId) {
                    continue;
                }
                if (isProfileOwnerOfOrganizationOwnedDevice(id)) {
                    return getProfileOwnerAdmin(id);
                }
            }

            for (int id : profileIds) {
                if (id == userId) {
                    continue;
                }
                if (isManagedProfile(id)) {
                    return getProfileOwnerAdmin(id);
                }
            }

            ActiveAdmin deviceOwner = getDeviceOwnerAdmin();
            if (deviceOwner != null) {
                return deviceOwner;
            }

            for (UserInfo userInfo : mUserManager.getUsers()) {
                ActiveAdmin profileOwner = getProfileOwner(userInfo.id);
                if (profileOwner != null) {
                    return profileOwner;
                }
            }
            return null;
        }
    }

    @VisibleForTesting
    boolean isActiveAdminWithPolicyForUser(
            ActiveAdmin admin, int reqPolicy, @UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            final boolean ownsDevice = isDeviceOwner(admin.info.getComponent(), userId);
            final boolean ownsProfile = isProfileOwner(admin.info.getComponent(), userId);

            boolean allowedToUsePolicy =
                    ownsDevice
                            || ownsProfile
                            || !DA_DISALLOWED_POLICIES.contains(reqPolicy)
                            || getTargetSdk(admin.info.getPackageName(), userId)
                                    < Build.VERSION_CODES.Q;
            return allowedToUsePolicy && admin.info.usesPolicy(reqPolicy);
        }
    }

    private @Nullable ComponentName getProfileOwnerAsUser(@UserIdInt int userId) {
        return mOwners.getProfileOwnerComponent(userId);
    }

    /**
     * Returns {@code true} if the provided caller identity is of a device owner.
     *
     * @param caller identity of caller.
     * @return true if {@code identity} is a device owner, false otherwise.
     */
    public boolean isDeviceOwner(CallerIdentity caller) {
        synchronized (mLock.getLockObject()) {
            return isDeviceOwnerLocked(caller);
        }
    }

    /**
     * Check if the user is a Device Owner
     *
     * @param who to check against
     * @param userId user to check
     * @return if the user is a Device Owner
     */
    public boolean isDeviceOwner(@Nullable ComponentName who, @UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            return mOwners.hasDeviceOwner()
                    && getDeviceOwnerUserId() == userId
                    && getDeviceOwnerComponent().equals(who);
        }
    }

    /**
     * Check if the user is a Device Owner
     *
     * @param userId user to check
     * @return if the user is a Device Owner
     */
    public boolean isDeviceOwnerUserId(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            return mOwners.hasDeviceOwner() && mOwners.getDeviceOwnerUserId() == userId;
        }
    }

    public boolean isDeviceOwner(ActiveAdmin admin) {
        return isDeviceOwner(admin.info.getComponent(), admin.getUserHandle().getIdentifier());
    }

    /**
     * Returns {@code true} <b>only if</b> the caller is the device owner and the device owner type
     * is {@link DevicePolicyManager#DEVICE_OWNER_TYPE_DEFAULT}. {@code false} is returned for the
     * case where the caller is not the device owner, there is no device owner, or the device owner
     * type is not {@link DevicePolicyManager#DEVICE_OWNER_TYPE_DEFAULT}.
     */
    public boolean isDefaultDeviceOwner(CallerIdentity caller) {
        synchronized (mLock.getLockObject()) {
            return isDeviceOwnerLocked(caller)
                    && getDeviceOwnerTypeLocked(mOwners.getDeviceOwnerPackageName())
                            == DEVICE_OWNER_TYPE_DEFAULT;
        }
    }

    public boolean isDefaultDeviceOwnerUserId(int userId) {
        return mOwners.isDefaultDeviceOwnerUserId(userId);
    }

    /**
     * {@code true} is returned <b>only if</b> the caller is the device owner and the device owner
     * type is {@link DevicePolicyManager#DEVICE_OWNER_TYPE_FINANCED}. {@code false} is returned for
     * the case where the caller is not the device owner, there is no device owner, or the device
     * owner type is not {@link DevicePolicyManager#DEVICE_OWNER_TYPE_FINANCED}.
     */
    public boolean isFinancedDeviceOwner(CallerIdentity caller) {
        synchronized (mLock.getLockObject()) {
            return isDeviceOwnerLocked(caller)
                    && getDeviceOwnerTypeLocked(mOwners.getDeviceOwnerPackageName())
                            == DEVICE_OWNER_TYPE_FINANCED;
        }
    }

    public boolean isFinancedDeviceOwnerUserId(int userId) {
        return mOwners.isFinancedDeviceOwnerUserId(userId);
    }

    private boolean isDeviceOwnerLocked(CallerIdentity caller) {
        if (!isDeviceOwnerUserId(caller.getUserId())) {
            return false;
        }

        if (caller.hasAdminComponent()) {
            return getDeviceOwnerComponent().equals(caller.getComponentName());
        } else {
            return isUidDeviceOwnerLocked(caller.getUid());
        }
    }

    /**
     * Returns {@code true} if the provided caller identity is of a profile owner of an organization
     * owned device.
     *
     * @param caller identity of caller
     * @return true if {@code identity} is a profile owner of an organization owned device, false
     *     otherwise.
     */
    public boolean isProfileOwnerOfOrganizationOwnedDevice(CallerIdentity caller) {
        return isProfileOwner(caller)
                && isProfileOwnerOfOrganizationOwnedDevice(caller.getUserId());
    }

    /**
     * Returns {@code true} if the provided caller identity is of a profile owner of an organization
     * owned device.
     *
     * @return true if {@code identity} is a profile owner of an organization owned device, false
     *     otherwise.
     */
    public boolean isProfileOwnerOfOrganizationOwnedDevice(ComponentName who, int userId) {
        return isProfileOwner(who, userId) && isProfileOwnerOfOrganizationOwnedDevice(userId);
    }

    public boolean isProfileOwnerOfOrganizationOwnedDevice(@UserIdInt int userId) {
        return mOwners.isProfileOwnerOfOrganizationOwnedDevice(userId);
    }

    public boolean isProfileOwner(@Nullable ComponentName who, @UserIdInt int userId) {
        final ComponentName profileOwner =
                mInjector.binderWithCleanCallingIdentity(() -> getProfileOwnerAsUser(userId));
        return who != null && who.equals(profileOwner);
    }

    boolean isProfileOwnerOnUser0(CallerIdentity caller) {
        return isProfileOwner(caller) && caller.getUserHandle().isSystem();
    }

    /**
     * Returns {@code true} if the provided caller identity is of a profile owner.
     *
     * @param caller identity of caller.
     * @return true if {@code identity} is a profile owner, false otherwise.
     */
    public boolean isProfileOwner(CallerIdentity caller) {
        synchronized (mLock.getLockObject()) {
            final ComponentName profileOwner =
                    mInjector.binderWithCleanCallingIdentity(
                            () -> getProfileOwnerAsUser(caller.getUserId()));
            // No profile owner.
            if (profileOwner == null) {
                return false;
            }
            // The admin ComponentName was specified, check it directly.
            if (caller.hasAdminComponent()) {
                return profileOwner.equals(caller.getComponentName());
            } else {
                return isUidProfileOwnerLocked(caller.getUid());
            }
        }
    }

    private boolean isManagedProfile(@UserIdInt int userHandle) {
        final UserInfo user = getUserInfo(userHandle);
        return user != null && user.isManagedProfile();
    }

    public boolean hasProfileOwner(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            return mOwners.hasProfileOwner(userId);
        }
    }

    /**
     * Returns the user id of the Device Owner, or {@code UserHandle.USER_NULL} if there is no
     * Device Owner.
     */
    public int getDeviceOwnerUserId() {
        synchronized (mLock.getLockObject()) {
            return mOwners.getDeviceOwnerUserId();
        }
    }

    public int getDeviceOwnerUserIdUnchecked() {
        synchronized (mLock.getLockObject()) {
            return mOwners.hasDeviceOwner() ? mOwners.getDeviceOwnerUserId() : UserHandle.USER_NULL;
        }
    }

    @DeviceOwnerType
    public int getDeviceOwnerType(String packageName) {
        synchronized (mLock.getLockObject()) {
            return mOwners.getDeviceOwnerType(packageName);
        }
    }

    @DeviceOwnerType
    private int getDeviceOwnerTypeLocked(String packageName) {
        return mOwners.getDeviceOwnerType(packageName);
    }

    public boolean hasDeviceOwner() {
        synchronized (mLock.getLockObject()) {
            return mOwners.hasDeviceOwner();
        }
    }

    public boolean isDeviceManaged() {
        return mOwners.isDeviceManaged();
    }

    public Pair<Integer, ComponentName> getDeviceOwnerUserIdAndComponent() {
        return mOwners.getDeviceOwnerUserIdAndComponent();
    }

    public ComponentName getDeviceOwnerComponent() {
        return mOwners.getDeviceOwnerComponent();
    }

    public String getDeviceOwnerPackageName() {
        return mOwners.getDeviceOwnerPackageName();
    }

    public Set<Integer> getProfileOwnerKeys() {
        synchronized (mLock.getLockObject()) {
            return mOwners.getProfileOwnerKeys();
        }
    }

    public String getProfileOwnerPackage(int userId) {
        return mOwners.getProfileOwnerPackage(userId);
    }

    ComponentName getProfileOwnerComponent(int userId) {
        return mOwners.getProfileOwnerComponent(userId);
    }

    public String getProfileOwnerPackageName(int userId) {
        return mOwners.getProfileOwnerPackage(userId);
    }

    /** Return device owner or profile owner set on a given user. */
    @Nullable
    ComponentName getOwnerComponent(int userId) {
        synchronized (mLock.getLockObject()) {
            if (isDeviceOwnerUserId(userId)) {
                return getDeviceOwnerComponent();
            }
            if (mOwners.hasProfileOwner(userId)) {
                return getProfileOwnerComponent(userId);
            }
        }
        return null;
    }

    /** Return the package name of owner in a given user. */
    String getOwnerPackageNameForUser(int userId) {
        return isDeviceOwnerUserId(userId)
                ? getDeviceOwnerPackageName()
                : mOwners.getProfileOwnerPackage(userId);
    }

    private UserInfo getUserInfo(@UserIdInt int userId) {
        return mInjector.binderWithCleanCallingIdentity(() -> mUserManager.getUserInfo(userId));
    }

    /**
     * Returns the policy data for the given user.
     *
     * <p>If the policy data is not loaded yet, this will load the policy data from XML.
     *
     * @param userHandle the user for whom to load the policy data
     * @return
     */
    @NonNull
    public DevicePolicyData getUserData(int userHandle) {
        synchronized (mLock.getLockObject()) {
            DevicePolicyData policy = mUserData.get(userHandle);
            if (policy == null) {
                policy = new DevicePolicyData(userHandle);
                // `policy` must be inserted into `mUserData` before we call
                // `initializePolicyDataFromXml`, since the initialize code has
                //  side-effects which in return call `getUserData` again :(
                mUserData.append(userHandle, policy);
                mDelegate.initializePolicyDataFromXml(userHandle, policy);
            }
            return policy;
        }
    }

    public @NonNull DevicePolicyData getUserDataUnchecked(int userHandle) {
        return mInjector.binderWithCleanCallingIdentity(() -> getUserData(userHandle));
    }

    /** Removes the in-memory policy data for the given user. */
    public void removeUserData(int userHandle) {
        synchronized (mLock.getLockObject()) {
            mUserData.remove(userHandle);
        }
    }

    /**
     * Returns the (first) user that has completed the setup, or {@code UserHandle.USER_NULL} if no
     * such user exists.
     */
    public int getUserWithSetupCompleted() {
        synchronized (mLock.getLockObject()) {
            for (int i = 0; i < mUserData.size(); i++) {
                int userId = mUserData.keyAt(i);
                if (mInjector.hasUserSetupCompleted(getUserData(userId))) {
                    return userId;
                }
            }
            return UserHandle.USER_NULL;
        }
    }

    /**
     * Get all users for which policy data is stored and which no longer exist.
     *
     * <p>This is needed in case the broadcast {@link Intent.ACTION_USER_REMOVED} was not handled
     * before reboot.
     */
    public Set<Integer> getDeletedUsers() {
        Set<Integer> usersWithProfileOwners;
        Set<Integer> usersWithData;
        synchronized (mLock.getLockObject()) {
            usersWithProfileOwners = mOwners.getProfileOwnerKeys();
            usersWithData = new ArraySet<>();
            for (int i = 0; i < mUserData.size(); i++) {
                usersWithData.add(mUserData.keyAt(i));
            }
        }
        List<UserInfo> allUsers = mUserManager.getUsers();

        Set<Integer> deletedUsers = new ArraySet<>();
        deletedUsers.addAll(usersWithProfileOwners);
        deletedUsers.addAll(usersWithData);
        for (UserInfo userInfo : allUsers) {
            deletedUsers.remove(userInfo.id);
        }
        return deletedUsers;
    }

    public void dumpPerUserPolicyData(IndentingPrintWriter pw) {
        synchronized (mLock.getLockObject()) {
            int userCount = mUserData.size();
            for (int i = 0; i < userCount; i++) {
                int userId = mUserData.keyAt(i);
                DevicePolicyData policy = getUserData(userId);
                policy.dump(pw);
                pw.println();
            }
        }
    }

    private int getTargetSdk(String packageName, @UserIdInt int userId) {
        return mDelegate.getTargetSdk(packageName, userId);
    }

    private boolean hasPermission(CallerIdentity caller, String permission) {
        return mInjector.hasPermission(permission, caller.getPid(), caller.getUid());
    }

    // Used by DevicePolicyManagerServiceShellCommand
    void printAllOwners(PrintWriter pw) {
        synchronized (mLock.getLockObject()) {
            if (getDeviceOwnerUserIdUnchecked() != UserHandle.USER_NULL) {
                pw.printf(
                        "User %d: admin=%s,DeviceOwner\n",
                        getDeviceOwnerUserIdUnchecked(),
                        getDeviceOwnerAdmin().info.getComponent().flattenToShortString());
            }
            for (var userId : mOwners.getProfileOwnerKeys()) {
                pw.printf(
                        "User %d: admin=%s,",
                        userId,
                        getProfileOwnerAdmin(userId).info.getComponent().flattenToShortString());
                if (isManagedProfile(userId)) {
                    pw.printf("ManagedProfileOwner(parentUserId=%d)", getProfileParentId(userId));
                } else {
                    pw.print("ProfileOwner");
                }
                if (isUserAffiliatedWithDevice(userId)) {
                    pw.print(",Affiliated");
                }
                if (mOwners.isProfileOwnerOfOrganizationOwnedDevice(userId)) {
                    pw.print(",OrganizationOwnedDevice");
                }
                pw.println();
            }
        }
    }

    public boolean isUserAffiliatedWithDevice(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            return isUserAffiliatedWithDeviceLocked(userId);
        }
    }

    private boolean isUserAffiliatedWithDeviceLocked(@UserIdInt int userId) {
        if (!mOwners.hasDeviceOwner()) {
            return false;
        }
        if (userId == UserHandle.USER_SYSTEM) {
            // The system user is always affiliated in a DO device,
            // even if in headless system user mode.
            return true;
        }
        if (isDeviceOwnerUserId(userId)) {
            // The user that the DO is installed on is always affiliated with the device.
            return true;
        }

        final ComponentName profileOwner = getProfileOwnerAsUser(userId);
        if (profileOwner == null) {
            return false;
        }

        final Set<String> userAffiliationIds = getUserData(userId).mAffiliationIds;
        final Set<String> deviceAffiliationIds =
                getUserData(UserHandle.USER_SYSTEM).mAffiliationIds;
        for (String id : userAffiliationIds) {
            if (deviceAffiliationIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean areAllUsersAffiliatedWithDevice() {
        synchronized (mLock.getLockObject()) {
            return mInjector.binderWithCleanCallingIdentity(
                    () -> {
                        final List<UserInfo> userInfos = mUserManager.getAliveUsers();
                        for (int i = 0; i < userInfos.size(); i++) {
                            int userId = userInfos.get(i).id;
                            if (!isUserAffiliatedWithDevice(userId)) {
                                Slogf.d(LOG_TAG, "User id " + userId + " not affiliated.");
                                return false;
                            }
                        }
                        return true;
                    });
        }
    }

    /**
     * Checks if any of the packages associated with the UID of the app provided is that of the
     * device owner.
     *
     * @param appUid UID of the app to check.
     * @return {@code true} if any of the packages are the device owner, {@code false} otherwise.
     */
    private boolean isUidDeviceOwnerLocked(int appUid) {
        mLock.ensureLocked();
        final String deviceOwnerPackageName = getDeviceOwnerComponent().getPackageName();
        try {
            String[] pkgs = mInjector.getIPackageManager().getPackagesForUid(appUid);
            if (pkgs == null) {
                return false;
            }

            for (String pkg : pkgs) {
                if (deviceOwnerPackageName.equals(pkg)) {
                    return true;
                }
            }
        } catch (RemoteException e) {
            return false;
        }
        return false;
    }

    /**
     * Checks if the app uid provided is the profile owner. This method should only be called if no
     * componentName is available.
     *
     * @param appUid UID of the caller.
     * @return true if the caller is the profile owner
     */
    private boolean isUidProfileOwnerLocked(int appUid) {
        mLock.ensureLocked();
        final int userId = UserHandle.getUserId(appUid);
        final ComponentName profileOwnerComponent = getProfileOwnerComponent(userId);
        if (profileOwnerComponent == null) {
            return false;
        }
        for (ActiveAdmin admin : getUserData(userId).mAdminList) {
            final ComponentName currentAdminComponent = admin.info.getComponent();
            if (admin.getUid() == appUid && profileOwnerComponent.equals(currentAdminComponent)) {
                return true;
            }
        }
        return false;
    }

    private int getProfileParentId(int userHandle) {
        return mInjector.binderWithCleanCallingIdentity(
                () -> {
                    UserInfo parentUser = mUserManager.getProfileParent(userHandle);
                    return parentUser != null ? parentUser.id : userHandle;
                });
    }

    /**
     * Returns the {@link Owners} object owned by {@code this}. This should only be used to access
     * one-off methods on the {@link Owners} object that are not exposed through the APIs of {@code
     * this}.
     */
    Owners getOwners() {
        return mOwners;
    }
}
