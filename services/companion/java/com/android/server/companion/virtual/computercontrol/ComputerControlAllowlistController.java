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

package com.android.server.companion.virtual.computercontrol;

import static android.Manifest.permission.ACCESS_COMPUTER_CONTROL;
import static android.Manifest.permission.POST_NOTIFICATIONS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.KeyguardManager;
import android.app.role.RoleManager;
import android.companion.virtual.VirtualDeviceManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.SigningDetails;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.pm.permission.PermissionManagerServiceInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Manages allowlists/denylists for computer control.
 */
final class ComputerControlAllowlistController implements DeviceConfig.OnPropertiesChangedListener {
    private static final String TAG = "ComputerControlAllowlistController";
    private static final String SIGNED_PACKAGE_SEPARATOR = ",";
    private static final String CERTIFICATE_SEPARATOR = ":";
    private static final String INCLUDE_EVERYTHING_WILDCARD = "*";
    private static final String COMPUTER_CONTROL_DIR = "computercontrol";
    private static final String SESSION_OWNER_ALLOWLIST_FILE_NAME = "session_owner_allowlist.txt";
    private static final String AUTOMATABLE_APP_ALLOWLIST_FILE_NAME =
            "automatable_app_allowlist.txt";
    private static final String AUTOMATABLE_APP_DENYLIST_FILE_NAME = "automatable_app_denylist.txt";
    @VisibleForTesting
    static final String COMPUTER_CONTROL_NAMESPACE = "computer_control";
    @VisibleForTesting
    static final String COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY = "allowed_session_owner_apps";
    @VisibleForTesting
    static final String COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY = "allowed_automatable_apps";
    @VisibleForTesting
    static final String COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY = "blocked_automatable_apps";

    private final Context mContext;
    private final Executor mBackgroundExecutor;
    private final DeviceConfigRemoteList mSessionOwnerAllowlist;
    private final DeviceConfigRemoteList mAutomatableAppAllowlist;
    private final DeviceConfigRemoteList mAutomatableAppDenylist;
    private final boolean mBuildIsDebuggable;
    private final PermissionManagerServiceInterface mPermissionManager;
    private final ComputerControlDataStore mDataStore;
    // In debuggable builds, we can have a list of "super agents" (defined by a config resource)
    // which are allowed to automate any app.
    private final SignedPackageList mSuperAgentPackages = new SignedPackageList();
    // List of automatable apps per agent package uid, package name.
    // Maps: Agent UID -> (Agent Package Name -> Set of Target Package Names)
    @GuardedBy("mPerAgentAutomatableAppList")
    private final SparseArray<Map<String, Set<String>>> mPerAgentAutomatableAppList =
            new SparseArray<>();

    ComputerControlAllowlistController(@NonNull Context context) {
        this(context, BackgroundThread.getExecutor(),
                new File(new File(Environment.getDataSystemDirectory(), COMPUTER_CONTROL_DIR),
                        SESSION_OWNER_ALLOWLIST_FILE_NAME),
                new File(new File(Environment.getDataSystemDirectory(), COMPUTER_CONTROL_DIR),
                        AUTOMATABLE_APP_ALLOWLIST_FILE_NAME),
                new File(new File(Environment.getDataSystemDirectory(), COMPUTER_CONTROL_DIR),
                        AUTOMATABLE_APP_DENYLIST_FILE_NAME),
                LocalServices.getService(PermissionManagerServiceInterface.class),
                new ComputerControlDataStore(), Build.isDebuggable());
    }

    @VisibleForTesting
    ComputerControlAllowlistController(@NonNull Context context, @NonNull Executor executor,
            @NonNull File agentAllowlistFile, @NonNull File automatableAppsAllowlistFile,
            @NonNull File automatableAppsDenylistFile,
            @NonNull PermissionManagerServiceInterface permissionManager,
            @NonNull ComputerControlDataStore dataStore,
            boolean buildIsDebuggable) {
        mContext = context;
        mBackgroundExecutor = executor;
        mSessionOwnerAllowlist = new DeviceConfigRemoteList(agentAllowlistFile,
                COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY);
        mAutomatableAppAllowlist = new DeviceConfigRemoteList(automatableAppsAllowlistFile,
                COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY);
        mAutomatableAppDenylist = new DeviceConfigRemoteList(automatableAppsDenylistFile,
                COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY);
        mPermissionManager = permissionManager;
        mBuildIsDebuggable = buildIsDebuggable;
        mDataStore = dataStore;
        final Resources resources = context.getResources();
        try {
            final String[] superAgentConfigItems =
                    resources.getStringArray(R.array.config_computerControlKnownSuperAgents);
            List<SignedPackage> superAgents = new ArrayList<>();
            for (int i = 0; i < superAgentConfigItems.length; ++i) {
                superAgents.add(parse(superAgentConfigItems[i]));
            }
            mSuperAgentPackages.setSignedPackages(new SignedPackages(superAgents));
        } catch (Resources.NotFoundException e) {
            Slog.e(TAG, "superAgentPackages not found in resources", e);
        }
    }

