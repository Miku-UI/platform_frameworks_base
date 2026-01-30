/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy.profile.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.policy.profile.data.repository.managedProfileRepository
import com.android.systemui.statusbar.policy.profile.shared.model.ProfileInfo
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
class ManagedProfileInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest = kosmos.managedProfileInteractor

    @Before
    fun setUp() {
        kosmos.sceneContainerStartable.start()
        kosmos.managedProfileRepository.currentProfileInfo.value = profileInfo
    }

    @Test
    fun currentProfileInfo_whenDeviceIsEntered_isShown() =
        kosmos.runTest {
            val info by collectLastValue(underTest.currentProfileInfo)

            // GIVEN device is locked
            setDeviceAsLocked()
            assertThat(info).isNull()

            // WHEN device is entered (unlocked)
            setDeviceAsEntered()

            // THEN profile info is shown
            assertThat(info).isEqualTo(profileInfo)
        }

    @Test
    fun currentProfileInfo_whenDeviceBecomesLocked_isHidden() =
        kosmos.runTest {
            val info by collectLastValue(underTest.currentProfileInfo)

            // GIVEN device is unlocked
            setDeviceAsEntered()
            assertThat(info).isEqualTo(profileInfo)

            // WHEN device becomes locked
            setDeviceAsLocked()

            // THEN profile info is hidden
            assertThat(info).isNull()
        }

    @Test
    fun currentProfileInfo_whenKeyguardIsOccluded_isShown() =
        kosmos.runTest {
            val info by collectLastValue(underTest.currentProfileInfo)

            // GIVEN device is locked and not occluded
            setDeviceAsLocked()
            assertThat(info).isNull()

            // WHEN keyguard becomes occluded
            setKeyguardState(from = KeyguardState.LOCKSCREEN, to = KeyguardState.OCCLUDED)

            // THEN profile info is shown
            assertThat(info).isEqualTo(profileInfo)
        }

    @Test
    fun currentProfileInfo_whenKeyguardBecomesNotOccluded_isHidden() =
        kosmos.runTest {
            val info by collectLastValue(underTest.currentProfileInfo)

            // GIVEN device is locked and occluded
            setDeviceAsLocked()
            setKeyguardState(from = KeyguardState.LOCKSCREEN, to = KeyguardState.OCCLUDED)
            assertThat(info).isEqualTo(profileInfo)

            // WHEN keyguard is no longer occluded
            setKeyguardState(from = KeyguardState.OCCLUDED, to = KeyguardState.LOCKSCREEN)

            // THEN profile info is hidden
            assertThat(info).isNull()
        }

    private fun Kosmos.setDeviceAsEntered() {
        fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.None)
        fakeDeviceEntryRepository.setLockscreenEnabled(false)
        setCurrentScene(Scenes.Gone)
    }

    private fun Kosmos.setDeviceAsLocked() {
        fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Password)
        fakeDeviceEntryRepository.setLockscreenEnabled(true)
        setCurrentScene(Scenes.Lockscreen)
    }

    private fun Kosmos.setCurrentScene(scene: SceneKey) {
        sceneInteractor.changeScene(scene, "ManagedProfileInteractorTest")
        sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(scene)))
    }

    private suspend fun Kosmos.setKeyguardState(from: KeyguardState, to: KeyguardState) {
        fakeKeyguardTransitionRepository.sendTransitionSteps(
            from = from,
            to = to,
            testScope = kosmos.testScope,
        )
    }

    companion object {
        private const val TEST_USER_ID = 10
        private const val TEST_ICON_RES_ID = 12345
        private const val TEST_ACCESSIBILITY_STRING = "Work apps"

        val profileInfo =
            ProfileInfo(
                userId = TEST_USER_ID,
                iconResId = TEST_ICON_RES_ID,
                contentDescription = TEST_ACCESSIBILITY_STRING,
            )
    }
}
