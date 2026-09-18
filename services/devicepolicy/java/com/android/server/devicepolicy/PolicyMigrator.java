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

import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
import static com.android.server.devicepolicy.PolicyDefinition.CROSS_PROFILE_WIDGET_PROVIDER;
import static com.android.server.pm.UserManagerInternal.OWNER_TYPE_DEVICE_OWNER;
import static com.android.server.pm.UserManagerInternal.OWNER_TYPE_PROFILE_OWNER;
import static com.android.server.pm.UserManagerInternal.OWNER_TYPE_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE;

import android.annotation.NonNull;
import android.app.admin.BooleanPolicyValue;
import android.app.admin.BundlePolicyValue;
import android.app.admin.DevicePolicyManager;
import android.app.admin.IntegerPolicyValue;
import android.app.admin.LockTaskPolicy;
import android.app.admin.LongPolicyValue;
import android.app.admin.PackageSetPolicyValue;
import android.app.admin.PolicyIdentifier;
import android.app.admin.StringPolicyValue;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.app.admin.flags.Flags;
import android.os.Binder;
import android.annotation.Nullable;

import android.os.RemoteException;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.Slog;
import com.android.internal.widget.LockPatternUtils;
import com.android.server.pm.UserManagerInternal;

import com.android.server.utils.Slogf;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** Helper class for migrating policies from the legacy implementation to the policy engine. */
public class PolicyMigrator {

    private static final String LOG_TAG = DevicePolicyManagerService.LOG_TAG;

    interface Delegate {
        boolean isRuntimePermission(String permissionName);

        boolean canDPCManagedUserUseLockTaskLocked(int userId);

        void setBackwardCompatibleUserRestrictionLocked(
                int ownerType,
                EnforcingAdmin admin,
                int userId,
                String key,
                boolean enabled,
                boolean parent);

        int getPermissionGrantStateForUser(
                String packageName, String permission, String adminPackage, int userId)
                throws RemoteException;

        @NonNull
        <T> PolicyDefinition<T> getPolicyDefinitionForIdentifier(
                @NonNull PolicyIdentifier<T> policyIdentifier);
    }

    private final Injector mInjector;
    private final UserManager mUserManager;
    private final UserManagerInternal mUserManagerInternal;
    private final DevicePolicyEngine mDevicePolicyEngine;
    private final DeviceAdmins mDeviceAdmins;
    private final Delegate mDelegate;
    private final LockPatternUtils mLockPatternUtils;

    public PolicyMigrator(
            Injector injector,
            DevicePolicyEngine devicePolicyEngine,
            DeviceAdmins deviceAdmins,
            Delegate delegate) {
        mInjector = injector;
        mUserManager = mInjector.getUserManager();
        mUserManagerInternal = mInjector.getUserManagerInternal();
        mDevicePolicyEngine = devicePolicyEngine;
        mDeviceAdmins = deviceAdmins;
        mDelegate = delegate;
        mLockPatternUtils = injector.newLockPatternUtils();
    }

    boolean shouldMigrateV1ToDevicePolicyEngine() {
        return mInjector.binderWithCleanCallingIdentity(
                () -> !mDeviceAdmins.getOwners().isMigratedToPolicyEngine());
    }

    /**
     * @return {@code true} if policies were migrated successfully, {@code false} otherwise.
     */
    boolean migrateV1PoliciesToDevicePolicyEngineLocked() {
        return mInjector.binderWithCleanCallingIdentity(
                () -> {
                    try {
                        Slogf.i(
                                LOG_TAG,
                                "Started device policies migration to the device policy engine.");
                        migratePermittedInputMethodsPolicyLocked();
                        migrateAccountManagementDisabledPolicyLocked();
                        migrateUserControlDisabledPackagesLocked();

                        mDeviceAdmins.getOwners().markMigrationToPolicyEngine();
                        return true;
                    } catch (Exception e) {
                        mDevicePolicyEngine.clearAllPolicies();
                        Slogf.e(
                                LOG_TAG,
                                e,
                                "Error occurred during device policy migration, will "
                                        + "reattempt on the next system server restart.");
                        return false;
                    }
                });
    }

    /**
     * Migrates the initial set of policies to use policy engine. [b/318497672] Migrate policies
     * that weren't migrated properly in the initial migration on update from Android T to Android U
     */
    void maybeMigratePoliciesPostUpgradeToDevicePolicyEngineLocked() {
        if (!mDeviceAdmins.getOwners().isMigratedToPolicyEngine()
                || mDeviceAdmins.getOwners().isMigratedPostUpdate()) {
            return;
        }
        migratePoliciesPostUpgradeToDevicePolicyEngineLocked();
        mDeviceAdmins.getOwners().markPostUpgradeMigration();
    }

