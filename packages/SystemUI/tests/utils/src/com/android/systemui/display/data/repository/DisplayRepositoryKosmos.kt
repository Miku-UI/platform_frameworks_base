/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.display.data.repository

import android.content.testableContext
import android.hardware.display.DisplayManager
import android.os.fakeHandler
import android.view.Display
import android.view.mockIWindowManager
import com.android.app.displaylib.fakes.FakePerDisplayRepository
import com.android.systemui.SysUICutoutProvider
import com.android.systemui.common.ui.ConfigurationState
import com.android.systemui.common.ui.configurationState
import com.android.systemui.display.dagger.ReferenceSysUIDisplaySubcomponent
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.domain.interactor.DisplayStateInteractor
import com.android.systemui.display.domain.interactor.displayStateInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.plugins.fakeDarkIconDispatcher
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel
import com.android.systemui.statusbar.chips.ui.viewmodel.ongoingActivityChipsViewModel
import com.android.systemui.statusbar.core.statusBarIconRefreshInteractor
import com.android.systemui.statusbar.data.repository.StatusBarConfigurationController
import com.android.systemui.statusbar.data.repository.StatusBarModePerDisplayRepository
import com.android.systemui.statusbar.data.repository.fakeStatusBarModePerDisplayRepository
import com.android.systemui.statusbar.disableflags.domain.interactor.DisableFlagsInteractor
import com.android.systemui.statusbar.disableflags.domain.interactor.disableFlagsInteractor
import com.android.systemui.statusbar.domain.interactor.StatusBarIconRefreshInteractor
import com.android.systemui.statusbar.events.SystemStatusAnimationScheduler
import com.android.systemui.statusbar.events.domain.interactor.SystemStatusEventAnimationInteractor
import com.android.systemui.statusbar.events.domain.interactor.systemStatusEventAnimationInteractor
import com.android.systemui.statusbar.events.systemStatusAnimationScheduler
import com.android.systemui.statusbar.gesture.StatusBarLongPressGestureDetector
import com.android.systemui.statusbar.gesture.SwipeStatusBarAwayGestureHandler
import com.android.systemui.statusbar.gesture.statusBarLongPressGestureDetector
import com.android.systemui.statusbar.gesture.swipeStatusBarAwayGestureHandler
import com.android.systemui.statusbar.layout.StatusBarContentInsetsProvider
import com.android.systemui.statusbar.layout.mockStatusBarContentInsetsProvider
import com.android.systemui.statusbar.mockCommandQueue
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher
import com.android.systemui.statusbar.phone.fragment.dagger.HomeStatusBarComponent
import com.android.systemui.statusbar.pipeline.shared.domain.interactor.StatusBarVisibilityInteractor
import com.android.systemui.statusbar.pipeline.shared.domain.interactor.statusBarVisibilityInteractor
import com.android.systemui.statusbar.pipeline.shared.ui.binder.HomeStatusBarViewBinder
import com.android.systemui.statusbar.pipeline.shared.ui.composable.StatusBarRootFactory
import com.android.systemui.statusbar.pipeline.shared.ui.composable.statusBarRootFactory
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel.HomeStatusBarViewModelFactory
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.homeStatusBarViewBinder
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.homeStatusBarViewModelFactory
import com.android.systemui.statusbar.policy.statusBarConfigurationController
import com.android.systemui.statusbar.quickactions.av.domain.interactor.AvControlsChipInteractor
import com.android.systemui.statusbar.quickactions.av.domain.interactor.avControlsChipInteractor
import com.android.systemui.statusbar.ui.SystemBarUtilsState
import com.android.systemui.statusbar.ui.systemBarUtilsState
import com.android.systemui.statusbar.window.StatusBarWindowStateController
import com.android.systemui.statusbar.window.data.repository.StatusBarWindowStatePerDisplayRepository
import com.android.systemui.statusbar.window.data.repository.fakeStatusBarWindowStatePerDisplayRepository
import com.android.systemui.util.mockito.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher

val Kosmos.displayRepository by Fixture { FakeDisplayRepository() }

val Kosmos.sysUiDefaultDisplaySubcomponentLifecycleListeners by Fixture {
    mutableSetOf<SystemUIDisplaySubcomponent.LifecycleListener>()
}

