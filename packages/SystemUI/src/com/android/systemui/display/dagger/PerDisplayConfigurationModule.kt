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

import android.view.Display
import android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR
import android.window.WindowContext
import com.android.systemui.common.ui.WindowContextConfigurationController
import com.android.systemui.common.ui.WindowContextConfigurationControllerImpl
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import com.android.systemui.common.ui.data.repository.ConfigurationRepositoryImpl
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractorImpl
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.display.data.repository.DisplayWindowPropertiesRepository
import com.android.systemui.statusbar.policy.ConfigurationController
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet

/**
 * Additional module to provide a [ConfigurationController] related to a specific display.
 *
 * While this is not tied to any UI object, it but uses a [TYPE_STATUS_BAR] window context type, and
 * gets the configuration using [android.content.ComponentCallbacks].
 *
 * The [WindowContext] bound here is from [DisplayWindowPropertiesRepository], and it's essentially
 * the same as the one used by the status bar (so, no new context creation happen as part of this).
 *
 * Note: for the default display it just binds the global configuration objects with the
 * `@DisplayAware` annotation, to keep the same behaviour as before and avoiding creating new
 * instances.
 */
@Module
class PerDisplayConfigurationModule {

    @Provides
    @PerDisplaySingleton
    @DisplayAware
    fun provideStatusBarWindowContext(
        @DisplayId displayId: Int,
        displayPropertiesRepository: DisplayWindowPropertiesRepository,
    ): WindowContext {
        if (displayId == Display.DEFAULT_DISPLAY) {
            error(
                """If you're receiving this error it either means something in
                    | PerDisplayConfigurationModule is wrong, or that you're injecting a
                    | @DisplayAware window context in a class used by the default display. This is
                    | not possible as the statusbar window context is used for this binding, but for
                    | the default display we're not creating a new window context."""
                    .trimMargin()
            )
        }
        return displayPropertiesRepository.get(displayId, TYPE_STATUS_BAR)?.context
            as? WindowContext
            ?: error(
                """Unable to cast status bar context to WindowContext. I
                    |f the statusbar is not using WindowContext, this will not work and you should
                    | remove PerDisplayConfigurationModule from your dagger graph and any dependency
                    | on its classes."""
                    .trimMargin()
            )
    }

    @Provides
    @PerDisplaySingleton
    @DisplayAware
    fun provideWindowContextDisplayConfigurationController(
        @DisplayAware statusbarWindowContext: WindowContext,
        windowContextConfigurationController: WindowContextConfigurationControllerImpl.Factory,
    ): WindowContextConfigurationController =
        windowContextConfigurationController.create(statusbarWindowContext)

    @Provides
    @PerDisplaySingleton
    @DisplayAware
    fun provideDisplayConfigurationController(
        @DisplayAware displayConfigurationController: Lazy<WindowContextConfigurationController>,
        globalConfigController: ConfigurationController,
        @DisplayAware displayId: Int,
    ): ConfigurationController {
        // We should remove this condition and just create also the instance for the default display
        // in the same way. This is not possible right now as we're not using a WindowContext for
        // the default display statusbar.
        return if (displayId == Display.DEFAULT_DISPLAY) {
            globalConfigController
        } else {
            displayConfigurationController.get()
        }
    }

    /**
     * The lifecycle listener is only needed if we're on an external display, as we can assume the
     * default display will always be there.
     */
    @Provides
    @PerDisplaySingleton
    @DisplayAware
    @IntoSet
    fun provideDisplayWindowContextConfigurationControllerLifecycleObserver(
        @DisplayAware displayConfigurationController: Lazy<WindowContextConfigurationController>,
        @DisplayAware displayId: Int,
    ): SystemUIDisplaySubcomponent.LifecycleListener =
        object : SystemUIDisplaySubcomponent.LifecycleListener {
            override fun start() {
                if (displayId != Display.DEFAULT_DISPLAY) {
                    displayConfigurationController.get().start()
                }
            }

            override fun stop() {
                if (displayId != Display.DEFAULT_DISPLAY) {
                    displayConfigurationController.get().stop()
                }
            }
        }

    @Provides
    @PerDisplaySingleton
    @DisplayAware
    fun provideConfigurationRepository(
        @DisplayAware configurationController: Lazy<ConfigurationController>,
        @DisplayAware context: Lazy<WindowContext>,
        configurationRepositoryFactory: ConfigurationRepositoryImpl.Factory,
        @DisplayAware displayId: Int,
        globalConfigurationRepository: ConfigurationRepository,
    ): ConfigurationRepository =
        if (displayId == Display.DEFAULT_DISPLAY) {
            globalConfigurationRepository
        } else {
            configurationRepositoryFactory.create(context.get(), configurationController.get())
        }

    @Provides
    @PerDisplaySingleton
    @DisplayAware
    fun provideConfigurationInteractor(
        @DisplayAware configurationRepository: Lazy<ConfigurationRepository>,
        @DisplayAware displayId: Int,
        globalConfigurationInteractor: ConfigurationInteractor,
    ): ConfigurationInteractor =
        if (displayId == Display.DEFAULT_DISPLAY) {
            globalConfigurationInteractor
        } else {
            ConfigurationInteractorImpl(configurationRepository.get())
        }
}
