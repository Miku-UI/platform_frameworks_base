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

package com.android.systemui.screencapture.common.domain.model

import android.graphics.Bitmap
import android.media.projection.MediaProjectionAppContent

/** Collection of information about currently available app content. */
data class ScreenCaptureAppContent(
    val packageName: String,
    val contentId: Int,
    val label: CharSequence,
    val thumbnail: Bitmap,
) {
    constructor(
        packageName: String,
        appContent: MediaProjectionAppContent,
    ) : this(
        packageName = packageName,
        contentId = appContent.id,
        label = appContent.title,
        thumbnail = appContent.thumbnail,
    )
}
