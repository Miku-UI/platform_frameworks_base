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

package com.android.systemui.volume.panel.component.mediainput.ui.viewmodel

import android.media.AudioDeviceInfo
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.settingslib.media.InputMediaDevice
import com.android.settingslib.media.MediaDevice
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.testKosmosNew
import com.android.systemui.volume.panel.component.mediainput.data.repository.mediaInputComponentRepository
import com.android.systemui.volume.panel.ui.VolumePanelUiEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_QS_TILE_DETAILED_VIEW)
@EnableSceneContainer
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class MediaInputViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val underTest: MediaInputViewModel by lazy { kosmos.mediaInputViewModel }
    private val testMediaDevice: MediaDevice? =
        InputMediaDevice.create(
            context,
            /*id=*/ "test_device_id",
            /*address=*/ "",
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
            /*maxVolume=*/ 1,
            /*currentVolume=*/ 0,
            /*isVolumeFixed =*/ true,
            /*isSelected=*/ true,
            /*productName=*/ "Built-in Mic",
        )

    @Before
    fun setUp() {
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun noCurrentInputDevice_hasInputDeviceFalse() =
        kosmos.runTest {
            setCurrentInputDevice(null)

            assertThat(underTest.hasInputDevice).isFalse()
        }

    @Test
    fun currentInputDevice_hasInputDeviceTrue() =
        kosmos.runTest {
            setCurrentInputDevice(testMediaDevice)

            assertThat(underTest.hasInputDevice).isTrue()
        }

    @Test
    fun noCurrentInputDevice_connectedDeviceViewModelDeviceName() =
        kosmos.runTest {
            setCurrentInputDevice(null)

            assertThat(underTest.connectedDeviceName).isNull()
        }

    @Test
    fun currentInputDevice_connectedDeviceViewModelDeviceName() =
        kosmos.runTest {
            setCurrentInputDevice(testMediaDevice)

            assertThat(underTest.connectedDeviceName).isEqualTo(checkNotNull(testMediaDevice).name)
        }

    @Test
    fun noCurrentInputDevice_deviceIconViewModelIcon() =
        kosmos.runTest {
            setCurrentInputDevice(null)

            assertThat(underTest.connectedDeviceIcon)
                .isEqualTo(Icon.Resource(R.drawable.ic_media_home_devices, null))
        }

    @Test
    fun currentInputDevice_deviceIconViewModelIcon() =
        kosmos.runTest {
            setCurrentInputDevice(testMediaDevice)

            assertThat(underTest.connectedDeviceIcon).isInstanceOf(Icon.Loaded::class.java)
        }

    @Test
    fun clickCurrentInputDevice_logVolumePanelMediaInputClickedEvent() =
        kosmos.runTest {
            setCurrentInputDevice(testMediaDevice)

            underTest.onBarClick(null)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            val event = VolumePanelUiEvent.VOLUME_PANEL_MEDIA_INPUT_CLICKED
            assertThat(uiEventLoggerFake.eventId(0)).isEqualTo(event.id)
        }

    private fun setCurrentInputDevice(device: MediaDevice?) {
        kosmos.mediaInputComponentRepository.setCurrentInputDevice(device)
    }
}
