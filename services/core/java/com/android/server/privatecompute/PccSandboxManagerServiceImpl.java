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

package com.android.server.privatecompute;

import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_DATA_MIGRATION_STATE_CHANGED__MIGRATION_STATE__NON_PCC_DATA_MIGRATION_SERVICE_STARTED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_WRITE_TO_AUDIT_LOG__WRITE_TYPE__BATCHED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_WRITE_TO_AUDIT_LOG__WRITE_TYPE__DIRECT;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_WRITE_TO_AUDIT_LOG__WRITE_TYPE__NATIVE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.privatecompute.DataMigrationToPccService;
import android.app.privatecompute.IDataMigrationToPccService;
import android.app.privatecompute.IMigrationRequestResultReceiver;
import android.app.privatecompute.IMigrationRequestResultSender;
import android.app.privatecompute.IPccSandboxManager;
import android.app.privatecompute.IPccSandboxManagerNative;
import android.app.privatecompute.MigrationException;
import android.app.privatecompute.MigrationRequestResult;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.ShellCommand;
import android.os.SystemClock;
import android.os.UserHandle;
import android.sysprop.PccProperties;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
  * Implementation of the {@link IPccSandboxManager} binder service.
  */
public class PccSandboxManagerServiceImpl extends IPccSandboxManager.Stub {

    private static final String TAG = "PccSandboxManagerServiceImpl";

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static final long AUDIT_LOG_CLEANUP_INTERVAL_MS = 12 * 60 * 60 * 1000L; // 12 hours

    private final Context mContext;
    private final PackageManagerInternal mPackageManagerInternal;
    private final Injector mInjector;

    // Only instantiated when audit mode is enabled.
    @GuardedBy("mAuditModeLock")
    private final SparseArray<AuditModeContext> mAuditModeContexts =
            new SparseArray<>();

    private final Object mAuditModeLock = new Object();
    private final ExecutorService mExecutorService;

    private PccSandboxManagerInternal mInternal;
    private final PccSandboxManagerNativeImpl mNativeImpl = new PccSandboxManagerNativeImpl();

    private final AlarmManager.OnAlarmListener mAuditLogCleanupListener;

