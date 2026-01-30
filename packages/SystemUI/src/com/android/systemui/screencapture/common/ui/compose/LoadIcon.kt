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

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel

/** @see DrawableLoaderViewModel */
@Composable
fun loadIcon(
    viewModel: DrawableLoaderViewModel,
    @DrawableRes resId: Int,
    contentDescription: ContentDescription?,
): State<Icon.Loaded?> {
    val context = LocalContext.current
    return produceState<Icon.Loaded?>(
        initialValue = null,
        keys = arrayOf(viewModel, context, resId),
    ) {
        val drawable = viewModel.loadDrawable(context = context, resId = resId)
        value =
            Icon.Loaded(drawable = drawable, resId = resId, contentDescription = contentDescription)
    }
}
