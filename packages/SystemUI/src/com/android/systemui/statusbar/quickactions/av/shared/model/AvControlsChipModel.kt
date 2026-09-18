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

package com.android.systemui.statusbar.quickactions.av.shared.model

import android.graphics.drawable.Drawable

/** Model used to display a VC/Privacy control chip in the status bar. */
data class AvControlsChipModel(
    val sensorActivityModel: SensorActivityModel = SensorActivityModel.Inactive,
    val sensorAccessList: List<SensorAccess> = emptyList(),
)

enum class Sensor {
    CAMERA,
    MICROPHONE,
}

/** Information about what app accesses which sensor. */
data class SensorAccess(
    val packageName: String,
    val appName: String,
    val sensor: Sensor,
    val icon: Drawable? = null,
)
