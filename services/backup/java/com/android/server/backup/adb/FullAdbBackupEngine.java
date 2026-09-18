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

package com.android.server.backup.adb;

import static com.android.server.backup.BackupManagerService.DEBUG;
import static com.android.server.backup.BackupManagerService.TAG;
import static com.android.server.backup.UserBackupManagerService.BACKUP_MANIFEST_FILENAME;
import static com.android.server.backup.UserBackupManagerService.BACKUP_METADATA_FILENAME;
import static com.android.server.backup.UserBackupManagerService.CROSS_PLATFORM_MANIFEST_FILENAME;
import static com.android.server.backup.UserBackupManagerService.SHARED_BACKUP_AGENT_PACKAGE;
import static com.android.server.backup.crossplatform.PlatformConfigParser.PLATFORM_IOS;

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
import com.android.server.backup.crossplatform.CrossPlatformManifest;
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
 * Core logic for performing one package's full backup for `adb backup`, gathering the tarball from
 * the application and emitting it to the designated OutputStream.
 */
public class FullAdbBackupEngine {
    private UserBackupManagerService backupManagerService;
    private OutputStream mOutput;
    private BackupRestoreTask mTimeoutMonitor;
    private IBackupAgent mAgent;
    private boolean mIncludeApks;
    private final PackageInfo mPkg;
    private final int mOpToken;
    private final BackupAgentTimeoutParameters mAgentTimeoutParameters;
    private final BackupEligibilityRules mBackupEligibilityRules;

    class FullAdbBackupRunner implements Runnable {
        private final @UserIdInt int mUserId;
        private final PackageManager mPackageManager;
        private final PackageInfo mPackage;
        private final IBackupAgent mAgent;
        private final ParcelFileDescriptor mPipe;
        private final int mToken;
        private final boolean mIncludeApks;
        private final File mFilesDir;

        FullAdbBackupRunner(
                UserBackupManagerService userBackupManagerService,
                PackageInfo packageInfo,
                IBackupAgent agent,
                ParcelFileDescriptor pipe,
                int token,
                boolean includeApks)
                throws IOException {
            mUserId = userBackupManagerService.getUserId();
            mPackageManager = backupManagerService.getPackageManager();
            mPackage = packageInfo;
            mAgent = agent;
            mPipe = ParcelFileDescriptor.dup(pipe.getFileDescriptor());
            mToken = token;
            mIncludeApks = includeApks;
            mFilesDir = userBackupManagerService.getDataDir();
        }

        @Override
        public void run() {
            try {
                FullBackupDataOutput output =
                        new FullBackupDataOutput(mPipe, /* quota */ -1, /* transportFlags= */ 0);
                AppMetadataAdbBackupWriter appMetadataAdbBackupWriter =
                        new AppMetadataAdbBackupWriter(output, mPackageManager);

                String packageName = mPackage.packageName;
                boolean isSharedStorage = SHARED_BACKUP_AGENT_PACKAGE.equals(packageName);
                boolean writeApk =
                        shouldWriteApk(mPackage.applicationInfo, mIncludeApks, isSharedStorage);

                if (!isSharedStorage) {
                    if (DEBUG) {
                        Slog.d(TAG, "Writing manifest for " + packageName);
                    }

                    File manifestFile = new File(mFilesDir, BACKUP_MANIFEST_FILENAME);
                    appMetadataAdbBackupWriter.backupManifest(
                            mPackage, manifestFile, mFilesDir, writeApk);
                    manifestFile.delete();

                    // Write widget data.
                    byte[] widgetData =
                            AppWidgetBackupBridge.getWidgetState(packageName, mUserId);
                    if (widgetData != null && widgetData.length > 0) {
                        File metadataFile = new File(mFilesDir, BACKUP_METADATA_FILENAME);
                        appMetadataAdbBackupWriter.backupWidget(
                                mPackage, metadataFile, mFilesDir, widgetData);
                        metadataFile.delete();
                    }
                }

                if (writeApk) {
                    appMetadataAdbBackupWriter.backupApk(mPackage);
                    appMetadataAdbBackupWriter.backupObb(mUserId, mPackage);
                }

                Slog.d(TAG, "Calling doFullBackup() on " + packageName);

                long timeout =
                        isSharedStorage
                                ? mAgentTimeoutParameters.getSharedBackupAgentTimeoutMillis()
                                : mAgentTimeoutParameters.getFullBackupAgentTimeoutMillis();
                backupManagerService.prepareOperationTimeout(
                        mToken,
                        timeout,
                        mTimeoutMonitor /* in parent class */,
                        OpType.BACKUP_WAIT);
                mAgent.doFullBackup(
                        mPipe,
                        /* quota */ Long.MAX_VALUE,
                        mToken,
                        backupManagerService.getBackupManagerBinder(),
                        /* transportFlags= */ 0);
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

        /**
         * Don't write apks for system-bundled apps that are not upgraded.
         */
        private boolean shouldWriteApk(
                ApplicationInfo applicationInfo, boolean includeApks, boolean isSharedStorage) {
            boolean isSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystemApp =
                    (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            return includeApks
                    && !isSharedStorage
                    && (!isSystemApp || isUpdatedSystemApp);
        }
    }

    public FullAdbBackupEngine(
            UserBackupManagerService backupManagerService,
            OutputStream output,
            PackageInfo pkg,
            boolean alsoApks,
            BackupRestoreTask timeoutMonitor,
            int opToken,
            BackupEligibilityRules backupEligibilityRules) {
        this.backupManagerService = backupManagerService;
        mOutput = output;
        mPkg = pkg;
        mIncludeApks = alsoApks;
        mTimeoutMonitor = timeoutMonitor;
        mOpToken = opToken;
        mAgentTimeoutParameters =
                Objects.requireNonNull(
                        backupManagerService.getAgentTimeoutParameters(),
                        "Timeout parameters cannot be null");
        mBackupEligibilityRules = backupEligibilityRules;
    }

    public void backupOnePackage() throws RemoteException {
        if (initializeAgent()) {
            ParcelFileDescriptor[] pipes = null;
            try {
                pipes = ParcelFileDescriptor.createPipe();

                FullAdbBackupRunner runner =
                        new FullAdbBackupRunner(
                                backupManagerService,
                                mPkg,
                                mAgent,
                                pipes[1],
                                mOpToken,
                                mIncludeApks);
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
                }
            } catch (IOException e) {
                Slog.e(TAG, "Error backing up " + mPkg.packageName + ": " + e.getMessage());
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
                }
            }
        } else {
            Slog.w(TAG, "Unable to bind to full agent for " + mPkg.packageName);
        }
        tearDown();
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
