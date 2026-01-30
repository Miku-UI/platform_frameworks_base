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
package com.android.systemui.statusbar.events

import android.location.flags.Flags.FLAG_LOCATION_INDICATORS_ENABLED
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.UsesFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.testing.TestableLooper
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.Flags.FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.domain.interactor.ConnectedDisplayInteractor
import com.android.systemui.display.domain.interactor.ConnectedDisplayInteractor.PendingDisplay
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.privacy.PrivacyApplication
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyItemController
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.av.domain.interactor.AvControlsChipInteractor
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.argThat
import com.android.systemui.util.time.FakeSystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
@UsesFlags(Flags::class, android.location.flags.Flags::class)
class SystemEventCoordinatorTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val fakeSystemClock = FakeSystemClock()
    private val testScope = TestScope(UnconfinedTestDispatcher())
    private val connectedDisplayInteractor = FakeConnectedDisplayInteractor()

    @Mock lateinit var batteryController: BatteryController
    @Mock lateinit var privacyController: PrivacyItemController
    @Mock lateinit var avControlsChipInteractor: AvControlsChipInteractor
    @Mock lateinit var scheduler: SystemStatusAnimationScheduler

    private lateinit var systemEventCoordinator: SystemEventCoordinator

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(FLAG_LOCATION_INDICATORS_ENABLED)
        }

        private const val ADVANCE_TIME_WITHIN_DEBOUNCE_MS = DEBOUNCE_TIME_LOCATION - 100_000L
        private const val ADVANCE_TIME_OUTSIDE_DEBOUNCE_MS = DEBOUNCE_TIME_LOCATION + 100_000L
        private const val ADVANCE_TIME_SHORT_MS = 50_000L
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        overrideResource(R.string.config_cameraGesturePackage, DEFAULT_CAMERA_PACKAGE_NAME)
        systemEventCoordinator =
            SystemEventCoordinator(
                    fakeSystemClock,
                    batteryController,
                    privacyController,
                    avControlsChipInteractor,
                    context,
                    TestScope(UnconfinedTestDispatcher()),
                    connectedDisplayInteractor,
                    logcatLogBuffer("SystemEventCoordinatorTest"),
                )
                .apply { attachScheduler(scheduler) }
        `when`(avControlsChipInteractor.isEnabled).thenReturn(MutableStateFlow(false))
    }

    @Test
    fun startObserving_propagatesConnectedDisplayStatusEvents() =
        testScope.runTest {
            systemEventCoordinator.startObserving()

            connectedDisplayInteractor.emit()
            connectedDisplayInteractor.emit()

            verify(scheduler, times(2)).onStatusEvent(any<ConnectedDisplayEvent>())
        }

    @Test
    fun stopObserving_doesNotPropagateConnectedDisplayStatusEvents() =
        testScope.runTest {
            systemEventCoordinator.startObserving()

            connectedDisplayInteractor.emit()

            verify(scheduler).onStatusEvent(any<ConnectedDisplayEvent>())

            systemEventCoordinator.stopObserving()

            connectedDisplayInteractor.emit()

            verifyNoMoreInteractions(scheduler)
        }

    @Test
    @EnableFlags(FLAG_LOCATION_INDICATORS_ENABLED)
    fun onPrivacyItemsChanged_locationOnly_locationFlagOn_showsAnimation() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("maps", 1),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    )
                )
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            verify(scheduler).onStatusEvent(argThat { it.showAnimation })
        }

    @Test
    @DisableFlags(FLAG_LOCATION_INDICATORS_ENABLED)
    fun onPrivacyItemsChanged_locationOnly_locationFlagOff_doesNotShowAnimation() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("maps", 1),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    )
                )
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            verify(scheduler).onStatusEvent(argThat { !it.showAnimation })
        }

    @Test
    @DisableFlags(FLAG_LOCATION_INDICATORS_ENABLED)
    fun onPrivacyItemsChanged_locationAndMic_locationFlagOff_showsAnimation() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("maps", 1),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    ),
                    PrivacyItem(
                        application = PrivacyApplication("recorder", 2),
                        privacyType = PrivacyType.TYPE_MICROPHONE,
                    ),
                )
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            verify(scheduler).onStatusEvent(argThat { it.showAnimation })
        }

    @Test
    @EnableFlags(FLAG_LOCATION_INDICATORS_ENABLED)
    fun onPrivacyItemsChanged_locationAndMic_locationFlagOn_showsAnimation() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("maps", 1),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    ),
                    PrivacyItem(
                        application = PrivacyApplication("recorder", 2),
                        privacyType = PrivacyType.TYPE_MICROPHONE,
                    ),
                )
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            verify(scheduler).onStatusEvent(argThat { it.showAnimation })
        }

    @Test
    @EnableFlags(FLAG_LOCATION_INDICATORS_ENABLED)
    fun onPrivacyItemsChanged_locationOnly_respectsDebounceWindow() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("maps", 1),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    )
                )

            // First event, show animation
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)
            verify(scheduler).onStatusEvent(argThat { it.showAnimation })

            // Second event, within debounce window, should not show animation
            fakeSystemClock.advanceTime(ADVANCE_TIME_WITHIN_DEBOUNCE_MS)
            // We must clear the privacy items first to trigger the listener.
            // The listener no-ops if the list of privacy items is identical to the last one.
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(emptyList())
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)
            verify(scheduler).onStatusEvent(argThat { !it.showAnimation })

            // Third event, after debounce window, should show animation again (More than 600_000L
            // since last animation)
            fakeSystemClock.advanceTime(ADVANCE_TIME_OUTSIDE_DEBOUNCE_MS)
            // We must clear the privacy items first to trigger the listener.
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(emptyList())
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)
            verify(scheduler, times(2)).onStatusEvent(argThat { it.showAnimation })
        }

    @Test
    @EnableFlags(FLAG_LOCATION_INDICATORS_ENABLED)
    fun onPrivacyItemsChanged_locationOnly_differentApps_respectsDebounceWindow() =
        testScope.runTest {
            val privacyList1 =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("maps", 1),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    )
                )
            val privacyList2 =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("photos", 2),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    )
                )

            // First event, show animation
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList1)
            verify(scheduler).onStatusEvent(argThat { it.showAnimation })

            // Second event from a different app, within debounce window, should show animation
            fakeSystemClock.advanceTime(ADVANCE_TIME_WITHIN_DEBOUNCE_MS)
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList2)
            verify(scheduler, times(2)).onStatusEvent(argThat { it.showAnimation })

            // Third event, from the first app again, within its debounce window, should NOT show
            // animation
            fakeSystemClock.advanceTime(ADVANCE_TIME_SHORT_MS)
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList1)
            verify(scheduler).onStatusEvent(argThat { !it.showAnimation })
        }

    @Test
    @DisableFlags(FLAG_LOCATION_INDICATORS_ENABLED)
    fun onPrivacyItemsChanged_locationOnly_flagOff_neverShowsAnimation() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("maps", 1),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    )
                )

            // First event, should not show animation
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)
            verify(scheduler).onStatusEvent(argThat { !it.showAnimation })

            // Second event, within debounce window, should not show animation
            fakeSystemClock.advanceTime(ADVANCE_TIME_WITHIN_DEBOUNCE_MS)
            // We must clear the privacy items first to trigger the listener.
            // The listener no-ops if the list of privacy items is identical to the last one.
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(emptyList())
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)
            verify(scheduler, times(2)).onStatusEvent(argThat { !it.showAnimation })

            // Third event, after debounce window, should not show animation (More than 600_000L
            // since last animation)
            fakeSystemClock.advanceTime(ADVANCE_TIME_OUTSIDE_DEBOUNCE_MS)
            // We must clear the privacy items first to trigger the listener.
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(emptyList())
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)
            verify(scheduler, times(3)).onStatusEvent(argThat { !it.showAnimation })
        }

    @Test
    @EnableFlags(FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION)
    fun onPrivacyItemsChanged_notDefaultCamera_showsAnimation() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("package1", 1),
                        privacyType = PrivacyType.TYPE_CAMERA,
                    )
                )
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            verify(scheduler).onStatusEvent(argThat { it.showAnimation })
        }

    @Test
    @EnableFlags(FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION)
    fun onPrivacyItemsChanged_defaultCameraApp_cameraAccess_doesNotShowAnimation() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication(DEFAULT_CAMERA_PACKAGE_NAME, 1),
                        privacyType = PrivacyType.TYPE_CAMERA,
                    )
                )
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            verify(scheduler).onStatusEvent(argThat { !it.showAnimation })
        }

    @Test
    @EnableFlags(FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION)
    fun onPrivacyItemsChanged_defaultCameraApp_microphoneAccess_doesNotShowAnimation() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication(DEFAULT_CAMERA_PACKAGE_NAME, 1),
                        privacyType = PrivacyType.TYPE_MICROPHONE,
                    )
                )
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            verify(scheduler).onStatusEvent(argThat { !it.showAnimation })
        }

    @Test
    @EnableFlags(FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION)
    fun onPrivacyItemsChanged_defaultCamera_thenAnotherApp_showsAnimation() =
        testScope.runTest {
            val privacyList1 =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication(DEFAULT_CAMERA_PACKAGE_NAME, 1),
                        privacyType = PrivacyType.TYPE_CAMERA,
                    )
                )
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList1)
            verify(scheduler).onStatusEvent(argThat { !it.showAnimation })

            val privacyList2 =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication(DEFAULT_CAMERA_PACKAGE_NAME, 1),
                        privacyType = PrivacyType.TYPE_CAMERA,
                    ),
                    PrivacyItem(
                        application = PrivacyApplication("package1", 1),
                        privacyType = PrivacyType.TYPE_MICROPHONE,
                    ),
                )
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList2)
            verify(scheduler).onStatusEvent(argThat { it.showAnimation })
        }

    @Test
    @DisableFlags(FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION)
    fun onPrivacyItemsChanged_defaultCameraApp_cameraAccess_flagOff_showsAnimation() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication(DEFAULT_CAMERA_PACKAGE_NAME, 1),
                        privacyType = PrivacyType.TYPE_CAMERA,
                    )
                )
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            verify(scheduler).onStatusEvent(argThat { it.showAnimation })
        }

    @Test
    @DisableFlags(FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION)
    fun onPrivacyItemsChanged_defaultCameraApp_microphoneAccess_flagOff_showsAnimation() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication(DEFAULT_CAMERA_PACKAGE_NAME, 1),
                        privacyType = PrivacyType.TYPE_MICROPHONE,
                    )
                )
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            verify(scheduler).onStatusEvent(argThat { it.showAnimation })
        }

    @Test
    @EnableFlags(FLAG_LOCATION_INDICATORS_ENABLED, FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION)
    fun onPrivacyItemsChanged_locationThenMicForDefaultCamera_noAnimation() =
        testScope.runTest {
            val locationList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("maps", 1),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    )
                )
            val micAndLocationList =
                locationList +
                    PrivacyItem(
                        application = PrivacyApplication(DEFAULT_CAMERA_PACKAGE_NAME, 2),
                        privacyType = PrivacyType.TYPE_MICROPHONE,
                    )

            // First, location access shows animation
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(locationList)
            verify(scheduler).onStatusEvent(argThat { it.showAnimation })

            // Second, location access within debounce window, no animation
            fakeSystemClock.advanceTime(ADVANCE_TIME_WITHIN_DEBOUNCE_MS)
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(emptyList())
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(locationList)
            verify(scheduler).onStatusEvent(argThat { !it.showAnimation })

            // Third, add mic access for default camera, no animation
            systemEventCoordinator
                .getPrivacyStateListener()
                .onPrivacyItemsChanged(micAndLocationList)
            verify(scheduler, times(2)).onStatusEvent(argThat { !it.showAnimation })
        }

    @Test
    @EnableFlags(FLAG_LOCATION_INDICATORS_ENABLED, FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION)
    fun onPrivacyItemsChanged_micForDefaultCameraThenLocation_showsAnimationOnce() =
        testScope.runTest {
            val micList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication(DEFAULT_CAMERA_PACKAGE_NAME, 1),
                        privacyType = PrivacyType.TYPE_MICROPHONE,
                    )
                )
            val micAndLocationList =
                micList +
                    PrivacyItem(
                        application = PrivacyApplication("maps", 2),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    )

            // First, mic access for default camera, no animation
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(micList)
            verify(scheduler).onStatusEvent(argThat { !it.showAnimation })

            // Second, add location access, shows animation
            systemEventCoordinator
                .getPrivacyStateListener()
                .onPrivacyItemsChanged(micAndLocationList)
            verify(scheduler).onStatusEvent(argThat { it.showAnimation })

            // Third, second location access within debounce window, no animation
            fakeSystemClock.advanceTime(ADVANCE_TIME_WITHIN_DEBOUNCE_MS)
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(emptyList())
            systemEventCoordinator
                .getPrivacyStateListener()
                .onPrivacyItemsChanged(micAndLocationList)
            verify(scheduler, times(2)).onStatusEvent(argThat { !it.showAnimation })
        }

    @Test
    @EnableFlags(FLAG_LOCATION_INDICATORS_ENABLED)
    fun onPrivacyItemsChanged_locationDebounced_thenMic_showsAnimation() =
        testScope.runTest {
            val locationList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("maps", 1),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    )
                )
            val micAndLocationList =
                locationList +
                    PrivacyItem(
                        application = PrivacyApplication("recorder", 2),
                        privacyType = PrivacyType.TYPE_MICROPHONE,
                    )

            // First, location access shows animation
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(locationList)
            verify(scheduler).onStatusEvent(argThat { it.showAnimation })

            // Second, location access within debounce window, no animation
            fakeSystemClock.advanceTime(ADVANCE_TIME_WITHIN_DEBOUNCE_MS)
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(emptyList())
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(locationList)
            verify(scheduler).onStatusEvent(argThat { !it.showAnimation })

            // Third, add mic access, which should show an animation
            systemEventCoordinator
                .getPrivacyStateListener()
                .onPrivacyItemsChanged(micAndLocationList)
            verify(scheduler, times(2)).onStatusEvent(argThat { it.showAnimation })
        }

    @Test
    @EnableFlags(FLAG_LOCATION_INDICATORS_ENABLED)
    fun onPrivacyItemsChanged_location_continuousUsage_onlyAnimatesFirstTime() =
        testScope.runTest {
            val privacyList =
                listOf(
                    PrivacyItem(
                        application = PrivacyApplication("maps", 1),
                        privacyType = PrivacyType.TYPE_LOCATION,
                    )
                )

            // First event, show animation
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            // Second event, within debounce window, should not show animation
            fakeSystemClock.advanceTime(ADVANCE_TIME_WITHIN_DEBOUNCE_MS)
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(emptyList())
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            // Third event, also within the debounce window of the *last usage*, should not show
            // animation
            fakeSystemClock.advanceTime(ADVANCE_TIME_WITHIN_DEBOUNCE_MS)
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(emptyList())
            systemEventCoordinator.getPrivacyStateListener().onPrivacyItemsChanged(privacyList)

            // Verify that the animation was only shown once, and no-animation was shown twice.
            verify(scheduler).onStatusEvent(argThat { it.showAnimation })
            verify(scheduler, times(2)).onStatusEvent(argThat { !it.showAnimation })
        }

    class FakeConnectedDisplayInteractor : ConnectedDisplayInteractor {
        private val flow = MutableSharedFlow<Unit>()

        suspend fun emit() = flow.emit(Unit)

        override val connectedDisplayState: Flow<ConnectedDisplayInteractor.State>
            get() = MutableSharedFlow<ConnectedDisplayInteractor.State>()

        override val connectedDisplayAddition: Flow<Unit>
            get() = flow

        override val pendingDisplay: Flow<PendingDisplay?>
            get() = MutableSharedFlow<PendingDisplay>()

        override val concurrentDisplaysInProgress: Flow<Boolean>
            get() = TODO("Not yet implemented")
    }
}

private const val DEFAULT_CAMERA_PACKAGE_NAME = "my.camera.package"
