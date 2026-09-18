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

package com.android.systemui.statusbar.pipeline.shared.ui.viewmodel

import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.text.Html
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.AccessibilityContentDescriptions.WIFI_OTHER_DEVICE_CONNECTION
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.common.shared.model.Text
import com.android.systemui.common.shared.model.Text.Companion.loadText
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.FakeFeatureFlagsClassic
import com.android.systemui.flags.Flags
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.qs.tileimpl.QSTileImpl.ResourceIcon
import com.android.systemui.res.R
import com.android.systemui.statusbar.connectivity.WifiIcons
import com.android.systemui.statusbar.connectivity.ui.MobileContextProvider
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.airplane.data.repository.fake
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.airplaneModeInteractor
import com.android.systemui.statusbar.pipeline.ethernet.domain.EthernetInteractor
import com.android.systemui.statusbar.pipeline.mobile.NewSatelliteIcon
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionsRepository
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractor
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractorImpl
import com.android.systemui.statusbar.pipeline.mobile.util.FakeMobileMappingsProxy
import com.android.systemui.statusbar.pipeline.shared.data.model.DefaultConnectionModel
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.statusbar.pipeline.shared.ui.model.InternetTileModel
import com.android.systemui.statusbar.pipeline.shared.ui.model.SignalIcon
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.InternetTileViewModel.Companion.NOT_CONNECTED_NETWORKS_UNAVAILABLE
import com.android.systemui.statusbar.pipeline.wifi.data.repository.FakeWifiRepository
import com.android.systemui.statusbar.pipeline.wifi.domain.interactor.WifiInteractorImpl
import com.android.systemui.statusbar.pipeline.wifi.shared.model.WifiNetworkModel
import com.android.systemui.statusbar.pipeline.wifi.shared.model.WifiScanEntry
import com.android.systemui.statusbar.pipeline.wifi.ui.model.WifiIcon
import com.android.systemui.statusbar.policy.data.repository.FakeUserSetupRepository
import com.android.systemui.testKosmos
import com.android.systemui.util.CarrierConfigTracker
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class InternetTileViewModelTest : SysuiTestCase() {
    private val testUser = UserHandle.of(1)
    private val kosmos = testKosmos()

    private lateinit var underTest: InternetTileViewModel
    private lateinit var mobileIconsInteractor: MobileIconsInteractor

    private val airplaneModeRepository = kosmos.airplaneModeRepository.fake
    private val airplaneModeInteractor = kosmos.airplaneModeInteractor
    private val connectivityRepository = kosmos.connectivityRepository.fake
    private val ethernetInteractor = EthernetInteractor(connectivityRepository)
    private val wifiRepository = FakeWifiRepository()
    private val userSetupRepo = FakeUserSetupRepository()
    private val testScope = TestScope()
    private val wifiInteractor =
        WifiInteractorImpl(connectivityRepository, wifiRepository, testScope.backgroundScope)

    private val tableLogBuffer = logcatTableLogBuffer(kosmos, "InternetTileViewModelTest")
    private val carrierConfigTracker: CarrierConfigTracker = mock()

    private val mobileConnectionsRepository =
        FakeMobileConnectionsRepository(FakeMobileMappingsProxy(), tableLogBuffer)
    private val mobileConnectionRepository =
        FakeMobileConnectionRepository(SUB_1_ID, tableLogBuffer)

    private val mobileContextProvider = mock<MobileContextProvider>()

    private val flags =
        FakeFeatureFlagsClassic().also {
            it.set(Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS, true)
        }

    private val internet = context.getString(R.string.quick_settings_internet_label)

    @Before
    fun setUp() {
        whenever(mobileContextProvider.getMobileContextForSub(any(), any())).thenReturn(context)

        mobileConnectionRepository.apply {
            setNetworkTypeKey(mobileConnectionsRepository.GSM_KEY)
            isInService.value = true
            dataConnectionState.value = DataConnectionState.Connected
            dataEnabled.value = true
        }

        mobileConnectionsRepository.apply {
            activeMobileDataRepository.value = mobileConnectionRepository
            activeMobileDataSubscriptionId.value = SUB_1_ID
            setMobileConnectionRepositoryMap(mapOf(SUB_1_ID to mobileConnectionRepository))
        }

        mobileIconsInteractor =
            MobileIconsInteractorImpl(
                mobileConnectionsRepository,
                carrierConfigTracker,
                tableLogBuffer,
                connectivityRepository,
                userSetupRepo,
                testScope.backgroundScope,
                context,
                flags,
            )

        underTest =
            InternetTileViewModel(
                airplaneModeInteractor,
                connectivityRepository,
                ethernetInteractor,
                mobileIconsInteractor,
                mobileContextProvider,
                wifiInteractor,
                context,
                testScope.backgroundScope,
            )
    }

    @Test
    fun noDefault_noNetworksAvailable() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            connectivityRepository.resolvedConnections.value = DefaultConnectionModel()

            assertThat(latest?.secondaryLabel)
                .isEqualTo(Text.Resource(R.string.quick_settings_networks_unavailable))
            assertThat(latest?.iconId).isEqualTo(R.drawable.ic_qs_no_internet_unavailable)
        }

    @Test
    fun noDefault_networksAvailable() =
        testScope.runTest {
            // TODO: support [WifiInteractor.areNetworksAvailable]
        }

    @Test
    fun wifiDefaultAndActive() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            val networkModel = WifiNetworkModel.Active.of(level = 4, ssid = "test ssid")
            val wifiIcon =
                WifiIcon.fromModel(model = networkModel, context = context, showHotspotInfo = false)
                    as WifiIcon.Visible

            connectivityRepository.setWifiConnected()
            wifiRepository.setIsWifiDefault(true)
            wifiRepository.setWifiNetwork(networkModel)

            assertThat(latest?.secondaryTitle).isEqualTo("test ssid")
            assertThat(latest?.secondaryLabel).isNull()
            assertThat(latest?.icon).isEqualTo(ResourceIcon.get(WifiIcons.WIFI_FULL_ICONS[4]))
            assertThat(latest?.iconId).isNull()
            assertThat(latest?.contentDescription.loadContentDescription(context))
                .isEqualTo("$internet,test ssid")
            val expectedSd = wifiIcon.contentDescription
            assertThat(latest?.stateDescription).isEqualTo(expectedSd)
        }

    @Test
    fun wifiDefaultAndActive_hotspotNone() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            val networkModel =
                WifiNetworkModel.Active.of(
                    level = 4,
                    ssid = "test ssid",
                    hotspotDeviceType = WifiNetworkModel.HotspotDeviceType.NONE,
                )

            connectivityRepository.setWifiConnected()
            wifiRepository.setIsWifiDefault(true)
            wifiRepository.setWifiNetwork(networkModel)

            assertThat(latest?.icon).isEqualTo(ResourceIcon.get(WifiIcons.WIFI_FULL_ICONS[4]))
            assertThat(latest?.stateDescription.loadContentDescription(context))
                .doesNotContain(context.getString(WIFI_OTHER_DEVICE_CONNECTION))
        }

    @Test
    fun wifiDefaultAndActive_hotspotTablet() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            setWifiNetworkWithHotspot(WifiNetworkModel.HotspotDeviceType.TABLET)

            assertThat(latest?.icon)
                .isEqualTo(ResourceIcon.get(com.android.settingslib.R.drawable.ic_hotspot_tablet))
            assertThat(latest?.stateDescription.loadContentDescription(context))
                .isEqualTo(context.getString(WIFI_OTHER_DEVICE_CONNECTION))
        }

    @Test
    fun wifiDefaultAndActive_hotspotLaptop() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            setWifiNetworkWithHotspot(WifiNetworkModel.HotspotDeviceType.LAPTOP)

            assertThat(latest?.icon)
                .isEqualTo(ResourceIcon.get(com.android.settingslib.R.drawable.ic_hotspot_laptop))
            assertThat(latest?.stateDescription.loadContentDescription(context))
                .isEqualTo(context.getString(WIFI_OTHER_DEVICE_CONNECTION))
        }

    @Test
    fun wifiDefaultAndActive_hotspotWatch() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            setWifiNetworkWithHotspot(WifiNetworkModel.HotspotDeviceType.WATCH)

            assertThat(latest?.icon)
                .isEqualTo(ResourceIcon.get(com.android.settingslib.R.drawable.ic_hotspot_watch))
            assertThat(latest?.stateDescription.loadContentDescription(context))
                .isEqualTo(context.getString(WIFI_OTHER_DEVICE_CONNECTION))
        }

    @Test
    fun wifiDefaultAndActive_hotspotAuto() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            setWifiNetworkWithHotspot(WifiNetworkModel.HotspotDeviceType.AUTO)

            assertThat(latest?.icon)
                .isEqualTo(ResourceIcon.get(com.android.settingslib.R.drawable.ic_hotspot_auto))
            assertThat(latest?.stateDescription.loadContentDescription(context))
                .isEqualTo(context.getString(WIFI_OTHER_DEVICE_CONNECTION))
        }

    @Test
    fun wifiDefaultAndActive_hotspotPhone() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            setWifiNetworkWithHotspot(WifiNetworkModel.HotspotDeviceType.PHONE)

            assertThat(latest?.icon)
                .isEqualTo(ResourceIcon.get(com.android.settingslib.R.drawable.ic_hotspot_phone))
            assertThat(latest?.stateDescription.loadContentDescription(context))
                .isEqualTo(context.getString(WIFI_OTHER_DEVICE_CONNECTION))
        }

    @Test
    fun wifiDefaultAndActive_hotspotUnknown() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            setWifiNetworkWithHotspot(WifiNetworkModel.HotspotDeviceType.UNKNOWN)

            assertThat(latest?.icon)
                .isEqualTo(ResourceIcon.get(com.android.settingslib.R.drawable.ic_hotspot_phone))
            assertThat(latest?.stateDescription.loadContentDescription(context))
                .isEqualTo(context.getString(WIFI_OTHER_DEVICE_CONNECTION))
        }

    @Test
    fun wifiDefaultAndActive_hotspotInvalid() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            setWifiNetworkWithHotspot(WifiNetworkModel.HotspotDeviceType.INVALID)

            assertThat(latest?.icon)
                .isEqualTo(ResourceIcon.get(com.android.settingslib.R.drawable.ic_hotspot_phone))
            assertThat(latest?.stateDescription.loadContentDescription(context))
                .isEqualTo(context.getString(WIFI_OTHER_DEVICE_CONNECTION))
        }

    @Test
    fun wifiDefaultAndNotActive_noNetworksAvailable() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            val networkModel = WifiNetworkModel.Inactive()

            connectivityRepository.setWifiConnected(validated = false)
            wifiRepository.setIsWifiDefault(true)
            wifiRepository.setWifiNetwork(networkModel)
            wifiRepository.wifiScanResults.value = emptyList()

            assertThat(latest).isEqualTo(NOT_CONNECTED_NETWORKS_UNAVAILABLE)
        }

    @Test
    fun wifiDefaultAndNotActive_networksAvailable() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            val networkModel = WifiNetworkModel.Inactive()

            connectivityRepository.setWifiConnected(validated = false)
            wifiRepository.setIsWifiDefault(true)
            wifiRepository.setWifiNetwork(networkModel)
            wifiRepository.wifiScanResults.value = listOf(WifiScanEntry("test 1"))

            assertThat(latest?.secondaryLabel).isNull()
            assertThat(latest?.secondaryTitle)
                .isEqualTo(context.getString(R.string.quick_settings_networks_available))
            assertThat(latest?.icon).isNull()
            assertThat(latest?.iconId).isEqualTo(R.drawable.ic_qs_no_internet_available)
            assertThat(latest?.stateDescription).isNull()
            val expectedCd =
                "$internet,${context.getString(R.string.quick_settings_networks_available)}"
            assertThat(latest?.contentDescription.loadContentDescription(context))
                .isEqualTo(expectedCd)
        }

    @Test
    fun mobileDefault_usesNetworkNameAndIcon() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)

            connectivityRepository.setMobileConnected()
            mobileConnectionsRepository.mobileIsDefault.value = true
            mobileConnectionRepository.apply {
                setAllLevels(3)
                setAllRoaming(false)
                networkName.value = NetworkNameModel.Default("test network")
            }

            assertThat(latest?.secondaryTitle.toString()).contains("test network")
            assertThat(latest?.secondaryLabel).isNull()
            assertThat(latest?.icon).isInstanceOf(SignalIcon::class.java)
            assertThat(latest?.iconId).isNull()
            assertThat(latest?.stateDescription.loadContentDescription(context))
                .isEqualTo(latest?.secondaryTitle.toString())
            assertThat(latest?.contentDescription.loadContentDescription(context))
                .isEqualTo(internet)
        }

    @Test
    fun ethernetDefault_validated_matchesInteractor() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)
            val ethernetIcon by collectLastValue(ethernetInteractor.icon)

            connectivityRepository.setEthernetConnected(default = true, validated = true)

            assertThat(latest?.secondaryLabel.loadText(context))
                .isEqualTo(ethernetIcon!!.contentDescription.loadContentDescription(context))
            assertThat(latest?.secondaryTitle).isNull()
            assertThat(latest?.iconId).isEqualTo(R.drawable.stat_sys_ethernet_fully)
            assertThat(latest?.icon).isNull()
            assertThat(latest?.stateDescription).isNull()
            assertThat(latest?.contentDescription.loadContentDescription(context))
                .isEqualTo(latest?.secondaryLabel.loadText(context))
        }

    @Test
    fun ethernetDefault_notValidated_matchesInteractor() =
        testScope.runTest {
            val latest by collectLastValue(underTest.tileModel)
            val ethernetIcon by collectLastValue(ethernetInteractor.icon)

            connectivityRepository.setEthernetConnected(default = true, validated = false)

            assertThat(latest?.secondaryLabel.loadText(context))
                .isEqualTo(ethernetIcon!!.contentDescription.loadContentDescription(context))
            assertThat(latest?.secondaryTitle).isNull()
            assertThat(latest?.iconId).isEqualTo(R.drawable.stat_sys_ethernet)
            assertThat(latest?.icon).isNull()
            assertThat(latest?.stateDescription).isNull()
            assertThat(latest?.contentDescription.loadContentDescription(context))
                .isEqualTo(latest?.secondaryLabel.loadText(context))
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun activeConnection_satellite_showsSatelliteText() =
        testScope.runTest {
            val output by collectLastValue(underTest.tileModel)
            val mobileConnectionRepo =
                FakeMobileConnectionRepository(SUB_2_ID, logcatTableLogBuffer(kosmos))
            mobileConnectionsRepository.setMobileConnectionRepositoryMap(
                mapOf(SUB_2_ID to mobileConnectionRepo)
            )
            mobileConnectionsRepository.setActiveMobileDataSubscriptionId(SUB_2_ID)
            val networkName = "test network"
            mobileConnectionRepo.networkName.value =
                NetworkNameModel.SubscriptionDerived(networkName)
            mobileConnectionRepo.dataConnectionState.value = DataConnectionState.Connected
            mobileConnectionRepo.isNonTerrestrial.value = true

            connectivityRepository.setMobileConnected(default = true, validated = true)

            val expected =
                Html.fromHtml(
                    context.getString(
                        R.string.mobile_carrier_text_format,
                        networkName,
                        context.getString(com.android.internal.R.string.satellite_indicator),
                    ),
                    0,
                )
            assertThat(output).isInstanceOf(InternetTileModel.Active::class.java)
            val activeModel = output as InternetTileModel.Active
            assertThat(activeModel.secondaryTitle.toString()).isEqualTo(expected.toString())
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun satellite_viaConstrainedFlow_showsSatelliteIcon() =
        testScope.runTest {
            // Collect the final UI model from the ViewModel.
            val latest by collectLastValue(underTest.tileModel)

            // Populate the repository with a valid default mobile connection via the constrained
            // flow.
            connectivityRepository.resolvedConnections.value =
                DefaultConnectionModel(
                    mobile = DefaultConnectionModel.Mobile(isDefault = true),
                    isValidated = true,
                )

            // Define the network characteristics (Name and Signal Level).
            val networkName = "Satellite Network"
            val satelliteLevel = 4

            // Configure the Mobile Connections Repository to reflect an active Satellite
            // connection.
            mobileConnectionsRepository.mobileIsDefault.value = true
            mobileConnectionsRepository.activeMobileDataSubscriptionId.value = SUB_1_ID

            mobileConnectionRepository.apply {
                isInService.value = true
                dataConnectionState.value = DataConnectionState.Connected
                isNonTerrestrial.value = true
                this.networkName.value = NetworkNameModel.Default(networkName)
                this.satelliteLevel.value = satelliteLevel
            }

            // Assertions on the resulting UI Model.
            // It should be an 'Active' state model.
            assertThat(latest).isInstanceOf(InternetTileModel.Active::class.java)
            val activeModel = latest as InternetTileModel.Active

            // The label should contain the satellite network name.
            assertThat(activeModel.secondaryTitle.toString()).contains(networkName)

            // The icon type should be a SignalIcon (which supports satellite levels).
            assertThat(activeModel.icon).isInstanceOf(SignalIcon::class.java)
        }

    private fun setWifiNetworkWithHotspot(hotspot: WifiNetworkModel.HotspotDeviceType) {
        val networkModel =
            WifiNetworkModel.Active.of(level = 4, ssid = "test ssid", hotspotDeviceType = hotspot)

        connectivityRepository.setWifiConnected()
        wifiRepository.setIsWifiDefault(true)
        wifiRepository.setWifiNetwork(networkModel)
    }

    companion object {
        const val SUB_1_ID = 1
        const val SUB_2_ID = 2
    }
}
