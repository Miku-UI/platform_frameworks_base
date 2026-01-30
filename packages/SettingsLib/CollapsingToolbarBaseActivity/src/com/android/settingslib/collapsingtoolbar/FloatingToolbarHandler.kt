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

package com.android.settingslib.collapsingtoolbar

import com.android.settingslib.collapsingtoolbar.widget.ScrollableToolbarItemLayout
import com.android.settingslib.collapsingtoolbar.widget.ScrollableToolbarItemLayout.ToolbarItem

/**
 * Interface for managing a floating toolbar, providing methods to control its visibility,
 * add items, and handle item selection events.
 */
interface FloatingToolbarHandler {
    /**
     * Sets the visibility of the floating toolbar.
     *
     * @param isVisible True to show the toolbar, false to hide it.
     */
    fun setFloatingToolbarVisibility(isVisible: Boolean)

    /**
     * Sets the data for the items in the floating toolbar.
     *
     * @param itemList The list of [ToolbarItem] to display as items.
     */
    fun setToolbarItems(itemList: List<ToolbarItem>)

    /**
     * Sets a listener to be notified when a [ToolbarItem] is selected, unselected, or
     * reselected.
     *
     * @param listener The [ScrollableToolbarItemLayout.OnItemSelectedListener] to be notified when
     * [ToolbarItem] is selected.
     */
    fun setOnItemSelectedListener(listener: ScrollableToolbarItemLayout.OnItemSelectedListener)

    /**
     * Stops listening to [ToolbarItem] selection changes
     */
    fun removeOnItemSelectedListener()
}