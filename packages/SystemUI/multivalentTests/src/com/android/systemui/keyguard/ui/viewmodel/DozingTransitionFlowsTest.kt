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
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionState.RUNNING
import com.android.systemui.keyguard.shared.model.TransitionState.STARTED
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@DisableSceneContainer
@SmallTest
@RunWith(AndroidJUnit4::class)
class DozingTransitionFlowsTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val underTest by lazy { kosmos.dozingTransitionFlows }

    @Test
    fun lockscreenAlpha_dozing_dozePulsing() =
        testScope.runTest {
            val lockscreenAlpha by collectLastValue(underTest.lockscreenAlpha(null))
            // Last startedKeyguardTransitionStep is to DOZING
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToDozingStep(0f, STARTED)
            )
            runCurrent()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING)
            )
            runCurrent()
            assertThat(lockscreenAlpha).isEqualTo(1f)
        }

    @Test
    fun lockscreenAlpha_dozing_dozePulsingAuthUI() =
        testScope.runTest {
            val lockscreenAlpha by collectLastValue(underTest.lockscreenAlpha(null))
            // Last startedKeyguardTransitionStep is to DOZING
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToDozingStep(0f, STARTED)
            )
            runCurrent()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING_AUTH_UI)
            )
            runCurrent()
            assertThat(lockscreenAlpha).isEqualTo(1f)
        }

    @Test
    fun lockscreenAlpha_keyguardStateDozing_dozeTransitionModelDoze() =
        testScope.runTest {
            val lockscreenAlpha by collectLastValue(underTest.lockscreenAlpha(null))
            // Last startedKeyguardTransitionStep is to DOZING
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToDozingStep(0f, STARTED)
            )
            runCurrent()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE)
            )
            runCurrent()
            assertThat(lockscreenAlpha).isEqualTo(0f)
        }

    @Test
    fun lockscreenAlpha_keyguardStateDozing_dozeTransitionModelDozePulsingWithoutUI() =
        testScope.runTest {
            val lockscreenAlpha by collectLastValue(underTest.lockscreenAlpha(null))
            // Last startedKeyguardTransitionStep is to DOZING
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToDozingStep(0f, STARTED)
            )
            runCurrent()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING_WITHOUT_UI)
            )
            runCurrent()
            assertThat(lockscreenAlpha).isEqualTo(0f)
        }

    @Test
    fun lockscreenAlpha_keyguardStateDozing_dozeTransitionModelUnhandledStates() =
        testScope.runTest {
            val lockscreenAlpha by collectLastValue(underTest.lockscreenAlpha(null))
            // Last startedKeyguardTransitionStep is to DOZING
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToDozingStep(0f, STARTED)
            )
            runCurrent()
            val unhandledStates =
                listOf(
                    DozeStateModel.DOZE_AOD_PAUSED,
                    DozeStateModel.DOZE_AOD_PAUSING,
                    DozeStateModel.UNINITIALIZED,
                    DozeStateModel.INITIALIZED,
                    DozeStateModel.FINISH,
                    DozeStateModel.DOZE_PULSE_DONE,
                )
            for (unhandledState in unhandledStates) {
                kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                    DozeTransitionModel(to = unhandledState)
                )
                runCurrent()
                assertThat(lockscreenAlpha).isNull()
            }
        }

    @Test
    fun lockscreenAlpha_notKeyguardStateDozing_nothingEmits() =
        testScope.runTest {
            val lockscreenAlpha by collectLastValue(underTest.lockscreenAlpha(null))
            // Last startedKeyguardTransitionStep is to AOD (not DOZING)
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToAodStep(0f, STARTED)
            )
            runCurrent()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING)
            )
            runCurrent()
            assertThat(lockscreenAlpha).isNull()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING_BRIGHT)
            )
            runCurrent()
            assertThat(lockscreenAlpha).isNull()
        }

    @Test
    fun nonAuthUIAlpha_dozing_dozePulsing() =
        testScope.runTest {
            val nonAuthUIAlpha by collectLastValue(underTest.nonAuthUIAlpha(null))
            // Last startedKeyguardTransitionStep is to DOZING
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToDozingStep(0f, STARTED)
            )
            runCurrent()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING)
            )
            runCurrent()
            assertThat(nonAuthUIAlpha).isEqualTo(1f)
        }

    @Test
    fun nonAuthUIAlpha_dozing_dozePulsingAuthUI() =
        testScope.runTest {
            val nonAuthUIAlpha by collectLastValue(underTest.nonAuthUIAlpha(null))
            // Last startedKeyguardTransitionStep is to DOZING
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToDozingStep(0f, STARTED)
            )
            runCurrent()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING_AUTH_UI)
            )
            runCurrent()
            assertThat(nonAuthUIAlpha).isEqualTo(0f)
        }

    @Test
    fun nonAuthUIAlpha_notKeyguardStateDozing_nothingEmits() =
        testScope.runTest {
            val nonAuthUIAlpha by collectLastValue(underTest.nonAuthUIAlpha(null))
            // Last startedKeyguardTransitionStep is to AOD (not DOZING)
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToAodStep(0f, STARTED)
            )
            runCurrent()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING)
            )
            runCurrent()
            assertThat(nonAuthUIAlpha).isNull()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING_BRIGHT)
            )
            runCurrent()
            assertThat(nonAuthUIAlpha).isNull()
        }

    @Test
    fun wasHiddenAuthUIShowingWhileDozing_falseFromLockscreenAlpha() =
        testScope.runTest {
            val wasHiddenAuthUIShowingWhileDozing by
                collectLastValue(underTest.wasHiddenAuthUIShowingWhileDozing)
            val lockscreenAlpha by collectLastValue(underTest.lockscreenAlpha(null))

            // Last startedKeyguardTransitionStep is to DOZING
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToDozingStep(0f, STARTED)
            )
            runCurrent()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE)
            )
            runCurrent()

            // GIVEN lockscreen alpha is 0f
            assertThat(lockscreenAlpha).isEqualTo(0f)

            // THEN wasHiddenAuthUIShowingWhileDozing should be false
            assertThat(wasHiddenAuthUIShowingWhileDozing).isEqualTo(false)
        }

    @Test
    fun wasHiddenAuthUIShowingWhileDozing_falseFromNonAuthUIAlpha() =
        testScope.runTest {
            val wasHiddenAuthUIShowingWhileDozing by
                collectLastValue(underTest.wasHiddenAuthUIShowingWhileDozing)
            val nonAuthUIAlpha by collectLastValue(underTest.nonAuthUIAlpha(null))
            // Last startedKeyguardTransitionStep is to DOZING
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToDozingStep(0f, STARTED)
            )
            runCurrent()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING_AUTH_UI)
            )
            runCurrent()

            // GIVEN nonAuthUIAlpha alpha is 0f
            assertThat(nonAuthUIAlpha).isEqualTo(0f)

            // THEN wasHiddenAuthUIShowingWhileDozing should be false
            assertThat(wasHiddenAuthUIShowingWhileDozing).isEqualTo(false)
        }

    @Test
    fun wasHiddenAuthUIShowingWhileDozing_true() =
        testScope.runTest {
            val wasHiddenAuthUIShowingWhileDozing by
                collectLastValue(underTest.wasHiddenAuthUIShowingWhileDozing)
            val nonAuthUIAlpha by collectLastValue(underTest.nonAuthUIAlpha(null))
            val lockscreenAlpha by collectLastValue(underTest.lockscreenAlpha(null))
            // Last startedKeyguardTransitionStep is to DOZING
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                lockscreenToDozingStep(0f, STARTED)
            )
            runCurrent()
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING)
            )
            runCurrent()

            // GIVEN nonAuthUIAlpha and lockscreen alphas are 1f
            assertThat(nonAuthUIAlpha).isEqualTo(1f)
            assertThat(lockscreenAlpha).isEqualTo(1f)

            // THEN wasHiddenAuthUIShowingWhileDozing should be true
            assertThat(wasHiddenAuthUIShowingWhileDozing).isEqualTo(true)
        }

    private fun lockscreenToDozingStep(value: Float, transitionState: TransitionState = RUNNING) =
        TransitionStep(
            from = KeyguardState.LOCKSCREEN,
            to = KeyguardState.DOZING,
            value = value,
            transitionState = transitionState,
            ownerName = "dozingViewModelTest",
        )

    private fun lockscreenToAodStep(value: Float, transitionState: TransitionState = RUNNING) =
        TransitionStep(
            from = KeyguardState.LOCKSCREEN,
            to = KeyguardState.AOD,
            value = value,
            transitionState = transitionState,
            ownerName = "dozingViewModelTest",
        )
}
