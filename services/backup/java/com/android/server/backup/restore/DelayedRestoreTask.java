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

package com.android.server.backup.restore;

import static com.android.server.backup.BackupManagerService.DEBUG;
import static com.android.server.backup.BackupManagerService.TAG;
import static com.android.server.backup.internal.BackupHandler.MSG_BACKUP_RESTORE_STEP;
import static com.android.server.backup.internal.BackupHandler.MSG_RESTORE_OPERATION_TIMEOUT;

import android.app.ApplicationThreadConstants;
import android.app.IBackupAgent;
import android.app.backup.BackupAnnotations.BackupDestination;
import android.app.backup.BackupAnnotations.OperationType;
import android.app.backup.BackupManagerMonitor;
import android.app.backup.DelayedRestoreRequest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.util.Slog;

import com.android.server.backup.BackupRestoreTask;
import com.android.server.backup.OperationStorage;
import com.android.server.backup.OperationStorage.OpType;
import com.android.server.backup.UserBackupManagerService;
import com.android.server.backup.utils.BackupEligibilityRules;
import com.android.server.backup.utils.BackupManagerMonitorEventSender;
import com.android.server.backup.transport.BackupTransportClient;
import com.android.server.backup.transport.TransportConnection;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Queue;

public class DelayedRestoreTask implements BackupRestoreTask {
    private final DelayedRestoreRequest mRequest;
    private final Queue<String> mRequesterPackageNames;
    private final UserBackupManagerService mBackupManagerService;
    private final int mEphemeralOpToken;
    private final OperationStorage mOperationStorage;
    private final BackupEligibilityRules mBackupEligibilityRules;
    private final BackupManagerMonitorEventSender mBackupManagerMonitorEventSender;

    private TransportConnection mTransportConnection;
    private String mCurrentPackageName;
    private PackageInfo mCurrentPackageInfo;
    private File mNewStateFile;
    private ParcelFileDescriptor mNewStatePfd;

    public DelayedRestoreTask(
            DelayedRestoreRequest request,
            UserBackupManagerService backupManagerService,
            Queue<String> requesterPackageNames,
            OperationStorage operationStorage) {
        mRequest = request;
        mBackupManagerService = backupManagerService;
        mRequesterPackageNames = requesterPackageNames;
        // Backup destination is not relevant for delayed restores since we use a local cached data
        // that was cached during the initial restore pass, so we use the default value which is
        // CLOUD.
        mBackupEligibilityRules =
                backupManagerService.getEligibilityRulesForOperation(BackupDestination.CLOUD);
        mEphemeralOpToken = backupManagerService.generateRandomIntegerToken();
        mOperationStorage = operationStorage;
        mBackupManagerMonitorEventSender = new BackupManagerMonitorEventSender(/* monitor= */ null);
    }

