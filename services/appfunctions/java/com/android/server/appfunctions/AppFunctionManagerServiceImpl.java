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

import static android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_UNREQUESTABLE;
import static android.app.appfunctions.AppFunctionManager.ACTION_REQUEST_APP_FUNCTION_ACCESS;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_METADATA_DB;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_NAMESPACE;

import static com.android.server.appfunctions.AppFunctionExecutors.THREAD_POOL_EXECUTOR;
import static com.android.server.appfunctions.CallerValidator.CAN_EXECUTE_APP_FUNCTIONS_ALLOWED_HAS_PERMISSION;
import static com.android.server.appfunctions.CallerValidator.CAN_EXECUTE_APP_FUNCTIONS_DENIED;

import android.Manifest;
import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.app.IUriGrantsManager;
import android.app.appfunctions.AppFunctionAccessServiceInterface;
import android.app.appfunctions.AppFunctionException;
import android.app.appfunctions.AppFunctionManager;
import android.app.appfunctions.AppFunctionManagerHelper;
import android.app.appfunctions.AppFunctionManagerHelper.AppFunctionNotFoundException;
import android.app.appfunctions.AppFunctionRuntimeMetadata;
import android.app.appfunctions.AppFunctionStaticMetadataHelper;
import android.app.appfunctions.AppFunctionUriGrant;
import android.app.appfunctions.ExecuteAppFunctionAidlRequest;
import android.app.appfunctions.ExecuteAppFunctionResponse;
import android.app.appfunctions.IAppFunctionEnabledCallback;
import android.app.appfunctions.IAppFunctionManager;
import android.app.appfunctions.IAppFunctionService;
import android.app.appfunctions.ICancellationCallback;
import android.app.appfunctions.IExecuteAppFunctionCallback;
import android.app.appfunctions.SafeOneTimeExecuteAppFunctionCallback;
import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchManager.SearchContext;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.ObserverCallback;
import android.app.appsearch.observer.ObserverSpec;
import android.app.appsearch.observer.SchemaChangeInfo;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.SignedPackage;
import android.content.pm.SignedPackageParcel;
import android.content.pm.SigningInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.OutcomeReceiver;
import android.os.ParcelableException;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.flags.Flags;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.util.DumpUtils;
import com.android.server.FgThread;
import com.android.server.SystemService;
import com.android.server.SystemService.TargetUser;
import com.android.server.uri.UriGrantsManagerInternal;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** Implementation of the AppFunctionManagerService. */
public class AppFunctionManagerServiceImpl extends IAppFunctionManager.Stub {
    private static final String TAG = AppFunctionManagerServiceImpl.class.getSimpleName();
    private static final String ALLOWLISTED_APP_FUNCTIONS_AGENTS =
            "allowlisted_app_functions_agents";
    private static final String NAMESPACE_MACHINE_LEARNING = "machine_learning";
    private static final String SHELL_PKG = "com.android.shell";

    private static final Uri ADDITIONAL_AGENTS_URI =
            Settings.Secure.getUriFor(Settings.Secure.APP_FUNCTION_ADDITIONAL_AGENT_ALLOWLIST);

    private final RemoteServiceCaller<IAppFunctionService> mRemoteServiceCaller;
    private final CallerValidator mCallerValidator;
    private final ServiceHelper mInternalServiceHelper;
    private final ServiceConfig mServiceConfig;
    private final Context mContext;
    private final Map<String, Object> mLocks = new WeakHashMap<>();
    private final AppFunctionsLoggerWrapper mLoggerWrapper;
    private final PackageManagerInternal mPackageManagerInternal;
    // Not Guarded by lock since this is only accessed in main thread.
    private final SparseArray<PackageMonitor> mPackageMonitors = new SparseArray<>();

    private final AppFunctionAccessServiceInterface mAppFunctionAccessService;

    private final IUriGrantsManager mUriGrantsManager;

    private final UriGrantsManagerInternal mUriGrantsManagerInternal;

    private final IBinder mPermissionOwner;

    private final DeviceSettingHelper mDeviceSettingHelper;

    private final MultiUserAppFunctionAccessHistory mMultiUserAppFunctionAccessHistory;

    private final Object mAgentAllowlistLock = new Object();

    // Any agents hardcoded by the system
    private static final List<SignedPackage> sSystemAllowlist =
            List.of(new SignedPackage(SHELL_PKG, null));

    // The main agent allowlist, set by the updatable DeviceConfig System
    @GuardedBy("mAgentAllowlistLock")
    private List<SignedPackage> mUpdatableAgentAllowlist = Collections.emptyList();

    // A secondary agent allowlist, set by ADB command using a secure setting
    @GuardedBy("mAgentAllowlistLock")
    private List<SignedPackage> mSecureSettingAgentAllowlist = Collections.emptyList();

    // The merged allowlist.
    @GuardedBy("mAgentAllowlistLock")
    private ArraySet<SignedPackage> mAgentAllowlist = new ArraySet<>(sSystemAllowlist);