    boolean migratePoliciesPostUpgradeToDevicePolicyEngineLocked() {
        try {
            migrateScreenCapturePolicyLocked();
            migrateLockTaskPolicyLocked();
            migrateUserRestrictionsLocked();
            return true;
        } catch (Exception e) {
            Slogf.e(
                    LOG_TAG,
                    e,
                    "Error occurred during post upgrade migration to the device "
                            + "policy engine.");
            return false;
        }
    }

    /** Migrates the rest of policies to use policy engine. */
    void migratePoliciesToPolicyEngineLocked() {
        maybeMigrateSecurityLoggingPolicyLocked();
        // ID format: <sdk-int>.<auto_increment_id>.<descriptions>'
        String unmanagedBackupId = "35.1.unmanaged-mode";
        boolean unmanagedMigrated = maybeMigrateRequiredPasswordComplexityLocked(unmanagedBackupId);
        if (unmanagedMigrated) {
            Slogf.i(LOG_TAG, "Backup made: " + unmanagedBackupId);
        }

        String supervisionBackupId = "36.2.supervision-support";
        boolean supervisionMigrated = maybeMigrateResetPasswordTokenLocked(supervisionBackupId);
        supervisionMigrated |= maybeMigrateSuspendedPackagesLocked(supervisionBackupId);
        supervisionMigrated |= maybeMigrateSetKeyguardDisabledFeatures(supervisionBackupId);
        if (supervisionMigrated) {
            Slogf.i(LOG_TAG, "Backup made: " + supervisionBackupId);
        }

        String memoryTaggingBackupId = "36.3.memory-tagging";
        boolean memoryTaggingMigrated = maybeMigrateMemoryTaggingLocked(memoryTaggingBackupId);
        if (memoryTaggingMigrated) {
            Slogf.i(LOG_TAG, "Backup made: " + memoryTaggingBackupId);
        }

        String permissionBackupId = "37.1.permission-support";
        boolean permissionMigrated =
                maybeMigratePermissionGrantStatePoliciesLocked(permissionBackupId);
        if (permissionMigrated) {
            Slogf.i(LOG_TAG, "Backup made: " + permissionBackupId);
        }

        String appRestrictionsBackupId = "37.2.application-restrictions";
        boolean appRestrictionsMigrated =
                maybeMigrateApplicationRestrictionsLocked(appRestrictionsBackupId);
        if (appRestrictionsMigrated) {
            Slogf.i(LOG_TAG, "Backup made: " + appRestrictionsBackupId);
        }

        String crossProfileWidgetProviderBackupId = "37.3.cross-profile-widget-provider";
        boolean crossProfileWidgetProviderMigrated =
                maybeMigrateCrossProfileWidgetProviderLocked(crossProfileWidgetProviderBackupId);
        if (crossProfileWidgetProviderMigrated) {
            Slogf.i(LOG_TAG, "Backup made: " + crossProfileWidgetProviderBackupId);
        }

        final String commonCriteriaModeEnabledBackupId = "37.4.common-criteria-mode-enabled";
        final boolean commonCriteriaModeEnabledMigrated =
                maybeMigrateCommonCriteriaModeLocked(commonCriteriaModeEnabledBackupId);
        if (commonCriteriaModeEnabledMigrated) {
            Slogf.i(LOG_TAG, "Backup made: " + commonCriteriaModeEnabledBackupId);
        }

        final String lockScreenInfoBackupId = "37.5.lockscreen-info";
        final boolean unused = maybeMigrateLockScreenInfoLocked(lockScreenInfoBackupId);

        // Additional migration steps should repeat the pattern above with a new backupId.
    }

    private boolean maybeMigrateRequiredPasswordComplexityLocked(String backupId) {
        Slog.i(LOG_TAG, "Migrating password complexity to policy engine");
        if (!Flags.unmanagedModeMigration()) {
            return false;
        }
        if (mDeviceAdmins.getOwners().isRequiredPasswordComplexityMigrated()) {
            return false;
        }
        // Create backup if none exists
        mDevicePolicyEngine.createBackup(backupId);
        try {
            iterateThroughDpcAdminsLocked(
                    (admin, enforcingAdmin) -> {
                        int userId = enforcingAdmin.getUserId();
                        if (admin.mPasswordComplexity != PASSWORD_COMPLEXITY_NONE) {
                            mDevicePolicyEngine.setLocalPolicy(
                                    PolicyDefinition.PASSWORD_COMPLEXITY,
                                    enforcingAdmin,
                                    new IntegerPolicyValue(admin.mPasswordComplexity),
                                    userId);
                        }
                        ActiveAdmin parentAdmin = admin.getParentActiveAdmin();
                        if (parentAdmin != null
                                && parentAdmin.mPasswordComplexity != PASSWORD_COMPLEXITY_NONE) {
                            mDevicePolicyEngine.setLocalPolicy(
                                    PolicyDefinition.PASSWORD_COMPLEXITY,
                                    enforcingAdmin,
                                    new IntegerPolicyValue(parentAdmin.mPasswordComplexity),
                                    getProfileParentId(userId));
                        }
                    });
        } catch (Exception e) {
            Slog.wtf(LOG_TAG, "Failed to migrate password complexity to policy engine", e);
        }

        Slog.i(LOG_TAG, "Marking password complexity migration complete");
        mDeviceAdmins.getOwners().markRequiredPasswordComplexityMigrated();
        return true;
    }

