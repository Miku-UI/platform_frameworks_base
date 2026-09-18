/*
 * Copyright 2025 The Android Open Source Project
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

package android.hardware.input;

/** @hide */
parcelable VirtualGamepadConfig {
    int vendorId;
    int productId;
    // The name of the created input device
    String name;
    // The display id that should be associated with the created input device
    int associatedDisplayId;
    // Whether to register trigger axes (LTRIGGER/RTRIGGER). Set this to false if targeting games
    // that do not correctly handle analog triggers and digital button presses (L2/R2)
    // simultaneously, as the presence of these axes can sometimes be misinterpreted.
    boolean registerTriggerAxes;
}