    @Override
    public void execute() {
        Bundle monitoringExtras = addDelayedRestoreOperationTypeToEvent();
        mTransportConnection =
                mBackupManagerService
                        .getTransportManager()
                        .getCurrentTransportClient("DelayedRestoreTask.execute()");
        if (mTransportConnection == null) {
            Slog.w(TAG, "Transport connection is null, skipping delayed restore for "
                    + mRequest.getPackageName());
            mBackupManagerMonitorEventSender.monitorEvent(
                    BackupManagerMonitor.LOG_EVENT_ID_TRANSPORT_IS_NULL,
                    null,
                    BackupManagerMonitor.LOG_EVENT_CATEGORY_TRANSPORT,
                    monitoringExtras);
            cleanup(true);
            return;
        }
        try {
            BackupTransportClient transport =
                    mTransportConnection.connectOrThrow("DelayedRestoreTask.execute()");
            mBackupManagerMonitorEventSender.setMonitor(transport.getBackupManagerMonitor());

            String stateDirName =
                    mBackupManagerService
                            .getTransportManager()
                            .getTransportDirName(mTransportConnection.getTransportComponent());

            mCurrentPackageName = mRequesterPackageNames.poll();
            Slog.i(
                    TAG,
                    "Executing delayed restore with dependency on "
                            + mRequest.getPackageName()
                            + " for requester package: "
                            + mCurrentPackageName);
            mCurrentPackageInfo =
                    mBackupManagerService
                            .getPackageManager()
                            .getPackageInfoAsUser(
                                    mCurrentPackageName, 0, mBackupManagerService.getUserId());
            ApplicationInfo currentPackageAppInfo = mCurrentPackageInfo.applicationInfo;

            mBackupManagerMonitorEventSender.monitorEvent(
                    BackupManagerMonitor.LOG_EVENT_ID_START_PACKAGE_RESTORE,
                    mCurrentPackageInfo,
                    BackupManagerMonitor.LOG_EVENT_CATEGORY_BACKUP_MANAGER_POLICY,
                    monitoringExtras);

            IBackupAgent agent =
                    mBackupManagerService
                            .getBackupAgentConnectionManager()
                            .bindToAgentSynchronous(
                                    currentPackageAppInfo,
                                    ApplicationThreadConstants.BACKUP_MODE_RESTORE,
                                    mBackupEligibilityRules.getBackupDestination());
            long restoreAgentTimeoutMillis =
                    mBackupManagerService
                            .getAgentTimeoutParameters()
                            .getRestoreAgentTimeoutMillis(currentPackageAppInfo.uid);
            mBackupManagerService.prepareOperationTimeout(
                    mEphemeralOpToken, restoreAgentTimeoutMillis, this, OpType.RESTORE_WAIT);
            if (agent != null) {
                if (mBackupEligibilityRules.appGetsFullBackup(mCurrentPackageInfo)) {
                    mBackupManagerMonitorEventSender.monitorEvent(
                            BackupManagerMonitor.LOG_EVENT_ID_FULL_RESTORE,
                            mCurrentPackageInfo,
                            BackupManagerMonitor.LOG_EVENT_CATEGORY_BACKUP_MANAGER_POLICY,
                            monitoringExtras);
                    Slog.i(TAG, "Calling doDelayedFullRestore for package: " + mCurrentPackageName);
                    agent.doDelayedFullRestore(
                            mRequest,
                            mBackupManagerService.getBackupManagerBinder(),
                            mEphemeralOpToken);
                } else {
                    mBackupManagerMonitorEventSender.monitorEvent(
                            BackupManagerMonitor.LOG_EVENT_ID_KV_RESTORE,
                            mCurrentPackageInfo,
                            BackupManagerMonitor.LOG_EVENT_CATEGORY_BACKUP_MANAGER_POLICY,
                            monitoringExtras);
                    setupNewStateFile(stateDirName);
                    Slog.i(TAG, "Calling doDelayedRestore for package: " + mCurrentPackageName);
                    agent.doDelayedRestore(
                            mRequest,
                            mNewStatePfd,
                            mBackupManagerService.getBackupManagerBinder(),
                            currentPackageAppInfo.longVersionCode,
                            mEphemeralOpToken);
                }
            } else {
                Slog.w(TAG, "Failed to bind to agent for delayed restore");
                mBackupManagerMonitorEventSender.monitorEvent(
                        BackupManagerMonitor.LOG_EVENT_ID_UNABLE_TO_CREATE_AGENT_FOR_RESTORE,
                        mCurrentPackageInfo,
                        BackupManagerMonitor.LOG_EVENT_CATEGORY_BACKUP_MANAGER_POLICY,
                        monitoringExtras);
                cleanup(false);
                return;
            }
        } catch (NameNotFoundException e) {
            Slog.w(TAG, "Failed to get application info for delayed restore: ", e);
            mBackupManagerMonitorEventSender.monitorEvent(
                    BackupManagerMonitor.LOG_EVENT_ID_PACKAGE_NOT_PRESENT,
                    createPackageInfoForBMMLogging(mCurrentPackageName),
                    BackupManagerMonitor.LOG_EVENT_CATEGORY_BACKUP_MANAGER_POLICY,
                    monitoringExtras);
            cleanup(false);
            return;
        } catch (Exception e) {
            Slog.w(TAG, "Failed to perform delayed restore: ", e);
            mBackupManagerMonitorEventSender.monitorEvent(
                    BackupManagerMonitor.LOG_EVENT_ID_AGENT_FAILURE,
                    mCurrentPackageInfo,
                    BackupManagerMonitor.LOG_EVENT_CATEGORY_AGENT,
                    monitoringExtras);
            cleanup(false);
            return;
        }
    }

