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

import android.media.tv.extension.cam.ICamPinCapabilityListener;
import android.media.tv.extension.cam.ICamPinStatusListener;
import android.os.Bundle;

/**
 * @hide
 */
interface ICamPinService {
    /**
     * Registers a listener to receive CICAM updates, such as PIN status and PIN capability changes.
     *
     * @param listener The ICamPinCapabilityListener to add for receiving notifications.
     */
    void addCamPinCapabilityListener(in ICamPinCapabilityListener listener);
    /**
     * Unregisters a listener to stop monitoring PIN status and PIN capability.
     *
     * @param listener The ICamPinCapabilityListener that was previously registered.
     */
    void removeCamPinCapabilityListener(in ICamPinCapabilityListener listener);
    /**
     * Sends a PIN code to be validated by the CICAM. The application should obtain the slotId from
     * ICamMonitoringService#getSlotIds() and verify that the region supports CAM before
     * calling this API.
     *
     * @param slotId The ID of the corresponding CICAM.
     * @param pinCode The PIN code to be validated by the CICAM.
     * @param listener An ICamPinStatusListener instance to receive the validation result.
     * @return @CamConstants.CamPinValidationResult to indicate request result.
     */
    int requestCamPinValidation(int slotId, in int[] pinCode, in ICamPinStatusListener listener);
    /**
     * Gets the PIN capabilities of the CICAM.
     *
     * @param slotId The ID of the corresponding CICAM.
     * @param camPinCapability An output Bundle that contain the PIN capability information, key
     *                         as defined in @CamConstants.CamPinCapabilityBundleKey and values
     *                         for KEY_PIN_CAP_CAPABILITY as @CamConstants.CamPinCapabilityType.
     * @return @CamConstants.CamPinCapabilityResult to indicate the result.
     */
    int getCamPinCapability(int slotId, out Bundle camPinCapability);
}
