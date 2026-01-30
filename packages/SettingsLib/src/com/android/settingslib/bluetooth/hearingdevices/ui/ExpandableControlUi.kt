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

import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_LEFT
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_LEFT_AND_RIGHT
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_MONO
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_RIGHT

/**
 * Defines the basic behavior and state for an expandable UI component.
 *
 * <p>An expandable UI typically consists of a header with a collapse/expand icon and multiple child
 * views. The UI's mode (expanded or collapsed) determines which specific set of child views is
 * visible.
 */
interface ExpandableControlUi {

    /** Interface definition for a callback to be invoked when event happens in ExpandableUi. */
    interface ExpandableControlUiListener {
        /** Called when the expand icon is clicked. */
        fun onExpandIconClick()
    }

    /** Sets if the UI is visible. */
    fun setVisible(visible: Boolean)

    /**
     * Sets if the UI is expandable between expanded and collapsed mode.
     *
     * A p> If the UI is not expandable, it implies the UI will always stay in collapsed mode
     */
    fun setControlExpandable(expandable: Boolean)

    /** @return if the UI is expandable. */
    fun isControlExpandable(): Boolean

    /** Sets if the UI is in expanded mode. */
    fun setControlExpanded(expanded: Boolean)

    /** @return if the UI is in expanded mode. */
    fun isControlExpanded(): Boolean

    companion object {
        /** The rotation degree of the expand icon when the UI is collapsed. */
        @JvmField val ROTATION_COLLAPSED = 0f
        /** The rotation degree of the expand icon when the UI is expanded. */
        @JvmField val ROTATION_EXPANDED = 180f

        /**
         * A special side identifier that represents unified control.
         *
         * A p>This identifier is used by the view in collapsed mode to allow a single control
         * to apply settings to all devices in the same set simultaneously.
         */
        @JvmField val SIDE_UNIFIED = 999

        /** All valid, controllable sides for the hearing device in the UI. */
        @JvmField val VALID_SIDES = listOf(
            SIDE_UNIFIED,
            SIDE_LEFT,
            SIDE_RIGHT,
            SIDE_MONO,
            SIDE_LEFT_AND_RIGHT
        )
    }
}