    private boolean maybeMigrateResetPasswordTokenLocked(String backupId) {
        if (!Flags.resetPasswordWithTokenCoexistence()) {
            Slog.i(
                    LOG_TAG,
                    "ResetPasswordWithToken not migrated because coexistence "
                            + "support is not enabled.");
            return false;
        }
        if (mDeviceAdmins.getOwners().isResetPasswordWithTokenMigrated()) {
            // TODO(b/359187209): Remove log after Flags.resetPasswordWithTokenCoexistence full
            //  rollout.
            Slog.v(
                    LOG_TAG,
                    "ResetPasswordWithToken was previously migrated to " + "policy engine.");
            return false;
        }

        Slog.i(LOG_TAG, "Migrating ResetPasswordWithToken to policy engine");

        // Create backup if none exists
        mDevicePolicyEngine.createBackup(backupId);
        try {
            iterateThroughDpcAdminsLocked(
                    (admin, enforcingAdmin) -> {
                        int userId = enforcingAdmin.getUserId();
                        DevicePolicyData policy = mDeviceAdmins.getUserData(userId);
                        if (policy.mPasswordTokenHandle != 0) {
                            Slog.i(LOG_TAG, "Setting RESET_PASSWORD_TOKEN policy");
                            mDevicePolicyEngine.setLocalPolicy(
                                    PolicyDefinition.RESET_PASSWORD_TOKEN,
                                    enforcingAdmin,
                                    new LongPolicyValue(policy.mPasswordTokenHandle),
                                    userId);
                        }
                    });
        } catch (Exception e) {
            Slog.wtf(LOG_TAG, "Failed to migrate ResetPasswordWithToken to policy engine", e);
        }

        Slog.i(LOG_TAG, "Marking ResetPasswordWithToken migration complete");
        mDeviceAdmins.getOwners().markResetPasswordWithTokenMigrated();
        return true;
    }

    private boolean maybeMigrateSuspendedPackagesLocked(String backupId) {
        Slog.i(LOG_TAG, "Migrating suspended packages to policy engine");
        if (!Flags.suspendPackagesCoexistence()) {
            return false;
        }
        if (mDeviceAdmins.getOwners().isSuspendedPackagesMigrated()) {
            return false;
        }
        // Create backup if none exists
        mDevicePolicyEngine.createBackup(backupId);
        try {
            iterateThroughDpcAdminsLocked(
                    (admin, enforcingAdmin) -> {
                        if (admin.suspendedPackages == null
                                || admin.suspendedPackages.size() == 0) {
                            return;
                        }
                        int userId = enforcingAdmin.getUserId();
                        mDevicePolicyEngine.setLocalPolicy(
                                PolicyDefinition.PACKAGES_SUSPENDED,
                                enforcingAdmin,
                                new PackageSetPolicyValue(new ArraySet<>(admin.suspendedPackages)),
                                userId);
                    });
        } catch (Exception e) {
            Slog.wtf(LOG_TAG, "Failed to migrate suspended packages to policy engine", e);
        }

        Slog.i(LOG_TAG, "Marking suspended packages migration complete");
        mDeviceAdmins.getOwners().markSuspendedPackagesMigrated();
        return true;
    }

    private boolean maybeMigrateSetKeyguardDisabledFeatures(String backupId) {
        Slog.i(LOG_TAG, "Migrating set keyguard disabled features to policy engine");
        if (!Flags.setKeyguardDisabledFeaturesCoexistence()) {
            return false;
        }
        if (mDeviceAdmins.getOwners().isSetKeyguardDisabledFeaturesMigrated()) {
            return false;
        }
        // Create backup if none exists
        mDevicePolicyEngine.createBackup(backupId);
        try {
            iterateThroughDpcAdminsLocked(
                    (admin, enforcingAdmin) -> {
                        if (admin.disabledKeyguardFeatures == 0) {
                            return;
                        }
                        int userId = enforcingAdmin.getUserId();
                        var unused =
                                mDevicePolicyEngine.setLocalPolicy(
                                        PolicyDefinition.KEYGUARD_DISABLED_FEATURES,
                                        enforcingAdmin,
                                        new IntegerPolicyValue(admin.disabledKeyguardFeatures),
                                        userId);
                    });
        } catch (Exception e) {
            Slog.wtf(LOG_TAG, "Failed to migrate set keyguard disabled to policy engine", e);
        }

        Slog.i(LOG_TAG, "Marking set keyguard disabled features migration complete");
        mDeviceAdmins.getOwners().markSetKeyguardDisabledFeaturesMigrated();
        return true;
    }

