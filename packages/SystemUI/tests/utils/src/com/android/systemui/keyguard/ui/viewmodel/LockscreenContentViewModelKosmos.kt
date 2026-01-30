/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.viewmodel

import com.android.systemui.deviceentry.domain.interactor.deviceEntryBypassInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceEntryUdfpsInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardBlueprintInteractor
import com.android.systemui.keyguard.shared.transition.KeyguardTransitionAnimationCallback
import com.android.systemui.keyguard.shared.transition.keyguardTransitionAnimationCallbackDelegator
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationStackAppearanceInteractor
import com.android.systemui.wallpapers.domain.interactor.wallpaperFocalAreaInteractor

val Kosmos.lockscreenContentViewModelFactory by Fixture {
    object : LockscreenContentViewModel.Factory {
        override fun create(
            keyguardTransitionAnimationCallback: KeyguardTransitionAnimationCallback,
            viewState: ViewStateAccessor,
        ): LockscreenContentViewModel {
            return LockscreenContentViewModel(
                interactor = keyguardBlueprintInteractor,
                touchHandlingFactory = keyguardTouchHandlingViewModelFactory,
                shadeModeInteractor = shadeModeInteractor,
                deviceEntryBypassInteractor = deviceEntryBypassInteractor,
                deviceEntryUdfpsInteractor = deviceEntryUdfpsInteractor,
                keyguardTransitionAnimationCallbackDelegator =
                    keyguardTransitionAnimationCallbackDelegator,
                keyguardTransitionAnimationCallback = keyguardTransitionAnimationCallback,
                wallpaperFocalAreaInteractor = wallpaperFocalAreaInteractor,
                notificationStackAppearanceInteractor = notificationStackAppearanceInteractor,
                lockscreenAlphaViewModelFactory = lockscreenAlphaViewModelFactory,
                viewStateAccessor = viewState,
            )
        }
    }
}
