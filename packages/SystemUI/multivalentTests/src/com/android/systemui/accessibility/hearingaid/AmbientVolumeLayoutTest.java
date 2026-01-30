/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.accessibility.hearingaid;

import static android.bluetooth.AudioInputControl.MUTE_DISABLED;
import static android.bluetooth.AudioInputControl.MUTE_MUTED;
import static android.bluetooth.AudioInputControl.MUTE_NOT_MUTED;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_LEFT;
import static com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_RIGHT;
import static com.android.systemui.accessibility.hearingaid.AmbientVolumeLayout.ROTATION_COLLAPSED;
import static com.android.systemui.accessibility.hearingaid.AmbientVolumeLayout.ROTATION_EXPANDED;
import static com.android.systemui.accessibility.hearingaid.AmbientVolumeLayout.SIDE_UNIFIED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.ArrayMap;
import android.view.View;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.settingslib.bluetooth.hearingdevices.ui.AmbientVolumeUi;
import com.android.systemui.SysuiTestCase;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Map;

/** Tests for {@link AmbientVolumeLayout}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class AmbientVolumeLayoutTest extends SysuiTestCase {

    private static final int TEST_LEFT_VOLUME_LEVEL = 1;
    private static final int TEST_RIGHT_VOLUME_LEVEL = 2;
    private static final int TEST_UNIFIED_VOLUME_LEVEL = 3;

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Spy
    private Context mContext = ApplicationProvider.getApplicationContext();
    @Mock
    private AmbientVolumeUi.AmbientVolumeUiListener mListener;

    private AmbientVolumeLayout mLayout;
    private ImageView mExpandIcon;
    private ImageView mVolumeIcon;
    private final Map<Integer, BluetoothDevice> mSideToDeviceMap = new ArrayMap<>();

    @Before
    public void setUp() {
        mLayout = new AmbientVolumeLayout(mContext);
        mLayout.setListener(mListener);
        mLayout.setControlExpandable(true);

        prepareDevices();
        mLayout.setupSliders(mSideToDeviceMap.keySet());
        mLayout.getSliders().forEach((side, slider) -> {
            slider.setMin(0);
            slider.setMax(4);
            if (side == SIDE_LEFT) {
                slider.setValue(TEST_LEFT_VOLUME_LEVEL);
            } else if (side == SIDE_RIGHT) {
                slider.setValue(TEST_RIGHT_VOLUME_LEVEL);
            } else if (side == SIDE_UNIFIED) {
                slider.setValue(TEST_UNIFIED_VOLUME_LEVEL);
            }
        });

        mExpandIcon = mLayout.getExpandIcon();
        mVolumeIcon = mLayout.getVolumeIcon();
    }

    @Test
    public void setControlExpandable_expandable_expandIconVisible() {
        mLayout.setControlExpandable(true);

        assertThat(mExpandIcon.getVisibility()).isEqualTo(VISIBLE);
    }

    @Test
    public void setControlExpandable_notExpandable_expandIconGone() {
        // Change the state from its default (false) to true. This ensures that the subsequent call
        // to setControlExpandable(false) will trigger the update logic.
        mLayout.setControlExpandable(true);
        mLayout.setControlExpandable(false);

        assertThat(mExpandIcon.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void setControlExpanded_expanded_assertControlUiCorrect() {
        mLayout.setControlExpanded(true);

        assertControlUiCorrect();
        int expectedLevel = calculateVolumeLevel(TEST_LEFT_VOLUME_LEVEL, TEST_RIGHT_VOLUME_LEVEL);
        assertThat(mVolumeIcon.getDrawable().getLevel()).isEqualTo(expectedLevel);
    }

    @Test
    public void setControlExpanded_notExpanded_assertControlUiCorrect() {
        // Change the state from its default (false) to true. This ensures that the subsequent call
        // to setControlExpanded(false) will trigger the update logic.
        mLayout.setControlExpanded(true);
        mLayout.setControlExpanded(false);

        assertControlUiCorrect();
        int expectedLevel = calculateVolumeLevel(TEST_UNIFIED_VOLUME_LEVEL,
                TEST_UNIFIED_VOLUME_LEVEL);
        assertThat(mVolumeIcon.getDrawable().getLevel()).isEqualTo(expectedLevel);
    }

    @Test
    public void setSliderEnabled_expandedAndLeftIsDisabled_volumeIconIsCorrect() {
        mLayout.setControlExpanded(true);
        mLayout.setSliderEnabled(SIDE_LEFT, false);

        int expectedLevel = calculateVolumeLevel(0, TEST_RIGHT_VOLUME_LEVEL);
        assertThat(mVolumeIcon.getDrawable().getLevel()).isEqualTo(expectedLevel);
    }

    @Test
    public void setSliderValue_expandedAndLeftValueChanged_volumeIconIsCorrect() {
        mLayout.setControlExpanded(true);
        mLayout.setSliderValue(SIDE_LEFT, 4);

        int expectedLevel = calculateVolumeLevel(4, TEST_RIGHT_VOLUME_LEVEL);
        assertThat(mVolumeIcon.getDrawable().getLevel()).isEqualTo(expectedLevel);
    }

    @Test
    public void isMutable_bothSideNotMutable_returnFalse() {
        mLayout.setSliderMuteState(SIDE_LEFT, MUTE_DISABLED);
        mLayout.setSliderMuteState(SIDE_RIGHT, MUTE_DISABLED);

        assertThat(mLayout.isMutable()).isFalse();
    }

    @Test
    public void isMutable_oneSideMutable_returnTrue() {
        mLayout.setSliderMuteState(SIDE_LEFT, MUTE_DISABLED);
        mLayout.setSliderMuteState(SIDE_RIGHT, MUTE_NOT_MUTED);

        assertThat(mLayout.isMutable()).isTrue();
    }

    @Test
    public void isMuted_bothSideMuted_returnTrue() {
        mLayout.setSliderMuteState(SIDE_LEFT, MUTE_MUTED);
        mLayout.setSliderMuteState(SIDE_RIGHT, MUTE_MUTED);

        assertThat(mLayout.isMuted()).isTrue();
    }

    @Test
    public void isMuted_oneSideNotMuted_returnFalse() {
        mLayout.setSliderMuteState(SIDE_LEFT, MUTE_MUTED);
        mLayout.setSliderMuteState(SIDE_RIGHT, MUTE_NOT_MUTED);

        assertThat(mLayout.isMuted()).isFalse();
    }

    @Test
    public void setSliderMuteState_muteLeft_volumeIconIsCorrect() {
        mLayout.setControlExpanded(true);
        mLayout.setSliderMuteState(SIDE_LEFT, MUTE_MUTED);
        mLayout.setSliderMuteState(SIDE_RIGHT, MUTE_NOT_MUTED);

        int expectedLevel = calculateVolumeLevel(0, TEST_RIGHT_VOLUME_LEVEL);
        assertThat(mVolumeIcon.getDrawable().getLevel()).isEqualTo(expectedLevel);
    }

    @Test
    public void setSliderMuteState_muteLeftAndRight_volumeIconIsCorrect() {
        mLayout.setControlExpanded(true);
        mLayout.setSliderMuteState(SIDE_LEFT, MUTE_MUTED);
        mLayout.setSliderMuteState(SIDE_RIGHT, MUTE_MUTED);

        int expectedLevel = calculateVolumeLevel(0, 0);
        assertThat(mVolumeIcon.getDrawable().getLevel()).isEqualTo(expectedLevel);
    }

    private int calculateVolumeLevel(int left, int right) {
        return left * 5 + right;
    }

    private void assertControlUiCorrect() {
        final boolean expanded = mLayout.isControlExpanded();
        final Map<Integer, AmbientVolumeSlider> sliders = mLayout.getSliders();
        if (expanded) {
            assertThat(sliders.get(SIDE_UNIFIED).getVisibility()).isEqualTo(GONE);
            assertThat(sliders.get(SIDE_LEFT).getVisibility()).isEqualTo(VISIBLE);
            assertThat(sliders.get(SIDE_RIGHT).getVisibility()).isEqualTo(VISIBLE);
            assertThat(mExpandIcon.getRotation()).isEqualTo(ROTATION_EXPANDED);
        } else {
            assertThat(sliders.get(SIDE_UNIFIED).getVisibility()).isEqualTo(VISIBLE);
            assertThat(sliders.get(SIDE_LEFT).getVisibility()).isEqualTo(GONE);
            assertThat(sliders.get(SIDE_RIGHT).getVisibility()).isEqualTo(GONE);
            assertThat(mExpandIcon.getRotation()).isEqualTo(ROTATION_COLLAPSED);
        }
    }

    private void prepareDevices() {
        mSideToDeviceMap.put(SIDE_LEFT, mock(BluetoothDevice.class));
        mSideToDeviceMap.put(SIDE_RIGHT, mock(BluetoothDevice.class));
    }
}
