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

package com.android.settingslib.bluetooth.hearingdevices

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHapPresetInfo
import android.bluetooth.BluetoothProfile
import com.android.settingslib.bluetooth.HapClientProfile
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager
import com.android.settingslib.utils.ThreadUtils
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

/** Tests for [PresetController]. */
@RunWith(RobolectricTestRunner::class)
class PresetControllerTest {

    private val profileManager = mock<LocalBluetoothProfileManager>()
    private val hapClientProfile = mock<HapClientProfile> {
        on { isProfileReady } doReturn true
    }
    private val callback = mock<PresetController.PresetControlCallback>()
    private val device = mock<BluetoothDevice> {
        on { isConnected } doReturn true
    }

    private lateinit var presetController: PresetController

    @Before
    fun setUp() {
        profileManager.stub {
            on { hapClientProfile } doReturn hapClientProfile
        }
        hapClientProfile.stub {
            on { getConnectionStatus(device) } doReturn BluetoothProfile.STATE_CONNECTED
        }
        presetController = PresetController(profileManager, callback)
    }

    @Test
    fun onServiceConnected_notifyCallback() {
        presetController.onServiceConnected()

        verify(callback).onHapClientServiceConnected()
    }

    @Test
    fun registerAndUnregisterCallback_verifyRegisterAndUnregisterOnProfile() {
        val executor: Executor = ThreadUtils.getBackgroundExecutor()
        presetController.registerCallback(executor)

        verify(hapClientProfile).registerCallback(eq(executor), any())

        presetController.unregisterCallback()

        verify(hapClientProfile).unregisterCallback(any())
    }

    @Test
    fun getActivePreset_verifyGetThroughProfile() {
        presetController.getActivePreset(device)

        verify(hapClientProfile).getActivePresetIndex(device)
    }

    @Test
    fun getPresetInfos_verifyGetOnlyAvailableInfoThroughProfile() {
        val testPresetInfos = listOf(
            getTestPresetInfo(true),
            getTestPresetInfo(true),
            getTestPresetInfo(false)
        )
        hapClientProfile.stub {
            on { getAllPresetInfo(device) } doReturn testPresetInfos
        }

        val infos: List<BluetoothHapPresetInfo> = presetController.getPresetInfos(device)

        verify(hapClientProfile).getAllPresetInfo(device)
        Truth.assertThat(infos.size).isEqualTo(2)
    }

    @Test
    fun selectPreset_verifySelectThroughProfile() {
        presetController.selectPreset(device, TEST_PRESET_INDEX)

        verify(hapClientProfile).selectPreset(device, TEST_PRESET_INDEX)
    }

    @Test
    fun selectPresetForGroup_verifySelectThroughProfile() {
        presetController.selectPresetForGroup(TEST_HAP_GROUP_ID, TEST_PRESET_INDEX)

        verify(hapClientProfile).selectPresetForGroup(TEST_HAP_GROUP_ID, TEST_PRESET_INDEX)
    }

    @Test
    fun getHapGroupId_verifyGetThroughProfile() {
        hapClientProfile.stub {
            on { getHapGroup(device) } doReturn TEST_HAP_GROUP_ID
        }

        val hapGroupId: Int = presetController.getHapGroupId(device)

        verify(hapClientProfile).getHapGroup(device)
        assertThat(hapGroupId).isEqualTo(TEST_HAP_GROUP_ID)
    }

    @Test
    fun supportsSynchronizedPresets_verifyGetThroughProfile() {
        presetController.supportsSynchronizedPresets(device)

        verify(hapClientProfile).supportsSynchronizedPresets(device)
    }

    private fun getTestPresetInfo(available: Boolean): BluetoothHapPresetInfo {
        return mock<BluetoothHapPresetInfo>() {
            on { name } doReturn TEST_PRESET_NAME
            on { index } doReturn TEST_PRESET_INDEX
            on { isAvailable } doReturn available
        }
    }

    companion object {
        private const val TEST_PRESET_INDEX = 1
        private const val TEST_PRESET_NAME = "test_preset"
        private const val TEST_HAP_GROUP_ID = 1
    }
}