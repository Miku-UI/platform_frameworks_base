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
import androidx.core.graphics.drawable.IconCompat

/** Data of the currently playing media. */
data class MediaMetadata(
    /** Title of the media used as a header title, usually song title. */
    val title: CharSequence,
    /** Secondary text used as a header subtitle, usually artist or album name. */
    val subtitle: CharSequence?,
    /** Optional thumbnail of the album to be displayed. */
    val albumArt: IconCompat?,
    /** Small icon of the source app in which the media is playing from. */
    val appIcon: Drawable?,
)
