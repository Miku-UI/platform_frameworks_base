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

package com.android.systemui.qs.panels.ui.viewmodel.toolbar

import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R

data class PowerMenuToggleButtonUiState(val onClick: () -> Unit, val isSelected: Boolean) {
    val icon: Icon = powerIcon
    val chevron: Icon = chevronIcon
    val chevronRotation: Float = if (isSelected) 270f else 90f
    val stateDescriptionRes =
        if (isSelected) {
            R.string.accessibility_quick_settings_power_menu_expanded
        } else {
            R.string.accessibility_quick_settings_power_menu_collapsed
        }

    companion object {
        private val powerIcon =
            Icon.Resource(
                R.drawable.ic_qs_footer_power,
                ContentDescription.Resource(R.string.accessibility_quick_settings_power_menu),
            )

        private val chevronIcon = Icon.Resource(R.drawable.ic_chevron_icon, null)
    }
}
