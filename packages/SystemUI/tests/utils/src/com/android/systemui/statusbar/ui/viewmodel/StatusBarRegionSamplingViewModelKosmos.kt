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

package com.android.systemui.statusbar.ui.viewmodel

import android.content.testableContext
import android.view.View
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.statusbar.domain.interactor.fakeStatusBarRegionSamplingInteractor
import com.android.systemui.statusbar.domain.interactor.statusBarRegionSamplingInteractor
import com.android.systemui.statusbar.ui.viewmodel.StatusBarRegionSamplingViewModel.RegionSamplingHelperFactory
import org.mockito.kotlin.mock

val Kosmos.mockStatusBarAttachStateView by Kosmos.Fixture { mock<View>() }
var Kosmos.mockStatusBarStartSideContainerView by Kosmos.Fixture { mock<View>() }
var Kosmos.mockStatusBarStartSideIconView by Kosmos.Fixture { mock<View>() }
var Kosmos.mockStatusBarEndSideContainerView by Kosmos.Fixture { mock<View>() }
var Kosmos.mockStatusBarEndSideIconView by Kosmos.Fixture { mock<View>() }
val Kosmos.mockRegionSamplingHelperFactory by Kosmos.Fixture { mock<RegionSamplingHelperFactory>() }

val Kosmos.statusBarRegionSamplingViewModel by
    Kosmos.Fixture {
        statusBarRegionSamplingViewModelFactory.create(
            displayId = testableContext.displayId,
            attachStateView = mockStatusBarAttachStateView,
            startSideContainerView = mockStatusBarStartSideContainerView,
            startSideIconView = mockStatusBarStartSideIconView,
            endSideContainerView = mockStatusBarEndSideContainerView,
            endSideIconView = mockStatusBarEndSideIconView,
            regionSamplingHelperFactory = mockRegionSamplingHelperFactory,
        )
    }

val Kosmos.statusBarRegionSamplingViewModelFactory: StatusBarRegionSamplingViewModel.Factory by
    Kosmos.Fixture {
        object : StatusBarRegionSamplingViewModel.Factory {
            override fun create(
                displayId: Int,
                attachStateView: View,
                startSideContainerView: View,
                startSideIconView: View,
                endSideContainerView: View,
                endSideIconView: View,
                regionSamplingHelperFactory: RegionSamplingHelperFactory,
            ): StatusBarRegionSamplingViewModel {
                return StatusBarRegionSamplingViewModel(
                    displayId = displayId,
                    attachStateView = attachStateView,
                    startSideContainerView = startSideContainerView,
                    startSideIconView = startSideIconView,
                    endSideContainerView = endSideContainerView,
                    endSideIconView = endSideIconView,
                    regionSamplingHelperFactory = regionSamplingHelperFactory,
                    statusBarRegionSamplingInteractor = fakeStatusBarRegionSamplingInteractor,
                    mainExecutor = fakeExecutor,
                    backgroundExecutor = fakeExecutor,
                    backgroundScope = backgroundScope,
                )
            }
        }
    }
