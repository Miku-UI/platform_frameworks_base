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

import androidx.compose.runtime.getValue
import com.android.systemui.keyguard.ui.composable.elements.UnfoldTranslations
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.unfold.domain.interactor.UnfoldTransitionInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class LockscreenLowerRegionViewModel
@AssistedInject
constructor(unfoldTransitionInteractor: UnfoldTransitionInteractor) : HydratedActivatable() {

    val unfoldTranslations: UnfoldTranslations =
        object : UnfoldTranslations {
            override val start: Float by
                unfoldTransitionInteractor
                    .unfoldTranslationX(isOnStartSide = true)
                    .hydratedStateOf(traceName = "unfoldTranslations.start", initialValue = 0f)

            override val end: Float by
                unfoldTransitionInteractor
                    .unfoldTranslationX(isOnStartSide = false)
                    .hydratedStateOf(traceName = "unfoldTranslations.end", initialValue = 0f)
        }

    @AssistedFactory
    interface Factory {
        fun create(): LockscreenLowerRegionViewModel
    }
}
