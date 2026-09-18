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
package com.android.systemui.media.dialog

import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.media.MediaDevice
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class OutputMediaItemListProxyTest : SysuiTestCase() {

    @JvmField @Rule val mockito: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var mMediaDevice1: MediaDevice
    @Mock private lateinit var mMediaDevice2: MediaDevice
    @Mock private lateinit var mMediaDevice3: MediaDevice
    @Mock private lateinit var mMediaDevice4: MediaDevice
    @Mock private lateinit var mMediaDevice5: MediaDevice
    @Mock private lateinit var mMediaDevice6: MediaDevice

    private var mOutputMediaItemListProxy = OutputMediaItemListProxy(mContext)

    @Before
    fun setUp() {
        Mockito.`when`(mMediaDevice1.id).thenReturn(DEVICE_ID_1)
        Mockito.`when`(mMediaDevice2.id).thenReturn(DEVICE_ID_2)
        Mockito.`when`(mMediaDevice2.isSuggestedDevice).thenReturn(true)
        Mockito.`when`(mMediaDevice3.id).thenReturn(DEVICE_ID_3)
        Mockito.`when`(mMediaDevice4.id).thenReturn(DEVICE_ID_4)
        Mockito.`when`(mMediaDevice5.id).thenReturn(DEVICE_ID_5)
        Mockito.`when`(mMediaDevice5.isSuggestedDevice).thenReturn(true)
        Mockito.`when`(mMediaDevice6.id).thenReturn(DEVICE_ID_6)
    }

    @Test
    fun updateMediaDevices_shouldUpdateMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue()

        Mockito.`when`(mMediaDevice3.isSelected()).thenReturn(true)
        // Create the initial output media item list with mMediaDevice2 and mMediaDevice3.
        mOutputMediaItemListProxy.updateMediaDevices(
            devices = listOf(mMediaDevice2, mMediaDevice3),
            connectedMediaDevice = null,
            needToHandleMutingExpectedDevice = false,
        )

        // Check the output media items to be
        //     * a media item with the selected mMediaDevice3
        //     * a group divider for suggested devices
        //     * a media item with the mMediaDevice2
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
            .containsExactly(mMediaDevice3, null, mMediaDevice2)

        Mockito.`when`(mMediaDevice3.isSelected()).thenReturn(true)
        // Update the output media item list with more media devices.
        mOutputMediaItemListProxy.updateMediaDevices(
            devices = listOf(mMediaDevice4, mMediaDevice1, mMediaDevice3, mMediaDevice2),
            connectedMediaDevice = null,
            needToHandleMutingExpectedDevice = false,
        )

        // Check the output media items to be
        //     * a media item with the selected route mMediaDevice3
        //     * a group divider for suggested devices
        //     * a media item with the route mMediaDevice2
        //     * a group divider for speakers and displays
        //     * a media item with the route mMediaDevice4
        //     * a media item with the route mMediaDevice1
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
            .containsExactly(mMediaDevice3, null, mMediaDevice2, null, mMediaDevice4, mMediaDevice1)

        Mockito.`when`(mMediaDevice1.isSelected()).thenReturn(true)
        Mockito.`when`(mMediaDevice3.isSelected()).thenReturn(true)
        // Update the output media item list where mMediaDevice4 is offline and new selected device.
        mOutputMediaItemListProxy.updateMediaDevices(
            devices = listOf(mMediaDevice1, mMediaDevice3, mMediaDevice2),
            connectedMediaDevice = null,
            needToHandleMutingExpectedDevice = false,
        )

        // Check the output media items to be
        //     * a media item with the selected route mMediaDevice3
        //     * a group divider for suggested devices
        //     * a media item with the route mMediaDevice2
        //     * a group divider for speakers and displays
        //     * a media item with the route mMediaDevice1
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
            .containsExactly(mMediaDevice3, null, mMediaDevice2, null, mMediaDevice1)
    }

    @Test
    fun updateMediaDevices_multipleSelectedDevices_shouldHaveCorrectDeviceOrdering() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue()

        Mockito.`when`(mMediaDevice2.isSelected()).thenReturn(true)
        Mockito.`when`(mMediaDevice1.isSelected()).thenReturn(true)
        Mockito.`when`(mMediaDevice3.isSelected()).thenReturn(true)
        // Create the initial output media item list with mMediaDevice2 and mMediaDevice3.
        mOutputMediaItemListProxy.updateMediaDevices(
            devices = listOf(mMediaDevice2, mMediaDevice4, mMediaDevice3, mMediaDevice1),
            connectedMediaDevice = null,
            needToHandleMutingExpectedDevice = false,
        )

        // The order of selected devices is preserved:
        //     * a media item with the selected mMediaDevice2
        //     * a media item with the selected mMediaDevice3
        //     * a media item with the selected mMediaDevice1
        //     * a group divider for speakers and displays
        //     * a media item with the mMediaDevice4
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
            .containsExactly(mMediaDevice2, mMediaDevice3, mMediaDevice1, null, mMediaDevice4)

        Mockito.`when`(mMediaDevice1.isSelected()).thenReturn(false)
        Mockito.`when`(mMediaDevice2.isSelected()).thenReturn(true)
        Mockito.`when`(mMediaDevice3.isSelected()).thenReturn(true)

        // Update the output media item list with a selected device being deselected.
        mOutputMediaItemListProxy.updateMediaDevices(
            devices = listOf(mMediaDevice4, mMediaDevice1, mMediaDevice3, mMediaDevice2),
            connectedMediaDevice = null,
            needToHandleMutingExpectedDevice = false,
        )

        // The order of selected devices is preserved:
        //     * a media item with the selected mMediaDevice2
        //     * a media item with the selected mMediaDevice3
        //     * a media item with the selected mMediaDevice1
        //     * a group divider for speakers and displays
        //     * a media item with the mMediaDevice4
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
            .containsExactly(mMediaDevice2, mMediaDevice3, mMediaDevice1, null, mMediaDevice4)

        Mockito.`when`(mMediaDevice1.isSelected()).thenReturn(false)
        Mockito.`when`(mMediaDevice2.isSelected()).thenReturn(false)
        Mockito.`when`(mMediaDevice3.isSelected()).thenReturn(true)
        // Update the output media item list with a selected device is missing.
        mOutputMediaItemListProxy.updateMediaDevices(
            devices = listOf(mMediaDevice1, mMediaDevice3, mMediaDevice4),
            connectedMediaDevice = null,
            needToHandleMutingExpectedDevice = false,
        )

        // The order of selected devices is preserved:
        //     * a media item with the selected mMediaDevice3
        //     * a media item with the selected mMediaDevice1
        //     * a group divider for speakers and displays
        //     * a media item with the mMediaDevice4
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
            .containsExactly(mMediaDevice3, mMediaDevice1, null, mMediaDevice4)
    }

    @Test
    fun clear_shouldClearMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue()

        mOutputMediaItemListProxy.updateMediaDevices(
            devices = listOf(mMediaDevice1),
            connectedMediaDevice = null,
            needToHandleMutingExpectedDevice = false,
        )
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse()

        mOutputMediaItemListProxy.clear()
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue()
    }

    @Test
    fun removeMutingExpectedDevices_shouldClearMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue()

        mOutputMediaItemListProxy.updateMediaDevices(
            devices = listOf(mMediaDevice1),
            connectedMediaDevice = null,
            needToHandleMutingExpectedDevice = false,
        )
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse()

        mOutputMediaItemListProxy.removeMutingExpectedDevices()
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse()
    }

    @Test
    fun getOutputMediaItemList_withMoreThanTwoSuggestedDevices_limitsSuggested() {
        Mockito.`when`(mMediaDevice4.isSuggestedDevice()).thenReturn(true)
        val allDevices =
            listOf(
                mMediaDevice1, // Normal
                mMediaDevice2, // Suggested 1
                mMediaDevice3, // Normal
                mMediaDevice4, // Suggested 2
                mMediaDevice5, // Suggested 3 (overflow)
                mMediaDevice6, // Normal
            )

        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue()

        Mockito.`when`(mMediaDevice3.isSelected()).thenReturn(true)
        // Update the proxy with all the devices keeping mMediaDevice3 as the selected device.
        mOutputMediaItemListProxy.updateMediaDevices(
            allDevices,
            connectedMediaDevice = null,
            needToHandleMutingExpectedDevice = false,
        )

        val actualDevices = getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList())

        // The order of selected devices should be:
        //     * a media item with the selected mMediaDevice3
        //     * a group divider for suggested
        //     * a media item with the suggested mMediaDevice2
        //     * a media item with the suggested mMediaDevice4
        //     * a group divider for speakers and displays
        //     * a media item with the mMediaDevice1
        //     * a media item with the mMediaDevice5
        //     * a media item with the mMediaDevice6
        assertThat(actualDevices)
            .containsExactly(
                mMediaDevice3,
                null,
                mMediaDevice2,
                mMediaDevice4,
                null,
                mMediaDevice1,
                mMediaDevice5,
                mMediaDevice6,
            )
            .inOrder()
    }

    private fun getMediaDevices(mediaItems: MutableList<MediaItem>): List<MediaDevice?> {
        return mediaItems.map {
            when (it) {
                is MediaItem.DeviceMediaItem -> it.mediaDevice
                else -> null
            }
        }
    }

    companion object {
        private const val DEVICE_ID_1 = "device_id_1"
        private const val DEVICE_ID_2 = "device_id_2"
        private const val DEVICE_ID_3 = "device_id_3"
        private const val DEVICE_ID_4 = "device_id_4"
        private const val DEVICE_ID_5 = "device_id_5"
        private const val DEVICE_ID_6 = "device_id_6"
    }
}
