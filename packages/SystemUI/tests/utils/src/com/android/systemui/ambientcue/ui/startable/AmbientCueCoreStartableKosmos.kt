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

package com.android.systemui.ambientcue.ui.startable

import android.content.om.OverlayManager
import android.view.windowManager
import com.android.systemui.ambientcue.domain.interactor.ambientCueInteractor
import com.android.systemui.ambientcue.ui.view.ambientCueWindowRootView
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.backgroundScope
import org.mockito.kotlin.mock

val Kosmos.mockOverlayManager by Fixture { mock<OverlayManager>() }

val Kosmos.ambientCueCoreStartable by Fixture {
    AmbientCueCoreStartable(
        windowManager = windowManager,
        ambientCueInteractor = ambientCueInteractor,
        ambientCueWindowRootView = ambientCueWindowRootView,
        mainScope = applicationCoroutineScope,
        backgroundScope = backgroundScope,
        overlayManager = mockOverlayManager,
    )
}
