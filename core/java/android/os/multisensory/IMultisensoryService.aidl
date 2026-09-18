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

import android.os.multisensory.IMultisensoryPlayer;
import android.os.multisensory.MultisensoryContinuousEffectModifier;

/**
* The Multisensory Service is the system service responsible for delivering audio-haptic feedback
* from the Multisensory Design System (MSDS).
*
* The service provides the capability to play feedback in a fully tokenized architecture, allowing
* clients to deliver consistent feedback compliant with the MSDS design language.
*
* @hide
*/
oneway interface IMultisensoryService {

    /**
    * Play feedback for a given multisensory token constant
    */
    void playToken(in int tokenConstant) = 1;

    /**
    * Open a continuous feedback session for a token by loading any necessary resources for realtime
    * haptic control.
    */
    void openContinuousFeedbackForToken(in int tokenConstant) = 2;

    /**
    * Start the continuous haptic feedback playback for a token after the session is openened.
    */
    void startContinuousFeedbackForToken(in int tokenConstant) = 3;

    /**
    * Modify the ongoing feedback session of a token with a set of modifiers
    */
    void modifyContinuousFeedbackForToken(in int tokenConstant,
        in List<MultisensoryContinuousEffectModifier> modifiers) = 4;

    /**
    * Stop the continuous feedback session of a token.
    */
    void stopContinuousFeedbackForToken(in int tokenConstant) = 5;

    /**
    * Set a remote player for the deliver of audio-haptic feedback from tokens in MSDS.
    *
    * Usages of this API must occur after the system server has fully initialized the
    * IMultisensoryService. This will occur during the PHASE_DEVICE_SPECIFIC_SERVICES_READY phase
    * of the system server initialization cycle. Once installed, the new player will be called to
    * load resources for all tokens (see IMultisensoryPlayer#load).
    */
    @EnforcePermission("REMOTE_MULTISENSORY_PLAYBACK")
    void setPlayer(in IMultisensoryPlayer player) = 6;
}