    private boolean maybeMigrateMemoryTaggingLocked(String backupId) {
        if (mDeviceAdmins.getOwners().isMemoryTaggingMigrated()) {
            return false;
        }

        Slog.i(LOG_TAG, "Migrating Memory Tagging to policy engine");

        // Create backup if none exists
        mDevicePolicyEngine.createBackup(backupId);
        try {
            iterateThroughDpcAdminsLocked(
                    (admin, enforcingAdmin) -> {
                        if (admin.mtePolicy != 0) {
                            Slog.i(LOG_TAG, "Setting Memory Tagging policy");
                            mDevicePolicyEngine.setGlobalPolicy(
                                    PolicyDefinition.MEMORY_TAGGING,
                                    enforcingAdmin,
                                    new IntegerPolicyValue(admin.mtePolicy),
                                    true /* No need to re-set system properties */);
                        }
                    });
        } catch (Exception e) {
            Slog.wtf(LOG_TAG, "Failed to migrate Memory Tagging to policy engine", e);
        }

        Slog.i(LOG_TAG, "Marking Memory Tagging migration complete");
        mDeviceAdmins.getOwners().markMemoryTaggingMigrated();
        return true;
    }

    private boolean maybeMigratePermissionGrantStatePoliciesLocked(String backupId) {
        Slogf.i(LOG_TAG, "Migrating PERMISSION_GRANT policy to device policy engine.");
        if (!Flags.setPermissionGrantStateCoexistence()) {
            return false;
        }
        if (mDeviceAdmins.getOwners().isPermissionGrantStateMigrated()) {
            return false;
        }
        // Create backup if none exists
        mDevicePolicyEngine.createBackup(backupId);
        try {
            iterateThroughDpcAdminsLocked(
                    (admin, enforcingAdmin) -> {
                        int userId = enforcingAdmin.getUserId();

                        for (PackageInfo packageInfo : getInstalledPackagesOnUser(userId)) {
                            if (packageInfo.requestedPermissions == null) {
                                continue;
                            }
                            for (String permission : packageInfo.requestedPermissions) {
                                if (!isRuntimePermission(permission)) {
                                    continue;
                                }
                                int grantState = PERMISSION_GRANT_STATE_DEFAULT;
                                try {
                                    grantState =
                                            getPermissionGrantStateForUser(
                                                    packageInfo.packageName,
                                                    permission,
                                                    admin.info.getComponent().getPackageName(),
                                                    userId);
                                } catch (RemoteException e) {
                                    Slogf.e(
                                            LOG_TAG,
                                            e,
                                            "Error retrieving permission grant state for %s "
                                                    + "and %s",
                                            packageInfo.packageName,
                                            permission);
                                }
                                if (grantState == PERMISSION_GRANT_STATE_DEFAULT) {
                                    // Not Controlled by a policy
                                    continue;
                                }

                                var unused =
                                        mDevicePolicyEngine.setLocalPolicy(
                                                PolicyDefinition.PERMISSION_GRANT(
                                                        packageInfo.packageName, permission),
                                                enforcingAdmin,
                                                new IntegerPolicyValue(grantState),
                                                userId,
                                                /* skipEnforcePolicy= */ true);
                            }
                        }
                    });
        } catch (Exception e) {
            Slog.wtf(LOG_TAG, "Failed to migrate Permission Grant State to policy engine", e);
        }

        Slog.i(LOG_TAG, "Marking Permission Grant State migration complete");
        mDeviceAdmins.getOwners().markPermissionGrantStateMigrated();
        return true;
    }

    private boolean maybeMigrateApplicationRestrictionsLocked(String backupId) {
        Slog.i(LOG_TAG, "Migrating application restrictions to policy engine");
        if (!Flags.appRestrictionsCoexistence()) {
            return false;
        }
        if (mDeviceAdmins.getOwners().isSetApplicationRestrictionsMigrated()) {
            return false;
        }

        // Create backup if none exists
        mDevicePolicyEngine.createBackup(backupId);
        try {
            Binder.withCleanCallingIdentity(
                    () -> {
                        List<UserInfo> users = mUserManager.getUsers();
                        for (UserInfo userInfo : users) {
                            ActiveAdmin admin =
                                    mDeviceAdmins.getProfileOwnerOrDeviceOwner(userInfo.id);

                            // If admin is null, it means the user doesn't have a DPC. Otherwise,
                            // the user
                            // can still be a restricted user managed by Settings app.
                            EnforcingAdmin enforcingAdmin =
                                    admin != null
                                            ? EnforcingAdmin.createEnterpriseEnforcingAdmin(
                                                    admin.info.getComponent(), userInfo.id)
                                            : EnforcingAdmin.createSystemEnforcingAdmin(
                                                    "com.android.settings");
                            mUserManagerInternal
                                    .getApplicationRestrictionsForUser(userInfo.id)
                                    .forEach(
                                            (packageName, restrictions) -> {
                                                var unused =
                                                        mDevicePolicyEngine.setLocalPolicy(
                                                                PolicyDefinition
                                                                        .APPLICATION_RESTRICTIONS(
                                                                                packageName),
                                                                enforcingAdmin,
                                                                new BundlePolicyValue(restrictions),
                                                                userInfo.id);
                                            });
                        }
                    });
        } catch (Exception e) {
            Slog.wtf(LOG_TAG, "Failed to migrate application restrictions to policy engine", e);
        }

        Slog.i(LOG_TAG, "Marking set application restrictions migration complete");
        mDeviceAdmins.getOwners().markSetApplicationRestrictionsMigrated();
        return true;
    }

