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
package com.android.server.modes;

import static android.Manifest.permission.STATUS_BAR_SERVICE;
import static android.app.modes.ContextualMode.STATE_ACTIVE;
import static android.app.modes.ContextualMode.STATE_INACTIVE;
import static android.app.modes.ContextualMode.STATE_UNKNOWN;
import static android.app.modes.ContextualMode.TYPE_MANUAL_DO_NOT_DISTURB;
import static android.app.modes.ContextualMode.modeStateToString;
import static android.app.modes.ContextualMode.modeTypeToString;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.RemoteCallbackList.FROZEN_CALLEE_POLICY_ENQUEUE_ALL;
import static android.os.RemoteCallbackList.FROZEN_CALLEE_POLICY_ENQUEUE_MOST_RECENT;
import static android.service.notification.ZenModeConfig.MANUAL_RULE_ID;

import android.Manifest;
import android.Manifest.permission;
import android.annotation.EnforcePermission;
import android.annotation.Nullable;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.RequiresNoPermission;
import android.annotation.SuppressLint;
import android.app.ActivityManagerInternal;
import android.app.AutomaticZenRule;
import android.app.modes.ContextualMode;
import android.app.modes.ContextualModeManager;
import android.app.modes.ContextualModesMutation;
import android.app.modes.IContextualModeListener;
import android.app.modes.IContextualModeManager;
import android.app.modes.IContextualModeSyncListener;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Build;
import android.os.IInterface;
import android.os.PermissionEnforcer;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.ArrayMap;
import android.util.IndentingPrintWriter;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.notification.ZenModeHelper;
import com.android.server.pm.UserManagerInternal;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A system service managing contextual modes. E.g. Do not disturb. See {@link
 * ContextualModeManager}.
 */
public final class ContextualModeManagerService extends SystemService {
    private static final String TAG = "CtxModeManager";
    private static final boolean DEBUG = !Build.IS_USER;

    private final Object mLock = new Object();
    private final Executor mMainExecutor;
    private final AtomicBoolean mCacheRefreshPending = new AtomicBoolean();
    private final BinderService mBinderService;
    private final Resources mResources;
    private final ContentResolver mResolver;
    private final PermissionEnforcer mPermissionEnforcer;

    @GuardedBy("mLock")
    private final Map<UserHandle, RemoteCallbackList<IContextualModeListener>> mModeListeners =
            new HashMap<>();

    @GuardedBy("mLock")
    private final Map<UserHandle, RemoteCallbackList<IContextualModeSyncListener>>
            mModeSyncListeners = new HashMap<>();

    @GuardedBy("mLock")
    private final Map<UserHandle, ContentObserver> mObservers = new HashMap<>();

    @GuardedBy("mLock")
    private final Map<UserHandle, Set<ContextualMode>> mCurrentModeStates = new HashMap<>();

    private boolean mModeSyncSupported;
    @Nullable private volatile NotificationManagerInternal mNotificationManagerInternal;
    private UserManagerInternal mUserManagerInternal;
    private ActivityManagerInternal mActivityManagerInternal;

    public ContextualModeManagerService(@NonNull Context context) {
        this(
                context,
                PermissionEnforcer.fromContext(context),
                context.getResources(),
                context.getContentResolver(),
                context.getMainExecutor());
    }

    @VisibleForTesting
    ContextualModeManagerService(
            @NonNull Context context,
            PermissionEnforcer permissionEnforcer,
            Resources resources,
            ContentResolver resolver,
            Executor mainExecutor) {
        super(context);
        mPermissionEnforcer = permissionEnforcer;
        mBinderService = new BinderService(permissionEnforcer);
        mResources = resources;
        mResolver = resolver;
        mMainExecutor = mainExecutor;
    }

    @Override
    public void onStart() {
        mModeSyncSupported =
                mResources.getBoolean(com.android.internal.R.bool.config_supportContextualModeSync)
                        && com.android.crossdevicesync.flags.Flags.startSyncServiceOnBoot();
        mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
        mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
        // We can't get notification manager here since it initializes after contextual mode
        // manager. It will be accessed lazily instead.
        try {
            publishBinderService(Context.CONTEXTUAL_MODE_SERVICE, mBinderService);
        } catch (Exception e) {
            // This should only happen during unit test, so catch it here instead of letting it
            // crash.
            Slog.e(TAG, "Failed to publish binder service!", e);
        }
    }

