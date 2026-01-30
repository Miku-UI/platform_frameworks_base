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

package com.android.systemui.screencapture.record.largescreen.ui.viewmodel

import com.android.systemui.common.shared.model.Icon

data class RadioButtonGroupItemViewModel
constructor(
    val label: String? = null,
    val selectedIcon: Icon? = null,
    val unselectedIcon: Icon? = null,
    val isSelected: Boolean,
    val onClick: () -> Unit,
    val contentDescription: String? = null,
) {
    init {
        require((selectedIcon != null) == (unselectedIcon != null)) {
            "selectedIcon and unselectedIcon must both be provided or both be null."
        }
    }

    /** Secondary constructor for cases where the icon is the same whether selected or not. */
    constructor(
        label: String? = null,
        icon: Icon? = null,
        isSelected: Boolean,
        onClick: () -> Unit,
        contentDescription: String? = null,
    ) : this(
        label = label,
        selectedIcon = icon,
        unselectedIcon = icon,
        isSelected = isSelected,
        onClick = onClick,
        contentDescription = contentDescription,
    )

    val icon: Icon?
        get() = if (isSelected) selectedIcon else unselectedIcon
}
