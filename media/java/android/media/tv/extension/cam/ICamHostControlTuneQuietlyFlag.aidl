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

import android.media.tv.extension.cam.ICamHostControlTuneQuietlyFlagListener;
import android.os.Bundle;

/**
 * @hide
 */
interface ICamHostControlTuneQuietlyFlag {
    /**
     * Registers a listener to receive notifications about changes to the Host Control
     * tune_quietly flag.
     *
     * @param listener The ICamHostControlTuneQuietlyFlagListener to add.
     */
    void addHcTuneQuietlyFlagListener(ICamHostControlTuneQuietlyFlagListener listener);
    /**
     * Unregisters a listener to stop monitoring changes to the Host Control tune_quietly flag.
     *
     * @param listener The ICamHostControlTuneQuietlyFlagListener to remove.
     */
    void removeHcTuneQuietlyFlagListener(ICamHostControlTuneQuietlyFlagListener listener);
    /**
     * Retrieves the current value of the Host Control tune_quietly flag for a specific session.
     *
     * @param sessionToken The unique token that identifies the session.
     * @return @CamConstants.TuneQuietlyFlag to indicate quiet tune or normal tune.
     */
    int getHcTuneQuietlyFlag(String sessionToken);
}