    private final BroadcastReceiver mUserUnlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL);
                if (userId != UserHandle.USER_NULL) {
                    mExecutorService.execute(() -> {
                        mInjector.deleteAuditLogFiles(userId);
                    });
                }
            }
        }
    };

    @RequiresPermission(android.Manifest.permission.INTERACT_ACROSS_USERS_FULL)
    public PccSandboxManagerServiceImpl(Context context) {
        this(context, new Injector());
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    @RequiresPermission(android.Manifest.permission.INTERACT_ACROSS_USERS_FULL)
    public PccSandboxManagerServiceImpl(Context context, Injector injector) {
        mContext = context;
        mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
        mInjector = injector;
        mExecutorService = mInjector.getExecutorService();
        mAuditLogCleanupListener = () -> mExecutorService.execute(this::runAuditLogCleanupTask);

        // Data retention: Delete audit log files when the user is first unlocked, after boot.
        mContext.registerReceiverForAllUsers(
                mUserUnlockedReceiver,
                new IntentFilter(Intent.ACTION_USER_UNLOCKED),
                /* broadcastPermission= */ null,
                mInjector.getHandler(mInjector.getBackgroundLooper()));
    }

    private void runAuditLogCleanupTask() {
        final long token = Binder.clearCallingIdentity();
        try {
            mInjector.deleteAuditLogFilesAllUsers();
            rescheduleAuditLogCleanup();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void rescheduleAuditLogCleanup() {
        AlarmManager am = mInjector.getAlarmManager(mContext);
        if (am == null) {
            return;
        }
        // Cancel any existing alarm to avoid duplicate alarms.
        am.cancel(mAuditLogCleanupListener);
        long triggerAtMillis = mInjector.getElapsedRealtime() + AUDIT_LOG_CLEANUP_INTERVAL_MS;
        am.set(
                AlarmManager.ELAPSED_REALTIME,
                triggerAtMillis,
                TAG,
                mAuditLogCleanupListener,
                mInjector.getHandler(mInjector.getBackgroundLooper()));
    }

    @VisibleForTesting
    static class Injector {
        int getCallingUid() {
            return Binder.getCallingUid();
        }

        Handler getHandler(Looper looper) {
            return new Handler(looper);
        }

        boolean auditModeEnabled() {
            return PccProperties.audit_mode_enabled().orElse(false);
        }

        AlarmManager getAlarmManager(Context context) {
            return context.getSystemService(AlarmManager.class);
        }

        Looper getBackgroundLooper() {
            return BackgroundThread.get().getLooper();
        }

        long getElapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }

        ExecutorService getExecutorService() {
            return Executors.newSingleThreadExecutor();
        }

        File getAuditLogFilesDirectory(int userId) {
            return new File(Environment.getDataMiscCeDirectory(userId),
                    AuditModeContext.AUDIT_LOG_FILES_DIRNAME);
        }

        void deleteAuditLogFiles(int userId) {
            AuditModeContext.deleteAuditLogFiles(getAuditLogFilesDirectory(userId));
        }

        void deleteAuditLogFilesAllUsers() {
            UserManagerInternal umi = LocalServices.getService(UserManagerInternal.class);
            if (umi != null) {
                for (int userId : umi.getUserIds()) {
                    // This conditions is to avoid deleting audit log files for a locked user,
                    // and triggering StrictMode violations.
                    if (umi.isUserUnlockingOrUnlocked(userId)) {
                        AuditModeContext.deleteAuditLogFiles(getAuditLogFilesDirectory(userId));
                    }
                }
            }
        }
    }

    public void setPccSandboxManagerInternal(PccSandboxManagerInternal internal) {
        mInternal = internal;
    }

    public ExecutorService getExecutorService() {
        return mExecutorService;
    }

    public IBinder getNativeBinder() {
        return mNativeImpl;
    }

    @Override
    @RequiresNoPermission
    public boolean isPrivateComputeServicesUid(int uid) {
        // Private Compute Services packages must be assigned from Application
        // UID range.
        if (!Process.isApplicationUid(uid)) {
            return false;
        }

        PackageManager pm = mContext.getPackageManager();
        final String[] packagesForUid = pm.getPackagesForUid(uid);
        if (packagesForUid == null || packagesForUid.length == 0) {
            return false;
        }

        for (String packageName : packagesForUid) {
            if (pm.checkPermission(
                    android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                    packageName) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }

        return false;
    }

    @Override
    @RequiresNoPermission
    public boolean isPccTrustedSystemComponent(int uid, String packageName) {
        if (mInternal == null) {
            return false;
        }
        return mInternal.isPccTrustedSystemComponent(uid, packageName);
    }

    @Override
    @RequiresNoPermission
    public void writeToAuditLog(@NonNull PersistableBundle bundle, @NonNull String packageName) {
        try {
            PrivateComputeStatsLogUtil.logPccWriteToAuditLog(
                    PCC_WRITE_TO_AUDIT_LOG__WRITE_TYPE__DIRECT);
            writeToAuditLogInternal(bundle, packageName);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to write to audit log: " + e);
            // No feedback is given to the app.
        }
    }

    @Override
    @RequiresNoPermission
    public void batchWriteToAuditLog(
            @NonNull List<PersistableBundle> data, @NonNull String packageName) {
        try {
            PrivateComputeStatsLogUtil.logPccWriteToAuditLog(
                    PCC_WRITE_TO_AUDIT_LOG__WRITE_TYPE__BATCHED);
            writeToAuditLogInternal(data, packageName);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to batch write to audit log: " + e);
            // No feedback is given to the app.
        }
    }

    /**
     * Internal method with feedback to the caller, for testing. Returns true if the write was
     * successfully scheduled.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    boolean writeToAuditLogInternal(@NonNull PersistableBundle bundle, @NonNull String packageName)
            throws SecurityException {
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(bundle);
        return writeToAuditLogInternal(data, packageName);
    }

    /**
     * Internal method with feedback to the caller, for testing. Returns true if the write was
     * successfully scheduled.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    boolean writeToAuditLogInternal(
            @NonNull List<PersistableBundle> data, @NonNull String packageName)
            throws SecurityException {
        final int callingUid = mInjector.getCallingUid();
        final int userId = UserHandle.getUserId(callingUid);
        if (!mPackageManagerInternal.isSameApp(packageName, callingUid, userId)) {
            // We don't report the security exception to apps, but we log it.
            throw new SecurityException(
                    "Package name " + packageName + " does not match calling UID " + callingUid);
        }

        if (!Process.isPrivateComputeCoreUid(callingUid)
                && !isPccTrustedSystemComponent(callingUid, packageName)) {
            return false;
        }

        synchronized (mAuditModeLock) {
            if (!mInjector.auditModeEnabled()) {
                // If audit mode was toggled off, clean up, including writing pending data to disk.
                if (mAuditModeContexts.size() > 0) {
                    for (int i = 0; i < mAuditModeContexts.size(); i++) {
                        mAuditModeContexts.valueAt(i).stopAuditing();
                    }
                    mAuditModeContexts.clear();
                    runAuditLogCleanupTask();
                }
                return false;
            }
            AuditModeContext context = mAuditModeContexts.get(userId);
            if (context == null) {
                // When we start auditing for the first user, clean up any old audit log files.
                if (mAuditModeContexts.size() == 0) {
                    runAuditLogCleanupTask();
                }
                context =
                        AuditModeContext.create(
                                userId, mInjector.getAuditLogFilesDirectory(userId));
                mAuditModeContexts.put(userId, context);
            }
            for (PersistableBundle bundle : data) {
                context.writeToAuditLog(bundle, packageName, callingUid);
            }
        }
        return true;
    }

    private class PccSandboxManagerNativeImpl extends IPccSandboxManagerNative.Stub {
        @Override
        @RequiresNoPermission
        public void writeToAuditLog(@NonNull PersistableBundle bundle) {
            String packageName = mContext.getPackageManager().getNameForUid(Binder.getCallingUid());
            PrivateComputeStatsLogUtil.logPccWriteToAuditLog(
                    PCC_WRITE_TO_AUDIT_LOG__WRITE_TYPE__NATIVE);
            writeToAuditLogInternal(bundle, packageName);
        }
    }

    @Override
    @RequiresNoPermission
    public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
            String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
        (new Shell()).exec(this, in, out, err, args, callback, resultReceiver);
    }

    private class Shell extends ShellCommand {
        @Override
        @RequiresNoPermission
        public int onCommand(String cmd) {
            if (cmd == null) {
                return handleDefaultCommands(cmd);
            }
            final PrintWriter pw = getOutPrintWriter();
            switch (cmd) {
                case "add-allowed-package" -> {
                    final int callingUid = mInjector.getCallingUid();
                    if (callingUid != Process.ROOT_UID && callingUid != Process.SHELL_UID) {
                        pw.println("Error: must be root or shell to use this command");
                        return -1;
                    }
                    final String packageName = getNextArgRequired();
                    if (mInternal != null) {
                        mInternal.addTestAllowedPackage(packageName);
                        pw.println("Added " + packageName + " to allowed packages");
                    }
                    return 0;
                }
                case "remove-allowed-package" -> {
                    final int callingUid = mInjector.getCallingUid();
                    if (callingUid != Process.ROOT_UID && callingUid != Process.SHELL_UID) {
                        pw.println("Error: must be root or shell to use this command");
                        return -1;
                    }
                    final String packageName = getNextArgRequired();
                    if (mInternal != null) {
                        mInternal.removeTestAllowedPackage(packageName);
                        pw.println("Removed " + packageName + " from allowed packages");
                    }
                    return 0;
                }
                case "enable-trust-instrumented-clients" -> {
                    final int callingUid = mInjector.getCallingUid();
                    if (callingUid != Process.ROOT_UID && callingUid != Process.SHELL_UID) {
                        pw.println("Error: must be root or shell to use this command");
                        return -1;
                    }
                    if (mInternal != null) {
                        mInternal.setTrustInstrumentedClients(true);
                        pw.println("Enabled trusting instrumented clients");
                    }
                    return 0;
                }
                case "disable-trust-instrumented-clients" -> {
                    final int callingUid = mInjector.getCallingUid();
                    if (callingUid != Process.ROOT_UID && callingUid != Process.SHELL_UID) {
                        pw.println("Error: must be root or shell to use this command");
                        return -1;
                    }
                    if (mInternal != null) {
                        mInternal.setTrustInstrumentedClients(false);
                        pw.println("Disabled trusting instrumented clients");
                    }
                    return 0;
                }
                case "audit-start" -> {
                    synchronized (mAuditModeLock) {
                        setAuditModeEnabled(pw, true);
                    }
                    return 0;
                }
                case "audit-stop" -> {
                    synchronized (mAuditModeLock) {
                        setAuditModeEnabled(pw, false);
                        for (int i = 0; i < mAuditModeContexts.size(); i++) {
                            mAuditModeContexts.valueAt(i).stopAuditing();
                        }
                        mAuditModeContexts.clear();
                    }
                    return 0;
                }
                case "read-intelligence-audit-log" -> {
                    // We check if the device is locked to force the user to input their LSKF
                    // when changing users. Otherwise, a user could `am switch-user` to a different
                    // user and read the audit log without unlocking the device for the target user.
                    KeyguardManager keyguardManager =
                            mContext.getSystemService(KeyguardManager.class);
                    if (keyguardManager == null) {
                        pw.println("Error: cannot get KeyguardManager.");
                        return -1;
                    }
                    if (keyguardManager.isKeyguardLocked()) {
                        pw.println("Please unlock your device to read the audit log.");
                        return -1;
                    }

                    if (PccProperties.audit_mode_enabled().orElse(true)) {
                        pw.println(
                                "Warning: Audit in progress. Results may be incomplete. Call"
                                    + " 'audit-stop' to save buffers before reading.");
                    }

                    final int userId = ActivityManager.getCurrentUser();
                    List<AuditLogEntry> entries = AuditModeContext.readAuditLogs(
                            mInjector.getAuditLogFilesDirectory(userId), userId);
                    if (entries.isEmpty()) {
                        pw.println("No audit logs found for user " + userId);
                        return 0;
                    }
                    pw.println("Found " + entries.size() + " log entries:");
                    DateTimeFormatter formatter =
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                                    .withZone(ZoneId.systemDefault());
                    for (AuditLogEntry entry : entries) {
                        Instant instant = Instant.ofEpochMilli(entry.mCurrentTimeMillis);
                        String humanReadableTime = formatter.format(instant);
                        pw.println(
                                "  Entry: realtime_nanos="
                                        + entry.mRealTimeNanos
                                        + " current_time="
                                        + humanReadableTime
                                        + " uid="
                                        + entry.mCallingUid
                                        + " package="
                                        + entry.mCallingPackage);
                        pw.println("    Bundle: " + entry.getBundle().toString());
                    }
                    return 0;
                }
                default -> {
                    return handleDefaultCommands(cmd);
                }
            }
        }

        private void setAuditModeEnabled(PrintWriter pw, boolean enabled) {
            try {
                PccProperties.audit_mode_enabled(enabled);
                Optional<Boolean> value = PccProperties.audit_mode_enabled();
                if (value.isPresent() && value.get() == enabled) {
                    pw.println("Audit mode " + (enabled ? "enabled" : "disabled"));
                } else {
                    pw.println("Failed to " + (enabled ? "enable" : "disable") + " audit mode");
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Failed to set audit_mode_enabled sysprop", e);
            }
        }

        @Override
        public void onHelp() {
            final PrintWriter pw = getOutPrintWriter();
            pw.println("PccSandboxManager commands:");
            pw.println("  help");
            pw.println("    Print this help text.");
            pw.println("  add-allowed-package PACKAGE");
            pw.println("    Add a package to the list of allowed PCC packages for testing.");
            pw.println("  remove-allowed-package PACKAGE");
            pw.println("    Remove a package from the list of allowed PCC packages for testing.");
            pw.println("  enable-trust-instrumented-clients");
            pw.println("    Temporarily consider instrumented clients as trusted.");
            pw.println("  disable-trust-instrumented-clients");
            pw.println("    Stop considering instrumented clients as trusted.");
        }
    }

    /**
     * Starts a non-PCC process for data migration.
     */
    @Override
    @RequiresNoPermission
    public void startNonPccProcessForDataMigration(IMigrationRequestResultReceiver callback) {
        final int callingUid = mInjector.getCallingUid();
        final PackageManager pm = mContext.getPackageManager();
        final String[] packages = pm.getPackagesForUid(callingUid);
        if (packages == null || packages.length == 0) {
            try {
                callback.onError(MigrationException.ERROR_INVOCATION_FAILED,
                        "Could not find package for calling UID " + callingUid);
            } catch (RemoteException e) {
                // Ignore
            }
            return;
        }

        Intent intent = new Intent(DataMigrationToPccService.SERVICE_INTERFACE);
        ResolveInfo resolvedService = null;
        String targetPackage = null;

        int userId = UserHandle.getUserId(callingUid);

        for (String pkg : packages) {
            intent.setPackage(pkg);
            ResolveInfo ri = pm.resolveServiceAsUser(intent, 0, userId);
            if (ri != null && ri.serviceInfo != null) {
                if (pkg.equals(ri.serviceInfo.packageName)) {
                    resolvedService = ri;
                    targetPackage = pkg;
                    break;
                }
            }
        }

        if (resolvedService == null) {
            try {
                callback.onError(MigrationException.ERROR_INVOCATION_FAILED,
                        "No data migration service found for calling package.");
            } catch (RemoteException e) {
                // Ignore
            }
            return;
        }

        // Only non-PCC to PCC data migration is supported.
        if (resolvedService.serviceInfo.shouldRunInPccSandbox()) {
            try {
                callback.onError(MigrationException.ERROR_INVOCATION_FAILED,
                        "Data migration service " + resolvedService.serviceInfo.name
                                + " is marked as a PCC component");
            } catch (RemoteException e) {
                // Ignore
            }
            return;
        }

        if (!android.Manifest.permission.BIND_DATA_MIGRATION_FOR_PRIVATECOMPUTE.equals(
                resolvedService.serviceInfo.permission)) {
            try {
                callback.onError(MigrationException.ERROR_INVOCATION_FAILED,
                        "Service " + resolvedService.serviceInfo.name + " does not require "
                                + "android.permission.BIND_DATA_MIGRATION_FOR_PRIVATECOMPUTE");
            } catch (RemoteException e) {
                // Ignore
            }
            return;
        }

        Intent bindIntent = new Intent(DataMigrationToPccService.SERVICE_INTERFACE);
        bindIntent.setComponent(new ComponentName(targetPackage, resolvedService.serviceInfo.name));

        final long token = Binder.clearCallingIdentity();
        try {
            boolean bound = mContext.bindServiceAsUser(bindIntent,
                    new MigrationServiceConnection(mContext, mInjector, callback),
                    Context.BIND_AUTO_CREATE, UserHandle.getUserHandleForUid(callingUid));
            if (bound) {
                PrivateComputeStatsLogUtil.logPccDataMigrationStateChanged(
                        PCC_DATA_MIGRATION_STATE_CHANGED__MIGRATION_STATE__NON_PCC_DATA_MIGRATION_SERVICE_STARTED);
            } else {
                try {
                    callback.onError(MigrationException.ERROR_INVOCATION_FAILED,
                            "Failed to bind to service");
                } catch (RemoteException e) {
                    // Ignore
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private static class MigrationServiceConnection implements ServiceConnection {
        private final Context mContext;
        private final Injector mInjector;
        private final IMigrationRequestResultReceiver mCallback;
        private final AtomicBoolean mIsDone = new AtomicBoolean(false);
        private final Handler mHandler;
        private Runnable mTimeoutRunnable;

        MigrationServiceConnection(Context context, Injector injector,
                IMigrationRequestResultReceiver callback) {
            mContext = context;
            mInjector = injector;
            mCallback = callback;
            mHandler = mInjector.getHandler(BackgroundThread.get().getLooper());
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            IDataMigrationToPccService migrationService =
                    IDataMigrationToPccService.Stub.asInterface(service);

            // Unbind after a timeout.
            mTimeoutRunnable = () -> reportError(MigrationException.ERROR_TIMEOUT,
                    "Migration timed out");
            mHandler.postDelayed(mTimeoutRunnable,
                    DataMigrationToPccService.MIGRATION_TIMEOUT_MS);

            try {
                migrationService.onMigrationRequested(new IMigrationRequestResultSender.Stub() {
                    @Override
                    @RequiresNoPermission
                    public void sendResult(MigrationRequestResult result) {
                        try {
                            PccBundleSanitizationUtil.sanitizeBundle(result.getExtras());
                        } catch (IllegalArgumentException e) {
                            reportError(MigrationException.ERROR_INVOCATION_FAILED,
                                    "Failed to sanitize bundle: " + e.getMessage());
                            return;
                        }

                        reportResult(result);

                    }
                });
            } catch (RemoteException e) {
                reportError(MigrationException.ERROR_INVOCATION_FAILED,
                        "RemoteException during migration request");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public void onBindingDied(ComponentName name) {
            reportError(MigrationException.ERROR_INVOCATION_FAILED, "Binding died");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            reportError(MigrationException.ERROR_INVOCATION_FAILED, "Null binding");
        }

        private void reportResult(MigrationRequestResult result) {
            if (mIsDone.compareAndSet(false, true)) {
                mHandler.removeCallbacks(mTimeoutRunnable);
                try {
                    mCallback.onResult(result);
                } catch (RemoteException e) {
                    // Ignore
                }
                unbind();
            }
        }

        private void reportError(int errorCode, String errorMessage) {
            if (mIsDone.compareAndSet(false, true)) {
                if (mTimeoutRunnable != null) {
                    mHandler.removeCallbacks(mTimeoutRunnable);
                }
                try {
                    mCallback.onError(errorCode, errorMessage);
                } catch (RemoteException e) {
                    // Ignore
                }
                unbind();
            }
        }

        private void unbind() {
            try {
                mContext.unbindService(this);
            } catch (IllegalArgumentException e) {
                // Ignore if already unbound
            }
        }
    }
}
