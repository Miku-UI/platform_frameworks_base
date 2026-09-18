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

import android.media.tv.extension.signal.IHdmiSignalInfoListener;
import android.os.Bundle;

/**
 * Interface containing information extracted from HDMI signal, such as VRR, low latency mode, pc
 * mode. This interface should be replaced by MediaQualityFramework once it is stable.
 * @hide
 */
interface IHdmiSignalInterface {
    /**
     * Registers a listener to receive notifications about HDMI signal information updates.
     *
     * @param inputId The input id used for TIS to identify the HDMI input
     * @param listener The listener instance that will be called when signal information changes.
     */
    void addHdmiSignalInfoListener(String inputId, in IHdmiSignalInfoListener listener);
    /**
     * Unregisters a previously added listener for HDMI signal information updates.
     *
     * @param inputId The input id used for TIS to identify the HDMI input.
     * @param listener The listener instance to be removed.
     */
    void removeHdmiSignalInfoListener(String inputId, in IHdmiSignalInfoListener listener);
    /**
     * Retrieves the current HDMI signal information for the given session.
     *
     * @param sessionToken The token to identify TIS session.
     * @return A Bundle containing the following keys:
      * <ul>
      * <li>KEY_VRR_STATUS: The current Variable Refresh Rate (VRR) status.</li>
      * <li>KEY_ALARM_STATUS: The status of the alarm.</li>
      * <li>KEY_CONTENT_TYPE: The type of content being transmitted.</li>
      * <li>KEY_LOW_LATENCY_MODE: The status of the Low Latency Mode.</li>
      * <li>KEY_VIDEO_DV_GAME: Indicates if the content is a Dolby Vision game.</li>
      * <li>KEY_AUTO_PC_MODE: The status of the automatic PC display mode.</li>
      * </ul>
      */
    Bundle getHdmiSignalInfo(String sessionToken);
    /**
     * Enables or disables low-latency decoding mode for the session.
     *
     * @param sessionToken The token to identify TIS session.
     * @param mode 1 to enable low-latency mode and 0 to disable it.
     */
    void setLowLatency(String sessionToken, int mode);
    /**
     * Enables or disables forced Variable Refresh Rate (VRR) mode for the session.
     *
     * @param sessionToken The per-session token provided by the host.
     * @param mode 1 to enable forced VRR and 0 to disable it.
     */
    void setForceVrr(String sessionToken, int mode);
}
