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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.res.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;


/** Tests for {@link AmbientVolumeLayout}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class AmbientVolumeSliderTest extends SysuiTestCase {

    private static final int TEST_MIN = 0;
    private static final int TEST_MAX = 100;
    private static final int TEST_VALUE = 30;

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Spy
    private Context mContext = ApplicationProvider.getApplicationContext();

    private AmbientVolumeSlider mSlider;

    @Before
    public void setUp() {
        mSlider = new AmbientVolumeSlider(mContext);
        mSlider.setMin(TEST_MIN);
        mSlider.setMax(TEST_MAX);
        mSlider.setValue(TEST_VALUE);
    }

    @Test
    public void setTitle_titleCorrect() {
        final String testTitle = "test";
        mSlider.setTitle(testTitle);

        assertThat(mSlider.getTitle()).isEqualTo(testTitle);
    }

    @Test
    public void getVolumeLevel_valueMin_volumeLevelIsZero() {
        mSlider.setValue(TEST_MIN);

        // The volume level is divided into 5 levels:
        // Level 0 corresponds to the minimum volume value. The range between the minimum and
        // maximum volume is divided into 4 equal intervals, represented by levels 1 to 4.
        assertThat(mSlider.getVolumeLevel()).isEqualTo(0);
    }

    @Test
    public void getVolumeLevel_valueMax_volumeLevelIsFour() {
        mSlider.setValue(TEST_MAX);

        assertThat(mSlider.getVolumeLevel()).isEqualTo(4);
    }

    @Test
    public void getVolumeLevel_volumeLevelIsCorrect() {
        mSlider.setValue(70);

        assertThat(mSlider.getVolumeLevel()).isEqualTo(3);
    }

    @Test
    public void setEnabled_disabled_valueIsMin() {
        mSlider.setEnabled(false);

        assertThat(mSlider.getValue()).isEqualTo(TEST_MIN);
    }

    @Test
    public void setMuteState_disabled_muteIconNotVisible() {
        mSlider.setMuteState(MUTE_DISABLED);

        assertThat(getMuteIconFrame().getVisibility()).isEqualTo(GONE);
    }

    @Test
    public void setMuteState_muted_muteIconVisibleAndValueIsMin() {
        mSlider.setMuteState(MUTE_MUTED);

        assertThat(getMuteIconFrame().getVisibility()).isEqualTo(VISIBLE);
        assertThat(mSlider.getValue()).isEqualTo(TEST_MIN);
    }

    @Test
    public void setMuteState_notMuted_muteIconVisible() {
        mSlider.setMuteState(MUTE_NOT_MUTED);

        assertThat(getMuteIconFrame().getVisibility()).isEqualTo(VISIBLE);
    }

    private ViewGroup getMuteIconFrame() {
        return mSlider.requireViewById(R.id.mute_icon_frame);
    }
}
