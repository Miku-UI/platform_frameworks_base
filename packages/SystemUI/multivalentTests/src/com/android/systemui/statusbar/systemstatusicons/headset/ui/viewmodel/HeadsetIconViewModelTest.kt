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

package com.android.systemui.statusbar.systemstatusicons.headset.ui.viewmodel

import android.content.testableContext
import android.media.AudioDeviceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.audio.audioManager
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class HeadsetIconViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val underTest =
        kosmos.headsetIconViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Test
    fun notVisible_and_nullIcon_whenNoDevice() =
        kosmos.runTest {
            assertThat(underTest.visible).isFalse()
            assertThat(underTest.icon).isNull()
        }

    @Test
    fun visible_and_noMicIcon_whenDeviceWithNoMic() =
        kosmos.runTest {
            val device =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
                }

            audioManager.setDevices(arrayOf(device))

            assertThat(underTest.visible).isTrue()

            val expectedIcon =
                Icon.Resource(
                    resId = R.drawable.ic_headset,
                    contentDescription =
                        ContentDescription.Resource(R.string.accessibility_status_bar_headset),
                )
            assertThat(underTest.icon).isEqualTo(expectedIcon)
        }

    @Test
    fun visible_and_micIcon_whenDeviceWithMic() =
        kosmos.runTest {
            val device =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
                }

            audioManager.setDevices(arrayOf(device))

            assertThat(underTest.visible).isTrue()

            val expectedIcon =
                Icon.Resource(
                    resId = R.drawable.ic_headset_mic,
                    contentDescription =
                        ContentDescription.Resource(R.string.accessibility_status_bar_headphones),
                )
            assertThat(underTest.icon).isEqualTo(expectedIcon)
        }

    @Test
    fun plugsIn_thenUnplugs() =
        kosmos.runTest {
            assertThat(underTest.visible).isFalse()

            // Plug in
            val device =
                mock<AudioDeviceInfo>().also {
                    whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
                }
            audioManager.setDevices(arrayOf(device))

            assertThat(underTest.visible).isTrue()

            // Unplug
            audioManager.removeDevices(arrayOf(device))

            assertThat(underTest.visible).isFalse()
        }
}
