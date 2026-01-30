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

package com.android.systemui.statusbar

import android.view.Choreographer
import android.view.View
import com.android.systemui.dump.dumpManager
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.shade.data.repository.fakeFocusedDisplayRepository
import com.android.systemui.shade.data.repository.shadeDisplaysRepository
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.statusbar.phone.biometricUnlockController
import com.android.systemui.statusbar.phone.dozeParameters
import com.android.systemui.statusbar.policy.keyguardStateController
import com.android.systemui.util.WallpaperController
import com.android.systemui.wallpapers.domain.interactor.wallpaperInteractor
import com.android.systemui.window.domain.interactor.windowRootViewBlurInteractor
import java.util.Optional
import org.mockito.kotlin.mock

var Kosmos.notificationShadeDepthController by Fixture {
    NotificationShadeDepthController(
            statusBarStateController = statusBarStateController,
            blurUtils = mock<BlurUtils>(),
            biometricUnlockController = biometricUnlockController,
            keyguardStateController = keyguardStateController,
            keyguardInteractor = keyguardInteractor,
            choreographer = mock<Choreographer>(),
            wallpaperController = mock<WallpaperController>(),
            wallpaperInteractor = wallpaperInteractor,
            notificationShadeWindowController = notificationShadeWindowController,
            dozeParameters = dozeParameters,
            shadeModeInteractor = shadeModeInteractor,
            windowRootViewBlurInteractor = windowRootViewBlurInteractor,
            appZoomOutOptional = Optional.empty(),
            shadeDisplaysRepository = { shadeDisplaysRepository },
            focusedDisplayRepository = fakeFocusedDisplayRepository,
            applicationScope = applicationCoroutineScope,
            desktopMode = Optional.empty(),
            dumpManager = dumpManager,
        )
        .apply { root = mock<View>() }
}
