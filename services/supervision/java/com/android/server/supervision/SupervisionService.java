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

package com.android.server.supervision;

import static android.Manifest.permission.BYPASS_ROLE_QUALIFICATION;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.MANAGE_ROLE_HOLDERS;
import static android.Manifest.permission.MANAGE_SUPERVISION;
import static android.Manifest.permission.MANAGE_USERS;
import static android.Manifest.permission.QUERY_USERS;
import static android.app.role.RoleManager.ROLE_SUPERVISION;
import static android.app.role.RoleManager.ROLE_SYSTEM_SUPERVISION;
import static android.content.pm.PackageInstaller.SessionParams.MAX_PACKAGE_NAME_LENGTH;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Settings.Secure.BROWSER_CONTENT_FILTERS_ENABLED;
import static android.provider.Settings.Secure.SEARCH_CONTENT_FILTERS_ENABLED;

import static com.android.internal.util.Preconditions.checkCallAuthorization;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.StatsManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManagerInternal;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.app.supervision.ISupervisionListener;
import android.app.supervision.ISupervisionManager;
import android.app.supervision.PackageUsagePolicy;
import android.app.supervision.Policy;
import android.app.supervision.PolicyKey;
import android.app.supervision.SupervisionManager;
import android.app.supervision.SupervisionManagerInternal;
import android.app.supervision.SupervisionRecoveryInfo;
import android.app.supervision.flags.Flags;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.SparseArray;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.internal.util.ConcurrentUtils;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.util.FunctionalUtils.RemoteExceptionIgnoringConsumer;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.LocalServices;
import com.android.server.ServiceThread;
import com.android.server.SystemService;
import com.android.server.appbinding.AppBindingService;
import com.android.server.appbinding.AppServiceConnection;
import com.android.server.appbinding.finders.SupervisionAppServiceFinder;
import com.android.server.pm.UserManagerInternal;
import com.android.server.supervision.SupervisionUserData.PolicyData;
import com.android.server.utils.Slogf;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/** Service for handling system supervision. */
public class SupervisionService extends ISupervisionManager.Stub {
    /**
     * Activity action: Requests user confirmation of supervision credentials.
     *
     * <p>Use {@link Activity#startActivityForResult} to launch this activity. The result will be
     * {@link Activity#RESULT_OK} if credentials are valid.
     *
     * <p>If supervision credentials are not configured, this action initiates the setup flow.
     */
    @VisibleForTesting
    static final String ACTION_CONFIRM_SUPERVISION_CREDENTIALS =
            "android.app.supervision.action.CONFIRM_SUPERVISION_CREDENTIALS";

    @VisibleForTesting static final String SETTINGS_PACKAGE_NAME = "com.android.settings";

    @VisibleForTesting
    static final List<String> SYSTEM_ENTITIES =
            List.of(SupervisionManager.SUPERVISION_SYSTEM_ENTITY);

    // TODO(b/362756788): Does this need to be a LockGuard lock?
    private final Object mLockDoNoUseDirectly = new Object();

    private final Injector mInjector;
    private final RoleObserver mRoleObserver;
    final SupervisionManagerInternal mInternal = new SupervisionManagerInternalImpl();

    @GuardedBy("getLockObject()")
    final ArrayMap<IBinder, SupervisionListenerRecord> mSupervisionListeners = new ArrayMap<>();

    // We need to create a new background thread here because the AppBindingService uses the
    // BackgroundThread for its connection callbacks. Using the same thread would block while
    // waiting for those callbacks, preventing the new connections from being perceived.
    final Handler mServiceThreadHandler;
    final SupervisionPackageMonitor mPackageMonitor = new SupervisionPackageMonitor();
    public static final boolean DEBUG = false; // DO NOT SUBMIT WITH TRUE

    @GuardedBy("getLockObject()")
    final SupervisionSettings mSupervisionSettings = SupervisionSettings.getInstance();

    private boolean mAllowBypassingSupervisionRoleQualification = false;

    public SupervisionService(Context context) {
        this(new Injector(context.createAttributionContext(SupervisionLog.TAG)));
    }

    @VisibleForTesting
    SupervisionService(Injector injector) {
        mInjector = injector;
        mServiceThreadHandler = injector.getServiceThreadHandler();
        mInjector.getUserManagerInternal().addUserLifecycleListener(new UserLifecycleListener());
        mRoleObserver = new RoleObserver();
        mRoleObserver.register();
    }