    private boolean maybeMigrateCrossProfileWidgetProviderLocked(String backupId) {
        if (!Flags.crossProfileWidgetProviderBulkApis()) {
            return false;
        }
        if (mDeviceAdmins.getOwners().isCrossProfileWidgetProviderMigrated()) {
            return false;
        }

        Slog.i(LOG_TAG, "Migrating Cross Profile Widget Provider to policy engine");

        // Create backup if none exists
        mDevicePolicyEngine.createBackup(backupId);
        try {
            iterateThroughDpcAdminsLocked(
                    (admin, enforcingAdmin) -> {
                        if (admin.crossProfileWidgetProviders == null
                                || admin.crossProfileWidgetProviders.isEmpty()) {
                            Slog.i(LOG_TAG, "Skip setting empty cross profile widget providers");
                            return;
                        }
                        int userId = enforcingAdmin.getUserId();
                        Slog.i(LOG_TAG, "Setting Cross Profile Widget Provider");
                        mDevicePolicyEngine.setLocalPolicy(
                                CROSS_PROFILE_WIDGET_PROVIDER,
                                enforcingAdmin,
                                new PackageSetPolicyValue(
                                        Set.copyOf(admin.crossProfileWidgetProviders)),
                                userId);
                    });
        } catch (Exception e) {
            Slog.wtf(
                    LOG_TAG, "Failed to migrate Cross Profile Widget Provider to policy engine", e);
        }

        Slog.i(LOG_TAG, "Marking Cross Profile Widget Provider migration complete");
        mDeviceAdmins.getOwners().markCrossProfileWidgetProviderMigrated();
        return true;
    }

    private boolean maybeMigrateCommonCriteriaModeLocked(String backupId) {
        if (!Flags.commonCriteriaModeCoexistence()) {
            return false;
        }
        if (mDeviceAdmins.getOwners().isCommonCriteriaModeMigrated()) {
            return false;
        }

        Slog.i(LOG_TAG, "Migrating Common Criteria Mode to policy engine");

        // Common Criteria Mode can be enabled either by DO or by COPE PO.
        final ActiveAdmin admin =
                mDeviceAdmins.getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDevice();
        if (admin == null) {
            Slog.i(LOG_TAG, "No appropriate admin found for migrating Common Criteria Mode");
            return false;
        }

        // Create backup if none exists
        mDevicePolicyEngine.createBackup(backupId);

        EnforcingAdmin enforcingAdmin =
                EnforcingAdmin.createEnterpriseEnforcingAdmin(
                        admin.info.getComponent(), admin.getUserHandle().getIdentifier());

        if (admin.mCommonCriteriaMode) {
            CompletableFuture<Integer> unused =
                    mDevicePolicyEngine.setGlobalPolicy(
                            PolicyDefinition.COMMON_CRITERIA_MODE,
                            enforcingAdmin,
                            new IntegerPolicyValue(
                                    DevicePolicyManager.COMMON_CRITERIA_MODE_ENABLED));
        } else {
            Slog.i(
                    LOG_TAG,
                    "Common Criteria Mode is disabled, skip setting policy in policy engine");
        }

        Slog.i(LOG_TAG, "Marking Common Criteria Mode migration complete");
        mDeviceAdmins.getOwners().markCommonCriteriaModeMigrated();

        return true;
    }

