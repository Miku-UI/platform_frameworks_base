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
package com.android.systemui.locationbutton.shared.model

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

data class ButtonModel(
    val width: Int,
    val height: Int,
    val paddingLeft: Int,
    val paddingTop: Int,
    val paddingRight: Int,
    val paddingBottom: Int,
    val backgroundColor: Color,
    val strokeColor: Color,
    val strokeWidth: Int,
    val cornerRadius: Float?,
    val pressedCornerRadius: Float?,
    val iconTint: Color,
    @StringRes val textResId: Int?,
    val textColor: Color,
    val configuration: Configuration,
    val density: Float,
    val textType: Int,
    val validationFlags: Int = 0,
)
