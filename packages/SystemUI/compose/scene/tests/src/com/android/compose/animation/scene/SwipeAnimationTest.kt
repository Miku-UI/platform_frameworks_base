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

package com.android.compose.animation.scene

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.SetFlagsRule
import androidx.compose.animation.SplineBasedFloatDecayAnimationSpec
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.mechanics.UserActionGestureFlag
import com.android.mechanics.DistanceGestureContext
import com.android.mechanics.ProvidedGestureContext
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.MotionSpec
import com.android.systemui.Flags.FLAG_STL_USER_ACTION_GESTURE
import com.google.common.truth.FloatSubject
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.runMonotonicClockTest
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
class SwipeAnimationTest(flags: FlagsParameterization) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(FLAG_STL_USER_ACTION_GESTURE)
        }

        const val FLOAT_EPSILON = 0.0001f

        fun FloatSubject.isWithinTolerance() = this.isWithin(FLOAT_EPSILON)
    }

    @get:Rule val setFlagsRule = SetFlagsRule().apply { setFlagsParameterization(flags) }

    @Test
    // Regression test for b/462102178.
    fun animateOffsetCalledOnce() = runMonotonicClockTest {
        val state = MutableSceneTransitionLayoutStateForTests(SceneA)
        val swipeAnimation =
            SwipeAnimation(
                    layoutState = state,
                    fromContent = SceneA,
                    toContent = SceneB,
                    isUpOrLeft = false,
                    requiresFullDistanceSwipe = false,
                    distance = { 100f },
                    gestureContext =
                        ProvidedGestureContext(dragOffset = 100f, direction = InputDirection.Max),
                    gestureTransformationSpec = { MotionSpec.InitiallyUndefined },
                    decayAnimationSpec =
                        SplineBasedFloatDecayAnimationSpec(Density(1f))
                            .generateDecayAnimationSpec(),
                    density = Density(1f),
                    velocityThreshold = 10.dp,
                )
                .apply {
                    val swipeAnimation = this
                    contentTransition =
                        object : TransitionState.Transition.ChangeScene(SceneA, SceneB) {
                            override val progress: Float = 0f
                            override val progressVelocity: Float = 0f
                            override val isInitiatedByUserInput: Boolean = true
                            override val isUserInputOngoing: Boolean = true
                            override val currentScene: SceneKey = SceneA

                            override suspend fun run() {
                                swipeAnimation.run()
                            }

                            override fun freezeAndAnimateToCurrentState() {
                                swipeAnimation.freezeAndAnimateToCurrentState()
                            }
                        }
                }

        launch(start = CoroutineStart.UNDISPATCHED) {
            state.startTransitionImmediately(this, swipeAnimation.contentTransition)
        }

        swipeAnimation.freezeAndAnimateToCurrentState()
        if (!swipeAnimation.isAnimatingOffset()) {
            swipeAnimation.animateOffset(initialVelocity = 0f, targetContent = SceneB)
        }
    }

    @Test
    @EnableFlags(UserActionGestureFlag.FLAG_NAME)
    fun userActionGesture_downOrRight_asDefined() = runSwipeAnimationTest {
        val underTest =
            createSwipeAnimation(isUpOrLeft = false, isReversed = false).also {
                it.runForTest()
                it.setDistanceAvailable()
            }

        assertThat(underTest.effectiveDragOffset).isWithinTolerance().of(0f)
        gestureContext.dragOffset = 10f
        assertThat(underTest.effectiveDragOffset).isWithinTolerance().of(10f)
    }

    @Test
    @EnableFlags(UserActionGestureFlag.FLAG_NAME)
    fun userActionGesture_downOrRight_reversed() = runSwipeAnimationTest {
        val underTest =
            createSwipeAnimation(isUpOrLeft = false, isReversed = true).also {
                it.runForTest()
                it.setDistanceAvailable()
            }

        assertThat(underTest.effectiveDragOffset).isWithinTolerance().of(0f)
        gestureContext.dragOffset = 10f
        assertThat(underTest.effectiveDragOffset).isWithinTolerance().of(10f)
    }

    @Test
    @EnableFlags(UserActionGestureFlag.FLAG_NAME)
    fun userActionGesture_upOrLeft_asDefined() = runSwipeAnimationTest {
        val underTest =
            createSwipeAnimation(isUpOrLeft = true).also {
                it.runForTest()
                it.setDistanceAvailable()
            }

        assertThat(underTest.effectiveDragOffset).isWithinTolerance().of(0f)
        gestureContext.dragOffset = -10f
        assertThat(underTest.effectiveDragOffset).isWithinTolerance().of(-10f)
    }

    @Test
    @EnableFlags(UserActionGestureFlag.FLAG_NAME)
    fun userActionGesture_upOrLeft_reversed() = runSwipeAnimationTest {
        val underTest =
            createSwipeAnimation(isUpOrLeft = true, isReversed = true).also {
                it.runForTest()
                it.setDistanceAvailable()
            }

        assertThat(underTest.effectiveDragOffset).isWithinTolerance().of(0f)
        gestureContext.dragOffset = -10f
        assertThat(underTest.effectiveDragOffset).isWithinTolerance().of(-10f)
    }

    private fun runSwipeAnimationTest(block: suspend TestSwipeAnimationScope.() -> Unit) {
        runMonotonicClockTest {
            val testScope = TestSwipeAnimationScope(this)
            try {
                testScope.block()
            } finally {
                testScope.onTearDown.fastForEach { it.invoke() }
            }
        }
    }

    private class TestSwipeAnimationScope(testScope: CoroutineScope) : CoroutineScope by testScope {
        var distance = TransitionState.DistanceUnspecified
        var gestureTransformationSpec = MotionSpec.InitiallyUndefined

        val directionChangeThreshold = 5f
        lateinit var gestureContext: DistanceGestureContext
            private set

        val density = Density(1f)

        fun SwipeAnimation<SceneKey>.setDistanceAvailable(
            absoluteDistance: Float = 100f,
            gestureSpec: MotionSpec = MotionSpec.Identity,
        ) {
            this@TestSwipeAnimationScope.distance =
                if (isUpOrLeft) -absoluteDistance else absoluteDistance
            this@TestSwipeAnimationScope.gestureTransformationSpec = gestureSpec
        }

        val onTearDown = mutableListOf<() -> Unit>()

        fun SwipeAnimation<SceneKey>.runForTest(): Job {
            val swipeAnimationJob = launch { run() }
            onTearDown += { swipeAnimationJob.cancel() }
            return swipeAnimationJob
        }

        fun createSwipeAnimation(
            isUpOrLeft: Boolean = false,
            isReversed: Boolean = false,
            requiresFullDistanceSwipe: Boolean = false,
            velocityThreshold: Dp = 10.dp,
        ): SwipeAnimation<SceneKey> {
            this.gestureContext =
                DistanceGestureContext(
                    initialDragOffset = 0f,
                    initialDirection = if (isUpOrLeft) InputDirection.Min else InputDirection.Max,
                    directionChangeSlop = directionChangeThreshold,
                )

            val fromContent = if (isReversed) SceneB else SceneA
            val toContent = if (isReversed) SceneA else SceneB

            return SwipeAnimation(
                    layoutState = MutableSceneTransitionLayoutStateForTests(fromContent),
                    fromContent = fromContent,
                    toContent = toContent,
                    isUpOrLeft = isUpOrLeft,
                    requiresFullDistanceSwipe = requiresFullDistanceSwipe,
                    distance = { distance },
                    gestureContext = gestureContext,
                    gestureTransformationSpec = { gestureTransformationSpec },
                    decayAnimationSpec =
                        SplineBasedFloatDecayAnimationSpec(density).generateDecayAnimationSpec(),
                    density = density,
                    velocityThreshold = velocityThreshold,
                )
                .apply {
                    contentTransition =
                        object : TransitionState.Transition.ChangeScene(SceneA, SceneB) {
                                override val progress: Float = 0f
                                override val progressVelocity: Float = 0f
                                override val isInitiatedByUserInput: Boolean = true
                                override val isUserInputOngoing: Boolean = true
                                override val currentScene: SceneKey = SceneA

                                override suspend fun run() {}

                                override fun freezeAndAnimateToCurrentState() {}
                            }
                            .also {
                                if (isReversed) {
                                    it.transformationSpec =
                                        TransformationSpecImpl(
                                            progressSpec = snap(),
                                            distance = null,
                                            intrinsicDirection = null,
                                            transformationMatchers = emptyList(),
                                            isReversed = true,
                                        )
                                }
                            }
                }
        }
    }
}
