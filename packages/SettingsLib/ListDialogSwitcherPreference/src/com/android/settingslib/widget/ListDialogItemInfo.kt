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
package com.android.settingslib.widget

import android.graphics.drawable.Drawable

/**
 * A data class representing a single selectable item within the [ListSwitcherDialogFragment].
 *
 * @property id A unique and stable identifier for this item. This ID is persisted to save the user's selection.
 * @property title The main text to display for this item in the list.
 * @property summary Optional descriptive text to display below the title.
 * @property icon An optional icon to display next to the item's text.
 */
data class ListDialogItemInfo(
    val id: String,
    val title: String,
    val summary: String?,
    val icon: Drawable?
)
