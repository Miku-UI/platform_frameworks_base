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

package com.android.systemui.shade.ui.viewmodel

import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.data.repository.HideOverlay
import com.android.systemui.scene.data.repository.Idle
import com.android.systemui.scene.data.repository.ShowOverlay
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.data.repository.setTransition
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class NotificationShadeWindowModelTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val keyguardTransitionRepository by lazy { kosmos.fakeKeyguardTransitionRepository }
    private val underTest: NotificationShadeWindowModel by lazy {
        kosmos.notificationShadeWindowModel
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Test
    @DisableSceneContainer
    fun transitionToOccludedByOCCLUDEDTransition() =
        testScope.runTest {
            val isKeyguardOccluded by collectLastValue(underTest.isKeyguardOccluded)
            assertThat(isKeyguardOccluded).isFalse()

            keyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.OCCLUDED,
                testScope,
            )
            assertThat(isKeyguardOccluded).isTrue()

            keyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.OCCLUDED,
                to = KeyguardState.GONE,
                testScope,
            )
            assertThat(isKeyguardOccluded).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun transitionToOccludedByDREAMINGTransition() =
        testScope.runTest {
            val isKeyguardOccluded by collectLastValue(underTest.isKeyguardOccluded)
            assertThat(isKeyguardOccluded).isFalse()

            keyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.DREAMING,
                testScope,
            )
            assertThat(isKeyguardOccluded).isTrue()

            keyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.DREAMING,
                to = KeyguardState.AOD,
                testScope,
            )
            assertThat(isKeyguardOccluded).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun transitionFromOccludedToDreamingTransitionRemainsTrue() =
        testScope.runTest {
            val isKeyguardOccluded by collectLastValue(underTest.isKeyguardOccluded)
            assertThat(isKeyguardOccluded).isFalse()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    TransitionStep(
                        from = KeyguardState.LOCKSCREEN,
                        to = KeyguardState.DREAMING,
                        value = 0f,
                        transitionState = TransitionState.STARTED,
                    ),
                    TransitionStep(
                        from = KeyguardState.LOCKSCREEN,
                        to = KeyguardState.DREAMING,
                        value = 0.5f,
                        transitionState = TransitionState.RUNNING,
                    ),
                ),
                testScope,
            )
            assertThat(isKeyguardOccluded).isFalse()

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.DREAMING,
                    value = 1f,
                    transitionState = TransitionState.FINISHED,
                )
            )
            assertThat(isKeyguardOccluded).isTrue()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    TransitionStep(
                        from = KeyguardState.DREAMING,
                        to = KeyguardState.OCCLUDED,
                        value = 0f,
                        transitionState = TransitionState.STARTED,
                    ),
                    TransitionStep(
                        from = KeyguardState.DREAMING,
                        to = KeyguardState.OCCLUDED,
                        value = 0.5f,
                        transitionState = TransitionState.RUNNING,
                    ),
                ),
                testScope,
            )
            assertThat(isKeyguardOccluded).isTrue()

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.DREAMING,
                    to = KeyguardState.OCCLUDED,
                    value = 1f,
                    transitionState = TransitionState.FINISHED,
                )
            )
            assertThat(isKeyguardOccluded).isTrue()
        }

    @Test
    fun isOnOrGoingToDream_whenTransitioningToDreaming_isTrue() =
        kosmos.runTest {
            val isOnOrGoingToDream by collectLastValue(underTest.isOnOrGoingToDream)
            assertThat(isOnOrGoingToDream).isFalse()

            setTransition(
                sceneTransition =
                    Transition(
                        from = Scenes.Lockscreen,
                        to = Scenes.Dream,
                        progress = flowOf(0.5f),
                    ),
                stateTransition =
                    TransitionStep(
                        from = KeyguardState.LOCKSCREEN,
                        to = KeyguardState.DREAMING,
                        value = 0.5f,
                        transitionState = TransitionState.RUNNING,
                    ),
            )

            assertThat(isOnOrGoingToDream).isTrue()
        }

    @Test
    fun isOnOrGoingToDream_whenTransitionToDreamingFinished_isTrue() =
        kosmos.runTest {
            val isOnOrGoingToDream by collectLastValue(underTest.isOnOrGoingToDream)
            assertThat(isOnOrGoingToDream).isFalse()

            setTransition(
                sceneTransition = Idle(Scenes.Dream),
                stateTransition =
                    TransitionStep(from = KeyguardState.LOCKSCREEN, to = KeyguardState.DREAMING),
            )

            assertThat(isOnOrGoingToDream).isTrue()
        }

    @Test
    fun isOnOrGoingToDream_whenTransitioningAwayFromDreaming_isFalse() =
        kosmos.runTest {
            val isOnOrGoingToDream by collectLastValue(underTest.isOnOrGoingToDream)

            setTransition(
                sceneTransition = Idle(Scenes.Dream),
                stateTransition =
                    TransitionStep(from = KeyguardState.LOCKSCREEN, to = KeyguardState.DREAMING),
            )

            assertThat(isOnOrGoingToDream).isTrue()

            setTransition(
                sceneTransition =
                    Transition(
                        from = Scenes.Dream,
                        to = Scenes.Lockscreen,
                        progress = flowOf(0.5f),
                    ),
                stateTransition =
                    TransitionStep(
                        from = KeyguardState.DREAMING,
                        to = KeyguardState.LOCKSCREEN,
                        value = 0.5f,
                        transitionState = TransitionState.RUNNING,
                    ),
            )

            assertThat(isOnOrGoingToDream).isFalse()
        }

    @Test
    fun isOnOrGoingToDream_whenFinishedTransitionAwayFromDreaming_isFalse() =
        kosmos.runTest {
            val isOnOrGoingToDream by collectLastValue(underTest.isOnOrGoingToDream)

            setTransition(
                sceneTransition = Idle(Scenes.Dream),
                stateTransition =
                    TransitionStep(from = KeyguardState.LOCKSCREEN, to = KeyguardState.DREAMING),
            )

            assertThat(isOnOrGoingToDream).isTrue()

            setTransition(
                sceneTransition = Idle(Scenes.Lockscreen),
                stateTransition =
                    TransitionStep(from = KeyguardState.DREAMING, to = KeyguardState.LOCKSCREEN),
            )
            assertThat(isOnOrGoingToDream).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isAnimatingGoneToAod_withSceneContainer() =
        kosmos.runTest {
            val isAnimatingGoneToAod by collectLastValue(underTest.isAnimatingGoneToAod)
            assertThat(isAnimatingGoneToAod).isFalse()

            setSceneTransition(
                Transition(from = Scenes.Gone, to = Scenes.Lockscreen, progress = flowOf(0.5f))
            )

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.UNDEFINED,
                    to = KeyguardState.AOD,
                    value = 0.5f,
                    transitionState = TransitionState.RUNNING,
                )
            )

            assertThat(isAnimatingGoneToAod).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun isAnimatingGoneToAod_withoutSceneContainer() =
        kosmos.runTest {
            val isAnimatingGoneToAod by collectLastValue(underTest.isAnimatingGoneToAod)
            assertThat(isAnimatingGoneToAod).isFalse()

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                    value = 0.5f,
                    transitionState = TransitionState.RUNNING,
                )
            )
            assertThat(isAnimatingGoneToAod).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun isBouncerShowing_withSceneContainer() =
        kosmos.runTest {
            val isBouncerShowing by collectLastValue(underTest.isBouncerShowing)
            assertThat(isBouncerShowing).isFalse()

            setSceneTransition(Idle(currentScene = Scenes.Lockscreen, currentOverlays = setOf(Overlays.Bouncer)))
            assertThat(isBouncerShowing).isTrue()

            setSceneTransition(Idle(currentScene = Scenes.Lockscreen, currentOverlays = emptySet()))
            assertThat(isBouncerShowing).isFalse()

            setSceneTransition(
                ShowOverlay(
                    overlay = Overlays.Bouncer,
                    fromScene = Scenes.Lockscreen,
                    progress = flowOf(0.5f)
                )
            )
            assertThat(isBouncerShowing).isTrue()

            setSceneTransition(
                HideOverlay(
                    overlay = Overlays.Bouncer,
                    toScene = Scenes.Lockscreen,
                    progress = flowOf(0.5f)
                )
            )
            assertThat(isBouncerShowing).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun withSceneContainer_doesBouncerRequireIme_providesTheCorrectState() =
        testScope.runTest {
            val bouncerRequiresIme by collectLastValue(underTest.doesBouncerRequireIme)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )
            runCurrent()
            assertThat(bouncerRequiresIme).isFalse()

            // change auth method
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Password
            )
            runCurrent()
            assertThat(bouncerRequiresIme).isTrue()
        }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }
}
