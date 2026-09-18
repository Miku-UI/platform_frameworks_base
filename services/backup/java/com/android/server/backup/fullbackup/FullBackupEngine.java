/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.backup.fullbackup;

import static com.android.server.backup.BackupManagerService.DEBUG;
import static com.android.server.backup.BackupManagerService.TAG;

import android.annotation.UserIdInt;
import android.app.ApplicationThreadConstants;
import android.app.IBackupAgent;
import android.app.backup.BackupAgent;
import android.app.backup.BackupManagerMonitor;
import android.app.backup.BackupTransport;
import android.app.backup.FullBackup;
import android.app.backup.FullBackupDataOutput;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Slog;

import com.android.server.AppWidgetBackupBridge;
import com.android.server.backup.BackupAgentTimeoutParameters;
import com.android.server.backup.BackupRestoreTask;
import com.android.server.backup.Flags;
import com.android.server.backup.OperationStorage.OpType;
import com.android.server.backup.UserBackupManagerService;
import com.android.server.backup.remote.RemoteCall;
import com.android.server.backup.utils.BackupEligibilityRules;
import com.android.server.backup.utils.BackupManagerMonitorEventSender;
import com.android.server.backup.utils.FullBackupUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Core logic for performing one package's full backup, gathering the tarball from the application
 * and emitting it to the designated OutputStream.
 */
public class FullBackupEngine {
    private final UserBackupManagerService backupManagerService;
    private final OutputStream mOutput;
    private final FullBackupPreflight mPreflightHook;
    private final BackupRestoreTask mTimeoutMonitor;
    private IBackupAgent mAgent;
    private final PackageInfo mPkg;
    private final long mQuota;
    private final int mOpToken;
    private final int mTransportFlags;
    private final BackupAgentTimeoutParameters mAgentTimeoutParameters;
    private final BackupEligibilityRules mBackupEligibilityRules;
    private final BackupManagerMonitorEventSender mBackupManagerMonitorEventSender;

    class FullBackupRunner implements Runnable {
        private final @UserIdInt int mUserId;
        private final PackageManager mPackageManager;
        private final PackageInfo mPackage;
        private final IBackupAgent mAgent;
        private final ParcelFileDescriptor mPipe;
        private final int mToken;
        private final File mFilesDir;

        FullBackupRunner(
                UserBackupManagerService userBackupManagerService,
                PackageInfo packageInfo,
                IBackupAgent agent,
                ParcelFileDescriptor pipe,
                int token)
                throws IOException {
            mUserId = userBackupManagerService.getUserId();
            mPackageManager = backupManagerService.getPackageManager();
            mPackage = packageInfo;
            mAgent = agent;
            mPipe = ParcelFileDescriptor.dup(pipe.getFileDescriptor());
            mToken = token;
            mFilesDir = userBackupManagerService.getDataDir();
        }