    /**
     * Returns whether supervision is enabled for the given user.
     *
     * <p>Supervision is automatically enabled when the supervision app becomes the profile owner or
     * explicitly enabled via an internal call to {@link #setSupervisionEnabledForUser}.
     */
    @Override
    public boolean isSupervisionEnabledForUser(@UserIdInt int userId) {
        enforceAnyPermission(QUERY_USERS, MANAGE_USERS);
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }
        synchronized (getLockObject()) {
            return getUserDataLocked(userId).supervisionEnabled;
        }
    }

    @Override
    public void setSupervisionEnabledForUser(@UserIdInt int userId, boolean enabled) {
        enforceCallerCanSetSupervisionEnabled();
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }
        setSupervisionEnabledForUserInternal(userId, enabled, getSystemSupervisionPackage());
    }

    private void registerStatsPullAtomCallback() {
        StatsManager statsManager = mInjector.context.getSystemService(StatsManager.class);
        if (statsManager == null) {
            return;
        }
        statsManager.setPullAtomCallback(
                FrameworkStatsLog.SUPERVISION_STATE,
                null, // metadata
                ConcurrentUtils.DIRECT_EXECUTOR,
                this::onPullAtom);
    }

    @VisibleForTesting
    int onPullAtom(int atomTag, List<android.util.StatsEvent> data) {
        boolean isCredentialSet = hasSupervisionCredentials();

        synchronized (getLockObject()) {
            SupervisionRecoveryInfo recoveryInfo = getSupervisionRecoveryInfo();
            int recoveryState = (recoveryInfo != null) ? recoveryInfo.getState() : -1;

            mSupervisionSettings.forEachUserData(
                    (userId, userData) -> {
                        data.add(
                                FrameworkStatsLog.buildStatsEvent(
                                        atomTag,
                                        userData.supervisionEnabled,
                                        isCredentialSet,
                                        recoveryState,
                                        userId));
                    });
        }

        return StatsManager.PULL_SUCCESS;
    }

    /**
     * Returns the package name of the active supervision app or null if supervision is disabled.
     */
    @Override
    @Nullable
    public String getActiveSupervisionAppPackage(@UserIdInt int userId) {
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }
        synchronized (getLockObject()) {
            return getUserDataLocked(userId).supervisionAppPackage;
        }
    }

    /**
     * Creates an {@link Intent} that can be used with {@link Context#startActivityForResult(String,
     * Intent, int, Bundle)} to launch the activity to verify supervision credentials.
     *
     * <p>A valid {@link Intent} is always returned if supervision is enabled at the time this
     * method is called, the launched activity still need to perform validity checks as the
     * supervision state can change when it's launched. A null intent is returned if supervision is
     * disabled at the time of this method call.
     *
     * <p>A result code of {@link android.app.Activity#RESULT_OK} indicates successful verification
     * of the supervision credentials.
     */
    @Override
    @Nullable
    public Intent createConfirmSupervisionCredentialsIntent(@UserIdInt int userId) {
        enforceAnyPermission(QUERY_USERS, MANAGE_USERS);
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }

        return Binder.withCleanCallingIdentity(
                () -> {
                    if (!isSupervisionEnabledForUser(userId)
                            || !hasAnySupervisionApprovalMethods(userId)) {
                        return null;
                    }

                    final Intent intent = new Intent(ACTION_CONFIRM_SUPERVISION_CREDENTIALS);
                    // explicitly set the package for security
                    intent.setPackage(SETTINGS_PACKAGE_NAME);

                    return intent;
                });
    }

    /** Set the Supervision Recovery Info. */
    @Override
    public void setSupervisionRecoveryInfo(SupervisionRecoveryInfo recoveryInfo) {
        checkCallAuthorization(isCallerSystem());

        synchronized (getLockObject()) {
            mSupervisionSettings.saveRecoveryInfo(recoveryInfo);
        }

        maybeApplyUserRestrictions();
    }

    /** Returns the Supervision Recovery Info or null if recovery is not set. */
    @Override
    public SupervisionRecoveryInfo getSupervisionRecoveryInfo() {
        checkCallAuthorization(isCallerSystem());

        synchronized (getLockObject()) {
            return mSupervisionSettings.getRecoveryInfo();
        }
    }

    @Override
    @RequiresPermission(MANAGE_ROLE_HOLDERS)
    public boolean shouldAllowBypassingSupervisionRoleQualification() {
        enforcePermission(MANAGE_ROLE_HOLDERS);
        if (!Flags.enableSupervisionManagerPolicyApis()) {
            return shouldAllowBypassingSupervisionRoleQualificationBasedOnState();
        }

        if (hasNonTestDefaultUsers()) {
            return false;
        }

        synchronized (getLockObject()) {
            return mAllowBypassingSupervisionRoleQualification;
        }
    }

    private boolean shouldAllowBypassingSupervisionRoleQualificationBasedOnState() {
        if (hasNonTestDefaultUsers()) {
            return false;
        }

        synchronized (getLockObject()) {
            if (mSupervisionSettings.anySupervisedUser()) {
                return false;
            }
        }

        return true;
    }

    @Override
    @RequiresPermission(BYPASS_ROLE_QUALIFICATION)
    public void setShouldAllowBypassingSupervisionRoleQualification(boolean allowBypassing) {
        enforcePermission(BYPASS_ROLE_QUALIFICATION);
        synchronized (getLockObject()) {
            mAllowBypassingSupervisionRoleQualification = allowBypassing;
        }
    }

    private boolean hasAnySupervisionApprovalMethods(@UserIdInt int userId) {
        return hasSupervisionCredentials() || hasAnySupervisionAppApprovalMethods(userId);
    }

    @Override
    public boolean hasSupervisionCredentials() {
        enforceAnyPermission(QUERY_USERS, MANAGE_USERS);

        // Verify the supervising user profile exists and has a secure credential set.
        final int supervisingUserId = mInjector.getUserManagerInternal().getSupervisingProfileId();
        final long token = Binder.clearCallingIdentity();
        try {
            if (supervisingUserId == UserHandle.USER_NULL
                    || !mInjector.getKeyguardManager().isDeviceSecure(supervisingUserId)) {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        return true;
    }

    private boolean hasAnySupervisionAppApprovalMethods(@UserIdInt int userId) {
        if (!Flags.enableSupervisionSettingsUiUpdates()) {
            return false;
        }
        return !querySupervisionApprovalActivities(userId).isEmpty();
    }

    @Override
    public void registerSupervisionListener(
            @UserIdInt int userId, @Nullable ISupervisionListener listener) {
        if (listener == null) {
            return;
        }
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }

        synchronized (getLockObject()) {
            SupervisionListenerRecord record = mSupervisionListeners.get(listener.asBinder());
            if (record == null) {
                try {
                    mSupervisionListeners.put(
                            listener.asBinder(), new SupervisionListenerRecord(listener, userId));
                } catch (RemoteException e) {
                    // Binder died, ignore
                }
            }
        }
    }

    @Override
    public void unregisterSupervisionListener(@Nullable ISupervisionListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (getLockObject()) {
            SupervisionListenerRecord record = mSupervisionListeners.remove(listener.asBinder());
            if (record != null) {
                record.unlinkToDeath();
            }
        }
    }

    /**
     * Returns true if the user has at least one supervision credential recovery method available.
     */
    @Override
    public boolean canLaunchPinRecovery(int userId) {
        if (!Flags.enableSupervisionSettingsUiUpdates()) {
            return false;
        }
        List<ResolveInfo> activities = querySupervisionApprovalActivities(userId);
        SupervisionRecoveryInfo recoveryInfo = getSupervisionRecoveryInfo();
        return !activities.isEmpty()
                || (recoveryInfo != null && !recoveryInfo.getAccountName().isEmpty());
    }

    @Override
    public List<Policy> getPolicies(@UserIdInt int userId) {
        enforceCallerCanGetPolicies();
        return mSupervisionSettings.getUserData(userId).policies.getPolicies();
    }

    @Override
    public void setPolicy(@UserIdInt int userId, @NonNull Policy policy) {
        enforceCallerCanSetPolicy();
        synchronized (getLockObject()) {
            validatePolicyLocked(userId, policy);
            policy.incrementVersion();
            getUserDataLocked(userId).policies.add(policy);
            mSupervisionSettings.saveUserData();
            SupervisionManager.invalidateGetPoliciesCache();
        }

        executeOnServiceThread(
                () -> {
                    applyPolicy(userId, policy);
                    dispatchSupervisionEvent(userId, listener -> listener.onPolicyChanged(policy));
                });
    }

    /**
     * Returns true if the user has a verified recovery email or if there exist alternative recovery
     * methods.
     */
    @Override
    public boolean hasValidRecoveryMethod(int userId) {
        if (!Flags.enableSupervisionSettingsUiUpdates()) {
            return false;
        }
        List<ResolveInfo> activities = querySupervisionApprovalActivities(userId);
        SupervisionRecoveryInfo recoveryInfo = getSupervisionRecoveryInfo();
        return !activities.isEmpty()
                || (recoveryInfo != null
                        && (recoveryInfo.getState() == SupervisionRecoveryInfo.STATE_VERIFIED));
    }

    private void clearAllPolicies(@UserIdInt int userId) {
        if (!Flags.enableSupervisionManagerPolicyApis()) {
            return;
        }
        synchronized (getLockObject()) {
            SupervisionUserData data = getUserDataLocked(userId);
            if (data.policies.isEmpty()) {
                return;
            }
            data.policies.clear();
            mSupervisionSettings.saveUserData();
            SupervisionManager.invalidateGetPoliciesCache();
        }
    }

    private void applyPolicy(@UserIdInt int userId, @NonNull Policy policy) {
        switch (policy) {
            case PackageUsagePolicy pp -> applyPackageUsagePolicy(userId, pp);
            default -> Slogf.w(SupervisionLog.TAG, "Unsupported policy type.");
        }
    }

    private void applyPackageUsagePolicy(@UserIdInt int userId, PackageUsagePolicy policy) {
        String packageName = policy.getPackageName();
        int type = policy.getType();
        switch (type) {
            case PackageUsagePolicy.TYPE_BLOCKED -> {
                if (Flags.enableSupervisionPackageUsageApis()) {
                    setPackageSuspendedForUser(userId, packageName, false);
                }
                setApplicationHiddenForUser(userId, packageName, true);
                enablePendingNotificationStateLocked(userId, policy);
            }
            case PackageUsagePolicy.TYPE_ALLOWED -> {
                if (Flags.enableSupervisionPackageUsageApis()) {
                    setPackageSuspendedForUser(userId, packageName, false);
                }
                setApplicationHiddenForUser(userId, packageName, false);
                enablePendingNotificationStateLocked(userId, policy);
            }
            case PackageUsagePolicy.TYPE_TIME_LIMIT -> {
                if (Flags.enableSupervisionPackageUsageApis()) {
                    setApplicationHiddenForUser(userId, packageName, false);
                    // TODO(b/482425646): Only suspend the package when limit is reached.
                    setPackageSuspendedForUser(userId, packageName, true);
                }
            }
            default ->
                    Slogf.w(
                            SupervisionLog.TAG,
                            "Unsupported restriction type: %s for package: %s",
                            type,
                            packageName);
        }
    }

    private void enablePendingNotificationStateLocked(
            @UserIdInt int userId, PackageUsagePolicy policy) {
        synchronized (getLockObject()) {
            PolicyData policyData = getUserDataLocked(userId).policies.get(policy.getPolicyKey());
            if (policyData != null) {
                policyData.hasPendingNotification = true;
            }
        }
    }

    @Override
    public List<ResolveInfo> querySupervisionApprovalActivities(int userId) {
        checkCallAuthorization(isCallerSystem());

        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }

        UserHandle userHandle = UserHandle.of(userId);

        PackageManager packageManager =
                mInjector
                        .context
                        .createContextAsUser(userHandle, /* flags= */ 0)
                        .getPackageManager();
        List<String> supervisionPackages =
                mInjector.getRoleHoldersAsUser(ROLE_SUPERVISION, userHandle);

        Intent intent = new Intent(SupervisionManager.ACTION_CONFIRM_SUPERVISION_APPROVAL);
        List<ResolveInfo> availableMethods = new ArrayList<>();

        for (String packageName : supervisionPackages) {
            intent.setPackage(packageName);
            List<ResolveInfo> resolveInfo =
                    packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            availableMethods.addAll(resolveInfo);
        }

        // Sort alphabetically by label loaded using the user-specific package manager
        availableMethods.sort(
                Comparator.comparing(ri -> ri.loadLabel(packageManager), CharSequence::compare));

        return availableMethods;
    }

    private void setApplicationHiddenForUser(
            @UserIdInt int userId, String packageName, boolean hidden) {
        DevicePolicyManagerInternal dpmi = mInjector.getDpmInternal();
        if (dpmi != null) {
            dpmi.setApplicationHiddenBySystem(
                    SupervisionManager.SUPERVISION_SYSTEM_ENTITY, packageName, userId, hidden);
        }
    }

    private void setPackageSuspendedForUser(
            @UserIdInt int userId, String packageName, boolean suspended) {
        PackageManagerInternal pmi = mInjector.getPackageManagerInternal();
        if (pmi != null) {
            pmi.setPackagesSuspendedByAdmin(userId, new String[] {packageName}, suspended);
        }
    }

    private void validatePolicyLocked(@UserIdInt int userId, @NonNull Policy policy) {
        switch (policy) {
            case PackageUsagePolicy pp -> validatePackageUsagePolicy(pp);
            default -> {
                throw new IllegalArgumentException(
                        "Unsupported policy type: " + policy.getClass().getSimpleName());
            }
        }
        long currentPolicyVersion =
                getUserDataLocked(userId).policies.getCurrentVersion(policy.getPolicyKey());
        if (currentPolicyVersion != policy.getVersion()) {
            throw new IllegalArgumentException("Policy version mismatch.");
        }
    }

    private void validatePackageUsagePolicy(@NonNull PackageUsagePolicy policy) {
        if (policy.getPackageName().isEmpty()
                || policy.getPackageName().length() > MAX_PACKAGE_NAME_LENGTH
                || !PackageUsagePolicy.isTypeValid(policy.getType())) {
            throw new IllegalArgumentException("Invalid package policy");
        }
    }

    private void postApplicationHiddenNotification(
            @UserIdInt int userId, String packageName, boolean hidden) {
        String title = mInjector.context.getString(R.string.supervision_blocked_app_title);
        CharSequence appLabel;
        try {
            PackageManager pm = mInjector.getPackageManager();
            appLabel =
                    pm.getApplicationInfoAsUser(
                                    packageName,
                                    PackageManager.MATCH_UNINSTALLED_PACKAGES,
                                    UserHandle.of(userId))
                            .loadLabel(pm);
        } catch (PackageManager.NameNotFoundException e) {
            Slogf.e(
                    SupervisionLog.TAG,
                    "postApplicationHiddenNotification: package not found " + packageName);
            // Package is uninstalled, just skip the notification.
            return;
        }

        String text =
                mInjector.context.getString(
                        hidden
                                ? R.string.supervision_blocked_app_content
                                : R.string.supervision_unblocked_app_content,
                        appLabel);

        final Intent intent =
                hidden
                        ? new Intent(Settings.ACTION_SUPERVISION_SETTINGS)
                                .setPackage(SETTINGS_PACKAGE_NAME)
                        : mInjector.getPackageManager().getLaunchIntentForPackage(packageName);

        final Bundle extras = new Bundle();
        extras.putString(
                Notification.EXTRA_SUBSTITUTE_APP_NAME,
                mInjector.context.getString(R.string.notification_channel_parental_controls));
        final Notification notification =
                new Notification.Builder(
                                mInjector.context, SystemNotificationChannels.PARENTAL_CONTROLS)
                        .setSmallIcon(R.drawable.ic_account_child_invert)
                        .setTicker(title)
                        .setColor(
                                mInjector.context.getColor(
                                        R.color.system_notification_accent_color))
                        .setContentTitle(title)
                        .setContentText(text)
                        .setContentIntent(createActivityPendingIntent(intent, userId))
                        .setExtras(extras)
                        .build();
        mInjector
                .getNotificationManager()
                .notifyAsUser(
                        /* tag= */ packageName, /* id= */ 0, notification, UserHandle.of(userId));
    }

    private PendingIntent createActivityPendingIntent(Intent intent, int userId) {
        if (intent == null) {
            return null;
        }
        return PendingIntent.getActivityAsUser(
                mInjector.context,
                /* requestCode= */ 0,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
                /* options= */ null,
                UserHandle.of(userId));
    }

    @Override
    public List<UserInfo> getUsersThatRequirePlatformCredential() {
        if (!Flags.enableSupervisionSettingsUiUpdates()) {
            return List.of();
        }
        List<UserInfo> users =
                mInjector
                        .getUserManagerInternal()
                        .getUsers(UserManagerInternal.USER_FILTER_WITH_ALL_COMPLETE_USERS);
        return users.stream().filter(this::doesUserRequirePlatformCredential).toList();
    }

    /**
     * Returns true if the user requires a platform credential.
     *
     * <p>A user requires a platform credential if they have supervision enabled and there is no
     * supervision approval activity for the user.
     */
    private boolean doesUserRequirePlatformCredential(UserInfo userInfo) {
        if (!isSupervisionEnabledForUser(userInfo.id)) {
            return false;
        }
        List<String> roleHolders =
                mInjector.getRoleHoldersAsUser(ROLE_SUPERVISION, UserHandle.of(userInfo.id));
        List<String> packagesWithSupervisionApprovalActivities =
                querySupervisionApprovalActivities(userInfo.id).stream()
                        .map(resolveInfo -> resolveInfo.activityInfo.packageName)
                        .toList();
        return roleHolders.isEmpty()
                || roleHolders.stream()
                        .anyMatch(pkg -> !packagesWithSupervisionApprovalActivities.contains(pkg));
    }

    /**
     * Returns true if there are any non-default non-test users.
     *
     * <p>This excludes the system and main user(s) as those users are created by default.
     */
    private boolean hasNonTestDefaultUsers() {
        UserManagerInternal userManager = mInjector.getUserManagerInternal();
        // Headless system user mode has two default users: system and main/primary users.
        int numOfDefaultUsers = userManager.isHeadlessSystemUserMode() ? 2 : 1;
        List<UserInfo> users = userManager.getUsers(true);
        return users.stream().filter(user -> !user.isForTesting()).count() > numOfDefaultUsers;
    }

    @Override
    public void onShellCommand(
            @Nullable FileDescriptor in,
            @Nullable FileDescriptor out,
            @Nullable FileDescriptor err,
            @NonNull String[] args,
            @Nullable ShellCallback callback,
            @NonNull ResultReceiver resultReceiver)
            throws RemoteException {
        new SupervisionServiceShellCommand(this)
                .exec(this, in, out, err, args, callback, resultReceiver);
    }

    @Override
    protected void dump(
            @NonNull FileDescriptor fd, @NonNull PrintWriter printWriter, @Nullable String[] args) {
        if (!DumpUtils.checkDumpPermission(mInjector.context, SupervisionLog.TAG, printWriter)) {
            return;
        }

        try (var pw = new IndentingPrintWriter(printWriter, "  ")) {
            pw.println("SupervisionService state:");
            pw.increaseIndent();

            pw.println(
                    "bypassingRoleQualification: " + mAllowBypassingSupervisionRoleQualification);
            pw.println();

            List<UserInfo> users = mInjector.getUserManagerInternal().getUsers(false);
            synchronized (getLockObject()) {
                if (Flags.enableSupervisionManagerPolicyApis()) {
                    mSupervisionSettings.dump(pw);
                }
                for (var user : users) {
                    getUserDataLocked(user.id).dump(pw);
                    pw.println();
                }
            }
        }
    }

    /**
     * Upgrades supervision settings from an older version. This is a one-way migration.
     *
     * @return {@code true} if the upgrade was successful.
     */
    private boolean doUpgrade(int fromVersion, int toVersion) {
        // Perform upgrade without holding the lock
        if (!mInjector.areAllRequiredServicesAvailable()) {
            Slogf.e(
                    SupervisionLog.TAG,
                    "Cannot perform upgrade, required services are not available.");
            return false;
        }

        final Context context = mInjector.context;
        return new SupervisionPolicyMigrator(
                        context, mInjector.getUserManagerInternal(), mInjector.getDpmInternal())
                .upgrade(fromVersion, toVersion);
    }

    /**
     * Performs data migration if necessary and completes boot-time initialization.
     *
     * <p>On boot, this checks the stored settings version and runs a data migration if it's out of
     * date. It then registers listeners needed for supervision functionality.
     */
    private void onBootCompleted() {
        final int fromVersion;
        final int toVersion = SupervisionSettings.VERSION;

        synchronized (getLockObject()) {
            fromVersion = mSupervisionSettings.getVersion();
        }

        if (fromVersion < toVersion) {
            // The lock is released before calling doUpgrade and re-acquired after. This is a
            // non-obvious but important pattern to avoid holding locks for long-running
            // operations.
            final boolean success = doUpgrade(fromVersion, toVersion);
            if (success) {
                synchronized (getLockObject()) {
                    mSupervisionSettings.setVersion(toVersion);
                    mSupervisionSettings.saveUserData();
                }
            }
        }

        mPackageMonitor.register(
                mInjector.context,
                mServiceThreadHandler.getLooper(),
                UserHandle.ALL,
                /* externalStorage= */ false);
    }

    private Object getLockObject() {
        return mLockDoNoUseDirectly;
    }

    @NonNull
    @GuardedBy("getLockObject()")
    SupervisionUserData getUserDataLocked(@UserIdInt int userId) {
        return mSupervisionSettings.getUserData(userId);
    }

    /**
     * Sets supervision as enabled or disabled for the given user and, in case supervision is being
     * enabled, the package of the active supervision app.
     */
    private void setSupervisionEnabledForUserInternal(
            @UserIdInt int userId, boolean enabled, @Nullable String supervisionAppPackage) {
        synchronized (getLockObject()) {
            SupervisionUserData data = getUserDataLocked(userId);
            data.supervisionEnabled = enabled;
            data.supervisionAppPackage = enabled ? supervisionAppPackage : null;
            mSupervisionSettings.saveUserData();
        }
        if (enabled) {
            onSupervisionEnabled(userId);
        } else {
            onSupervisionDisabled(userId);
        }
    }

    private void onSupervisionEnabled(@UserIdInt int userId) {
        Binder.withCleanCallingIdentity(
                () -> {
                    updateWebContentFilters(userId, true);
                    dispatchSupervisionEvent(
                            userId, listener -> listener.onSetSupervisionEnabled(userId, true));
                    maybeApplyUserRestrictionsFor(UserHandle.of(userId));
                });
    }

    private void onSupervisionDisabled(@UserIdInt int userId) {
        if (!Flags.removeRoleAfterEventDispatch()) {
            onSupervisionDisabledLegacy(userId);
            return;
        }

        Binder.withCleanCallingIdentity(
                () -> {
                    updateWebContentFilters(userId, false);
                    clearAllDevicePoliciesAndSuspendedPackages(userId);
                    dispatchSupervisionEvent(
                            userId,
                            new SupervisionAppEvent(
                                    listener -> listener.onSetSupervisionEnabled(userId, false),
                                    packageName -> removeSupervisionRoleHolder(userId, packageName)
                            ));
                    AppBindingService abs = mInjector.getAppBindingService();
                    if (abs != null) {
                        abs.unbindAndRemoveInvalidConnections(
                                userId, SupervisionAppServiceFinder.class);
                    }
                    clearAllPolicies(userId);
                });
    }

    private void onSupervisionDisabledLegacy(@UserIdInt int userId) {
        Binder.withCleanCallingIdentity(
                () -> {
                    updateWebContentFilters(userId, false);
                    dispatchSupervisionEvent(
                            userId, listener -> listener.onSetSupervisionEnabled(userId, false));
                    AppBindingService abs = mInjector.getAppBindingService();
                    if (abs != null) {
                        abs.unbindAndRemoveInvalidConnections(
                                userId, SupervisionAppServiceFinder.class);
                    }
                    clearAllDevicePoliciesAndSuspendedPackages(userId);
                    clearAllPolicies(userId);
                });
    }

    private void executeOnServiceThread(Runnable runnable) {
        mServiceThreadHandler.post(runnable);
    }

    private void dispatchSupervisionEvent(
            @UserIdInt int userId,
            @NonNull RemoteExceptionIgnoringConsumer<ISupervisionListener> action) {
        dispatchSupervisionEvent(userId, new SupervisionAppEvent(action, null));
    }

    private void dispatchSupervisionEvent(@UserIdInt int userId, SupervisionAppEvent action) {
        dispatchSupervisionAppServiceEvent(userId, action);

        ArrayList<ISupervisionListener> listeners = new ArrayList<>();

        synchronized (getLockObject()) {
            mSupervisionListeners.forEach(
                    (binder, record) -> {
                        if (record.userId == userId || record.userId == UserHandle.USER_ALL) {
                            listeners.add(record.listener);
                        }
                    });
        }

        listeners.forEach(action.event);
    }

    private void dispatchSupervisionAppServiceEvent(@UserIdInt int userId,
            SupervisionAppEvent action) {
        AppBindingService abs = mInjector.getAppBindingService();
        if (abs == null) {
            Slogf.e(SupervisionLog.TAG, "AppBindingService is not available.");
            return;
        }

        abs.dispatchAppServiceEvent(
                SupervisionAppServiceFinder.class,
                userId,
                connection -> onAppServiceConnection(connection, action));
    }

    private void onAppServiceConnection(AppServiceConnection connection,
            SupervisionAppEvent action) {
        if (connection == null) {
            if (DEBUG) {
                Slogf.i(SupervisionLog.TAG, "AppService connection is null.");
            }
            return;
        }

        ISupervisionListener binder =
                (ISupervisionListener) connection.getServiceBinder();
        String target = connection.getPackageName();
        if (binder == null) {
            if (DEBUG) {
                Slogf.i(
                        SupervisionLog.TAG,
                        "Failed to connect to SupervisionAppService in %s",
                        target);
            }
        } else {
            if (DEBUG) {
                Slogf.i(
                        SupervisionLog.TAG,
                        "Connected to SupervisionAppService in %s",
                        target);
            }
            action.event.accept(binder);
        }
        if (action.onEventComplete != null) {
            action.onEventComplete.accept(target);
        }
    }

    private void clearAllDevicePoliciesAndSuspendedPackages(@UserIdInt int userId) {
        if (!Flags.enableRemovePoliciesOnSupervisionDisable()) {
            return;
        }

        enforcePermission(MANAGE_ROLE_HOLDERS);
        UserHandle user = UserHandle.of(userId);
        List<String> supervisionPackages = mInjector.getRoleHoldersAsUser(ROLE_SUPERVISION, user);
        List<String> systemSupervisionPackage =
                mInjector.getRoleHoldersAsUser(ROLE_SYSTEM_SUPERVISION, user);

        Set<String> allSupervisionPackages = new HashSet<>(supervisionPackages);
        allSupervisionPackages.addAll(systemSupervisionPackage);

        clearSuspendedPackagesFor(userId, allSupervisionPackages);
        if (!Flags.removeRoleAfterEventDispatch()) {
            removeSupervisionRoleHolders(user, supervisionPackages);
        }

        DevicePolicyManagerInternal dpmi = mInjector.getDpmInternal();
        if (dpmi != null) {
            // Ideally all policy removals would be done atomically in a single call, but there
            // isn't a good way to handle that right now so they will be done separately.
            // It is currently safe to separate them because no restrictions are set by the
            // system entity when supervision role holders are present anyway.
            dpmi.removePoliciesForAdmins(userId, new ArrayList<>(allSupervisionPackages));
            // We're only setting local policies for now, but if we ever were to add a global policy
            // we should also clear that here, if there are no longer any users with supervision
            // enabled.
            dpmi.removeLocalPoliciesForSystemEntities(userId, SYSTEM_ENTITIES);
        }
    }

    private void clearSuspendedPackagesFor(int userId, Collection<String> supervisionPackages) {
        PackageManagerInternal pmi = mInjector.getPackageManagerInternal();
        if (pmi != null) {
            for (String packageName : supervisionPackages) {
                pmi.unsuspendForSuspendingPackage(packageName, userId, userId);
            }
        }
    }

    private void removeSupervisionRoleHolder(@UserIdInt int userId, String packageName) {
        mInjector.removeRoleHoldersAsUser(ROLE_SUPERVISION, packageName,
                UserHandle.of(userId));
    }

    private void removeSupervisionRoleHolders(UserHandle user, List<String> supervisionPackages) {
        for (String packageName : supervisionPackages) {
            mInjector.removeRoleHoldersAsUser(ROLE_SUPERVISION, packageName, user);
        }
    }

    private void maybeApplyUserRestrictions() {
        List<UserInfo> users =
                mInjector.getUserManagerInternal().getUsers(/* excludeDying= */ false);

        for (var user : users) {
            maybeApplyUserRestrictionsFor(user.getUserHandle());
        }
    }

    private void maybeApplyUserRestrictionsFor(@NonNull UserHandle user) {
        DevicePolicyManagerInternal dpmi = mInjector.getDpmInternal();
        if (dpmi != null) {
            boolean enabled = shouldApplyFactoryResetRestriction(user);
            dpmi.setUserRestrictionForUser(
                    SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                    UserManager.DISALLOW_FACTORY_RESET,
                    enabled,
                    user.getIdentifier());
        }
    }

    private boolean shouldApplyFactoryResetRestriction(@NonNull UserHandle user) {
        List<String> supervisionRoleHolders =
                mInjector.getRoleHoldersAsUser(ROLE_SUPERVISION, user);
        @UserIdInt int userId = user.getIdentifier();

        synchronized (getLockObject()) {
            // If there are no Supervision role holders to otherwise enforce restrictions, set a
            // factory reset restriction by default when supervision is enabled and recovery info is
            // set.
            SupervisionRecoveryInfo recoveryInfo = mSupervisionSettings.getRecoveryInfo();
            return supervisionRoleHolders.isEmpty()
                    && getUserDataLocked(userId).supervisionEnabled
                    && recoveryInfo != null
                    && recoveryInfo.getState() == SupervisionRecoveryInfo.STATE_VERIFIED;
        }
    }

    /**
     * Updates Web Content Filters when supervision status is updated.
     *
     * <p>Only change the content filter value if it is not in sync with the supervision state.
     * Disable the filter when disabling supervision and re-set to original value when re-enabling
     * supervision. (If the filter is already enabled when enabling supervision, do not disable it).
     */
    private void updateWebContentFilters(@UserIdInt int userId, boolean enabled) {
        updateContentFilterSetting(userId, enabled, BROWSER_CONTENT_FILTERS_ENABLED);
        updateContentFilterSetting(userId, enabled, SEARCH_CONTENT_FILTERS_ENABLED);
    }

    private void updateContentFilterSetting(@UserIdInt int userId, boolean enabled, String key) {
        try {
            final ContentResolver contentResolver = mInjector.context.getContentResolver();
            if (Flags.enableSupervisionSettingsUiUpdates()) {
                if (!enabled) {
                    Settings.Secure.putIntForUser(contentResolver, key, 0, userId);
                }
            } else {
                final int value = Settings.Secure.getIntForUser(contentResolver, key, userId);
                if (!enabled || value != 1) {
                    Settings.Secure.putIntForUser(contentResolver, key, value * -1, userId);
                }
            }
        } catch (Settings.SettingNotFoundException ignored) {
            // Ignore the exception and do not change the value as no value has been set.
        }
    }

    /**
     * Ensures that supervision is enabled when the supervision app is the profile owner.
     *
     * <p>The state syncing with the DevicePolicyManager can only enable supervision and never
     * disable. Supervision can only be disabled explicitly via calls to the {@link
     * #setSupervisionEnabledForUser} method.
     */
    private void syncStateWithDevicePolicyManager(@UserIdInt int userId) {
        final DevicePolicyManagerInternal dpmInternal = mInjector.getDpmInternal();
        final ComponentName po =
                dpmInternal != null ? dpmInternal.getProfileOwnerAsUser(userId) : null;

        if (po != null && po.getPackageName().equals(getSystemSupervisionPackage())) {
            setSupervisionEnabledForUserInternal(userId, true, getSystemSupervisionPackage());
        } else if (po != null && po.equals(getSupervisionProfileOwnerComponent())) {
            // TODO(b/392071637): Consider not enabling supervision in case profile owner is given
            // to the legacy supervision profile owner component.
            setSupervisionEnabledForUserInternal(userId, true, po.getPackageName());
        }
    }

    /**
     * Returns the {@link ComponentName} of the supervision profile owner component.
     *
     * <p>This component is used to give GMS Kids Module permission to supervise the device and may
     * still be active during the transition to the {@code SYSTEM_SUPERVISION} role.
     */
    private ComponentName getSupervisionProfileOwnerComponent() {
        return ComponentName.unflattenFromString(
                mInjector
                        .context
                        .getResources()
                        .getString(R.string.config_defaultSupervisionProfileOwnerComponent));
    }

    /** Returns the package assigned to the {@code SYSTEM_SUPERVISION} role. */
    private String getSystemSupervisionPackage() {
        return mInjector.context.getResources().getString(R.string.config_systemSupervision);
    }

    /** Enforces that the caller has the given permission. */
    private void enforcePermission(String permission) {
        checkCallAuthorization(hasCallingPermission(permission));
    }

    /** Enforces that the caller has at least one of the given permission. */
    private void enforceAnyPermission(String... permissions) {
        boolean authorized = false;
        for (String permission : permissions) {
            if (hasCallingPermission(permission)) {
                authorized = true;
                break;
            }
        }
        checkCallAuthorization(authorized);
    }

    /**
     * Enforces that the caller can set supervision enabled state.
     *
     * <p>This is restricted to the callers with the root, shell, or system uid or callers with the
     * BYPASS_ROLE_QUALIFICATION permission. This permission is only granted to the SYSTEM_SHELL
     * role holder.
     */
    private void enforceCallerCanSetSupervisionEnabled() {
        checkCallAuthorization(isCallerSystem() || hasCallingPermission(BYPASS_ROLE_QUALIFICATION));
    }

    private boolean hasCallingPermission(String permission) {
        return mInjector.context.checkCallingOrSelfPermission(permission) == PERMISSION_GRANTED;
    }

    private boolean isCallerSystem() {
        return UserHandle.isSameApp(mInjector.getCallingUid(), Process.SYSTEM_UID);
    }

    private void enforceCallerCanSetPolicy() {
        checkCallAuthorization(isCallerSystem() || hasCallingPermission(MANAGE_SUPERVISION));
    }

    private void enforceCallerCanGetPolicies() {
        checkCallAuthorization(isCallerSystem() || hasCallingPermission(MANAGE_SUPERVISION));
    }

    /**
     * Updates the cache of supervision role holders for a given user and returns the ones that were
     * removed.
     *
     * @param userId The ID of the user for whom to update the role holders.
     * @return A list of the supervision role holders that were removed.
     */
    private List<String> updateSupervisionRoleHolders(@UserIdInt int userId) {
        List<String> allSupervisionRoleHolders =
                new ArrayList<String>(
                        mInjector.getRoleHoldersAsUser(
                                ROLE_SYSTEM_SUPERVISION, UserHandle.of(userId)));

        List<String> supervisionRoleHolders =
                new ArrayList<String>(
                        mInjector.getRoleHoldersAsUser(ROLE_SUPERVISION, UserHandle.of(userId)));
        allSupervisionRoleHolders.addAll(supervisionRoleHolders);

        synchronized (getLockObject()) {
            SupervisionUserData data = getUserDataLocked(userId);
            List<String> removedRoleHolders = new ArrayList<>(data.supervisionRoleHolders);
            removedRoleHolders.removeAll(allSupervisionRoleHolders);
            data.supervisionRoleHolders.clear();
            data.supervisionRoleHolders.addAll(allSupervisionRoleHolders);
            if (Flags.verifySupervisionRoleHoldersBeforeDestroyingEscrowToken()) {
                data.escrowTokenRequired = !supervisionRoleHolders.isEmpty();
            }
            mSupervisionSettings.saveUserData();
            return removedRoleHolders;
        }
    }

    /** Provides local services in a lazy manner. */
    static class Injector {
        public Context context;

        private AppBindingService mAppBindingService;
        private DevicePolicyManagerInternal mDpmInternal;
        private KeyguardManager mKeyguardManager;
        private PackageManager mPackageManager;
        private PackageManagerInternal mPackageManagerInternal;
        private RoleManager mRoleManager;
        private UserManagerInternal mUserManagerInternal;
        private ServiceThread mServiceThread;
        private Handler mServiceThreadHandler;
        private NotificationManager mNotificationManager;

        Injector(Context context) {
            this.context = context;
            mRoleManager = Objects.requireNonNull(context.getSystemService(RoleManager.class));
        }

        @Nullable
        AppBindingService getAppBindingService() {
            if (mAppBindingService == null) {
                mAppBindingService = LocalServices.getService(AppBindingService.class);
            }
            return mAppBindingService;
        }

        @Nullable
        DevicePolicyManagerInternal getDpmInternal() {
            if (mDpmInternal == null) {
                mDpmInternal = LocalServices.getService(DevicePolicyManagerInternal.class);
            }
            return mDpmInternal;
        }

        KeyguardManager getKeyguardManager() {
            if (mKeyguardManager == null) {
                mKeyguardManager = context.getSystemService(KeyguardManager.class);
            }
            return mKeyguardManager;
        }

        PackageManager getPackageManager() {
            if (mPackageManager == null) {
                mPackageManager = context.getPackageManager();
            }
            return mPackageManager;
        }

        UserManagerInternal getUserManagerInternal() {
            if (mUserManagerInternal == null) {
                mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
            }
            return mUserManagerInternal;
        }

        PackageManagerInternal getPackageManagerInternal() {
            if (mPackageManagerInternal == null) {
                mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
            }
            return mPackageManagerInternal;
        }

        NotificationManager getNotificationManager() {
            if (mNotificationManager == null) {
                mNotificationManager = context.getSystemService(NotificationManager.class);
            }
            return mNotificationManager;
        }

        void addOnRoleHoldersChangedListenerAsUser(
                @CallbackExecutor @NonNull Executor executor,
                @NonNull OnRoleHoldersChangedListener listener,
                @NonNull UserHandle user) {
            mRoleManager.addOnRoleHoldersChangedListenerAsUser(executor, listener, user);
        }

        @NonNull
        List<String> getRoleHoldersAsUser(String roleName, UserHandle user) {
            return mRoleManager.getRoleHoldersAsUser(roleName, user);
        }

        void removeRoleHoldersAsUser(String roleName, String packageName, UserHandle user) {
            mRoleManager.removeRoleHolderAsUser(
                    roleName,
                    packageName,
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                    user,
                    context.getMainExecutor(),
                    success -> {
                        if (!success) {
                            Slogf.e(
                                    SupervisionLog.TAG,
                                    "Failed to remove role %s for %s",
                                    packageName,
                                    roleName);
                        }
                    });
        }

        @NonNull
        ServiceThread getServiceThread() {
            if (mServiceThread == null) {
                mServiceThread =
                        new ServiceThread(
                                SupervisionLog.TAG, Process.THREAD_PRIORITY_BACKGROUND, true);
                mServiceThread.start();
            }
            return mServiceThread;
        }

        /** Returns the handler for the background service thread. */
        @NonNull
        Handler getServiceThreadHandler() {
            if (mServiceThreadHandler == null) {
                mServiceThreadHandler = new Handler(getServiceThread().getLooper());
            }
            return mServiceThreadHandler;
        }

        // TODO: b/458276188 - potentially get rid of this if we can use a robolectric shadow to
        //  set the calling uid instead.
        /** Provides a way to override the calling uid for testing purposes. */
        int getCallingUid() {
            return Binder.getCallingUid();
        }

        /**
         * Checks if all services required for supervision are available.
         *
         * <p>The service needs to interact with user data, roles, and device policies to clear
         * legacy settings.
         *
         * <p>This is used to avoid a crash loop in case of a crash during system server
         * initialization.
         */
        boolean areAllRequiredServicesAvailable() {
            if (getDpmInternal() == null
                    || getUserManagerInternal() == null
                    || context.getSystemService(RoleManager.class) == null
                    || context.getSystemService(DevicePolicyManager.class) == null) {
                Slogf.e(SupervisionLog.TAG, "Required services are not available.");
                return false;
            }
            return true;
        }
    }

    final class SupervisionPackageMonitor extends PackageMonitor {
        @Override
        public void onPackageAppeared(String packageName, int reason) {
            handlePackageHiddenStateChange(packageName);
        }

        @Override
        public void onPackageDisappeared(String packageName, int reason) {
            handlePackageHiddenStateChange(packageName);
        }

        private void handlePackageHiddenStateChange(String packageName) {
            final int userId = getChangingUserId();
            final boolean isHidden =
                    mInjector
                            .getPackageManager()
                            .getApplicationHiddenSettingAsUser(packageName, UserHandle.of(userId));

            boolean shouldPostNotification = false;
            synchronized (getLockObject()) {
                final SupervisionUserData data = getUserDataLocked(userId);
                final PolicyKey policyKey =
                        PolicyKey.builder()
                                .setType(Policy.PACKAGE_POLICY_IDENTIFIER)
                                .setPackageName(packageName)
                                .build();
                final PolicyData policyData = data.policies.get(policyKey);

                if (policyData == null) {
                    return;
                }

                if (policyData.policy instanceof PackageUsagePolicy pp) {
                    boolean shouldBeHidden = pp.getType() == PackageUsagePolicy.TYPE_BLOCKED;
                    if (policyData.hasPendingNotification && shouldBeHidden == isHidden) {
                        shouldPostNotification = true;
                        policyData.hasPendingNotification = false;
                    }
                }
            }

            // Post the notification if needed, after releasing the lock.
            if (shouldPostNotification) {
                postApplicationHiddenNotification(userId, packageName, isHidden);
            }
        }
    }

    /** Publishes local and binder services and allows the service to act during initialization. */
    public static class Lifecycle extends SystemService {
        private final SupervisionService mSupervisionService;

        public Lifecycle(@NonNull Context context) {
            super(context);
            mSupervisionService = new SupervisionService(context);
        }

        @VisibleForTesting
        Lifecycle(Context context, SupervisionService supervisionService) {
            super(context);
            mSupervisionService = supervisionService;
        }

        @Override
        public void onStart() {
            publishLocalService(SupervisionManagerInternal.class, mSupervisionService.mInternal);
            publishBinderService(Context.SUPERVISION_SERVICE, mSupervisionService);
            if (Flags.enableSyncWithDpm()) {
                registerProfileOwnerListener();
            }
        }

        @Override
        public void onBootPhase(int phase) {
            switch (phase) {
                case SystemService.PHASE_SYSTEM_SERVICES_READY -> onSystemServicesReady();
                case SystemService.PHASE_BOOT_COMPLETED -> onBootCompleted();
            }
        }

        private void onSystemServicesReady() {
            mSupervisionService.executeOnServiceThread(
                    SupervisionManager::invalidateGetPoliciesCache);
        }

        private void onBootCompleted() {
            if (Flags.enableSupervisionSettingsUiUpdates()) {
                mSupervisionService.registerStatsPullAtomCallback();
            }
            if (Flags.enableSupervisionManagerPolicyApis()) {
                mSupervisionService.onBootCompleted();
            }
        }

        @VisibleForTesting
        @SuppressLint("MissingPermission")
        void registerProfileOwnerListener() {
            IntentFilter poIntentFilter = new IntentFilter();
            poIntentFilter.addAction(DevicePolicyManager.ACTION_PROFILE_OWNER_CHANGED);
            poIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            getContext()
                    .registerReceiverForAllUsers(
                            new ProfileOwnerBroadcastReceiver(),
                            poIntentFilter,
                            /* broadcastPermission= */ null,
                            /* scheduler= */ null);
        }

        @Override
        public void onUserStarting(@NonNull TargetUser user) {
            mSupervisionService.executeOnServiceThread(
                    () -> {
                        mSupervisionService.updateSupervisionRoleHolders(user.getUserIdentifier());
                        if (Flags.enableSyncWithDpm() && !user.isPreCreated()) {
                            mSupervisionService.syncStateWithDevicePolicyManager(
                                    user.getUserIdentifier());
                        }
                        mSupervisionService.maybeApplyUserRestrictionsFor(user.getUserHandle());
                    });
        }

        private final class ProfileOwnerBroadcastReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                mSupervisionService.syncStateWithDevicePolicyManager(getSendingUserId());
            }
        }
    }

    /** Implementation of the local service, API used by other services. */
    private final class SupervisionManagerInternalImpl extends SupervisionManagerInternal {
        @Override
        public boolean isActiveSupervisionApp(int uid) {
            int userId = UserHandle.getUserId(uid);
            String supervisionAppPackage = getActiveSupervisionAppPackage(userId);
            if (supervisionAppPackage == null) {
                return false;
            }

            String[] packages = mInjector.getPackageManager().getPackagesForUid(uid);
            if (packages != null) {
                for (var packageName : packages) {
                    if (supervisionAppPackage.equals(packageName)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean isSupervisionEnabledForUser(@UserIdInt int userId) {
            return SupervisionService.this.isSupervisionEnabledForUser(userId);
        }

        @Override
        public void setSupervisionEnabledForUser(@UserIdInt int userId, boolean enabled) {
            SupervisionService.this.setSupervisionEnabledForUser(userId, enabled);
        }

        @Override
        public boolean isSupervisionLockscreenEnabledForUser(@UserIdInt int userId) {
            synchronized (getLockObject()) {
                return getUserDataLocked(userId).supervisionLockScreenEnabled;
            }
        }

        @Override
        public void setSupervisionLockscreenEnabledForUser(
                @UserIdInt int userId, boolean enabled, @Nullable PersistableBundle options) {
            synchronized (getLockObject()) {
                SupervisionUserData data = getUserDataLocked(userId);
                data.supervisionLockScreenEnabled = enabled;
                data.supervisionLockScreenOptions = options;
                mSupervisionSettings.saveUserData();
            }
        }

        @Override
        public boolean isEscrowTokenRequired(@UserIdInt int userId) {
            if (!Flags.verifySupervisionRoleHoldersBeforeDestroyingEscrowToken()) {
                return false;
            }
            synchronized (getLockObject()) {
                return getUserDataLocked(userId).escrowTokenRequired;
            }
        }
    }

    private final class RoleObserver implements OnRoleHoldersChangedListener {
        RoleObserver() {}

        void register() {
            mInjector.addOnRoleHoldersChangedListenerAsUser(
                    mInjector.getServiceThread().getThreadExecutor(), this, UserHandle.ALL);
        }

        @Override
        public void onRoleHoldersChanged(@NonNull String roleName, @NonNull UserHandle user) {
            if (ROLE_SUPERVISION.equals(roleName) || ROLE_SYSTEM_SUPERVISION.equals(roleName)) {
                executeOnServiceThread(
                        () -> {
                            maybeApplyUserRestrictionsFor(user);
                            List<String> removedRoleHolders =
                                    updateSupervisionRoleHolders(user.getIdentifier());
                            clearSuspendedPackagesFor(user.getIdentifier(), removedRoleHolders);
                        });
            }
        }
    }

    /** Deletes user data when the user gets removed. */
    private final class UserLifecycleListener implements UserManagerInternal.UserLifecycleListener {
        @Override
        public void onUserRemoved(UserInfo user) {
            synchronized (getLockObject()) {
                mSupervisionSettings.removeUserData(user.id);
            }
        }
    }

    private final class SupervisionListenerRecord implements DeathRecipient {
        public final ISupervisionListener listener;
        public final int userId;

        SupervisionListenerRecord(@NonNull ISupervisionListener listener, @UserIdInt int userId)
                throws RemoteException {
            this.listener = listener;
            this.userId = userId;
            listener.asBinder().linkToDeath(this, 0);
        }

        public void unlinkToDeath() {
            listener.asBinder().unlinkToDeath(this, 0);
        }

        @Override
        public void binderDied() {
            unregisterSupervisionListener(listener);
        }
    }

    private record SupervisionAppEvent(
            @NonNull RemoteExceptionIgnoringConsumer<ISupervisionListener> event,
            @Nullable RemoteExceptionIgnoringConsumer<String> onEventComplete
    ) {}
}
