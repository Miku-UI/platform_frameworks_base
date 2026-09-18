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

package com.android.server.personalcontext;

import static android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION;

import android.Manifest;
import android.annotation.ArrayRes;
import android.annotation.IntDef;
import android.app.privatecompute.PccSandboxManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.PermissionEnforcer;
import android.os.Process;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.service.personalcontext.Flags;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.R;
import com.android.server.SystemConfig;
import com.android.server.personalcontext.component.client.BaseServiceClientComponent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link AccessController} is in charge of verifying that a specific component has the access
 * necessary to complete the given operation.
 *
 * @hide
 */
public class AccessController {
    private static final String TAG = "AccessController";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, prefix = {"ACCESS_"}, value = {
            ACCESS_NONE,
            ACCESS_PUBLISH_HINTS_ALLOWLIST,
            ACCESS_RECEIVE_HINTS_ALLOWLIST,
            ACCESS_PUBLISH_INSIGHTS_ALLOWLIST,
            ACCESS_RECEIVE_INSIGHTS_ALLOWLIST,
            ACCESS_FILTER_INSIGHTS_ALLOWLIST,
            ACCESS_PUBLISH_HINTS_PERMISSION,
            ACCESS_RECEIVE_HINTS_PERMISSION,
            ACCESS_PUBLISH_INSIGHTS_PERMISSION,
            ACCESS_RECEIVE_INSIGHTS_PERMISSION,
            ACCESS_HOST_INSIGHT_SURFACE_PERMISSION,
            ACCESS_PCC_OR_TRUSTED_PACKAGE,
            ACCESS_PCC_OR_AUTO_COMPANION_ROLE,
            ACCESS_REGISTER_VISUALIZER,
            ACCESS_BIND_CONTEXT_PERMISSION
    })
    public @interface Access {
    }

    /** Undefined / no access value. */
    public static final int ACCESS_NONE = 0;

    /** Access to publish hints via the allowlist. */
    public static final int ACCESS_PUBLISH_HINTS_ALLOWLIST = 1;

    /** Access to receive hints via the allowlist. */
    public static final int ACCESS_RECEIVE_HINTS_ALLOWLIST = 1 << 1;

    /** Access to publish insights via the allowlist. */
    public static final int ACCESS_PUBLISH_INSIGHTS_ALLOWLIST = 1 << 2;

    /** Access to receive insights via the allowlist. */
    public static final int ACCESS_RECEIVE_INSIGHTS_ALLOWLIST = 1 << 3;

    /** Access to receive insights without RenderTokens via the allowlist. */
    public static final int ACCESS_FILTER_INSIGHTS_ALLOWLIST = 1 << 4;

    /** Access to publish hints via permissions. */
    public static final int ACCESS_PUBLISH_HINTS_PERMISSION = 1 << 5;

    /** Access to receive hints via permissions. */
    public static final int ACCESS_RECEIVE_HINTS_PERMISSION = 1 << 6;

    /** Access to publish insights via permissions. */
    public static final int ACCESS_PUBLISH_INSIGHTS_PERMISSION = 1 << 7;

    /** Access to receive insights via permissions. */
    public static final int ACCESS_RECEIVE_INSIGHTS_PERMISSION = 1 << 8;

    /** Access to host insight surface */
    public static final int ACCESS_HOST_INSIGHT_SURFACE_PERMISSION = 1 << 9;

    /**
     * Component is PCC compliant, trusted by PCC, or has restricted connection and internet access.
     *
     * <p>Used for refiners and understanders.</p>
     */
    public static final int ACCESS_PCC_OR_TRUSTED_PACKAGE = 1 << 10;

    /**
     * Component is PCC compliant or has the automotive companion app role.
     *
     * <p>Used for renderers.</p>
     */
    public static final int ACCESS_PCC_OR_AUTO_COMPANION_ROLE = 1 << 11;

    /** Access to register a visualizer. */
    public static final int ACCESS_REGISTER_VISUALIZER = 1 << 12;

    /** Access to bind context via permissions. */
    public static final int ACCESS_BIND_CONTEXT_PERMISSION = 1 << 13;

    /** All access flags related to permissions */
    public static final int ACCESS_ALL_PERMISSIONS =
            ACCESS_PUBLISH_HINTS_PERMISSION
                    | ACCESS_RECEIVE_HINTS_PERMISSION
                    | ACCESS_PUBLISH_INSIGHTS_PERMISSION
                    | ACCESS_RECEIVE_INSIGHTS_PERMISSION
                    | ACCESS_HOST_INSIGHT_SURFACE_PERMISSION;

    /** All access flags related to allowlists */
    public static final int ACCESS_ALL_ALLOWLISTS =
            ACCESS_PUBLISH_HINTS_ALLOWLIST
            | ACCESS_RECEIVE_HINTS_ALLOWLIST
            | ACCESS_PUBLISH_INSIGHTS_ALLOWLIST
            | ACCESS_RECEIVE_INSIGHTS_ALLOWLIST
            | ACCESS_FILTER_INSIGHTS_ALLOWLIST;

    /** Interface to inject dependencies. */
    public interface Injector {
        /** Get {@link Resources}. */
        Resources getResources();

        /** Get {@link PackageManager}. */
        PackageManager getPackageManager();

        /** Get {@link PermissionEnforcer}. */
        PermissionEnforcer getPermissionEnforcer();

        /** Get {@link PermissionManager}. */
        PermissionManager getPermissionManager();

        /** Get {@link RoleManager}. */
        RoleManager getRoleManager();

        /** Get {@link SystemConfig}. */
        SystemConfig getSystemConfig();

        /** Get {@link PccSandboxManager}. */
        PccSandboxManager getPccSandboxManager();

        /** Get {@link EventListener}. */
        EventListener getEventListener();
    }

    private static Injector defaultInjector(Context context, EventListener eventListener) {
        return new Injector() {
            @Override
            public Resources getResources() {
                return context.getResources();
            }

            @Override
            public PackageManager getPackageManager() {
                return context.getPackageManager();
            }

            @Override
            public PermissionEnforcer getPermissionEnforcer() {
                return PermissionEnforcer.fromContext(context);
            }

            @Override
            public PermissionManager getPermissionManager() {
                return context.getSystemService(PermissionManager.class);
            }

            @Override
            public RoleManager getRoleManager() {
                return context.getSystemService(RoleManager.class);
            }

            @Override
            public SystemConfig getSystemConfig() {
                return SystemConfig.getInstance();
            }

            @Override
            public PccSandboxManager getPccSandboxManager() {
                return context.getSystemService(PccSandboxManager.class);
            }

            @Override
            public EventListener getEventListener() {
                return eventListener;
            }
        };
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"RESULT_"}, value = {
            RESULT_ALLOWED,
            RESULT_DENIED,
            RESULT_BYPASSED,
            RESULT_SYSTEM,
    })
    public @interface AccessResult {
    }

    /** Allowed. */
    public static final int RESULT_ALLOWED = 0;

    /** Denied. */
    public static final int RESULT_DENIED = 1;

    /** Bypassed. */
    public static final int RESULT_BYPASSED = 2;

    /** System. */
    public static final int RESULT_SYSTEM = 3;

    /** Listener for access checks. */
    public interface EventListener {
        /** Called whenever an access determination is made. */
        void onAccessChecked(
                String packageName,
                UserHandle user,
                String description,
                @AccessResult int result);
    }

    private final Resources mResources;
    private final PackageManager mPackageManager;

    private final PermissionEnforcer mPermissionEnforcer;
    private final PermissionManager mPermissionManager;
    private final RoleManager mRoleManager;
    private final PccSandboxManager mPccSandboxManager;
    private final UserHandle mUser;
    private final SparseArray<Set<String>> mAllowLists = new SparseArray<>();
    private final EventListener mEventListener;
    private final SystemConfig mSystemConfig;

    /**
     * Creates an {@link AccessController} from the given {@link Context}, which is used to
     * retrieve the configured allowlists for the device.
     */
    public AccessController(Context context, EventListener eventListener, UserHandle user) {
        this(defaultInjector(context, eventListener), user);
    }

    /**
     * Creates an {@link AccessController} from the given {@link Context}, which is used to
     * retrieve the configured allowlists for the device.
     */
    AccessController(Injector injector, UserHandle user) {
        mResources = injector.getResources();
        mPackageManager = injector.getPackageManager();
        mPermissionEnforcer = injector.getPermissionEnforcer();
        mPermissionManager = injector.getPermissionManager();
        mRoleManager = injector.getRoleManager();
        mPccSandboxManager = injector.getPccSandboxManager();
        mEventListener = injector.getEventListener();
        mSystemConfig = injector.getSystemConfig();
        mUser = user;
    }

    /**
     * Checks whether the given package uid satisfies the specified access rule.
     *
     * @param uid         The uid whose packages should be checked
     * @param accessFlags The access permissions to check
     * @return {@code true} if the service client has the specified access, {@code false} otherwise
     */
    public boolean isAnyPackageForUidAllowed(int uid, @Access int accessFlags) {
        if (uid == Process.SYSTEM_UID) {
            return true;
        }

        final String[] packagesForUid = mPackageManager.getPackagesForUid(uid);

        if (packagesForUid == null) {
            return false;
        }

        final HashSet<String> uidPackages = new HashSet<>(Arrays.asList(packagesForUid));
        return isAnyPackageAllowed(uidPackages, accessFlags);
    }

    /**
     * Checks whether the given service client satisfies the specified access rule.
     *
     * @param serviceClient The service client to be verified
     * @param accessFlags   The access permissions to check
     * @return {@code true} if the service client has the specified access, {@code false} otherwise
     */
    public boolean isClientAllowed(
            BaseServiceClientComponent<?> serviceClient, @Access int accessFlags) {
        return isServiceAllowed(serviceClient.getServiceInfo(), accessFlags);
    }

    /**
     * Checks whether any of the given package satisfies the specified access rule. Not all rules
     * can be verified by package name.
     *
     * @param packageNames The names of the package to be verified
     * @param accessFlags  The access permissions to check
     * @return {@code true} if the package has the specified access, {@code false} otherwise
     */
    public boolean isAnyPackageAllowed(Collection<String> packageNames, @Access int accessFlags) {
        for (String packageName : packageNames) {
            if (isPackageAllowed(packageName, accessFlags)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks whether the given service satisfies the specified access rule.
     *
     * @param serviceInfo The service to be verified
     * @param accessFlags The rule to check
     * @return {@code true} if the service has the specified access, {@code false} otherwise
     */
    public boolean isServiceAllowed(ServiceInfo serviceInfo, @Access int accessFlags) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Checking service " + serviceInfo);
        }

        boolean result = true;

        // We only need the service info to check the PCC flag.
        if ((accessFlags & ACCESS_PCC_OR_TRUSTED_PACKAGE) != 0) {
            result &= checkPccFlagOrTrusted(serviceInfo);
        } else if ((accessFlags & ACCESS_PCC_OR_AUTO_COMPANION_ROLE) != 0) {
            result &= checkPccOrAutoCompanionFlag(serviceInfo);
        } else if ((accessFlags & ACCESS_BIND_CONTEXT_PERMISSION) != 0) {
            result &= Manifest.permission.BIND_CONTEXT_COMPONENT_SERVICE.equals(
                    serviceInfo.permission);
        }

        return result
                & isPackageAllowed(
                        serviceInfo.packageName,
                        accessFlags
                                & ~ACCESS_PCC_OR_TRUSTED_PACKAGE
                                & ~ACCESS_PCC_OR_AUTO_COMPANION_ROLE);
    }

    /**
     * Checks whether the given package satisfies the specified access rule. Not all rules can be
     * verified by package name.
     *
     * @param packageName The name of the package to be verified
     * @param accessFlags The access permissions to check
     * @return {@code true} if the package has the specified access, {@code false} otherwise
     */
    public boolean isPackageAllowed(String packageName, @Access int accessFlags) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Checking package " + packageName);
        }

        if (PersonalContextManagerService.isSystemPackage(packageName)) {
            return true;
        }

        boolean result = true;

        // We need the service info to check the PCC flag.
        if ((accessFlags & (ACCESS_PCC_OR_TRUSTED_PACKAGE | ACCESS_PCC_OR_AUTO_COMPANION_ROLE))
                != 0) {
            Log.d(TAG, "PCC check: requires ServiceInfo");
            result = false;
        }

        if ((accessFlags & ACCESS_PUBLISH_HINTS_ALLOWLIST) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextHintPublishing);
        }

        if ((accessFlags & ACCESS_RECEIVE_HINTS_ALLOWLIST) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextHintReceiving);
        }

        if ((accessFlags & ACCESS_PUBLISH_INSIGHTS_ALLOWLIST) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextInsightPublishing);
        }

        if ((accessFlags & ACCESS_RECEIVE_INSIGHTS_ALLOWLIST) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextInsightReceiving);
        }

        if ((accessFlags & ACCESS_FILTER_INSIGHTS_ALLOWLIST) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextInsightFiltering);
        }

        if ((accessFlags & ACCESS_PUBLISH_HINTS_PERMISSION) != 0) {
            result &= checkPermission(
                    packageName, Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS);
        }

        if ((accessFlags & ACCESS_RECEIVE_HINTS_PERMISSION) != 0) {
            result &= checkPermission(
                    packageName, Manifest.permission.PERSONAL_CONTEXT_RECEIVE_HINTS);
        }

        if ((accessFlags & ACCESS_PUBLISH_INSIGHTS_PERMISSION) != 0) {
            result &= checkPermission(
                    packageName, Manifest.permission.PERSONAL_CONTEXT_PUBLISH_INSIGHTS);
        }

        if ((accessFlags & ACCESS_RECEIVE_INSIGHTS_PERMISSION) != 0) {
            result &= checkPermission(
                    packageName, Manifest.permission.PERSONAL_CONTEXT_RECEIVE_INSIGHTS);
        }

        if ((accessFlags & ACCESS_REGISTER_VISUALIZER) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextVisualizers);
        }

        return result;
    }

    /** Performs checks for PCC, the auto companion role, or trusted component. */
    private boolean checkPccOrAutoCompanionFlag(ServiceInfo serviceInfo) {
        return checkPccFlag(serviceInfo)
                || checkRoleFlag(serviceInfo.packageName, DEVICE_PROFILE_AUTOMOTIVE_PROJECTION)
                || checkTrustedComponent(serviceInfo);
    }

    /** Checks if the service meets our criteria for participating in PCC. */
    private boolean checkPccFlagOrTrusted(ServiceInfo serviceInfo) {
        return checkPccFlag(serviceInfo)
                || checkAccessRestricted(serviceInfo.packageName)
                || checkTrustedComponent(serviceInfo);
    }

    /** Performs checks for PCC. */
    private boolean checkPccFlag(ServiceInfo serviceInfo) {
        return makeAccessDecision(
                serviceInfo.packageName,
                "PCC",
                (serviceInfo.flags & ServiceInfo.FLAG_RUN_IN_PCC_SANDBOX) != 0
                        ? RESULT_ALLOWED : RESULT_DENIED);
    }

    /**
     * Checks if the package does not have internet permissions and is also restricted in what
     * components can connect to it using allowed-associations.
     *
     * <p>Check can only pass if {@link R.bool.config_enableStrictPersonalContextPccNextCheck} is
     * not enabled, components.
     */
    private boolean checkAccessRestricted(String packageName) {
        if (mResources.getBoolean(
                R.bool.config_enableStrictPersonalContextPccNextCheck)) {
            // Strict PCC check is enabled, fail this check.
            return makeAccessDecision(packageName, "Access restricted", RESULT_DENIED);
        }
        ArraySet<String> allowedAssociations =
                mSystemConfig.getAllowedAssociations().get(packageName);
        boolean hasAllowedAssociations =
                allowedAssociations != null && !allowedAssociations.isEmpty();
        boolean hasInternetPermission =
                checkPermission(packageName, Manifest.permission.INTERNET);

        return makeAccessDecision(
                packageName,
                "Access restricted",
                (!hasInternetPermission && hasAllowedAssociations)
                        ? RESULT_ALLOWED
                        : RESULT_DENIED);
    }

    /** Checks if a package is a trusted system component in the PCC sandbox. */
    private boolean checkTrustedComponent(ServiceInfo serviceInfo) {
        return makeAccessDecision(
                serviceInfo.packageName,
                "Trusted component",
                mPccSandboxManager.isPccTrustedSystemComponent(
                                serviceInfo.getUid(), serviceInfo.packageName)
                        ? RESULT_ALLOWED
                        : RESULT_DENIED);
    }

    /** Performs checks for a role. */
    private boolean checkRoleFlag(String packageName, String role) {
        if (!Flags.enforcePersonalContextRoleAccessControl()) {
            // Deny if flag is disabled since this check is used in an OR statement.
            return makeAccessDecision(packageName, "Role " + role, RESULT_DENIED);
        }

        return makeAccessDecision(
                packageName,
                "Role " + role,
                mRoleManager.getRoleHolders(role).contains(packageName)
                        ? RESULT_ALLOWED : RESULT_DENIED);
    }

    /**
     * Checks and enforces permissions associated with the provided access flags.
     */
    public void enforcePermissions(int pid, int uid, @Access int accessFlags) {
        if (uid == Process.SYSTEM_UID) {
            return;
        }

        HashSet<String> permissions = new HashSet<>();

        if ((accessFlags & ACCESS_RECEIVE_HINTS_PERMISSION) != 0) {
            permissions.add(Manifest.permission.PERSONAL_CONTEXT_RECEIVE_HINTS);
        }

        if ((accessFlags & ACCESS_PUBLISH_HINTS_PERMISSION) != 0) {
            permissions.add(Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS);
        }

        if ((accessFlags & ACCESS_RECEIVE_INSIGHTS_PERMISSION) != 0) {
            permissions.add(Manifest.permission.PERSONAL_CONTEXT_RECEIVE_INSIGHTS);
        }

        if ((accessFlags & ACCESS_PUBLISH_INSIGHTS_PERMISSION) != 0) {
            permissions.add(Manifest.permission.PERSONAL_CONTEXT_PUBLISH_INSIGHTS);
        }

        if ((accessFlags & ACCESS_HOST_INSIGHT_SURFACE_PERMISSION) != 0) {
            permissions.add(Manifest.permission.PERSONAL_CONTEXT_HOST_INSIGHT_SURFACE);
        }

        if (permissions.isEmpty()) {
            return;
        }

        String[] permissionArray = new String[permissions.size()];
        permissions.toArray(permissionArray);

        mPermissionEnforcer.enforcePermissionAllOf(permissionArray, pid, uid);
    }

    /** Performs checks for a permission. */
    private boolean checkPermission(String packageName, String permission) {
        if (PersonalContextManagerService.isSystemPackage(packageName)) {
            return makeAccessDecision(packageName, "Permission " + permission, RESULT_SYSTEM);
        }

        final int permissionResult =
                mPermissionManager.checkPackageNamePermission(
                        permission, packageName, Context.DEVICE_ID_DEFAULT, mUser.getIdentifier());

        return makeAccessDecision(
                packageName,
                "Permission " + permission,
                permissionResult == PackageManager.PERMISSION_GRANTED
                        ? RESULT_ALLOWED
                        : RESULT_DENIED);
    }

    /** Performs checks for an allowlist. */
    private boolean checkAllowList(String packageName, @ArrayRes int allowListResId) {
        final String description = "AllowList " + (mResources == null
                ? "[unknown]"
                : mResources.getResourceName(allowListResId));
        if (!Flags.enforcePersonalContextAllowlistAccessControl()) {
            return makeAccessDecision(packageName, description, RESULT_BYPASSED);
        }

        if (PersonalContextManagerService.isSystemPackage(packageName)) {
            return makeAccessDecision(packageName, description, RESULT_SYSTEM);
        }

        if (!mAllowLists.contains(allowListResId)) {
            mAllowLists.put(allowListResId, Set.of(mResources.getStringArray(allowListResId)));
        }

        return makeAccessDecision(
                packageName,
                description,
                mAllowLists.get(allowListResId).contains(packageName)
                        ? RESULT_ALLOWED : RESULT_DENIED);
    }

    private boolean makeAccessDecision(
            String packageName,
            String description,
            @AccessResult int result) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(
                    TAG,
                    String.format(
                            "%s check: %s for %s",
                            description,
                            result == RESULT_ALLOWED
                                    ? "allowed"
                                    : result == RESULT_DENIED ? "denied" : "bypassed",
                            packageName));
        }

        if (mEventListener != null) {
            mEventListener.onAccessChecked(
                    packageName,
                    mUser,
                    description,
                    result);
        }

        return result != RESULT_DENIED;
    }
}