        @Override
        public void run() {
            try {
                FullBackupDataOutput output =
                        new FullBackupDataOutput(mPipe, /* quota */ -1, mTransportFlags);
                AppMetadataBackupWriter appMetadataBackupWriter =
                        new AppMetadataBackupWriter(output, mPackageManager, mPackage, mFilesDir);

                String packageName = mPackage.packageName;

                if (DEBUG) {
                    Slog.d(TAG, "Writing manifest for " + packageName);
                }

                appMetadataBackupWriter.backupManifest();

                // Write widget data.
                byte[] widgetData = AppWidgetBackupBridge.getWidgetState(packageName, mUserId);
                if (widgetData != null && widgetData.length > 0) {
                    appMetadataBackupWriter.backupWidget(widgetData);
                }

                if (Flags.enableCrossPlatformTransfer()
                        && (mTransportFlags & BackupAgent.FLAG_CROSS_PLATFORM_DATA_TRANSFER_IOS)
                                != 0) {
                    appMetadataBackupWriter.backupCrossPlatformManifest(mBackupEligibilityRules);
                }

                Slog.d(TAG, "Calling doFullBackup() on " + packageName);

                backupManagerService.prepareOperationTimeout(
                        mToken,
                        mAgentTimeoutParameters.getFullBackupAgentTimeoutMillis(),
                        mTimeoutMonitor /* in parent class */,
                        OpType.BACKUP_WAIT);
                mAgent.doFullBackup(
                        mPipe,
                        mQuota,
                        mToken,
                        backupManagerService.getBackupManagerBinder(),
                        mTransportFlags);
            } catch (IOException e) {
                Slog.e(TAG, "Error running full backup for " + mPackage.packageName, e);
            } catch (RemoteException e) {
                Slog.e(
                        TAG,
                        "Remote agent vanished during full backup of " + mPackage.packageName,
                        e);
            } finally {
                try {
                    mPipe.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public FullBackupEngine(
            UserBackupManagerService backupManagerService,
            OutputStream output,
            FullBackupPreflight preflightHook,
            PackageInfo pkg,
            BackupRestoreTask timeoutMonitor,
            long quota,
            int opToken,
            int transportFlags,
            BackupEligibilityRules backupEligibilityRules,
            BackupManagerMonitorEventSender backupManagerMonitorEventSender) {
        this.backupManagerService = backupManagerService;
        mOutput = output;
        mPreflightHook = preflightHook;
        mPkg = pkg;
        mTimeoutMonitor = timeoutMonitor;
        mQuota = quota;
        mOpToken = opToken;
        mTransportFlags = transportFlags;
        mAgentTimeoutParameters =
                Objects.requireNonNull(
                        backupManagerService.getAgentTimeoutParameters(),
                        "Timeout parameters cannot be null");
        mBackupEligibilityRules = backupEligibilityRules;
        mBackupManagerMonitorEventSender = backupManagerMonitorEventSender;
    }

    public int preflightCheck() throws RemoteException {
        if (initializeAgent()) {
            int result = mPreflightHook.preflightFullBackup(mPkg, mAgent);
            if (DEBUG) {
                Slog.v(TAG, "preflight returned " + result);
            }

            // Clear any logs generated during preflight
            mAgent.clearBackupRestoreEventLogger();
            return result;
        } else {
            Slog.w(TAG, "Unable to bind to full agent for " + mPkg.packageName);
            return BackupTransport.AGENT_ERROR;
        }
    }

    public int backupOnePackage() throws RemoteException {
        int result = BackupTransport.AGENT_ERROR;

        if (initializeAgent()) {
            ParcelFileDescriptor[] pipes = null;
            try {
                pipes = ParcelFileDescriptor.createPipe();

                FullBackupRunner runner =
                        new FullBackupRunner(
                                backupManagerService,
                                mPkg,
                                mAgent,
                                pipes[1],
                                mOpToken);
                pipes[1].close(); // the runner has dup'd it
                pipes[1] = null;
                Thread t = new Thread(runner, "app-data-runner");
                t.start();

                FullBackupUtils.routeSocketDataToOutput(pipes[0], mOutput);

                if (!backupManagerService.waitUntilOperationComplete(mOpToken)) {
                    Slog.e(TAG, "Full backup failed on package " + mPkg.packageName);
                } else {
                    if (DEBUG) {
                        Slog.d(TAG, "Full package backup success: " + mPkg.packageName);
                    }
                    result = BackupTransport.TRANSPORT_OK;
                }

                mBackupManagerMonitorEventSender.monitorAgentLoggingResults(mPkg, mAgent);
            } catch (IOException e) {
                Slog.e(TAG, "Error backing up " + mPkg.packageName + ": " + e.getMessage());
                // This is likely due to the app process dying.
                mBackupManagerMonitorEventSender.monitorEvent(
                        BackupManagerMonitor.LOG_EVENT_ID_FULL_BACKUP_AGENT_PIPE_BROKEN,
                        mPkg,
                        BackupManagerMonitor.LOG_EVENT_CATEGORY_AGENT,
                        /* extras= */ null);
                result = BackupTransport.AGENT_ERROR;
            } finally {
                try {
                    // flush after every package
                    mOutput.flush();
                    if (pipes != null) {
                        if (pipes[0] != null) {
                            pipes[0].close();
                        }
                        if (pipes[1] != null) {
                            pipes[1].close();
                        }
                    }
                } catch (IOException e) {
                    Slog.w(TAG, "Error bringing down backup stack");
                    result = BackupTransport.TRANSPORT_ERROR;
                }
            }
        } else {
            Slog.w(TAG, "Unable to bind to full agent for " + mPkg.packageName);
        }
        tearDown();
        return result;
    }

    public void sendQuotaExceeded(long backupDataBytes, long quotaBytes) {
        if (initializeAgent()) {
            try {
                RemoteCall.execute(
                        callback -> mAgent.doQuotaExceeded(backupDataBytes, quotaBytes, callback),
                        mAgentTimeoutParameters.getQuotaExceededTimeoutMillis());
            } catch (RemoteException e) {
                Slog.e(TAG, "Remote exception while telling agent about quota exceeded");
            }
        }
    }

    private boolean initializeAgent() {
        if (mAgent == null) {
            if (DEBUG) {
                Slog.d(TAG, "Binding to full backup agent : " + mPkg.packageName);
            }
            mAgent =
                    backupManagerService.getBackupAgentConnectionManager().bindToAgentSynchronous(
                            mPkg.applicationInfo, ApplicationThreadConstants.BACKUP_MODE_FULL,
                            mBackupEligibilityRules.getBackupDestination());
        }
        return mAgent != null;
    }

    private void tearDown() {
        if (mPkg != null) {
            backupManagerService.getBackupAgentConnectionManager().unbindAgent(
                    mPkg.applicationInfo, /* allowKill= */ true);
        }
    }
}
