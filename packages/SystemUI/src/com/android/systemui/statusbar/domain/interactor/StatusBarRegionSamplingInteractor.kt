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
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.StatusBarAlwaysUseRegionSampling
import com.android.systemui.statusbar.StatusBarRegionSampling
import com.android.systemui.statusbar.data.repository.StatusBarModeRepositoryStore
import com.android.systemui.uimode.data.repository.ForceInvertRepository
import dagger.Binds
import dagger.Module
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Communicates sampled status bar lightness info to [StatusBarModeRepositoryStore]. */
interface StatusBarRegionSamplingInteractor {

    /** Flow is `true` if region sampling should be enabled, otherwise `false`. */
    val isRegionSamplingEnabled: Flow<Boolean>

    /**
     * Set the sampled [AppearanceRegion]s for the status bar.
     *
     * Provide an empty list to clear the sampled [AppearanceRegion]s, which will cause the status
     * bar to use [AppearanceRegion]s from `wm/DisplayPolicy` rather than sampled regions.
     */
    fun setSampledAppearanceRegions(displayId: Int, appearanceRegions: List<AppearanceRegion?>)
}

@SysUISingleton
class StatusBarRegionSamplingInteractorImpl
@Inject
constructor(
    private val statusBarModeRepositoryStore: StatusBarModeRepositoryStore,
    forceInvertRepository: ForceInvertRepository,
) : StatusBarRegionSamplingInteractor {

    override val isRegionSamplingEnabled =
        if (StatusBarAlwaysUseRegionSampling.isEnabled) {
            flowOf(true)
        } else if (StatusBarRegionSampling.isEnabled) {
            forceInvertRepository.isForceInvertDark
        } else {
            flowOf(false)
        }

    override fun setSampledAppearanceRegions(
        displayId: Int,
        appearanceRegions: List<AppearanceRegion?>,
    ) {
        if (!StatusBarAlwaysUseRegionSampling.isAnyRegionSamplingEnabled) {
            return
        }
        val statusBarModePerDisplayRepository = statusBarModeRepositoryStore.forDisplay(displayId)
        statusBarModePerDisplayRepository?.setSampledAppearanceRegions(
            appearanceRegions.filterNotNull()
        )
    }
}

@Module
interface StatusBarRegionSamplingInteractorModule {
    @Binds
    fun bindImpl(impl: StatusBarRegionSamplingInteractorImpl): StatusBarRegionSamplingInteractor
}
