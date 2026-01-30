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

package com.android.systemui.keyguard.ui.viewmodel

import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.Flags
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.statusbar.phone.ScrimState
import com.android.systemui.testKosmos
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class LockscreenToDreamingTransitionViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            fakeFeatureFlagsClassic.apply { set(Flags.FULL_SCREEN_USER_SWITCHER, false) }
        }
    private lateinit var underTest: LockscreenToDreamingTransitionViewModel

    // add to init block
    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        underTest = kosmos.lockscreenToDreamingTransitionViewModel
    }

    @Test
    fun lockscreenFadeOut() =
        kosmos.runTest {
            val values by collectValues(underTest.lockscreenAlpha)

            startLockscreenToDreamSceneTransition()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                steps =
                    listOf(
                        step(0f, TransitionState.STARTED), // Should start running here...
                        step(0f),
                        step(.1f),
                        step(.2f),
                        step(.3f), // ...up to here
                        step(1f),
                    ),
                testScope = testScope,
            )

            // Only five values should be present, since the dream overlay runs for a small
            // fraction of the overall animation time
            assertThat(values.size).isEqualTo(5)
            values.forEach { assertThat(it).isIn(Range.closed(0f, 1f)) }
        }

    @Test
    fun lockscreenFadeOut_shadeExpanded() =
        kosmos.runTest {
            val values by collectValues(underTest.lockscreenAlpha)
            shadeExpanded(true)
            startShadeToDreamSceneTransition()

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                steps =
                    listOf(
                        step(0f, TransitionState.STARTED),
                        step(0f),
                        step(.3f),
                        step(.5f),
                        step(1f),
                        step(1f, TransitionState.FINISHED),
                    ),
                testScope = testScope,
            )

            // Lockscreen is not shown during the whole transition.
            values.forEach { assertThat(it).isEqualTo(0f) }
        }

    @Test
    fun shortcutsFadeOut() =
        kosmos.runTest {
            val values by collectValues(underTest.shortcutsAlpha)

            startLockscreenToDreamSceneTransition()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                steps =
                    listOf(
                        step(0f, TransitionState.STARTED), // Should start running here...
                        step(0f),
                        step(.1f),
                        step(.2f),
                        step(.3f), // ...up to here
                        step(1f),
                    ),
                testScope = testScope,
            )

            // Only five values should be present, since the dream overlay runs for a small
            // fraction of the overall animation time
            assertThat(values.size).isEqualTo(5)
            values.forEach { assertThat(it).isIn(Range.closed(0f, 1f)) }
        }

    @Test
    fun shortcutsFadeOut_shadeExpanded() =
        kosmos.runTest {
            val values by collectValues(underTest.shortcutsAlpha)
            shadeExpanded(true)
            startShadeToDreamSceneTransition()

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                steps =
                    listOf(
                        step(0f, TransitionState.STARTED),
                        step(0f),
                        step(.3f),
                        step(.5f),
                        step(1f),
                        step(1f, TransitionState.FINISHED),
                    ),
                testScope = testScope,
            )

            // Shortcuts are not shown during the whole transition.
            values.forEach { assertThat(it).isEqualTo(0f) }
        }

    @Test
    fun lockscreenTranslationY() =
        kosmos.runTest {
            val pixels = 100
            val values by collectValues(underTest.lockscreenTranslationY(pixels))

            startLockscreenToDreamSceneTransition()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                steps =
                    listOf(
                        step(0f, TransitionState.STARTED), // Should start running here...
                        step(0f),
                        step(.3f),
                        step(.5f),
                        step(1f),
                        step(1f, TransitionState.FINISHED), // Final reset event on FINISHED
                    ),
                testScope = testScope,
            )

            assertThat(values.size).isEqualTo(6)
            values.forEach { assertThat(it).isIn(Range.closed(0f, 100f)) }
            // Validate finished value
            assertThat(values[5]).isEqualTo(0f)
        }

    @Test
    fun deviceEntryParentViewAlpha_shadeExpanded() =
        kosmos.runTest {
            val values by collectValues(underTest.deviceEntryParentViewAlpha)
            shadeExpanded(true)
            startShadeToDreamSceneTransition()

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                steps =
                    listOf(
                        step(0f, TransitionState.STARTED),
                        step(0f),
                        step(.3f),
                        step(.5f),
                        step(1f),
                        step(1f, TransitionState.FINISHED),
                    ),
                testScope = testScope,
            )

            // immediately 0f
            values.forEach { assertThat(it).isEqualTo(0f) }
        }

    @Test
    fun deviceEntryParentViewAlpha_shadeNotExpanded() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.deviceEntryParentViewAlpha)
            shadeExpanded(false)
            startLockscreenToDreamSceneTransition()

            // fade out
            fakeKeyguardTransitionRepository.sendTransitionStep(step(0f, TransitionState.STARTED))
            assertThat(actual).isEqualTo(1f)

            fakeKeyguardTransitionRepository.sendTransitionStep(step(.1f))
            assertThat(actual).isIn(Range.open(.1f, .9f))

            // alpha is 1f before the full transition starts ending
            fakeKeyguardTransitionRepository.sendTransitionStep(step(0.8f))
            assertThat(actual).isEqualTo(0f)

            fakeKeyguardTransitionRepository.sendTransitionStep(step(1f, TransitionState.FINISHED))

            assertThat(actual).isEqualTo(0f)
        }

    @Test
    fun scrimAlpha() =
        kosmos.runTest {
            val value by collectLastValue(underTest.scrimAlpha)

            startLockscreenToDreamSceneTransition()
            for (step in listOf(0f, 0.2f, 0.5f, 0.8f, 1.0f)) {
                fakeKeyguardTransitionRepository.sendTransitionStep(step(step))
                assertThat(value?.behindAlpha)
                    .isEqualTo((1 - step) * ScrimState.KEYGUARD.behindAlpha)
            }
        }

    private fun Kosmos.startShadeToDreamSceneTransition() {
        if (SceneContainerFlag.isEnabled) {
            setSceneTransition(
                Transition(from = Scenes.Shade, to = Scenes.Dream, progress = flowOf(0f))
            )
        }
    }

    private fun Kosmos.startLockscreenToDreamSceneTransition() {
        if (SceneContainerFlag.isEnabled) {
            setSceneTransition(
                Transition(from = Scenes.Lockscreen, to = Scenes.Dream, progress = flowOf(0f))
            )
        }
    }

    private fun step(
        value: Float,
        state: TransitionState = TransitionState.RUNNING,
    ): TransitionStep {
        return TransitionStep(
            from = KeyguardState.LOCKSCREEN,
            to =
                if (SceneContainerFlag.isEnabled) {
                    KeyguardState.UNDEFINED
                } else {
                    KeyguardState.DREAMING
                },
            value = value,
            transitionState = state,
            ownerName = "LockscreenToDreamingTransitionViewModelTest",
        )
    }

    private fun Kosmos.shadeExpanded(expanded: Boolean) {
        if (expanded) {
            shadeTestUtil.setQsExpansion(1f)
        } else {
            fakeKeyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)
            shadeTestUtil.setQsExpansion(0f)
            shadeTestUtil.setLockscreenShadeExpansion(0f)
        }
    }
}
