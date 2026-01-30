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

import android.app.backup.BlobBackupHelper;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.display.utils.DebugUtils;
import com.android.server.wm.WindowManagerInternal;

public class DisplayWindowSettingsBackupHelper extends BlobBackupHelper {
    private static final String TAG = "DWSBackupHelper";

    // current schema of the backup state blob
    private static final int BLOB_VERSION = 1;

    // key under which the data blob is committed to back up
    private static final String KEY_DISPLAY = "display_window";

    // To enable these logs, run:
    // adb shell setprop persist.log.tag.DWSBackupHelper DEBUG
    // adb reboot
    private static final boolean DEBUG = DebugUtils.isDebuggable(TAG);

    private final int mUserId;

    public DisplayWindowSettingsBackupHelper(int userId) {
        super(BLOB_VERSION, KEY_DISPLAY);
        mUserId = userId;
    }

    @Override
    protected byte[] getBackupPayload(String key) {
        Slog.i(TAG, "getBackupPayload for " + key + " user " + mUserId);
        if (!KEY_DISPLAY.equals(key)) {
            return null;
        }

        WindowManagerInternal wmInternal = LocalServices.getService(WindowManagerInternal.class);
        if (DEBUG) {
            Slog.d(TAG, "Handling backup of " + key);
        }
        return wmInternal.backupDisplayWindowSettings(mUserId);
    }

    @Override
    protected void applyRestoredPayload(String key, byte[] payload) {
        Slog.i(TAG, "applyRestoredPayload for " + key + " user " + mUserId);
        if (!KEY_DISPLAY.equals(key)) {
            return;
        }

        WindowManagerInternal wmInternal = LocalServices.getService(WindowManagerInternal.class);
        if (DEBUG) {
            Slog.d(TAG, "Handling restore of " + key);
        }
        wmInternal.restoreDisplayWindowSettings(mUserId, payload);
    }
}
