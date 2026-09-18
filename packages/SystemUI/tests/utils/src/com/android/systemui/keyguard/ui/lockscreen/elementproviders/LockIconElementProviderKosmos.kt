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
import com.android.systemui.classifier.falsingManager
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.haptics.msdl.msdlPlayer
import com.android.systemui.haptics.vibratorHelper
import com.android.systemui.keyguard.ui.composable.elements.LockIconElementProvider
import com.android.systemui.keyguard.ui.viewmodel.deviceEntryBackgroundViewModel
import com.android.systemui.keyguard.ui.viewmodel.deviceEntryForegroundIconViewModel
import com.android.systemui.keyguard.ui.viewmodel.deviceEntryIconViewModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.window.domain.interactor.windowRootViewBlurInteractor
import kotlinx.coroutines.Dispatchers

val Kosmos.lockIconElementProvider by
    Kosmos.Fixture {
        LockIconElementProvider(
            context = testableContext,
            applicationScope = applicationCoroutineScope,
            mainDispatcher = Dispatchers.Main,
            windowManager = testableWindowManager,
            windowRootViewBlurInteractor = windowRootViewBlurInteractor,
            featureFlags = featureFlagsClassic,
            deviceEntryIconViewModel = { deviceEntryIconViewModel },
            deviceEntryForegroundViewModel = { deviceEntryForegroundIconViewModel },
            deviceEntryBackgroundViewModel = { deviceEntryBackgroundViewModel },
            falsingManager = { falsingManager },
            vibratorHelper = { vibratorHelper },
            msdlPlayer = { msdlPlayer },
            logBuffer = logcatLogBuffer("LockscreenBuffer"),
        )
    }
