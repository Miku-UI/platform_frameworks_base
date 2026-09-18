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

package com.android.systemui.screencapture.common.ui.viewmodel

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.android.systemui.lifecycle.Activatable
import com.android.systemui.screencapture.common.domain.model.TargetModel

/** A view model for capture targets. */
interface TargetViewModel : Activatable {
    /** The model that this view model is backed by */
    val model: TargetModel
    /** The icon associated with the target, if any. */
    val icon: Result<Bitmap>?
    /** The label associated with the target, if any. */
    val label: Result<CharSequence>?
    /** The thumbnail associated with the target, if any. */
    val thumbnail: Result<Bitmap?>?
    /** The background color associated with the target. Alpha channel removed. */
    val backgroundColorOpaque: Color
}