fun Kosmos.createFakeDisplaySubcomponent(
    coroutineScope: () -> CoroutineScope = { testScope.backgroundScope },
    displayStateRepository: () -> DisplayStateRepository = { this.displayStateRepository },
    displayStateInteractor: () -> DisplayStateInteractor = { this.displayStateInteractor },
    statusbarIconRefreshInteractorFromConstructor: () -> StatusBarIconRefreshInteractor = {
        this.statusBarIconRefreshInteractor
    },
    homeStatusBarViewModelFactory: (Int) -> HomeStatusBarViewModel =
        this.homeStatusBarViewModelFactory,
    avControlsChipInteractor: () -> AvControlsChipInteractor = { this.avControlsChipInteractor },
    homeStatusBarViewBinder: () -> HomeStatusBarViewBinder = { this.homeStatusBarViewBinder },
    statusBarRootFactory: () -> StatusBarRootFactory = { this.statusBarRootFactory },
    ongoingActivityChipsViewModel: () -> OngoingActivityChipsViewModel = {
        this.ongoingActivityChipsViewModel
    },
    statusBarVisibilityInteractor: () -> StatusBarVisibilityInteractor = {
        this.statusBarVisibilityInteractor
    },
    statusBarContentInsetsProvider: () -> StatusBarContentInsetsProvider = {
        this.mockStatusBarContentInsetsProvider
    },
    systemStatusAnimationScheduler: () -> SystemStatusAnimationScheduler = {
        this.systemStatusAnimationScheduler
    },
    darkIconDispatcher: () -> DarkIconDispatcher = { this.fakeDarkIconDispatcher },
    sysUiDarkIconDispatcher: () -> SysuiDarkIconDispatcher = { this.fakeDarkIconDispatcher },
    systemBarUtilsState: () -> SystemBarUtilsState = { this.systemBarUtilsState },
    configurationState: () -> ConfigurationState = { this.configurationState },
    disableFlagsInteractor: () -> DisableFlagsInteractor = { this.disableFlagsInteractor },
    swipeStatusBarAwayGestureHandler: () -> SwipeStatusBarAwayGestureHandler = {
        this.swipeStatusBarAwayGestureHandler
    },
    statusBarConfigurationController: () -> StatusBarConfigurationController = {
        this.statusBarConfigurationController
    },
    systemStatusEventAnimationInteractor: () -> SystemStatusEventAnimationInteractor = {
        this.systemStatusEventAnimationInteractor
    },
    statusBarModeRepo: () -> StatusBarModePerDisplayRepository = {
        this.fakeStatusBarModePerDisplayRepository
    },
    statusBarWindowStateRepository: () -> StatusBarWindowStatePerDisplayRepository = {
        this.fakeStatusBarWindowStatePerDisplayRepository
    },
    statusBarLongPressGestureDetector: () -> StatusBarLongPressGestureDetector = {
        this.statusBarLongPressGestureDetector
    },
): ReferenceSysUIDisplaySubcomponent {
    return object : ReferenceSysUIDisplaySubcomponent {
        override val displayCoroutineScope: CoroutineScope
            get() = coroutineScope()

        override val displayStateRepository: DisplayStateRepository
            get() = displayStateRepository()

        override val displayStateInteractor: DisplayStateInteractor
            get() = displayStateInteractor()

        override val statusBarIconRefreshInteractor: StatusBarIconRefreshInteractor =
            statusbarIconRefreshInteractorFromConstructor()

        override val lifecycleListeners: Set<SystemUIDisplaySubcomponent.LifecycleListener> =
            sysUiDefaultDisplaySubcomponentLifecycleListeners

        override val ongoingActivityChipsViewModel: OngoingActivityChipsViewModel
            get() = ongoingActivityChipsViewModel()

        override val statusBarVisibilityInteractor: StatusBarVisibilityInteractor
            get() = statusBarVisibilityInteractor()

        override val statusBarContentInsetsProvider: StatusBarContentInsetsProvider
            get() = statusBarContentInsetsProvider()

        override val systemStatusAnimationScheduler: SystemStatusAnimationScheduler
            get() = systemStatusAnimationScheduler()

        override val systemBarUtilsState: SystemBarUtilsState
            get() = systemBarUtilsState()

        override val statusBarModeRepo: StatusBarModePerDisplayRepository
            get() = statusBarModeRepo()

        override val statusBarConfigurationController: StatusBarConfigurationController
            get() = statusBarConfigurationController()

        override val configurationState: ConfigurationState
            get() = configurationState()

        override val sysUICutoutProvider: SysUICutoutProvider
            get() = mock<SysUICutoutProvider>()

        override val disableFlagsInteractor: DisableFlagsInteractor
            get() = disableFlagsInteractor()

        override val swipeStatusBarAwayGestureHandler: SwipeStatusBarAwayGestureHandler
            get() = swipeStatusBarAwayGestureHandler()

        override val systemStatusEventAnimationInteractor: SystemStatusEventAnimationInteractor
            get() = systemStatusEventAnimationInteractor()

        override val homeStatusBarComponentFactory: HomeStatusBarComponent.Factory
            get() = mock<HomeStatusBarComponent.Factory>()

        override val statusBarWindowStateController: StatusBarWindowStateController
            get() = mock<StatusBarWindowStateController>()

        override val darkIconDispatcher: DarkIconDispatcher
            get() = darkIconDispatcher()

        override val sysuiDarkIconDispatcher: SysuiDarkIconDispatcher
            get() = sysUiDarkIconDispatcher()

        override val statusBarLongPressGestureDetector: StatusBarLongPressGestureDetector
            get() = statusBarLongPressGestureDetector()

        override val homeStatusBarViewModelFactory: HomeStatusBarViewModelFactory
            get() =
                object : HomeStatusBarViewModelFactory {
                    override fun create(): HomeStatusBarViewModel {
                        return homeStatusBarViewModelFactory.invoke(testableContext.displayId)
                    }
                }

        override val homeStatusBarViewBinder: HomeStatusBarViewBinder
            get() = homeStatusBarViewBinder()

        override val statusBarRootFactory: StatusBarRootFactory
            get() = statusBarRootFactory()

        override val statusBarWindowStateRepository: StatusBarWindowStatePerDisplayRepository
            get() = statusBarWindowStateRepository()

        override val avControlsChipInteractor: AvControlsChipInteractor
            get() = avControlsChipInteractor()
    }
}

