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

package com.android.systemui.statusbar.dagger

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.Display
import android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR
import com.android.systemui.Flags
import com.android.systemui.common.ui.ConfigurationState
import com.android.systemui.common.ui.ConfigurationStateImpl
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAwareStatusBar
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.headline.ui.viewmodel.HeadlineItemsAdapter
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel
import com.android.systemui.statusbar.data.repository.StatusBarConfigurationController
import com.android.systemui.statusbar.data.repository.StatusBarModePerDisplayRepository
import com.android.systemui.statusbar.data.repository.StatusBarModePerDisplayRepositoryImpl
import com.android.systemui.statusbar.disableflags.data.repository.DisableFlagsRepository
import com.android.systemui.statusbar.disableflags.data.repository.DisableFlagsRepositoryImpl
import com.android.systemui.statusbar.disableflags.domain.interactor.DisableFlagsInteractor
import com.android.systemui.statusbar.domain.interactor.StatusBarIconRefreshInteractor
import com.android.systemui.statusbar.domain.interactor.StatusBarIconRefreshInteractorImpl
import com.android.systemui.statusbar.events.SystemEventChipAnimationController
import com.android.systemui.statusbar.events.SystemEventChipAnimationControllerImpl
import com.android.systemui.statusbar.events.SystemEventCoordinator
import com.android.systemui.statusbar.events.SystemStatusAnimationScheduler
import com.android.systemui.statusbar.events.SystemStatusAnimationSchedulerImpl
import com.android.systemui.statusbar.events.data.repository.SystemStatusEventAnimationRepository
import com.android.systemui.statusbar.events.data.repository.SystemStatusEventAnimationRepositoryImpl
import com.android.systemui.statusbar.events.domain.interactor.SystemStatusEventAnimationInteractor
import com.android.systemui.statusbar.gesture.SwipeStatusBarAwayGestureHandler
import com.android.systemui.statusbar.layout.StatusBarContentInsetsProvider
import com.android.systemui.statusbar.layout.StatusBarContentInsetsProviderImpl
import com.android.systemui.statusbar.layout.ui.viewmodel.StatusBarContentInsetsViewModel
import com.android.systemui.statusbar.phone.ConfigurationControllerImpl
import com.android.systemui.statusbar.pipeline.shared.domain.interactor.HomeStatusBarInteractor
import com.android.systemui.statusbar.pipeline.shared.domain.interactor.StatusBarVisibilityInteractor
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.DisplayAwareHeadlineViewModelImpl
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HeadlineItemsAdapterImpl
import com.android.systemui.statusbar.quickactions.av.domain.interactor.AvControlsChipInteractor
import com.android.systemui.statusbar.quickactions.av.domain.interactor.AvControlsChipInteractorImpl
import com.android.systemui.statusbar.quickactions.av.domain.interactor.NoOpAvControlsChipInteractor
import com.android.systemui.statusbar.ui.SystemBarUtilsState
import com.android.systemui.statusbar.window.StatusBarWindowController
import com.android.systemui.statusbar.window.StatusBarWindowControllerStore
import com.android.systemui.statusbar.window.StatusBarWindowStateController
import com.android.systemui.statusbar.window.data.repository.StatusBarWindowStatePerDisplayRepository
import com.android.systemui.statusbar.window.data.repository.StatusBarWindowStatePerDisplayRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope

/**
 * Contains bindings that are [SystemUIDisplaySubcomponent.DisplayAware] related to the statusbar.
 */
@Module
interface PerDisplayStatusBarModule {

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun bindsStatusBarIconRefreshInteractor(
        impl: StatusBarIconRefreshInteractorImpl
    ): StatusBarIconRefreshInteractor

    @Binds
    @DisplayAware
    fun statusBarWindowStateController(
        controller: StatusBarWindowStateController
    ): StatusBarWindowStateController

    @Binds
    @IntoSet
    @DisplayAware
    fun statusBarWindowStateControllerAsLifecycleListener(
        controller: StatusBarWindowStateController
    ): SystemUIDisplaySubcomponent.LifecycleListener

    @Binds
    @DisplayAware
    fun statusBarContentInsetsProvider(
        impl: StatusBarContentInsetsProviderImpl
    ): StatusBarContentInsetsProvider

    @Binds
    @DisplayAware
    @IntoSet
    fun statusBarContentInsetsProviderAsLifecycleListener(
        impl: StatusBarContentInsetsProviderImpl
    ): SystemUIDisplaySubcomponent.LifecycleListener

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun statusBarWindowStateRepository(
        impl: StatusBarWindowStatePerDisplayRepositoryImpl
    ): StatusBarWindowStatePerDisplayRepository

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun ongoingActivityChipsViewModel(
        impl: OngoingActivityChipsViewModel
    ): OngoingActivityChipsViewModel

    @Binds
    @DisplayAware
    fun headlineItemsAdapter(impl: HeadlineItemsAdapterImpl): HeadlineItemsAdapter

