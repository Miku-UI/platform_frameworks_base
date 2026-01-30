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

package com.android.systemui.securelockdevice.data.repository

import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import android.security.authenticationpolicy.AuthenticationPolicyManager
import android.security.authenticationpolicy.authenticationPolicyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.withArgCaptor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_SECURE_LOCK_DEVICE)
class SecureLockDeviceRepositoryTest : SysuiTestCase() {
    @JvmField @Rule var mockitoRule: MockitoRule = MockitoJUnit.rule()

    private val kosmos = testKosmos()
    private val underTest: SecureLockDeviceRepository = kosmos.realSecureLockDeviceRepository

    @Test
    fun updatesSecureLockDeviceEnabledStatus_fromAuthenticationPolicyManager() =
        kosmos.testScope.runTest {
            val isSecureLockDeviceEnabled by collectLastValue(underTest.isSecureLockDeviceEnabled)
            runCurrent()

            val listener = kosmos.authenticationPolicyManager.captureListener()
            runCurrent()

            assertThat(isSecureLockDeviceEnabled).isEqualTo(false)

            listener.onSecureLockDeviceEnabledStatusChanged(true)

            assertThat(isSecureLockDeviceEnabled).isEqualTo(true)

            listener.onSecureLockDeviceEnabledStatusChanged(false)

            assertThat(isSecureLockDeviceEnabled).isEqualTo(false)
        }
}

private fun AuthenticationPolicyManager.captureListener() =
    withArgCaptor<AuthenticationPolicyManager.SecureLockDeviceStatusListener> {
        verify(this@captureListener).registerSecureLockDeviceStatusListener(any(), capture())
    }
