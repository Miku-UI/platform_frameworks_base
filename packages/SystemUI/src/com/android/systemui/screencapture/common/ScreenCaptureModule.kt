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

import android.app.Activity
import com.android.systemui.CoreStartable
import com.android.systemui.screencapture.ScreenCaptureCoreStartable
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModelImpl
import com.android.systemui.screencapture.data.repository.ScreenCaptureTracingRepository
import com.android.systemui.screencapture.data.repository.ScreenCaptureTracingRepositoryImpl
import com.android.systemui.screencapture.record.largescreen.ui.DirectoryPickerActivity
import com.android.systemui.screencapture.record.smallscreen.ui.SmallScreenPostRecordingActivity
import com.android.systemui.screencapture.ui.ShareScreenActivity
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds

@Module(subcomponents = [ScreenCaptureComponent::class])
interface ScreenCaptureModule {

    @Binds
    @IntoMap
    @ClassKey(ScreenCaptureCoreStartable::class)
    fun bindScreenCaptureUiStartable(impl: ScreenCaptureCoreStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(ShareScreenActivity::class)
    fun bindsShareScreenActivity(impl: ShareScreenActivity): Activity

    @Binds
    @IntoMap
    @ClassKey(SmallScreenPostRecordingActivity::class)
    fun provideSmallScreenPostRecordingActivity(
        activity: SmallScreenPostRecordingActivity
    ): Activity

    @Binds
    @IntoMap
    @ClassKey(DirectoryPickerActivity::class)
    fun bindDirectoryPickerActivity(activity: DirectoryPickerActivity): Activity

    @Binds
    fun bindDrawableLoaderViewModel(impl: DrawableLoaderViewModelImpl): DrawableLoaderViewModel

    @Binds
    fun bindScreenCaptureTracingRepository(
        impl: ScreenCaptureTracingRepositoryImpl
    ): ScreenCaptureTracingRepository

    @Multibinds fun screenCaptureStartableSet(): Set<ScreenCaptureStartable>

    @Multibinds fun screenCaptureReleasableSet(): Set<ScreenCaptureReleasable>
}
