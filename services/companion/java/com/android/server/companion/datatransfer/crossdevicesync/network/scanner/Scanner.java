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
package com.android.server.companion.datatransfer.crossdevicesync.network.scanner;

import com.android.server.companion.datatransfer.crossdevicesync.common.Dumpable;

/** A interface managing scanning behavior. */
public interface Scanner extends Dumpable {

    /**
     * Scan for a device. The implementation can decide how to scan, scanning frequency, scanning
     * strategy, etc. This is just a request to start scanning, while the actual scanning operation
     * may not start immediately if conditions are not satisfied. It's OK to delay the scan for
     * arbitrary long time in order to save power.
     *
     * @param associationId the association id of the device we want to look for.
     * @param requestName the name of this request for logging.
     * @return a session object holding the state of the scanning request. Each call to this method
     *     will create a new session. The scanning will stop only if all sessions are stopped.
     */
    ScanningSession startScanning(int associationId, String requestName);

    /** Close all scanning sessions. */
    void closeAllScanningSessions();

    /** A session object holding the state of a scanning request. */
    interface ScanningSession extends AutoCloseable {

        /** The association id of the device we're scanning for. */
        int getAssociationId();

        /** Check if this scanning session is active. */
        boolean isActive();

        /** Close this scanning session. Scanning will stop if all sessions are closed. */
        void close();
    }
}
