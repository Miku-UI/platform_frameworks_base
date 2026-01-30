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

import android.bluetooth.BluetoothHapPresetInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class PresetSpinnerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var spinnerAdapter: HearingDevicesSpinnerAdapter
    private lateinit var spinner: PresetSpinner

    @Before
    fun setUp() {
        spinnerAdapter = HearingDevicesSpinnerAdapter(context)
        spinner = PresetSpinner(context, spinnerAdapter = spinnerAdapter)
    }

    @Test
    fun setTitle_titleCorrect() {
        val testTitle = "test"
        spinner.setTitle(testTitle)

        assertThat(spinner.getTitle()).isEqualTo(testTitle)
    }

    @Test
    fun setList_setToAdapter() {
        val testInfos =
            listOf(
                createMockPresetInfo(1, "test_preset_1"),
                createMockPresetInfo(2, "test_preset_2"),
                createMockPresetInfo(3, "test_preset_3"),
            )

        spinner.setList(testInfos)

        for (i in testInfos.indices) {
            assertThat(spinnerAdapter.getItem(i)).isEqualTo(testInfos[i].name)
        }
    }

    @Test
    fun setValue_setToAdapter() {
        val testInfos =
            listOf(
                createMockPresetInfo(1, "test_preset_1"),
                createMockPresetInfo(2, "test_preset_2"),
                createMockPresetInfo(3, "test_preset_3"),
            )

        val testSelectedPositin = 1
        val testSelectedPresetIndex = testInfos[testSelectedPositin].index
        spinner.setList(testInfos)
        spinner.setValue(testSelectedPresetIndex)

        assertThat(spinnerAdapter.selected).isEqualTo(testSelectedPositin)
        assertThat(spinner.getValue()).isEqualTo(testSelectedPresetIndex)
    }

    private fun createMockPresetInfo(index: Int, name: String): BluetoothHapPresetInfo =
        mock<BluetoothHapPresetInfo> {
            on { this.index } doReturn index
            on { this.name } doReturn name
        }
}