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

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.screencapture.common.ScreenCaptureComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.data.repository.screenCaptureComponentRepository
import com.android.systemui.screencapture.ui.ScreenCaptureUi
import com.android.systemui.screencapture.ui.screenCaptureUiFactory
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import kotlinx.coroutines.CoroutineScope

val Kosmos.screenCaptureComponentInteractor by
    Kosmos.Fixture {
        ScreenCaptureComponentInteractor(
            dispatcherContext = testDispatcher,
            repository = screenCaptureComponentRepository,
            screenCaptureUiInteractor = screenCaptureUiInteractor,
            componentBuilder = FakeScreenCaptureComponentBuilder(this),
            screenRecordingServiceInteractor = screenRecordingServiceInteractor,
        )
    }

private class FakeScreenCaptureComponentBuilder(private val kosmos: Kosmos) :
    ScreenCaptureComponent.Builder {

    private lateinit var scope: CoroutineScope
    private lateinit var parameters: ScreenCaptureUiParameters

    override fun setScope(scope: CoroutineScope): ScreenCaptureComponent.Builder {
        this.scope = scope
        return this
    }

    override fun setParameters(
        parameters: ScreenCaptureUiParameters
    ): ScreenCaptureComponent.Builder {
        this.parameters = parameters
        return this
    }

    override fun build(): ScreenCaptureComponent =
        object : ScreenCaptureComponent {
            override fun coroutineScope(): CoroutineScope = scope

            override fun screenCaptureUiFactory(): ScreenCaptureUi.Factory =
                kosmos.screenCaptureUiFactory
        }
}