    @Binds
    @DisplayAware
    fun headlineViewModelFactory(
        impl: DisplayAwareHeadlineViewModelImpl.Factory
    ): HeadlineViewModel.Factory

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun statusBarVisibilityInteractor(
        impl: StatusBarVisibilityInteractor
    ): StatusBarVisibilityInteractor

    @Binds
    @DisplayAware
    fun homeStatusBarInteractor(interactor: HomeStatusBarInteractor): HomeStatusBarInteractor

    @Binds
    @DisplayAware
    fun contentInsetsViewModel(vm: StatusBarContentInsetsViewModel): StatusBarContentInsetsViewModel

    @Binds
    @DisplayAware
    fun swipeStatusBarAwayGestureHandler(
        impl: SwipeStatusBarAwayGestureHandler
    ): SwipeStatusBarAwayGestureHandler

    @Binds
    @DisplayAware
    fun disableFlagsRepo(impl: DisableFlagsRepositoryImpl): DisableFlagsRepository

    @Binds
    @DisplayAware
    fun disableFlagsInteractor(impl: DisableFlagsInteractor): DisableFlagsInteractor

    @Binds
    @DisplayAware
    fun modeRepo(impl: StatusBarModePerDisplayRepositoryImpl): StatusBarModePerDisplayRepository

    @Binds
    @IntoSet
    @DisplayAware
    fun modeRepoAsLifecycleListener(
        impl: StatusBarModePerDisplayRepositoryImpl
    ): SystemUIDisplaySubcomponent.LifecycleListener

    @Binds
    @DisplayAware
    fun systemStatusEventAnimationInteractor(
        interactor: SystemStatusEventAnimationInteractor
    ): SystemStatusEventAnimationInteractor

    @Binds
    @DisplayAware
    fun systemStatusEventAnimationRepository(
        impl: SystemStatusEventAnimationRepositoryImpl
    ): SystemStatusEventAnimationRepository

    @Binds
    @DisplayAware
    fun systemStatusAnimationScheduler(
        impl: SystemStatusAnimationSchedulerImpl
    ): SystemStatusAnimationScheduler

    @Binds
    @DisplayAware
    fun systemEventCoordinator(impl: SystemEventCoordinator): SystemEventCoordinator

    @Binds
    @DisplayAware
    fun systemEventChipAnimationController(
        impl: SystemEventChipAnimationControllerImpl
    ): SystemEventChipAnimationController

    companion object {

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun statusBarWindowController(
            @DisplayAware displayId: Int,
            store: StatusBarWindowControllerStore,
        ): StatusBarWindowController? {
            return store.forDisplay(displayId)
        }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun provideStatusBarConfigurationController(
            @DisplayAwareStatusBar context: Context,
            configurationControllerFactory: ConfigurationControllerImpl.Factory,
        ): StatusBarConfigurationController {
            return configurationControllerFactory.create(context)
        }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun systemBarUtilsState(
            @DisplayAware context: Context,
            @DisplayAware configurationController: StatusBarConfigurationController,
            factory: SystemBarUtilsState.Factory,
        ): SystemBarUtilsState {
            return factory.create(context, configurationController)
        }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun configurationState(
            configStateFactory: ConfigurationStateImpl.Factory,
            @DisplayAware configurationController: StatusBarConfigurationController,
            @DisplayAware context: Context,
        ): ConfigurationState {
            return configStateFactory.create(context, configurationController)
        }

        /**
         * Status Bar specific per display [Context]. For the default display it uses the default
         * application [Context], which is all that is needed.
         *
         * For external displays, it will be a [WindowContext], which is tied to the display and
         * also window type [TYPE_STATUS_BAR].
         */
        @Provides
        @PerDisplaySingleton
        @DisplayAwareStatusBar
        fun provideStatusBarWindowContext(
            display: Display,
            @Application context: Context,
        ): Context {
            return if (display.displayId == Display.DEFAULT_DISPLAY) {
                // No need to create a new context, if we already have one.
                context
            } else {
                context
                    .createWindowContext(display, TYPE_STATUS_BAR, /* options= */ Bundle.EMPTY)
                    .also { it.setTheme(R.style.Theme_SystemUI) }
            }
        }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun provideStatusBarWindowResources(@DisplayAwareStatusBar context: Context): Resources {
            return context.resources
        }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun avControlsChipInteractor(
            avControlsChipNotSupported: Provider<NoOpAvControlsChipInteractor>,
            factory: AvControlsChipInteractorImpl.Factory,
            @DisplayAware scope: CoroutineScope,
            @DisplayAware statusBarModeRepo: StatusBarModePerDisplayRepository,
        ): AvControlsChipInteractor {
            return if (Flags.expandedPrivacyIndicatorsOnLargeScreen()) {
                factory.create(scope, statusBarModeRepo)
            } else {
                avControlsChipNotSupported.get()
            }
        }
    }
}