    @VisibleForTesting
    IContextualModeManager getBinderService() {
        return mBinderService;
    }

    private List<ContextualMode> getModes(UserHandle userHandle) {
        refreshModesCache(userHandle, /* async= */ false);
        synchronized (mCurrentModeStates) {
            if (mCurrentModeStates.containsKey(userHandle)) {
                return List.copyOf(mCurrentModeStates.get(userHandle));
            } else {
                return List.of();
            }
        }
    }

    /**
     * Refresh modes cache to match the NotificationManager's zen mode state. Also fires listeners
     * if changes are detected.
     */
    private void refreshModesCache(UserHandle userHandle, boolean async) {
        boolean refreshPending = mCacheRefreshPending.getAndSet(true);
        if (!async) {
            // Synchronously invalidate cache.
            doRefreshModesCache(userHandle);
            return;
        }
        // Post refreshing only if we haven't done it.
        if (!refreshPending) {
            mMainExecutor.execute(() -> doRefreshModesCache(userHandle));
        }
    }

    private void doRefreshModesCache(UserHandle userHandle) {
        if (!mCacheRefreshPending.getAndSet(false)) {
            // Prevent duplicate refreshing.
            return;
        }
        synchronized (mLock) {
            Set<ContextualMode> set = new HashSet<>();
            NotificationManagerInternal nm = getNotificationManagerInternal();
            if (nm.hasZenModeConfig(userHandle)) {
                set.add(getManualDnd(userHandle));
                for (Entry<String, AutomaticZenRule> entry :
                        nm.getAutomaticZenRules(userHandle).entrySet()) {
                    String id = entry.getKey();
                    AutomaticZenRule rule = entry.getValue();
                    set.add(getAutomaticZenMode(userHandle, id, rule));
                }
            }

            // Apply change to cache and notify delta.
            List<ContextualMode> changedModes = new ArrayList<>();
            List<String> deletedModeIds = new ArrayList<>();
            Map<String, ContextualMode> prevModes;
            if (set.isEmpty()) {
                prevModes = toMap(mCurrentModeStates.remove(userHandle));
            } else {
                prevModes = toMap(mCurrentModeStates.put(userHandle, set));
            }
            for (ContextualMode mode : set) {
                ContextualMode prevMode = prevModes.get(mode.getId());
                if (!mode.equals(prevMode)) {
                    changedModes.add(mode);
                }
            }
            Map<String, ContextualMode> currentModes = toMap(set);
            for (String prevModeId : prevModes.keySet()) {
                if (!currentModes.containsKey(prevModeId)) {
                    deletedModeIds.add(prevModeId);
                }
            }
            RemoteCallbackList<IContextualModeListener> callbackList =
                    mModeListeners.get(userHandle);
            if (!changedModes.isEmpty()) {
                if (DEBUG) {
                    Slog.d(TAG, "Modes changed: user=" + userHandle + ", change=" + changedModes);
                }
                if (callbackList != null) {
                    notifyListenersLocked(callbackList, l -> l.onModesChanged(changedModes));
                }
            }
            if (!deletedModeIds.isEmpty()) {
                if (DEBUG) {
                    Slog.d(
                            TAG,
                            "Modes deleted: user=" + userHandle + ", deleted=" + deletedModeIds);
                }
                if (callbackList != null) {
                    for (String deletedId : deletedModeIds) {
                        notifyListenersLocked(callbackList, l -> l.onModeRemoved(deletedId));
                    }
                }
            }
        }
    }

    private static Map<String, ContextualMode> toMap(@Nullable Collection<ContextualMode> modes) {
        if (modes == null) {
            return Map.of();
        }
        Map<String, ContextualMode> map = new ArrayMap<>(modes.size());
        for (ContextualMode mode : modes) {
            map.put(mode.getId(), mode);
        }
        return map;
    }

    private ContextualMode getManualDnd(UserHandle userHandle) {
        boolean active = getNotificationManagerInternal().isManualZenRuleActive(userHandle);
        return new ContextualMode.Builder(MANUAL_RULE_ID)
                .setType(TYPE_MANUAL_DO_NOT_DISTURB)
                .setState(active ? STATE_ACTIVE : STATE_INACTIVE)
                .build();
    }

