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

package com.android.settingslib.bluetooth.hearingdevices.ui;

import android.bluetooth.AudioInputControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/** Interface for the ambient volume UI. */
public interface AmbientVolumeUi extends ExpandableControlUi {

    /** Interface definition for a callback to be invoked when event happens in AmbientVolumeUi. */
    interface AmbientVolumeUiListener extends ExpandableControlUiListener {
        /** Called when the ambient volume icon is clicked. */
        void onAmbientVolumeIconClick();

        /** Called when the slider of the specified side is changed. */
        void onSliderValueChange(int side, int value);
    }

    /**
     * The default ambient volume level for hearing device ambient volume icon
     *
     * <p> This icon visually represents the current ambient volume. It displays separate
     * levels for the left and right sides, each with 5 levels ranging from 0 to 4.
     *
     * <p> To represent the combined left/right levels with a single value, the following
     * calculation is used:
     *      finalLevel = (leftLevel * 5) + rightLevel
     * For example:
     * <ul>
     *    <li>If left level is 2 and right level is 3, the final level will be 13 (2 * 5 + 3)</li>
     *    <li>If both left and right levels are 0, the final level will be 0</li>
     *    <li>If both left and right levels are 4, the final level will be 24</li>
     * </ul>
     */
    int AMBIENT_VOLUME_LEVEL_DEFAULT = 24;
    /**
     * The minimum ambient volume level for hearing device ambient volume icon
     *
     * @see #AMBIENT_VOLUME_LEVEL_DEFAULT
     */
    int AMBIENT_VOLUME_LEVEL_MIN = 0;
    /**
     * The maximum ambient volume level for hearing device ambient volume icon
     *
     * @see #AMBIENT_VOLUME_LEVEL_DEFAULT
     */
    int AMBIENT_VOLUME_LEVEL_MAX = 24;

    /** @return if the UI is capable to mute the ambient of remote device. */
    boolean isMutable();

    /** @return if the UI shows mute state */
    boolean isMuted();

    /**
     * Sets the listener to be invoked when events happen in this UI.
     * @see AmbientVolumeUiListener
     */
    void setListener(@Nullable AmbientVolumeUiListener listener);

    /**
     * Sets up sliders in the UI based on the provided sides.
     *
     * <p>A slider will be created for each unique side identifier in the {@code sides} set.
     * Additionally, a unified slider ({@link ExpandableControlUi#SIDE_UNIFIED}) is always created
     * to control all sides together when the UI is in a collapsed state.
     *
     * @param sides A set of integers representing the device sides for which individual
     *              sliders should be created (e.g., {@code HearingAidInfo.DeviceSide.SIDE_LEFT},
     *              {@code HearingAidInfo.DeviceSide.SIDE_RIGHT}).
     */
    void setupSliders(@NonNull Set<Integer> sides);

    /**
     * Sets if the slider is enabled.
     *
     * @param side the side of the slider
     * @param enabled the enabled state
     */
    void setSliderEnabled(int side, boolean enabled);

    /**
     * Sets the slider's value.
     *
     * @param side the side of the slider
     * @param value the ambient value
     */
    void setSliderValue(int side, int value);

    /**
     * Sets the slider's minimum and maximum value.
     *
     * @param side the side of the slider
     * @param min the minimum ambient value
     * @param max the maximum ambient value
     */
    void setSliderRange(int side, int min, int max);

    /**
     * Sets the slider's mute state.
     *
     * @param side the side of the slider
     * @param muteState the mute state, see {@link AudioInputControl.Mute}
     */
    void setSliderMuteState(int side, int muteState);

    /**
     * Gets the slider's mute state.
     *
     * @param side the side of the slider
     * @return the mute state, see {@link AudioInputControl.Mute}
     */
    int getSliderMuteState(int side);
}
