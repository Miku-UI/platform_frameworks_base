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

package com.android.systemui.keyguard.ui.viewmodel

import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.FakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionState.RUNNING
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class AlternateBouncerToDreamingTransitionViewModelTest(flags: FlagsParameterization) :
    SysuiTestCase() {
    private val kosmos = testKosmos()
    private lateinit var keyguardTransitionRepository: FakeKeyguardTransitionRepository
    private lateinit var underTest: AlternateBouncerToDreamingTransitionViewModel

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
        keyguardTransitionRepository = kosmos.fakeKeyguardTransitionRepository
        underTest = kosmos.alternateBouncerToDreamingTransitionViewModel
    }

    @Test
    fun deviceEntryParentViewAlpha() =
        kosmos.runTest {
            val values by collectValues(underTest.deviceEntryParentViewAlpha)
            startLockscreenToDreamSceneTransition()

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

    private fun step(value: Float, state: TransitionState = RUNNING): TransitionStep {
        return TransitionStep(
            from = KeyguardState.ALTERNATE_BOUNCER,
            to = KeyguardState.DREAMING,
            value = value,
            transitionState = state,
            ownerName = "AlternateBounceToDreamingTransitionViewModelTest",
        )
    }

    private fun Kosmos.startLockscreenToDreamSceneTransition() {
        if (SceneContainerFlag.isEnabled) {
            setSceneTransition(
                Transition(from = Scenes.Lockscreen, to = Scenes.Dream, progress = flowOf(0f))
            )
        }
    }
}
