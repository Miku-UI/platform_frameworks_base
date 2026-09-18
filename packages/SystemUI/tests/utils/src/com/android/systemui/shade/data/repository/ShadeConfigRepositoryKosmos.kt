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

package com.android.systemui.shade.data.repository

import android.content.res.mainResources
import com.android.systemui.common.ui.data.repository.configurationRepository
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.shared.settings.data.repository.secureSettingsRepository

var Kosmos.shadeConfigRepository: ShadeConfigRepository by
    Kosmos.Fixture {
        ShadeConfigRepository(
            backgroundDispatcher = testDispatcher,
            resources = mainResources,
            configurationRepository = configurationRepository,
            secureSettingsRepository = secureSettingsRepository,
            featureFlags = featureFlagsClassic,
        )
    }
