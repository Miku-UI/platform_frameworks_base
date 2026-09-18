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

import android.util.Log;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.privatecompute.AuditModeContext.Injector;
import java.io.File;

/**
 * Manages file names for audit log files. Exposes a single method {@link #getNextAuditLogFileName}
 * which returns the File that should be used for the next audit log. Produces files
 * audit_log.<counter>.bin. When instantiated, counter is always 0. Handles deleting old files to
 * make sure we don't have more than {@link Injector#auditModeMaxLogFiles} files. This class is
 * thread-safe.
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
class AuditLogFileManager {
    private static final String TAG = "PccSandboxManagerServiceAuditMode.AuditLogWriter";

    static final String AUDIT_LOG_FILE_PREFIX = "audit_log";
    static final String AUDIT_LOG_FILE_SUFFIX = ".bin";

    private final File mFolder;
    private final Object mLock = new Object();
    private final Injector mInjector;

    @GuardedBy("mLock")
    private int mFileCounter = 0;

    AuditLogFileManager(File folder, Injector injector) {
        mFolder = folder;
        mInjector = injector;
    }

    /**
     * Returns the next audit log file name, and increments the counter. After {@link
     * Injector#auditModeMaxLogFiles}, loops back to 0, overwriting old files.
     */
    public File rotateAndReturnNewFile() {
        synchronized (mLock) {
            String fileName = AUDIT_LOG_FILE_PREFIX + "." + mFileCounter + AUDIT_LOG_FILE_SUFFIX;
            File file = new File(mFolder, fileName);
            mFileCounter = (mFileCounter + 1) % this.mInjector.auditModeMaxLogFiles();
            return file;
        }
    }
}
