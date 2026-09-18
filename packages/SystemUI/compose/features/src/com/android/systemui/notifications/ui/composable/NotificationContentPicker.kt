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

package com.android.systemui.notifications.ui.composable

import com.android.compose.animation.scene.ContentKey
import com.android.systemui.scene.shared.model.Scenes
import javax.inject.Inject

interface NotificationContentPicker {
    fun pickContentFrom(keys: Set<ContentKey>): ContentKey?
}

/**
 * Picks content with highest z-order (top-most).
 * Used for the main notification stack.
 */
class StackPlaceholderContentPicker @Inject constructor(
    private val sorter: ContentZOrderSorter
) : NotificationContentPicker {

    override fun pickContentFrom(keys: Set<ContentKey>): ContentKey? {
        return sorter.getHighestZ(keys)
    }
}

/**
 * Pick lowest z-order (bottom-most).
 */
class LowestZContentPicker @Inject constructor(
    private val sorter: ContentZOrderSorter
) : NotificationContentPicker {
    override fun pickContentFrom(keys: Set<ContentKey>): ContentKey? {
        return sorter.getLowestZ(keys)
    }
}

/**
 * Apply HUN-specific rules before falling back to default behavior
 */
class HeadsUpPlaceholderContentPicker @Inject constructor(
    private val defaultPicker: LowestZContentPicker
) : NotificationContentPicker {

    override fun pickContentFrom(keys: Set<ContentKey>): ContentKey? {
        // Pick shade (higher z than lockscreen/AOD) so that HUN gets
        // expanded target bounds at start of transition
        // to prevent jump cut at end of transition
        if (Scenes.Lockscreen in keys && Scenes.Shade in keys) {
            return Scenes.Shade
        }
        return defaultPicker.pickContentFrom(keys)
    }
}
