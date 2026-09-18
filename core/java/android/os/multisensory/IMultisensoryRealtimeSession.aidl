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

import android.os.multisensory.IMultisensoryPlayerSessionCallback;
import android.os.multisensory.MultisensoryContinuousEffectModifier;

/**
* A handle of a realtime feedback session in the Multisensory Design System (MSDS)
*
* A session allows an ongoing MultisensoryContinuousEffect to be manipulated in realtime, delivering
* audio-haptic feedback changes in realtime.
*
* @hide
*/
oneway interface IMultisensoryRealtimeSession {

    /**
    * Start the realtime session.
    *
    * @param callback. The callback used to report the result of the start operation
    */
    void start(in IMultisensoryPlayerSessionCallback callback) = 1;

    /**
    * Update the realtime session.
    *
    * @param modifiers. The list of MultisensoryContinuousEffectModifier to manipulate the ongoing
    *   MultisensoryContinuousEffect in the session.
    */
    void update(in List<MultisensoryContinuousEffectModifier> modifiers) = 2;

    /**
    * Close the realtime session.
    *
    * @param callback. The callback used to report the result of the close operation
    */
    void close(in IMultisensoryPlayerSessionCallback callback) = 3;
}