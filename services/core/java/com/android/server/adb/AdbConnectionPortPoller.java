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
package com.android.server.adb;

import android.annotation.NonNull;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Slog;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class will poll for a period of time for adbd to write the port it connected to.
 *
 * <p>TODO(joshuaduong): The port is being sent via system property because the adbd socket
 * (AdbDebuggingManager) is not created when ro.adb.secure=0. Thus, we must communicate the port
 * through different means. A better fix would be to always start AdbDebuggingManager, but it needs
 * to adjust accordingly on whether ro.adb.secure is set.
 */
class AdbConnectionPortPoller extends Thread {

    private static final String TAG = AdbConnectionPortPoller.class.getSimpleName();
    private final String mAdbPortProp = "service.adb.tls.port";
    private final int mDurationSecs = 10;
    private AtomicBoolean mCanceled = new AtomicBoolean(false);

    interface PortChangedCallback {

        void onPortChanged(int port);
    }

    private PortChangedCallback mCallback;

    AdbConnectionPortPoller(@NonNull PortChangedCallback callback) {
        this.mCallback = callback;
    }

    @Override
    public void run() {
        Slog.d(TAG, "Starting TLS port property poller");
        // Once adbwifi is enabled, we poll the service.adb.tls.port
        // system property until we get the port, or -1 on failure.
        // Let's also limit the polling to 10 seconds, just in case
        // something went wrong.
        for (int i = 0; i < mDurationSecs; ++i) {
            if (mCanceled.get()) {
                return;
            }

            // If the property is set to -1, then that means adbd has failed
            // to start the server. Otherwise we should have a valid port.
            int port = SystemProperties.getInt(mAdbPortProp, Integer.MAX_VALUE);
            if (port == -1 || (port > 0 && port <= 65535)) {
                Slog.d(TAG, "System property received TLS port " + port);
                mCallback.onPortChanged(port);
                return;
            }
            SystemClock.sleep(1000);
        }
        Slog.w(TAG, "Failed to receive adb connection port from system property");
        mCallback.onPortChanged(-1);
    }

    public void cancelAndWait() {
        Slog.d(TAG, "Stopping TLS port property poller");
        mCanceled.set(true);
        if (this.isAlive()) {
            try {
                this.join();
            } catch (InterruptedException e) {
            }
        }
    }
}
