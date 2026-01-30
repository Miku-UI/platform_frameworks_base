/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.mobile.data.repository.prod

import android.telephony.CarrierConfigManager.KEY_INFLATE_SIGNAL_STRENGTH_BOOL
import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.backgroundCoroutineContext
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.model.SystemUiCarrierConfig
import com.android.systemui.statusbar.pipeline.mobile.data.model.testCarrierConfig
import com.android.systemui.statusbar.pipeline.mobile.data.model.testCarrierConfigWithOverride
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionRepository
import com.android.systemui.statusbar.pipeline.shared.data.model.DataActivityModel
import com.android.systemui.statusbar.pipeline.wifi.data.repository.fake
import com.android.systemui.statusbar.pipeline.wifi.data.repository.wifiRepository
import com.android.systemui.statusbar.pipeline.wifi.shared.model.WifiNetworkModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class CarrierMergedConnectionRepositoryTest : CarrierMergedConnectionRepositoryTestBase() {
    override fun recreateRepo() =
        CarrierMergedConnectionRepository(
            SUB_ID,
            logger,
            telephonyManager,
            systemUiCarrierConfig,
            bgContext = kosmos.backgroundCoroutineContext,
            scope = kosmos.testScope.backgroundScope,
            kosmos.wifiRepository.fake,
        )
}

abstract class CarrierMergedConnectionRepositoryTestBase : SysuiTestCase() {

    protected val kosmos = testKosmos().useUnconfinedTestDispatcher()

    protected lateinit var underTest: MobileConnectionRepository

    protected val logger = logcatTableLogBuffer(kosmos, "CarrierMergedConnectionRepositoryTest")
    protected val telephonyManager = mock<TelephonyManager>()
    protected val systemUiCarrierConfig = SystemUiCarrierConfig(SUB_ID, testCarrierConfig())

    abstract fun recreateRepo(): MobileConnectionRepository

    @Before
    fun setUp() {
        whenever(telephonyManager.subscriptionId).thenReturn(SUB_ID)
        whenever(telephonyManager.simOperatorName).thenReturn("")

        underTest = recreateRepo()
    }

    @Test
    fun inactiveWifi_isDefault() =
        kosmos.runTest {
            val latestConnState by collectLastValue(underTest.dataConnectionState)
            val latestNetType by collectLastValue(underTest.resolvedNetworkType)

            wifiRepository.fake.setWifiNetwork(WifiNetworkModel.Inactive())

            assertThat(latestConnState).isEqualTo(DataConnectionState.Disconnected)
            assertThat(latestNetType).isNotEqualTo(ResolvedNetworkType.CarrierMergedNetworkType)
        }

    @Test
    fun activeWifi_isDefault() =
        kosmos.runTest {
            val latestConnState by collectLastValue(underTest.dataConnectionState)
            val latestNetType by collectLastValue(underTest.resolvedNetworkType)

            wifiRepository.fake.setWifiNetwork(WifiNetworkModel.Active.of(level = 1))

            assertThat(latestConnState).isEqualTo(DataConnectionState.Disconnected)
            assertThat(latestNetType).isNotEqualTo(ResolvedNetworkType.CarrierMergedNetworkType)
        }

