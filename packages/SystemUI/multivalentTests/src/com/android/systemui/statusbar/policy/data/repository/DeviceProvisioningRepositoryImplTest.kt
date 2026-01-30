/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.systemui.statusbar.policy.data.repository

import android.content.Context
import android.content.applicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.withArgCaptor
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceProvisioningRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val systemClock = kosmos.fakeSystemClock
    @Mock lateinit var deviceProvisionedController: DeviceProvisionedController

    lateinit var underTest: DeviceProvisioningRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        underTest =
            DeviceProvisioningRepositoryImpl(
                kosmos.applicationContext,
                testScope.backgroundScope,
                systemClock,
                deviceProvisionedController,
            )
    }

    @Test
    fun isDeviceProvisioned_reflectsCurrentControllerState() = runTest {
        whenever(deviceProvisionedController.isDeviceProvisioned).thenReturn(true)
        val deviceProvisioned by collectLastValue(underTest.isDeviceProvisioned)
        assertThat(deviceProvisioned).isTrue()
    }

    @Test
    fun isDeviceProvisioned_updatesWhenControllerStateChanges_toTrue() = runTest {
        val deviceProvisioned by collectLastValue(underTest.isDeviceProvisioned)
        runCurrent()
        whenever(deviceProvisionedController.isDeviceProvisioned).thenReturn(true)
        withArgCaptor { verify(deviceProvisionedController).addCallback(capture()) }
            .onDeviceProvisionedChanged()
        assertThat(deviceProvisioned).isTrue()
    }

    @Test
    fun isDeviceProvisioned_updatesWhenControllerStateChanges_toFalse() = runTest {
        val deviceProvisioned by collectLastValue(underTest.isDeviceProvisioned)
        runCurrent()
        whenever(deviceProvisionedController.isDeviceProvisioned).thenReturn(false)
        withArgCaptor { verify(deviceProvisionedController).addCallback(capture()) }
            .onDeviceProvisionedChanged()
        assertThat(deviceProvisioned).isFalse()
    }

    @Test
    fun getProvisionedTimestamp_provisionedEarlierWithoutTracking_isUnknown() =
        testScope.runTest {
            whenever(deviceProvisionedController.isDeviceProvisioned).thenReturn(true)
            mContext
                .getSharedPreferences(mContext.packageName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            underTest.start()

            val timestamp = underTest.getProvisionedTimestamp()

            assertThat(timestamp)
                .isEqualTo(DeviceProvisioningRepository.ProvisionedTimestamp.Unknown)
        }

    @Test
    fun getProvisionedTimestamp_provisionedEarlierWithTracking_isProvisioningInstant() =
        testScope.runTest {
            whenever(deviceProvisionedController.isDeviceProvisioned).thenReturn(true)
            kosmos.applicationContext
                .getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
                .edit()
                .putLong(DeviceProvisioningRepositoryImpl.PREF_DEVICE_PROVISIONED_TIMESTAMP, 42L)
                .apply()
            underTest.start()

            val timestamp = underTest.getProvisionedTimestamp()

            assertThat(timestamp)
                .isEqualTo(
                    DeviceProvisioningRepository.ProvisionedTimestamp.AtInstant(
                        Instant.ofEpochMilli(42L)
                    )
                )
        }

    @Test
    fun getProvisionedTimestamp_notProvisioned_isNotProvisioned() =
        testScope.runTest {
            whenever(deviceProvisionedController.isDeviceProvisioned).thenReturn(false)
            underTest.start()

            val timestamp = underTest.getProvisionedTimestamp()

            assertThat(timestamp)
                .isEqualTo(DeviceProvisioningRepository.ProvisionedTimestamp.NotProvisioned)
        }

    @Test
    fun getProvisionedTimestamp_provisionedWhileTracking_isProvisioningInstant() =
        testScope.runTest {
            // Start not provisioned -> tracking
            systemClock.setCurrentTimeMillis(1L)
            whenever(deviceProvisionedController.isDeviceProvisioned).thenReturn(false)
            underTest.start()
            runCurrent()
            assertThat(underTest.getProvisionedTimestamp())
                .isEqualTo(DeviceProvisioningRepository.ProvisionedTimestamp.NotProvisioned)

            // Now provisioning happens
            systemClock.setCurrentTimeMillis(2L)
            whenever(deviceProvisionedController.isDeviceProvisioned).thenReturn(true)
            withArgCaptor { verify(deviceProvisionedController).addCallback(capture()) }
                .onDeviceProvisionedChanged()
            runCurrent()

            // A bit later, we query
            systemClock.setCurrentTimeMillis(3L)
            val timestamp = underTest.getProvisionedTimestamp()
            assertThat(timestamp)
                .isEqualTo(
                    DeviceProvisioningRepository.ProvisionedTimestamp.AtInstant(
                        Instant.ofEpochMilli(2L)
                    )
                )

            // Also, we stored it for next SystemUI start
            assertThat(
                    kosmos.applicationContext
                        .getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
                        .getLong(
                            DeviceProvisioningRepositoryImpl.PREF_DEVICE_PROVISIONED_TIMESTAMP,
                            0L,
                        )
                )
                .isEqualTo(2L)
        }
}
