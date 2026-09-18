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

import android.sysprop.PccProperties;
import android.util.Log;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.privatecompute.AuditModeContext.Injector;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * A buffer for the audit log. Once the buffer is full, or if serialization to disk has begun, no
 * data can be added. This class is thread-safe. All calls are blocking.
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
class AuditLogInMemoryBuffer {
    private final int QUEUE_INITIAL_CAPACITY = 1000; // Pointers to a byte array.

    private static final String TAG = "PccSandboxManagerServiceAuditMode.AuditLogInMemoryBuffer";

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    final File mAuditLogFile;

    private final Object mLock = new Object();
    private final Injector mInjector;

    @GuardedBy("mLock")
    private final List<byte[]> mAuditLogQueue = new ArrayList<>(QUEUE_INITIAL_CAPACITY);

    // This is used to signal that serialization to disk has begun, and that no more data should
    // be added. When writing to disk has finished, this stays true, as no data should be added.
    @GuardedBy("mLock")
    private boolean mFinalized = false;

    @GuardedBy("mLock")
    private int mInMemoryBufferSizeBytes = 0;

    AuditLogInMemoryBuffer(File auditLogFile, Injector injector) {
        mAuditLogFile = auditLogFile;
        mInjector = injector;
    }

    /**
     * Adds a serialized AuditLogEntry to the in-memory buffer. Returns true if successfully added.
     * Returns false if the buffer is full or if serialization to disk has begun.
     */
    public boolean add(byte[] data) {
        synchronized (mLock) {
            if (mFinalized) {
                return false;
            }
            if (mInMemoryBufferSizeBytes + data.length
                    >= this.mInjector.auditModeLogFileMaxSizeKb() * 1024) {
                return false;
            }
            mInMemoryBufferSizeBytes += data.length;
            mAuditLogQueue.add(data);
            return true;
        }
    }

    /** Closes the in-memory buffer, writing the contents to disk. */
    public void writeToFile() {
        synchronized (mLock) {
            if (mFinalized) {
                return;
            }
            mFinalized = true;
            AuditLogFileWriter writer = new AuditLogFileWriter(mAuditLogFile);
            try {
                writer.writeEntries(mAuditLogQueue);
            } catch (IOException e) {
                Log.e(TAG, "Failed to write to audit log file: " + e);
            }
        }
    }
}
