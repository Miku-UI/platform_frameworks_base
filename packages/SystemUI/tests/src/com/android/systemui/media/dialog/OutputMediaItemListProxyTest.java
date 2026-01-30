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

package com.android.systemui.media.dialog;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.settingslib.media.MediaDevice;
import com.android.systemui.SysuiTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.stream.Collectors;

@SmallTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
public class OutputMediaItemListProxyTest extends SysuiTestCase {
    private static final String DEVICE_ID_1 = "device_id_1";
    private static final String DEVICE_ID_2 = "device_id_2";
    private static final String DEVICE_ID_3 = "device_id_3";
    private static final String DEVICE_ID_4 = "device_id_4";
    private static final String DEVICE_ID_5 = "device_id_5";
    private static final String DEVICE_ID_6 = "device_id_6";
    @Mock private MediaDevice mMediaDevice1;
    @Mock private MediaDevice mMediaDevice2;
    @Mock private MediaDevice mMediaDevice3;
    @Mock private MediaDevice mMediaDevice4;
    @Mock private MediaDevice mMediaDevice5;
    @Mock private MediaDevice mMediaDevice6;

    private OutputMediaItemListProxy mOutputMediaItemListProxy;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mMediaDevice1.getId()).thenReturn(DEVICE_ID_1);
        when(mMediaDevice2.getId()).thenReturn(DEVICE_ID_2);
        when(mMediaDevice2.isSuggestedDevice()).thenReturn(true);
        when(mMediaDevice3.getId()).thenReturn(DEVICE_ID_3);
        when(mMediaDevice4.getId()).thenReturn(DEVICE_ID_4);
        when(mMediaDevice5.getId()).thenReturn(DEVICE_ID_5);
        when(mMediaDevice5.isSuggestedDevice()).thenReturn(true);
        when(mMediaDevice6.getId()).thenReturn(DEVICE_ID_6);

        mOutputMediaItemListProxy = new OutputMediaItemListProxy(mContext);
    }

    @Test
    public void updateMediaDevices_shouldUpdateMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        when(mMediaDevice3.isSelected()).thenReturn(true);
        // Create the initial output media item list with mMediaDevice2 and mMediaDevice3.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice2, mMediaDevice3),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        // Check the output media items to be
        //     * a media item with the selected mMediaDevice3
        //     * a group divider for suggested devices
        //     * a media item with the mMediaDevice2
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                .containsExactly(mMediaDevice3, null, mMediaDevice2);
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList().get(0).isFirstDeviceInGroup())
                .isTrue();

        when(mMediaDevice3.isSelected()).thenReturn(true);
        // Update the output media item list with more media devices.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice4, mMediaDevice1, mMediaDevice3, mMediaDevice2),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        // Check the output media items to be
        //     * a media item with the selected route mMediaDevice3
        //     * a group divider for suggested devices
        //     * a media item with the route mMediaDevice2
        //     * a group divider for speakers and displays
        //     * a media item with the route mMediaDevice4
        //     * a media item with the route mMediaDevice1
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                .containsExactly(
                        mMediaDevice3, null, mMediaDevice2, null, mMediaDevice4, mMediaDevice1);
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList().get(0).isFirstDeviceInGroup())
                .isTrue();

        when(mMediaDevice1.isSelected()).thenReturn(true);
        when(mMediaDevice3.isSelected()).thenReturn(true);
        // Update the output media item list where mMediaDevice4 is offline and new selected device.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice1, mMediaDevice3, mMediaDevice2),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        // Check the output media items to be
        //     * a media item with the selected route mMediaDevice3
        //     * a group divider for suggested devices
        //     * a media item with the route mMediaDevice2
        //     * a group divider for speakers and displays
        //     * a media item with the route mMediaDevice1
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                .containsExactly(mMediaDevice3, null, mMediaDevice2, null, mMediaDevice1);
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList().get(0).isFirstDeviceInGroup())
                .isTrue();
    }

    @Test
    public void updateMediaDevices_multipleSelectedDevices_shouldHaveCorrectDeviceOrdering() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        when(mMediaDevice1.isSelected()).thenReturn(true);
        when(mMediaDevice2.isSelected()).thenReturn(true);
        when(mMediaDevice3.isSelected()).thenReturn(true);
        // Create the initial output media item list with mMediaDevice2 and mMediaDevice3.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice2, mMediaDevice4, mMediaDevice3, mMediaDevice1),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        // The order of selected devices is preserved:
        //     * a media item with the selected mMediaDevice2
        //     * a media item with the selected mMediaDevice3
        //     * a media item with the selected mMediaDevice1
        //     * a group divider for speakers and displays
        //     * a media item with the mMediaDevice4
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                .containsExactly(mMediaDevice2, mMediaDevice3, mMediaDevice1, null, mMediaDevice4);
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList().get(0).isFirstDeviceInGroup())
                .isTrue();

        when(mMediaDevice1.isSelected()).thenReturn(false);
        when(mMediaDevice2.isSelected()).thenReturn(true);
        when(mMediaDevice3.isSelected()).thenReturn(true);

        // Update the output media item list with a selected device being deselected.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice4, mMediaDevice1, mMediaDevice3, mMediaDevice2),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        // The order of selected devices is preserved:
        //     * a media item with the selected mMediaDevice2
        //     * a media item with the selected mMediaDevice3
        //     * a media item with the selected mMediaDevice1
        //     * a group divider for speakers and displays
        //     * a media item with the mMediaDevice4
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                .containsExactly(mMediaDevice2, mMediaDevice3, mMediaDevice1, null, mMediaDevice4);
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList().get(0).isFirstDeviceInGroup())
                .isTrue();

        when(mMediaDevice1.isSelected()).thenReturn(false);
        when(mMediaDevice2.isSelected()).thenReturn(false);
        when(mMediaDevice3.isSelected()).thenReturn(true);
        // Update the output media item list with a selected device is missing.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice1, mMediaDevice3, mMediaDevice4),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        // The order of selected devices is preserved:
        //     * a media item with the selected mMediaDevice3
        //     * a media item with the selected mMediaDevice1
        //     * a group divider for speakers and displays
        //     * a media item with the mMediaDevice4
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                .containsExactly(mMediaDevice3, mMediaDevice1, null, mMediaDevice4);
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList().get(0).isFirstDeviceInGroup())
                .isTrue();
    }

    @Test
    public void clear_shouldClearMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice1),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse();

        mOutputMediaItemListProxy.clear();
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();
    }

    @Test
    public void removeMutingExpectedDevices_shouldClearMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice1),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse();

        mOutputMediaItemListProxy.removeMutingExpectedDevices();
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse();
    }

    @Test
    public void getOutputMediaItemList_withMoreThanTwoSuggestedDevices_limitsSuggested() {
        when(mMediaDevice4.isSuggestedDevice()).thenReturn(true);
        List<MediaDevice> allDevices = List.of(
                mMediaDevice1, // Normal
                mMediaDevice2, // Suggested 1
                mMediaDevice3, // Normal
                mMediaDevice4, // Suggested 2
                mMediaDevice5, // Suggested 3 (overflow)
                mMediaDevice6  // Normal
        );

        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        when(mMediaDevice3.isSelected()).thenReturn(true);
        // Update the proxy with all the devices keeping mMediaDevice3 as the selected device.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ allDevices,
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        List<MediaDevice> actualDevices = getMediaDevices(
                mOutputMediaItemListProxy.getOutputMediaItemList());

        // The order of selected devices should be:
        //     * a media item with the selected mMediaDevice3
        //     * a group divider for suggested
        //     * a media item with the suggested mMediaDevice2
        //     * a media item with the suggested mMediaDevice4
        //     * a group divider for speakers and displays
        //     * a media item with the mMediaDevice1
        //     * a media item with the mMediaDevice5
        //     * a media item with the mMediaDevice6
        assertThat(actualDevices).containsExactly(
                mMediaDevice3,
                null,
                mMediaDevice2,
                mMediaDevice4,
                null,
                mMediaDevice1,
                mMediaDevice5,
                mMediaDevice6
        ).inOrder();
    }

    private List<MediaDevice> getMediaDevices(List<MediaItem> mediaItems) {
        return mediaItems.stream()
                .map(item -> item.getMediaDevice().orElse(null))
                .collect(Collectors.toList());
    }
}
