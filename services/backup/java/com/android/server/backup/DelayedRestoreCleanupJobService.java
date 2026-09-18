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

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Slog;

/** Job for cleaning up cached delayed restore data and requests. */
public class DelayedRestoreCleanupJobService extends JobService {
    private static final String TAG = "DelayedRestoreCleanupJobService";

    /**
     * Offset for delayed restore cleanup job IDs to avoid conflict with other job IDs.
     *
     * <p>See {@link com.android.server.backup.FullBackupJob} and {@link
     * com.android.server.backup.KeyValueBackupJob} for min and max job IDs.
     */
    public static final int DELAYED_RESTORE_CLEANUP_JOB_ID_OFFSET = 52430000;

    @Override
    public boolean onStartJob(JobParameters params) {
        int userId = params.getExtras().getInt("userId");
        String packageName = params.getExtras().getString("packageName");

        if (packageName == null) {
            Slog.e(TAG, "packageName is null, skipping cleanup");
            return false;
        }

        BackupManagerService backupManagerService = BackupManagerService.getInstance();
        if (backupManagerService != null) {
            backupManagerService.onDelayedRestoreCachedDataExpiredForUser(userId, packageName);
        } else {
            Slog.e(
                    TAG,
                    "BackupManagerService is null, "
                            + "cannot clear cached data for delayed restore");
        }

        return false; // Job finished
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false; // Do not reschedule
    }

    /**
     * Returns the job ID for the given UID.
     *
     * <p>The UID range is [10000, 20000), pointing to {@link
     * android.os.Process#FIRST_APPLICATION_UID} and {@link
     * android.os.Process#LAST_APPLICATION_UID}.
     */
    public static int getJobIdForUserId(int uid) {
        return DELAYED_RESTORE_CLEANUP_JOB_ID_OFFSET + uid;
    }
}