    void monitor() {
        mAutomatableAppAllowlist.monitor();
        mSessionOwnerAllowlist.monitor();
        mAutomatableAppDenylist.monitor();
    }

    @Override
    public void onPropertiesChanged(@NonNull DeviceConfig.Properties properties) {
        final Set<String> keySet = properties.getKeyset();
        if (keySet.contains(COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY)) {
            Slog.v(TAG, "DeviceConfig onPropertiesChanged: Updating session owner allowlist");
            mSessionOwnerAllowlist.update();
        }
        if (keySet.contains(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY)) {
            Slog.v(TAG, "DeviceConfig onPropertiesChanged: Updating automatable app allowlist");
            mAutomatableAppAllowlist.update();
        }
        if (keySet.contains(COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY)) {
            Slog.v(TAG, "DeviceConfig onPropertiesChanged: Updating automatable app denylist");
            mAutomatableAppDenylist.update();
        }
    }

    void initialize() {
        Slog.v(TAG, "Initialize called, updating allowlists and adding DeviceConfig listener");

        mBackgroundExecutor.execute(() -> {
            mSessionOwnerAllowlist.update();
            mAutomatableAppAllowlist.update();
            mAutomatableAppDenylist.update();
            if (android.companion.virtualdevice.flags.Flags.computerControlPerAppConsent()) {
                SparseArray<Map<String, Set<String>>> automatableApps =
                        mDataStore.readAutomatableAppList();
                synchronized (mPerAgentAutomatableAppList) {
                    for (int i = 0; i < automatableApps.size(); i++) {
                        mPerAgentAutomatableAppList.put(automatableApps.keyAt(i),
                                automatableApps.valueAt(i));
                    }
                }
            }
        });
        DeviceConfig.addOnPropertiesChangedListener(
                COMPUTER_CONTROL_NAMESPACE, mBackgroundExecutor, this);

        if (android.companion.virtualdevice.flags.Flags.computerControlPerAppConsent()) {
            // Register broadcast receiver for package removal events.
            final IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
            intentFilter.addDataScheme("package");
            mContext.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    onPackageChanged(intent);
                }
            }, intentFilter);
        }
    }

    boolean isPackageAllowedToCreateSession(@Nullable String packageName,
            @NonNull PackageManager packageManager,
            @NonNull UserHandle ownerUser,
            int targetComputerControlVersion) {
        if (packageName == null) {
            return false;
        }

        final int packageUid = getPackageUid(packageName, packageManager);

        // Check if the caller actually has this packageName.
        if (!isPackageAssociatedToCallingUid(packageUid, packageName)) {
            return false;
        }

        final SigningDetails signingDetails = getSigningDetails(packageName, packageManager);
        if (isSuperAgent(packageName, signingDetails)) {
            Slog.i(TAG, "isPackageAllowedToCreateSession: Found super agent " + packageName);
            return true;
        }

        // Manually enforce the computer control session creation permission for non-super-agents.
        mContext.enforceCallingOrSelfPermission(
                ACCESS_COMPUTER_CONTROL, "Requires ACCESS_COMPUTER_CONTROL permission");
        mContext.enforceCallingOrSelfPermission(
                POST_NOTIFICATIONS, "Requires POST_NOTIFICATIONS permission");

        if (!hasComputerControlAccessPermission(packageUid) || packageUid == Process.SHELL_UID) {
            final ApplicationInfo appInfo = getApplicationInfo(packageName, packageManager);
            if (appInfo == null) {
                return false;
            }

            // Being here means that the permission was obtained via shell permission identity.
            if (isPackageTestOnly(appInfo)) {
                Slog.i(TAG, "isPackageAllowedToCreateSession: Found test agent " + packageName);
                return true;
            } else {
                Slog.e(TAG, "isPackageAllowedToCreateSession: Test agent " + packageName
                        + " is not testOnly app");
                return false;
            }
        }

        if (android.companion.virtualdevice.flags.Flags.computerControlRoleAssistantRequirement()) {
            final long token = Binder.clearCallingIdentity();
            try {
                RoleManager roleManager = mContext.getSystemService(RoleManager.class);
                if (roleManager == null
                        || !roleManager
                                .getRoleHoldersAsUser(RoleManager.ROLE_ASSISTANT, ownerUser)
                                .contains(packageName)) {
                    Slog.i(
                            TAG,
                            "isPackageAllowedToCreateSession: Calling application "
                                    + packageName
                                    + " is not the ASSISTANT role holder");
                    return false;
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        return isPackageApprovedToRunAutomation(packageName, ownerUser.getIdentifier());
    }

    /**
     * Returns whether the given package is an approved computer control agent.
     * @param packageName package name of the agent package
     * @param userId user id of the agent package
     */
    public boolean isPackageApprovedToRunAutomation(@NonNull String packageName,
            @UserIdInt int userId) {
        final PackageManager packageManager = getPackageManagerForUser(userId);
        final SigningDetails signingDetails = getSigningDetails(packageName, packageManager);
        if (signingDetails == null) {
            Slog.e(TAG, "isPackageAllowedToCreateSession: Failed to fetch signing details for "
                    + packageName);
            return false;
        }
        if (isSuperAgent(packageName, signingDetails)) {
            Slog.i(TAG, "isPackageAllowedToCreateSession: Found super agent " + packageName);
            return true;
        }
        final boolean isInAllowlist = mSessionOwnerAllowlist.anyMatch(packageName, signingDetails);
        Slog.i(TAG, "isPackageAllowedToCreateSession: Is there any allowlist entry for "
                + packageName + " : " + isInAllowlist);
        return isInAllowlist;
    }

    /**
     * Returns whether the given {@code targetPackage} is automatable in the provided computer
     * control session.
     *
     * @param targetPackage package name of the package that is being automated
     * @param session computer control session that is trying to automate the target app
     */
    boolean isPackageAutomatable(@Nullable String targetPackage,
            @NonNull ComputerControlSessionImpl session) {
        if (session.isTestSession()) {
            if (targetPackage == null) {
                return false;
            }
            final ApplicationInfo appInfo =
                    getApplicationInfo(targetPackage, session.getPackageManager());
            if (appInfo == null || !isPackageTestOnly(appInfo)) {
                Slog.e(TAG, "isPackageAutomatable: Test automation requires testOnly target "
                        + "package");
                return false;
            }
            final KeyguardManager keyguardManager = session.getKeyguardManager();
            if (keyguardManager != null && keyguardManager.isKeyguardSecure()) {
                Slog.e(TAG, "isPackageAutomatable: Test automation requires insecure keyguard");
                return false;
            }
            return true;
        }

        return isPackageAutomatable(targetPackage, session.getOwnerPackageName(),
                session.getPackageManager());
    }

    /**
     * Returns whether the given {@code targetPackage} is automatable by the provided agent package.
     *
     * @param agentPackage package name of the package that is automating {@code targetPackage}
     * @param targetPackage package name of the package that is being automated
     * @param packageManager package manager for the user of the agent package that is initiating
     *                       the automation request
     */
    boolean isPackageAutomatable(@Nullable String targetPackage,
            @Nullable String agentPackage, @NonNull PackageManager packageManager) {
        if (targetPackage == null || agentPackage == null) {
            return false;
        }

        final SigningDetails sessionOwnerPackageSigningDetails =
                getSigningDetails(agentPackage, packageManager);
        if (isSuperAgent(agentPackage, sessionOwnerPackageSigningDetails)) {
            Slog.i(TAG, "isPackageAutomatable: Found super agent " + agentPackage);
            return true;
        }

        final int sessionOwnerUid = getPackageUid(agentPackage, packageManager);
        final boolean isTestAgent = isTestAgent(sessionOwnerUid, agentPackage, packageManager);
        return isPackageTargetableForAutomation(targetPackage,
                Binder.getCallingUserHandle().getIdentifier(), isTestAgent);
    }

    /**
     * Returns whether the given {@code targetPackage} is an approved computer control session
     * target. This method checks whether the target package is a launchable app, is not
     * denylisted and is part of device allowlist of automatable apps.
     *
     * @param targetPackage package name of the package that is being automated
     * @param userId user id of the target package
     */
    public boolean isPackageTargetableForAutomation(@NonNull String targetPackage,
            @UserIdInt int userId) {
        return isPackageTargetableForAutomation(targetPackage, userId, /* isTestAgent= */false);
    }

    boolean isPackageTargetableForAutomation(@NonNull String targetPackage,
            @UserIdInt int userId, boolean isTestAgent) {
        final PackageManager packageManager = getPackageManagerForUser(userId);
        if (packageManager.getLaunchIntentForPackage(targetPackage) == null) {
            Slog.i(TAG, "isPackageAutomatable: No launch intent for package "
                    + targetPackage);
            return false;
        }

        if (Objects.equals(packageManager.getPermissionControllerPackageName(), targetPackage)) {
            Slog.i(TAG, "isPackageAutomatable: Cannot automate permission controller");
            return false;
        }
        if (isTestAgent) {
            final ApplicationInfo appInfo = getApplicationInfo(targetPackage, packageManager);
            if (appInfo == null || !isPackageTestOnly(appInfo)) {
                Slog.e(TAG, "isPackageAutomatable: Test automation requires testOnly target "
                        + "package");
                return false;
            } else {
                return true;
            }
        }
        return isPackageInAutomatableAppList(targetPackage, packageManager);
    }

    private boolean isPackageInAutomatableAppList(@NonNull String targetPackage,
            @NonNull PackageManager packageManager) {
        final SigningDetails targetPackageSigningDetails =
                getSigningDetails(targetPackage, packageManager);
        if (targetPackageSigningDetails == null) {
            Slog.e(TAG, "isPackageAutomatable: Failed to fetch signing details for target package "
                    + targetPackage);
            return false;
        }

        if (mSessionOwnerAllowlist.anyMatch(targetPackage, targetPackageSigningDetails)) {
            Slog.i(
                    TAG,
                    "isPackageAutomatable: Cannot automate an approved agent application: "
                            + targetPackage);
            return false;
        }

        // Check if the app is denylisted first.
        if (mAutomatableAppDenylist.anyMatch(targetPackage, targetPackageSigningDetails)) {
            Slog.i(TAG, "isPackageAutomatable: Found denylist entry for " + targetPackage);
            return false;
        }
        final ApplicationInfo appInfo = getApplicationInfo(targetPackage, packageManager);
        final boolean isInAllowlist = mAutomatableAppAllowlist.anyMatch(targetPackage,
                targetPackageSigningDetails, isPreInstalledApp(appInfo));
        Slog.i(TAG, "isPackageAutomatable: Is there any allowlist entry for " + targetPackage
                + " : " + isInAllowlist);
        return isInAllowlist;
    }

    /**
     * Returns whether the given {@param agentUid} and {@param agentPackageName} have consent to
     * automate the {@param targetPackage}
     */
    boolean doesAgentHaveConsentToAutomateTargetApp(int agentUid, @Nullable String agentPackageName,
            @Nullable String targetPackage) {
        if (!android.companion.virtualdevice.flags.Flags.computerControlPerAppConsent()) {
            return true;
        }
        if (targetPackage == null || agentPackageName == null) {
            return false;
        }
        // TODO(b/483624078): Consider making per app consent APIs @TestApis and create
        //  appropriate tests and CTS
        // Allow test agents to automate test targets regardless of formal consent records.
        final PackageManager pm = getPackageManagerForUser(UserHandle.getUserId(agentUid));
        if (isTestAgent(agentUid, agentPackageName, pm)) {
            final ApplicationInfo targetAppInfo = getApplicationInfo(targetPackage, pm);
            if (targetAppInfo != null && isPackageTestOnly(targetAppInfo)) {
                Slog.i(TAG, "doesAgentHaveConsentToAutomateTargetApp: Allowing test automation "
                        + "for test agent " + agentPackageName + " on target " + targetPackage);
                return true;
            }
        }
        synchronized (mPerAgentAutomatableAppList) {
            final Map<String, Set<String>> agentMap = mPerAgentAutomatableAppList.get(agentUid);
            if (agentMap == null) {
                return false;
            }
            final Set<String> allowList = agentMap.get(agentPackageName);
            return allowList != null && allowList.contains(targetPackage);
        }
    }

    String[] getAutomatableAppListForAgent(int agentUid, @NonNull String agentPackageName) {
        synchronized (mPerAgentAutomatableAppList) {
            final Map<String, Set<String>> agentMap = mPerAgentAutomatableAppList.get(agentUid);
            if (agentMap == null) {
                return new String[0];
            }
            final Set<String> allowlist = agentMap.get(agentPackageName);
            if (allowlist == null) {
                return new String[0];
            }
            return allowlist.toArray(new String[0]);
        }
    }

    private PackageManager getPackageManagerForUser(int userId) {
        final UserHandle user = UserHandle.of(userId);
        final long token = Binder.clearCallingIdentity();
        try {
            return mContext.createContextAsUser(user, /* flags = */ 0).getPackageManager();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    void addAppToAutomatableAppListForAgent(int agentUid, @NonNull String agentPackageName,
            @NonNull String packageName) {
        synchronized (mPerAgentAutomatableAppList) {
            Map<String, Set<String>> agentMap = mPerAgentAutomatableAppList.get(agentUid);
            if (agentMap == null) {
                agentMap = new ArrayMap<>();
                mPerAgentAutomatableAppList.put(agentUid, agentMap);
            }
            Set<String> allowlist = agentMap.get(agentPackageName);
            if (allowlist == null) {
                allowlist = new ArraySet<>();
                agentMap.put(agentPackageName, allowlist);
            }
            allowlist.add(packageName);
            scheduleWritePerAgentConsentData();
        }
    }

    void removeAppFromAutomatableAppListForAgent(int agentUid, @NonNull String agentPackageName,
            @NonNull String packageName) {
        synchronized (mPerAgentAutomatableAppList) {
            final Map<String, Set<String>> agentMap = mPerAgentAutomatableAppList.get(agentUid);
            if (agentMap == null) {
                return;
            }
            final Set<String> allowlist = agentMap.get(agentPackageName);
            if (allowlist == null) {
                return;
            }
            boolean changed = allowlist.remove(packageName);
            if (!changed) {
                return;
            }
            if (allowlist.isEmpty()) {
                agentMap.remove(agentPackageName);
                if (agentMap.isEmpty()) {
                    mPerAgentAutomatableAppList.remove(agentUid);
                }
            }
            scheduleWritePerAgentConsentData();
        }
    }

    void clearAutomatableAppListForAgent(int agentUid, @NonNull String agentPackageName) {
        synchronized (mPerAgentAutomatableAppList) {
            final Map<String, Set<String>> agentMap = mPerAgentAutomatableAppList.get(agentUid);
            if (agentMap == null) {
                return;
            }
            if (!agentMap.containsKey(agentPackageName)) {
                return;
            }
            agentMap.remove(agentPackageName);
            if (agentMap.isEmpty()) {
                mPerAgentAutomatableAppList.remove(agentUid);
            }
            scheduleWritePerAgentConsentData();
        }
    }

    private void onPackageChanged(@NonNull Intent intent) {
        final String action = intent.getAction();
        if (!Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            return;
        }
        final boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
        if (replacing) {
            return;
        }
        final String packageName = intent.getData().getSchemeSpecificPart();
        final int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
        if (packageName == null || uid == -1) {
            return;
        }
        Slog.i(TAG, "Package removed: " + packageName);
        final int userId = UserHandle.getUserId(uid);
        synchronized (mPerAgentAutomatableAppList) {
            boolean changed = false;
            // If the removed package was an agent, remove it from the agent map
            Map<String, Set<String>> agentMap = mPerAgentAutomatableAppList.get(uid);
            if (agentMap != null) {
                agentMap.remove(packageName);
                if (agentMap.isEmpty()) {
                    mPerAgentAutomatableAppList.remove(uid);
                }
                changed = true;
            }

            // If the removed package was a target app, remove it from all agents' lists
            // that belong to the same user.
            for (int i = 0; i < mPerAgentAutomatableAppList.size(); i++) {
                final int agentUid = mPerAgentAutomatableAppList.keyAt(i);
                if (UserHandle.getUserId(agentUid) == userId) {
                    Map<String, Set<String>> map = mPerAgentAutomatableAppList.valueAt(i);
                    for (Set<String> targets : map.values()) {
                        if (targets.remove(packageName)) {
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                scheduleWritePerAgentConsentData();
            }
        }
    }

    private void scheduleWritePerAgentConsentData() {
        // Create a copy of the data to write, to avoid holding the lock during I/O.
        final SparseArray<Map<String, Set<String>>> automatableAppsCopy = new SparseArray<>();
        synchronized (mPerAgentAutomatableAppList) {
            for (int i = 0; i < mPerAgentAutomatableAppList.size(); i++) {
                int key = mPerAgentAutomatableAppList.keyAt(i);
                Map<String, Set<String>> value = mPerAgentAutomatableAppList.valueAt(i);
                Map<String, Set<String>> valueCopy = new ArrayMap<>();
                for (Map.Entry<String, Set<String>> entry : value.entrySet()) {
                    valueCopy.put(entry.getKey(), new ArraySet<>(entry.getValue()));
                }
                automatableAppsCopy.put(key, valueCopy);
            }
        }
        mBackgroundExecutor.execute(() -> {
            mDataStore.writeAutomatableAppList(automatableAppsCopy);
        });
    }

    private static int getPackageUid(@NonNull String packageName,
            @NonNull PackageManager packageManager) {
        try {
            return packageManager.getPackageUidAsUser(packageName,
                    Binder.getCallingUserHandle().getIdentifier());
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "Failed to get uid for " + packageName, e);
            return Process.INVALID_UID;
        }
    }

    @Nullable
    private static ApplicationInfo getApplicationInfo(@Nullable String packageName,
            @NonNull PackageManager packageManager) {
        if (packageName == null) {
            return null;
        }
        try {
            return packageManager.getApplicationInfo(packageName,
                    PackageManager.ApplicationInfoFlags.of(0));
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "getApplicationInfo: Failed to fetch application info for "
                    + packageName);
            return null;
        }
    }

    boolean isTestAgent(int packageUid, @Nullable String packageName,
            @NonNull PackageManager packageManager) {
        if (hasComputerControlAccessPermission(packageUid) && packageUid != Process.SHELL_UID) {
            return false;
        }
        final ApplicationInfo appInfo = getApplicationInfo(packageName, packageManager);
        return appInfo != null && isPackageTestOnly(appInfo);
    }

    /**
     * Returns whether the given {@code packageName} is a "super agent". In debuggable builds, we
     * can have a list of "super agents" (defined by a config resource) which are allowed to
     * automate any app.
     */
    private boolean isSuperAgent(@NonNull String packageName,
            @Nullable SigningDetails signingDetails) {
        if (!mBuildIsDebuggable) {
            return false;
        }
        if (signingDetails == null) {
            return false;
        }
        return mSuperAgentPackages.anyMatch(packageName, signingDetails);
    }

    private boolean hasComputerControlAccessPermission(int packageUid) {
        final int permissionState = mPermissionManager.checkUidPermission(packageUid,
                ACCESS_COMPUTER_CONTROL, VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isPreInstalledApp(@Nullable ApplicationInfo applicationInfo) {
        return applicationInfo != null && applicationInfo.isSystemApp();
    }

    private static boolean isPackageTestOnly(@NonNull ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
    }

    private static boolean isPackageAssociatedToCallingUid(int packageUid,
            @NonNull String packageName) {
        final int callingUid = Binder.getCallingUid();
        if (packageUid != callingUid) {
            Slog.w(TAG, "Package with name " + packageName + " is not owned by calling uid "
                    + callingUid);
            return false;
        }
        return true;
    }

    @Nullable
    private static SigningDetails getSigningDetails(@NonNull String packageName,
            @NonNull PackageManager packageManager) {
        try {
            final PackageInfo packageInfo = packageManager.getPackageInfo(packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES);
            if (packageInfo == null || packageInfo.signingInfo == null) {
                return null;
            }
            return packageInfo.signingInfo.getSigningDetails();
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "Failed to get package info for " + packageName, e);
        }
        return null;
    }

    private static boolean packageSignatureMatches(@NonNull String packageName,
            @NonNull SigningDetails signingDetails, @NonNull SignedPackage signedPackage,
            boolean preinstalled) {
        if (!Objects.equals(packageName, signedPackage.getPackageName())) {
            return false;
        }

        if (preinstalled && signedPackage.matchAnyPreinstalled()) {
            return true;
        }

        return signingDetails.hasAncestorOrSelfWithDigest(
                Set.of(signedPackage.getCertificateDigest()));
    }

    /**
     * Parses a {@link SignedPackage} from a string input.
     *
     * @param input the package name, followed by a colon and a signing certificate digest
     * @return the parsed {@link SignedPackage}
     */
    @NonNull
    private static SignedPackage parse(@NonNull String input) throws IllegalArgumentException {
        final int certificateSeparatorIndex = input.indexOf(CERTIFICATE_SEPARATOR);
        if (certificateSeparatorIndex <= 0) {
            throw new IllegalArgumentException("Invalid signed package input: " + input);
        }
        final String packageName = input.substring(0, certificateSeparatorIndex);
        final String certificate = input.substring(certificateSeparatorIndex + 1);
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(certificate)) {
            throw new IllegalArgumentException("Invalid signed package input: " + input
                    + " , package name or certificate is empty");
        }
        return new SignedPackage(packageName, certificate);
    }

    /**
     * Parses {@link SignedPackages} from a string input.
     *
     * @param input the package names, each followed by a colon and a signing certificate digest
     * @return the parsed valid {@link SignedPackages}
     */
    @NonNull
    private static SignedPackages parseList(@NonNull String input) throws IllegalArgumentException {
        if (input.isEmpty()) {
            return new SignedPackages();
        }

        final List<SignedPackage> signedPackages = new ArrayList<>();
        final String[] parts = input.split(SIGNED_PACKAGE_SEPARATOR);
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid input string " + input);
        }

        for (int i = 0; i < parts.length; i++) {
            final String part = parts[i];
            if (INCLUDE_EVERYTHING_WILDCARD.equals(part)) {
                return new SignedPackages(true /* includeEverything */);
            }
            final SignedPackage signedPackage = parse(part);
            Slog.v(TAG, "Parsed signed package : " + signedPackage);
            signedPackages.add(signedPackage);
        }
        return new SignedPackages(signedPackages);
    }

    /** Represents an allowlist/denylist of package names and signatures. */
    private static class SignedPackageList {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        @Nullable
        private SignedPackages mSignedPackages = null;

        void monitor() {
            synchronized (mLock) { /* no-op */ }
        }

        boolean anyMatch(@NonNull String packageName,
                @NonNull SigningDetails signingDetails) {
            return anyMatch(packageName, signingDetails, false);
        }

        boolean anyMatch(@NonNull String packageName,
                @NonNull SigningDetails signingDetails, boolean preinstalled) {
            synchronized (mLock) {
                return mSignedPackages != null
                        && mSignedPackages.anyMatch(packageName, signingDetails, preinstalled);
            }
        }

        void setSignedPackages(SignedPackages signedPackages) {
            synchronized (mLock) {
                mSignedPackages = signedPackages;
            }
        }

        boolean isSameSignedPackagesObject(@NonNull SignedPackages signedPackages) {
            synchronized (mLock) {
                return Objects.equals(signedPackages, mSignedPackages);
            }
        }
    }

    /**
     * Represents a remote allowlist/denylist, automatically handles persistence of valid snapshots
     * from DeviceConfig.
     */
    private static final class DeviceConfigRemoteList extends SignedPackageList {
        private final ValueStorage mStorage;
        private final String mDeviceConfigKey;

        DeviceConfigRemoteList(@NonNull File file, @NonNull String deviceConfigKey) {
            mStorage = new ValueStorage(file);
            mDeviceConfigKey = deviceConfigKey;
        }

        @Override
        void monitor() {
            super.monitor();
            mStorage.monitor();
        }

        private void update() {
            final Pair<String, SignedPackages> parsedValueAndSignedPackages = readDeviceConfig();
            if (parsedValueAndSignedPackages != null) {
                if (isSameSignedPackagesObject(parsedValueAndSignedPackages.second)) {
                    // If the parsed value is same as what we already have right now,
                    // then nothing to do.
                    return;
                }
                // Persist the parsed value;
                mStorage.writeValue(parsedValueAndSignedPackages.first);
                // Update the cached value in memory.
                setSignedPackages(parsedValueAndSignedPackages.second);
                return;
            }

            // Read last persisted value, if we didn't find a valid value in DeviceConfig.
            final SignedPackages storedSignedPackages = mStorage.readPersistedValue();
            if (storedSignedPackages == null) {
                Slog.w(TAG, "No persisted value found for key " + mDeviceConfigKey);
                return;
            }

            Slog.v(TAG, "Using previously stored value " + storedSignedPackages + " for key "
                    + mDeviceConfigKey);
            // Update the cached value in memory.
            setSignedPackages(storedSignedPackages);
        }

        @SuppressLint("MissingPermission")
        @Nullable
        private Pair<String, SignedPackages> readDeviceConfig() {
            try {
                final String value = DeviceConfig.getString(COMPUTER_CONTROL_NAMESPACE,
                        mDeviceConfigKey, null /* defaultValue */);
                Slog.v(TAG, "Read value from DeviceConfig: " + value + " for key "
                        + mDeviceConfigKey);
                if (value == null) {
                    return null;
                }
                final SignedPackages signedPackages = parseList(value);
                return new Pair<>(value, signedPackages);
            } catch (Exception e) {
                Slog.e(TAG, "Failed to parse value from DeviceConfig for key "
                        + mDeviceConfigKey);
            }
            return null;
        }
    }

    private static final class ValueStorage {
        @NonNull
        private final AtomicFile mAtomicFile;
        @NonNull
        private final Object mLock = new Object();

        /**
         * Creates an instance that manages the value at the specified file path.
         *
         * @param file The file to read from and write to.
         */
        ValueStorage(@NonNull File file) {
            mAtomicFile = new AtomicFile(file);
        }

        void monitor() {
            synchronized (mLock) { /* no-op */ }
        }

        /**
         * Reads and parses the value from persistent storage.
         *
         * <p>If the file is found to be corrupt during reading or parsing, it will be deleted.
         *
         * @return A list of {@link SignedPackage}s if the file exists and is valid, or
         * {@code null}
         */
        @Nullable
        SignedPackages readPersistedValue() {
            synchronized (mLock) {
                if (!mAtomicFile.exists()) {
                    Slog.w(TAG, "Allowlist file does not exist.");
                    return null;
                }

                try {
                    byte[] data = mAtomicFile.readFully();
                    final String value = new String(data, StandardCharsets.UTF_8);
                    return parseList(value);
                } catch (Exception e) {
                    Slog.e(TAG, "Error reading or parsing allowlist file", e);
                    mAtomicFile.delete();
                    return null;
                }
            }
        }

        /**
         * Writes the given value to persistent storage atomically.
         *
         * @param value The raw string representation of the allowlist/denylist to be written.
         */
        void writeValue(@NonNull String value) {
            synchronized (mLock) {
                FileOutputStream fos = null;
                try {
                    fos = mAtomicFile.startWrite();
                    fos.write(value.getBytes(StandardCharsets.UTF_8));
                    mAtomicFile.finishWrite(fos);
                } catch (IOException e) {
                    Slog.e(TAG, "Error writing allowlist file", e);
                    if (fos != null) {
                        mAtomicFile.failWrite(fos);
                    }
                }
            }
        }
    }

    private static final class SignedPackages {
        private final List<SignedPackage> mSignedPackages;
        private final boolean mIncludeEverything;

        SignedPackages(@NonNull List<SignedPackage> signedPackages) {
            this(signedPackages, false /* includeEverything */);
        }

        SignedPackages(boolean includeEverything) {
            this(Collections.emptyList(), includeEverything);
        }

        SignedPackages() {
            this(Collections.emptyList(), false /* includeEverything */);
        }

        private SignedPackages(@NonNull List<SignedPackage> signedPackages,
                boolean includeEverything) {
            mSignedPackages = signedPackages;
            mIncludeEverything = includeEverything;
        }

        boolean anyMatch(@NonNull String packageName, @NonNull SigningDetails signingDetails,
                boolean preinstalled) {
            if (mIncludeEverything) {
                Slog.i(TAG, "Allowlist match for package " + packageName
                        + " as it includes everything");
                return true;
            }

            for (int i = 0; i < mSignedPackages.size(); i++) {
                final SignedPackage allowlistedPackage = mSignedPackages.get(i);
                if (packageSignatureMatches(packageName, signingDetails, allowlistedPackage,
                        preinstalled)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SignedPackages that)) {
                return false;
            }
            return mIncludeEverything == that.mIncludeEverything && Objects.equals(
                    mSignedPackages, that.mSignedPackages);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mSignedPackages, mIncludeEverything);
        }

        @Override
        public String toString() {
            return "SignedPackages{" + "mSignedPackages=" + mSignedPackages
                    + ", mIncludeEverything=" + mIncludeEverything + '}';
        }
    }

    @VisibleForTesting
    static final class SignedPackage {
        private static final String MATCH_ANY_PREINSTALLED = "PREINSTALLED";
        private final String mPackageName;
        private final String mCertificateDigest;

        SignedPackage(@NonNull String packageName, @NonNull String certificateDigest) {
            mPackageName = packageName;
            mCertificateDigest = certificateDigest;
        }

        @NonNull
        String getPackageName() {
            return mPackageName;
        }

        @NonNull
        String getCertificateDigest() {
            return mCertificateDigest;
        }

        boolean matchAnyPreinstalled() {
            return Objects.equals(mCertificateDigest, MATCH_ANY_PREINSTALLED);
        }

        @Override
        public String toString() {
            return mPackageName + ":" + mCertificateDigest;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SignedPackage that)) {
                return false;
            }
            return Objects.equals(mPackageName, that.mPackageName)
                    && Objects.equals(mCertificateDigest, that.mCertificateDigest);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPackageName, mCertificateDigest);
        }
    }
}
