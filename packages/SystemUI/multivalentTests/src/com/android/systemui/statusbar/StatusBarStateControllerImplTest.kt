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

package com.android.systemui.statusbar

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.testing.TestableLooper
import androidx.test.filters.SmallTest
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.Flags.FLAG_SCENE_CONTAINER
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.bouncer.domain.interactor.alternateBouncerInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.parameterizeSceneContainerFlag
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.biometricUnlockInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.scene.SceneHelper.setDeviceEntered
import com.android.systemui.scene.data.repository.Idle
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.data.repository.setTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.disableDualShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@TestableLooper.RunWithLooper
class StatusBarStateControllerImplTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest: StatusBarStateControllerImpl by lazy {
        kosmos.statusBarStateController as StatusBarStateControllerImpl
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return parameterizeSceneContainerFlag()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        kosmos.sceneContainerStartable.start()
    }

    @Test
    @DisableSceneContainer
    fun testChangeState_logged() {
        TestableLooper.get(this).runWithLooper {
            underTest.state = StatusBarState.KEYGUARD
            underTest.state = StatusBarState.SHADE
            underTest.state = StatusBarState.SHADE_LOCKED
        }

        val stateLogIds =
            kosmos.uiEventLoggerFake.logs.map(UiEventLoggerFake.FakeUiEvent::eventId).filter {
                it == StatusBarStateEvent.STATUS_BAR_STATE_KEYGUARD.id ||
                    it == StatusBarStateEvent.STATUS_BAR_STATE_SHADE.id ||
                    it == StatusBarStateEvent.STATUS_BAR_STATE_SHADE_LOCKED.id
            }
        assertEquals(3, stateLogIds.size)
        assertEquals(StatusBarStateEvent.STATUS_BAR_STATE_KEYGUARD.id, stateLogIds[0])
        assertEquals(StatusBarStateEvent.STATUS_BAR_STATE_SHADE.id, stateLogIds[1])
        assertEquals(StatusBarStateEvent.STATUS_BAR_STATE_SHADE_LOCKED.id, stateLogIds[2])
    }

    @Test
    @DisableSceneContainer
    fun testSetDozeAmountInternal_onlySetsOnce() {
        val listener = mock(StatusBarStateController.StateListener::class.java)
        underTest.addCallback(listener)

        underTest.setAndInstrumentDozeAmount(null, 0.5f, false /* animated */)
        underTest.setAndInstrumentDozeAmount(null, 0.5f, false /* animated */)
        verify(listener).onDozeAmountChanged(eq(0.5f), anyFloat())
    }

    @Test
    @DisableSceneContainer
    fun testSetState_appliesState_sameStateButDifferentUpcomingState() {
        underTest.state = StatusBarState.SHADE
        underTest.setUpcomingState(StatusBarState.KEYGUARD)

        assertEquals(underTest.state, StatusBarState.SHADE)

        // We should return true (state change was applied) despite going from SHADE to SHADE, since
        // the upcoming state was set to KEYGUARD.
        assertTrue(underTest.setState(StatusBarState.SHADE))
    }

    @Test
    @DisableSceneContainer
    fun testSetState_appliesState_differentStateEqualToUpcomingState() {
        underTest.state = StatusBarState.SHADE
        underTest.setUpcomingState(StatusBarState.KEYGUARD)

        assertEquals(underTest.state, StatusBarState.SHADE)

        // Make sure we apply a SHADE -> KEYGUARD state change when the upcoming state is KEYGUARD.
        assertTrue(underTest.setState(StatusBarState.KEYGUARD))
    }

    @Test
    @DisableSceneContainer
    fun testSetState_doesNotApplyState_currentAndUpcomingStatesSame() {
        underTest.state = StatusBarState.SHADE
        underTest.setUpcomingState(StatusBarState.SHADE)

        assertEquals(underTest.state, StatusBarState.SHADE)

        // We're going from SHADE -> SHADE, and the upcoming state is also SHADE, this should not do
        // anything.
        assertFalse(underTest.setState(StatusBarState.SHADE))

        // Double check that we can still force it to happen.
        assertTrue(underTest.setState(StatusBarState.SHADE, true /* force */))
    }

    @Test
    @DisableSceneContainer
    fun testSetDozeAmount_immediatelyChangesDozeAmount_lockscreenTransitionFromAod() {
        // Put controller in AOD state
        underTest.setAndInstrumentDozeAmount(null, 1f, false)

        // When waking from doze, CentralSurfaces#updateDozingState will update the dozing state
        // before the doze amount changes
        underTest.setIsDozing(false)

        // Animate the doze amount to 0f, as would normally happen
        underTest.setAndInstrumentDozeAmount(null, 0f, true)

        // Check that the doze amount is immediately set to a value slightly less than 1f. This is
        // to ensure that any scrim implementation changes its opacity immediately rather than
        // waiting an extra frame. Waiting an extra frame will cause a relayout (which is expensive)
        // and cause us to drop a frame during the KEYGUARD_TRANSITION_AOD_TO_LOCKSCREEN CUJ.
        assertEquals(0.99f, underTest.dozeAmount, 0.009f)
    }

    @Test
    fun testSetDreamState_invokesCallback() {
        val listener = mock(StatusBarStateController.StateListener::class.java)
        underTest.addCallback(listener)

        underTest.setIsDreaming(true)
        verify(listener).onDreamingChanged(true)

        Mockito.clearInvocations(listener)

        underTest.setIsDreaming(false)
        verify(listener).onDreamingChanged(false)
    }

    @Test
    fun testSetDreamState_getterReturnsCurrentState() {
        underTest.setIsDreaming(true)
        assertTrue(underTest.isDreaming)

        underTest.setIsDreaming(false)
        assertFalse(underTest.isDreaming)
    }

    @Test
    @EnableSceneContainer
    @DisableFlags(FLAG_DUAL_SHADE)
    fun start_hydratesStatusBarState_whileLocked() =
        kosmos.runTest {
            disableDualShade()
            var statusBarState = underTest.state
            val listener =
                object : StatusBarStateController.StateListener {
                    override fun onStateChanged(newState: Int) {
                        statusBarState = newState
                    }
                }
            underTest.addCallback(listener)

            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            val deviceUnlockStatus by collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus)
            val shadeMode by collectLastValue(shadeModeInteractor.shadeMode)

            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Password)
            assertThat(deviceUnlockStatus!!.isUnlocked).isFalse()

            sceneInteractor.changeScene(toScene = Scenes.Lockscreen, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            // Call start to begin hydrating based on the scene framework:
            underTest.start()

            sceneInteractor.showOverlay(overlay = Overlays.Bouncer, loggingReason = "reason")
            assertThat(currentOverlays).contains(Overlays.Bouncer)
            assertThat(statusBarState).isEqualTo(StatusBarState.KEYGUARD)

            sceneInteractor.changeScene(toScene = Scenes.Shade, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE_LOCKED)

            if (shadeMode is ShadeMode.Single) {
                sceneInteractor.changeScene(
                    toScene = Scenes.QuickSettings,
                    loggingReason = "reason",
                )
                assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
                assertThat(statusBarState).isEqualTo(StatusBarState.SHADE_LOCKED)
            }

            sceneInteractor.changeScene(toScene = Scenes.Communal, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Communal)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE_LOCKED)

            sceneInteractor.changeScene(toScene = Scenes.Lockscreen, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(statusBarState).isEqualTo(StatusBarState.KEYGUARD)
        }

    @Test
    @EnableSceneContainer
    @DisableFlags(FLAG_DUAL_SHADE)
    fun start_hydratesStatusBarState_withAlternateBouncer() =
        kosmos.runTest {
            disableDualShade()
            var statusBarState = underTest.state
            val listener =
                object : StatusBarStateController.StateListener {
                    override fun onStateChanged(newState: Int) {
                        statusBarState = newState
                    }
                }
            underTest.addCallback(listener)

            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val deviceUnlockStatus by collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus)
            val alternateBouncerIsVisible by collectLastValue(alternateBouncerInteractor.isVisible)

            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Password)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            assertThat(deviceUnlockStatus!!.isUnlocked).isTrue()

            sceneInteractor.changeScene(toScene = Scenes.Lockscreen, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            alternateBouncerInteractor.forceShow()
            assertThat(alternateBouncerIsVisible).isTrue()

            // Call start to begin hydrating based on the scene framework:
            underTest.start()

            sceneInteractor.changeScene(toScene = Scenes.Gone, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(statusBarState).isEqualTo(StatusBarState.KEYGUARD)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, FLAG_DUAL_SHADE)
    fun start_hydratesStatusBarState_dualShade_whileLocked() =
        kosmos.runTest {
            kosmos.enableDualShade()
            var statusBarState = underTest.state
            val listener =
                object : StatusBarStateController.StateListener {
                    override fun onStateChanged(newState: Int) {
                        statusBarState = newState
                    }
                }
            underTest.addCallback(listener)

            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            val deviceUnlockStatus by collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus)

            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Password)
            assertThat(deviceUnlockStatus!!.isUnlocked).isFalse()

            sceneInteractor.changeScene(Scenes.Lockscreen, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            // Call start to begin hydrating based on the scene framework:
            underTest.start()

            sceneInteractor.showOverlay(Overlays.Bouncer, loggingReason = "reason")
            assertThat(currentOverlays).contains(Overlays.Bouncer)
            assertThat(statusBarState).isEqualTo(StatusBarState.KEYGUARD)

            sceneInteractor.hideOverlay(Overlays.Bouncer, loggingReason = "reason")

            sceneInteractor.showOverlay(Overlays.NotificationsShade, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE_LOCKED)

            sceneInteractor.replaceOverlay(
                from = Overlays.NotificationsShade,
                to = Overlays.QuickSettingsShade,
                loggingReason = "reason",
            )
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.QuickSettingsShade)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE_LOCKED)

            sceneInteractor.hideOverlay(Overlays.QuickSettingsShade, loggingReason = "reason")
            sceneInteractor.changeScene(Scenes.Communal, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Communal)
            assertThat(currentOverlays).isEmpty()
            assertThat(statusBarState).isEqualTo(StatusBarState.KEYGUARD)

            sceneInteractor.changeScene(Scenes.Lockscreen, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()
            assertThat(statusBarState).isEqualTo(StatusBarState.KEYGUARD)
        }

    @Test
    @EnableSceneContainer
    fun start_hydratesStatusBarState_whileUnlocked_singleShade() =
        kosmos.runTest {
            enableSingleShade()
            var statusBarState = underTest.state
            val listener =
                object : StatusBarStateController.StateListener {
                    override fun onStateChanged(newState: Int) {
                        statusBarState = newState
                    }
                }
            underTest.addCallback(listener)

            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val deviceUnlockStatus by collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus)
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Password)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            assertThat(deviceUnlockStatus!!.isUnlocked).isTrue()

            sceneInteractor.changeScene(toScene = Scenes.Lockscreen, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            // Call start to begin hydrating based on the scene framework:
            underTest.start()

            assertThat(statusBarState).isEqualTo(StatusBarState.KEYGUARD)

            sceneInteractor.changeScene(toScene = Scenes.Gone, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE)

            sceneInteractor.changeScene(toScene = Scenes.Shade, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE)

            sceneInteractor.changeScene(toScene = Scenes.QuickSettings, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, FLAG_DUAL_SHADE)
    fun start_hydratesStatusBarState_whileUnlocked_dualShade() =
        kosmos.runTest {
            enableDualShade()
            var statusBarState = underTest.state
            val listener =
                object : StatusBarStateController.StateListener {
                    override fun onStateChanged(newState: Int) {
                        statusBarState = newState
                    }
                }
            underTest.addCallback(listener)

            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            val deviceUnlockStatus by collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus)
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Password)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            assertThat(deviceUnlockStatus!!.isUnlocked).isTrue()

            sceneInteractor.changeScene(toScene = Scenes.Lockscreen, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            // Call start to begin hydrating based on the scene framework:
            underTest.start()

            assertThat(statusBarState).isEqualTo(StatusBarState.KEYGUARD)

            sceneInteractor.changeScene(toScene = Scenes.Gone, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE)

            sceneInteractor.showOverlay(Overlays.NotificationsShade, loggingReason = "reason")
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE)

            sceneInteractor.replaceOverlay(
                from = Overlays.NotificationsShade,
                to = Overlays.QuickSettingsShade,
                loggingReason = "reason",
            )
            assertThat(currentOverlays).containsExactly(Overlays.QuickSettingsShade)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE)
        }

    @Test
    @EnableSceneContainer
    fun start_hydratesStatusBarState_whileOccluded_singleShade() =
        kosmos.runTest {
            enableSingleShade()
            var statusBarState = underTest.state
            val listener =
                object : StatusBarStateController.StateListener {
                    override fun onStateChanged(newState: Int) {
                        statusBarState = newState
                    }
                }
            underTest.addCallback(listener)

            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            sceneInteractor.changeScene(toScene = Scenes.Occluded, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Occluded)

            // Call start to begin hydrating based on the scene framework:
            underTest.start()

            sceneInteractor.changeScene(toScene = Scenes.Shade, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE)

            sceneInteractor.changeScene(toScene = Scenes.QuickSettings, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, FLAG_DUAL_SHADE)
    fun start_hydratesStatusBarState_whileOccluded_dualShade() =
        kosmos.runTest {
            enableDualShade()
            var statusBarState = underTest.state
            val listener =
                object : StatusBarStateController.StateListener {
                    override fun onStateChanged(newState: Int) {
                        statusBarState = newState
                    }
                }
            underTest.addCallback(listener)

            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            sceneInteractor.changeScene(toScene = Scenes.Occluded, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Occluded)

            // Call start to begin hydrating based on the scene framework:
            underTest.start()

            sceneInteractor.showOverlay(Overlays.NotificationsShade, loggingReason = "reason")
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE)

            sceneInteractor.replaceOverlay(
                from = Overlays.NotificationsShade,
                to = Overlays.QuickSettingsShade,
                loggingReason = "reason",
            )
            assertThat(currentOverlays).containsExactly(Overlays.QuickSettingsShade)
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE)
        }

    @Test
    @EnableSceneContainer
    fun start_hydratesStatusBarState_duringTransitionFromLockscreenToGone() =
        kosmos.runTest {
            var statusBarState = underTest.state
            val listener =
                object : StatusBarStateController.StateListener {
                    override fun onStateChanged(newState: Int) {
                        statusBarState = newState
                    }
                }
            underTest.addCallback(listener)

            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val deviceUnlockStatus by collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus)

            // initial state: device is locked, on Lockscreen
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Password)
            assertThat(deviceUnlockStatus!!.isUnlocked).isFalse()

            sceneInteractor.changeScene(toScene = Scenes.Lockscreen, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            // Call start to begin hydrating based on the scene framework:
            underTest.start()

            assertThat(statusBarState).isEqualTo(StatusBarState.KEYGUARD)

            // unlock device
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            assertThat(deviceUnlockStatus!!.isUnlocked).isTrue()

            // device begins transition from lockscreen to gone
            setSceneTransition(Transition(from = Scenes.Lockscreen, to = Scenes.Gone))
            assertThat(currentScene).isEqualTo(Scenes.Gone)

            // because we are still in transition, even though device is unlocked and current
            // (destination) scene is Gone, status bar state should still be KEYGUARD until
            // the transition finishes
            assertThat(statusBarState).isEqualTo(StatusBarState.KEYGUARD)

            // complete transition
            setSceneTransition(Idle(Scenes.Gone))
            assertThat(statusBarState).isEqualTo(StatusBarState.SHADE)
        }

    @Test
    @DisableSceneContainer
    fun leaveOpenOnKeyguard_whenGone_isFalse() =
        kosmos.runTest {
            underTest.start()
            underTest.setLeaveOpenOnKeyguardHide(true)
            sceneInteractor.snapToScene(Scenes.Lockscreen, "")
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.None)

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.AOD,
                to = KeyguardState.LOCKSCREEN,
                testScope = testScope,
            )
            assertThat(underTest.leaveOpenOnKeyguardHide()).isEqualTo(true)

            sceneInteractor.changeScene(Scenes.Gone, "")
            setTransition(
                sceneTransition = Idle(Scenes.Gone),
                stateTransition =
                    TransitionStep(from = KeyguardState.LOCKSCREEN, to = KeyguardState.GONE),
            )

            assertThat(underTest.leaveOpenOnKeyguardHide()).isEqualTo(false)
        }

    @Test
    @EnableSceneContainer
    fun whenDeviceEnteredFalse_leaveOpenOnKeyguardResetToFalse() =
        kosmos.runTest {
            underTest.start()
            this.setDeviceEntered(true)
            underTest.setLeaveOpenOnKeyguardHide(true)
            assertThat(underTest.leaveOpenOnKeyguardHide()).isEqualTo(true)

            this.setDeviceEntered(false)
            assertThat(underTest.leaveOpenOnKeyguardHide()).isEqualTo(false)
        }
}
