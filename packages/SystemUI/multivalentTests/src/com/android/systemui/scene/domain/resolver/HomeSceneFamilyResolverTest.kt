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

package com.android.systemui.scene.domain.resolver

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.platform.test.flag.junit.FlagsParameterization
import android.service.dreams.Flags.FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.DriveDreamStateFromOcclusion
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.domain.interactor.sceneBackInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@EnableSceneContainer
@OptIn(ExperimentalCoroutinesApi::class)
class HomeSceneFamilyResolverTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Test
    fun resolvesToDream() =
        kosmos.runTest {
            val resolvedScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            val dreamTaskInfo =
                RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_DREAM
                }
            keyguardOcclusionRepository.setOccludedFromRemoteAnimation(true, dreamTaskInfo)
            if (!DriveDreamStateFromOcclusion.isEnabled) {
                fakeKeyguardRepository.setDreaming(true)
                fakeKeyguardRepository.setDozeTransitionModel(
                    DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
                )
                testScope.advanceTimeBy(KeyguardInteractor.IS_DREAMING_NOT_DOZING_DELAY_MS + 100L)
            }

            assertThat(resolvedScene).isEqualTo(Scenes.Dream)
        }

    @Test
    fun resolvesToOccluded() =
        kosmos.runTest {
            val resolvedScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            val appTaskInfo =
                RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD
                }
            keyguardOcclusionRepository.setOccludedFromRemoteAnimation(true, appTaskInfo)
            keyguardOcclusionRepository.setOccludedFromWm(true)

            assertThat(resolvedScene).isEqualTo(Scenes.Occluded)
        }

    @Test
    fun resolvesToGone_whenKeyguardDisabled() =
        kosmos.runTest {
            val resolvedScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            fakeKeyguardRepository.setKeyguardEnabled(false)

            assertThat(resolvedScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun resolvesToLockscreen_whenKeyguardDisabled_butAodEnabledAndAsleep() =
        kosmos.runTest {
            val resolvedScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            kosmos.powerInteractor.setAsleepForTest()
            fakeKeyguardRepository.setKeyguardEnabled(false)
            fakeKeyguardRepository.setAodAvailable(true)

            assertThat(resolvedScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun resolvesToGone_whenKeyguardDisabled_aodEnabledButAwake() =
        kosmos.runTest {
            val resolvedScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            kosmos.powerInteractor.setAwakeForTest()
            fakeKeyguardRepository.setKeyguardEnabled(false)
            fakeKeyguardRepository.setAodAvailable(true)

            assertThat(resolvedScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun resolvesToLockscreen_whenCanSwipeToEnter() =
        kosmos.runTest {
            val resolvedScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            setupSwipeDeviceEntryMethod()

            assertThat(resolvedScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun resolvesToLockscreen_whenDeviceNotEntered() =
        kosmos.runTest {
            val resolvedScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)

            assertThat(resolvedScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun resolvesToLockscreen_whenOnBackStack() =
        kosmos.runTest {
            val resolvedScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)
            sceneBackInteractor.addLockscreenToBackStack("test")

            assertThat(deviceEntryInteractor.canSwipeToEnter.value).isEqualTo(true)
            assertThat(deviceEntryInteractor.isDeviceEntered.value).isEqualTo(false)
            assertThat(deviceEntryInteractor.isUnlocked.value).isEqualTo(true)
            assertThat(resolvedScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun resolvesToGone_byDefault() =
        kosmos.runTest {
            val resolvedScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Gone)

            assertThat(deviceEntryInteractor.canSwipeToEnter.value).isEqualTo(false)
            assertThat(deviceEntryInteractor.isDeviceEntered.value).isEqualTo(true)
            assertThat(deviceEntryInteractor.isUnlocked.value).isEqualTo(true)
            assertThat(resolvedScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun resolvesToDream_whenUnlocked_butIsDreaming() =
        kosmos.runTest {
            val resolvedScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Gone)

            val dreamTaskInfo =
                RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_DREAM
                }
            keyguardOcclusionRepository.setOccludedFromRemoteAnimation(true, dreamTaskInfo)

            if (!DriveDreamStateFromOcclusion.isEnabled) {
                fakeKeyguardRepository.setDreaming(true)
                fakeKeyguardRepository.setDozeTransitionModel(
                    DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
                )
                testScope.advanceTimeBy(KeyguardInteractor.IS_DREAMING_NOT_DOZING_DELAY_MS + 100L)
            }

            assertThat(resolvedScene).isEqualTo(Scenes.Dream)
        }

    private fun Kosmos.setupSwipeDeviceEntryMethod() {
        fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.None)
        fakeKeyguardRepository.setKeyguardEnabled(true)
    }

    private fun Kosmos.switchToScene(sceneKey: SceneKey) {
        sceneInteractor.changeScene(sceneKey, "reason")
        sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(sceneKey)))
    }
}
