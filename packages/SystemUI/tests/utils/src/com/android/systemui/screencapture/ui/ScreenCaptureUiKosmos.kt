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

package com.android.systemui.screencapture.ui

import android.content.applicationContext
import android.view.Display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.screencapture.common.screenCaptureUiComponentBuilder
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.ui.viewmodel.screenCaptureUiViewModelFactory
import com.android.systemui.settings.userTracker

val Kosmos.recordingScreenCaptureUi by
    Kosmos.Fixture {
        screenCaptureUiFactory.create(
            display = displayRepository.getDisplay(Display.DEFAULT_DISPLAY)!!,
            type = ScreenCaptureType.RECORD,
        )
    }
val Kosmos.screenCaptureUiFactory by
    Kosmos.Fixture {
        object : ScreenCaptureUi.Factory {
            override fun create(display: Display, type: ScreenCaptureType): ScreenCaptureUi =
                ScreenCaptureUi(
                    display = display,
                    type = type,
                    context = applicationContext,
                    userContextProvider = userTracker,
                    viewModelFactory = screenCaptureUiViewModelFactory,
                    componentBuilders =
                        ScreenCaptureType.entries.associateWith {
                            screenCaptureUiComponentBuilder(it)
                        },
                    defaultBuilder = { error("Provide one instead") },
                )
        }
    }
