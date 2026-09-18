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

/**
 * @hide
 */
oneway interface ICamPinStatusListener {
    /**
     * Called to notify the result of a PIN code validation by the CICAM.
     *
     * @param slotId The slot ID of the corresponding CICAM.
     * @param pinValidationReply A Bundle that provides the status of the PIN code validation
     *                           with keys follow @CamConstants.CamPinStatusBundleKey; values for
     *                           KEY_PIN_VALIDATION_PINCODE_STATUS as @CamConstants.CamPinCodeStatus
     *                           and values for KEY_PIN_VALIDATION_RESULT should follow
     *                           @CamConstants.@CamPinValidationResult.
     */
    void onCamPinValidationReply(int slotId, in Bundle pinValidationReply);
}