val Kosmos.sysuiDefaultDisplaySubcomponent by Fixture {
    createFakeDisplaySubcomponent({ testScope.backgroundScope })
}

val Kosmos.fakeSysuiDisplayComponentFactory by Fixture {
    object : SystemUIDisplaySubcomponent.Factory {
        override fun create(displayId: Int): SystemUIDisplaySubcomponent {
            return sysuiDefaultDisplaySubcomponent
        }
    }
}

val Kosmos.displaySubcomponentPerDisplayRepository by Fixture {
    FakePerDisplayRepository<SystemUIDisplaySubcomponent>().apply {
        add(Display.DEFAULT_DISPLAY, sysuiDefaultDisplaySubcomponent)
    }
}

val Kosmos.displayPhoneSubcomponentPerDisplayRepository by Fixture {
    FakePerDisplayRepository<ReferenceSysUIDisplaySubcomponent>().apply {
        add(Display.DEFAULT_DISPLAY, sysuiDefaultDisplaySubcomponent)
    }
}

val Kosmos.mockDisplayManager by Fixture { mock<DisplayManager>() }
val Kosmos.displayRepositoryFromDisplayLib by Fixture {
    com.android.app.displaylib.DisplayRepositoryImpl(
        mockDisplayManager,
        testableContext.contentResolver,
        fakeHandler,
        testScope.backgroundScope,
        UnconfinedTestDispatcher(),
    )
}
val Kosmos.displayWithDecorationsRepository by Fixture {
    DisplaysWithDecorationsRepositoryImpl(
        mockCommandQueue,
        mockIWindowManager,
        testScope.backgroundScope,
        displayRepositoryFromDisplayLib,
    )
}
val Kosmos.displaysWithDecorationsRepositoryFromDisplayLib by Fixture {
    com.android.app.displaylib.DisplaysWithDecorationsRepositoryImpl(
        mockIWindowManager,
        testScope.backgroundScope,
        displayRepositoryFromDisplayLib,
    )
}

val Kosmos.realDisplayRepository by Fixture {
    DisplayRepositoryImpl(
        displayRepositoryFromDisplayLib,
        displayWithDecorationsRepository,
        displaysWithDecorationsRepositoryFromDisplayLib,
    )
}

val Kosmos.displaysWithDecorationsRepositoryCompat by Fixture {
    com.android.app.displaylib.DisplaysWithDecorationsRepositoryCompat(
        testScope.backgroundScope,
        displaysWithDecorationsRepositoryFromDisplayLib,
    )
}

fun Kosmos.setDisplayType(displayId: Int, type: Int) {
    runBlocking {
        displayRepository.removeDisplay(displayId)
        displayRepository.addDisplay(displayId, type = type)
        displayRepository.emitDisplayChangeEvent(displayId)
    }
}