    private boolean maybeMigrateLockScreenInfoLocked(String backupId) {
        if (!Flags.lockscreenInfoCoexistence()) {
            return false;
        }
        if (mDeviceAdmins.getOwners().isLockScreenInfoMigrated()) {
            return false;
        }

        Slog.i(LOG_TAG, "Migrating Lock Screen Info to policy engine");

        // Lock Screen Info can be set either by DO or by COPE PO.
        final ActiveAdmin admin =
                mDeviceAdmins.getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDevice();
        if (admin == null) {
            Slog.i(LOG_TAG, "No appropriate admin found for migrating Lock Screen Info");
            return false;
        }

        final String lockScreenInfo = mLockPatternUtils.getDeviceOwnerInfo();
        if (lockScreenInfo != null) {
            // Create backup if none exists, but only if a policy value will be written.
            mDevicePolicyEngine.createBackup(backupId);
            Slog.i(LOG_TAG, "Backup made: " + backupId);

            EnforcingAdmin enforcingAdmin = EnforcingAdmin.createEnterpriseEnforcingAdmin(
                    admin.info.getComponent(),
                    admin.getUserHandle().getIdentifier()
            );

            final CompletableFuture<Integer> unused = mDevicePolicyEngine.setGlobalPolicy(
                    mDelegate.getPolicyDefinitionForIdentifier(PolicyIdentifier.LOCKSCREEN_MESSAGE),
                    enforcingAdmin,
                    new StringPolicyValue(lockScreenInfo)
            );
        } else {
            Slog.i(LOG_TAG, "Lock Screen Info is empty, skip setting policy in policy engine");
        }

        Slog.i(LOG_TAG, "Marking Lock Screen Info migration complete");
        mDeviceAdmins.getOwners().markLockScreenInfoMigrated();

        return true;
    }

    private void maybeMigrateSecurityLoggingPolicyLocked() {
        if (mDeviceAdmins.getOwners().isSecurityLoggingMigrated()) {
            return;
        }

        try {
            migrateSecurityLoggingPolicyInternalLocked();
        } catch (Exception e) {
            Slog.e(LOG_TAG, "Failed to properly migrate security logging to policy engine", e);
        }

        Slog.i(LOG_TAG, "Marking security logging policy migration complete");
        mDeviceAdmins.getOwners().markSecurityLoggingMigrated();
    }

    private void migrateSecurityLoggingPolicyInternalLocked() {
        Slog.i(LOG_TAG, "Migrating security logging policy to policy engine");
        if (!mInjector.securityLogGetLoggingEnabledProperty()) {
            Slog.i(LOG_TAG, "Security logs not enabled, exiting");
            return;
        }

        // Security logging can be enabled either by DO or by COPE PO.
        final ActiveAdmin admin =
                mDeviceAdmins.getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDevice();
        if (admin == null) {
            Slog.wtf(LOG_TAG, "Security logging is enabled, but no appropriate admin found");
            return;
        }

        EnforcingAdmin enforcingAdmin =
                EnforcingAdmin.createEnterpriseEnforcingAdmin(
                        admin.info.getComponent(), admin.getUserHandle().getIdentifier());
        mDevicePolicyEngine.setGlobalPolicy(
                PolicyDefinition.SECURITY_LOGGING, enforcingAdmin, new BooleanPolicyValue(true));
    }

    private void migrateUserControlDisabledPackagesLocked() {
        Binder.withCleanCallingIdentity(
                () -> {
                    List<UserInfo> users = mUserManager.getUsers();
                    for (UserInfo userInfo : users) {
                        ActiveAdmin admin = mDeviceAdmins.getProfileOwnerOrDeviceOwner(userInfo.id);
                        if (admin != null && admin.protectedPackages != null) {
                            EnforcingAdmin enforcingAdmin =
                                    EnforcingAdmin.createEnterpriseEnforcingAdmin(
                                            admin.info.getComponent(),
                                            admin.getUserHandle().getIdentifier());
                            if (mDeviceAdmins.isDeviceOwner(admin)) {
                                mDevicePolicyEngine.setGlobalPolicy(
                                        PolicyDefinition.USER_CONTROLLED_DISABLED_PACKAGES,
                                        enforcingAdmin,
                                        new PackageSetPolicyValue(
                                                new HashSet<>(admin.protectedPackages)));
                            } else {
                                mDevicePolicyEngine.setLocalPolicy(
                                        PolicyDefinition.USER_CONTROLLED_DISABLED_PACKAGES,
                                        enforcingAdmin,
                                        new PackageSetPolicyValue(
                                                new HashSet<>(admin.protectedPackages)),
                                        admin.getUserHandle().getIdentifier());
                            }
                        }
                    }
                });
    }

    private void migrateAccountManagementDisabledPolicyLocked() {
        Binder.withCleanCallingIdentity(
                () -> {
                    List<UserInfo> users = mUserManager.getUsers();
                    for (UserInfo userInfo : users) {
                        ActiveAdmin admin = mDeviceAdmins.getProfileOwnerOrDeviceOwner(userInfo.id);
                        if (admin != null) {
                            EnforcingAdmin enforcingAdmin =
                                    EnforcingAdmin.createEnterpriseEnforcingAdmin(
                                            admin.info.getComponent(),
                                            admin.getUserHandle().getIdentifier());
                            for (String accountType : admin.accountTypesWithManagementDisabled) {
                                mDevicePolicyEngine.setLocalPolicy(
                                        PolicyDefinition.ACCOUNT_MANAGEMENT_DISABLED(accountType),
                                        enforcingAdmin,
                                        new BooleanPolicyValue(true),
                                        admin.getUserHandle().getIdentifier());
                            }
                            if (admin.getParentActiveAdmin() != null) {
                                for (String accountType :
                                        admin.getParentActiveAdmin()
                                                .accountTypesWithManagementDisabled) {
                                    mDevicePolicyEngine.setLocalPolicy(
                                            PolicyDefinition.ACCOUNT_MANAGEMENT_DISABLED(
                                                    accountType),
                                            enforcingAdmin,
                                            new BooleanPolicyValue(true),
                                            getProfileParentId(
                                                    admin.getUserHandle().getIdentifier()));
                                }
                            }
                        }
                    }
                });
    }

