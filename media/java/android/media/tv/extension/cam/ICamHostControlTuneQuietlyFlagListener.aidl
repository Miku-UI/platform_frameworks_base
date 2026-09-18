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

/**
 * @hide
 */
oneway interface ICamHostControlTuneQuietlyFlagListener {
    /**
     * Called when the Host Control tune_quietly flag has changed.
     *
     * @param sessionToken The unique token that identifies the session, which was
     *                     established during the initial tune request.
     * @param tuneQuietlyFlag @CamConstants.TuneQuietlyFlag to represent the new state of the flag.
     */
    void onHcTuneQuietlyFlagChanged(String sessionToken, int tuneQuietlyFlag);
}
