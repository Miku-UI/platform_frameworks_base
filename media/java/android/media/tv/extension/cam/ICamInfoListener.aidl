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
oneway interface ICamInfoListener {
    /**
     * Called when information about the CAM being monitored, such as its insertion or removal,
     * has changed.
     *
     * @param slotId The ID or slot number of the monitored CAM.
     * @param updatedCamInfo A Bundle containing about the updated CAM information, should at least
     *                       contain keys defined in @CamConstants.CamInfoBundleKey.
     */
    void onCamInfoChanged(int slotId, in Bundle updatedCamInfo);
    /**
     * Called to notify that the status of a CAM slot has been updated.
     *
     * @param slotId The ID of the updated slot.
     * @param updatedSlotInfo A Bundle containing information like insertion status and slot type;
     *                        at least contain keys defined in @CamConstants.CamSlotInfoBundleKey.
     */
    void onSlotInfoChanged(int slotId, in Bundle updatedSlotInfo);
}
