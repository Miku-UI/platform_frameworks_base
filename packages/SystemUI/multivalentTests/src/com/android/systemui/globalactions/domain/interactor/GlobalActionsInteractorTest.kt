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

package com.android.systemui.globalactions.domain.interactor

import android.content.pm.UserInfo
import android.os.UserManager
import android.os.userManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.globalactions.data.repository.globalActionsRepository
import com.android.systemui.globalactions.globalActionsManager
import com.android.systemui.globalactions.shared.model.GlobalActionType
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class GlobalActionsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val underTest = kosmos.globalActionsInteractor

    @Test
    fun OnDismissed() {
        kosmos.runTest {
            val isVisible by collectLastValue(underTest.isVisible)
            underTest.onDismissed()

            assertThat(isVisible).isFalse()
        }
    }

    @Test
    fun OnShown() {
        kosmos.runTest {
            val isVisible by collectLastValue(underTest.isVisible)
            underTest.onShown()

            assertThat(isVisible).isTrue()
        }
    }

    @Test
    fun availableGlobalActions_provisionedAndUnlocked_showsAllActions() =
        kosmos.runTest {
            kosmos.globalActionsRepository.possibleGlobalActions =
                listOf(GlobalActionType.POWER, GlobalActionType.LOCK)
            kosmos.globalActionsRepository.lockedDeviceStateBlockList =
                listOf(GlobalActionType.LOCK)

            kosmos.fakeDeviceProvisioningRepository.setDeviceProvisioned(true)
            // Device entered = unlocked AND on the Gone scene
            setUnlocked(true)

            val actions by collectLastValue(underTest.availableGlobalActions)

            assertThat(actions)
                .containsExactly(GlobalActionType.POWER, GlobalActionType.LOCK)
                .inOrder()
        }

    @Test
    fun availableGlobalActions_locked_filtersBlockedActions() =
        kosmos.runTest {
            kosmos.globalActionsRepository.possibleGlobalActions =
                listOf(GlobalActionType.POWER, GlobalActionType.LOCK)
            kosmos.globalActionsRepository.lockedDeviceStateBlockList =
                listOf(GlobalActionType.LOCK)

            kosmos.fakeDeviceProvisioningRepository.setDeviceProvisioned(true)
            // Device not entered = locked OR on Lockscreen scene
            setUnlocked(false)

            val actions by collectLastValue(underTest.availableGlobalActions)

            assertThat(actions).containsExactly(GlobalActionType.POWER)
        }

    @Test
    fun availableGlobalActions_unprovisioned_filtersBlockedActions() =
        kosmos.runTest {
            globalActionsRepository.possibleGlobalActions =
                listOf(GlobalActionType.POWER, GlobalActionType.AIRPLANE)
            globalActionsRepository.unprovisionedDeviceStateBlockList =
                listOf(GlobalActionType.AIRPLANE)

            fakeDeviceProvisioningRepository.setDeviceProvisioned(false)
            // Ensure we are in a "clean" state, though unprovisioned should trump all.
            setUnlocked(false)

            val actions by collectLastValue(underTest.availableGlobalActions)

            assertThat(actions).containsExactly(GlobalActionType.POWER)
        }

    @Test
    fun shutdown() =
        kosmos.runTest {
            underTest.shutdown()

            verify(globalActionsManager).shutdown()
        }

    @Test
    fun reboot_noSafeMode() =
        kosmos.runTest {
            underTest.reboot(false)

            verify(globalActionsManager).reboot(false)
        }

    @Test
    fun reboot_safeMode_allowed() =
        kosmos.runTest {
            val user = UserInfo(10, "Test", 0)
            fakeUserRepository.setUserInfos(listOf(user))
            fakeUserRepository.setSelectedUserInfo(user)
            whenever(
                    userManager.hasUserRestrictionForUser(
                        UserManager.DISALLOW_SAFE_BOOT,
                        user.userHandle,
                    )
                )
                .thenReturn(false)

            val result = underTest.reboot(true)

            assertThat(result).isTrue()
            verify(globalActionsManager).reboot(true)
        }

    @Test
    fun reboot_safeMode_disallowed() =
        kosmos.runTest {
            val user = UserInfo(10, "Test", 0)
            fakeUserRepository.setUserInfos(listOf(user))
            fakeUserRepository.setSelectedUserInfo(user)
            whenever(
                    userManager.hasUserRestrictionForUser(
                        UserManager.DISALLOW_SAFE_BOOT,
                        user.userHandle,
                    )
                )
                .thenReturn(true)

            val result = underTest.reboot(true)

            assertThat(result).isFalse()
            verify(globalActionsManager, never()).reboot(any())
        }

    private fun Kosmos.setUnlocked(isUnlocked: Boolean) {
        fakeDeviceEntryRepository.deviceUnlockStatus.value =
            DeviceUnlockStatus(isUnlocked = isUnlocked, deviceUnlockSource = null)
    }
}
