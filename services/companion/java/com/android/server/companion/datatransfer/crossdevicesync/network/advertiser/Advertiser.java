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
package com.android.server.companion.datatransfer.crossdevicesync.network.advertiser;

import com.android.server.companion.datatransfer.crossdevicesync.common.Dumpable;

/** A interface managing advertising behavior. */
public interface Advertiser extends Dumpable {

    /**
     * Start advertising my presence to the target device.
     *
     * @param associationId the association id of the device we want to advertise.
     * @param requestName the name of this request for logging.
     * @return the advertising session.
     */
    AdvertisingSession startAdvertising(int associationId, String requestName);

    /** Close all advertising sessions. */
    void closeAllAdvertisingSessions();

    /** A session object holding the state of an advertising request. */
    interface AdvertisingSession extends AutoCloseable {

        /** Get the association id we are advertising to. */
        int getAssociationId();

        /** Check if this advertising session is active. */
        boolean isActive();

        /** Close this advertising session. Advertising will stop if all sessions are closed. */
        void close();
    }
}
