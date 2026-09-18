/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.statusbar.systemstatusicons.ui.viewmodel

import android.content.testableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.systemstatusicons.domain.interactor.SystemStatusIconBlocklistInteractor
import com.android.systemui.statusbar.systemstatusicons.data.repository.statusBarConfigIconSlotNames
import com.android.systemui.statusbar.systemstatusicons.flags.EnableSystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.hideAirplaneMode
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showAirplaneMode
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showBluetooth
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showConnectedDisplay
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showDataSaver
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showEthernet
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showExternalIcon
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showHeadset
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showHotspot
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showManagedProfile
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showMute
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showNextAlarm
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showSatelliteIcon
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showTty
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showVibrate
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showVpn
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showWifi
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModelHelper.showZenMode
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@EnableSystemStatusIconsInCompose
@SmallTest
@RunWith(AndroidJUnit4::class)
class SystemStatusIconsViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val iconBlockList: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    private val mFakeSystemStatusIconBlocklistInteractor =
        object : SystemStatusIconBlocklistInteractor {
            override val blockedIconSlots: Flow<Set<String>> = iconBlockList.map { it.toSet() }
        }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            kosmos.systemStatusIconsViewModelFactory
                .create(kosmos.testableContext, mFakeSystemStatusIconBlocklistInteractor)
                .apply { activateIn(kosmos.testScope) }
        }

    private lateinit var slotAirplane: String
    private lateinit var slotBluetooth: String
    private lateinit var slotConnectedDisplay: String
    private lateinit var slotDataSaver: String
    private lateinit var slotDeviceBasedSatellite: String
    private lateinit var slotEthernet: String
    private lateinit var slotHeadset: String
    private lateinit var slotHotspot: String
    private lateinit var slotManagedProfile: String
    private lateinit var slotMute: String
    private lateinit var slotNextAlarm: String
    private lateinit var slotTty: String
    private lateinit var slotVibrate: String
    private lateinit var slotVpn: String
    private lateinit var slotWifi: String
    private lateinit var slotZen: String

    @Before
    fun setUp() {
        slotAirplane = context.getString(com.android.internal.R.string.status_bar_airplane)
        slotBluetooth = context.getString(com.android.internal.R.string.status_bar_bluetooth)
        slotConnectedDisplay =
            context.getString(com.android.internal.R.string.status_bar_connected_display)
        slotDataSaver = context.getString(com.android.internal.R.string.status_bar_data_saver)
        slotDeviceBasedSatellite =
            context.getString(com.android.internal.R.string.status_bar_oem_satellite)
        slotEthernet = context.getString(com.android.internal.R.string.status_bar_ethernet)
        slotHeadset = context.getString(com.android.internal.R.string.status_bar_headset)
        slotHotspot = context.getString(com.android.internal.R.string.status_bar_hotspot)
        slotManagedProfile =
            context.getString(com.android.internal.R.string.status_bar_managed_profile)
        slotMute = context.getString(com.android.internal.R.string.status_bar_mute)
        slotNextAlarm = context.getString(com.android.internal.R.string.status_bar_alarm_clock)
        slotTty = context.getString(com.android.internal.R.string.status_bar_tty)
        slotVibrate = context.getString(com.android.internal.R.string.status_bar_volume)
        slotVpn = context.getString(com.android.internal.R.string.status_bar_vpn)
        slotWifi = context.getString(com.android.internal.R.string.status_bar_wifi)
        slotZen = context.getString(com.android.internal.R.string.status_bar_zen)
    }

    @Test
    fun iconViewModels_noIconIsActive_initiallyEmpty() =
        kosmos.runTest { assertThat(underTest.activeSlotNames).isEmpty() }

    @Test
    fun iconViewModels_oneIconActive_showsOneIcon() =
        kosmos.runTest {
            showAirplaneMode()
            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane)
        }

    @Test
    fun iconViewModels_multipleIconsActive_orderIsRespected() =
        kosmos.runTest {
            val customOrder = arrayOf(slotBluetooth, slotZen, slotAirplane)
            statusBarConfigIconSlotNames = (customOrder)

            showZenMode()
            showBluetooth()
            showAirplaneMode()

            assertThat(underTest.activeSlotNames)
                .containsExactly(slotBluetooth, slotZen, slotAirplane)
                .inOrder()
        }

    @Test
    fun iconViewModels_multipleIconsActive_someOrderedSomeNot_unorderedFirstThenOrdered() =
        kosmos.runTest {
            val customOrder = arrayOf(slotMute, slotBluetooth, slotZen)
            statusBarConfigIconSlotNames = customOrder

            showAirplaneMode()
            showBluetooth()
            showEthernet()
            showVibrate()
            showZenMode()

            assertThat(underTest.activeSlotNames)
                .containsExactly(slotAirplane, slotEthernet, slotVibrate, slotBluetooth, slotZen)
                .inOrder()
        }

    @Test
    fun iconViewModels_firstExternalThenUnorderedThenOrdered() =
        kosmos.runTest {
            val customOrder = arrayOf(slotBluetooth)
            statusBarConfigIconSlotNames = customOrder
            assertThat(underTest.activeSlotNames).isEmpty()

            showBluetooth()
            showEthernet()
            showExternalIcon(slotName = "externalSlot")

            assertThat(underTest.activeSlotNames)
                .containsExactly("externalSlot", slotEthernet, slotBluetooth)
                .inOrder()
        }

    @Test
    fun iconViewModels_externalSlotSameNameAsInternal_bothShown() =
        kosmos.runTest {
            assertThat(underTest.activeSlotNames).isEmpty()

            showAirplaneMode()
            showExternalIcon(slotName = slotAirplane)
            // Verify both icons are shown
            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane, slotAirplane)
            // Verify external is before internal
            assertThat(underTest.iconViewModels[0])
                .isInstanceOf(SystemStatusIconViewModel.External::class.java)
            assertThat(underTest.iconViewModels[1])
                .isInstanceOf(SystemStatusIconViewModel.Default::class.java)
        }

    @Test
    fun iconViewModels_individualIconStateChanges_becomesActive() =
        kosmos.runTest {
            statusBarConfigIconSlotNames = arrayOf(slotAirplane)

            assertThat(underTest.activeSlotNames).isEmpty()

            showAirplaneMode()

            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane)
        }

    @Test
    fun iconViewModels_individualIconStateChanges_becomesInactive() =
        kosmos.runTest {
            statusBarConfigIconSlotNames = arrayOf(slotAirplane)
            showAirplaneMode()

            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane)

            hideAirplaneMode()
            assertThat(underTest.activeSlotNames).isEmpty()
        }

    @Test
    fun iconViewModels_orderWithNonExistentSlots_ignoresNonExistent() =
        kosmos.runTest {
            showBluetooth()
            showAirplaneMode()

            val customOrder =
                arrayOf("non_existent_slot_1", slotBluetooth, slotAirplane, "non_existent_slot_2")
            statusBarConfigIconSlotNames = customOrder

            // "non_existent_slot_1" and "non_existent_slot_2" are ignored.
            // bluetooth and airplane are ordered according to their appearance in customOrder.
            assertThat(underTest.activeSlotNames)
                .containsExactly(slotBluetooth, slotAirplane)
                .inOrder()
        }

    @Test
    fun iconViewModels_emptyOrderList_allIconsShownInAlphabeticalOrder() =
        kosmos.runTest {
            statusBarConfigIconSlotNames = emptyArray()

            // GIVEN the device is entered (unlocked). This is required for some icons to show.
            kosmos.sceneContainerStartable.start()
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.None
            )
            kosmos.fakeKeyguardRepository.setKeyguardEnabled(false)
            kosmos.sceneInteractor.changeScene(Scenes.Gone, "SystemStatusIconsViewModelTest")

            showZenMode()
            showBluetooth()
            showConnectedDisplay()
            showTty()
            showDataSaver()
            showNextAlarm()
            showEthernet()
            showVibrate()
            showHotspot()
            showVpn()
            showManagedProfile()
            showSatelliteIcon()
            showHeadset()

            assertThat(underTest.activeSlotNames)
                .containsExactly(
                    slotBluetooth,
                    slotConnectedDisplay,
                    slotDataSaver,
                    slotDeviceBasedSatellite,
                    slotEthernet,
                    slotHeadset,
                    slotHotspot,
                    slotManagedProfile,
                    slotNextAlarm,
                    slotTty,
                    slotVibrate,
                    slotVpn,
                    slotZen,
                )
                .inOrder()

            // Some icons need to be tested separately
            showMute() // This will make vibrate inactive
            showWifi() // This will make ethernet, satellite inactive
            showAirplaneMode() // This will make satellite inactive

            assertThat(underTest.activeSlotNames)
                .containsExactly(
                    slotAirplane,
                    slotBluetooth,
                    slotConnectedDisplay,
                    slotDataSaver,
                    slotHeadset,
                    slotHotspot,
                    slotManagedProfile,
                    slotMute,
                    slotNextAlarm,
                    slotTty,
                    slotVpn,
                    slotWifi,
                    slotZen,
                )
                .inOrder()
        }

    @Test
    fun iconViewModels_iconInBlockList_isNotShown() =
        kosmos.runTest {
            showAirplaneMode()
            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane)

            iconBlockList.value = listOf(slotAirplane)

            assertThat(underTest.activeSlotNames).isEmpty()
        }

    @Test
    fun iconViewModels_multipleIconsActive_oneInBlockList_othersShown() =
        kosmos.runTest {
            showAirplaneMode()
            showBluetooth()
            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane, slotBluetooth)

            iconBlockList.value = listOf(slotAirplane)

            assertThat(underTest.activeSlotNames).containsExactly(slotBluetooth)
        }

    @Test
    fun iconViewModels_iconRemovedFromBlockList_isShownIfActive() =
        kosmos.runTest {
            showAirplaneMode()
            iconBlockList.value = listOf(slotAirplane)
            assertThat(underTest.activeSlotNames).isEmpty()

            iconBlockList.value = emptyList()

            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane)
        }

    @Test
    fun iconViewModels_blockListedIcon_doesNotAffectOrderOfOtherIcons() =
        kosmos.runTest {
            val customOrder = arrayOf(slotBluetooth, slotZen, slotAirplane)
            statusBarConfigIconSlotNames = customOrder

            showZenMode()
            showBluetooth()
            showAirplaneMode()

            iconBlockList.value = listOf(slotZen)

            assertThat(underTest.activeSlotNames)
                .containsExactly(slotBluetooth, slotAirplane)
                .inOrder()
        }

    @Test
    fun iconViewModels_allIconsActiveAndBlockListed_showsNoIcons() =
        kosmos.runTest {
            showAirplaneMode()
            showBluetooth()
            showZenMode()

            iconBlockList.value = listOf(slotAirplane, slotBluetooth, slotZen)

            assertThat(underTest.activeSlotNames).isEmpty()
        }

    @Test
    fun iconViewModels_blockListingNonActiveIcon_hasNoEffect() =
        kosmos.runTest {
            showAirplaneMode()
            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane)

            iconBlockList.value = listOf(slotBluetooth)

            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane)
        }

    @Test
    fun iconViewModels_emptyBlockList_showsAllActiveIcons() =
        kosmos.runTest {
            showAirplaneMode()
            showBluetooth()
            iconBlockList.value = emptyList()

            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane, slotBluetooth)
        }

    private val SystemStatusIconsViewModel.activeSlotNames: List<String>
        get() = this.iconViewModels.filter { it.visible }.map { it.slotName }
}
