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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectValues
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
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DozingToDreamingTransitionViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val underTest by lazy { kosmos.dozingToDreamingViewModel }

    @Before
    fun setUp() {
        kosmos.sceneContainerRepository.setTransitionState(
            MutableStateFlow<ObservableTransitionState>(
                ObservableTransitionState.Idle(Scenes.Lockscreen)
            )
        )
        kosmos.setupSceneTransition(from = Scenes.Lockscreen, to = Scenes.Dream)
    }

    @Test
    fun deviceEntryParentViewDisappear() =
        testScope.runTest {
            val values by collectValues(underTest.deviceEntryParentViewAlpha)
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0f),
                    step(0.1f),
                    step(0.2f),
                    step(0.3f),
                    step(1f),
                ),
                testScope,
            )

            values.forEach { assertThat(it).isEqualTo(0f) }
        }

    private fun step(value: Float, transitionState: TransitionState = RUNNING) =
        TransitionStep(
            from = KeyguardState.DOZING,
            to =
                if (SceneContainerFlag.isEnabled) {
                    KeyguardState.UNDEFINED
                } else {
                    KeyguardState.DREAMING
                },
            value = value,
            transitionState = transitionState,
            ownerName = "dozingToDreamingTransitionViewModelTest",
        )

    private fun Kosmos.setupSceneTransition(from: SceneKey, to: SceneKey) {
        if (SceneContainerFlag.isEnabled) {
            setSceneTransition(Transition(from = from, to = to))
        }
    }
}
