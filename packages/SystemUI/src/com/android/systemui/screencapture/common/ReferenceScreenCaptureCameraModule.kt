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

import com.android.systemui.screencapture.record.camera.data.repository.ScreenRecordCameraRepository
import com.android.systemui.screencapture.record.camera.data.repository.ScreenRecordCameraRepositoryImpl
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenCaptureCameraHintInteractor
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenRecordCameraInteractor
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenRecordCameraSurfaceInteractor
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet

/** Provides overridable [ScreenRecordCameraRepository] binding */
@Module
interface ReferenceScreenCaptureCameraModule {

    @Binds
    @IntoSet
    fun bindScreenRecordCameraSurfaceInteractorReleasable(
        interactor: ScreenRecordCameraSurfaceInteractor
    ): ScreenCaptureReleasable

    @Binds
    @IntoSet
    fun bindScreenRecordCameraInteractorStartable(
        interactor: ScreenRecordCameraInteractor
    ): ScreenCaptureStartable

    @Binds
    @IntoSet
    fun bindScreenCaptureCameraHintInteractor(
        interactor: ScreenCaptureCameraHintInteractor
    ): ScreenCaptureStartable

    @Binds
    fun bindScreenRecordCameraRepository(
        impl: ScreenRecordCameraRepositoryImpl
    ): ScreenRecordCameraRepository
}