    @Test
    fun carrierMergedWifi_isValidAndFieldsComeFromWifiNetwork() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.primaryLevel)

            wifiRepository.fake.setIsWifiEnabled(true)
            wifiRepository.fake.setIsWifiDefault(true)

            wifiRepository.fake.setWifiNetwork(
                WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID, level = 3)
            )

            assertThat(latest).isEqualTo(3)
        }

    @Test
    fun activity_comesFromWifiActivity() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.dataActivityDirection)

            wifiRepository.fake.setIsWifiEnabled(true)
            wifiRepository.fake.setIsWifiDefault(true)
            wifiRepository.fake.setWifiNetwork(
                WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID, level = 3)
            )
            wifiRepository.fake.setWifiActivity(
                DataActivityModel(hasActivityIn = true, hasActivityOut = false)
            )

            assertThat(latest!!.hasActivityIn).isTrue()
            assertThat(latest!!.hasActivityOut).isFalse()

            wifiRepository.fake.setWifiActivity(
                DataActivityModel(hasActivityIn = false, hasActivityOut = true)
            )

            assertThat(latest!!.hasActivityIn).isFalse()
            assertThat(latest!!.hasActivityOut).isTrue()
        }

    @Test
    fun carrierMergedWifi_wrongSubId_isDefault() =
        kosmos.runTest {
            val latestLevel by collectLastValue(underTest.primaryLevel)
            val latestType by collectLastValue(underTest.resolvedNetworkType)

            wifiRepository.fake.setWifiNetwork(
                WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID + 10, level = 3)
            )

            assertThat(latestLevel).isNotEqualTo(3)
            assertThat(latestType).isNotEqualTo(ResolvedNetworkType.CarrierMergedNetworkType)
        }

    // This scenario likely isn't possible, but write a test for it anyway
    @Test
    fun carrierMergedButNotEnabled_isDefault() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.primaryLevel)

            wifiRepository.fake.setWifiNetwork(
                WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID, level = 3)
            )
            wifiRepository.fake.setIsWifiEnabled(false)

            assertThat(latest).isNotEqualTo(3)
        }

    // This scenario likely isn't possible, but write a test for it anyway
    @Test
    fun carrierMergedButWifiNotDefault_isDefault() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.primaryLevel)

            wifiRepository.fake.setWifiNetwork(
                WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID, level = 3)
            )
            wifiRepository.fake.setIsWifiDefault(false)

            assertThat(latest).isNotEqualTo(3)
        }

    @Test
    fun numberOfLevels_comesFromCarrierMerged() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.numberOfLevels)

            wifiRepository.fake.setWifiNetwork(
                WifiNetworkModel.CarrierMerged.of(
                    subscriptionId = SUB_ID,
                    level = 1,
                    numberOfLevels = 6,
                )
            )

            assertThat(latest).isEqualTo(6)
        }

    @Test
    fun numberOfLevels_comesFromCarrierMerged_andInflated() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.numberOfLevels)

            wifiRepository.fake.setWifiNetwork(
                WifiNetworkModel.CarrierMerged.of(
                    subscriptionId = SUB_ID,
                    level = 1,
                    numberOfLevels = 6,
                )
            )
            systemUiCarrierConfig.processNewCarrierConfig(
                testCarrierConfigWithOverride(KEY_INFLATE_SIGNAL_STRENGTH_BOOL, true)
            )

            assertThat(latest).isEqualTo(7)
        }

    @Test
    fun inflateSignalStrength_usesCarrierConfig() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.inflateSignalStrength)

            assertThat(latest).isEqualTo(false)

            systemUiCarrierConfig.processNewCarrierConfig(
                testCarrierConfigWithOverride(KEY_INFLATE_SIGNAL_STRENGTH_BOOL, true)
            )

            assertThat(latest).isEqualTo(true)

            systemUiCarrierConfig.processNewCarrierConfig(
                testCarrierConfigWithOverride(KEY_INFLATE_SIGNAL_STRENGTH_BOOL, false)
            )

            assertThat(latest).isEqualTo(false)
        }

    @Test
    fun dataEnabled_matchesWifiEnabled() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.dataEnabled)

            wifiRepository.fake.setIsWifiEnabled(true)
            assertThat(latest).isTrue()

            wifiRepository.fake.setIsWifiEnabled(false)
            assertThat(latest).isFalse()
        }

    @Test
    fun cdmaRoaming_alwaysFalse() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.cdmaRoaming)

            assertThat(latest).isFalse()
        }

    @Test
    fun networkName_usesSimOperatorNameAsInitial() =
        kosmos.runTest {
            whenever(telephonyManager.simOperatorName).thenReturn("Test SIM name")
            underTest = recreateRepo()

            val latest by collectLastValue(underTest.networkName)

            assertThat(latest).isEqualTo(NetworkNameModel.SimDerived("Test SIM name"))
        }

    @Test
    fun networkName_updatesOnNetworkUpdate() =
        kosmos.runTest {
            whenever(telephonyManager.simOperatorName).thenReturn("Test SIM name")
            underTest = recreateRepo()

            wifiRepository.fake.setIsWifiEnabled(true)
            wifiRepository.fake.setIsWifiDefault(true)

            val latest by collectLastValue(underTest.networkName)

            assertThat(latest).isEqualTo(NetworkNameModel.SimDerived("Test SIM name"))

            whenever(telephonyManager.simOperatorName).thenReturn("New SIM name")
            wifiRepository.fake.setWifiNetwork(
                WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID, level = 3)
            )

            assertThat(latest).isEqualTo(NetworkNameModel.SimDerived("New SIM name"))
        }

    @Test
    fun isAllowedDuringAirplaneMode_alwaysTrue() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isAllowedDuringAirplaneMode)

            assertThat(latest).isTrue()
        }

    companion object {
        const val SUB_ID = 123
    }
}
