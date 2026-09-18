/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.tv.extension.time;

import android.os.Bundle;

import android.media.tv.extension.time.IBroadcastTimeListener;

/**
 * @hide
 */
interface IBroadcastTime {
    /**
     * Gets the UTC time from the transport stream.
     *
     * @return The difference, in milliseconds, between the current time and the
     *         Unix epoch (midnight, January 1, 1970 UTC).
     */
    long getUtcTime();
    /**
     * Gets the local time from the transport stream, adjusted for the current time zone offset.
     *
     * @return The local time in milliseconds since the Unix epoch.
     */
    long getLocalTime();
    /**
     * Gets time zone information from the transport stream.
     *
     * @return A Bundle containing time zone details with bundle keys defined as
     *         @TimeConstants.TimeZoneInfoBundleKey.
     */
    Bundle getTimeZoneInfo();
    /**
     * Gets the UTC time from the transport stream for a specific multi-session instance.
     *
     * @param SessionToken A unique token created by the TIS to identify a specific session.
     * @return The difference, in milliseconds, between the current time and the Unix epoch.
     */
    long getUtcTimePerStream(String SessionToken);
    /**
     * Gets the local time from the transport stream for a specific multi-session instance,
     * adjusted for the current time zone offset.
     *
     * @param SessionToken A unique token created by the TIS to identify a specific session.
     * @return The local time in milliseconds since the Unix epoch.
     */
    long getLocalTimePerStream(String SessionToken);
    /**
     * Registers a listener to receive notification when the broadcast time changes.
     *
     * @param listener The listener to register.
     */
    void addListener(in IBroadcastTimeListener listener);
    /**
     * Removes a previously registered listener.
     *
     * @param listener The listener to remove.
     */
    void removeListener(in IBroadcastTimeListener listener);
}