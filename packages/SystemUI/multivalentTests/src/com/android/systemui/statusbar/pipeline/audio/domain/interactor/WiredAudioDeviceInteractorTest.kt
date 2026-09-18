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

package com.android.systemui.statusbar.pipeline.audio.domain.interactor

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
class WiredAudioDeviceInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { wiredAudioDeviceInteractor }

    @Test
    fun startsNull() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)

            assertThat(latest).isNull()
        }

    @Test
    fun startsWithWiredHeadphones_noMic() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)
            val device =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
                }

            audioManager.setDevices(arrayOf(device))

            assertThat(latest).isEqualTo(WiredAudioDevice(hasMic = false))
        }

    @Test
    fun startsWithWiredHeadset_hasMic() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)
            val device =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
                }

            audioManager.setDevices(arrayOf(device))

            assertThat(latest).isEqualTo(WiredAudioDevice(hasMic = true))
        }

    @Test
    fun startsWithUsbHeadset_hasMic() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)
            val device =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_USB_HEADSET)
                }

            audioManager.setDevices(arrayOf(device))

            assertThat(latest).isEqualTo(WiredAudioDevice(hasMic = true))
        }

    @Test
    fun startsWithUsbDevice_source_hasMic() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)
            val device =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_USB_DEVICE)
                    whenever(it.isSource).thenReturn(true)
                }

            audioManager.setDevices(arrayOf(device))

            assertThat(latest).isEqualTo(WiredAudioDevice(hasMic = true))
        }

    @Test
    fun startsWithLineAnalog_noMic() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)
            val device =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_LINE_ANALOG)
                }

            audioManager.setDevices(arrayOf(device))

            assertThat(latest).isEqualTo(WiredAudioDevice(hasMic = false))
        }

    @Test
    fun startsWithUsbDevice_notSource_noMic() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)
            val device =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_USB_DEVICE)
                    whenever(it.isSource).thenReturn(false)
                }

            audioManager.setDevices(arrayOf(device))

            assertThat(latest).isEqualTo(WiredAudioDevice(hasMic = false))
        }

    @Test
    fun headsetAndHeadphones_headsetHasPriority() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)
            val headsetDevice =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
                }
            val headphoneDevice =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
                }

            audioManager.setDevices(arrayOf(headsetDevice, headphoneDevice))

            assertThat(latest).isEqualTo(WiredAudioDevice(hasMic = true))
        }

    @Test
    fun headsetAndHeadphones_headsetHasPriority_reversed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.wiredAudioDevice)
            val headsetDevice =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
                }
            val headphoneDevice =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
                }

            audioManager.setDevices(arrayOf(headphoneDevice, headsetDevice))

            assertThat(latest).isEqualTo(WiredAudioDevice(hasMic = true))
        }
}
