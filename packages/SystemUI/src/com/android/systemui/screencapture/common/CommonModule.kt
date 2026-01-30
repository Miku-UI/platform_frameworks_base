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

import android.content.Context
import com.android.launcher3.icons.IconFactory
import com.android.systemui.mediaprojection.appselector.data.RecentTaskListProvider
import com.android.systemui.mediaprojection.appselector.data.ShellRecentTaskListProvider
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureAppContentRepository
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureAppContentRepositoryImpl
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureIconRepository
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureIconRepositoryImpl
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureLabelRepository
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureLabelRepositoryImpl
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureRecentTaskRepository
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureRecentTaskRepositoryImpl
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureThumbnailRepository
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureThumbnailRepositoryImpl
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTasksViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTasksViewModelImpl
import dagger.Binds
import dagger.Module
import dagger.Provides

/**
 * Dagger Module for bindings common to all [ScreenCaptureUiComponent]s.
 *
 * This module must be included in the Subcomponent or replaced with equivalent bindings.
 */
@Module
interface CommonModule {
    @Binds
    fun bindScreenCaptureIconRepository(
        impl: ScreenCaptureIconRepositoryImpl
    ): ScreenCaptureIconRepository

    @Binds
    fun bindScreenCaptureLabelRepository(
        impl: ScreenCaptureLabelRepositoryImpl
    ): ScreenCaptureLabelRepository

    @Binds
    fun bindScreenCaptureThumbnailRepository(
        impl: ScreenCaptureThumbnailRepositoryImpl
    ): ScreenCaptureThumbnailRepository

    @Binds
    fun bindRecentTaskRepository(
        impl: ScreenCaptureRecentTaskRepositoryImpl
    ): ScreenCaptureRecentTaskRepository

    @Binds
    fun bindAppContentRepository(
        impl: ScreenCaptureAppContentRepositoryImpl
    ): ScreenCaptureAppContentRepository

    @Binds fun bindRecentTaskListProvider(impl: ShellRecentTaskListProvider): RecentTaskListProvider

    @Binds fun bindRecentTasksViewModel(impl: RecentTasksViewModelImpl): RecentTasksViewModel

    companion object {
        @Provides
        @ScreenCaptureUi
        fun provideIconFactory(context: Context): IconFactory = IconFactory.obtain(context)
    }
}
