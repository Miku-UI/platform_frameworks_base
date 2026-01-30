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

package com.android.systemui.statusbar.systemstatusicons.wifi.ui.viewmodel

import android.content.testableContext
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.pipeline.shared.data.model.ConnectivitySlot
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.statusbar.pipeline.wifi.data.repository.fakeWifiRepository
import com.android.systemui.statusbar.pipeline.wifi.shared.model.WifiNetworkModel
import com.android.systemui.statusbar.pipeline.wifi.ui.model.WifiIcon as PipelineWifiIcon
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@EnableFlags(SystemStatusIconsInCompose.FLAG_NAME, NewStatusBarIcons.FLAG_NAME)
@SmallTest
@RunWith(AndroidJUnit4::class)
class WifiIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest = kosmos.wifiIconViewModelFactory.create(kosmos.testableContext)

    @Before
    fun setUp() {
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun visible_isFalse_byDefault() = kosmos.runTest { assertThat(underTest.visible).isFalse() }

    @Test
    fun visible_wifiIsEnabledAndNotHidden_isTrue() =
        kosmos.runTest {
            fakeWifiRepository.setIsWifiEnabled(true)
            connectivityRepository.fake.setForceHiddenIcons(setOf())

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_wifiIsDisabled_isFalse() =
        kosmos.runTest {
            fakeWifiRepository.setIsWifiEnabled(false)

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun visible_forceHidden_isFalse() =
        kosmos.runTest {
            fakeWifiRepository.setIsWifiEnabled(true)
            connectivityRepository.fake.setForceHiddenIcons(setOf(ConnectivitySlot.WIFI))

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun visible_networkIsCarrierMerged_isFalse() =
        kosmos.runTest {
            fakeWifiRepository.setIsWifiEnabled(true)
            fakeWifiRepository.setWifiNetwork(
                WifiNetworkModel.CarrierMerged.of(
                    subscriptionId = 1212,
                    level = 4,
                    numberOfLevels = 4,
                )
            )

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun visible_wifiStateChanges_flips() =
        kosmos.runTest {
            fakeWifiRepository.setIsWifiEnabled(false)
            assertThat(underTest.visible).isFalse()

            fakeWifiRepository.setIsWifiEnabled(true)

            assertThat(underTest.visible).isTrue()

            fakeWifiRepository.setIsWifiEnabled(false)

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun visible_forceHiddenChanges_flips() =
        kosmos.runTest {
            fakeWifiRepository.setIsWifiEnabled(true)

            connectivityRepository.fake.setForceHiddenIcons(setOf())
            assertThat(underTest.visible).isTrue()

            connectivityRepository.fake.setForceHiddenIcons(setOf(ConnectivitySlot.WIFI))

            assertThat(underTest.visible).isFalse()

            connectivityRepository.fake.setForceHiddenIcons(setOf())

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun icon_visible_isCorrect() =
        kosmos.runTest {
            fakeWifiRepository.setIsWifiEnabled(true)
            val testNetwork =
                WifiNetworkModel.Active.of(isValidated = true, level = 4, ssid = "TestWifi")
            fakeWifiRepository.setWifiNetwork(testNetwork)
            connectivityRepository.fake.setWifiConnected()

            val expectedPipelineIcon =
                PipelineWifiIcon.fromModel(testNetwork, context, showHotspotInfo = false)
                    as PipelineWifiIcon.Visible
            assertThat(underTest.icon).isEqualTo(expectedPipelineIcon.icon)
        }

    @Test fun icon_notVisible_isNull() = kosmos.runTest { assertThat(underTest.icon).isNull() }

    @Test
    fun icon_inactiveNetwork_isShownAsNoNetwork() =
        kosmos.runTest {
            fakeWifiRepository.setIsWifiEnabled(true)
            fakeWifiRepository.setWifiNetwork(WifiNetworkModel.Inactive())
            connectivityRepository.fake.setWifiConnected()

            val expectedPipelineIcon =
                PipelineWifiIcon.fromModel(
                    WifiNetworkModel.Inactive(),
                    context,
                    showHotspotInfo = false,
                ) as PipelineWifiIcon.Visible
            assertThat(underTest.icon).isEqualTo(expectedPipelineIcon.icon)
        }
}
