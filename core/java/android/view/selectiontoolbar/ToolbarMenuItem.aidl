/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.view.selectiontoolbar;

import android.graphics.drawable.Icon;

/**
 * @hide
 */
parcelable ToolbarMenuItem {

    /**
     * The priority of menu item is unknown.
     */
    const int PRIORITY_UNKNOWN = 0;

    /**
     * The priority of menu item is shown in primary selection toolbar.
     */
    const int PRIORITY_PRIMARY = 1;

    /**
     * The priority of menu item is shown in overflow selection toolbar.
     */
    const int PRIORITY_OVERFLOW = 2;

    /**
     * The id of the menu item. Not guaranteed to be unique.
     *
     * @see MenuItem#getItemId()
     */
    int itemId;

    /**
     * The index the item was at in the original list of menu items.
     *
     * @see MenuItem#getItemId()
     */
    int itemIndex;

    /**
     * The title of the menu item.
     *
     * @see MenuItem#getTitle()
     */
    CharSequence title;

    /**
     * The content description of the menu item.
     *
     * @see MenuItem#getContentDescription()
     */
    CharSequence contentDescription;

    /**
     * The group id of the menu item.
     *
     * @see MenuItem#getGroupId()
     */
    int groupId;

    /**
     * The icon id of the menu item.
     *
     * @see MenuItem#getIcon()
     */
    Icon icon;

    /**
     * The tooltip text of the menu item.
     *
     * @see MenuItem#getTooltipText()
     */
    CharSequence tooltipText;

    /**
     * The priority of the menu item used to display the order of the menu item.
     */
    int priority;
}
