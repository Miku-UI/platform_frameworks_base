/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_BACKUP_AGENT_STARTED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_BACKUP_AGENT_STARTED__BACKUP_AGENT_PROCESS_TYPE__TYPE_PCC_PROCESS;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_BINDER_PROXY_TRANSACTION_REPORTED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_BINDER_PROXY_TRANSACTION_REPORTED__TRANSACTION_STATUS__STATUS_FAILED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_BINDER_PROXY_TRANSACTION_REPORTED__TRANSACTION_STATUS__STATUS_SUCCESS;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_DATA_MIGRATION_STATE_CHANGED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_INPUT_SANITIZATION_REPORTED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_PERMISSION_CHECK_RESULT;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_UID_ASSIGNMENT_REPORTED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_WRITE_TO_AUDIT_LOG;


import com.android.os.privatecompute.PrivateComputeAtomsLog;

public class PrivateComputeStatsLogUtil {

    /** Logs the latency for performing bundle sanitization. */
    public static void logPccBundleInputSanitizationLatency(long elapsedTimeNanos,
            int sanitizationResult) {
        PrivateComputeAtomsLog.write(
                PCC_INPUT_SANITIZATION_REPORTED,
                elapsedTimeNanos,
                sanitizationResult
        );
    }

    /**
     * Logs a successful PCC binder proxy transaction.
     */
    public static void logPccBinderProxyTransactionSuccess() {
        PrivateComputeAtomsLog.write(
                PCC_BINDER_PROXY_TRANSACTION_REPORTED,
                PCC_BINDER_PROXY_TRANSACTION_REPORTED__TRANSACTION_STATUS__STATUS_SUCCESS
        );
    }

    /**
     * Logs a failed PCC binder proxy transaction with a specific failure reason.
     */
    public static void logPccBinderProxyTransactionFailure(int failureReason) {
        PrivateComputeAtomsLog.write(
                PCC_BINDER_PROXY_TRANSACTION_REPORTED,
                PCC_BINDER_PROXY_TRANSACTION_REPORTED__TRANSACTION_STATUS__STATUS_FAILED,
                failureReason
        );
    }

    /**
     * Logs the result of a PCC permission check.
     */
    public static void logPccPermissionCheckResult(int result, String permissionName, int uid) {
        PrivateComputeAtomsLog.write(PCC_PERMISSION_CHECK_RESULT, result, permissionName, uid);
    }

    /** Logs when write to the audit log is performed. */
    public static void logPccWriteToAuditLog(int writeType) {
        PrivateComputeAtomsLog.write(
                PCC_WRITE_TO_AUDIT_LOG,
                writeType
        );
    }


    /**
     * Logs the status of a PCC UID assignment.
     */
    public static void logPccUidAssignment(int status) {
        PrivateComputeAtomsLog.write(PCC_UID_ASSIGNMENT_REPORTED, status);
    }

    /**
     * Logs a state change in the PCC data migration process.
     *
     * @param state  The current state of the data migration process (e.g., COMPLETE, FAILED),
     *               as defined by PrivateComputeAtomsLog constants.
     */
    public static void logPccDataMigrationStateChanged(int state) {
        PrivateComputeAtomsLog.write(PCC_DATA_MIGRATION_STATE_CHANGED, state);
    }

    /**
     * Logs a state change in the PCC data migration process.
     *
     * @param state  The current state of the data migration process (e.g., COMPLETE, FAILED),
     *               as defined by PrivateComputeAtomsLog constants.
     * @param source The source file type of the data migration (e.g., APP_DATA_FILE_SOURCE),
     *               as defined by PrivateComputeAtomsLog constants.
     * @param target The target file type of the data migration (e.g., TARGET_PCC),
     *               as defined by PrivateComputeAtomsLog constants.
     */
    public static void logPccDataMigrationStateChanged(int state, int source, int target) {
        PrivateComputeAtomsLog.write(PCC_DATA_MIGRATION_STATE_CHANGED, state, source, target);
    }

    /**
     * Logs that a PCC backup agent has started.
     */
    public static void logPccBackupAgentStarted() {
        PrivateComputeAtomsLog.write(
                PCC_BACKUP_AGENT_STARTED,
                PCC_BACKUP_AGENT_STARTED__BACKUP_AGENT_PROCESS_TYPE__TYPE_PCC_PROCESS
        );
    }
}
