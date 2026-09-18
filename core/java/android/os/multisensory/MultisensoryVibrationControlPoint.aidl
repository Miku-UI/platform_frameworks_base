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
* Define the properties of a Multisensory vibration at a point in time.
*
* @hide
*/
parcelable MultisensoryVibrationControlPoint {
    /**
    * The amplitude of the vibration in the range from 0 to 1.
    *
    * A value of 1 represents the maximum vibration acceleration at the specific frequency of this
    * control point, and zero represents the vibrator turned off.
    */
    float amplitude;

    /**
    * The frequency of the vibration at this particular control point in Hertz.
    */
    float frequencyHz;

    /**
    * The timestamp of this control point in milliseconds, relative the beginning of the vibration.
    */
    int timeMillis;
}
