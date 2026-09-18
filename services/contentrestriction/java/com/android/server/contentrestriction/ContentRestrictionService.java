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

package com.android.server.contentrestriction;

import static android.Manifest.permission.BYPASS_ROLE_QUALIFICATION;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.internal.util.Preconditions.checkCallAuthorization;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.RequiresNoPermission;
import android.annotation.UserIdInt;
import android.app.contentrestriction.ClassifiableContent;
import android.app.contentrestriction.ContentRestrictionManager;
import android.app.contentrestriction.IContentRestrictionAppService;
import android.app.contentrestriction.IContentRestrictionCallback;
import android.app.contentrestriction.IContentRestrictionManager;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.LocusId;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.FunctionalUtils.RemoteExceptionIgnoringConsumer;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.appbinding.AppBindingService;
import com.android.server.appbinding.finders.ContentRestrictionAppServiceFinder;
import com.android.server.pm.UserManagerInternal;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * System service for handling content restrictions.
 */
public class ContentRestrictionService extends IContentRestrictionManager.Stub {

    private static final String TAG = "ContentRestrictionService";

    private final Context mContext;
    private final AppBindingService mAppBindingService;
    private final RoleManager mRoleManager;
    private final UserManagerInternal mUserManagerInternal;
    private final RoleObserver mRoleObserver;
    private final ContentRestrictionSettings mContentRestrictionSettings;

    private boolean mAllowBypassingContentRestrictionSandboxing;

    final ContentRestrictionManagerInternal mInternal = new ContentRestrictionManagerInternalImpl();

    private final Object mLock = new Object();

    public ContentRestrictionService(Context context) {
        this(context,
                LocalServices.getService(AppBindingService.class),
                context.getSystemService(RoleManager.class),
                LocalServices.getService(UserManagerInternal.class));
    }

