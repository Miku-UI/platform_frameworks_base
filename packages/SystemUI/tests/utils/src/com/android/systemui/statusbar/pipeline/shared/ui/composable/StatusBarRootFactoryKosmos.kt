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

package com.android.systemui.statusbar.pipeline.shared.ui.composable

import com.android.systemui.clock.ui.viewmodel.clockViewModelFactory
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.media.controls.ui.controller.mediaHierarchyManager
import com.android.systemui.media.controls.ui.view.qsMediaHost
import com.android.systemui.media.remedia.ui.viewmodel.factory.mediaViewModelFactory
import com.android.systemui.plugins.fakeDarkIconDispatcher
import com.android.systemui.statusbar.events.domain.interactor.systemStatusEventAnimationInteractor
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.connectedDisplaysStatusBarNotificationIconViewStoreFactory
import com.android.systemui.statusbar.phone.ui.statusBarIconController
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.defaultDisplayHomeStatusBarViewModelFactory
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.homeStatusBarViewBinder
import com.android.systemui.statusbar.ui.viewmodel.statusBarRegionSamplingViewModelFactory
import org.mockito.kotlin.mock

val Kosmos.statusBarRootFactory by
    Kosmos.Fixture {
        StatusBarRootFactory(
            notificationIconsBinder = mock(),
            iconViewStoreFactory = connectedDisplaysStatusBarNotificationIconViewStoreFactory,
            clockViewModelFactory = clockViewModelFactory,
            darkIconManagerFactory = mock(),
            tintedIconManagerFactory = mock(),
            iconController = statusBarIconController,
            ongoingCallController = mock(),
            eventAnimationInteractor = systemStatusEventAnimationInteractor,
            mediaHierarchyManager = mediaHierarchyManager,
            mediaHost = qsMediaHost,
            mediaViewModelFactory = mediaViewModelFactory,
            darkIconDispatcher = fakeDarkIconDispatcher,
            homeStatusBarViewBinder = homeStatusBarViewBinder,
            homeStatusBarViewModelFactory = defaultDisplayHomeStatusBarViewModelFactory,
            statusBarRegionSamplingViewModelFactory = statusBarRegionSamplingViewModelFactory,
        )
    }
