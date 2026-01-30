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

package com.android.systemui.bluetooth.devicesettings.shared.model

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.graphics.drawable.toDrawable
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingIcon
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon

@SuppressLint("UseCompatLoadingForDrawables")
fun DeviceSettingIcon.toSysUiIcon(context: Context, contentDescription: ContentDescription?): Icon {
    return when (this) {
        is DeviceSettingIcon.BitmapIcon ->
            Icon.Loaded(
                drawable = bitmap.toDrawable(context.resources),
                contentDescription = contentDescription,
            )
        is DeviceSettingIcon.ResourceIcon ->
            Icon.Resource(resId = resId, contentDescription = contentDescription)
    }
}
