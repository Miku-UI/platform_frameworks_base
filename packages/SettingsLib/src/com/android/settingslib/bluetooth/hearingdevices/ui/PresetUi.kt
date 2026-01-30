/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settingslib.bluetooth.hearingdevices.ui

import android.bluetooth.BluetoothHapPresetInfo

/**
 * Defines the contract for a user interface that controls hearing device presets.
 *
 * <p>This interface serves as the "view" in the architecture, providing methods for a controller
 * to manage the display of preset controls and to receive user interactions.
 */
interface PresetUi : ExpandableControlUi {
    /** Interface definition for a callback to be invoked when event happens in PresetUi. */
    interface PresetUiListener {
        /**
         * Called when the preset of the specified side is changed by a user interaction in the UI.
         *
         * @param side The side of the device (e.g., left, right, or unified).
         * @param value The index of the selected preset.
         */
        fun onPresetChangedFromUi(side: Int, value: Int)
    }

    /**
     * Sets the listener to be invoked when events happen in this UI.
     *
     * @param listener The listener to set. A {@code null} value will unregister the listener.
     * @see PresetUiListener
     */
    fun setListener(listener: PresetUiListener?)

    /**
     * Sets up the preset control in the UI.
     *
     * <p>The UI provides separate controls for each side, along with a single unified control
     * for all sides simultaneously.
     *
     * @param sides A set of device sides in the same set.
     */
    fun setupControls(sides: Set<Int>)

    /**
     * Sets if the specified preset control is enabled.
     *
     * @param side The side of the control to update.
     * @param enabled The enabled state.
     */
    fun setControlEnabled(side: Int, enabled: Boolean)

    /**
     * Sets the available preset options for a specified preset control in the UI.
     *
     * @param side The side of the control to update.
     * @param presetInfos A list of the preset options.
     */
    fun setControlList(side: Int, presetInfos: List<BluetoothHapPresetInfo>)

    /**
     * Sets the selected preset option for a specified preset control in the UI.
     *
     * @param side The side of the control to update.
     * @param presetIndex An index value of the selected preset.
     */
    fun setControlValue(side: Int, presetIndex: Int)

    /**
     * Gets the current preset value from a specified preset control in the UI.
     *
     * @param side The side of the control to get the value from.
     * @return The index value of the current selected preset.
     */
    fun getControlValue(side: Int): Int

    @Override
    override fun setControlExpandable(expandable: Boolean) {
        // This implementation intentionally does nothing to enforce a non-expandable UI.
    }

    @Override
    override fun isControlExpandable(): Boolean {
        return true
    }
}