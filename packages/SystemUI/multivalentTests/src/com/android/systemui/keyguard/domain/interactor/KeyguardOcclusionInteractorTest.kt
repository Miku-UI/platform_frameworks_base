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

package com.android.systemui.keyguard.domain.interactor

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_DREAM
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.service.dreams.Flags.FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.domain.model.OcclusionStateModel
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class KeyguardOcclusionInteractorTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest: KeyguardOcclusionInteractor by
        Kosmos.Fixture { keyguardOcclusionInteractor }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Test
    @EnableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    fun occlusionState_whenNotOccluded_isNone() =
        kosmos.runTest {
            val occlusionState by collectLastValue(underTest.occlusionState)
            keyguardOcclusionRepository.setOccludedFromWm(false)
            assertThat(occlusionState).isEqualTo(OcclusionStateModel.NONE)
        }

    @Test
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    fun occlusionState_whenOccluded_flagOff_isLegacy() =
        kosmos.runTest {
            val occlusionState by collectLastValue(underTest.occlusionState)
            powerInteractor.setAwakeForTest()
            keyguardOcclusionRepository.setOccludedFromWm(true)
            assertThat(occlusionState).isEqualTo(OcclusionStateModel.LEGACY_OCCLUDED_GENERIC)
        }

    @Test
    @EnableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    fun occlusionState_whenOccludedByApp_flagOn_isApp() =
        kosmos.runTest {
            val occlusionState by collectLastValue(underTest.occlusionState)
            powerInteractor.setAwakeForTest()
            val taskInfo = RunningTaskInfo().apply { topActivityType = ACTIVITY_TYPE_STANDARD }
            keyguardOcclusionRepository.setOccludedFromRemoteAnimation(true, taskInfo)
            assertThat(occlusionState).isEqualTo(OcclusionStateModel.APP)
        }

    @Test
    @EnableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    fun occlusionState_whenOccludedByDream_flagOn_isDream() =
        kosmos.runTest {
            val occlusionState by collectLastValue(underTest.occlusionState)
            powerInteractor.setAwakeForTest()
            val taskInfo = RunningTaskInfo().apply { topActivityType = ACTIVITY_TYPE_DREAM }
            keyguardOcclusionRepository.setOccludedFromRemoteAnimation(true, taskInfo)
            assertThat(occlusionState).isEqualTo(OcclusionStateModel.DREAM)
        }

    @Test
    fun transitionFromPowerGesture_whileGoingToSleep_isTrue() =
        kosmos.runTest {
            powerInteractor.setAwakeForTest()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope = testScope,
                throughTransitionState = TransitionState.RUNNING,
            )

            powerInteractor.onCameraLaunchGestureDetected()

            assertThat(underTest.shouldTransitionFromPowerButtonGesture()).isTrue()
        }

    @Test
    fun transitionFromPowerGesture_whileAsleep_isTrue() =
        kosmos.runTest {
            powerInteractor.setAwakeForTest()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope = testScope,
            )

            powerInteractor.onCameraLaunchGestureDetected()

            assertThat(underTest.shouldTransitionFromPowerButtonGesture()).isTrue()
        }

    @Test
    fun transitionFromPowerGesture_whileWaking_isFalse() =
        kosmos.runTest {
            powerInteractor.setAwakeForTest()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope = testScope,
            )
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.AOD,
                to = KeyguardState.LOCKSCREEN,
                testScope = testScope,
                throughTransitionState = TransitionState.RUNNING,
            )

            powerInteractor.onCameraLaunchGestureDetected()

            assertThat(underTest.shouldTransitionFromPowerButtonGesture()).isFalse()
        }

    @Test
    fun transitionFromPowerGesture_whileAwake_isFalse() =
        kosmos.runTest {
            powerInteractor.setAwakeForTest()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope = testScope,
            )
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.AOD,
                to = KeyguardState.LOCKSCREEN,
                testScope = testScope,
            )

            powerInteractor.onCameraLaunchGestureDetected()

            assertThat(underTest.shouldTransitionFromPowerButtonGesture()).isFalse()
        }

    @Test
    fun showWhenLockedActivityLaunchedFromPowerGesture_notTrueSecondTime() =
        kosmos.runTest {
            val values by collectValues(underTest.showWhenLockedActivityLaunchedFromPowerGesture)
            powerInteractor.setAsleepForTest()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope = testScope,
            )

            powerInteractor.onCameraLaunchGestureDetected()
            powerInteractor.setAwakeForTest()

            keyguardOcclusionRepository.setOccludedFromWm(true)
            assertThat(values).containsExactly(false, true)

            keyguardOcclusionRepository.setOccludedFromWm(false)
            assertThat(values).containsExactly(false, true, false)

            keyguardOcclusionRepository.setOccludedFromWm(true)
            assertThat(values)
                .containsExactly(
                    false,
                    true,
                    // Power button gesture was not triggered a second time, so this should remain
                    // false.
                    false,
                )
        }

    @Test
    @DisableSceneContainer
    fun showWhenLockedActivityLaunchedFromPowerGesture_falseIfReturningToGone() =
        kosmos.runTest {
            val values by collectValues(underTest.showWhenLockedActivityLaunchedFromPowerGesture)
            powerInteractor.setAwakeForTest()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                testScope = testScope,
            )

            powerInteractor.setAsleepForTest()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.GONE,
                to = KeyguardState.AOD,
                testScope = testScope,
                throughTransitionState = TransitionState.RUNNING,
            )

            powerInteractor.onCameraLaunchGestureDetected()
            keyguardOcclusionRepository.setOccludedFromWm(true)
            powerInteractor.setAwakeForTest()

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.AOD,
                to = KeyguardState.GONE,
                testScope = testScope,
            )

            assertThat(values).containsExactly(false)
        }

    @Test
    @EnableSceneContainer
    fun showWhenLockedActivityLaunchedFromPowerGesture_falseIfReturningToGone_scene_container() =
        kosmos.runTest {
            val values by collectValues(underTest.showWhenLockedActivityLaunchedFromPowerGesture)
            powerInteractor.setAwakeForTest()
            setSceneTransition(Transition(Scenes.Lockscreen, Scenes.Gone), skipChangeScene = true)

            powerInteractor.setAsleepForTest()

            setSceneTransition(Transition(Scenes.Gone, Scenes.Lockscreen))
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                testScope = testScope,
                throughTransitionState = TransitionState.RUNNING,
            )

            powerInteractor.onCameraLaunchGestureDetected()
            keyguardOcclusionRepository.setOccludedFromWm(true)
            powerInteractor.setAwakeForTest()

            setSceneTransition(Transition(Scenes.Lockscreen, Scenes.Gone), skipChangeScene = true)
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                testScope = testScope,
            )

            assertThat(values).containsExactly(false)
        }

    @Test
    @EnableSceneContainer
    fun occludingActivityWillDismissKeyguard() =
        kosmos.runTest {
            val occludingActivityWillDismissKeyguard by
                collectLastValue(underTest.occludingActivityWillDismissKeyguard)
            assertThat(occludingActivityWillDismissKeyguard).isFalse()
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)

            // Unlock device:
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            assertThat(occludingActivityWillDismissKeyguard).isTrue()

            // Re-lock device:
            powerInteractor.setAsleepForTest()
            kosmos.lockAfterDelayInteractor.timeoutElapsedForTesting()
            assertThat(occludingActivityWillDismissKeyguard).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isKeyguardOccluded_whenOnLockscreen_isTrue() =
        kosmos.runTest {
            val values by collectLastValue(underTest.isKeyguardOccluded)
            setSceneTransition(Transition(Scenes.Gone, Scenes.Lockscreen))
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                testScope = testScope,
                throughTransitionState = TransitionState.RUNNING,
            )

            keyguardOcclusionRepository.setOccludedFromWm(true)

            assertThat(values).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun isKeyguardOccluded_whenDeviceEntered_isFalse() =
        kosmos.runTest {
            val values by collectLastValue(underTest.isKeyguardOccluded)
            sceneInteractor.changeScene(Scenes.Lockscreen, "reason")
            sceneInteractor.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen))
            )

            // Make sure to unlock the device so it can be considered "Entered"
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            sceneInteractor.changeScene(Scenes.Gone, "reason")
            sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(Scenes.Gone)))

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                testScope = testScope,
            )

            keyguardOcclusionRepository.setOccludedFromWm(true)
            assertThat(values).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isKeyguardOccluded_whenAsleep_isFalse() =
        kosmos.runTest {
            val values by collectLastValue(underTest.isKeyguardOccluded)
            setSceneTransition(Transition(Scenes.Gone, Scenes.Lockscreen))
            powerInteractor.setAsleepForTest()

            keyguardOcclusionRepository.setOccludedFromWm(true)
            assertThat(values).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isKeyguardOccluded_whenAwake_isTrue() =
        kosmos.runTest {
            val values by collectLastValue(underTest.isKeyguardOccluded)
            setSceneTransition(Transition(Scenes.Gone, Scenes.Lockscreen))
            powerInteractor.setAsleepForTest()

            keyguardOcclusionRepository.setOccludedFromWm(true)
            powerInteractor.setAwakeForTest()
            assertThat(values).isTrue()
        }
}
