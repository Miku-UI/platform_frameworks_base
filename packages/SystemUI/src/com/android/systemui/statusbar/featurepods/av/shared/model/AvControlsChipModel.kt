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

package com.android.systemui.statusbar.featurepods.av.shared.model

/**
 * Model used to display a VC/Privacy control chip in the status bar.
 *
 * The class currently wraps only the SensorActivityModel, however in future it is intended to
 * contain more elements as we add functionality into the status bar chip.
 */
data class AvControlsChipModel(
    val sensorActivityModel: SensorActivityModel = SensorActivityModel.Inactive
)