    private void setupNewStateFile(String stateDirName) throws FileNotFoundException {
        File stateDir = new File(mBackupManagerService.getBaseStateDir(), stateDirName);
        mNewStateFile = new File(stateDir, mCurrentPackageName + ".new");
        mNewStatePfd =
                ParcelFileDescriptor.open(
                        mNewStateFile,
                        ParcelFileDescriptor.MODE_READ_WRITE
                                | ParcelFileDescriptor.MODE_CREATE
                                | ParcelFileDescriptor.MODE_TRUNCATE);
    }

    // An operation that wanted a callback has completed
    @Override
    public void operationComplete(long result) {
        Slog.i(TAG, "Delayed restore operation complete for package: " + mCurrentPackageName);
        mOperationStorage.removeOperation(mEphemeralOpToken);
        mBackupManagerService
                .getDelayedRestoreJournal()
                .removeRequest(mRequest, mCurrentPackageName);

        mBackupManagerMonitorEventSender.monitorEvent(
                BackupManagerMonitor.LOG_EVENT_ID_PACKAGE_RESTORE_FINISHED,
                mCurrentPackageInfo,
                BackupManagerMonitor.LOG_EVENT_CATEGORY_BACKUP_MANAGER_POLICY,
                addDelayedRestoreOperationTypeToEvent());
        cleanup(false);
    }

    /** The task is cancelled for the given {@link CancellationReason}. */
    public void handleCancel(@CancellationReason int cancellationReason) {
        Slog.w(TAG, "Delayed restore failed with reason: " + cancellationReason);
        mOperationStorage.removeOperation(mEphemeralOpToken);
        Bundle monitoringExtras = addDelayedRestoreOperationTypeToEvent();
        if (cancellationReason == CancellationReason.TIMEOUT) {
            if (mBackupEligibilityRules.appGetsFullBackup(mCurrentPackageInfo)) {
                mBackupManagerMonitorEventSender.monitorEvent(
                        BackupManagerMonitor.LOG_EVENT_ID_FULL_RESTORE_TIMEOUT,
                        mCurrentPackageInfo,
                        BackupManagerMonitor.LOG_EVENT_CATEGORY_AGENT,
                        monitoringExtras);
            } else {
                mBackupManagerMonitorEventSender.monitorEvent(
                        BackupManagerMonitor.LOG_EVENT_ID_KEY_VALUE_RESTORE_TIMEOUT,
                        mCurrentPackageInfo,
                        BackupManagerMonitor.LOG_EVENT_CATEGORY_AGENT,
                        monitoringExtras);
            }
        } else {
            Bundle monitoringExtrasWithCancellationReason =
                    mBackupManagerMonitorEventSender.putMonitoringExtra(
                            monitoringExtras,
                            BackupManagerMonitor.EXTRA_LOG_CANCELLATION_REASON,
                            cancellationReason);
            mBackupManagerMonitorEventSender.monitorEvent(
                    BackupManagerMonitor.LOG_EVENT_ID_RESTORE_CANCELLED,
                    mCurrentPackageInfo,
                    BackupManagerMonitor.LOG_EVENT_CATEGORY_AGENT,
                    monitoringExtrasWithCancellationReason);
        }
        cleanup(false);
    }