    @VisibleForTesting
    ContentRestrictionService(
            Context context,
            AppBindingService appBindingService,
            RoleManager roleManager,
            UserManagerInternal userManagerInternal) {
        mContext = context;
        mAppBindingService = appBindingService;
        mRoleManager = roleManager;
        mUserManagerInternal = userManagerInternal;
        mUserManagerInternal.addUserLifecycleListener(new UserLifecycleListener());
        mRoleObserver = new RoleObserver();
        mRoleObserver.register();
        mContentRestrictionSettings = new ContentRestrictionSettings();
        mAllowBypassingContentRestrictionSandboxing = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowBypassingContentRestrictionSandboxing);
    }

    @Override
    @PermissionManuallyEnforced
    public boolean isContentRestrictionEnabledForUser(@UserIdInt int userId) {
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }
        synchronized (mLock) {
            return mContentRestrictionSettings.getUserData(userId).contentRestrictionEnabled;
        }
    }

    @Override
    @PermissionManuallyEnforced
    public void requestClassification(
            @UserIdInt int userId, ClassifiableContent content,
            IContentRestrictionCallback callback) {
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }
        if (callback == null) {
            return;
        }
        if (!isContentRestrictionEnabledForUser(userId)) {
            dispatchIsContentAllowedCallback(callback, true);
            return;
        }

        Binder.withCleanCallingIdentity(() -> {
            final IContentRestrictionCallback systemCallback =
                    new IContentRestrictionCallback.Stub() {
                @Override
                @RequiresNoPermission
                public void onResult(boolean isContentAllowed) {
                    dispatchIsContentAllowedCallback(callback, isContentAllowed);
                }

                @Override
                @RequiresNoPermission
                public void onError(int error, String message) {
                    dispatchOnErrorCallback(callback, error, message);
                }
            };

            dispatchContentRestrictionAppServiceEvent(userId, service -> {
                if (service != null) {
                    service.onClassifyContent(content, systemCallback);
                } else {
                    Slog.w(TAG, "ContentRestrictionAppService was null. Failing close.");
                    dispatchIsContentAllowedCallback(callback, false);
                }
            });
        });
    }

    @Override
    @PermissionManuallyEnforced
    public boolean shouldAllowBypassingContentRestrictionSandboxingForUser(@UserIdInt int userId) {
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }

        return mAllowBypassingContentRestrictionSandboxing;
    }

    @Override
    @PermissionManuallyEnforced
    public void setShouldAllowBypassingContentRestrictionSandboxingForUser(
            @UserIdInt int userId, boolean enabled) {
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }

        enforcePermission(BYPASS_ROLE_QUALIFICATION);
        mAllowBypassingContentRestrictionSandboxing = enabled;
    }

    @Override
    @RequiresNoPermission
    public Intent createContentRestrictedIntent(LocusId locusId) {
        Intent intent = new Intent(Settings.ACTION_SHOW_RESTRICTED_CONTENT);
        intent.putExtra(ContentRestrictionManager.EXTRA_CONTENT_LOCUS_ID, locusId);
        return intent;
    }

    private void dispatchIsContentAllowedCallback(IContentRestrictionCallback callback,
            boolean isContentAllowed) {
        if (callback == null) {
            return;
        }
        BackgroundThread.getExecutor().execute(() -> {
            try {
                callback.onResult(isContentAllowed);
            } catch (RemoteException e) {
                // Client died, the service should have logged it.
            }
        });
    }

    private void dispatchOnErrorCallback(IContentRestrictionCallback callback,
            int error, String message) {
        if (callback == null) {
            return;
        }
        BackgroundThread.getExecutor().execute(() -> {
            try {
                callback.onError(error, message);
            } catch (RemoteException e) {
                // Client died, the service should have logged it.
            }
        });
    }

    private void dispatchContentRestrictionAppServiceEvent(
            @UserIdInt int userId,
            @NonNull RemoteExceptionIgnoringConsumer<IContentRestrictionAppService> action) {
        mAppBindingService.dispatchAppServiceEvent(
            ContentRestrictionAppServiceFinder.class,
            userId,
            (connection) -> {
                if (connection == null) {
                    action.accept(null);
                    return;
                }
                IContentRestrictionAppService binder =
                        (IContentRestrictionAppService) connection.getServiceBinder();
                action.accept(binder);
            });
    }

    private void setContentRestrictionPackagesInternal(
            @UserIdInt int userId,
            @Nullable List<String> packageNames,
            @NonNull String systemEntity) {
        synchronized (mLock) {
            ContentRestrictionUserData data = mContentRestrictionSettings.getUserData(userId);
            if (packageNames == null || packageNames.isEmpty()) {
                data.contentRestrictionPackages.remove(systemEntity);
            } else {
                data.contentRestrictionPackages.put(systemEntity, new ArrayList<>(packageNames));
            }
            data.contentRestrictionEnabled = !data.contentRestrictionPackages.isEmpty();
            mContentRestrictionSettings.saveUserData();
        }
        updateContentRestrictionRoleHolders(userId);
    }

    private void updateContentRestrictionRoleHolders(@UserIdInt int userId) {
        Set<String> allPackages = new HashSet<>();
        synchronized (mLock) {
            ContentRestrictionUserData data = mContentRestrictionSettings.getUserData(userId);
            for (List<String> packages : data.contentRestrictionPackages.values()) {
                allPackages.addAll(packages);
            }
        }

        final UserHandle userHandle = UserHandle.of(userId);
        final List<String> currentPackages = mRoleManager.getRoleHoldersAsUser(
                RoleManager.ROLE_CONTENT_RESTRICTION, userHandle);

        final Set<String> packagesToAdd = new HashSet<>(allPackages);
        packagesToAdd.removeAll(currentPackages);

        final Set<String> packagesToRemove = new HashSet<>(currentPackages);
        packagesToRemove.removeAll(allPackages);

        for (String packageName : packagesToAdd) {
            mRoleManager.addRoleHolderAsUser(
                    RoleManager.ROLE_CONTENT_RESTRICTION, packageName, 0, userHandle,
                    mContext.getMainExecutor(),
                    successful -> {
                        if (!successful) {
                            Slog.w(TAG, "Failed to add " + packageName + " as role holder");
                        }
                    });
        }

        for (String packageName : packagesToRemove) {
            mRoleManager.removeRoleHolderAsUser(
                    RoleManager.ROLE_CONTENT_RESTRICTION, packageName, 0, userHandle,
                    mContext.getMainExecutor(),
                    successful -> {
                        if (!successful) {
                            Slog.w(TAG, "Failed to remove " + packageName + " as role holder");
                        }
                    });
        }
    }

    private void enforcePermission(String permission) {
        checkCallAuthorization(hasCallingPermission(permission));
    }

    @PermissionManuallyEnforced
    private boolean hasCallingPermission(String permission) {
        return mContext.checkCallingOrSelfPermission(permission) == PERMISSION_GRANTED;
    }

    public static class Lifecycle extends SystemService {
        private final ContentRestrictionService mContentRestrictionService;

        public Lifecycle(@NonNull Context context) {
            super(context);
            mContentRestrictionService = new ContentRestrictionService(context);
        }

        @VisibleForTesting
        Lifecycle(Context context, ContentRestrictionService service) {
            super(context);
            mContentRestrictionService = service;
        }

        @Override
        public void onStart() {
            publishLocalService(
                    ContentRestrictionManagerInternal.class,
                    mContentRestrictionService.mInternal);
            publishBinderService(Context.CONTENT_RESTRICTION_SERVICE, mContentRestrictionService);
        }
    }

    /** Implementation of the local service, API used by other services. */
    private final class ContentRestrictionManagerInternalImpl
            extends ContentRestrictionManagerInternal {
        @Override
        public boolean isContentRestrictionEnabledForUser(@UserIdInt int userId) {
            return ContentRestrictionService.this.isContentRestrictionEnabledForUser(userId);
        }

        @Override
        public void setContentRestrictionPackages(
                @UserIdInt int userId,
                @Nullable List<String> packageNames,
                @NonNull String systemEntity) {
            ContentRestrictionService.this.setContentRestrictionPackagesInternal(
                    userId, packageNames, systemEntity);
        }
    }

    private final class RoleObserver implements OnRoleHoldersChangedListener {
        RoleObserver() {}

        void register() {
            mRoleManager.addOnRoleHoldersChangedListenerAsUser(
                BackgroundThread.getExecutor(), this, UserHandle.ALL);
        }

        @Override
        public void onRoleHoldersChanged(@NonNull String roleName, @NonNull UserHandle user) {
            if (RoleManager.ROLE_CONTENT_RESTRICTION.equals(roleName)) {
                updateContentRestrictionRoleHolders(user.getIdentifier());
            }
        }
    }

    /** Deletes content restriction data when the user gets removed. */
    private final class UserLifecycleListener implements UserManagerInternal.UserLifecycleListener {
        @Override
        public void onUserRemoved(UserInfo user) {
            synchronized (mLock) {
                mContentRestrictionSettings.removeUserData(user.id);
            }
        }
    }

    @Override
    @PermissionManuallyEnforced
    protected void dump(
        @NonNull FileDescriptor fd, @NonNull PrintWriter printWriter, @Nullable String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, printWriter)) {
            return;
        }

        try (var pw = new IndentingPrintWriter(printWriter, "  ")) {
            pw.println("ContentRestrictionService state:");
            pw.increaseIndent();

            pw.println("allowBypassingContentRestrictionSandboxing: "
                    + mAllowBypassingContentRestrictionSandboxing);
            pw.println();

            synchronized (mLock) {
                mContentRestrictionSettings.dump(pw);
            }
        }
    }
}
