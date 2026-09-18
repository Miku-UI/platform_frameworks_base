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

package com.android.systemui.accessibility.shortcutchooser.shared.model

import android.graphics.drawable.Drawable
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType

/** The model that represents the state of an accessibility target in ShortcutChooser dialogs. */
data class AccessibilityTargetModel(
    @param:UserShortcutType val shortcutType: Int,
    /**
     * The flattened [ComponentName] string or the class name of a system class implementing a
     * supported accessibility feature.
     */
    val targetName: String,
    /** The name of the accessibility feature itself shown to users. */
    val featureName: String,
    val icon: Drawable,
    /** True if the feature is assigned to the `shortcutType` shortcut. */
    val isAssigned: Boolean,
    /** True if the target has a toggle. */
    val isToggleable: Boolean,
    /** True if the accessibility feature is turned on. */
    val isStateOn: Boolean,
)
