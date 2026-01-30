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

package com.android.systemui.screencapture.common

import android.view.Display
import android.view.Window
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureContent
import com.android.systemui.screencapture.common.ui.compose.screenCaptureContents
import kotlinx.coroutines.CoroutineScope

fun Kosmos.screenCaptureUiComponentBuilder(
    type: ScreenCaptureType
): ScreenCaptureUiComponent.Builder =
    object : ScreenCaptureUiComponent.Builder {

        private lateinit var scope: CoroutineScope

        override fun setScope(scope: CoroutineScope): ScreenCaptureUiComponent.Builder {
            this.scope = scope
            return this
        }

        override fun setDisplay(display: Display): ScreenCaptureUiComponent.Builder = this

        override fun setWindow(window: Window?): ScreenCaptureUiComponent.Builder = this

        override fun build(): ScreenCaptureUiComponent =
            object : ScreenCaptureUiComponent {
                override val screenCaptureContent: ScreenCaptureContent
                    get() = screenCaptureContents.getValue(type)
            }
    }
