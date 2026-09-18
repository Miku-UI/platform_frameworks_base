/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.os.multisensory;

/**
* Represents a vibration amplitude and frequency modification to a MultisensoryContinuousEffect.
*
* @hide
*/
parcelable MultisensoryContinuousEffectVibrationModifier {
    /**
    * Represents the amplitude (from 0 to 1) that the effect should go to, where 1 represents the
    * maximum amplitude of vibration at the end frequency after this modifier is applied, and zero
    * represents the vibrator turned off.
    */
    float amplitude;

    /**
    * A frequency in Hertz that the vibration should go to.
    */
    float frequencyHz;

    /**
    * A ramp in millisenconds that specifies the period of time over which the amplitude and
    * frequency modification should be applied.
    */
    int rampDurationMillis;
}
