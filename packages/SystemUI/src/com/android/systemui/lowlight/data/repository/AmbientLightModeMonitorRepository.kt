/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.lowlight.data.repository

import com.android.systemui.lowlight.AmbientLightModeMonitor
import kotlinx.coroutines.flow.Flow

/**
 * {@link AmbientLightModeMonitorRepository} houses information around the {@link
 * AmbientLightModeMonitor}. This may be device dependent based on the availability of sensors and
 * the desired behavior, such as switching between monitors based on condition.
 */
interface AmbientLightModeMonitorRepository {
    /** Tracks the {@link AmbientLightModeMonitor} that should be used at the current moment. */
    val currentMonitor: Flow<AmbientLightModeMonitor?>
}
