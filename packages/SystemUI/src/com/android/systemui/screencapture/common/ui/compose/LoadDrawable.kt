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

package com.android.systemui.screencapture.common.ui.compose

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel

/** @see DrawableLoaderViewModel */
@Composable
fun loadDrawable(viewModel: DrawableLoaderViewModel, @DrawableRes resId: Int): State<Drawable?> {
    val context = LocalContext.current
    return produceState<Drawable?>(initialValue = null, keys = arrayOf(viewModel, context, resId)) {
        value = viewModel.loadDrawable(context = context, resId = resId)
    }
}