    /**
     * Get automatic zen mode from notification manager and convert it into a {@link
     * ContextualMode}. Must pass a valid mode id and a valid {@link AutomaticZenRule}.
     */
    @SuppressLint("WrongConstant")
    private ContextualMode getAutomaticZenMode(
            UserHandle userHandle, String id, AutomaticZenRule rule) {
        boolean active = getNotificationManagerInternal().isAutomaticZenRuleActive(userHandle, id);
        return new ContextualMode.Builder(id)
                .setType(rule.getType())
                .setState(active ? STATE_ACTIVE : STATE_INACTIVE)
                .build();
    }

    @SuppressLint("WrongConstant")
    private void mutateModes(UserHandle userHandle, ContextualModesMutation mutation) {
        if (mutation.getUpdatedModes().isEmpty()) {
            // No change.
            return;
        }
        NotificationManagerInternal nm = getNotificationManagerInternal();
        if (!nm.hasZenModeConfig(userHandle)) {
            throw new IllegalArgumentException("User " + userHandle + " has no mode!");
        }

        // First pass: validate mutation.
        Map<String, AutomaticZenRule> automaticZenRules = null;
        Set<String> ids = new HashSet<>();
        for (ContextualMode change : mutation.getUpdatedModes()) {
            if (!ids.add(change.getId())) {
                throw new IllegalArgumentException(
                        "Illegal mutation: duplicate mode id " + change.getState());
            }
            if (change.getState() == STATE_UNKNOWN) {
                throw new IllegalArgumentException(
                        "Illegal mutation: unsupported new state "
                                + modeStateToString(change.getState()));
            }
            if (change.getType() == TYPE_MANUAL_DO_NOT_DISTURB) {
                continue;
            }
            if (automaticZenRules == null) {
                // Lazily fetch automatic zen rules.
                automaticZenRules = nm.getAutomaticZenRules(userHandle);
            }
            AutomaticZenRule rule = automaticZenRules.get(change.getId());
            if (rule == null) {
                throw new IllegalArgumentException(
                        "Illegal mutation: unknown mode id " + change.getId());
            }
            if (rule.getType() != change.getType()) {
                throw new IllegalArgumentException(
                        "Illegal mutation: expected type "
                                + modeTypeToString(rule.getType())
                                + ", but got "
                                + modeTypeToString(change.getType()));
            }
        }

        // Second pass: apply mutation. Acquire lock to ensure atomicity between batch update and
        // config change callback.
        synchronized (mLock) {
            for (ContextualMode change : mutation.getUpdatedModes()) {
                if (DEBUG) {
                    Slog.d(TAG, "Applying mode state change " + change + " for user " + userHandle);
                }
                boolean isActive = change.getState() == STATE_ACTIVE;
                if (change.getType() == TYPE_MANUAL_DO_NOT_DISTURB) {
                    nm.setManualZenRuleActive(userHandle, isActive);
                } else {
                    nm.setAutomaticZenRuleActive(userHandle, change.getId(), isActive);
                }
            }
        }
    }

    private NotificationManagerInternal getNotificationManagerInternal() {
        if (mNotificationManagerInternal != null) {
            return mNotificationManagerInternal;
        }
        synchronized (mLock) {
            if (mNotificationManagerInternal == null) {
                mNotificationManagerInternal =
                        LocalServices.getService(NotificationManagerInternal.class);
                mNotificationManagerInternal.addZenModeCallback(
                        new ZenModeHelper.Callback() {
                            @Override
                            public void onConfigChanged(UserHandle user) {
                                if (DEBUG) {
                                    Slog.d(TAG, "onConfigChanged: user=" + user);
                                }
                                // Must invalidate cache async to prevent blocking notification
                                // manager and avoid potential deadlock.
                                refreshModesCache(user, /* async= */ true);
                            }
                        });
            }
            return mNotificationManagerInternal;
        }
    }

    private boolean isModeSyncEnabled(UserHandle userHandle) {
        if (!mModeSyncSupported) {
            return false;
        }
        return Settings.Secure.getIntForUser(
                        mResolver,
                        Settings.Secure.CONTEXTUAL_MODE_SYNC_ENABLED,
                        1, // Default enabled
                        userHandle.getIdentifier())
                != 0;
    }

