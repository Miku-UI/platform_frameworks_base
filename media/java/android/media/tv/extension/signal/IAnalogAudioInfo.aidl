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

import android.os.Bundle;

/**
 * Analog audio info extracted from driver module.
 * Client app should use TvTrackInfo to obatin information from SI/PSI tables.
 * @hide
 */
interface IAnalogAudioInfo {
   /**
     * Gets analog audio signal information for the session.
     *
     * @param sessionToken A unique token created by the TIS to identify the session.
     * @return A Bundle containing the audio signal information from driver module and may have key:
     * <ul>
     * <li>KEY_AUDIO_CODEC: The audio codec in use.</li>
     * <li>KEY_AUDIO_HAS_ATMOS: Boolean indicating if has a Dolby Atmos track.</li>
     * <li>KEY_AUDIO_FRONT_CH_NUM_MAP: The final combined mapping of front audio channels.</li>
     * <li>KEY_AUDIO_FRONT_CH_NUM: The number of front audio channels.</li>
     * <li>KEY_AUDIO_REAR_CH_NUM: The number of rear audio channels.</li>
     * <li>KEY_AUDIO_IS_LOW_FREQ_EFFECT: Boolean indicating if is Low Frequency Effect.</li>
     * <li>KEY_AUDIO_SIGNAL_STATUS: The general status of the audio signal.</li>
     * <li>KEY_AUDIO_SIGNAL_IS_PROGRESSIVE: Boolean indicating if the audio signal is
     * progressive.</li>
     * <li>KEY_AUDIO_SIGNAL_UNKNOWN_DATA: Any unknown data associated with the signal.</li>
     * <li>KEY_AUDIO_FRONT_CH_NUM_RAW: The raw number of front channels before processing.</li>
     * <li>KEY_AUDIO_REAR_CH_NUM_RAW: The raw number of rear channels before processing.</li>
     * <li>KEY_AUDIO_IS_LOW_FREQ_EFFECT_RAW: The raw LFE presence flag before processing.</li>
     * <li>KEY_AUDIO_CH_INFO: Detailed channel information.</li>
     * </ul>
     */
    Bundle getAnalogAudioInfo(String sessionToken);
}
