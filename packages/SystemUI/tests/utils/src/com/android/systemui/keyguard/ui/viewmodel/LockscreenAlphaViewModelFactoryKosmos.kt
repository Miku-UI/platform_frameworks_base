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

package com.android.systemui.keyguard.ui.viewmodel

import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.minmode.minModeManager
import com.android.systemui.shade.ui.viewmodel.notificationShadeWindowModel
import java.util.Optional

val Kosmos.lockscreenAlphaViewModelFactory by Fixture {
    object : LockscreenAlphaViewModel.Factory {
        override fun create(viewStateAccessor: ViewStateAccessor): LockscreenAlphaViewModel {
            return LockscreenAlphaViewModel(
                transitionInteractor = keyguardTransitionInteractor,
                alternateBouncerToAodTransitionViewModel = alternateBouncerToAodTransitionViewModel,
                minModeManager = Optional.of(minModeManager),
                notificationShadeWindowModel = notificationShadeWindowModel,
                alternateBouncerToGoneTransitionViewModel =
                    alternateBouncerToGoneTransitionViewModel,
                alternateBouncerToLockscreenTransitionViewModel =
                    alternateBouncerToLockscreenTransitionViewModel,
                alternateBouncerToOccludedTransitionViewModel =
                    alternateBouncerToOccludedTransitionViewModel,
                aodToLockscreenTransitionViewModel = aodToLockscreenTransitionViewModel,
                aodToOccludedTransitionViewModel = aodToOccludedTransitionViewModel,
                dozingToLockscreenTransitionViewModel = dozingToLockscreenTransitionViewModel,
                dozingToOccludedTransitionViewModel = dozingToOccludedTransitionViewModel,
                lockscreenToAodTransitionViewModel = lockscreenToAodTransitionViewModel,
                lockscreenToDozingTransitionViewModel = lockscreenToDozingTransitionViewModel,
                lockscreenToOccludedTransitionViewModel = lockscreenToOccludedTransitionViewModel,
                occludedToAlternateBouncerTransitionViewModel =
                    occludedToAlternateBouncerTransitionViewModel,
                occludedToAodTransitionViewModel = occludedToAodTransitionViewModel,
                occludedToDozingTransitionViewModel = occludedToDozingTransitionViewModel,
                occludedToLockscreenTransitionViewModel = occludedToLockscreenTransitionViewModel,
                offToLockscreenTransitionViewModel = offToLockscreenTransitionViewModel,
                keyguardInteractor = keyguardInteractor,
                viewStateAccessor = viewStateAccessor,
            )
        }
    }
}
