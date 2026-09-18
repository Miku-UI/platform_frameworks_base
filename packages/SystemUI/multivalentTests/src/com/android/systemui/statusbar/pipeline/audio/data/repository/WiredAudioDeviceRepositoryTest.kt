/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.audio.data.repository

import android.media.AudioDeviceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.audio.audioManager
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class WiredAudioDeviceRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { wiredAudioDeviceRepository }

    @Test
    fun startsEmpty() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)

            assertThat(latest).isNotNull()
            assertThat(latest).isEmpty()
        }

    @Test
    fun plugsIn_thenUnplugs() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)

            // Plug in
            val device =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
                }
            audioManager.setDevices(arrayOf(device))

            assertThat(latest).containsExactly(device)

            // Unplug
            audioManager.removeDevices(arrayOf(device))

            assertThat(latest).isEmpty()
        }

    @Test
    fun unplugs_thenPlugsIn() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)

            // Unplug
            audioManager.setDevices(emptyArray())

            assertThat(latest).isEmpty()

            // Plug in
            val device =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
                }
            audioManager.setDevices(arrayOf(device))

            assertThat(latest).containsExactly(device)
        }

    @Test
    fun filtersOutNonWiredDevices() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)

            val wiredHeadset =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
                }
            val usbDevice =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_USB_DEVICE)
                }
            val usbHeadset =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_USB_HEADSET)
                }
            val wiredHeadphones =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
                }
            val lineAnalog =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_LINE_ANALOG)
                }
            val bluetoothDevice =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
                }
            val builtInSpeaker =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                }

            audioManager.setDevices(
                arrayOf(
                    wiredHeadset,
                    usbHeadset,
                    wiredHeadphones,
                    bluetoothDevice,
                    usbDevice,
                    builtInSpeaker,
                    lineAnalog,
                )
            )

            assertThat(latest)
                .containsExactly(wiredHeadset, usbHeadset, wiredHeadphones, usbDevice, lineAnalog)
        }
}
