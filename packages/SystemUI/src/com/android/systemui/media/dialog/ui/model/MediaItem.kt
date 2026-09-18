/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.media.dialog.ui.model

import android.graphics.drawable.Drawable

/**
 * MediaItem represents an item in OutputSwitcher list (could be a MediaDevice, a device group, or a
 * section title.).
 */
sealed class MediaItem {

    /** Represents a media device. */
    data class DeviceMediaItem(
        /** Text of the title. */
        val title: String,
        /** Optional subtitle to be put below the title. */
        val subtitle: String? = null,
        /** Optional drawable to show as an icon representing this item. */
        val icon: Drawable? = null,
        /** Whether or not the item can be selected. */
        val isSelectable: Boolean = false,
        /** Whether or not the item is currently selected by the user. */
        val isSelected: Boolean = false,
        /** Whether or not the item is currently connected. */
        val isConnected: Boolean = false,
        /**
         * Click listener to be triggered on user click. If it's null then the device will be
         * considered a disabled device option.
         */
        val onClick: (() -> Unit)? = null,
    ) : MediaItem()

    /**
     * Represents a media device group. This can be either a Cast device group or a Bluetooth
     * Personal Audio Sharing session.
     */
    data object DeviceGroupMediaItem : MediaItem()

    /** Represents the section title in the Output Switcher list. */
    data class SectionTitleMediaItem(
        /** Text of the title */
        val title: String,
        /** Whether a group divider has a button that expands group device list */
        val hasGroupExpandButton: Boolean = false,
        /** Whether a group divider has a border at the top */
        val hasTopSeparator: Boolean = false,
    ) : MediaItem()
}
