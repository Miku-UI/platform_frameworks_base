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

package com.android.server.backup;

import static android.app.ApplicationThreadConstants.BACKUP_MODE_RESTORE;
import static com.android.server.backup.BackupManagerService.DEBUG;
import static com.android.server.backup.internal.BackupHandler.MSG_BACKUP_RESTORE_STEP;
import static com.android.server.backup.internal.BackupHandler.MSG_RESTORE_OPERATION_TIMEOUT;

import android.app.IBackupAgent;
import android.app.backup.BackupAnnotations.BackupDestination;
import android.app.backup.BackupAnnotations.OperationType;
import android.app.backup.BackupManagerMonitor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Slog;

import com.android.server.backup.OperationStorage.OpType;
import com.android.server.backup.utils.BackupEligibilityRules;

import com.android.server.backup.transport.BackupTransportClient;
import com.android.server.backup.transport.TransportConnection;
import com.android.server.backup.utils.BackupManagerMonitorEventSender;

public class DelayedRestoreCleanupTask implements BackupRestoreTask {
    private static final String TAG = "DelayedRestoreCleanupTask";

    private final UserBackupManagerService mBackupManagerService;
    private final String mPackageName;
    private final int mEphemeralOpToken;
    private final OperationStorage mOperationStorage;
    private final BackupEligibilityRules mBackupEligibilityRules;
    private final BackupManagerMonitorEventSender mBackupManagerMonitorEventSender;

    private TransportConnection mTransportConnection;
    private ApplicationInfo mAppInfo;
    private PackageInfo mPackageInfo;

    public DelayedRestoreCleanupTask(
            UserBackupManagerService backupManagerService,
            String packageName,
            OperationStorage operationStorage) {
        mOperationStorage = operationStorage;
        mBackupManagerService = backupManagerService;
        mPackageName = packageName;
        mEphemeralOpToken = backupManagerService.generateRandomIntegerToken();
        // Backup destination is not relevant for delayed restores since we use a local cached data
        // that was cached during the initial restore pass, so we use the default value which is
        // CLOUD.
        mBackupEligibilityRules =
                backupManagerService.getEligibilityRulesForOperation(BackupDestination.CLOUD);
        mBackupManagerMonitorEventSender = new BackupManagerMonitorEventSender(/* monitor= */ null);
    }

    @Override
    public void execute() {
        Slog.i(TAG, "Starting delayed restore cleanup for " + mPackageName);
        Bundle monitoringExtras = addDelayedRestoreOperationTypeToEvent();
        mTransportConnection =
                mBackupManagerService
                        .getTransportManager()
                        .getCurrentTransportClient(
                                "DelayedRestoreCleanupTask.execute()");
        try {
            if (mTransportConnection != null) {
                BackupTransportClient transport =
                        mTransportConnection.connectOrThrow("DelayedRestoreCleanupTask.execute()");
                mBackupManagerMonitorEventSender.setMonitor(transport.getBackupManagerMonitor());
            }

            mPackageInfo =
                    mBackupManagerService
                            .getPackageManager()
                            .getPackageInfoAsUser(
                                    mPackageName, 0, mBackupManagerService.getUserId());
            mAppInfo = mPackageInfo.applicationInfo;

            mBackupManagerMonitorEventSender.monitorEvent(
                    BackupManagerMonitor.LOG_EVENT_ID_START_DELAYED_RESTORE_CLEANUP,
                    mPackageInfo,
                    BackupManagerMonitor.LOG_EVENT_CATEGORY_BACKUP_MANAGER_POLICY,
                    monitoringExtras);

            mBackupManagerService
                    .getDelayedRestoreJournal()
                    .clearAllRequestsForPackage(mPackageName);

            if (mBackupEligibilityRules.appGetsFullBackup(mPackageInfo)) {
                // No need to bind to the agent for full backup delayed restore cleanup since there
                // is no cached data to clear up.
                Slog.i(TAG, "Cleaned up full backup delayed restore for " + mPackageName);
                cleanup();
                return;
            }

            IBackupAgent agent =
                    mBackupManagerService
                            .getBackupAgentConnectionManager()
                            .bindToAgentSynchronous(
                                    mAppInfo,
                                    BACKUP_MODE_RESTORE,
                                    mBackupEligibilityRules.getBackupDestination());

            if (agent == null) {
                Slog.w(TAG, "Could not bind to agent for " + mPackageName);
                mBackupManagerMonitorEventSender.monitorEvent(
                        BackupManagerMonitor.LOG_EVENT_ID_UNABLE_TO_CREATE_AGENT_FOR_RESTORE,
                        mPackageInfo,
                        BackupManagerMonitor.LOG_EVENT_CATEGORY_BACKUP_MANAGER_POLICY,
                        monitoringExtras);
                cleanup();
                return;
            }

            long restoreAgentTimeoutMillis =
                    mBackupManagerService
                            .getAgentTimeoutParameters()
                            .getRestoreAgentTimeoutMillis(mAppInfo.uid);
            mBackupManagerService.prepareOperationTimeout(
                    mEphemeralOpToken, restoreAgentTimeoutMillis, this, OpType.RESTORE_WAIT);

            agent.doDelayedRestoreCachedDataExpired(
                    mEphemeralOpToken, mBackupManagerService.getBackupManagerBinder());
        } catch (NameNotFoundException e) {
            Slog.w(TAG, "Package " + mPackageName + " not found");
            mBackupManagerMonitorEventSender.monitorEvent(
                    BackupManagerMonitor.LOG_EVENT_ID_PACKAGE_NOT_PRESENT,
                    createPackageInfoForBMMLogging(mPackageName),
                    BackupManagerMonitor.LOG_EVENT_CATEGORY_BACKUP_MANAGER_POLICY,
                    monitoringExtras);
            cleanup();
        } catch (Exception e) {
            Slog.e(TAG, "Error clearing cached data for " + mPackageName, e);
            mBackupManagerMonitorEventSender.monitorEvent(
                    BackupManagerMonitor.LOG_EVENT_ID_AGENT_FAILURE,
                    mPackageInfo,
                    BackupManagerMonitor.LOG_EVENT_CATEGORY_AGENT,
                    monitoringExtras);
            cleanup();
        }
    }