    /** Test-only helper for fake content observer callback. */
    @VisibleForTesting
    void notifyModeSyncEnabledChanged(UserHandle userHandle) {
        synchronized (mLock) {
            ContentObserver observer = mObservers.get(userHandle);
            if (observer == null) {
                return;
            }
            mMainExecutor.execute(() -> observer.onChange(/* selfChange= */ false));
        }
    }

    private void onModeListenerCallbackDied(UserHandle userHandle) {
        synchronized (mLock) {
            RemoteCallbackList<IContextualModeListener> callbackList =
                    mModeListeners.get(userHandle);
            if (callbackList.getRegisteredCallbackCount() == 0) {
                callbackList.kill();
                mModeListeners.remove(userHandle);
            }
        }
    }

    private void onModeSyncListenerCallbackDied(UserHandle userHandle) {
        synchronized (mLock) {
            RemoteCallbackList<IContextualModeSyncListener> callbackList =
                    mModeSyncListeners.get(userHandle);
            if (callbackList.getRegisteredCallbackCount() == 0) {
                callbackList.kill();
                mModeSyncListeners.remove(userHandle);
            }
        }
    }

    /** Binder service for clients to interact with. */
    private class BinderService extends IContextualModeManager.Stub {

        BinderService(PermissionEnforcer permissionEnforcer) {
            super(permissionEnforcer);
        }

        @Override
        @RequiresNoPermission
        public boolean isModeSyncSupported() {
            return mModeSyncSupported;
        }

        @Override
        @RequiresNoPermission
        public boolean isModeSyncEnabled(UserHandle userHandle) {
            UserHandle resolvedUserHandle = resolveUser(userHandle);
            return Binder.withCleanCallingIdentity(
                    () -> ContextualModeManagerService.this.isModeSyncEnabled(resolvedUserHandle));
        }

        @Override
        @EnforcePermission(Manifest.permission.WRITE_SECURE_SETTINGS)
        public void setModeSyncEnabled(UserHandle userHandle, boolean enabled) {
            setModeSyncEnabled_enforcePermission();
            UserHandle resolvedUserHandle = resolveUser(userHandle);
            Binder.withCleanCallingIdentity(
                    () ->
                            Settings.Secure.putIntForUser(
                                    mResolver,
                                    Secure.CONTEXTUAL_MODE_SYNC_ENABLED,
                                    enabled ? 1 : 0,
                                    resolvedUserHandle.getIdentifier()));
        }

        @Override
        @EnforcePermission(Manifest.permission.MANAGE_CONTEXTUAL_MODES)
        public List<ContextualMode> getModes(UserHandle userHandle) {
            getModes_enforcePermission();
            UserHandle resolvedUserHandle = resolveUser(userHandle);
            return Binder.withCleanCallingIdentity(
                    () -> ContextualModeManagerService.this.getModes(resolvedUserHandle));
        }

        @Override
        @EnforcePermission(Manifest.permission.MANAGE_CONTEXTUAL_MODES)
        public void mutateModes(UserHandle userHandle, ContextualModesMutation mutation) {
            mutateModes_enforcePermission();
            final UserHandle resolvedUserHandle = resolveUser(userHandle);
            Binder.withCleanCallingIdentity(
                    () ->
                            ContextualModeManagerService.this.mutateModes(
                                    resolvedUserHandle, mutation));
        }

