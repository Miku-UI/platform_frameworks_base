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

package com.android.systemui.statusbar.systemstatusicons.ethernet.ui.viewmodel

import android.content.testableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.AccessibilityContentDescriptions
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.shared.data.repository.FakeConnectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class EthernetIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val fakeConnectivityRepository: FakeConnectivityRepository by lazy {
        kosmos.connectivityRepository.fake
    }

    private val underTest =
        kosmos.ethernetIconViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Test
    fun icon_default_validated() =
        kosmos.runTest {
            // Ethernet is the default connection and validated
            fakeConnectivityRepository.setEthernetConnected(default = true, validated = true)

            // Icon is the 'fully connected' ethernet icon
            val expected =
                Icon.Resource(
                    R.drawable.stat_sys_ethernet_fully,
                    ContentDescription.Resource(
                        AccessibilityContentDescriptions.ETHERNET_CONNECTION_VALUES[1]
                    ),
                )

            assertThat(underTest.icon).isEqualTo(expected)
        }

    @Test
    fun icon_default_notValidated() =
        kosmos.runTest {
            // Ethernet is the default connection but not validated
            fakeConnectivityRepository.setEthernetConnected(default = true, validated = false)

            // Icon is the 'connected, not validated' ethernet icon
            val expected =
                Icon.Resource(
                    R.drawable.stat_sys_ethernet,
                    ContentDescription.Resource(
                        AccessibilityContentDescriptions.ETHERNET_CONNECTION_VALUES[0]
                    ),
                )

            assertThat(underTest.icon).isEqualTo(expected)
        }

    @Test
    fun icon_notDefault_validated() =
        kosmos.runTest {
            // Ethernet is connected and validated, but NOT the default connection
            fakeConnectivityRepository.setEthernetConnected(default = false, validated = true)

            // Icon is null (we only show the icon for the default connection)
            assertThat(underTest.icon).isNull()
        }

    @Test
    fun icon_notDefault_notValidated() =
        kosmos.runTest {
            // Ethernet is connected, but NOT validated and NOT the default connection
            fakeConnectivityRepository.setEthernetConnected(default = false, validated = false)

            // Icon is null
            assertThat(underTest.icon).isNull()
        }

    @Test
    fun icon_connectivityChanges_updates() =
        kosmos.runTest {
            // Start default and validated
            fakeConnectivityRepository.setEthernetConnected(default = true, validated = true)
            val expectedFully =
                Icon.Resource(
                    R.drawable.stat_sys_ethernet_fully,
                    ContentDescription.Resource(
                        AccessibilityContentDescriptions.ETHERNET_CONNECTION_VALUES[1]
                    ),
                )
            assertThat(underTest.icon).isEqualTo(expectedFully)

            // Change to default but not validated
            fakeConnectivityRepository.setEthernetConnected(default = true, validated = false)
            val expectedNotValidated =
                Icon.Resource(
                    R.drawable.stat_sys_ethernet,
                    ContentDescription.Resource(
                        AccessibilityContentDescriptions.ETHERNET_CONNECTION_VALUES[0]
                    ),
                )
            assertThat(underTest.icon).isEqualTo(expectedNotValidated)

            // Change to not default
            fakeConnectivityRepository.setEthernetConnected(default = false, validated = false)
            assertThat(underTest.icon).isNull()

            // Change back to default and validated
            fakeConnectivityRepository.setEthernetConnected(default = true, validated = true)
            assertThat(underTest.icon).isEqualTo(expectedFully)
        }
}
