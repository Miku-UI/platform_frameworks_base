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

import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.ui.ScreenCaptureUi
import dagger.BindsInstance
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineScope

/**
 * Dagger Subcomponent interface for Screen Capture. It's alive while there is an ongoing Screen
 * Capture or the UI is visible.
 */
@ScreenCaptureScope
@Subcomponent(modules = [ScreenCaptureUiModule::class])
interface ScreenCaptureComponent {

    @ScreenCapture fun coroutineScope(): CoroutineScope

    fun screenCaptureUiFactory(): ScreenCaptureUi.Factory

    /**
     * Dagger Subcomponent Builder for [ScreenCaptureComponent].
     *
     * Actual Subcomponent Builders should extend this interface and override [build] to return the
     * actual subcomponent type.
     */
    @Subcomponent.Builder
    interface Builder {

        /** The [CoroutineScope] to use coroutines limited to Screen Capture sessions. */
        @BindsInstance fun setScope(@ScreenCapture scope: CoroutineScope): Builder

        /** [ScreenCaptureUiParameters] that has been used to start capture flow. */
        @BindsInstance
        fun setParameters(@ScreenCapture parameters: ScreenCaptureUiParameters): Builder

        /**
         * Builds this [ScreenCaptureComponent]. Actual Subcomponent Builders should override this
         * method with their own version that returns the actual subcomponent type.
         */
        fun build(): ScreenCaptureComponent
    }
}
