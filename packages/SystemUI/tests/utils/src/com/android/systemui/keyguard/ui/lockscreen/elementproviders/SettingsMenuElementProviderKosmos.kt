/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.lockscreen.elementproviders

import android.content.testableContext
import com.android.systemui.accessibility.domain.interactor.accessibilityInteractor
import com.android.systemui.common.ui.domain.interactor.configurationInteractor
import com.android.systemui.haptics.vibratorHelper
import com.android.systemui.keyguard.domain.interactor.keyguardTouchHandlingInteractor
import com.android.systemui.keyguard.ui.composable.elements.SettingsMenuElementProvider
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSettingsMenuViewModel
import com.android.systemui.keyguard.ui.viewmodel.keyguardTouchHandlingViewModelFactory
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.plugins.activityStarter

val Kosmos.settingsMenuElementProvider by
    Kosmos.Fixture {
        SettingsMenuElementProvider(
            context = testableContext,
            viewModel =
                KeyguardSettingsMenuViewModel(
                    interactor = keyguardTouchHandlingInteractor,
                    configurationInteractor = configurationInteractor,
                    accessibilityInteractor = accessibilityInteractor,
                ),
            touchHandlingViewModelFactory = keyguardTouchHandlingViewModelFactory,
            vibratorHelper = vibratorHelper,
            activityStarter = activityStarter,
        )
    }
