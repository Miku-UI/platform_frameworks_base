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

package com.android.server;

import static com.android.server.input.InputManagerService.SW_HEADPHONE_INSERT_BIT;
import static com.android.server.input.InputManagerService.SW_LINEOUT_INSERT_BIT;
import static com.android.server.input.InputManagerService.SW_MICROPHONE_INSERT_BIT;
import static com.android.server.input.InputManagerService.SW_VIDEOOUT_INSERT_BIT;

import static org.junit.Assert.assertEquals;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WiredAccessoryManagerUnitTest {
    private static final String TAG = "WiredAccessoryManUnitTest";

    // The values defined here must match the ones in WiredAccessoryManager.
    // See L60-L65 in WiredAccessoryManager.java
    private static final int BIT_HEADSET = (1 << 0);
    private static final int BIT_HEADSET_NO_MIC = (1 << 1);
    private static final int BIT_USB_HEADSET_ANLG = (1 << 2);
    private static final int BIT_USB_HEADSET_DGTL = (1 << 3);
    private static final int BIT_HDMI_AUDIO = (1 << 4);
    private static final int BIT_LINEOUT = (1 << 5);
    private static final int SW_HEADSET_INSERT_BITS =
            SW_HEADPHONE_INSERT_BIT | SW_MICROPHONE_INSERT_BIT;
    private static final int SW_AVOUT_INSERT_BITS = SW_LINEOUT_INSERT_BIT | SW_VIDEOOUT_INSERT_BIT;

    @Test
    public void plugHeadphoneTest() {
        // Use mHeadsetState variable to represents the internal state.
        // WiredAccessoryManager.mHeadsetState
        int mHeadsetState = 0;
        // Headphone Plug Event:
        int switchValue = SW_HEADPHONE_INSERT_BIT;
        int switchMask = SW_HEADPHONE_INSERT_BIT;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect headphone plugged in", BIT_HEADSET_NO_MIC, mHeadsetState);
        // Headphone Unplug Event:
        switchValue = 0;
        switchMask = SW_HEADPHONE_INSERT_BIT;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect headphone unplugged", 0, mHeadsetState);
        // Check cases when value != mask
        switchValue = SW_HEADPHONE_INSERT_BIT;
        switchMask = SW_HEADSET_INSERT_BITS;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect headphone plugged in", BIT_HEADSET_NO_MIC, mHeadsetState);
        // Unplug
        switchValue = 0;
        switchMask = SW_HEADSET_INSERT_BITS;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect headphone unplugged", 0, mHeadsetState);
    }

    @Test
    public void plugHeadphoneAndHdmiTest() {
        // Use mHeadsetState variable to represents the internal state.
        // WiredAccessoryManager.mHeadsetState
        int mHeadsetState = 0;
        // Headphone Plug Event:
        // value = SW_HEADPHONE_INSERT_BIT, mask = SW_HEADPHONE_INSERT_BIT
        // HDMI Plug Event:
        // value = SW_AVOUT_INSERT_BITS, mask = SW_AVOUT_INSERT_BITS

        // Plug headphone
        int switchValue = SW_HEADPHONE_INSERT_BIT;
        int switchMask = SW_HEADPHONE_INSERT_BIT;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect headphone plugged in", BIT_HEADSET_NO_MIC, mHeadsetState);

        // Plug HDMI
        switchValue = SW_AVOUT_INSERT_BITS;
        switchMask = SW_AVOUT_INSERT_BITS;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals(
                "Expect headphone and HDMI plugged in",
                BIT_HEADSET_NO_MIC | BIT_HDMI_AUDIO,
                mHeadsetState);

        switchValue = 0;
        switchMask = SW_HEADPHONE_INSERT_BIT;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect headphone unplugged", BIT_HDMI_AUDIO, mHeadsetState);

        switchValue = 0;
        switchMask = SW_AVOUT_INSERT_BITS;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect HDMI unplugged", 0, mHeadsetState);

        switchValue = SW_AVOUT_INSERT_BITS;
        switchMask = SW_AVOUT_INSERT_BITS;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect HDMI plugged in", BIT_HDMI_AUDIO, mHeadsetState);

        // Plug headphone
        switchValue = SW_HEADPHONE_INSERT_BIT;
        switchMask = SW_HEADPHONE_INSERT_BIT;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals(
                "Expect both headphone and HDMI plugged in",
                BIT_HDMI_AUDIO | BIT_HEADSET_NO_MIC,
                mHeadsetState);

        switchValue = 0;
        switchMask = SW_AVOUT_INSERT_BITS;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect HDMI unplugged", BIT_HEADSET_NO_MIC, mHeadsetState);

        switchValue = 0;
        switchMask = SW_HEADPHONE_INSERT_BIT;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect headphone unplugged", 0, mHeadsetState);
    }

    @Test
    public void plugHeadsetAndLineoutTest() {
        // Use mHeadsetState variable to represents the internal state.
        // WiredAccessoryManager.mHeadsetState
        int mHeadsetState = 0;

        // Headset Plug Event:
        // value = SW_HEADPHONE_INSERT_BIT, mask = SW_HEADPHONE_INSERT_BIT
        // Lineout Plug Event:
        // value = SW_LINEOUT_INSERT_BIT, mask = SW_LINEOUT_INSERT_BIT

        // Plug headphone
        int switchValue = SW_HEADPHONE_INSERT_BIT;
        int switchMask = SW_HEADPHONE_INSERT_BIT;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect headphone plugged in", BIT_HEADSET_NO_MIC, mHeadsetState);

        // Plug Lineout
        switchValue = SW_LINEOUT_INSERT_BIT;
        switchMask = SW_LINEOUT_INSERT_BIT;
        int newHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals(
                "Expect headphone and lineout plugged in",
                BIT_HEADSET_NO_MIC | BIT_LINEOUT,
                newHeadsetState);
        // updateLocked will reject newHeadsetState
        // so we do not update mHeadsetState here.

        switchValue = 0;
        switchMask = SW_HEADPHONE_INSERT_BIT;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect headphone unplugged", 0, mHeadsetState);

        switchValue = 0;
        switchMask = SW_LINEOUT_INSERT_BIT;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect LINEOUT unplugged", 0, mHeadsetState);
    }

    @Test
    public void plugLineoutAndHdmiTest() {
        int mHeadsetState = 0;

        // Plug HDMI
        int switchValue = SW_AVOUT_INSERT_BITS;
        int switchMask = SW_AVOUT_INSERT_BITS;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect HDMI plugged in", BIT_HDMI_AUDIO, mHeadsetState);

        // Plug lineout
        switchValue = SW_LINEOUT_INSERT_BIT;
        switchMask = SW_LINEOUT_INSERT_BIT;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect LINEOUT plugged in", BIT_LINEOUT | BIT_HDMI_AUDIO, mHeadsetState);

        switchValue = 0;
        switchMask = SW_LINEOUT_INSERT_BIT;
        mHeadsetState =
                WiredAccessoryManager.calculateHeadsetState(mHeadsetState, switchValue, switchMask);
        assertEquals("Expect LINEOUT unplugged", BIT_HDMI_AUDIO, mHeadsetState);
    }
}