    @Override
    public void operationComplete(long result) {
        Slog.i(TAG, "Delayed restore cleanup finished for " + mPackageName);
        mOperationStorage.removeOperation(mEphemeralOpToken);

        mBackupManagerMonitorEventSender.monitorEvent(
                BackupManagerMonitor.LOG_EVENT_ID_DELAYED_RESTORE_CLEANUP_FINISHED,
                mPackageInfo,
                BackupManagerMonitor.LOG_EVENT_CATEGORY_BACKUP_MANAGER_POLICY,
                addDelayedRestoreOperationTypeToEvent());
        cleanup();
    }

    @Override
    public void handleCancel(@CancellationReason int cancellationReason) {
        Slog.w(TAG, "Delayed restore cleanup failed with reason: " + cancellationReason);
        mOperationStorage.removeOperation(mEphemeralOpToken);

        if (cancellationReason == CancellationReason.TIMEOUT) {
            mBackupManagerMonitorEventSender.monitorEvent(
                    BackupManagerMonitor.LOG_EVENT_ID_KEY_VALUE_RESTORE_TIMEOUT,
                    mPackageInfo,
                    BackupManagerMonitor.LOG_EVENT_CATEGORY_AGENT,
                    addDelayedRestoreOperationTypeToEvent());
        } else {
            Bundle monitoringExtrasWithCancellationReason =
                    mBackupManagerMonitorEventSender.putMonitoringExtra(
                            addDelayedRestoreOperationTypeToEvent(),
                            BackupManagerMonitor.EXTRA_LOG_CANCELLATION_REASON,
                            cancellationReason);
            mBackupManagerMonitorEventSender.monitorEvent(
                    BackupManagerMonitor.LOG_EVENT_ID_RESTORE_CANCELLED,
                    mPackageInfo,
                    BackupManagerMonitor.LOG_EVENT_CATEGORY_AGENT,
                    monitoringExtrasWithCancellationReason);
        }
        cleanup();
    }

    /*
     * Cleans up the resources used by the delayed restore cleanup task.
     *
     * <p>This method removes the operation from the operation storage, unbinds the agent, and
     * removes the operation timeout message. It also handles the next pending restore task if there
     * is one. This method is called from both the operationComplete() and handleCancel() methods.
     * This method is used for cleaning up in both cases of KV and full delayed restores.
     */
    private void cleanup() {
        if (mTransportConnection != null) {
            mBackupManagerService
                    .getTransportManager()
                    .disposeOfTransportClient(
                            mTransportConnection, "DelayedRestoreCleanupTask.cleanup()");
        }

        if (mAppInfo != null) {
            mBackupManagerService
                    .getBackupAgentConnectionManager()
                    .unbindAgent(mAppInfo, /* allowKill= */ false);
        }

        // Remove the operation timeout message since the operation is complete. In the case of
        // timeout, this will be a no-op since the message will have already been handled.
        mBackupManagerService
                .getBackupHandler()
                .removeMessages(MSG_RESTORE_OPERATION_TIMEOUT, this);

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
