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
 * limitations under the License
 */

package com.android.server.backup.fullbackup;

/**
 * Result returned after sending backup data to the transport.
 */
public class SendBackupDataResult {
    private final int backupPackageStatus;
    private final long totalRead;

    public SendBackupDataResult(int backupPackageStatus, long totalRead) {
        this.backupPackageStatus = backupPackageStatus;
        this.totalRead = totalRead;
    }

    public int getBackupPackageStatus() {
        return backupPackageStatus;
    }

    public long getTotalRead() {
        return totalRead;
    }
}
