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

package com.android.systemui.screencapture.sharescreen.ui.viewmodel

import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class ScreenCaptureShareScreenViewModel
@AssistedInject
constructor(interactor: ScreenCaptureUiInteractor) : HydratedActivatable() {
    val isLargeScreen: Boolean? by interactor.isLargeScreen.hydratedStateOf(null)

    @AssistedFactory
    interface Factory {
        fun create(): ScreenCaptureShareScreenViewModel
    }
}
