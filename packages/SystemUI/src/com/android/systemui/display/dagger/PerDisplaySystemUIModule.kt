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

package com.android.systemui.display.dagger

import android.content.Context
import android.view.Display
import com.android.app.displaylib.DisplayRepository
import com.android.systemui.CameraProtectionLoader
import com.android.systemui.CameraProtectionLoaderImpl
import com.android.systemui.SysUICutoutProvider
import com.android.systemui.SysUICutoutProviderImpl
import com.android.systemui.coroutines.newTracingContext
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.display.data.repository.DisplayStateRepository
import com.android.systemui.display.data.repository.DisplayStateRepositoryImpl
import com.android.systemui.display.domain.interactor.DisplayStateInteractor
import com.android.systemui.display.domain.interactor.DisplayStateInteractorImpl
import com.android.systemui.display.shared.DisplayNotFoundException
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.statusbar.dagger.PerDisplayStatusBarModule
import com.android.systemui.statusbar.phone.DarkIconDispatcherImpl
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher
import com.android.systemui.statusbar.pipeline.shared.ui.composable.StatusBarRootFactory
import com.android.systemui.wallpapers.dagger.PerDisplayWallpaperModule
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** Module providing common dependencies for per-display singletons. */
@Module(
    includes =
        [
            PerDisplayStatusBarModule::class,
            PerDisplayConfigurationModule::class,
            PerDisplayWallpaperModule::class,
        ]
)
interface PerDisplaySystemUIModule {

    @Multibinds
    @DisplayAware
    fun lifecycleListeners(): Set<SystemUIDisplaySubcomponent.LifecycleListener>

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun displayStateRepository(impl: DisplayStateRepositoryImpl): DisplayStateRepository

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun bindsDisplayStateInteractor(impl: DisplayStateInteractorImpl): DisplayStateInteractor

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun statusBarRootFactory(statusBarRootFactory: StatusBarRootFactory): StatusBarRootFactory

    @Binds @DisplayAware fun darkIconDispatcher(impl: DarkIconDispatcherImpl): DarkIconDispatcher

    @Binds
    @DisplayAware
    @IntoSet
    fun bindDarkIconDispatcherLifecycleListener(
        impl: DarkIconDispatcherImpl
    ): SystemUIDisplaySubcomponent.LifecycleListener

    @Binds
    @DisplayAware
    fun sysUiDarkIconDispatcher(impl: DarkIconDispatcherImpl): SysuiDarkIconDispatcher

    @Binds @DisplayAware fun sysUICutoutProvider(impl: SysUICutoutProviderImpl): SysUICutoutProvider

    companion object {

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun cameraProtectionLoader(
            factory: CameraProtectionLoaderImpl.Factory,
            @DisplayAware context: Context,
        ): CameraProtectionLoader {
            return factory.create(context)
        }

        @Provides
        @PerDisplaySingleton
        fun provideDisplay(
            @DisplayId displayId: Int,
            displayRepository: DisplayRepository,
        ): Display {
            return displayRepository.getDisplay(displayId)
                ?: throw DisplayNotFoundException("Couldn't get the display with id=$displayId")
        }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun provideDisplayContext(display: Display, @Application context: Context): Context {
            return if (display.displayId == Display.DEFAULT_DISPLAY) {
                // No need to create a new context, if we already have one.
                context
            } else {
                context.createDisplayContext(display)
            }
        }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun provideDisplayCoroutineScope(
            @Background backgroundDispatcher: CoroutineDispatcher,
            @DisplayId displayId: Int,
        ): CoroutineScope {
            return CoroutineScope(
                backgroundDispatcher + newTracingContext("DisplayScope(id=$displayId)")
            )
        }

        @Provides @DisplayAware fun provideDisplayId(@DisplayId displayId: Int): Int = displayId
    }
}
