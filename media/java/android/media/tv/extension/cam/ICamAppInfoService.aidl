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

import android.media.tv.extension.cam.ICamAppInfoListener;
import android.os.Bundle;

/**
 * @hide
 */
interface ICamAppInfoService {
/**
     * Registers an ICamAppInfoListener to receive notifications about CICAM
     * application information updates.
     *
     * @param listener The ICamAppInfoListener to be added.
     */
    void addCamAppInfoListener(ICamAppInfoListener listener);
    /**
     * Unregisters an ICamAppInfoListener to stop receiving notifications about
     * CICAM application information updates.
     *
     * @param listener The ICamAppInfoListener to be removed.
     */
    void removeCamAppInfoListener(ICamAppInfoListener listener);
    /**
     * Retrieves the application information for a specific CICAM.
     *
     * @param slotId The ID of the corresponding CICAM slot.
     * @param appInfo An output bundle with the application information upon success retrieval, the
     *                bundle containing keys as defined in @CamConstants.CamAppInfoBundleKey.
     * @return An integer status code in @CamConstants.CamAppInfoResult indicating the result.
     */
    int getCamAppInfo(int slotId, out Bundle appInfo);
}
