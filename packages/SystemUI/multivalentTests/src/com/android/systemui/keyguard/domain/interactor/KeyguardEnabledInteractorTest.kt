/*
 * Copyright (C) 2026 The Android Open Source Project
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
 *
 */

package com.android.systemui.keyguard.domain.interactor

import android.content.pm.UserInfo
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.internal.widget.lockPatternUtils
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.data.model.contains
import com.android.systemui.scene.data.repository.Idle
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.whenever

@SmallTest
@EnableSceneContainer
@RunWith(AndroidJUnit4::class)
class KeyguardEnabledInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private val underTest = kosmos.keyguardEnabledInteractor

    @Test
    fun keyguardDisabledByLockPatternUtils_updatesOnTransitionToLockscreen() =
        kosmos.runTest {
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            val transitionStateFlow =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(Scenes.Lockscreen)
                )
            sceneInteractor.setTransitionState(transitionStateFlow)
            transitionStateFlow.value = ObservableTransitionState.Idle(Scenes.Gone)

            underTest.start()

            val isKeyguardEnabled by collectLastValue(underTest.isKeyguardEnabled)
            assertThat(isKeyguardEnabled).isTrue()

            // Now disable as if by ADB
            whenever(lockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(true)

            // Then next transition to Lockscreen should refresh this value
            transitionStateFlow.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Gone,
                    toScene = Scenes.Lockscreen,
                    currentScene = flowOf(Scenes.Gone),
                    progress = flowOf(0.1f),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            runCurrent()
            assertThat(isKeyguardEnabled).isFalse()
        }

    @Test
    fun keyguardDisabledByLockPatternUtils_updates_onSwitchToUserWithNoLockscreen() =
        kosmos.runTest {
            kosmos.fakeUserRepository.setUserInfos(listOf(systemUser, primaryUser))
            kosmos.fakeUserRepository.setSelectedUserInfo(primaryUser)

            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            underTest.start()

            val isKeyguardEnabled by collectLastValue(underTest.isKeyguardEnabled)
            assertThat(isKeyguardEnabled).isTrue()

            // Now switch to a user that has lockscreen disabled as if by Settings -> None security
            whenever(lockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(true)
            kosmos.fakeUserRepository.setSelectedUserInfo(systemUser)

            runCurrent()
            assertThat(isKeyguardEnabled).isFalse()
        }

    @Test
    fun keyguardDisabledByLockPatternUtils_updates_onSwitchToUserWithLockscreen() =
        kosmos.runTest {
            kosmos.fakeUserRepository.setUserInfos(listOf(systemUser, primaryUser))
            whenever(lockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(true)
            kosmos.fakeUserRepository.setSelectedUserInfo(systemUser)

            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            underTest.start()

            val isKeyguardEnabled by collectLastValue(underTest.isKeyguardEnabled)
            assertThat(isKeyguardEnabled).isFalse()

            // Now switch to a user that has lockscreen enabled
            whenever(lockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(false)
            kosmos.fakeUserRepository.setSelectedUserInfo(primaryUser)

            runCurrent()
            assertThat(isKeyguardEnabled).isTrue()
        }

    @Test
    fun keyguardEnabledAndNotSuppressed_isEnabled() =
        kosmos.runTest {
            fakeKeyguardRepository.setKeyguardEnabled(true)

            whenever(lockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(false)
            assertThat(underTest.isKeyguardEnabledAndNotSuppressed()).isTrue()
        }

    @Test
    fun keyguardEnabledAndSuppressed_isNotEnabled() =
        kosmos.runTest {
            fakeKeyguardRepository.setKeyguardEnabled(true)

            whenever(lockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(true)
            assertThat(underTest.isKeyguardEnabledAndNotSuppressed()).isFalse()
        }

    @Test
    fun keyguardDisabledIgnoresSuppressed() =
        kosmos.runTest {
            fakeKeyguardRepository.setKeyguardEnabled(false)

            whenever(lockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(true)
            assertThat(underTest.isKeyguardEnabledAndNotSuppressed()).isFalse()

            whenever(lockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(false)
            assertThat(underTest.isKeyguardEnabledAndNotSuppressed()).isFalse()
        }

    companion object {
        private val systemUser =
            UserInfo(
                /* id =*/ 0,
                "system user",
                /* iconPath =*/ null,
                UserInfo.FLAG_SYSTEM,
                UserManager.USER_TYPE_SYSTEM_HEADLESS,
            )

        private val primaryUser =
            UserInfo(
                /* id =*/ 10,
                "user with lockscreen",
                UserInfo.FLAG_FULL or UserInfo.FLAG_PRIMARY,
            )
    }
}
