/*
 * Copyright (C) 2020 The Android Open Source Project
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

/** Data defining UI of one button in a quick shelf part of Output Switcher. */
data class QuickAccessShelfButton(
    /** Label shown on the button for this action. */
    val title: String,
    /** Icon shown next to the title. */
    val icon: Drawable?,
    /** Whether or not the button is in an activated state. */
    val isActivated: Boolean,
    /** Listener to be executed on the click of the button. */
    val onClick: () -> Unit,
)
