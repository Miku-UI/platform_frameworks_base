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

import android.media.tv.extension.signal.IAudioSignalInfoListener;
import android.os.Bundle;

/**
 * Audio signal info extracted from driver module.
 * Client app should use TvTrackInfo to obatin information from SI/PSI tables.
 * @hide
 */
interface IAudioSignalInfo {
    /**
     * Gets audio signal information for the session.
     *
     * @param sessionToken A unique token created by the TIS to identify the session.
     * @return A Bundle containing the audio signal information from driver module and must contain
     *         keys defined in @SignalConstant.AudioSignalInfoKeys.
     */
    Bundle getAudioSignalInfo(String sessionToken);
    /**
     * Notify TIS whether user selects audio track via mts button on the remote control.
     *
     * @param mtsFlag true if the current track was selected via the MTS button, false otherwise.
     */
    void notifyMtsSelectTrackFlag(boolean mtsFlag);
    /**
     * Gets the audio track id selected via mts.
     *
     * @return The string ID of the MTS-selected audio track.
     */
    String getMtsSelectedTrackId();
    /**
     * Registers a listener to receive notifications when audio signal information is updated.
     *
     * @param clientToken A token to identify the client registering the listener.
     * @param listener    The listener instance to be called with updates.
     */
    void addAudioSignalInfoListener(String clientToken, in IAudioSignalInfoListener listener);
    /**
     * Unregisters a previously added listener for audio signal information updates.
     *
     * @param listener The listener instance to remove.
     */
    void removeAudioSignalInfoListener(in IAudioSignalInfoListener listener);
}
