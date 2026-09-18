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

import android.os.Bundle;

import android.media.tv.extension.cam.ICamProfileListener;
import android.media.tv.extension.cam.ICiOperatorListener;

/**
 * @hide
 */
interface ICamProfileInterface {
    /**
     * Gets CAM service update information for a specific slot.
     *
     * @param slotNumber The ID of the slot to query.
     * @return A Bundle containing the CAM service update information, bundle keys as defined
     *         in @CamConstants.CamServiceUpdateInfoBundleKey and each key's corresponding values
     *         should also follow @CamConstants.CamOpProfileType/CamOpServiceUpdateMode/
     *         CamOpDeliverySystemHint/CamOpRefreshRequestFlag.
     */
    Bundle getCamServiceUpdateInfo(int slotNumber);
    /**
     * Requests the CAM TV Input Service to resend the CAM info update broadcast message.
     * This is typically used when an application boots up to ensure it has the latest profile
     * information if a profile update occurred during boot.
     */
    void requestResendProfileInfoBroadcastACON();
    /**
     * Checks if CAM scanning is enabled for the current profile based on its settings.
     *
     * @param slotNumber The ID of the slot to query.
     * @return true if CAM scanning is enabled, false otherwise.
     */
    boolean isCamScanEnabled(int slotNumber);
    /**
     * Registers a listener to receive callbacks when CAM profile changes.
     *
     * @param listener The listener to register.
     */
    void addListener(in ICamProfileListener listener);
    /**
     * Unregisters a previously registered profile listener.
     *
     * @param listener The listener to unregister.
     */
    void removeListener(in ICamProfileListener listener);

    /**
     * Updates the control status of a specific Operator Profile.
     *
     * @param profileName The unique name/ID of the profile (Consistency with deleteProfile).
     * @param enable      True to enable/activate, False to disable.
     * @param listener    Callback for the result of this async operation.
     * @return 0 for success (request sent), or specific error code.
     */
    int updateCiOPControl(String profileName, boolean enable, in ICiOperatorListener listener);

    /**
     * Retrieves the list of available Operator Profile names.
     *
     * @return Array of profile names. Returns empty array if none found.
     */
     String[] getCiOpNameList();

     /**
      * Deletes a specific profile from storage.
      *
      * @param profileName The unique name of the profile to delete.
      * @return true if deletion was successful, false otherwise.
      */
     boolean deleteProfile(String profileName);
}
