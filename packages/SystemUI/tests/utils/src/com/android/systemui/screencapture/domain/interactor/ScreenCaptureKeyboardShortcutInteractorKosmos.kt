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

package com.android.systemui.screencapture.domain.interactor

import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.screencapture.record.domain.interactor.screenCaptureRecordFeaturesInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.largeScreenCaptureFeaturesInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.screenshotInteractor
import com.android.systemui.statusbar.policy.domain.interactor.userSetupInteractor
import com.android.systemui.user.data.repository.userRepository
import com.android.systemui.user.domain.interactor.fakeHeadlessSystemUserMode

val Kosmos.screenCaptureKeyboardShortcutInteractor: ScreenCaptureKeyboardShortcutInteractor by
    Kosmos.Fixture {
        ScreenCaptureKeyboardShortcutInteractor(
            backgroundScope = backgroundScope,
            screenCaptureUiInteractor = screenCaptureUiInteractor,
            keyguardInteractor = keyguardInteractor,
            userRepository = userRepository,
            hsum = fakeHeadlessSystemUserMode,
            featuresInteractor = largeScreenCaptureFeaturesInteractor,
            screenCaptureRecordFeaturesInteractor = screenCaptureRecordFeaturesInteractor,
            screenshotInteractor = screenshotInteractor,
            userSetupInteractor = userSetupInteractor,
        )
    }
