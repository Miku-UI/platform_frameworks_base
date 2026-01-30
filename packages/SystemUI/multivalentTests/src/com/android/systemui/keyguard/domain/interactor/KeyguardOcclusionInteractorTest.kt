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

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.KeyguardViewMediator
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.data.repository.userAwareSecureSettingsRepository
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class KeyguardOcclusionInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private lateinit var underTest: KeyguardOcclusionInteractor

    @Before
    fun setUp() {
        underTest = kosmos.keyguardOcclusionInteractor
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

            assertTrue(underTest.shouldTransitionFromPowerButtonGesture())
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

            assertTrue(underTest.shouldTransitionFromPowerButtonGesture())
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

            assertFalse(underTest.shouldTransitionFromPowerButtonGesture())
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

            assertFalse(underTest.shouldTransitionFromPowerButtonGesture())
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

            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true)
            assertThat(values).containsExactly(false, true)

            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(false)
            assertThat(values).containsExactly(false, true, false)

            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true)
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
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true)
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
            setSceneTransition(Transition(Scenes.Lockscreen, Scenes.Gone))

            powerInteractor.setAsleepForTest()

            setSceneTransition(Transition(Scenes.Gone, Scenes.Lockscreen))
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                testScope = testScope,
                throughTransitionState = TransitionState.RUNNING,
            )

            powerInteractor.onCameraLaunchGestureDetected()
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true)
            powerInteractor.setAwakeForTest()

            setSceneTransition(Transition(Scenes.Lockscreen, Scenes.Gone))
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
            deviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
            assertThat(occludingActivityWillDismissKeyguard).isTrue()

            // Re-lock device:
            powerInteractor.setAsleepForTest()
            testScope.advanceTimeBy(
                userAwareSecureSettingsRepository
                    .getInt(
                        Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                        KeyguardViewMediator.KEYGUARD_LOCK_AFTER_DELAY_DEFAULT,
                    )
                    .toLong()
            )
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

            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true)

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
            deviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
            testScope.runCurrent()

            sceneInteractor.changeScene(Scenes.Gone, "reason")
            sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(Scenes.Gone)))

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                testScope = testScope,
            )

            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true)
            assertThat(values).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isKeyguardOccluded_whenOnAod_isFalse() =
        kosmos.runTest {
            val values by collectLastValue(underTest.isKeyguardOccluded)
            setSceneTransition(Transition(Scenes.Gone, Scenes.Lockscreen))
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope = testScope,
                throughTransitionState = TransitionState.RUNNING,
            )

            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true)
            assertThat(values).isFalse()
        }
}
