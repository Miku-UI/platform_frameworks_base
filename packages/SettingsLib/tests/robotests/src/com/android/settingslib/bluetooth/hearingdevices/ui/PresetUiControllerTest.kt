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

package com.android.settingslib.bluetooth.hearingdevices.ui

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothHapPresetInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.bluetooth.BluetoothEventManager
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.HapClientProfile
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_LEFT
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_RIGHT
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.settingslib.bluetooth.LocalBluetoothProfile
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager
import com.android.settingslib.bluetooth.hearingdevices.ui.ExpandableControlUi.Companion.SIDE_UNIFIED
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PresetUiControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockBluetoothManager = mock<LocalBluetoothManager>()
    private val mockEventManager = mock<BluetoothEventManager>()
    private val mockProfileManager = mock<LocalBluetoothProfileManager>()
    private val mockHapClientProfile = mock<HapClientProfile>()
    private val mockCachedDevice = mock<CachedBluetoothDevice>()
    private val mockCachedMemberDevice = mock<CachedBluetoothDevice>()
    private val mockDevice = mock<BluetoothDevice>()
    private val mockMemberDevice = mock<BluetoothDevice>()

    private val presetUi = TestPresetUi()
    private lateinit var controller: PresetUiController

    @Before
    fun setUp() {
        mockBluetoothManager.stub {
            on { eventManager } doReturn mockEventManager
            on { profileManager } doReturn mockProfileManager
        }
        mockProfileManager.stub {
            on { hapClientProfile } doReturn mockHapClientProfile
        }
        controller = PresetUiController(context, mockBluetoothManager, presetUi)
    }

    @Test
    fun loadDevice_deviceIsNull_presetUiGone() {
        controller.loadDevice(null)

        assertThat(presetUi.isVisible()).isEqualTo(false)
    }

    @Test
    fun loadDevice_deviceNotSupportHap_presetUiGone() {
        prepareTestDevice(hasMember = false)
        mockCachedDevice.stub {
            on { profiles } doReturn emptyList<LocalBluetoothProfile>()
        }

        controller.loadDevice(mockCachedDevice)

        assertThat(presetUi.isVisible()).isEqualTo(false)
    }

    @Test
    fun startAndStop_callbackRegisteredAndUnregistered() {
        prepareTestDevice(hasMember = true)
        controller.loadDevice(mockCachedDevice)

        controller.start()

        verify(mockEventManager).registerCallback(any())
        verify(mockCachedDevice).registerCallback(any(), any())
        verify(mockCachedMemberDevice).registerCallback(any(), any())
        verify(mockHapClientProfile).registerCallback(any(), any())

        controller.stop()

        verify(mockEventManager).unregisterCallback(any())
        verify(mockCachedDevice).unregisterCallback(any())
        verify(mockCachedMemberDevice).unregisterCallback(any())
        verify(mockHapClientProfile).unregisterCallback(any())
    }

    @Test
    fun refresh_onlyOneDevice_controlNotExpanded() {
        prepareTestDevice(hasMember = false)
        controller.loadDevice(mockCachedDevice)

        controller.refresh()

        assertThat(presetUi.isControlExpanded()).isEqualTo(false)
    }

    @Test
    fun refresh_samePresetInfos_controlNotExpanded() {
        prepareTestDevice(hasMember = true, syncedInfos = true)
        controller.loadDevice(mockCachedDevice)

        controller.refresh()

        assertThat(presetUi.isControlExpanded()).isEqualTo(false)
    }

    @Test
    fun refresh_differentPresetInfos_controlExpanded() {
        prepareTestDevice(hasMember = true, syncedInfos = false)
        controller.loadDevice(mockCachedDevice)

        controller.refresh()

        assertThat(presetUi.isControlExpanded()).isEqualTo(true)
    }

    @Test
    fun onPresetChangedFromRemote_samePresetInfos_verifyUnifiedControlUpdate() {
        prepareTestDevice(hasMember = true, syncedInfos = true)
        controller.loadDevice(mockCachedDevice)
        controller.refresh()

        val currentPreset = mockHapClientProfile.getActivePresetIndex(mockCachedDevice.device)
        val updatedPreset = currentPreset + 1
        assertThat(presetUi.getControlValue(SIDE_UNIFIED)).isEqualTo(currentPreset)

        controller.onPresetChangedFromRemote(mockCachedDevice.device, updatedPreset)

        assertThat(presetUi.getControlValue(SIDE_UNIFIED)).isEqualTo(updatedPreset)
    }

    @Test
    fun onPresetChangedFromRemote_differentPresetInfos_verifySeparatedControlUpdate() {
        prepareTestDevice(hasMember = true, syncedInfos = false)
        controller.loadDevice(mockCachedDevice)
        controller.refresh()

        val currentPreset = mockHapClientProfile.getActivePresetIndex(mockCachedDevice.device)
        val updatedPreset = currentPreset + 1
        assertThat(presetUi.getControlValue(mockCachedDevice.deviceSide)).isEqualTo(currentPreset)

        controller.onPresetChangedFromRemote(mockCachedDevice.device, updatedPreset)

        assertThat(presetUi.getControlValue(mockCachedDevice.deviceSide)).isEqualTo(updatedPreset)
    }

    @Test
    fun onPresetInfoChangedFromRemote_samePresetInfos_verifyUnifiedControlUpdate() {
        prepareTestDevice(hasMember = true, syncedInfos = true)
        controller.loadDevice(mockCachedDevice)
        controller.refresh()

        val updatedInfos = listOf(getTestPresetInfo(1))
        controller.onPresetInfoChangedFromRemote(mockCachedDevice.device, updatedInfos)

        assertThat(presetUi.getControlPresetList(SIDE_UNIFIED)).isEqualTo(updatedInfos)
    }

    @Test
    fun onPresetInfoChangedFromRemote_differentPresetInfos_verifySeparatedControlUpdate() {
        prepareTestDevice(hasMember = true, syncedInfos = false)
        controller.loadDevice(mockCachedDevice)
        controller.refresh()

        val updatedInfos = listOf(getTestPresetInfo(1))
        controller.onPresetInfoChangedFromRemote(mockCachedDevice.device, updatedInfos)

        assertThat(presetUi.getControlPresetList(mockCachedDevice.deviceSide)).isEqualTo(
            updatedInfos
        )
    }

    @Test
    fun onPresetGroupSelectionFailedFromRemote_verifySelectPresetForEachDevice() {
        prepareTestDevice(hasMember = true, syncedInfos = true)
        controller.loadDevice(mockCachedDevice)
        controller.refresh()

        controller.onPresetGroupSelectionFailedFromRemote(TEST_HAP_GROUP_ID)

        val currentPreset = presetUi.getControlValue(SIDE_UNIFIED)
        verify(mockHapClientProfile).selectPreset(mockDevice, currentPreset)
        verify(mockHapClientProfile).selectPreset(mockMemberDevice, currentPreset)
    }

    private fun prepareTestDevice(hasMember: Boolean) {
        prepareTestDevice(hasMember, syncedInfos = true)
    }

    private fun prepareTestDevice(hasMember: Boolean, syncedInfos: Boolean) {
        mockCachedDevice.stub {
            on { deviceSide } doReturn SIDE_LEFT
            on { bondState } doReturn BOND_BONDED
            on { profiles } doReturn listOf(mockHapClientProfile)
            on { device } doReturn mockDevice
            on { memberDevice } doReturn if (hasMember) setOf(mockCachedMemberDevice) else setOf()
        }
        mockDevice.stub {
            on { isConnected } doReturn true
        }
        if (hasMember) {
            mockCachedMemberDevice.stub {
                on { deviceSide } doReturn SIDE_RIGHT
                on { bondState } doReturn BOND_BONDED
                on { profiles } doReturn listOf(mockHapClientProfile)
                on { device } doReturn mockMemberDevice
            }
            mockMemberDevice.stub {
                on { isConnected } doReturn true
            }
        }
        val infos1 = listOf(
            getTestPresetInfo(TEST_PRESET_INDEX),
            getTestPresetInfo(TEST_PRESET_INDEX + 1)
        )
        val infos2 = listOf(
            getTestPresetInfo(TEST_PRESET_INDEX),
            getTestPresetInfo(TEST_PRESET_INDEX + 2)
        )
        mockHapClientProfile.stub {
            on { getAllPresetInfo(mockDevice) } doReturn infos1
            on { getAllPresetInfo(mockMemberDevice) } doReturn if (syncedInfos) infos1 else infos2
            on { getActivePresetIndex(mockDevice) } doReturn TEST_PRESET_INDEX
            on { getActivePresetIndex(mockMemberDevice) } doReturn TEST_PRESET_INDEX
            on { getHapGroup(mockDevice) } doReturn TEST_HAP_GROUP_ID
            on { getHapGroup(mockMemberDevice) } doReturn TEST_HAP_GROUP_ID
        }
    }

    private class TestPresetUi : PresetUi {
        private var visible: Boolean = true
        private var controlExpanded: Boolean = false
        private val values: MutableMap<Int, Int> = mutableMapOf()
        private val infos: MutableMap<Int, List<BluetoothHapPresetInfo>> = mutableMapOf()

        override fun setListener(listener: PresetUi.PresetUiListener?) {
        }

        override fun setupControls(sides: Set<Int>) {
        }

        override fun setControlEnabled(side: Int, enabled: Boolean) {
        }

        override fun setControlList(
            side: Int,
            presetInfos: List<BluetoothHapPresetInfo>
        ) {
            infos.put(side, presetInfos)
        }

        fun getControlPresetList(side: Int) = infos[side] ?: emptyList()

        override fun setControlValue(side: Int, presetIndex: Int) {
            values[side] = presetIndex
        }

        override fun getControlValue(side: Int): Int = values[side] ?: 0

        override fun setVisible(visible: Boolean) {
            this.visible = visible
        }

        override fun setControlExpanded(expanded: Boolean) {
            controlExpanded = expanded
        }

        override fun isControlExpanded(): Boolean = controlExpanded

        fun isVisible(): Boolean = visible
    }

    private fun getTestPresetInfo(presetIndex: Int): BluetoothHapPresetInfo {
        return mock<BluetoothHapPresetInfo> {
            on { name } doReturn "preset$presetIndex"
            on { index } doReturn presetIndex
            on { isAvailable } doReturn true
        }
    }

    companion object {
        private const val TEST_PRESET_INDEX = 1
        private const val TEST_HAP_GROUP_ID = 1
    }
}
