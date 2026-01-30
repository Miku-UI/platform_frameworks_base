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

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.provider.Settings
import android.service.dream.dreamManager
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.Flags.FLAG_GLANCEABLE_HUB_V2
import com.android.systemui.Flags.glanceableHubV2
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.common.data.repository.batteryRepositoryDeprecated
import com.android.systemui.common.data.repository.fake
import com.android.systemui.communal.data.repository.communalSceneRepository
import com.android.systemui.communal.domain.interactor.setCommunalAvailable
import com.android.systemui.communal.domain.interactor.setCommunalV2ConfigEnabled
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.keyguard.data.repository.FakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepositorySpy
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.data.repository.keyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.util.KeyguardTransitionRepositorySpySubject.Companion.assertThat
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useStandardTestDispatcher
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.statusbar.pipeline.battery.shared.StatusBarUniversalBatteryDataSource
import com.android.systemui.statusbar.policy.batteryController
import com.android.systemui.statusbar.policy.fake
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.reset
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@DisableFlags(Flags.FLAG_SCENE_CONTAINER, Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR)
class FromDreamingTransitionInteractorTest(flags: FlagsParameterization?) : SysuiTestCase() {
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

    private val kosmos =
        testKosmos().useStandardTestDispatcher().apply {
            this.fakeKeyguardTransitionRepository =
                FakeKeyguardTransitionRepository(
                    // This test sends transition steps manually in the test cases.
                    initiallySendTransitionStepsOnStartTransition = false,
                    testScope = testScope,
                )

            this.keyguardTransitionRepository = fakeKeyguardTransitionRepositorySpy
        }

    private val Kosmos.underTest by Kosmos.Fixture { fromDreamingTransitionInteractor }
    private val Kosmos.transitionRepository by
        Kosmos.Fixture { fakeKeyguardTransitionRepositorySpy }

    @Before
    fun setup() {
        runBlocking {
            kosmos.fakeKeyguardRepository.setKeyguardOccluded(true)
            kosmos.fakeKeyguardRepository.setDreaming(true)
            // Get past initial setup
            kosmos.testScope.advanceTimeBy(600L)

            kosmos.transitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.DREAMING,
                kosmos.testScope,
            )
            reset(kosmos.transitionRepository)
            kosmos.setCommunalAvailable(true)
            kosmos.setCommunalV2ConfigEnabled(true)
        }
        kosmos.underTest.start()
    }

    @Test
    @EnableFlags(Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR)
    @Ignore("Until b/349837588 is fixed")
    fun testTransitionToOccluded_ifDreamEnds_occludingActivityOnTop() =
        kosmos.runTest {
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(onTop = true)
            fakeKeyguardRepository.setDreaming(false)

            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DREAMING, to = KeyguardState.OCCLUDED)
        }

    @Test
    fun testTransitionsToLockscreen_whenOccludingActivityEnds() =
        kosmos.runTest {
            fakeKeyguardRepository.setDreaming(false)
            fakeKeyguardRepository.setKeyguardOccluded(false)
            testScope.advanceTimeBy(110L)

            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DREAMING, to = KeyguardState.LOCKSCREEN)
        }

    @Test
    fun testTransitionsToOccluded_whenDreamEnds_andStillOccluded() =
        kosmos.runTest {
            fakeKeyguardRepository.setDreaming(false)
            testScope.advanceTimeBy(110L)

            assertThat(transitionRepository)
                .startedTransition(from = KeyguardState.DREAMING, to = KeyguardState.OCCLUDED)
        }

    @Test
    fun testTransitionToAlternateBouncer() =
        kosmos.runTest {
            fakeKeyguardBouncerRepository.setAlternateVisible(true)
            testScope.runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    from = KeyguardState.DREAMING,
                    to = KeyguardState.ALTERNATE_BOUNCER,
                )
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun testTransitionToGlanceableHubOnWake() =
        kosmos.runTest {
            if (glanceableHubV2()) {
                val user = fakeUserRepository.asMainUser()
                fakeSettings.putIntForUser(
                    Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP,
                    1,
                    user.id,
                )
                if (StatusBarUniversalBatteryDataSource.isEnabled) {
                    batteryController.fake._isPluggedIn = true
                } else {
                    batteryRepositoryDeprecated.fake.setDevicePluggedIn(true)
                }
            } else {
                whenever(dreamManager.canStartDreaming(anyBoolean())).thenReturn(true)
            }

            // Device wakes up.
            powerInteractor.setAwakeForTest()
            testScope.advanceTimeBy(60L)

            // We transition to the hub when waking up.
            assertThat(communalSceneRepository.currentScene.value)
                .isEqualTo(CommunalScenes.Communal)
            // No transitions are directly started by this interactor.
            assertThat(transitionRepository).noTransitionsStarted()
        }
}
