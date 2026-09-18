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

package android.media.tv.extension.signal;

/**
* @hide
*/
oneway interface ITunerFrontendSignalInfoListener {
    /**
     * Called when the tuner's frontend signal status changes.
     *
     * @param frontendStatus An int value {@link SignalConstant.FrontendStatus}
     *        containing the updated frontend status, indicating whether the
     *        signal is locked, tuning, untuned, or unlocked.
     */
    void onFrontendStatusChanged(int frontendStatus);
}
