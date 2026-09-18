/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.data.repository

import android.content.res.mainResources
import android.content.testableContext
import android.testing.LeakCheck
import android.view.LayoutInflater
import com.android.keyguard.ClockEventController
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.common.ui.data.repository.configurationRepository
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.customization.clocks.ClockLogger
import com.android.systemui.customization.clocks.FixedTimeKeeper
import com.android.systemui.display.data.repository.fakeDisplayWindowPropertiesRepository
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.ui.viewmodel.dozingToLockscreenTransitionViewModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMessageBuffers
import com.android.systemui.plugins.pluginManager
import com.android.systemui.settings.userTracker
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.systemui.shared.clocks.DefaultClockProvider
import com.android.systemui.statusbar.policy.batteryController
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.statusbar.policy.domain.interactor.zenModeInteractor
import com.android.systemui.util.settings.FakeSettings
import com.android.systemui.utils.leaks.FakeZenModeController
import org.mockito.kotlin.mock

val Kosmos.clockRegistry: ClockRegistry by
    Kosmos.Fixture {
        ClockRegistry(
            context = testableContext,
            pluginManager = pluginManager,
            scope = testScope,
            mainDispatcher = testDispatcher,
            bgDispatcher = testDispatcher,
            handleAllUsers = true,
            defaultClockProvider =
                DefaultClockProvider(
                        LayoutInflater.from(testableContext),
                        testableContext.resources,
                        vibrator = null,
                        timeKeeperFactory = { FixedTimeKeeper() },
                    )
                    .apply {},
            assert = mock(),
            keepAllLoaded = true,
            subTag = "subtag",
        )
    }
val Kosmos.keyguardClockRepository: KeyguardClockRepository by
    Kosmos.Fixture { fakeKeyguardClockRepository }
val Kosmos.fakeKeyguardClockRepository by Kosmos.Fixture { FakeKeyguardClockRepository() }
val Kosmos.keyguardClockRepositoryImpl by
    Kosmos.Fixture {
        KeyguardClockRepositoryImpl(
            secureSettings = FakeSettings(testDispatcher),
            clockRegistry = clockRegistry,
            clockEventController =
                ClockEventController(
                        keyguardTransitionInteractor = keyguardTransitionInteractor,
                        broadcastDispatcher = broadcastDispatcher,
                        batteryController = batteryController,
                        keyguardUpdateMonitor = keyguardUpdateMonitor,
                        configurationController = configurationController,
                        context = testableContext,
                        mainExecutor = fakeExecutor,
                        bgExecutor = fakeExecutor,
                        clockBuffers = ClockMessageBuffers(ClockLogger.DEFAULT_MESSAGE_BUFFER),
                        featureFlags =
                            fakeFeatureFlagsClassic.apply { set(Flags.REGION_SAMPLING, false) },
                        zenModeController = FakeZenModeController(LeakCheck()),
                        zenModeInteractor = zenModeInteractor,
                        userTracker = userTracker,
                        dozingToLockscreenViewModel = { dozingToLockscreenTransitionViewModel },
                        displayWindowPropertiesRepository = fakeDisplayWindowPropertiesRepository,
                    )
                    .apply { clock = clockRegistry.createCurrentClock(context) },
            backgroundDispatcher = testDispatcher,
            applicationScope = applicationCoroutineScope,
            context = testableContext,
            configurationRepository = configurationRepository,
            featureFlags = featureFlagsClassic,
        )
    }
val Kosmos.clockEventController by
    Kosmos.Fixture { fakeKeyguardClockRepository.clockEventController }
