/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.data.repository

import com.android.internal.view.AppearanceRegion
import com.android.systemui.statusbar.data.model.StatusBarAppearance
import com.android.systemui.statusbar.data.model.StatusBarMode
import com.android.systemui.statusbar.phone.fragment.dagger.HomeStatusBarComponent
import kotlinx.coroutines.flow.MutableStateFlow

class FakeStatusBarModePerDisplayRepository : StatusBarModePerDisplayRepository {
    override val isTransientShown = MutableStateFlow(false)
    override val isInFullscreenMode = MutableStateFlow(false)
    override val statusBarAppearance = MutableStateFlow<StatusBarAppearance?>(null)
    override val statusBarMode = MutableStateFlow(StatusBarMode.TRANSPARENT)
    override val ongoingProcessRequiresStatusBarVisible = MutableStateFlow(false)
    var fakeSampledAppearanceRegions: List<AppearanceRegion>? = null

    override fun showTransient() {
        isTransientShown.value = true
    }

    fun abortTransient() {
        isTransientShown.value = false
    }

    override fun setOngoingProcessRequiresStatusBarVisible(requiredVisible: Boolean) {
        ongoingProcessRequiresStatusBarVisible.value = requiredVisible
    }

    override fun setSampledAppearanceRegions(appearanceRegions: List<AppearanceRegion>) {
        fakeSampledAppearanceRegions = appearanceRegions
    }

    override fun onStatusBarViewInitialized(component: HomeStatusBarComponent) {}
}
