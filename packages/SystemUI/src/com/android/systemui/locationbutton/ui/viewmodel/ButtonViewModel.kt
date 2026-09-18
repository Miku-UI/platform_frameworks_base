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
package com.android.systemui.locationbutton.ui.viewmodel

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

data class ButtonViewModel(
    val width: Dp,
    val height: Dp,
    val paddingLeft: Dp,
    val paddingTop: Dp,
    val paddingRight: Dp,
    val paddingBottom: Dp,
    val backgroundColor: Color,
    val strokeColor: Color,
    val strokeWidth: Dp,
    val cornerRadius: Dp?,
    val pressedCornerRadius: Dp?,
    val iconTint: Color,
    @StringRes val textResId: Int?,
    val textColor: Color,
    val configuration: Configuration,
    val validationFlags: Int = 0,
)