    /*
     * Cleans up the resources used by the delayed restore task.
     *
     * <p>This method removes the operation from the operation storage, closes the new state file
     * descriptor (if it exists), deletes the new state file (if it exists), unbinds the agent, and
     * removes the operation timeout message. It also handles the next pending restore task if there
     * is one. This method is called for both KV and full delayed restore operations, and is called
     * from both the operationComplete() and handleCancel() methods.
     */
    private void cleanup(boolean shouldClearRequesterPackageNames) {
        if (mTransportConnection != null) {
            mBackupManagerService
                    .getTransportManager()
                    .disposeOfTransportClient(mTransportConnection, "DelayedRestoreTask.cleanup()");
        }

        try {
            if (mNewStatePfd != null) {
                mNewStatePfd.close();
            }
        } catch (IOException e) {
            Slog.w(TAG, "Failed to close new state file descriptor for delayed restore: ", e);
        }

        // if everything went okay, remember the recorded state now
        //
        // !!! TODO: the restored data could be migrated on the server
        // side into the current dataset.  In that case the new state file
        // we just created would reflect the data already extant in the
        // backend, so there'd be nothing more to do.  Until that happens,
        // however, we need to make sure that we record the data to the
        // current backend dataset.  (Yes, this means shipping the data over
        // the wire in both directions.  That's bad, but consistency comes
        // first, then efficiency.)  Once we introduce server-side data
        // migration to the newly-restored device's dataset, we will change
        // the following from a discard of the newly-written state to the
        // "correct" operation of renaming into the canonical state blob.
        if (mNewStateFile != null) {
            mNewStateFile.delete(); // TODO: remove; see above comment
        }

        if (mCurrentPackageInfo != null) {
            mBackupManagerService
                    .getBackupAgentConnectionManager()
                    .unbindAgent(mCurrentPackageInfo.applicationInfo, /* allowKill= */ false);
        }

        // Remove the operation timeout message since the operation is complete. In the case of
        // timeout, this will be a no-op since the message will have already been handled.
        mBackupManagerService
                .getBackupHandler()
                .removeMessages(MSG_RESTORE_OPERATION_TIMEOUT, this);

        if (shouldClearRequesterPackageNames) {
            mRequesterPackageNames.clear();
        }

        if (mRequesterPackageNames.size() > 0) {
            // There are more packages to delayed restore in this request, so schedule the next
            // package.
            Message msg =
                    mBackupManagerService
                            .getBackupHandler()
                            .obtainMessage(MSG_BACKUP_RESTORE_STEP, this);
            mBackupManagerService.getBackupHandler().sendMessage(msg);
        } else {
            // No more packages to delayed restore in this request, so schedule the next pending
            // restore task if there is one.
            synchronized (mBackupManagerService.getPendingRestores()) {
                if (mBackupManagerService.getPendingRestores().size() > 0) {
                    BackupRestoreTask task = mBackupManagerService.getPendingRestores().remove();
                    mBackupManagerService
                            .getBackupHandler()
                            .sendMessage(
                                    mBackupManagerService
                                            .getBackupHandler()
                                            .obtainMessage(MSG_BACKUP_RESTORE_STEP, task));
                } else {
                    mBackupManagerService.setRestoreInProgress(false);
                    if (DEBUG) {
                        Slog.d(TAG, "No pending restores.");
                    }
                }
            }
        }
    }

    private Bundle addDelayedRestoreOperationTypeToEvent() {
        Bundle bundle =
                mBackupManagerMonitorEventSender.putMonitoringExtra(
                        /* extras= */ null,
                        BackupManagerMonitor.EXTRA_LOG_OPERATION_TYPE,
                        OperationType.RESTORE);
        return mBackupManagerMonitorEventSender.putMonitoringExtra(
                bundle, BackupManagerMonitor.EXTRA_LOG_IS_DELAYED_RESTORE, true);
    }

    private PackageInfo createPackageInfoForBMMLogging(String packageName) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;

        return packageInfo;
    }
}