    private void migratePermittedInputMethodsPolicyLocked() {
        Binder.withCleanCallingIdentity(
                () -> {
                    List<UserInfo> users = mUserManager.getUsers();
                    for (UserInfo userInfo : users) {
                        ActiveAdmin admin = mDeviceAdmins.getProfileOwnerOrDeviceOwner(userInfo.id);
                        if (admin != null) {
                            EnforcingAdmin enforcingAdmin =
                                    EnforcingAdmin.createEnterpriseEnforcingAdmin(
                                            admin.info.getComponent(),
                                            admin.getUserHandle().getIdentifier());
                            if (admin.permittedInputMethods != null) {
                                mDevicePolicyEngine.setLocalPolicy(
                                        PolicyDefinition.PERMITTED_INPUT_METHODS,
                                        enforcingAdmin,
                                        new PackageSetPolicyValue(
                                                new HashSet<>(admin.permittedInputMethods)),
                                        admin.getUserHandle().getIdentifier());
                            }
                            if (admin.getParentActiveAdmin() != null
                                    && admin.getParentActiveAdmin().permittedInputMethods != null) {
                                mDevicePolicyEngine.setLocalPolicy(
                                        PolicyDefinition.PERMITTED_INPUT_METHODS,
                                        enforcingAdmin,
                                        new PackageSetPolicyValue(
                                                new HashSet<>(
                                                        admin.getParentActiveAdmin()
                                                                .permittedInputMethods)),
                                        getProfileParentId(admin.getUserHandle().getIdentifier()));
                            }
                        }
                    }
                });
    }

    private void migrateScreenCapturePolicyLocked() {
        Binder.withCleanCallingIdentity(
                () -> {
                    ActiveAdmin admin =
                            mDeviceAdmins.getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDevice();
                    if (admin != null
                            && ((mDeviceAdmins.isDeviceOwner(admin) && admin.disableScreenCapture)
                                    || (admin.getParentActiveAdmin() != null
                                            && admin.getParentActiveAdmin()
                                                    .disableScreenCapture))) {

                        EnforcingAdmin enforcingAdmin =
                                EnforcingAdmin.createEnterpriseEnforcingAdmin(
                                        admin.info.getComponent(),
                                        admin.getUserHandle().getIdentifier());
                        mDevicePolicyEngine.setGlobalPolicy(
                                PolicyDefinition.SCREEN_CAPTURE_DISABLED,
                                enforcingAdmin,
                                new BooleanPolicyValue(true));
                    }

                    List<UserInfo> users = mUserManager.getUsers();
                    for (UserInfo userInfo : users) {
                        ActiveAdmin profileOwner = mDeviceAdmins.getProfileOwner(userInfo.id);
                        if (profileOwner != null && profileOwner.disableScreenCapture) {
                            EnforcingAdmin enforcingAdmin =
                                    EnforcingAdmin.createEnterpriseEnforcingAdmin(
                                            profileOwner.info.getComponent(),
                                            profileOwner.getUserHandle().getIdentifier());
                            mDevicePolicyEngine.setLocalPolicy(
                                    PolicyDefinition.SCREEN_CAPTURE_DISABLED,
                                    enforcingAdmin,
                                    new BooleanPolicyValue(true),
                                    profileOwner.getUserHandle().getIdentifier());
                        }
                    }
                });
    }

    private void migrateLockTaskPolicyLocked() {
        Binder.withCleanCallingIdentity(
                () -> {
                    ActiveAdmin deviceOwner = mDeviceAdmins.getDeviceOwnerAdmin();
                    if (deviceOwner != null) {
                        int doUserId = deviceOwner.getUserHandle().getIdentifier();
                        DevicePolicyData policies = mDeviceAdmins.getUserData(doUserId);
                        List<String> packages = policies.mLockTaskPackages;
                        int features = policies.mLockTaskFeatures;
                        // TODO: find out about persistent preferred activities
                        if (!packages.isEmpty()) {
                            setLockTaskPolicyInPolicyEngine(
                                    deviceOwner, doUserId, packages, features);
                        }
                    }

                    for (int userId : mUserManagerInternal.getUserIds()) {
                        ActiveAdmin profileOwner = mDeviceAdmins.getProfileOwner(userId);
                        if (profileOwner != null && canDPCManagedUserUseLockTaskLocked(userId)) {
                            DevicePolicyData policies = mDeviceAdmins.getUserData(userId);
                            List<String> packages = policies.mLockTaskPackages;
                            int features = policies.mLockTaskFeatures;
                            if (!packages.isEmpty()) {
                                setLockTaskPolicyInPolicyEngine(
                                        profileOwner, userId, packages, features);
                            }
                        }
                    }
                });
    }

