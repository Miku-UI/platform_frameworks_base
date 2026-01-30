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
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureContent
import dagger.BindsInstance
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineScope

/**
 * Dagger Subcomponent interface for Screen Capture UI.
 *
 * Actual Subcomponents should extend this interface and be listed as a subcomponent in
 * [ScreenCaptureUiModule].
 */
@ScreenCaptureUiScope
@Subcomponent(modules = [CommonModule::class, FallbackModule::class])
interface ScreenCaptureUiComponent {

    val screenCaptureContent: ScreenCaptureContent

    /**
     * Dagger Subcomponent Builder for [ScreenCaptureUiComponent].
     *
     * Actual Subcomponent Builders should extend this interface and override [build] to return the
     * actual subcomponent type.
     */
    @Subcomponent.Builder
    interface Builder {

        /** The [CoroutineScope] to use coroutines limited to Screen Capture sessions. */
        @BindsInstance fun setScope(@ScreenCaptureUi scope: CoroutineScope): Builder

        /** [Display] that hosts the Screen Capture UI. */
        @BindsInstance fun setDisplay(@ScreenCaptureUi display: Display): Builder

        /** [Window] that hosts the Screen Capture UI. */
        @BindsInstance fun setWindow(@ScreenCaptureUi window: Window?): Builder

        /**
         * Builds this [ScreenCaptureUiComponent]. Actual Subcomponent Builders should override this
         * method with their own version that returns the actual subcomponent type.
         */
        fun build(): ScreenCaptureUiComponent
    }
}
