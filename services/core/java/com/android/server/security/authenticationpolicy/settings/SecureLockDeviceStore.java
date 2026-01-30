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

package com.android.server.security.authenticationpolicy.settings;

import android.annotation.NonNull;
import android.os.Build;
import android.os.Handler;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;

/**
 * Stores the current state of Secure Lock Device in GlobalSettings.
 */
@VisibleForTesting
public class SecureLockDeviceStore {
    private static final String TAG = "SecureLockDeviceStore";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    @NonNull
    private final SecureLockDeviceSettingsFileStore mFileStore;

    public SecureLockDeviceStore(Handler handler,
            @NonNull SecureLockDeviceSettingsManager settingsManager) {
        mFileStore = new SecureLockDeviceSettingsFileStore(handler, settingsManager);
        mFileStore.loadStateFromFile();
    }

    /**
     * For test purposes only: overrides file for storing secure lock device settings state.
     * @param testFile The new file.
     */
    public void overrideStateFile(File testFile) {
        mFileStore.overrideStateFile(testFile);
    }

    /**
     * Updates the current Global settings to reflect Secure Lock Device being enabled.
     *
     * @param userId the userId of the client that enabled secure lock device
     */
    public void storeSecureLockDeviceEnabled(int userId) {
        if (DEBUG) {
            Slog.d(TAG, "Storing SLD enabled by user: " + userId);
        }
        mFileStore.updateStateAndWriteToFile(/* enabled= */ true, /* userId= */ userId);
    }

    /**
     * Updates the current Global settings to reflect Secure Lock Device being disabled.
     */
    public void storeSecureLockDeviceDisabled() {
        if (DEBUG) {
            Slog.d(TAG, "Storing SLD disabled.");
        }
        mFileStore.updateStateAndWriteToFile(/* enabled= */ false,
                /* userId= */ UserHandle.USER_NULL);
    }

    /**
     * Retrieves the current state of whether Secure Lock Device in enabled or disabled in
     * GlobalSettings.
     *
     * @return true if Secure Lock Device is enabled, false otherwise
     */
    public boolean retrieveSecureLockDeviceEnabled() {
        return mFileStore.retrieveSecureLockDeviceEnabled();
    }

    /**
     * Retrieves the user id of the client that enabled secure lock device, or USER_NULL if secure
     * lock device is disabled.
     *
     * @return userId of the client that enabled secure lock device, or USER_NULL if secure lock
     * device is disabled
     */
    public int retrieveSecureLockDeviceClientId() {
        return mFileStore.retrieveSecureLockDeviceClientId();
    }

    /**
     * Returns the atomic file for storing secure lock device settings state.
     * @return The atomic file.
     */
    public AtomicFile getStateFile() {
        return mFileStore.getStateFile();
    }
}
