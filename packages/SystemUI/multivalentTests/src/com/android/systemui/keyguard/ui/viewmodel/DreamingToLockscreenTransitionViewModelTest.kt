/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.FakeFingerprintPropertyRepository
import com.android.systemui.biometrics.data.repository.fingerprintPropertyRepository
import com.android.systemui.biometrics.shared.model.FingerprintSensorType
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.keyguard.data.repository.FakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState.DREAMING
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.shared.model.KeyguardState.UNDEFINED
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
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamingToLockscreenTransitionViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private lateinit var keyguardTransitionRepository: FakeKeyguardTransitionRepository
    private lateinit var fingerprintPropertyRepository: FakeFingerprintPropertyRepository
    private lateinit var underTest: DreamingToLockscreenTransitionViewModel

    @Before
    fun setUp() {
        keyguardTransitionRepository = kosmos.fakeKeyguardTransitionRepository
        fingerprintPropertyRepository = kosmos.fingerprintPropertyRepository
        underTest = kosmos.dreamingToLockscreenTransitionViewModel
    }

    @Test
    fun shortcutsAlpha_bothShortcutsReceiveLastValue() =
        kosmos.runTest {
            val valuesLeft by collectValues(underTest.shortcutsAlpha)
            val valuesRight by collectValues(underTest.shortcutsAlpha)
            startDreamToLockscreenSceneTransition()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0.3f),
                    step(0.5f),
                    step(0.6f),
                    step(0.8f),
                    step(1f),
                ),
                testScope,
            )

            assertThat(valuesLeft.last()).isEqualTo(1f)
            assertThat(valuesRight.last()).isEqualTo(1f)
        }

    @Test
    fun dreamOverlayTranslationY() =
        kosmos.runTest {
            val pixels = 100
            val values by collectValues(underTest.dreamOverlayTranslationY(pixels))
            startDreamToLockscreenSceneTransition()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0f),
                    step(0.3f),
                    step(0.5f),
                    step(0.6f),
                    step(0.8f),
                    step(1f),
                ),
                testScope,
            )

            assertThat(values.size).isEqualTo(7)
            values.forEach { assertThat(it).isIn(Range.closed(0f, 100f)) }
        }

    @Test
    fun dreamOverlayFadeOut() =
        kosmos.runTest {
            val values by collectValues(underTest.dreamOverlayAlpha)
            startDreamToLockscreenSceneTransition()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    // Should start running here...
                    step(0f, TransitionState.STARTED),
                    step(0f),
                    step(0.1f),
                    step(0.5f),
                    // ...up to here
                    step(1f),
                ),
                testScope,
            )

            assertThat(values.size).isEqualTo(4)
            values.forEach { assertThat(it).isIn(Range.closed(0f, 1f)) }
        }

    @Test
    fun lockscreenFadeIn() =
        kosmos.runTest {
            val values by collectValues(underTest.lockscreenAlpha)
            startDreamToLockscreenSceneTransition()

            keyguardTransitionRepository.sendTransitionSteps(
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

            assertThat(values.size).isEqualTo(4)
            values.forEach { assertThat(it).isIn(Range.closed(0f, 1f)) }
        }

    @Test
    fun deviceEntryParentViewFadeIn() =
        kosmos.runTest {
            val values by collectValues(underTest.deviceEntryParentViewAlpha)
            startDreamToLockscreenSceneTransition()

            keyguardTransitionRepository.sendTransitionSteps(
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

            assertThat(values.size).isEqualTo(4)
            values.forEach { assertThat(it).isIn(Range.closed(0f, 1f)) }
        }

    @Test
    fun deviceEntryBackgroundViewAppear() =
        kosmos.runTest {
            fingerprintPropertyRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.UDFPS_OPTICAL,
                sensorLocations = emptyMap(),
            )
            val values by collectValues(underTest.deviceEntryBackgroundViewAlpha)
            startDreamToLockscreenSceneTransition()

            keyguardTransitionRepository.sendTransitionSteps(
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

            values.forEach { assertThat(it).isEqualTo(1f) }
        }

    @Test
    fun lockscreenTranslationY() =
        kosmos.runTest {
            val pixels = 100
            val values by collectValues(underTest.lockscreenTranslationY(pixels))
            startDreamToLockscreenSceneTransition()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0f),
                    step(0.3f),
                    step(0.5f),
                    step(1f),
                ),
                testScope,
            )

            assertThat(values.size).isEqualTo(5)
            values.forEach { assertThat(it).isIn(Range.closed(-100f, 0f)) }
        }

    private fun Kosmos.startDreamToLockscreenSceneTransition() {
        if (SceneContainerFlag.isEnabled) {
            setSceneTransition(
                Transition(from = Scenes.Dream, to = Scenes.Lockscreen, progress = flowOf(0f))
            )
        }
    }

    private fun step(value: Float, state: TransitionState = RUNNING): TransitionStep {
        return TransitionStep(
            from =
                if (SceneContainerFlag.isEnabled) {
                    UNDEFINED
                } else {
                    DREAMING
                },
            to = LOCKSCREEN,
            value = value,
            transitionState = state,
            ownerName = "DreamingToLockscreenTransitionViewModelTest",
        )
    }
}
