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

package com.android.systemui.wallpapers.dagger

import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.wallpapers.WallpaperPresentationEnabled
import com.android.systemui.wallpapers.WallpaperPresentationManager
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractorImpl
import com.android.systemui.wallpapers.domain.interactor.NoOpDisplayWallpaperPresentationInteractor
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import javax.inject.Provider

/**
 * Module providing [SystemUIDisplaySubcomponent.DisplayAware] components that are related to the
 * wallpaper.
 */
@Module
interface PerDisplayWallpaperModule {

    companion object {
        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun bindsDisplayWallpaperPresentationInteractor(
            @WallpaperPresentationEnabled isWallpaperPresentationEnabled: Boolean,
            impl: Provider<DisplayWallpaperPresentationInteractorImpl>,
        ): DisplayWallpaperPresentationInteractor =
            if (isWallpaperPresentationEnabled) {
                impl.get()
            } else {
                NoOpDisplayWallpaperPresentationInteractor
            }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        @ElementsIntoSet
        fun bindWallpaperPresentationManagerLifecycleListener(
            @WallpaperPresentationEnabled isWallpaperPresentationEnabled: Boolean,
            impl: Provider<WallpaperPresentationManager>,
        ): Set<SystemUIDisplaySubcomponent.LifecycleListener> =
            if (isWallpaperPresentationEnabled) {
                setOf(impl.get())
            } else {
                emptySet()
            }
    }
}
