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

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.os.PowerManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.service.dream.dreamManager
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_COMMUNAL_HUB
import com.android.systemui.Flags.FLAG_GLANCEABLE_HUB_V2
import com.android.systemui.Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR
import com.android.systemui.Flags.FLAG_SCENE_CONTAINER
import com.android.systemui.Flags.FLAG_WAKEFULNESS_FOR_ANIMATIONS
import com.android.systemui.Flags.glanceableHubV2
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.communal.data.repository.FakeCommunalSceneRepository
import com.android.systemui.communal.data.repository.communalSceneRepository
import com.android.systemui.communal.data.repository.fakeCommunalSceneRepository
import com.android.systemui.communal.domain.interactor.setCommunalAvailable
import com.android.systemui.communal.domain.interactor.setCommunalV2ConfigEnabled
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepositorySpy
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.data.repository.keyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.util.KeyguardTransitionRepositorySpySubject.Companion.assertThat
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.testKosmos
import com.google.common.truth.Truth
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.reset
import org.mockito.Mockito.spy
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@EnableFlags(FLAG_COMMUNAL_HUB)
class FromDozingTransitionInteractorTest(flags: FlagsParameterization?) : SysuiTestCase() {
    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            this.keyguardTransitionRepository = fakeKeyguardTransitionRepositorySpy
            this.fakeCommunalSceneRepository =
                spy(FakeCommunalSceneRepository(applicationScope = applicationCoroutineScope))
            fakeCommunalSceneRepository.activateIn(testScope)
        }

    private val Kosmos.underTest by Kosmos.Fixture { fromDozingTransitionInteractor }

    private val Kosmos.transitionRepository by
        Kosmos.Fixture { fakeKeyguardTransitionRepositorySpy }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(FLAG_GLANCEABLE_HUB_V2)
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags!!)
    }

    @Before
    fun setup() {
        kosmos.underTest.start()

        // Transition to DOZING and set the power interactor asleep.
        kosmos.powerInteractor.setAsleepForTest()
        kosmos.setCommunalV2ConfigEnabled(true)
        runBlocking {
            kosmos.transitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.DOZING,
                kosmos.testScope,
            )
            kosmos.fakeKeyguardRepository.setBiometricUnlockState(BiometricUnlockMode.NONE)
            reset(kosmos.transitionRepository)
        }
    }

    @Test
    @EnableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR)
    fun testTransitionToLockscreen_onWakeup() =
        kosmos.runTest {
            powerInteractor.setAwakeForTest()

            // Under default conditions, we should transition to LOCKSCREEN when waking up.
            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DOZING, to = KeyguardState.LOCKSCREEN)
        }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR)
    fun testTransitionToLockscreen_onWake_whenOccludedByDream() =
        kosmos.runTest {
            val taskInfo =
                ActivityManager.RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_DREAM
                }
            kosmos.keyguardOcclusionRepository.setOccludedFromRemoteAnimation(
                onTop = true,
                taskInfo = taskInfo,
            )

            powerInteractor.setAwakeForTest()

            // We should still transition to LOCKSCREEN (not OCCLUDED) when waking up
            // from DOZING while a dream is stopping.
            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DOZING, to = KeyguardState.LOCKSCREEN)
        }

    @Test
    @DisableSceneContainer
    fun testTransitionToDreaming() =
        kosmos.runTest {
            // Ensure dozing is off
            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
            )
            testScope.advanceTimeBy(600L)

            keyguardInteractor.setDreaming(true)
            testScope.advanceTimeBy(60L)

            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DOZING, to = KeyguardState.DREAMING)
        }

    @Test
    @EnableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR)
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun testTransitionToLockscreen_onWake_canNotDream_glanceableHubAvailable() =
        kosmos.runTest {
            whenever(dreamManager.canStartDreaming(anyBoolean())).thenReturn(false)
            setCommunalAvailable(true)
            powerInteractor.setAwakeForTest()

            // If dreaming is NOT possible but communal is available, then we should transition to
            // LOCKSCREEN when waking up due to power button press.
            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DOZING, to = KeyguardState.LOCKSCREEN)
        }

    @Test
    @EnableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR)
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun testTransitionToLockscreen_onWake_canDream_glanceableHubNotAvailable() =
        kosmos.runTest {
            whenever(dreamManager.canStartDreaming(anyBoolean())).thenReturn(true)
            setCommunalAvailable(false)

            powerInteractor.setAwakeForTest()

            // If dreaming is possible but communal is NOT available, then we should transition to
            // LOCKSCREEN when waking up due to power button press.
            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DOZING, to = KeyguardState.LOCKSCREEN)
        }

    @Test
    @DisableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR, FLAG_SCENE_CONTAINER, FLAG_GLANCEABLE_HUB_V2)
    fun testTransitionToGlanceableHub_onWakeup_ifAvailable() =
        kosmos.runTest {
            setCommunalAvailable(true)
            if (!glanceableHubV2()) {
                whenever(dreamManager.canStartDreaming(anyBoolean())).thenReturn(true)
            }

            // Device turns on.
            powerInteractor.setAwakeForTest()
            testScope.advanceTimeBy(51L)

            // We transition to the hub when waking up.
            Truth.assertThat(communalSceneRepository.currentScene.value)
                .isEqualTo(CommunalScenes.Communal)
            // No transitions are directly started by this interactor.
            assertThat(transitionRepository).noTransitionsStarted()
        }

    @Test
    @DisableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR, FLAG_SCENE_CONTAINER)
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun testTransitionToLockscreen_onWakeupFromLift() =
        kosmos.runTest {
            setCommunalAvailable(true)
            whenever(dreamManager.canStartDreaming(anyBoolean())).thenReturn(true)

            // Device turns on.
            powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_LIFT)
            testScope.advanceTimeBy(51L)

            // We transition to the lockscreen instead of the hub.
            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DOZING, to = KeyguardState.LOCKSCREEN)
        }

    @Test
    @DisableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR, FLAG_SCENE_CONTAINER, FLAG_GLANCEABLE_HUB_V2)
    fun testTransitionOccluded_onWakeup_ifGlanceableHubAvailableAndOccluded() =
        kosmos.runTest {
            setCommunalAvailable(true)
            fakeKeyguardRepository.setKeyguardOccluded(true)
            whenever(dreamManager.canStartDreaming(anyBoolean())).thenReturn(true)

            // Device turns on.
            powerInteractor.setAwakeForTest()
            advanceTimeBy(100.milliseconds)

            // We do not transition to the hub.
            Truth.assertThat(communalSceneRepository.currentScene.value)
                .isEqualTo(CommunalScenes.Blank)
            // No transitions are directly started by this interactor.
            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DOZING, to = KeyguardState.OCCLUDED)
        }

    @Test
    @DisableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR, FLAG_SCENE_CONTAINER)
    fun testTransitionToPrimaryBouncer_onWakeup_withLockscreenNotEnabledButLockscreenIsNotDismissible() =
        kosmos.runTest {
            val primaryBouncerShowing by collectLastValue(keyguardInteractor.primaryBouncerShowing)
            fakeKeyguardRepository.setKeyguardShowing(true)
            fakeKeyguardBouncerRepository.setPrimaryShow(true)
            fakeKeyguardRepository.setKeyguardEnabled(false)
            runCurrent()
            Truth.assertThat(primaryBouncerShowing).isTrue()

            // Device turns on.
            powerInteractor.setAwakeForTest()
            advanceTimeBy(100.milliseconds)

            // starts transition to primary bouncer.
            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DOZING, to = KeyguardState.PRIMARY_BOUNCER)
        }

    @Test
    @EnableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR, FLAG_GLANCEABLE_HUB_V2)
    fun testTransitionToLockscreen_onWake_glanceableHubAvailable_glanceableHubV2Enabled() =
        kosmos.runTest {
            whenever(dreamManager.canStartDreaming(anyBoolean())).thenReturn(false)
            setCommunalAvailable(true)
            powerInteractor.setAwakeForTest()

            // Even if communal is available (and we can't dream), in hub_v2 we should transition to
            // LOCKSCREEN when waking up due to power button press.
            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DOZING, to = KeyguardState.LOCKSCREEN)
        }

    @Test
    @EnableFlags(FLAG_WAKEFULNESS_FOR_ANIMATIONS)
    @DisableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR) // Test the legacy path
    fun testTransition_flagOn_onlyTriggersOnAwake_notOnStartingToWake() =
        kosmos.runTest {
            // We start in AOD and asleep (from setup())

            // Start waking up
            powerInteractor.onStartedWakingUp(
                PowerManager.WAKE_REASON_POWER_BUTTON,
                /* powerButtonLaunchGestureTriggeredOnWakeUp = */ false,
            )

            // Ensure transition isn't playing yet
            assertThat(transitionRepository).noTransitionsStarted()

            // Finish waking up
            powerInteractor.onFinishedWakingUp()

            // Ensure transition plays
            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DOZING, to = KeyguardState.LOCKSCREEN)
        }

    @Test
    @DisableFlags(
        FLAG_WAKEFULNESS_FOR_ANIMATIONS,
        FLAG_KEYGUARD_WM_STATE_REFACTOR,
    ) // Test legacy path
    fun testTransition_flagOff_triggersOnStartingToWake() =
        kosmos.runTest {
            // We start in AOD and asleep (from setup())

            // Start waking up
            powerInteractor.onStartedWakingUp(
                PowerManager.WAKE_REASON_POWER_BUTTON,
                /* powerButtonLaunchGestureTriggeredOnWakeUp = */ false,
            )

            advanceTimeBy(100) // account for debouncing

            // Ensure transition plays
            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DOZING, to = KeyguardState.LOCKSCREEN)
        }
}
