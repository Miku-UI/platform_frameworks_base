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

import android.app.IBackupAgent;
import android.os.RemoteException;

import com.android.server.backup.UserBackupManagerService;

/**
 * Runner that can be placed on a separate thread to do in-process invocation of the "restore
 * finished" API asynchronously.  Used by adb restore.
 */
public class AdbRestoreFinishedRunnable implements Runnable {

    private final IBackupAgent mAgent;
    private final int mToken;
    private final UserBackupManagerService mBackupManagerService;

    public AdbRestoreFinishedRunnable(IBackupAgent agent, int token,
            UserBackupManagerService backupManagerService) {
        mAgent = agent;
        mToken = token;
        mBackupManagerService = backupManagerService;
    }

    @Override
    public void run() {
        try {
            mAgent.doRestoreFinished(mToken, mBackupManagerService.getBackupManagerBinder());
        } catch (RemoteException e) {
            // never happens; this is used only for local binder calls
        }
    }
}
