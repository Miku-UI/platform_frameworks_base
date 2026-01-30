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
import static android.Manifest.permission.MANAGE_USERS;
import static android.Manifest.permission.QUERY_USERS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Settings.Secure.BROWSER_CONTENT_FILTERS_ENABLED;
import static android.provider.Settings.Secure.SEARCH_CONTENT_FILTERS_ENABLED;

import static com.android.internal.util.Preconditions.checkCallAuthorization;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManagerInternal;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.app.supervision.ISupervisionListener;
import android.app.supervision.ISupervisionManager;
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
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Bundle;
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
import com.android.internal.util.DumpUtils;
import com.android.internal.util.FunctionalUtils.RemoteExceptionIgnoringConsumer;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.LocalServices;
import com.android.server.ServiceThread;
import com.android.server.SystemService;
import com.android.server.appbinding.AppBindingService;
import com.android.server.appbinding.AppServiceConnection;
import com.android.server.appbinding.finders.SupervisionAppServiceFinder;
import com.android.server.pm.UserManagerInternal;
import com.android.server.utils.Slogf;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

    @VisibleForTesting
    static final List<String> SYSTEM_ENTITIES =
            List.of(SupervisionManager.SUPERVISION_SYSTEM_ENTITY);

    // TODO(b/362756788): Does this need to be a LockGuard lock?
    private final Object mLockDoNoUseDirectly = new Object();

    @GuardedBy("getLockObject()")
    private final SparseArray<SupervisionUserData> mUserData = new SparseArray<>();

    private final Injector mInjector;
    private final RoleObserver mRoleObserver;
    final SupervisionManagerInternal mInternal = new SupervisionManagerInternalImpl();

    @GuardedBy("getLockObject()")
    final ArrayMap<IBinder, SupervisionListenerRecord> mSupervisionListeners = new ArrayMap<>();

    // We need to create a new background thread here because the AppBindingService uses the
    // BackgroundThread for its connection callbacks. Using the same thread would block while
    // waiting for those callbacks, preventing the new connections from being perceived.
    final ServiceThread mServiceThread;
    public static final boolean DEBUG = false; // DO NOT SUBMIT WITH TRUE

    @GuardedBy("getLockObject()")
    final SupervisionSettings mSupervisionSettings = SupervisionSettings.getInstance();

    public SupervisionService(Context context) {
        this(new Injector(context.createAttributionContext(SupervisionLog.TAG)));
    }

    @VisibleForTesting
    SupervisionService(Injector injector) {
        mInjector = injector;
        mServiceThread = injector.getServiceThread();
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

    // TODO(b/444411638): Remove this after enable_app_service_connection_callback rollout
    private List<AppServiceConnection> getSupervisionAppServiceConnections(@UserIdInt int userId) {
        AppBindingService abs = mInjector.getAppBindingService();
        return abs != null
                ? abs.getAppServiceConnectionsBlocking(SupervisionAppServiceFinder.class, userId)
                : new ArrayList<>();
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
        if (!isSupervisionEnabledForUser(userId) || !hasSupervisionCredentials()) {
            return null;
        }
        final Intent intent = new Intent(ACTION_CONFIRM_SUPERVISION_CREDENTIALS);
        // explicitly set the package for security
        intent.setPackage("com.android.settings");

        return intent;
    }

    /** Set the Supervision Recovery Info. */
    @Override
    public void setSupervisionRecoveryInfo(SupervisionRecoveryInfo recoveryInfo) {
        if (!Flags.persistentSupervisionSettings()) {
            SupervisionRecoveryInfoStorage.getInstance(mInjector.context)
                    .saveRecoveryInfo(recoveryInfo);
            return;
        }

        synchronized (getLockObject()) {
            mSupervisionSettings.saveRecoveryInfo(recoveryInfo);
        }

        maybeApplyUserRestrictions();
    }

    /** Returns the Supervision Recovery Info or null if recovery is not set. */
    @Override
    public SupervisionRecoveryInfo getSupervisionRecoveryInfo() {
        if (Flags.persistentSupervisionSettings()) {
            return mSupervisionSettings.getRecoveryInfo();
        }
        return SupervisionRecoveryInfoStorage.getInstance(mInjector.context).loadRecoveryInfo();
    }

    @Override
    public boolean shouldAllowBypassingSupervisionRoleQualification() {
        enforcePermission(MANAGE_ROLE_HOLDERS);

        if (hasNonTestDefaultUsers()) {
            return false;
        }

        synchronized (getLockObject()) {
            if (Flags.persistentSupervisionSettings()) {
                if (mSupervisionSettings.anySupervisedUser()) {
                    return false;
                }
            } else {
                for (int i = 0; i < mUserData.size(); i++) {
                    if (mUserData.valueAt(i).supervisionEnabled) {
                        return false;
                    }
                }
            }
        }

        return true;
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
     * Returns true if there are any non-default non-test users.
     *
     * <p>This excludes the system and main user(s) as those users are created by default.
     */
    private boolean hasNonTestDefaultUsers() {
        List<UserInfo> users = mInjector.getUserManagerInternal().getUsers(true);
        for (var user : users) {
            if (!user.isForTesting() && !user.isMain() && !isSystemUser(user)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSystemUser(UserInfo userInfo) {
        return (userInfo.flags & UserInfo.FLAG_SYSTEM) == UserInfo.FLAG_SYSTEM;
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

            List<UserInfo> users = mInjector.getUserManagerInternal().getUsers(false);
            synchronized (getLockObject()) {
                for (var user : users) {
                    getUserDataLocked(user.id).dump(pw);
                    pw.println();
                }
            }
        }
    }

    private Object getLockObject() {
        return mLockDoNoUseDirectly;
    }

    @NonNull
    @GuardedBy("getLockObject()")
    SupervisionUserData getUserDataLocked(@UserIdInt int userId) {
        if (Flags.persistentSupervisionSettings()) {
            return mSupervisionSettings.getUserData(userId);
        } else {
            SupervisionUserData data = mUserData.get(userId);
            if (data == null) {
                // TODO(b/362790738): Do not create user data for nonexistent users.
                data = new SupervisionUserData(userId);
                mUserData.append(userId, data);
            }
            return data;
        }
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
            if (Flags.persistentSupervisionSettings()) {
                mSupervisionSettings.saveUserData();
            }
        }
        executeOnSupervisionEnabled(
                () -> {
                    updateWebContentFilters(userId, enabled);
                    dispatchSupervisionEvent(
                            userId,
                            listener -> listener.onSetSupervisionEnabled(userId, enabled));
                    if (!enabled) {
                        clearAllDevicePoliciesAndSuspendedPackages(userId);
                    } else {
                        maybeApplyUserRestrictionsFor(UserHandle.of(userId));
                    }
                });
    }

    private void executeOnSupervisionEnabled(Runnable runnable) {
        if (Flags.enableAppServiceConnectionCallback()) {
            Binder.withCleanCallingIdentity(runnable::run);
        } else {
            mServiceThread.getThreadExecutor().execute(runnable);
        }
    }

    @NonNull
    // TODO(b/444411638): Remove this after enable_app_service_connection_callback rollout
    private List<ISupervisionListener> getSupervisionAppServiceListeners(
            @UserIdInt int userId,
            @NonNull RemoteExceptionIgnoringConsumer<ISupervisionListener> action) {
        ArrayList<ISupervisionListener> listeners = new ArrayList<>();
        if (!Flags.enableSupervisionAppService() || Flags.enableAppServiceConnectionCallback()) {
            return listeners;
        }

        List<AppServiceConnection> connections = getSupervisionAppServiceConnections(userId);
        for (AppServiceConnection conn : connections) {
            String targetPackage = conn.getPackageName();
            ISupervisionListener binder = null;
            try {
                binder = (ISupervisionListener) conn.getServiceBinder();
            } catch (Exception e) {
                Slogf.e(SupervisionLog.TAG, "Error getting binder: " + e.getMessage(), e);
            }

            if (binder == null) {
                Slogf.d(
                        SupervisionLog.TAG,
                        "Failed to bind to SupervisionAppService for %s",
                        targetPackage);
                continue;
            }

            listeners.add(binder);
        }

        return listeners;
    }

    private void dispatchSupervisionEvent(
            @UserIdInt int userId,
            @NonNull RemoteExceptionIgnoringConsumer<ISupervisionListener> action) {
        if (Flags.enableAppServiceConnectionCallback()) {
            dispatchSupervisionAppServiceEvent(userId, action);
        }
        // Add SupervisionAppServices listeners before the platform listeners.
        ArrayList<ISupervisionListener> listeners =
                new ArrayList<>(getSupervisionAppServiceListeners(userId, action));

        synchronized (getLockObject()) {
            mSupervisionListeners.forEach(
                    (binder, record) -> {
                        if (record.userId == userId || record.userId == UserHandle.USER_ALL) {
                            listeners.add(record.listener);
                        }
                    });
        }

        listeners.forEach(action);
    }

    private void dispatchSupervisionAppServiceEvent(
            @UserIdInt int userId,
            @NonNull RemoteExceptionIgnoringConsumer<ISupervisionListener> action) {
        if (!Flags.enableSupervisionAppService()) {
            return;
        }
        AppBindingService abs = mInjector.getAppBindingService();
        if (abs == null) {
            Slogf.e(SupervisionLog.TAG, "AppBindingService is not available.");
            return;
        }

        abs.dispatchAppServiceEvent(SupervisionAppServiceFinder.class, userId, connection -> {
            ISupervisionListener binder = (ISupervisionListener) connection.getServiceBinder();
            String target = connection.getPackageName();
            if (binder == null) {
                if (DEBUG) {
                    Slogf.i(SupervisionLog.TAG,
                            "Failed to connect to SupervisionAppService in %s", target);
                }
            } else {
                if (DEBUG) {
                    Slogf.i(SupervisionLog.TAG,
                            "Connected to SupervisionAppService in %s", target);
                }
                action.accept(binder);
            }
        });
    }

    private void clearAllDevicePoliciesAndSuspendedPackages(@UserIdInt int userId) {
        if (!Flags.enableRemovePoliciesOnSupervisionDisable()) {
            return;
        }

        enforcePermission(MANAGE_ROLE_HOLDERS);
        List<String> roles =
                Arrays.asList(RoleManager.ROLE_SYSTEM_SUPERVISION, RoleManager.ROLE_SUPERVISION);
        List<String> supervisionPackages = new ArrayList<>();
        for (String role : roles) {
            List<String> supervisionPackagesPerRole =
                    mInjector.getRoleHoldersAsUser(role, UserHandle.of(userId));
            supervisionPackages.addAll(supervisionPackagesPerRole);
            clearSuspendedPackagesFor(userId, supervisionPackagesPerRole, role);
        }

        DevicePolicyManagerInternal dpmi = mInjector.getDpmInternal();
        if (dpmi != null) {
            // Ideally all policy removals would be done atomically in a single call, but there
            // isn't a good way to handle that right now so they will be done separately.
            // It is currently safe to separate them because no restrictions are set by the
            // system entity when supervision role holders are present anyway.
            dpmi.removePoliciesForAdmins(userId, supervisionPackages);
            // We're only setting local policies for now, but if we ever were to add a global policy
            // we should also clear that here, if there are no longer any users with supervision
            // enabled.
            dpmi.removeLocalPoliciesForSystemEntities(userId, SYSTEM_ENTITIES);
        }
    }

    private void clearSuspendedPackagesFor(int userId, List<String> supervisionPackages,
            @Nullable String role) {
        PackageManagerInternal pmi = mInjector.getPackageManagerInternal();
        for (String supervisionPackage: supervisionPackages) {
            if (pmi != null) {
                pmi.unsuspendForSuspendingPackage(supervisionPackage, userId, userId);
            }
            if (RoleManager.ROLE_SUPERVISION.equals(role)) {
                mInjector.removeRoleHoldersAsUser(role, supervisionPackage, UserHandle.of(userId));
            }
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
                mInjector.getRoleHoldersAsUser(RoleManager.ROLE_SUPERVISION, user);
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
            final int value = Settings.Secure.getIntForUser(contentResolver, key, userId);
            if (!enabled || value != 1) {
                Settings.Secure.putIntForUser(contentResolver, key, value * -1, userId);
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
        return UserHandle.isSameApp(Binder.getCallingUid(), Process.SYSTEM_UID);
    }

    private void updateSupervisionRoleHolders(@UserIdInt int userId) {
        List<String> roleHolders =
                mInjector.getRoleHoldersAsUser(RoleManager.ROLE_SUPERVISION, UserHandle.of(userId));

        synchronized (getLockObject()) {
            SupervisionUserData data = getUserDataLocked(userId);
            data.supervisionRoleHolders.clear();
            data.supervisionRoleHolders.addAll(roleHolders);
            if (Flags.persistentSupervisionSettings()) {
                mSupervisionSettings.saveUserData();
            }
        }
    }

    private List<String> getRemovedSupervisionRoleHolders(@UserIdInt int userId) {
        List<String> newRoleHolders =
                mInjector.getRoleHoldersAsUser(RoleManager.ROLE_SUPERVISION, UserHandle.of(userId));

        synchronized (getLockObject()) {
            SupervisionUserData data = getUserDataLocked(userId);
            List<String> removedRoleHolders = new ArrayList<>(data.supervisionRoleHolders);
            removedRoleHolders.removeAll(newRoleHolders);
            data.supervisionRoleHolders.clear();
            data.supervisionRoleHolders.addAll(newRoleHolders);
            if (Flags.persistentSupervisionSettings()) {
                mSupervisionSettings.saveUserData();
            }
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

        @NonNull
        ServiceThread getServiceThread() {
            ServiceThread thread =
                    new ServiceThread(SupervisionLog.TAG, Process.THREAD_PRIORITY_BACKGROUND, true);
            thread.start();
            return thread;
        }

        void removeRoleHoldersAsUser(String roleName, String packageName, UserHandle user) {
            mRoleManager.removeRoleHolderAsUser(
                    roleName,
                    packageName,
                    0,
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
            mSupervisionService.updateSupervisionRoleHolders(user.getUserIdentifier());
            if (Flags.enableSyncWithDpm() && !user.isPreCreated()) {
                mSupervisionService.syncStateWithDevicePolicyManager(user.getUserIdentifier());
            }
            mSupervisionService.maybeApplyUserRestrictionsFor(user.getUserHandle());
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
                if (Flags.persistentSupervisionSettings()) {
                    mSupervisionSettings.saveUserData();
                }
            }
        }
    }

    private final class RoleObserver implements OnRoleHoldersChangedListener {
        RoleObserver() {}

        void register() {
            mInjector.addOnRoleHoldersChangedListenerAsUser(
                    mServiceThread.getThreadExecutor(), this, UserHandle.ALL);
        }

        @Override
        public void onRoleHoldersChanged(@NonNull String roleName, @NonNull UserHandle user) {
            if (RoleManager.ROLE_SUPERVISION.equals(roleName)) {
                maybeApplyUserRestrictionsFor(user);
                List<String> removedRoleHolders =
                        getRemovedSupervisionRoleHolders(user.getIdentifier());
                clearSuspendedPackagesFor(user.getIdentifier(), removedRoleHolders, null);
            }
        }
    }

    /** Deletes user data when the user gets removed. */
    private final class UserLifecycleListener implements UserManagerInternal.UserLifecycleListener {
        @Override
        public void onUserRemoved(UserInfo user) {
            synchronized (getLockObject()) {
                if (Flags.persistentSupervisionSettings()) {
                    mSupervisionSettings.removeUserData(user.id);
                } else {
                    mUserData.remove(user.id);
                }
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
}
