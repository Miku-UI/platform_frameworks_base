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
package com.android.server.pm;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import com.android.server.utils.Slogf;

/**
 * Class responsible for device provisioning related activities for when the device boots in
 * headless system user mode.
 */
final class HsuDeviceProvisioner extends ContentObserver {

    private static final String TAG = HsuDeviceProvisioner.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final ContentResolver mContentResolver;

    public HsuDeviceProvisioner(Handler handler, ContentResolver contentResolver) {
        super(handler);
        mContentResolver = contentResolver;
    }

    /**
     * Initialize this object.
     *
     * It will register itself as a content observer for the settings changes if necessary.
     */
    public void init() {
        if (isDeviceProvisioned()) {
            return;
        }

        mContentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED), false, this);
    }

    @Override
    public void onChange(boolean selfChange) {
        if (DEBUG) {
            Slogf.d(TAG, "onChange(%b): isDeviceProvisioned=%b", selfChange, isDeviceProvisioned());
        }
        // Set USER_SETUP_COMPLETE for the (headless) system user only when the device
        // has been set up at least once.
        if (isDeviceProvisioned()) {
            Slogf.i(TAG, "Marking USER_SETUP_COMPLETE for system user");
            Settings.Secure.putInt(mContentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1);
            mContentResolver.unregisterContentObserver(this);
        }
    }

    private boolean isDeviceProvisioned() {
        try {
            return Settings.Global.getInt(mContentResolver, Settings.Global.DEVICE_PROVISIONED)
                    == 1;
        } catch (Exception e) {
            Slogf.wtf(
                TAG,
                "DEVICE_PROVISIONED setting (%s) not found: %s",
                Settings.Global.DEVICE_PROVISIONED,
                e);
            return false;
        }
    }
}