        @Override
        @RequiresNoPermission
        public void registerModeSyncListener(
                UserHandle userHandle, IContextualModeSyncListener listener) {
            if (!isModeSyncSupported()) {
                return;
            }
            final UserHandle resolvedUserHandle = resolveUser(userHandle);
            synchronized (mLock) {
                RemoteCallbackList<IContextualModeSyncListener> callbackList =
                        mModeSyncListeners.computeIfAbsent(
                                resolvedUserHandle,
                                k ->
                                        new RemoteCallbackList.Builder<IContextualModeSyncListener>(
                                                        FROZEN_CALLEE_POLICY_ENQUEUE_MOST_RECENT)
                                                .setInterfaceDiedCallback(
                                                        (remoteCallbackList,
                                                                deadInterface,
                                                                cookie) ->
                                                                onModeSyncListenerCallbackDied(
                                                                        resolvedUserHandle))
                                                .build());
                if (callbackList.getRegisteredCallbackCount() == 0) {
                    ContentObserver observer =
                            new ModeSyncEnabledContentObserver(resolvedUserHandle);
                    Binder.withCleanCallingIdentity(
                            () -> {
                                try {

                                    mResolver.registerContentObserverAsUser(
                                            Settings.Secure.getUriFor(
                                                    Secure.CONTEXTUAL_MODE_SYNC_ENABLED),
                                            /* notifyForDescendants= */ false,
                                            observer,
                                            resolvedUserHandle);
                                } catch (Exception e) {
                                    // This should only happen in unit test. So catch the exception
                                    // instead of letting it crash.
                                    Slog.e(TAG, "Failed to register content observer.", e);
                                }
                            });
                    mObservers.put(resolvedUserHandle, observer);
                }
                callbackList.register(listener);
            }
        }

        @Override
        @RequiresNoPermission
        public void unregisterModeSyncListener(IContextualModeSyncListener listener) {
            synchronized (mLock) {
                var iterator = mModeSyncListeners.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    UserHandle userHandle = entry.getKey();
                    RemoteCallbackList<IContextualModeSyncListener> callbackList = entry.getValue();
                    callbackList.unregister(listener);
                    if (callbackList.getRegisteredCallbackCount() == 0) {
                        callbackList.kill();
                        iterator.remove();
                        ContentObserver observer = mObservers.remove(userHandle);
                        if (observer != null) {
                            mResolver.unregisterContentObserver(observer);
                        }
                    }
                }
            }
        }

        @Override
        @EnforcePermission(Manifest.permission.MANAGE_CONTEXTUAL_MODES)
        public void registerModeListener(UserHandle userHandle, IContextualModeListener listener) {
            registerModeListener_enforcePermission();
            final UserHandle resolvedUserHandle = resolveUser(userHandle);
            synchronized (mLock) {
                RemoteCallbackList<IContextualModeListener> callbackList =
                        mModeListeners.computeIfAbsent(
                                resolvedUserHandle,
                                k ->
                                        new RemoteCallbackList.Builder<IContextualModeListener>(
                                                        FROZEN_CALLEE_POLICY_ENQUEUE_ALL)
                                                .setInterfaceDiedCallback(
                                                        (remoteCallbackList,
                                                                deadInterface,
                                                                cookie) ->
                                                                onModeListenerCallbackDied(
                                                                        resolvedUserHandle))
                                                .build());
                if (callbackList.getRegisteredCallbackCount() == 0) {
                    // First callback. Invalidate cache in case we have never cached this user's
                    // modes before. This ensures we have an initial cache of the user's modes, so
                    // that we only notify future changes.
                    Binder.withCleanCallingIdentity(
                            () -> refreshModesCache(resolvedUserHandle, /* async= */ false));
                }
                callbackList.register(listener);
            }
        }

        @Override
        @EnforcePermission(Manifest.permission.MANAGE_CONTEXTUAL_MODES)
        public void unregisterModeListener(IContextualModeListener listener) {
            unregisterModeListener_enforcePermission();
            synchronized (mLock) {
                var iterator = mModeListeners.values().iterator();
                while (iterator.hasNext()) {
                    RemoteCallbackList<IContextualModeListener> callbackList = iterator.next();
                    callbackList.unregister(listener);
                    if (callbackList.getRegisteredCallbackCount() == 0) {
                        callbackList.kill();
                        iterator.remove();
                    }
                }
            }
        }

        private UserHandle resolveUser(UserHandle userHandle) {
            if (UserHandle.ALL.equals(userHandle)) {
                throw new IllegalArgumentException("Unsupported user: " + userHandle);
            }
            if (UserHandle.CURRENT_OR_SELF.equals(userHandle)) {
                userHandle =
                        isCallerSystemOrSystemUiOrShell()
                                ? UserHandle.CURRENT
                                : Binder.getCallingUserHandle();
                // Fall through
            }
            if (UserHandle.CURRENT.equals(userHandle)) {
                userHandle = UserHandle.of(mActivityManagerInternal.getCurrentUserId());
            } else if (!mUserManagerInternal.exists(userHandle.getIdentifier())) {
                throw new IllegalArgumentException("User " + userHandle + " doesn't exist!");
            }
            if (!Binder.getCallingUserHandle().equals(userHandle)) {
                mPermissionEnforcer.enforcePermission(
                        permission.INTERACT_ACROSS_USERS,
                        Binder.getCallingPid(),
                        Binder.getCallingUid());
            }
            return userHandle;
        }