    private final ContentObserver mAdbAgentObserver =
            new ContentObserver(FgThread.getHandler()) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    if (!ADDITIONAL_AGENTS_URI.equals(uri)) {
                        return;
                    }
                    updateAgentAllowlist(
                            /* readFromDeviceConfig= */ false, /* readFromSecureSetting= */ true);
                }
            };

    private final Executor mBackgroundExecutor;

    private final AppFunctionAgentAllowlistStorage mAgentAllowlistStorage;

    public AppFunctionManagerServiceImpl(
            @NonNull Context context,
            @NonNull PackageManagerInternal packageManagerInternal,
            @NonNull AppFunctionAccessServiceInterface appFunctionAccessServiceInterface,
            @NonNull IUriGrantsManager uriGrantsManager,
            @NonNull UriGrantsManagerInternal uriGrantsManagerInternal,
            @NonNull AppFunctionsLoggerWrapper loggerWrapper,
            @NonNull AppFunctionAgentAllowlistStorage agentAllowlistStorage,
            @NonNull MultiUserAppFunctionAccessHistory multiUserAppFunctionAccessHistory,
            @NonNull Executor backgroundExecutor) {
        this(
                context,
                new RemoteServiceCallerImpl<>(
                        context, IAppFunctionService.Stub::asInterface, THREAD_POOL_EXECUTOR),
                new CallerValidatorImpl(
                        context,
                        appFunctionAccessServiceInterface,
                        Objects.requireNonNull(context.getSystemService(UserManager.class))),
                new ServiceHelperImpl(context),
                new ServiceConfigImpl(),
                loggerWrapper,
                packageManagerInternal,
                appFunctionAccessServiceInterface,
                uriGrantsManager,
                uriGrantsManagerInternal,
                new DeviceSettingHelperImpl(context),
                agentAllowlistStorage,
                multiUserAppFunctionAccessHistory,
                backgroundExecutor);
    }

    private AppFunctionManagerServiceImpl(
            Context context,
            RemoteServiceCaller<IAppFunctionService> remoteServiceCaller,
            CallerValidator callerValidator,
            ServiceHelper appFunctionInternalServiceHelper,
            ServiceConfig serviceConfig,
            AppFunctionsLoggerWrapper loggerWrapper,
            PackageManagerInternal packageManagerInternal,
            AppFunctionAccessServiceInterface appFunctionAccessServiceInterface,
            IUriGrantsManager uriGrantsManager,
            UriGrantsManagerInternal uriGrantsManagerInternal,
            DeviceSettingHelper deviceSettingHelper,
            AppFunctionAgentAllowlistStorage agentAllowlistStorage,
            MultiUserAppFunctionAccessHistory multiUserAppFunctionAccessHistory,
            Executor backgroundExecutor) {
        mContext = Objects.requireNonNull(context);
        mRemoteServiceCaller = Objects.requireNonNull(remoteServiceCaller);
        mCallerValidator = Objects.requireNonNull(callerValidator);
        mInternalServiceHelper = Objects.requireNonNull(appFunctionInternalServiceHelper);
        mServiceConfig = Objects.requireNonNull(serviceConfig);
        mLoggerWrapper = Objects.requireNonNull(loggerWrapper);
        mPackageManagerInternal = Objects.requireNonNull(packageManagerInternal);
        mAppFunctionAccessService = Objects.requireNonNull(appFunctionAccessServiceInterface);
        mUriGrantsManager = Objects.requireNonNull(uriGrantsManager);
        mUriGrantsManagerInternal = Objects.requireNonNull(uriGrantsManagerInternal);
        mPermissionOwner =
                Objects.requireNonNull(
                        mUriGrantsManagerInternal.newUriPermissionOwner("appfunctions"));
        mDeviceSettingHelper = Objects.requireNonNull(deviceSettingHelper);
        mAgentAllowlistStorage = Objects.requireNonNull(agentAllowlistStorage);
        mMultiUserAppFunctionAccessHistory =
                Objects.requireNonNull(multiUserAppFunctionAccessHistory);
        mBackgroundExecutor = Objects.requireNonNull(backgroundExecutor);
    }

    /** Called when the user is unlocked. */
    public void onUserUnlocked(TargetUser user) {
        Objects.requireNonNull(user);
        registerAppSearchObserver(user);
        trySyncRuntimeMetadata(user);
        PackageMonitor pkgMonitorForUser =
                AppFunctionPackageMonitor.registerPackageMonitorForUser(mContext, user);
        mPackageMonitors.append(user.getUserIdentifier(), pkgMonitorForUser);
        if (accessCheckFlagsEnabled()) {
            mMultiUserAppFunctionAccessHistory.onUserUnlocked(user);
        }
    }

    /** Called when a the user is starting. */
    public void onUserStarting(@NonNull TargetUser user) {
        mAppFunctionAccessService.onUserStarting(user.getUserIdentifier());
    }

    /** Called when the user is stopping. */
    public void onUserStopping(@NonNull TargetUser user) {
        Objects.requireNonNull(user);

        MetadataSyncPerUser.removeUserSyncAdapter(user.getUserHandle());

        int userIdentifier = user.getUserIdentifier();
        if (mPackageMonitors.contains(userIdentifier)) {
            mPackageMonitors.get(userIdentifier).unregister();
            mPackageMonitors.delete(userIdentifier);
        }
        if (accessCheckFlagsEnabled()) {
            mMultiUserAppFunctionAccessHistory.onUserStopping(user);
        }
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter pw, @NonNull String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, pw)) {
            return;
        }

        final long token = Binder.clearCallingIdentity();
        try {
            AppFunctionDumpHelper.dumpAppFunctionsState(mContext, pw, args);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void onShellCommand(
            FileDescriptor in,
            FileDescriptor out,
            FileDescriptor err,
            @NonNull String[] args,
            ShellCallback callback,
            @NonNull ResultReceiver resultReceiver) {
        new AppFunctionManagerServiceShellCommand(mContext, this)
                .exec(this, in, out, err, args, callback, resultReceiver);
    }

    private final DeviceConfig.OnPropertiesChangedListener mDeviceConfigListener =
            properties -> {
                if (Flags.appFunctionAccessServiceEnabled()) {
                    if (properties.getKeyset().contains(ALLOWLISTED_APP_FUNCTIONS_AGENTS)) {
                        updateAgentAllowlist(
                                /* readFromDeviceConfig= */ true,
                                /* readFromSecureSetting= */ false);
                    }
                }
            };

    /**
     * Called during different phases of the system boot process.
     *
     * @param phase The current boot phase, as defined in {@link SystemService}. This method
     *     specifically acts on {@link SystemService#PHASE_SYSTEM_SERVICES_READY}.
     */
    public void onBootPhase(int phase) {
        if (!Flags.appFunctionAccessServiceEnabled()) return;
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            mBackgroundExecutor.execute(
                    () ->
                            updateAgentAllowlist(
                                    /* readFromDeviceConfig */ true,
                                    /* readFromSecureSetting= */ true));
            DeviceConfig.addOnPropertiesChangedListener(
                    NAMESPACE_MACHINE_LEARNING, mBackgroundExecutor, mDeviceConfigListener);
            mContext.getContentResolver()
                    .registerContentObserver(ADDITIONAL_AGENTS_URI, false, mAdbAgentObserver);
        }
    }

    @Override
    public ICancellationSignal executeAppFunction(
            @NonNull ExecuteAppFunctionAidlRequest requestInternal,
            @NonNull IExecuteAppFunctionCallback executeAppFunctionCallback) {
        Objects.requireNonNull(requestInternal);
        Objects.requireNonNull(executeAppFunctionCallback);

        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        final SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback =
                initializeSafeExecuteAppFunctionCallback(
                        requestInternal, executeAppFunctionCallback, callingUid);

        String validatedCallingPackage;
        try {
            validatedCallingPackage =
                    mCallerValidator.validateCallingPackage(requestInternal.getCallingPackage());
            mCallerValidator.verifyTargetUserHandle(
                    requestInternal.getUserHandle(), validatedCallingPackage);
        } catch (SecurityException exception) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_DENIED, exception.getMessage()));
            return null;
        }

        ICancellationSignal localCancelTransport = CancellationSignal.createTransport();

        THREAD_POOL_EXECUTOR.execute(
                () -> {
                    try {
                        executeAppFunctionInternal(
                                requestInternal,
                                callingUid,
                                callingPid,
                                localCancelTransport,
                                safeExecuteAppFunctionCallback,
                                executeAppFunctionCallback.asBinder());
                    } catch (Exception e) {
                        safeExecuteAppFunctionCallback.onError(
                                mapExceptionToExecuteAppFunctionResponse(e));
                    }
                });
        return localCancelTransport;
    }

    @WorkerThread
    private void executeAppFunctionInternal(
            @NonNull ExecuteAppFunctionAidlRequest requestInternal,
            int callingUid,
            int callingPid,
            @NonNull ICancellationSignal localCancelTransport,
            @NonNull SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback,
            @NonNull IBinder callerBinder) {
        UserHandle targetUser = requestInternal.getUserHandle();
        UserHandle callingUser = UserHandle.getUserHandleForUid(callingUid);
        if (!mCallerValidator.verifyEnterprisePolicyIsAllowed(callingUser, targetUser)) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_ENTERPRISE_POLICY_DISALLOWED,
                            "Cannot run on a user with a restricted enterprise policy"));
            return;
        }

        String targetPackageName = requestInternal.getClientRequest().getTargetPackageName();
        if (TextUtils.isEmpty(targetPackageName)) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_INVALID_ARGUMENT,
                            "Target package name cannot be empty."));
            return;
        }

        mCallerValidator
                .verifyCallerCanExecuteAppFunction(
                        callingUid,
                        callingPid,
                        targetUser,
                        requestInternal.getCallingPackage(),
                        targetPackageName,
                        requestInternal.getClientRequest().getFunctionIdentifier())
                .thenCompose(
                        canExecuteResult -> {
                            if (canExecuteResult == CAN_EXECUTE_APP_FUNCTIONS_DENIED) {
                                return AndroidFuture.failedFuture(
                                        new SecurityException(
                                                "Caller does not have permission to execute the"
                                                        + " appfunction"));
                            }
                            return isAppFunctionEnabled(
                                            requestInternal
                                                    .getClientRequest()
                                                    .getFunctionIdentifier(),
                                            requestInternal
                                                    .getClientRequest()
                                                    .getTargetPackageName(),
                                            getAppSearchManagerAsUser(
                                                    requestInternal.getUserHandle()),
                                            THREAD_POOL_EXECUTOR)
                                    .thenApply(
                                            isEnabled -> {
                                                if (!isEnabled) {
                                                    throw new DisabledAppFunctionException(
                                                            "The app function is disabled");
                                                }
                                                return canExecuteResult;
                                            });
                        })
                .thenAccept(
                        canExecuteResult -> {
                            int bindFlags = Context.BIND_AUTO_CREATE;
                            if (canExecuteResult
                                    == CAN_EXECUTE_APP_FUNCTIONS_ALLOWED_HAS_PERMISSION) {
                                // If the caller doesn't have the permission, do not use
                                // BIND_FOREGROUND_SERVICE to avoid it raising its process state by
                                // calling its own AppFunctions.
                                bindFlags |= Context.BIND_FOREGROUND_SERVICE;
                            }
                            Intent serviceIntent =
                                    mInternalServiceHelper.resolveAppFunctionService(
                                            targetPackageName, targetUser);
                            if (serviceIntent == null) {
                                safeExecuteAppFunctionCallback.onError(
                                        new AppFunctionException(
                                                AppFunctionException.ERROR_SYSTEM_ERROR,
                                                "Cannot find the target service."));
                                return;
                            }
                            // Grant target app implicit visibility to the caller
                            final int grantRecipientUserId = targetUser.getIdentifier();
                            final int grantRecipientAppId =
                                    UserHandle.getAppId(
                                            mPackageManagerInternal.getPackageUid(
                                                    requestInternal
                                                            .getClientRequest()
                                                            .getTargetPackageName(),
                                                    /* flags= */ 0,
                                                    /* userId= */ grantRecipientUserId));
                            if (grantRecipientAppId > 0) {
                                mPackageManagerInternal.grantImplicitAccess(
                                        grantRecipientUserId,
                                        serviceIntent,
                                        grantRecipientAppId,
                                        callingUid,
                                        /* direct= */ true);
                            }
                            bindAppFunctionServiceUnchecked(
                                    requestInternal,
                                    serviceIntent,
                                    targetUser,
                                    localCancelTransport,
                                    safeExecuteAppFunctionCallback,
                                    bindFlags,
                                    callerBinder,
                                    callingUid);
                        })
                .exceptionally(
                        ex -> {
                            safeExecuteAppFunctionCallback.onError(
                                    mapExceptionToExecuteAppFunctionResponse(ex));
                            return null;
                        });
    }

    private static AndroidFuture<Boolean> isAppFunctionEnabled(
            @NonNull String functionIdentifier,
            @NonNull String targetPackage,
            @NonNull AppSearchManager appSearchManager,
            @NonNull Executor executor) {
        AndroidFuture<Boolean> future = new AndroidFuture<>();
        AppFunctionManagerHelper.isAppFunctionEnabled(
                functionIdentifier,
                targetPackage,
                appSearchManager,
                executor,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Boolean result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        future.completeExceptionally(error);
                    }
                });
        return future;
    }

    @Override
    public void setAppFunctionEnabled(
            @NonNull String callingPackage,
            @NonNull String functionIdentifier,
            @NonNull UserHandle userHandle,
            @AppFunctionManager.EnabledState int enabledState,
            @NonNull IAppFunctionEnabledCallback callback) {
        try {
            // Skip validation for shell to allow changing enabled state via shell.
            if (Binder.getCallingUid() != Process.SHELL_UID) {
                mCallerValidator.validateCallingPackage(callingPackage);
            }
        } catch (SecurityException e) {
            reportException(callback, e);
            return;
        }
        THREAD_POOL_EXECUTOR.execute(
                () -> {
                    try {
                        synchronized (getLockForPackage(callingPackage)) {
                            setAppFunctionEnabledInternalLocked(
                                    callingPackage, functionIdentifier, userHandle, enabledState);
                        }
                        callback.onSuccess();
                    } catch (Exception e) {
                        Slog.e(TAG, "Error in setAppFunctionEnabled: ", e);
                        reportException(callback, e);
                    }
                });
    }

    @Override
    public int getAccessFlags(
            String agentPackageName, int agentUserId, String targetPackageName, int targetUserId)
            throws RemoteException {
        if (!accessCheckFlagsEnabled()) {
            return 0;
        }
        final String targetPermissionOwner =
                mDeviceSettingHelper.getPermissionOwnerPackage(targetPackageName);
        return mAppFunctionAccessService.getAccessFlags(
                agentPackageName, agentUserId, targetPermissionOwner, targetUserId);
    }

    @Override
    public boolean updateAccessFlags(
            String agentPackageName,
            int agentUserId,
            String targetPackageName,
            int targetUserId,
            int flagMask,
            int flags)
            throws RemoteException {
        if (!accessCheckFlagsEnabled()) {
            return false;
        }
        final String targetPermissionOwner =
                mDeviceSettingHelper.getPermissionOwnerPackage(targetPackageName);
        return mAppFunctionAccessService.updateAccessFlags(
                agentPackageName,
                agentUserId,
                targetPermissionOwner,
                targetUserId,
                flagMask,
                flags);
    }

    @Override
    public void revokeSelfAccess(String targetPackageName) {
        if (!accessCheckFlagsEnabled()) {
            return;
        }
        final String targetPermissionOwner =
                mDeviceSettingHelper.getPermissionOwnerPackage(targetPackageName);
        mAppFunctionAccessService.revokeSelfAccess(targetPermissionOwner);
    }

    @Override
    public int getAccessRequestState(
            String agentPackageName, int agentUserId, String targetPackageName, int targetUserId)
            throws RemoteException {
        if (!accessCheckFlagsEnabled()) {
            return ACCESS_REQUEST_STATE_UNREQUESTABLE;
        }
        final String targetPermissionOwner =
                mDeviceSettingHelper.getPermissionOwnerPackage(targetPackageName);
        return mAppFunctionAccessService.getAccessRequestState(
                agentPackageName, agentUserId, targetPermissionOwner, targetUserId);
    }

    @Override
    public List<String> getValidAgents(int userId) throws RemoteException {
        if (!accessCheckFlagsEnabled()) {
            return List.of();
        }
        return mAppFunctionAccessService.getValidAgents(userId);
    }

    @Override
    public List<String> getValidTargets(int userId) throws RemoteException {
        if (!accessCheckFlagsEnabled()) {
            return List.of();
        }
        final List<String> validTargets = mAppFunctionAccessService.getValidTargets(userId);
        final ArraySet<String> validPermissionOwnerTargets = new ArraySet<>();

        final int validTargetSize = validTargets.size();
        for (int i = 0; i < validTargetSize; i++) {
            final String target = validTargets.get(i);
            final String permissionOwner = mDeviceSettingHelper.getPermissionOwnerPackage(target);
            validPermissionOwnerTargets.add(permissionOwner);
        }

        return List.copyOf(validPermissionOwnerTargets);
    }

    @Override
    @EnforcePermission(Manifest.permission.MANAGE_APP_FUNCTION_ACCESS)
    public List<SignedPackageParcel> getAgentAllowlist() {
        getAgentAllowlist_enforcePermission();
        if (!accessCheckFlagsEnabled()) {
            return List.of();
        }
        synchronized (mAgentAllowlistLock) {
            int agentAllowlistSize = mAgentAllowlist.size();
            List<SignedPackageParcel> agentAllowlistParcels = new ArrayList<>(agentAllowlistSize);
            for (int i = 0; i < agentAllowlistSize; i++) {
                agentAllowlistParcels.add(mAgentAllowlist.valueAt(i).getData());
            }
            return agentAllowlistParcels;
        }
    }

    @Override
    @EnforcePermission(Manifest.permission.MANAGE_APP_FUNCTION_ACCESS)
    public void clearAccessHistory(int userId) {
        clearAccessHistory_enforcePermission();
        enforceClearAccessHistoryUserPermission(userId);
        try {
            mMultiUserAppFunctionAccessHistory.asUser(userId).deleteAll();
        } catch (IllegalStateException e) {
            Slog.w(TAG, "Unable to clear access history", e);
        }
    }

    private void enforceClearAccessHistoryUserPermission(int userId) {
        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();
        mCallerValidator.verifyUserInteraction(userId, callingUid, callingPid);
    }

    private void updateAgentAllowlist(boolean readFromDeviceConfig, boolean readFromSecureSetting) {
        synchronized (mAgentAllowlistLock) {
            List<SignedPackage> newDeviceConfigAgents;
            boolean changed = false;
            if (readFromDeviceConfig) {
                newDeviceConfigAgents = readDeviceConfigAgentAllowlist();
                if (newDeviceConfigAgents == null) {
                    // If we fail to parse a valid list
                    newDeviceConfigAgents = mUpdatableAgentAllowlist;
                }
            } else {
                newDeviceConfigAgents = mUpdatableAgentAllowlist;
            }
            changed = changed || !newDeviceConfigAgents.equals(mUpdatableAgentAllowlist);
            List<SignedPackage> newAdbAgents;
            if (readFromSecureSetting) {
                newAdbAgents = readAdbAgentAllowlist();
            } else {
                newAdbAgents = mSecureSettingAgentAllowlist;
            }
            changed = changed || !newAdbAgents.equals(mSecureSettingAgentAllowlist);

            if (!changed) {
                return;
            }

            ArraySet<SignedPackage> newAgents = new ArraySet<>();
            newAgents.addAll(newDeviceConfigAgents);
            newAgents.addAll(newAdbAgents);
            newAgents.addAll(sSystemAllowlist);

            mUpdatableAgentAllowlist = newDeviceConfigAgents;
            mSecureSettingAgentAllowlist = newAdbAgents;
            mAgentAllowlist = newAgents;
            mAppFunctionAccessService.setAgentAllowlist(mAgentAllowlist);
        }
    }

    @Nullable
    @WorkerThread
    private List<SignedPackage> readDeviceConfigAgentAllowlist() {
        final String allowlistString =
                DeviceConfig.getString(
                        NAMESPACE_MACHINE_LEARNING, ALLOWLISTED_APP_FUNCTIONS_AGENTS, "");
        if (!TextUtils.isEmpty(allowlistString)) {
            try {
                List<SignedPackage> parsedAllowlist =
                        SignedPackageParser.parseList(allowlistString);
                mAgentAllowlistStorage.writeCurrentAllowlist(allowlistString);
                return parsedAllowlist;
            } catch (Exception e) {
                Slog.e(TAG, "Cannot parse agent allowlist from config: " + allowlistString, e);
            }
        }
        List<SignedPackage> stored = mAgentAllowlistStorage.readPreviousValidAllowlist();
        if (stored != null) {
            Slog.i(TAG, "Using previously stored valid allowlist.");
            return stored;
        }
        Slog.i(TAG, "No valid stored allowlist, falling back to static list.");
        return readPreloadedAgentAllowlist();
    }

    @NonNull
    private List<SignedPackage> readPreloadedAgentAllowlist() {
        final String[] preloadedAllowlistArray =
                mContext.getResources()
                        .getStringArray(
                                com.android.internal.R.array
                                        .config_defaultAppFunctionAgentAllowlist);
        if (preloadedAllowlistArray.length == 0) {
            return Collections.emptyList();
        }
        final String preloadedAllowlistString = String.join(";", preloadedAllowlistArray);
        try {
            return SignedPackageParser.parseList(preloadedAllowlistString);
        } catch (Exception e) {
            Slog.e(TAG, "Cannot parse preloaded allowlist: " + preloadedAllowlistString, e);
            return Collections.emptyList();
        }
    }

    @NonNull
    private List<SignedPackage> readAdbAgentAllowlist() {
        String agents =
                Settings.Secure.getStringForUser(
                        mContext.getContentResolver(),
                        Settings.Secure.APP_FUNCTION_ADDITIONAL_AGENT_ALLOWLIST,
                        Process.myUserHandle().getIdentifier());
        if (agents == null) {
            return Collections.emptyList();
        }
        try {
            return SignedPackageParser.parseList(agents);
        } catch (Exception e) {
            Slog.e(TAG, "Cannot parse agent list string: " + agents, e);
            return Collections.emptyList();
        }
    }

    @Override
    @NonNull
    public Intent createRequestAccessIntent(@NonNull String targetPackageName) {
        Objects.requireNonNull(targetPackageName);
        final String permissionOwner =
                mDeviceSettingHelper.getPermissionOwnerPackage(targetPackageName);
        Intent intent = new Intent(ACTION_REQUEST_APP_FUNCTION_ACCESS);
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, permissionOwner);
        intent.setPackage(mContext.getPackageManager().getPermissionControllerPackageName());
        return intent;
    }

    private boolean accessCheckFlagsEnabled() {
        return android.permission.flags.Flags.appFunctionAccessApiEnabled()
                && android.permission.flags.Flags.appFunctionAccessServiceEnabled();
    }

    /**
     * Grants temporary uri permission on behalf of the app that returns the response to the caller
     * that sends the request.
     *
     * <p>All {@link AppFunctionUriGrant} in {@link ExecuteAppFunctionResponse} would be granted to
     * the receiver until the system service is finished. That is usually until device reboots.
     */
    private void grantTemporaryUriPermissions(
            @NonNull ExecuteAppFunctionAidlRequest requestInternal,
            @NonNull ExecuteAppFunctionResponse response,
            int callingUid) {
        if (!accessCheckFlagsEnabled()) return;

        final int uriReceiverUserId = UserHandle.getUserId(callingUid);
        final int targetUserId = requestInternal.getUserHandle().getIdentifier();
        final String uriOwnerPackageName =
                requestInternal.getClientRequest().getTargetPackageName();
        final int uriOwnerUid =
                mPackageManagerInternal.getPackageUid(
                        uriOwnerPackageName, /* flags= */ 0, /* userId= */ targetUserId);

        final long ident = Binder.clearCallingIdentity();
        try {
            final int uriGrantsSize = response.getUriGrants().size();
            for (int i = 0; i < uriGrantsSize; i++) {
                final AppFunctionUriGrant uriGrant = response.getUriGrants().get(i);
                if (!ContentResolver.SCHEME_CONTENT.equals(uriGrant.getUri().getScheme())) continue;

                final String uriReceiverPackageName = requestInternal.getCallingPackage();
                final int uriOwnerUserid =
                        ContentProvider.getUserIdFromUri(
                                uriGrant.getUri(), UserHandle.getUserId(uriOwnerUid));
                mUriGrantsManager.grantUriPermissionFromOwner(
                        mPermissionOwner,
                        uriOwnerUid,
                        uriReceiverPackageName,
                        ContentProvider.getUriWithoutUserId(uriGrant.getUri()),
                        uriGrant.getModeFlags(),
                        uriOwnerUserid,
                        uriReceiverUserId);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Granting URI permissions failed", e);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private static void reportException(
            @NonNull IAppFunctionEnabledCallback callback, @NonNull Exception exception) {
        try {
            callback.onError(new ParcelableException(exception));
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to report the exception", e);
        }
    }

    /**
     * Sets the enabled status of a specified app function.
     *
     * <p>Required to hold a lock to call this function to avoid document changes during the
     * process.
     */
    @WorkerThread
    @GuardedBy("getLockForPackage(callingPackage)")
    private void setAppFunctionEnabledInternalLocked(
            @NonNull String callingPackage,
            @NonNull String functionIdentifier,
            @NonNull UserHandle userHandle,
            @AppFunctionManager.EnabledState int enabledState)
            throws Exception {
        AppSearchManager perUserAppSearchManager = getAppSearchManagerAsUser(userHandle);

        if (perUserAppSearchManager == null) {
            throw new IllegalStateException(
                    "AppSearchManager not found for user:" + userHandle.getIdentifier());
        }
        SearchContext runtimeMetadataSearchContext =
                new SearchContext.Builder(APP_FUNCTION_RUNTIME_METADATA_DB).build();

        try (FutureAppSearchSession runtimeMetadataSearchSession =
                new FutureAppSearchSessionImpl(
                        perUserAppSearchManager,
                        THREAD_POOL_EXECUTOR,
                        runtimeMetadataSearchContext)) {
            AppFunctionRuntimeMetadata existingMetadata =
                    new AppFunctionRuntimeMetadata(
                            getRuntimeMetadataGenericDocument(
                                    callingPackage,
                                    functionIdentifier,
                                    runtimeMetadataSearchSession));
            AppFunctionRuntimeMetadata newMetadata =
                    new AppFunctionRuntimeMetadata.Builder(existingMetadata)
                            .setEnabled(enabledState)
                            .build();
            AppSearchBatchResult<String, Void> putDocumentBatchResult =
                    runtimeMetadataSearchSession
                            .put(
                                    new PutDocumentsRequest.Builder()
                                            .addGenericDocuments(newMetadata)
                                            .build())
                            .get();
            if (!putDocumentBatchResult.isSuccess()) {
                throw new IllegalStateException(
                        "Failed writing updated doc to AppSearch due to " + putDocumentBatchResult);
            }
        }
    }

    @WorkerThread
    @NonNull
    private AppFunctionRuntimeMetadata getRuntimeMetadataGenericDocument(
            @NonNull String packageName,
            @NonNull String functionId,
            @NonNull FutureAppSearchSession runtimeMetadataSearchSession)
            throws Exception {
        String documentId =
                AppFunctionRuntimeMetadata.getDocumentIdForAppFunction(packageName, functionId);
        GetByDocumentIdRequest request =
                new GetByDocumentIdRequest.Builder(APP_FUNCTION_RUNTIME_NAMESPACE)
                        .addIds(documentId)
                        .build();
        AppSearchBatchResult<String, GenericDocument> result =
                runtimeMetadataSearchSession.getByDocumentId(request).get();
        if (result.isSuccess()) {
            return new AppFunctionRuntimeMetadata((result.getSuccesses().get(documentId)));
        }
        throw new IllegalArgumentException("Function " + functionId + " does not exist");
    }

    private void bindAppFunctionServiceUnchecked(
            @NonNull ExecuteAppFunctionAidlRequest requestInternal,
            @NonNull Intent serviceIntent,
            @NonNull UserHandle targetUser,
            @NonNull ICancellationSignal cancellationSignalTransport,
            @NonNull SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback,
            int bindFlags,
            @NonNull IBinder callerBinder,
            int callingUid) {
        CancellationSignal cancellationSignal =
                CancellationSignal.fromTransport(cancellationSignalTransport);
        ICancellationCallback cancellationCallback =
                new ICancellationCallback.Stub() {
                    @Override
                    public void sendCancellationTransport(
                            @NonNull ICancellationSignal cancellationTransport) {
                        cancellationSignal.setRemote(cancellationTransport);
                    }
                };
        boolean bindServiceResult =
                mRemoteServiceCaller.runServiceCall(
                        serviceIntent,
                        bindFlags,
                        targetUser,
                        mServiceConfig.getExecuteAppFunctionCancellationTimeoutMillis(),
                        cancellationSignal,
                        new RunAppFunctionServiceCallback(
                                requestInternal,
                                cancellationCallback,
                                safeExecuteAppFunctionCallback,
                                getPackageSigningInfo(
                                        targetUser,
                                        requestInternal.getCallingPackage(),
                                        callingUid)),
                        callerBinder);

        if (!bindServiceResult) {
            Slog.e(TAG, "Failed to bind to the AppFunctionService");
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_SYSTEM_ERROR,
                            "Failed to bind the AppFunctionService."));
        }
    }

    @NonNull
    private SigningInfo getPackageSigningInfo(
            @NonNull UserHandle targetUser, @NonNull String packageName, int uid) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(targetUser);

        if (uid == Process.ROOT_UID) {
            // root is not a package. It does not have a signing info.
            return new SigningInfo();
        }
        PackageInfo packageInfo;
        packageInfo =
                Objects.requireNonNull(
                        mPackageManagerInternal.getPackageInfo(
                                packageName,
                                PackageManager.GET_SIGNING_CERTIFICATES,
                                uid,
                                targetUser.getIdentifier()));
        return Objects.requireNonNull(packageInfo.signingInfo);
    }

    private AppSearchManager getAppSearchManagerAsUser(@NonNull UserHandle userHandle) {
        return mContext.createContextAsUser(userHandle, /* flags= */ 0)
                .getSystemService(AppSearchManager.class);
    }

    private AppFunctionException mapExceptionToExecuteAppFunctionResponse(Throwable e) {
        if (e instanceof CompletionException) {
            e = e.getCause();
        }
        int resultCode = AppFunctionException.ERROR_SYSTEM_ERROR;
        if (e instanceof AppFunctionNotFoundException) {
            resultCode = AppFunctionException.ERROR_FUNCTION_NOT_FOUND;
        } else if (e instanceof AppSearchException appSearchException) {
            resultCode =
                    mapAppSearchResultFailureCodeToExecuteAppFunctionResponse(
                            appSearchException.getResultCode());
        } else if (e instanceof SecurityException) {
            resultCode = AppFunctionException.ERROR_DENIED;
        } else if (e instanceof DisabledAppFunctionException) {
            resultCode = AppFunctionException.ERROR_DISABLED;
        }
        return new AppFunctionException(resultCode, e.getMessage());
    }

    private int mapAppSearchResultFailureCodeToExecuteAppFunctionResponse(int resultCode) {
        if (resultCode == AppSearchResult.RESULT_OK) {
            throw new IllegalArgumentException(
                    "This method can only be used to convert failure result codes.");
        }

        switch (resultCode) {
            case AppSearchResult.RESULT_NOT_FOUND:
                return AppFunctionException.ERROR_FUNCTION_NOT_FOUND;
            case AppSearchResult.RESULT_INVALID_ARGUMENT:
            case AppSearchResult.RESULT_INTERNAL_ERROR:
            case AppSearchResult.RESULT_SECURITY_ERROR:
                // fall-through
        }
        return AppFunctionException.ERROR_SYSTEM_ERROR;
    }

    private void registerAppSearchObserver(@NonNull TargetUser user) {
        AppSearchManager perUserAppSearchManager =
                mContext.createContextAsUser(user.getUserHandle(), /* flags= */ 0)
                        .getSystemService(AppSearchManager.class);
        if (perUserAppSearchManager == null) {
            Slog.d(TAG, "AppSearch Manager not found for user: " + user.getUserIdentifier());
            return;
        }
        FutureGlobalSearchSession futureGlobalSearchSession =
                new FutureGlobalSearchSession(perUserAppSearchManager, THREAD_POOL_EXECUTOR);
        AppFunctionMetadataObserver appFunctionMetadataObserver =
                new AppFunctionMetadataObserver(
                        user.getUserHandle(),
                        mContext.createContextAsUser(user.getUserHandle(), /* flags= */ 0));
        var unused =
                futureGlobalSearchSession
                        .registerObserverCallbackAsync(
                                "android",
                                new ObserverSpec.Builder().build(),
                                // AppFunctionMetadataObserver implements a simple callback that
                                // does not block and should be safe to run on any thread.
                                Runnable::run,
                                appFunctionMetadataObserver)
                        .whenComplete(
                                (voidResult, ex) -> {
                                    if (ex != null) {
                                        Slog.e(TAG, "Failed to register observer: ", ex);
                                    }
                                    futureGlobalSearchSession.close();
                                });
    }

    private void trySyncRuntimeMetadata(@NonNull TargetUser user) {
        MetadataSyncAdapter metadataSyncAdapter =
                MetadataSyncPerUser.getPerUserMetadataSyncAdapter(
                        user.getUserHandle(),
                        mContext.createContextAsUser(user.getUserHandle(), /* flags= */ 0));
        if (metadataSyncAdapter != null) {
            var unused =
                    metadataSyncAdapter
                            .submitSyncRequest()
                            .whenComplete(
                                    (isSuccess, ex) -> {
                                        if (ex != null || !isSuccess) {
                                            Slog.e(TAG, "Sync was not successful");
                                        }
                                    });
        }
    }

    /**
     * Retrieves the lock object associated with the given package name.
     *
     * <p>This method returns the lock object from the {@code mLocks} map if it exists. If no lock
     * is found for the given package name, a new lock object is created, stored in the map, and
     * returned.
     */
    @VisibleForTesting
    @NonNull
    Object getLockForPackage(String callingPackage) {
        // Synchronized the access to mLocks to prevent race condition.
        synchronized (mLocks) {
            // By using a WeakHashMap, we allow the garbage collector to reclaim memory by removing
            // entries associated with unused callingPackage keys. Therefore, we remove the null
            // values before getting/computing a new value. The goal is to not let the size of this
            // map grow without an upper bound.
            mLocks.values().removeAll(Collections.singleton(null)); // Remove null values
            return mLocks.computeIfAbsent(callingPackage, k -> new Object());
        }
    }

    /**
     * Returns a new {@link SafeOneTimeExecuteAppFunctionCallback} initialized with a {@link
     * SafeOneTimeExecuteAppFunctionCallback.CompletionCallback} that logs the results and a {@link
     * SafeOneTimeExecuteAppFunctionCallback.BeforeCompletionCallback} that grants the temporary uri
     * permission to caller.
     */
    @VisibleForTesting
    SafeOneTimeExecuteAppFunctionCallback initializeSafeExecuteAppFunctionCallback(
            @NonNull ExecuteAppFunctionAidlRequest requestInternal,
            @NonNull IExecuteAppFunctionCallback executeAppFunctionCallback,
            int callingUid) {
        return new SafeOneTimeExecuteAppFunctionCallback(
                executeAppFunctionCallback,
                new SafeOneTimeExecuteAppFunctionCallback.BeforeCompletionCallback() {
                    @Override
                    public void beforeOnSuccess(@NonNull ExecuteAppFunctionResponse result) {
                        grantTemporaryUriPermissions(requestInternal, result, callingUid);
                    }
                },
                new SafeOneTimeExecuteAppFunctionCallback.CompletionCallback() {
                    @Override
                    public void finalizeOnSuccess(
                            @NonNull ExecuteAppFunctionResponse result,
                            long executionStartTimeMillis) {
                        mLoggerWrapper.logAppFunctionSuccess(
                                requestInternal, result, callingUid, executionStartTimeMillis);
                        recordAppFunctionAccess(requestInternal);
                    }

                    @Override
                    public void finalizeOnError(
                            @NonNull AppFunctionException error, long executionStartTimeMillis) {
                        mLoggerWrapper.logAppFunctionError(
                                requestInternal,
                                error.getErrorCode(),
                                callingUid,
                                executionStartTimeMillis);
                        recordAppFunctionAccess(requestInternal);
                    }
                });
    }

    private void recordAppFunctionAccess(@NonNull ExecuteAppFunctionAidlRequest aidlRequest) {
        if (!accessCheckFlagsEnabled()) return;
        final long duration = SystemClock.elapsedRealtime() - aidlRequest.getRequestTime();
        final long accessTime = aidlRequest.getRequestWallTime();
        mBackgroundExecutor.execute(
                () -> {
                    try {
                        mMultiUserAppFunctionAccessHistory
                                .asUser(aidlRequest.getUserHandle().getIdentifier())
                                .insertAppFunctionAccessHistory(aidlRequest, accessTime, duration);
                    } catch (IllegalStateException e) {
                        Slog.e(
                                TAG,
                                "Fail to insert new access history to user "
                                        + aidlRequest.getUserHandle().getIdentifier(),
                                e);
                    }
                });
    }

    private static class AppFunctionMetadataObserver implements ObserverCallback {
        @Nullable private final MetadataSyncAdapter mPerUserMetadataSyncAdapter;

        AppFunctionMetadataObserver(@NonNull UserHandle userHandle, @NonNull Context userContext) {
            mPerUserMetadataSyncAdapter =
                    MetadataSyncPerUser.getPerUserMetadataSyncAdapter(userHandle, userContext);
        }

        @Override
        public void onDocumentChanged(@NonNull DocumentChangeInfo documentChangeInfo) {
            if (mPerUserMetadataSyncAdapter == null) {
                return;
            }
            if (documentChangeInfo
                            .getDatabaseName()
                            .equals(AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_METADATA_DB)
                    && documentChangeInfo
                            .getNamespace()
                            .equals(
                                    AppFunctionStaticMetadataHelper
                                            .APP_FUNCTION_STATIC_NAMESPACE)) {
                var unused = mPerUserMetadataSyncAdapter.submitSyncRequest();
            }
        }

        @Override
        public void onSchemaChanged(@NonNull SchemaChangeInfo schemaChangeInfo) {
            if (mPerUserMetadataSyncAdapter == null) {
                return;
            }
            if (schemaChangeInfo
                    .getDatabaseName()
                    .equals(AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_METADATA_DB)) {
                boolean shouldInitiateSync = false;
                for (String schemaName : schemaChangeInfo.getChangedSchemaNames()) {
                    if (schemaName.startsWith(AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)) {
                        shouldInitiateSync = true;
                        break;
                    }
                }
                if (shouldInitiateSync) {
                    var unused = mPerUserMetadataSyncAdapter.submitSyncRequest();
                }
            }
        }
    }

    /** Throws when executing a disabled app function. */
    private static class DisabledAppFunctionException extends RuntimeException {
        private DisabledAppFunctionException(@NonNull String errorMessage) {
            super(errorMessage);
        }
    }
}
