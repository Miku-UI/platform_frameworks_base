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

package com.android.systemui.statusbar.pipeline.satellite.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.fakeMobileIconsInteractor
import com.android.systemui.statusbar.pipeline.satellite.data.repository.deviceBasedSatelliteRepository
import com.android.systemui.statusbar.pipeline.satellite.shared.model.SatelliteConnectionState
import com.android.systemui.statusbar.pipeline.wifi.data.repository.fakeWifiRepository
import com.android.systemui.statusbar.pipeline.wifi.shared.model.WifiNetworkModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceBasedSatelliteViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Kosmos.Fixture { deviceBasedSatelliteViewModel }

    @Test
    fun icon_null_satelliteNotAllowed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is not allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = false

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // THEN icon is null because we should not be showing it
            assertThat(latest).isNull()
        }

    @Test
    fun icon_null_connectedAndNotAllowed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is not allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = false

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN satellite state is Connected. (this should not ever occur, but still)
            deviceBasedSatelliteRepository.connectionState.value =
                SatelliteConnectionState.Connected

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // THEN icon is null despite the connected state
            assertThat(latest).isNull()
        }

    @Test
    fun icon_null_notAllOos() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true

            // GIVEN all icons are not OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = true
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // THEN icon is null because we have service
            assertThat(latest).isNull()
        }

    @Test
    fun icon_null_allOosAndNotAllowed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = false

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // THEN icon is null because it is not allowed
            assertThat(latest).isNull()
        }

    @Test
    fun icon_null_allOosAndConfigIsFalse() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN config for opportunistic icon is false
            deviceBasedSatelliteRepository.isOpportunisticSatelliteIconEnabled = false

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // THEN icon is null because it is not allowed
            assertThat(latest).isNull()
        }

    @Test
    fun icon_null_isEmergencyOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // Wait for delay to be completed
            advanceTimeBy(10.seconds)

            // THEN icon is set because we don't have service
            assertThat(latest).isInstanceOf(Icon::class.java)

            // GIVEN the connection is emergency only
            i1.isEmergencyOnly.value = true

            // THEN icon is null because we have emergency connection
            assertThat(latest).isNull()
        }

    @Test
    fun icon_null_apmIsEnabled() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is enabled
            airplaneModeRepository.setIsAirplaneMode(true)

            // THEN icon is null because we should not be showing it
            assertThat(latest).isNull()
        }

    @Test
    fun icon_notNull_satelliteAllowedAndAllOos() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // Wait for delay to be completed
            advanceTimeBy(10.seconds)

            // THEN icon is set because we don't have service
            assertThat(latest).isInstanceOf(Icon::class.java)
        }

    @Test
    fun icon_hysteresisWhenEnablingIcon() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // THEN icon is null because of the hysteresis
            assertThat(latest).isNull()

            // Wait for delay to be completed
            advanceTimeBy(10.seconds)

            // THEN icon is set after the delay
            assertThat(latest).isInstanceOf(Icon::class.java)

            // GIVEN apm is enabled
            airplaneModeRepository.setIsAirplaneMode(true)

            // THEN icon is null immediately
            assertThat(latest).isNull()
        }

    @Test
    fun icon_ignoresHysteresis_whenConnected() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // GIVEN satellite reports that it is Connected
            deviceBasedSatelliteRepository.connectionState.value =
                SatelliteConnectionState.Connected

            // THEN icon is non null because we are connected, despite the normal OOS icon waiting
            // 10 seconds for hysteresis
            assertThat(latest).isInstanceOf(Icon::class.java)
        }

    @Test
    fun icon_ignoresHysteresis_whenOn() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true

            // GIVEN all icons are OOS and not ntn
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false
            i1.isNonTerrestrial.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // GIVEN satellite reports that it is Connected
            deviceBasedSatelliteRepository.connectionState.value = SatelliteConnectionState.On

            // THEN icon is non null because the connection state is On, despite the normal OOS icon
            // waiting 10 seconds for hysteresis
            assertThat(latest).isInstanceOf(Icon::class.java)
        }

    @Test
    fun icon_nullWhenConnected_mobileNtnConnectionExists() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true

            // GIVEN ntn connection exists
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isNonTerrestrial.value = true

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // GIVEN satellite reports that it is Connected
            deviceBasedSatelliteRepository.connectionState.value = SatelliteConnectionState.On

            // THEN icon is null because despite being connected, the mobile stack is reporting a
            // nonTerrestrial network, and therefore will have its own icon
            assertThat(latest).isNull()
        }

    @Test
    fun icon_satelliteIsProvisioned() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // GIVEN satellite is not provisioned
            deviceBasedSatelliteRepository.isSatelliteProvisioned.value = false

            // THEN icon is null because the device is not provisioned
            assertThat(latest).isNull()

            // GIVEN satellite becomes provisioned
            deviceBasedSatelliteRepository.isSatelliteProvisioned.value = true

            // Wait for delay to be completed
            advanceTimeBy(10.seconds)

            // THEN icon is null because the device is not provisioned
            assertThat(latest).isInstanceOf(Icon::class.java)
        }

    @Test
    fun icon_wifiIsActive() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icon)

            // GIVEN satellite is allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // GIVEN satellite is provisioned
            deviceBasedSatelliteRepository.isSatelliteProvisioned.value = true

            // GIVEN wifi network is active
            fakeWifiRepository.setWifiNetwork(WifiNetworkModel.Active.of(level = 1))

            // THEN icon is null because the device is connected to wifi
            assertThat(latest).isNull()

            // GIVEN device loses wifi connection
            fakeWifiRepository.setWifiNetwork(WifiNetworkModel.Invalid("test"))

            // Wait for delay to be completed
            advanceTimeBy(10.seconds)

            // THEN icon is set because the device lost wifi connection
            assertThat(latest).isInstanceOf(Icon::class.java)
        }

    @Test
    fun carrierText_nullWhenShouldNotShow_satelliteNotAllowed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // GIVEN satellite is not allowed
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = false

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // THEN carrier text is null because we should not be showing it
            assertThat(latest).isNull()
        }

    @Test
    fun carrierText_null_notAllOos() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // GIVEN satellite is allowed + off
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
            deviceBasedSatelliteRepository.connectionState.value = SatelliteConnectionState.Off

            // GIVEN all icons are not OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = true
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // THEN carrier text is null because we have service
            assertThat(latest).isNull()
        }

    @Test
    fun carrierText_notNull_notAllOos_butConnected() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // GIVEN satellite is allowed + connected
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
            deviceBasedSatelliteRepository.connectionState.value =
                SatelliteConnectionState.Connected

            // GIVEN all icons are not OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = true
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // THEN carrier text is not null, because it is connected
            // This case should never happen, but let's test it anyway
            assertThat(latest).isNotNull()
        }

    @Test
    fun carrierText_nullWhenShouldNotShow_apmIsEnabled() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // GIVEN satellite is allowed + connected
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
            deviceBasedSatelliteRepository.connectionState.value =
                SatelliteConnectionState.Connected

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is enabled
            airplaneModeRepository.setIsAirplaneMode(true)

            // THEN carrier text is null because we should not be showing it
            assertThat(latest).isNull()
        }

    @Test
    fun carrierText_satelliteIsOn() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // GIVEN satellite is allowed + connected
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
            deviceBasedSatelliteRepository.connectionState.value =
                SatelliteConnectionState.Connected

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // Wait for delay to be completed
            //            advanceTimeBy(10.seconds)

            // THEN carrier text is set because we don't have service
            assertThat(latest).isNotNull()
        }

    @Test
    fun carrierText_noHysteresisWhenEnablingText_connected() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // GIVEN satellite is allowed + connected
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
            deviceBasedSatelliteRepository.connectionState.value =
                SatelliteConnectionState.Connected

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // THEN carrier text is not null because we skip hysteresis when connected
            assertThat(latest).isNotNull()
        }

    @Test
    fun carrierText_deviceIsProvisioned() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // GIVEN satellite is allowed + connected
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
            deviceBasedSatelliteRepository.connectionState.value =
                SatelliteConnectionState.Connected

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // GIVEN satellite is not provisioned
            deviceBasedSatelliteRepository.isSatelliteProvisioned.value = false

            // THEN carrier text is null because the device is not provisioned
            assertThat(latest).isNull()

            // GIVEN satellite becomes provisioned
            deviceBasedSatelliteRepository.isSatelliteProvisioned.value = true

            // Wait for delay to be completed
            advanceTimeBy(10.seconds)

            // THEN carrier text is null because the device is not provisioned
            assertThat(latest).isNotNull()
        }

    @Test
    fun carrierText_wifiIsActive() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // GIVEN satellite is allowed + connected
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
            deviceBasedSatelliteRepository.connectionState.value =
                SatelliteConnectionState.Connected

            // GIVEN all icons are OOS
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false

            // GIVEN apm is disabled
            airplaneModeRepository.setIsAirplaneMode(false)

            // GIVEN satellite is provisioned
            deviceBasedSatelliteRepository.isSatelliteProvisioned.value = true

            // GIVEN wifi network is active
            fakeWifiRepository.setWifiNetwork(WifiNetworkModel.Active.of(level = 1))

            // THEN carrier text is null because the device is connected to wifi
            assertThat(latest).isNull()

            // GIVEN device loses wifi connection
            fakeWifiRepository.setWifiNetwork(WifiNetworkModel.Invalid("test"))

            // Wait for delay to be completed
            advanceTimeBy(10.seconds)

            // THEN carrier text is set because the device lost wifi connection
            assertThat(latest).isNotNull()
        }

    @Test
    fun carrierText_connectionStateUnknown_usesEmergencyOnlyText() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // Set up the conditions for satellite to be enabled
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false
            airplaneModeRepository.setIsAirplaneMode(false)

            deviceBasedSatelliteRepository.connectionState.value = SatelliteConnectionState.Unknown

            // Wait for delay to be completed
            advanceTimeBy(10.seconds)

            assertThat(latest)
                .isEqualTo(context.getString(R.string.satellite_emergency_only_carrier_text))
        }

    @Test
    fun carrierText_connectionStateOff_usesEmergencyOnlyText() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // Set up the conditions for satellite to be enabled
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false
            airplaneModeRepository.setIsAirplaneMode(false)

            deviceBasedSatelliteRepository.connectionState.value = SatelliteConnectionState.Off

            // Wait for delay to be completed
            advanceTimeBy(10.seconds)

            assertThat(latest)
                .isEqualTo(context.getString(R.string.satellite_emergency_only_carrier_text))
        }

    @Test
    fun carrierText_connectionStateOn_notConnectedString() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // Set up the conditions for satellite to be enabled
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false
            airplaneModeRepository.setIsAirplaneMode(false)

            deviceBasedSatelliteRepository.connectionState.value = SatelliteConnectionState.On

            // Wait for delay to be completed
            advanceTimeBy(10.seconds)

            assertThat(latest)
                .isEqualTo(context.getString(R.string.satellite_connected_carrier_text))
        }

    @Test
    fun carrierText_connectionStateConnected_connectedString() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.carrierText)

            // Set up the conditions for satellite to be enabled
            deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
            val i1 = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
            i1.isInService.value = false
            i1.isEmergencyOnly.value = false
            airplaneModeRepository.setIsAirplaneMode(false)

            deviceBasedSatelliteRepository.connectionState.value =
                SatelliteConnectionState.Connected

            // Wait for delay to be completed
            advanceTimeBy(10.seconds)

            assertThat(latest)
                .isEqualTo(context.getString(R.string.satellite_connected_carrier_text))
        }
}
