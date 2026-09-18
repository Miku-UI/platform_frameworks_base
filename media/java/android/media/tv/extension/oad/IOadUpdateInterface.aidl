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

package android.media.tv.extension.oad;

import android.media.tv.extension.oad.IOadListener;
import android.os.Bundle;

/**
 * @hide
 */
interface IOadUpdateInterface {
    /**
     * Enables or disables the OAD (Over-the-Air Download) function.
     *
     * @param true to enable the OAD function, false to disable it.
     */
    void setOadStatus(boolean enable);
    /**
     * Gets the current status of the OAD function.
     *
     * @return true if the OAD function is enabled, false otherwise.
     */
    boolean getOadStatus();
    /**
     * Registers a listener to receive OAD events instead of relying on sessionEvent.
     */
    void registerListener(in IOadListener listener);

    /**
     * Unregisters the listener.
     */
    void unregisterListener(in IOadListener listener);
    /**
     * Starts an OAD scan across all frequencies in the program list to search for updates.
     * This API can be called after OAD is enabled.
     */
    oneway void startScan();
    /**
     * Stops an in-progress OAD scan. This API can be called after scan has started.
     */
    oneway void stopScan();
    /**
     * Starts OAD detection on the currently tuned channel to check for an available update.
     * This API can be called after OAD is enabled.
     */
    oneway void startDetect();
    /*
     * Stops the OAD detection process on the current channel.
     * This API can be called detect has started.
     */
    oneway void stopDetect();
    /**
     * Starts the OAD download process for an update that has been found via scan or detection.
     */
    oneway void startDownload();
    /**
     * Stops an in-progress OAD download. This API can be called after download has started.
     */
    oneway void stopDownload();
    /**
     * Retrieves the current OAD software version.
     *
     * @return An integer representing the current software version.
     */
    int getSoftwareVersion();
    /**
     * Notify the upgrader APK to apply the OAD update after it is downloaded by the LiveTV app.
     *
     * @param url The file path of the OTA zip.
     * @param offset The start position.
     * @param size the num of bytes to read from the offset.
     * @param properties a Bundle containing extra instructions, such as if reboot is needed.
     */
    oneway void applyUpgrade(String url, long offset, long size, in Bundle properties);
}