    private void migrateUserRestrictionsLocked() {
        Binder.withCleanCallingIdentity(
                () -> {
                    List<UserInfo> users = mUserManager.getUsers();
                    for (UserInfo userInfo : users) {
                        ActiveAdmin admin = mDeviceAdmins.getProfileOwnerOrDeviceOwner(userInfo.id);
                        if (admin == null) continue;
                        ComponentName adminComponent = admin.info.getComponent();
                        int userId = userInfo.id;
                        EnforcingAdmin enforcingAdmin =
                                EnforcingAdmin.createEnterpriseEnforcingAdmin(
                                        adminComponent, userId);
                        int ownerType;
                        if (mDeviceAdmins.isDeviceOwner(admin)) {
                            ownerType = OWNER_TYPE_DEVICE_OWNER;
                        } else if (mDeviceAdmins.isProfileOwnerOfOrganizationOwnedDevice(
                                adminComponent, userId)) {
                            ownerType = OWNER_TYPE_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE;
                        } else if (mDeviceAdmins.isProfileOwner(adminComponent, userId)) {
                            ownerType = OWNER_TYPE_PROFILE_OWNER;
                        } else {
                            throw new IllegalStateException("Invalid DO/PO state");
                        }

                        for (final String restriction : admin.ensureUserRestrictions().keySet()) {
                            setBackwardCompatibleUserRestrictionLocked(
                                    ownerType,
                                    enforcingAdmin,
                                    userId,
                                    restriction, /* enabled */
                                    true, /* parent */
                                    false);
                        }
                        for (final String restriction :
                                admin.getParentActiveAdmin().ensureUserRestrictions().keySet()) {
                            setBackwardCompatibleUserRestrictionLocked(
                                    ownerType,
                                    enforcingAdmin,
                                    userId,
                                    restriction, /* enabled */
                                    true, /* parent */
                                    true);
                        }
                    }
                });
    }

    private void setLockTaskPolicyInPolicyEngine(
            ActiveAdmin admin, int userId, List<String> packages, int features) {
        EnforcingAdmin enforcingAdmin =
                EnforcingAdmin.createEnterpriseEnforcingAdmin(admin.info.getComponent(), userId);
        mDevicePolicyEngine.setLocalPolicy(
                PolicyDefinition.LOCK_TASK,
                enforcingAdmin,
                new LockTaskPolicy(new HashSet<>(packages), features),
                userId);
    }

    private int getProfileParentId(int userHandle) {
        return mInjector.binderWithCleanCallingIdentity(
                () -> {
                    UserInfo parentUser = mUserManager.getProfileParent(userHandle);
                    return parentUser != null ? parentUser.id : userHandle;
                });
    }

    private List<PackageInfo> getInstalledPackagesOnUser(int userId) {
        return mInjector.binderWithCleanCallingIdentity(
                () ->
                        mInjector.getPackageManager()
                                .getInstalledPackagesAsUser(
                                        PackageManager.PackageInfoFlags.of(
                                                PackageManager.GET_PERMISSIONS),
                                        userId));
    }

    private boolean canDPCManagedUserUseLockTaskLocked(int userId) {
        return mDelegate.canDPCManagedUserUseLockTaskLocked(userId);
    }

    private void setBackwardCompatibleUserRestrictionLocked(
            int ownerType,
            EnforcingAdmin admin,
            int userId,
            String key,
            boolean enabled,
            boolean parent) {
        mDelegate.setBackwardCompatibleUserRestrictionLocked(
                ownerType, admin, userId, key, enabled, parent);
    }

    private boolean isRuntimePermission(String permissionName) {
        return mDelegate.isRuntimePermission(permissionName);
    }

    private int getPermissionGrantStateForUser(
            String packageName, String permission, String adminPackage, int userId)
            throws RemoteException {
        return mDelegate.getPermissionGrantStateForUser(
                packageName, permission, adminPackage, userId);
    }

    private void iterateThroughDpcAdminsLocked(BiConsumer<ActiveAdmin, EnforcingAdmin> runner) {
        Binder.withCleanCallingIdentity(
                () -> {
                    List<UserInfo> users = mUserManager.getUsers();
                    for (UserInfo userInfo : users) {
                        ActiveAdmin admin = mDeviceAdmins.getProfileOwnerOrDeviceOwner(userInfo.id);
                        if (admin == null) continue;
                        EnforcingAdmin enforcingAdmin =
                                EnforcingAdmin.createEnterpriseEnforcingAdmin(
                                        admin.info.getComponent(), userInfo.id);

                        runner.accept(admin, enforcingAdmin);
                    }
                });
    }
}