        @SuppressWarnings("AndroidFrameworkRequiresPermission")
        private boolean isCallerSystemOrSystemUiOrShell() {
            int callingUid = Binder.getCallingUid();
            if (callingUid == Process.SHELL_UID) {
                return true;
            }
            return isCallerSystemOrSystemUi();
        }

        private boolean isCallerSystemOrSystemUi() {
            if (isUidSystem(Binder.getCallingUid())) {
                return true;
            }
            return getContext().checkCallingPermission(STATUS_BAR_SERVICE) == PERMISSION_GRANTED;
        }

        private boolean isUidSystem(int uid) {
            final int appid = UserHandle.getAppId(uid);
            return appid == Process.SYSTEM_UID || uid == Process.ROOT_UID;
        }

        @Override
        @PermissionManuallyEnforced
        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (!DumpUtils.checkDumpAndUsageStatsPermission(getContext(), TAG, pw)) {
                return;
            }
            IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
            Binder.withCleanCallingIdentity(
                    () -> {
                        synchronized (mLock) {
                            ipw.println("isModeSyncSupported: " + isModeSyncSupported());
                            ipw.println("mCacheInvalidationPending: " + mCacheRefreshPending.get());
                            dumpUserStatesLocked(ipw);
                        }
                    });
        }

        @GuardedBy("mLock")
        private void dumpUserStatesLocked(IndentingPrintWriter ipw) {
            Set<UserHandle> users = new HashSet<>();
            users.addAll(mCurrentModeStates.keySet());
            users.addAll(mModeSyncListeners.keySet());
            users.addAll(mModeListeners.keySet());
            users.addAll(mObservers.keySet());
            ipw.println("User states:");
            ipw.increaseIndent();
            if (users.isEmpty()) {
                ipw.println("No user states!");
            } else {
                for (UserHandle user : users) {
                    ipw.println("User " + user + ":");
                    ipw.increaseIndent();
                    boolean userExists = mUserManagerInternal.exists(user.getIdentifier());
                    ipw.println("User exists: " + userExists);
                    ipw.println("Modes cache: " + mCurrentModeStates.get(user));
                    ipw.println("Mode listeners: " + mModeListeners.get(user));
                    if (userExists) {
                        ipw.println("Mode sync enabled: " + isModeSyncEnabled(user));
                    }
                    ipw.println("Mode sync listeners: " + mModeSyncListeners.get(user));
                    ipw.println(
                            "Registered mode sync content observer: "
                                    + mObservers.containsKey(user));
                    ipw.decreaseIndent();
                }
            }
            ipw.decreaseIndent();
        }
    }

    @GuardedBy("mLock")
    private <E extends IInterface> void notifyListenersLocked(
            RemoteCallbackList<E> callbackList, ThrowingConsumer<E> consumer) {
        callbackList.broadcast(
                l -> {
                    try {
                        consumer.acceptOrThrow(l);
                    } catch (Throwable t) {
                        Slog.e(TAG, "Failed to notify listener!", t);
                    }
                });
    }

    private class ModeSyncEnabledContentObserver extends ContentObserver {
        private final UserHandle mUserHandle;
        @Nullable private Boolean mLastNotifiedVal;

        ModeSyncEnabledContentObserver(UserHandle userHandle) {
            super(mMainExecutor, 0);
            mUserHandle = userHandle;
        }

        @Override
        public void onChange(boolean selfChange) {
            boolean enabled = isModeSyncEnabled(mUserHandle);
            synchronized (mLock) {
                RemoteCallbackList<IContextualModeSyncListener> callbackList =
                        mModeSyncListeners.get(mUserHandle);
                if (callbackList == null) {
                    return;
                }
                if (mLastNotifiedVal == null || mLastNotifiedVal != enabled) {
                    mLastNotifiedVal = enabled;
                    notifyListenersLocked(callbackList, l -> l.onModeSyncEnabledChanged(enabled));
                }
            }
        }
    }
}
