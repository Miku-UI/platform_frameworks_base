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

package com.android.systemui.deviceentry.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeBiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeTrustRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class ActiveUnlockInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private lateinit var underTest: ActiveUnlockInteractor

    @Before
    fun setUp() {
        underTest = kosmos.activeUnlockInteractor
        kosmos.canTriggerActiveUnlock(canRun = true)
    }

    @Test
    fun canTriggerActiveUnlockTrue() =
        testScope.runTest {
            val canRunActiveUnlock by collectLastValue(underTest.canRunActiveUnlock)

            // using initial state from setUp
            assertThat(canRunActiveUnlock).isTrue()
        }

    @Test
    fun currentUserActiveUnlockNotRunning_canTriggerActiveUnlockFalse() =
        testScope.runTest {
            val canRunActiveUnlock by collectLastValue(underTest.canRunActiveUnlock)
            kosmos.fakeTrustRepository.setCurrentUserActiveUnlockAvailable(false)
            runCurrent()

            assertThat(canRunActiveUnlock).isFalse()
        }

    @Test
    fun fingerprintLockedOut_canTriggerActiveUnlockFalse() =
        testScope.runTest {
            val canRunActiveUnlock by collectLastValue(underTest.canRunActiveUnlock)
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(canRunActiveUnlock).isFalse()
        }

    @Test
    fun fingerprintNotCurrentlyAllowed_canTriggerActiveUnlockFalse() =
        testScope.runTest {
            val canRunActiveUnlock by collectLastValue(underTest.canRunActiveUnlock)
            kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(false)
            runCurrent()

            assertThat(canRunActiveUnlock).isFalse()
        }

    @Test
    fun userSwitching_canTriggerActiveUnlockFalse() =
        testScope.runTest {
            val canRunActiveUnlock by collectLastValue(underTest.canRunActiveUnlock)
            kosmos.fakeUserRepository.setMainUserIsUserSwitching()
            runCurrent()

            assertThat(canRunActiveUnlock).isFalse()
        }

    @Test
    fun isUnlocked_canTriggerActiveUnlockFalse() =
        testScope.runTest {
            val canRunActiveUnlock by collectLastValue(underTest.canRunActiveUnlock)
            kosmos.fakeDeviceEntryRepository.deviceUnlockStatus.value =
                DeviceUnlockStatus(isUnlocked = true, deviceUnlockSource = null)
            runCurrent()

            assertThat(canRunActiveUnlock).isFalse()
        }
}
