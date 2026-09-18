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

package com.android.systemui.media.dialog

import com.android.settingslib.media.MediaDevice

/**
 * MediaItem represents an item in OutputSwitcher list (could be a MediaDevice, a device group, or a
 * section title.).
 */
sealed class MediaItem {

    /** Represents a media device. */
    data class DeviceMediaItem(val mediaDevice: MediaDevice) : MediaItem()

    /**
     * Represents a media device group. This can be either a Cast device group or a Bluetooth
     * Personal Audio Sharing session.
     */
    data object DeviceGroupMediaItem : MediaItem()

    /** Represents the section title in the Output Switcher list. */
    data class GroupDividerMediaItem(
        /** Text of the title */
        val title: String,
        /** Whether a group divider has a button that expands group device list */
        val isExpandable: Boolean = false,
        /** Whether a group divider has a border at the top */
        val hasTopSeparator: Boolean = false,
    ) : MediaItem()
}
