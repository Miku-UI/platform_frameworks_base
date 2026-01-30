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

package com.android.systemui.keyguard.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.keyguard.data.repository.FakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState.DREAMING
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class KeyguardTransitionAnimationFlowTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private lateinit var animationFlow: KeyguardTransitionAnimationFlow
    private lateinit var repository: FakeKeyguardTransitionRepository

    private lateinit var underTest: KeyguardTransitionAnimationFlow.FlowBuilder

    @Before
    fun setUp() {
        animationFlow = kosmos.keyguardTransitionAnimationFlow
        repository = kosmos.fakeKeyguardTransitionRepository
        underTest =
            animationFlow
                .setup(
                    duration = 1000.milliseconds,
                    edge = Edge.create(from = LOCKSCREEN, to = DREAMING),
                )
                .setupWithoutSceneContainer(edge = Edge.create(from = LOCKSCREEN, to = DREAMING))
    }

    @Test
    fun zeroDurationThrowsException() =
        kosmos.runTest {
            assertThrows(IllegalArgumentException::class.java) {
                val flow = underTest.sharedFlow(duration = 0.milliseconds, onStep = { it })
            }
        }

    @Test
    fun startTimePlusDurationGreaterThanTransitionDurationThrowsException() =
        kosmos.runTest {
            assertThrows(IllegalArgumentException::class.java) {
                val flow =
                    underTest.sharedFlow(
                        startTime = 300.milliseconds,
                        duration = 800.milliseconds,
                        onStep = { it },
                    )
            }
        }

    @Test
    fun onFinishRunsWhenSpecified() =
        kosmos.runTest {
            val flow =
                underTest.sharedFlow(
                    duration = 100.milliseconds,
                    onStep = { it },
                    onFinish = { 10f },
                )
            var animationValues = collectLastValue(flow)
            runCurrent()

            repository.sendTransitionStep(step(1f, TransitionState.FINISHED), validateStep = false)
            assertThat(animationValues()).isEqualTo(10f)
        }

    @Test
    @DisableSceneContainer // CANCELED steps are filtered out when the scene framework is enabled.
    fun onCancelRunsWhenSpecified() =
        kosmos.runTest {
            val flow =
                underTest.sharedFlow(
                    duration = 100.milliseconds,
                    onStep = { it },
                    onCancel = { 100f },
                )
            var animationValues = collectLastValue(flow)
            runCurrent()

            repository.sendTransitionStep(step(0.5f, TransitionState.CANCELED))
            assertThat(animationValues()).isEqualTo(100f)
        }

    @Test
    fun onStepReturnsNullEmitsNothing() =
        kosmos.runTest {
            val flow = underTest.sharedFlow(duration = 100.milliseconds, onStep = { null })
            var animationValues = collectLastValue(flow)
            runCurrent()

            repository.sendTransitionStep(step(0.5f, TransitionState.RUNNING))
            assertThat(animationValues()).isNull()
        }

    @Test
    fun usesStartTime() =
        kosmos.runTest {
            val flow =
                underTest.sharedFlow(
                    startTime = 500.milliseconds,
                    duration = 500.milliseconds,
                    onStep = { it },
                )
            var animationValues = collectLastValue(flow)
            runCurrent()

            repository.sendTransitionStep(step(0f, TransitionState.STARTED))
            assertThat(animationValues()).isEqualTo(0f)

            // Should not emit a value
            repository.sendTransitionStep(step(0.1f, TransitionState.RUNNING))

            repository.sendTransitionStep(step(0.5f, TransitionState.RUNNING))
            assertFloat(animationValues(), 0f)
            repository.sendTransitionStep(step(0.6f, TransitionState.RUNNING))
            assertFloat(animationValues(), 0.2f)
            repository.sendTransitionStep(step(0.8f, TransitionState.RUNNING))
            assertFloat(animationValues(), 0.6f)
            repository.sendTransitionStep(step(1f, TransitionState.RUNNING))
            assertFloat(animationValues(), 1f)
        }

    @Test
    fun usesInterpolator() =
        kosmos.runTest {
            val flow =
                underTest.sharedFlow(
                    duration = 1000.milliseconds,
                    interpolator = EMPHASIZED_ACCELERATE,
                    onStep = { it },
                )
            var animationValues = collectLastValue(flow)
            runCurrent()

            repository.sendTransitionStep(step(0f, TransitionState.STARTED))
            assertFloat(animationValues(), EMPHASIZED_ACCELERATE.getInterpolation(0f))
            repository.sendTransitionStep(step(0.5f, TransitionState.RUNNING))
            assertFloat(animationValues(), EMPHASIZED_ACCELERATE.getInterpolation(0.5f))
            repository.sendTransitionStep(step(0.6f, TransitionState.RUNNING))
            assertFloat(animationValues(), EMPHASIZED_ACCELERATE.getInterpolation(0.6f))
            repository.sendTransitionStep(step(0.8f, TransitionState.RUNNING))
            assertFloat(animationValues(), EMPHASIZED_ACCELERATE.getInterpolation(0.8f))
            repository.sendTransitionStep(step(1f, TransitionState.RUNNING))
            assertFloat(animationValues(), EMPHASIZED_ACCELERATE.getInterpolation(1f))
        }

    @Test
    fun usesOnStepToDoubleValue() =
        kosmos.runTest {
            val flow = underTest.sharedFlow(duration = 1000.milliseconds, onStep = { it * 2 })
            val animationValues by collectLastValue(flow)
            runCurrent()

            repository.sendTransitionStep(step(0f, TransitionState.STARTED))
            assertFloat(animationValues, 0f)
            repository.sendTransitionStep(step(0.3f, TransitionState.RUNNING))
            assertFloat(animationValues, 0.6f)
            repository.sendTransitionStep(step(0.6f, TransitionState.RUNNING))
            assertFloat(animationValues, 1.2f)
            repository.sendTransitionStep(step(0.8f, TransitionState.RUNNING))
            assertFloat(animationValues, 1.6f)
            repository.sendTransitionStep(step(1f, TransitionState.RUNNING))
            assertFloat(animationValues, 2f)
        }

    @Test
    fun usesOnStepToDoubleValueWithState() =
        kosmos.runTest {
            val flow =
                underTest.sharedFlowWithState(duration = 1000.milliseconds, onStep = { it * 2 })
            val animationValues by collectLastValue(flow)
            runCurrent()

            repository.sendTransitionStep(step(0f, TransitionState.STARTED))
            assertThat(animationValues)
                .isEqualTo(
                    StateToValue(
                        from = LOCKSCREEN,
                        to = DREAMING,
                        transitionState = TransitionState.STARTED,
                        value = 0f,
                    )
                )
            repository.sendTransitionStep(step(0.3f, TransitionState.RUNNING))
            assertThat(animationValues)
                .isEqualTo(
                    StateToValue(
                        from = LOCKSCREEN,
                        to = DREAMING,
                        transitionState = TransitionState.RUNNING,
                        value = 0.6f,
                    )
                )
            repository.sendTransitionStep(step(0.6f, TransitionState.RUNNING))
            assertThat(animationValues)
                .isEqualTo(
                    StateToValue(
                        from = LOCKSCREEN,
                        to = DREAMING,
                        transitionState = TransitionState.RUNNING,
                        value = 1.2f,
                    )
                )
            repository.sendTransitionStep(step(0.8f, TransitionState.RUNNING))
            assertThat(animationValues)
                .isEqualTo(
                    StateToValue(
                        from = LOCKSCREEN,
                        to = DREAMING,
                        transitionState = TransitionState.RUNNING,
                        value = 1.6f,
                    )
                )
            repository.sendTransitionStep(step(1f, TransitionState.RUNNING))
            assertThat(animationValues)
                .isEqualTo(
                    StateToValue(
                        from = LOCKSCREEN,
                        to = DREAMING,
                        transitionState = TransitionState.RUNNING,
                        value = 2f,
                    )
                )
            repository.sendTransitionStep(step(1f, TransitionState.FINISHED))
            assertThat(animationValues)
                .isEqualTo(
                    StateToValue(
                        from = LOCKSCREEN,
                        to = DREAMING,
                        transitionState = TransitionState.FINISHED,
                        value = null,
                    )
                )
        }

    @Test
    fun sameFloatValueWithTheSameTransitionStateDoesNotEmitTwice() =
        kosmos.runTest {
            val flow = underTest.sharedFlow(duration = 1000.milliseconds, onStep = { it })
            val values by collectValues(flow)
            runCurrent()

            repository.sendTransitionStep(step(0.3f, TransitionState.RUNNING))
            repository.sendTransitionStep(step(0.3f, TransitionState.RUNNING))

            assertThat(values.size).isEqualTo(1)
            assertThat(values[0]).isEqualTo(0.3f)
        }

    @Test
    fun sameFloatValueWithADifferentTransitionStateDoesEmitTwice() =
        kosmos.runTest {
            val flow = underTest.sharedFlow(duration = 1000.milliseconds, onStep = { it })
            val values by collectValues(flow)
            runCurrent()

            repository.sendTransitionStep(step(0.3f, TransitionState.STARTED))
            repository.sendTransitionStep(step(0.3f, TransitionState.RUNNING))

            assertThat(values.size).isEqualTo(2)
            assertThat(values[0]).isEqualTo(0.3f)
            assertThat(values[0]).isEqualTo(0.3f)
        }

    @Test
    fun sharedFlowWithShadeExpanded() =
        kosmos.runTest {
            val flow =
                underTest.sharedFlowWithShade(
                    duration = 1000.milliseconds,
                    onStep = { step, isShadeExpanded -> if (isShadeExpanded) 0f else 1f },
                )
            val values by collectValues(flow)
            shadeTestUtil.setQsExpansion(1f)

            repository.sendTransitionStep(step(0.0f, TransitionState.STARTED))
            repository.sendTransitionStep(step(0.3f, TransitionState.RUNNING))

            assertThat(values.size).isEqualTo(2)
            assertThat(values[0]).isEqualTo(0f)
            assertThat(values[0]).isEqualTo(0f)
        }

    @Test
    fun sharedFlowWithShadeNotExpanded() =
        kosmos.runTest {
            val flow =
                underTest.sharedFlowWithShade(
                    duration = 1000.milliseconds,
                    onStep = { step, isShadeExpanded -> if (isShadeExpanded) 0f else 1f },
                )
            val values by collectValues(flow)
            shadeTestUtil.setQsExpansion(0f)

            repository.sendTransitionStep(step(0.0f, TransitionState.STARTED))
            repository.sendTransitionStep(step(0.3f, TransitionState.RUNNING))

            assertThat(values.size).isEqualTo(2)
            assertThat(values[0]).isEqualTo(1f)
            assertThat(values[0]).isEqualTo(1f)
        }

    @Test
    @DisableSceneContainer // CANCELED steps are filtered out when the scene framework is enabled.
    fun sharedFlowWithShadeExpanded_onCancelRunsWhenSpecified() =
        kosmos.runTest {
            val flow =
                underTest.sharedFlowWithShade(
                    duration = 100.milliseconds,
                    onStep = { step, _ -> step },
                    onCancel = { isShadeExpanded -> if (isShadeExpanded) 100f else 200f },
                )
            val animationValues by collectLastValue(flow)
            shadeTestUtil.setQsExpansion(1f)

            repository.sendTransitionStep(step(0.0f, TransitionState.STARTED))
            repository.sendTransitionStep(step(0.5f, TransitionState.CANCELED))
            assertThat(animationValues).isEqualTo(100f)
        }

    @Test
    @DisableSceneContainer // CANCELED steps are filtered out when the scene framework is enabled.
    fun sharedFlowWithShadeNotExpanded_onCancelRunsWhenSpecified() =
        kosmos.runTest {
            val flow =
                underTest.sharedFlowWithShade(
                    duration = 100.milliseconds,
                    onStep = { step, _ -> step },
                    onCancel = { isShadeExpanded -> if (isShadeExpanded) 100f else 200f },
                )
            val animationValues by collectLastValue(flow)
            shadeTestUtil.setQsExpansion(0f)

            repository.sendTransitionStep(step(0.0f, TransitionState.STARTED))
            repository.sendTransitionStep(step(0.5f, TransitionState.CANCELED))
            assertThat(animationValues).isEqualTo(200f)
        }

    private fun assertFloat(actual: Float?, expected: Float) {
        assertThat(actual!!).isWithin(0.01f).of(expected)
    }

    private fun step(
        value: Float,
        state: TransitionState = TransitionState.RUNNING,
    ): TransitionStep {
        return TransitionStep(
            from = LOCKSCREEN,
            to = DREAMING,
            value = value,
            transitionState = state,
            ownerName = "GoneToDreamingTransitionViewModelTest",
        )
    }
}
