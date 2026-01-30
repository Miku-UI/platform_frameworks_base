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
package com.android.systemui.classifier

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.testing.TestableLooper.RunWithLooper
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.data.repository.communalSceneRepository
import com.android.systemui.communal.domain.interactor.communalInteractor
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.dock.DockManager
import com.android.systemui.dock.dockManager
import com.android.systemui.dock.fakeDockManager
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.domain.interactor.KeyguardOcclusionInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.SysuiStatusBarStateController
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.testKosmos
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.util.kotlin.javaAdapter
import com.android.systemui.util.sensors.ProximitySensor
import com.android.systemui.util.sensors.ThresholdSensor
import com.android.systemui.util.time.fakeSystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@RunWithLooper(setAsMainLooper = true)
class FalsingCollectorImplTest(flags: FlagsParameterization) : SysuiTestCase() {
    private var kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val isDeviceEntered = MutableStateFlow(false)
    private val isKeyguardOccluded = MutableStateFlow(false)

    private var falsingDataProvider = mock<FalsingDataProvider>()
    private var keyguardUpdateMonitor = mock<KeyguardUpdateMonitor>()
    private var historyTracker = mock<HistoryTracker>()
    private var proximitySensor = mock<ProximitySensor>()
    private var statusBarStateController =
        mock<SysuiStatusBarStateController> { on { state } doReturn StatusBarState.KEYGUARD }
    private var keyguardStateController =
        mock<KeyguardStateController> {
            on { isShowing } doReturn true
            on { isOccluded } doReturn false
        }
    private var shadeInteractor =
        mock<ShadeInteractor> { on { isQsExpanded } doReturn MutableStateFlow(false) }
    private var batteryController = mock<BatteryController>()
    private var selectedUserInteractor = mock<SelectedUserInteractor>()
    private var deviceEntryInteractor =
        mock<DeviceEntryInteractor> { on { isDeviceEntered } doReturn isDeviceEntered }
    private var occlusionInteractor =
        mock<KeyguardOcclusionInteractor> { on { isKeyguardOccluded } doReturn isKeyguardOccluded }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            FalsingCollectorImpl(
                falsingDataProvider,
                falsingManager,
                keyguardUpdateMonitor,
                historyTracker,
                proximitySensor,
                statusBarStateController,
                keyguardStateController,
                { shadeInteractor },
                batteryController,
                dockManager,
                fakeExecutor,
                javaAdapter,
                fakeSystemClock,
                { selectedUserInteractor },
                { communalInteractor },
                { communalSceneInteractor },
                { deviceEntryInteractor },
                { occlusionInteractor },
            )
        }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        kosmos.underTest.init()
    }

    @Test
    fun testRegisterSensor() =
        kosmos.runTest {
            underTest.onScreenTurningOn()
            verify(proximitySensor).register(any<ThresholdSensor.Listener>())
        }

    @Test
    fun testNoProximityWhenWirelessCharging() =
        kosmos.runTest {
            val batteryCallbackCaptor = argumentCaptor<BatteryStateChangeCallback>()
            verify(batteryController).addCallback(batteryCallbackCaptor.capture())
            batteryCallbackCaptor.firstValue.onWirelessChargingChanged(true)
            verify(proximitySensor).pause()
        }

    @Test
    fun testProximityWhenOffWirelessCharging() =
        kosmos.runTest {
            val batteryCallbackCaptor = argumentCaptor<BatteryStateChangeCallback>()
            verify(batteryController).addCallback(batteryCallbackCaptor.capture())
            batteryCallbackCaptor.firstValue.onWirelessChargingChanged(false)
            verify(proximitySensor).resume()
        }

    @Test
    fun testNoProximityWhenDocked() =
        kosmos.runTest {
            fakeDockManager.setDockEvent(DockManager.STATE_DOCKED)
            verify(proximitySensor).pause()
        }

    @Test
    fun testProximityWhenUndocked() =
        kosmos.runTest {
            fakeDockManager.setDockEvent(DockManager.STATE_NONE)
            verify(proximitySensor).resume()
        }

    @Test
    fun testUnregisterSensor() =
        kosmos.runTest {
            underTest.onScreenTurningOn()
            reset(proximitySensor)
            underTest.onScreenOff()
            verify(proximitySensor).unregister(any<ThresholdSensor.Listener>())
        }

    @Test
    fun testUnregisterSensor_QS() =
        kosmos.runTest {
            underTest.onScreenTurningOn()
            reset(proximitySensor)
            underTest.onQsExpansionChanged(true)
            verify(proximitySensor).unregister(any<ThresholdSensor.Listener>())
            underTest.onQsExpansionChanged(false)
            verify(proximitySensor).register(any<ThresholdSensor.Listener>())
        }

    @Test
    fun testUnregisterSensor_Bouncer() =
        kosmos.runTest {
            underTest.onScreenTurningOn()
            reset(proximitySensor)
            underTest.onBouncerShown()
            verify(proximitySensor).unregister(any<ThresholdSensor.Listener>())
            underTest.onBouncerHidden()
            verify(proximitySensor).register(any<ThresholdSensor.Listener>())
        }

    @Test
    fun testUnregisterSensor_StateTransition() =
        kosmos.runTest {
            val stateListenerArgumentCaptor =
                argumentCaptor<StatusBarStateController.StateListener>()
            verify(statusBarStateController).addCallback(stateListenerArgumentCaptor.capture())

            underTest.onScreenTurningOn()
            reset(proximitySensor)
            stateListenerArgumentCaptor.firstValue.onStateChanged(StatusBarState.SHADE)
            verify(proximitySensor).unregister(any<ThresholdSensor.Listener>())
        }

    @Test
    @DisableSceneContainer
    fun testRegisterSensor_OccludingActivity_sceneContainerDisabled() =
        kosmos.runTest {
            whenever(keyguardStateController.isOccluded).thenReturn(true)

            val stateListenerArgumentCaptor =
                argumentCaptor<StatusBarStateController.StateListener>()
            verify(statusBarStateController).addCallback(stateListenerArgumentCaptor.capture())

            underTest.onScreenTurningOn()
            reset(proximitySensor)
            stateListenerArgumentCaptor.firstValue.onStateChanged(StatusBarState.SHADE)
            verify(proximitySensor).register(any<ThresholdSensor.Listener>())
        }

    @Test
    @EnableSceneContainer
    fun testRegisterSensor_OccludingActivity_sceneContainerEnabled() =
        kosmos.runTest {
            isKeyguardOccluded.value = true

            val stateListenerArgumentCaptor =
                argumentCaptor<StatusBarStateController.StateListener>()
            verify(statusBarStateController).addCallback(stateListenerArgumentCaptor.capture())

            underTest.onScreenTurningOn()
            reset(proximitySensor)
            stateListenerArgumentCaptor.firstValue.onStateChanged(StatusBarState.SHADE)
            verify(proximitySensor).register(any<ThresholdSensor.Listener>())
        }

    @Test
    fun testPassThroughEnterKeyEvent() =
        kosmos.runTest {
            val enterDown =
                KeyEvent.obtain(
                    0,
                    0,
                    MotionEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_ENTER,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "",
                )
            val enterUp =
                KeyEvent.obtain(
                    0,
                    0,
                    MotionEvent.ACTION_UP,
                    KeyEvent.KEYCODE_ENTER,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "",
                )

            underTest.onKeyEvent(enterDown)
            verify(falsingDataProvider, never()).onKeyEvent(any<KeyEvent>())

            underTest.onKeyEvent(enterUp)
            verify(falsingDataProvider, times(1)).onKeyEvent(enterUp)
        }

    @Test
    fun testAvoidAKeyEvent() =
        kosmos.runTest {
            // Arbitrarily chose the "A" key, as it is not currently allowlisted. If this key is
            // allowlisted in the future, please choose another key that will not be collected.
            val aKeyDown =
                KeyEvent.obtain(
                    0,
                    0,
                    MotionEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_A,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "",
                )
            val aKeyUp =
                KeyEvent.obtain(
                    0,
                    0,
                    MotionEvent.ACTION_UP,
                    KeyEvent.KEYCODE_A,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "",
                )

            underTest.onKeyEvent(aKeyDown)
            verify(falsingDataProvider, never()).onKeyEvent(any<KeyEvent>())

            underTest.onKeyEvent(aKeyUp)
            verify(falsingDataProvider, never()).onKeyEvent(any<KeyEvent>())
        }

    @Test
    fun testPassThroughGesture() =
        kosmos.runTest {
            val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            val up = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)

            // Nothing passed initially
            underTest.onTouchEvent(down)
            verify(falsingDataProvider, never()).onMotionEvent(any<MotionEvent>())

            // Up event flushes the down event.
            underTest.onTouchEvent(up)
            inOrder(falsingDataProvider) {
                // We can't simply use "eq" or similar because the collector makes a copy of "down".
                verify(falsingDataProvider)
                    .onMotionEvent(
                        argThat { argument: MotionEvent ->
                            argument.actionMasked == MotionEvent.ACTION_DOWN
                        }
                    )
                verify(falsingDataProvider).onMotionEvent(up)
            }
        }

    @Test
    fun testAvoidGesture() =
        kosmos.runTest {
            val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            val up = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

            // Nothing passed initially
            underTest.onTouchEvent(down)
            verify(falsingDataProvider, never()).onMotionEvent(any<MotionEvent>())

            underTest.avoidGesture()
            // Up event would flush, but we were told to avoid.
            underTest.onTouchEvent(up)
            verify(falsingDataProvider, never()).onMotionEvent(any<MotionEvent>())
        }

    @Test
    fun testIgnoreActionOutside() =
        kosmos.runTest {
            val outside = MotionEvent.obtain(0, 0, MotionEvent.ACTION_OUTSIDE, 0f, 0f, 0)
            val up = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)

            // Nothing passed initially. The outside event will be completely ignored.
            underTest.onTouchEvent(outside)
            verify(falsingDataProvider, never()).onMotionEvent(any<MotionEvent>())

            // Up event flushes, and the outside event isn't passed through.
            underTest.onTouchEvent(up)
            verify(falsingDataProvider).onMotionEvent(up)
        }

    @Test
    @DisableSceneContainer
    fun testAvoidUnlocked_sceneContainerDisabled() =
        kosmos.runTest {
            val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            val up = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)

            whenever(keyguardStateController.isShowing).thenReturn(false)

            // Nothing passed initially
            underTest.onTouchEvent(down)
            verify(falsingDataProvider, never()).onMotionEvent(any<MotionEvent>())

            // Up event would normally flush the up event, but doesn't.
            underTest.onTouchEvent(up)
            verify(falsingDataProvider, never()).onMotionEvent(any<MotionEvent>())
        }

    @Test
    @EnableSceneContainer
    fun testAvoidUnlocked_sceneContainerEnabled() =
        kosmos.runTest {
            val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            val up = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)

            isDeviceEntered.value = true

            // Nothing passed initially
            underTest.onTouchEvent(down)
            verify(falsingDataProvider, never()).onMotionEvent(any<MotionEvent>())

            // Up event would normally flush the up event, but doesn't.
            underTest.onTouchEvent(up)
            verify(falsingDataProvider, never()).onMotionEvent(any<MotionEvent>())
        }

    @Test
    fun testGestureWhenDozing() =
        kosmos.runTest {
            // We check the FalsingManager for taps during the transition to AoD (dozing=true,
            // pulsing=false), so the FalsingCollector needs to continue to analyze events that
            // occur
            // while the device is dozing.
            val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            val up = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)

            whenever(statusBarStateController.isDozing).thenReturn(true)

            // Nothing passed initially
            underTest.onTouchEvent(down)
            verify(falsingDataProvider, never()).onMotionEvent(any<MotionEvent>())

            // Up event flushes
            underTest.onTouchEvent(up)
            verify(falsingDataProvider, times(2)).onMotionEvent(any<MotionEvent>())
        }

    @Test
    fun testGestureWhenPulsing() =
        kosmos.runTest {
            val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            val up = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)

            whenever(statusBarStateController.isDozing).thenReturn(true)
            whenever(statusBarStateController.isPulsing).thenReturn(true)

            // Nothing passed initially
            underTest.onTouchEvent(down)
            verify(falsingDataProvider, never()).onMotionEvent(any<MotionEvent>())

            // Up event would flushes
            underTest.onTouchEvent(up)
            verify(falsingDataProvider, times(2)).onMotionEvent(any<MotionEvent>())
        }

    @Test
    fun testOnA11yAction() =
        kosmos.runTest {
            underTest.onA11yAction()
            verify(falsingDataProvider).onA11yAction()
        }

    @Test
    @EnableFlags(Flags.FLAG_COMMUNAL_SHADE_TOUCH_HANDLING_FIXES)
    @DisableSceneContainer
    fun testCommunalShowingChanged_dataProviderUpdated() =
        kosmos.runTest {
            // Communal is enabled.
            communalSettingsInteractor.setSuppressionReasons(emptyList())

            communalSceneRepository.changeScene(CommunalScenes.Blank)
            verify(falsingDataProvider).isShowingCommunalHub = false

            communalSceneRepository.changeScene(CommunalScenes.Communal)
            verify(falsingDataProvider).isShowingCommunalHub = true
        }

    @Test
    @EnableFlags(Flags.FLAG_COMMUNAL_SHADE_TOUCH_HANDLING_FIXES)
    @EnableSceneContainer
    fun testCommunalShowingChanged_dataProviderUpdated_sceneContainer() =
        kosmos.runTest {
            // Communal is enabled.
            communalSettingsInteractor.setSuppressionReasons(emptyList())

            sceneContainerRepository.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen))
            )
            verify(falsingDataProvider).isShowingCommunalHub = false

            sceneContainerRepository.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Communal))
            )
            verify(falsingDataProvider).isShowingCommunalHub = true
        }

    @Test
    @EnableFlags(Flags.FLAG_COMMUNAL_SHADE_TOUCH_HANDLING_FIXES)
    @DisableSceneContainer
    fun testCommunalShowingChanged_hubShowing_sessionEnds() =
        kosmos.runTest {
            // Communal is enabled.
            communalSettingsInteractor.setSuppressionReasons(emptyList())
            // Session is started.
            underTest.onScreenTurningOn()
            verify(falsingDataProvider).onSessionStarted()

            // Communal shows.
            communalSceneRepository.changeScene(CommunalScenes.Communal)

            // Session ends.
            verify(falsingDataProvider).onSessionEnd()
        }

    @Test
    @EnableFlags(Flags.FLAG_COMMUNAL_SHADE_TOUCH_HANDLING_FIXES)
    @EnableSceneContainer
    fun testCommunalShowingChanged_hubShowing_sessionEnds_sceneContainer() =
        kosmos.runTest {
            // Communal is enabled.
            communalSettingsInteractor.setSuppressionReasons(emptyList())
            // Session is started.
            underTest.onScreenTurningOn()
            verify(falsingDataProvider).onSessionStarted()

            // Communal shows.
            sceneContainerRepository.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Communal))
            )

            // Session ends.
            verify(falsingDataProvider).onSessionEnd()
        }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }
}
