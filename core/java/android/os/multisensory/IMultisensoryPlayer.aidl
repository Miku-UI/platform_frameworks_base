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

import android.media.AudioAttributes;
import android.os.multisensory.IMultisensoryPlayerCapabilitiesCallback;
import android.os.multisensory.IMultisensoryPlayerLoadCallback;
import android.os.multisensory.IMultisensoryPlayerSessionCallback;
import android.os.multisensory.MultisensoryContinuousEffect;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;

/**
* A player that delivers audio-haptic feedback in the Multisensory Design System (MSDS).
*
* @hide
*/
oneway interface IMultisensoryPlayer {

    /**
    * Set the ID assigned to this player when it was registered in the MultisensoryService.
    *
    * @param playerId The ID for this player.
    */
    void setPlayerId(in long playerId) = 1;

    /**
    * Load the resources for a given tokenConstant. The token maps to the given haptic effect and
    * an optional audio effect.
    *
    * @param tokenConstant. The numerical constant that identifies an MSDS token.
    * @param vibrationEffect. The VibrationEffect that represents a single, "one-shot" vibration
    *   for the token.
    * @param audioEffect. Optional URI for an audio file to play in sync with the haptic effect.
    * @param callback. Callback to report the result of the load operation back to
    *   MultisensoryService.
    */
    void load(in int tokenConstant, in VibrationEffect vibrationEffect,
        in @nullable String audioEffect, in IMultisensoryPlayerLoadCallback callback) = 2;

    /**
    * Play the feedback for a token as a single event.
    *
    * @param tokenConstant. The constant for the MSDS token.
    * @param vibrationAttributes. VibrationAttributes for the vibration.
    * @param AudioAttributes. Optional audio attributes if the token specified audio feedback when
    *   the effect was loaded via load().
    */
    void play(in int tokenConstant, in VibrationAttributes vibrationAttributes,
        in @nullable AudioAttributes audioAttributes) = 3;

    /**
    * Cancel any ongoing effect in this player.
    */
    void cancel() = 4;

    /**
    * Query the capabilities of the player.
    *
    * @param callback. The callback used to report the capabilities back to the service.
    */
    void getCapabilities(IMultisensoryPlayerCapabilitiesCallback callback) = 5;

    /**
    * Open a realtime and continuous feedback session for the given token constant with the given
    * vibration and audio attributes and the given MultisensoryContinuousEffect.
    *
    * The implementation is tasked to load any necessary resources to begin a stream of haptics or
    * audio-haptic data associated with the tokenConstant and described by the
    * MultisensoryContinuousEffect.
    *
    * @param tokenConstant. The numerical constant that identifies the MSDS token.
    * @param baseEffect. The MultisensoryContinuousEffect to be played and modified in realtime
    *   during the session.
    * @param vibrationAttributes. VibrationAttributes for the vibration part of the continuous
    *    effect.
    * @param AudioAttributes. Optional audio attributes if the continuous effect also contains audio
    *   feedback.
    * @param callback. The callback used to report a IMultisensoryRealtimeSession handle.
    */
    void openRealtimeSession(int tokenConstant, in MultisensoryContinuousEffect baseEffect,
            in VibrationAttributes vibrationAttributes,
            in @nullable AudioAttributes audioAttributes,
            in IMultisensoryPlayerSessionCallback callback) = 6;
}
