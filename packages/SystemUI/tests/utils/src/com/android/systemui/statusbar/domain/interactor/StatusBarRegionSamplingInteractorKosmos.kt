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

package com.android.systemui.statusbar.domain.interactor

import com.android.internal.view.AppearanceRegion
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.data.repository.statusBarModeRepository
import com.android.systemui.uimode.data.repository.fakeForceInvertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

val Kosmos.statusBarRegionSamplingInteractor by
    Kosmos.Fixture<StatusBarRegionSamplingInteractor> {
        StatusBarRegionSamplingInteractorImpl(
            statusBarModeRepositoryStore = statusBarModeRepository,
            forceInvertRepository = fakeForceInvertRepository,
        )
    }

val Kosmos.fakeStatusBarRegionSamplingInteractor by
    Kosmos.Fixture { FakeStatusBarRegionSamplingInteractor() }

class FakeStatusBarRegionSamplingInteractor : StatusBarRegionSamplingInteractor {
    private val _isRegionSamplingEnabled = MutableStateFlow(false)
    override val isRegionSamplingEnabled: Flow<Boolean> = _isRegionSamplingEnabled.asStateFlow()
    var sampledAppearanceRegions: List<AppearanceRegion?>? = null

    fun setRegionSamplingEnabled(enabled: Boolean) {
        _isRegionSamplingEnabled.value = enabled
    }

    override fun setSampledAppearanceRegions(
        displayId: Int,
        appearanceRegions: List<AppearanceRegion?>,
    ) {
        sampledAppearanceRegions = appearanceRegions
    }
}
