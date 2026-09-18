/**
 * Copyright (c) 2026, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os.multisensory;

import android.os.multisensory.IMultisensoryRealtimeSession;

/**
* Callback to report the capabilities of the IMultisensoryPlayer
* @hide
*/
oneway interface IMultisensoryPlayerCapabilitiesCallback {

    // If set, the player supports token sessions.
    /**
    * If set, the player supports token sessions. This means that the player can play a base
    * waveform with a given scale and frequency, and can modify the waveform in realtime by
    * applying MultisensoryContinuousEffectModifier's to the ongoing waveform.
    */
    const int FLAG_SUPPORTS_SESSIONS = 0x20;

    /**
    * Report that the player capabilities have been updated.
    *
    * @param capability Indicates the capability that the player supports.
    */
    void onCapabilitiesUpdated(in long playerId, in int capabilityFlag) = 1;
}

