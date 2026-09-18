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

package com.android.systemui.screenrecord.shared.model

import android.graphics.drawable.Icon
import android.net.Uri

/** Models the screen recording */
sealed interface ScreenRecording {

    val uri: Uri?
    val notificationId: Int

    /** Screen recording is being saved and will be available with the [uri]. */
    data class Saving(override val uri: Uri, override val notificationId: Int) : ScreenRecording

    /**
     * Screen recording is saved and is available with the [uri]. You can[thumbnail] when it's
     * available for the video preview.
     */
    data class Saved(
        override val uri: Uri,
        val thumbnail: Icon?,
        override val notificationId: Int,
    ) : ScreenRecording

    /** Couldn't save the screen recording with the [uri]. */
    data class NotSaved(override val uri: Uri?, override val notificationId: Int) : ScreenRecording
}
