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

package com.android.systemui.shade.domain.interactor

import android.view.Display
import com.android.app.displaylib.PerDisplayRepository
import com.android.app.tracing.FlowTracing.traceEach
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel
import com.android.systemui.statusbar.data.repository.HomeStatusBarComponentsRepository
import com.android.systemui.statusbar.phone.PhoneStatusBarViewController
import com.android.systemui.statusbar.phone.fragment.dagger.HomeStatusBarComponent
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Provides the correct status bar components for the display that the notification shade is
 * currently on.
 */
@SysUISingleton
class ShadeStatusBarComponentsInteractor
@Inject
constructor(
    @Background private val bgScope: CoroutineScope,
    shadeDisplaysInteractor: Lazy<ShadeDisplaysInteractor>,
    homeStatusBarComponentsRepository: HomeStatusBarComponentsRepository,
    perDisplaySubcomponentRepository: PerDisplayRepository<SystemUIDisplaySubcomponent>,
) {

    private val shadeDisplayId: StateFlow<Int> =
        if (ShadeWindowGoesAround.isEnabled) {
            shadeDisplaysInteractor.get().displayId
        } else {
            MutableStateFlow(Display.DEFAULT_DISPLAY)
        }

    /**
     * Provides the [HomeStatusBarComponent] for the display the shade is currently on. Returns null
     * if no component is found for the current display.
     */
    private val homeStatusBarComponent: StateFlow<HomeStatusBarComponent?> =
        combine(shadeDisplayId, homeStatusBarComponentsRepository.componentsByDisplayId) {
                displayId,
                components ->
                components[displayId]
            }
            .traceEach("$TAG#homeStatusBarComponent", logcat = true)
            .stateIn(bgScope, SharingStarted.Eagerly, initialValue = null)

    /**
     * Provides the [PhoneStatusBarViewController] for the display the shade is currently on.
     * Returns null if no controller is found for the current display.
     */
    val phoneStatusBarViewController: StateFlow<PhoneStatusBarViewController?> =
        homeStatusBarComponent
            .map { it?.phoneStatusBarViewController }
            .traceEach("$TAG#phoneStatusBarViewController", logcat = true)
            .stateIn(bgScope, SharingStarted.Eagerly, initialValue = null)

    val ongoingActivityChipsViewModel: Flow<OngoingActivityChipsViewModel> =
        shadeDisplayId
            .map { displayId ->
                perDisplaySubcomponentRepository[displayId]?.ongoingActivityChipsViewModel
            }
            .filterNotNull()

    private companion object {
        const val TAG = "ShadeStatusBarComponentsInteractor"
    }
}
