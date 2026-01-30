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

import com.android.systemui.screencapture.cast.ScreenCaptureCastUiComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.record.ScreenCaptureRecordUiComponent
import com.android.systemui.screencapture.sharescreen.ScreenCaptureShareScreenUiComponent
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

/**
 * Top level Dagger Module for Screen Capture.
 *
 * Injects Screen Capture Subcomponents into the System UI dagger graph via
 * [SystemUIModule][com.android.systemui.dagger.SystemUIModule].
 */
@Module(
    subcomponents =
        [
            ScreenCaptureCastUiComponent::class,
            ScreenCaptureUiComponent::class,
            ScreenCaptureRecordUiComponent::class,
            ScreenCaptureShareScreenUiComponent::class,
        ]
)
interface ScreenCaptureUiModule {
    @Binds
    @IntoMap
    @ScreenCaptureTypeKey(ScreenCaptureType.CAST)
    fun bindCastComponentBuilder(
        impl: ScreenCaptureCastUiComponent.Builder
    ): ScreenCaptureUiComponent.Builder

    @Binds
    @IntoMap
    @ScreenCaptureTypeKey(ScreenCaptureType.RECORD)
    fun bindRecordComponentBuilder(
        impl: ScreenCaptureRecordUiComponent.Builder
    ): ScreenCaptureUiComponent.Builder

    @Binds
    @IntoMap
    @ScreenCaptureTypeKey(ScreenCaptureType.SHARE_SCREEN)
    fun bindShareScreenComponentBuilder(
        impl: ScreenCaptureShareScreenUiComponent.Builder
    ): ScreenCaptureUiComponent.Builder
}
