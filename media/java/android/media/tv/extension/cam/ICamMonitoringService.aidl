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

package android.media.tv.extension.cam;

import android.media.tv.extension.cam.ICamInfoListener;
import android.os.Bundle;

/**
 * @hide
 */
interface ICamMonitoringService {
    /**
     * Adds a listener to receive notifications for slot and CAM info updates.
     *
     * @param listener The ICamInfoListener to register for receiving notifications.
     */
    void addCamInfoListener(in ICamInfoListener listener);
    /**
     * Removes a previously added listener for slot and CAM info updates.
     *
     * @param listener The ICamInfoListener to unregister.
     */
    void removeCamInfoListener(in ICamInfoListener listener);
    /**
     * Gets CAM information for the specified slot.
     *
     * @param slotId The ID or slot number where the CAM is being monitored.
     * @return A Bundle containing CAM information, or null if the CAM is not inserted or the region
     *         is unsupported. If not null, bundle should at least contain keys defined in
     *         @CamConstants.CamInfoBundleKey.
     */
    Bundle getCamInfo(int slotId);
    /**
     * Gets information for the specified slot.
     *
     * @param slotId The ID or slot number of the slot being monitored.
     * @return A Bundle containing slot information, or null if the slot is invalid or CAM is not
     *         supported. If not null, bundle should at least contain keys defined in
     *         @CamConstants.CamSlotInfoBundleKey.
     */
    Bundle getSlotInfo(int slotId);
    /**
     * Returns a list of slot IDs.
     *
     * @return A list of slot IDs, or an empty list if CAM is not supported.
     */
    int[] getSlotIds();
    /**
     * Checks if CAM is supported in the current country.
     *
     * @return true if CAM is supported, false otherwise.
     */
    boolean isCamSupported();
}
